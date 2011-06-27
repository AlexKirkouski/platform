package platform.server.data.query.innerjoins;

import platform.base.BaseUtils;
import platform.base.QuickMap;
import platform.interop.Compare;
import platform.server.data.expr.BaseExpr;
import platform.server.data.expr.Expr;
import platform.server.data.expr.KeyExpr;
import platform.server.data.expr.query.StatKeys;
import platform.server.data.where.MapWhere;
import platform.server.data.translator.PartialQueryTranslator;
import platform.server.data.where.Where;

import java.util.*;

public class KeyEquals extends QuickMap<KeyEqual, Where> {

    public KeyEquals() {
    }

    public KeyEquals(Where where) {
        super(new KeyEqual(), where);
    }

    public KeyEquals(KeyExpr key, BaseExpr expr) {
        super(new KeyEqual(key, expr), expr.getWhere());
    }

    protected Where addValue(Where prevValue, Where newValue) {
        return prevValue.or(newValue);
    }

    protected KeyEquals createThis() {
        return new KeyEquals();
    }

    public KeyEquals and(KeyEquals joins) {
        KeyEquals result = new KeyEquals();
        // берем все пары joins'ов
        for(int i1=0;i1<size;i1++)
            for(int i2=0;i2<joins.size;i2++) {
                KeyEqual eq1 = getKey(i1);
                KeyEqual eq2 = joins.getKey(i2);

                Where where1 = getValue(i1);
                Where where2 = joins.getValue(i2);

                // сначала определяем общие ключи
                Map<KeyExpr, BaseExpr> diffEq1 = new HashMap<KeyExpr, BaseExpr>(); Map<KeyExpr, BaseExpr> diffEq2 = new HashMap<KeyExpr, BaseExpr>();
                Map<KeyExpr, BaseExpr> sameEq1 = BaseUtils.splitKeys(eq1.keyExprs, eq2.keyExprs.keySet(), diffEq1);
                Map<KeyExpr, BaseExpr> sameEq2 = BaseUtils.splitKeys(eq2.keyExprs, eq1.keyExprs.keySet(), diffEq2);

                // транслируем правые левыми
                Map<KeyExpr, Expr> cleanEq1 = new HashMap<KeyExpr, Expr>();// складываются "очищенные" equals'ы
                Map<KeyExpr, Expr> transEq1 = new PartialQueryTranslator(diffEq2).translate(diffEq1);
                // погнали топологическую сортировку отсеивая Expr'ы
                while(!transEq1.isEmpty()) {
                    boolean found = false;
                    Iterator<Map.Entry<KeyExpr,Expr>> it = transEq1.entrySet().iterator();
                    while(it.hasNext()) {
                        Map.Entry<KeyExpr,Expr> keyEq = it.next();
                        Set<KeyExpr> enumKeys = new HashSet<KeyExpr>();
                        keyEq.getValue().enumKeys(enumKeys);
                        if(Collections.disjoint(enumKeys, transEq1.keySet())) {// если не зависит от остальных
                            cleanEq1.put(keyEq.getKey(),keyEq.getValue().translateQuery(new PartialQueryTranslator(cleanEq1))); // транслэйтим clean'ами
                            it.remove();
                            found = true; 
                            break;
                        }
                    }

                    if(!found) { // значит остались циклы, берем любую и перекидываем в where
                        Map.Entry<KeyExpr, Expr> cycle = transEq1.entrySet().iterator().next();
                        where1 = where1.and(cycle.getKey().compare(cycle.getValue(), Compare.EQUALS));
                    }
                }

                // второй просто транслируем первым
                PartialQueryTranslator cleanTranslator1 = new PartialQueryTranslator(cleanEq1);
                Map<KeyExpr, Expr> cleanEq2 = cleanTranslator1.translate(diffEq2);
                PartialQueryTranslator cleanTranslator2 = new PartialQueryTranslator(cleanEq2);
                Where andWhere = where2.translateQuery(cleanTranslator1).and(where1.translateQuery(cleanTranslator2));

                // сливаем same'ы, их также надо translate'ить так как могут быть несвободными от противоположных ключей 
                Where extraWhere = Where.TRUE;
                Map<KeyExpr,Expr> mergeSame = new HashMap<KeyExpr,Expr>(cleanTranslator2.translate(sameEq1));
                for(Map.Entry<KeyExpr,Expr> andKeyExpr : cleanTranslator1.translate(sameEq2).entrySet()) {
                    Expr expr = mergeSame.get(andKeyExpr.getKey());
                    if(!expr.isValue()) // предпочитаем статичные значение
                        mergeSame.put(andKeyExpr.getKey(),andKeyExpr.getValue());
                    if(!BaseUtils.hashEquals(expr, andKeyExpr.getValue())) // закидываем compare
                        extraWhere = extraWhere.and(expr.compare(andKeyExpr.getValue(), Compare.EQUALS));
                }

                Map<KeyExpr,BaseExpr> andEq = new HashMap<KeyExpr, BaseExpr>();
                for(Map.Entry<KeyExpr,Expr> mergeEntry : BaseUtils.merge(mergeSame,BaseUtils.merge(cleanEq1, cleanEq2)).entrySet()) // assertion что не пересекаются
                    if(mergeEntry.getValue() instanceof BaseExpr)
                        andEq.put(mergeEntry.getKey(), (BaseExpr) mergeEntry.getValue());
                    else // выкидываем Expr
                        extraWhere = extraWhere.and(mergeEntry.getKey().compare(mergeEntry.getValue(), Compare.EQUALS));

                andWhere = andWhere.and(extraWhere);
                if (cleanEq1.isEmpty() && cleanEq2.isEmpty() && extraWhere.isTrue()) // чтобы не уходило в бесконечный цикл
                    result.add(new KeyEqual(andEq), andWhere);
                else {
                    KeyEquals recEquals = andWhere.getKeyEquals();
                    for(int i=0;i<recEquals.size;i++)
                        result.add(new KeyEqual(BaseUtils.merge(andEq, recEquals.getKey(i).keyExprs)), recEquals.getValue(i));
                }
            }
        return result;
    }
    

    protected boolean containsAll(Where who, Where what) {
        throw new RuntimeException("not supported yet");
    }

    public Collection<InnerGroupJoin<? extends GroupJoinSet>> getInnerJoins(boolean noJoins, Set<KeyExpr> keys) {
        Collection<InnerGroupJoin<? extends GroupJoinSet>> result = new ArrayList<InnerGroupJoin<? extends GroupJoinSet>>();
        for(int i=0;i<size;i++) {
            KeyEqual keyEqual = getKey(i);
            Where where = getValue(i);

            ObjectJoinSets objectJoinSets = where.groupObjectJoinSets();

            if(noJoins) { // группируем по enoughKeys
                MapWhere<StatKeys<KeyExpr>> insufWheres = objectJoinSets.compileStats(BaseUtils.removeSet(keys, keyEqual.keyExprs.keySet()));
                for(int j=0;j<insufWheres.size;j++)
                    result.add(new InnerGroupJoin<GroupStatKeys>(keyEqual, new GroupStatKeys(insufWheres.getKey(j)), insufWheres.getValue(j)));
            } else { // группируем по means
                for(Map.Entry<ObjectJoinSet,Where> objectJoin : objectJoinSets.compileMeans().entrySet())
                    result.add(new InnerSelectJoin(keyEqual, objectJoin.getKey().getJoins(), objectJoin.getValue()));
            }
        }
        return result;
    }

    // получает Where с учетом трансляций
    public Where getWhere() {
        Where where = Where.FALSE;
        for(int i=0;i<size;i++)
           where = where.or(getValue(i).and(getKey(i).getWhere()));
        return where;
    }
}
