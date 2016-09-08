package lsfusion.server.data.query;

import lsfusion.server.caches.IdentityQuickLazy;
import lsfusion.server.data.query.innerjoins.UpWheres;
import lsfusion.server.data.query.stat.*;
import lsfusion.server.data.query.stat.KeyStat;
import lsfusion.server.data.translator.ExprTranslator;
import lsfusion.server.form.navigator.SQLSessionUserProvider;
import lsfusion.server.session.PropertyChange;
import lsfusion.base.*;
import lsfusion.base.col.ListFact;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.*;
import lsfusion.base.col.interfaces.mutable.*;
import lsfusion.base.col.interfaces.mutable.add.MAddExclMap;
import lsfusion.base.col.interfaces.mutable.add.MAddSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.*;
import lsfusion.interop.Compare;
import lsfusion.server.Settings;
import lsfusion.server.SystemProperties;
import lsfusion.server.caches.AbstractOuterContext;
import lsfusion.server.caches.OuterContext;
import lsfusion.server.classes.IntegralClass;
import lsfusion.server.classes.OrderClass;
import lsfusion.server.classes.StringClass;
import lsfusion.server.data.*;
import lsfusion.server.data.expr.*;
import lsfusion.server.data.expr.formula.FormulaExpr;
import lsfusion.server.data.expr.order.PartitionCalc;
import lsfusion.server.data.expr.order.PartitionToken;
import lsfusion.server.data.expr.query.*;
import lsfusion.server.data.query.innerjoins.GroupJoinsWhere;
import lsfusion.server.data.sql.SQLSyntax;
import lsfusion.server.data.translator.MapTranslate;
import lsfusion.server.data.translator.MapValuesTranslate;
import lsfusion.server.data.type.*;
import lsfusion.server.data.where.AbstractWhere;
import lsfusion.server.data.where.CheckWhere;
import lsfusion.server.data.where.Where;
import lsfusion.server.logics.ServerResourceBundle;

import java.sql.SQLException;
import java.util.*;

// нужен для Map'а ключей / значений
// Immutable/Thread Safe
public class CompiledQuery<K,V> extends ImmutableObject {
    final public String from;
    final public ImMap<K,String> keySelect;
    final public ImMap<V,String> propertySelect;
    final public ImCol<String> whereSelect;
    final public ImOrderSet<K> keyOrder; // чисто оптимизация, чтобы лишний SELECT в getInsertSelect не делать
    final public ImOrderSet<V> propertyOrder;

    public final DynamicExecuteEnvironment queryExecEnv; // assertion что при передаче куда-то subQueries сохраняются - ни больше не меньше

    private TypeExecuteEnvironment getTypeExecEnv(SQLSessionUserProvider userProvider) {
        if(sql.getLength() <= Settings.get().getQueryLengthTimeout())
            return TypeExecuteEnvironment.NONE;

        Integer type = null;
        if(userProvider != null) {
            type = DynamicExecuteEnvironment.getUserExecEnv(userProvider);
        }
        if(type == null)
            type = Settings.get().getDefaultTypeExecuteEnvironment();

        return TypeExecuteEnvironment.get(type);
    }

    // тут немного специфичная оптимизация на уменьшения locks, с учетом того что почти у всех пользователей всегда будут одни и те же env'ы
    public DynamicExecuteEnvironment getQueryExecEnv(SQLSessionUserProvider userProvider) {
        TypeExecuteEnvironment typeEnv = getTypeExecEnv(userProvider);
        TypeExecuteEnvironment queryType = queryExecEnv.getType();
        if(queryType == null || queryType.equals(typeEnv))
            return queryExecEnv;
        return extraEnvs.getEnv(typeEnv, sql);
    }

    private static class ExtraEnvs {
        private MAddExclMap<TypeExecuteEnvironment, DynamicExecuteEnvironment> extraQueryExecEnvs;

        private synchronized DynamicExecuteEnvironment getEnv(TypeExecuteEnvironment type, SQLQuery query) {
            if(extraQueryExecEnvs == null)
                extraQueryExecEnvs = MapFact.mAddExclMap();

            DynamicExecuteEnvironment execEnv = extraQueryExecEnvs.get(type);
            if (execEnv == null) {
                execEnv = type.create(query);
                extraQueryExecEnvs.exclAdd(type, execEnv);
            }
            return execEnv;
        }
    }
    private ExtraEnvs extraEnvs;

    final public ImRevMap<K,String> keyNames;
    final public ImRevMap<V,String> propertyNames;
    final ImRevMap<ParseValue,String> params;

    final public ImSet<K> areKeyValues;
    final public ImSet<V> arePropValues;

    public ImMap<V, ClassReader> getMapPropertyReaders() { // пока не хочется generics лепить для ClassReader
        return (ImMap<V, ClassReader>) propertyNames.join(sql.propertyReaders);
    }

    final public SQLQuery sql;
    public final StaticExecuteEnvironment env;

    private boolean checkQuery() {
        return true;
    }

    // перемаппит другой CompiledQuery
    public <MK,MV> CompiledQuery(CompiledQuery<MK,MV> compile,ImRevMap<K,MK> mapKeys,ImRevMap<V,MV> mapProperties, final MapValuesTranslate mapValues) {
        from = compile.from;
        whereSelect = compile.whereSelect;
        keySelect = mapKeys.join(compile.keySelect);
        propertySelect = mapProperties.join(compile.propertySelect);

        keyNames = mapKeys.join(compile.keyNames);
        propertyNames = mapProperties.join(compile.propertyNames);

        sql = compile.sql;
        queryExecEnv = compile.queryExecEnv;
        extraEnvs = compile.extraEnvs;

        ImRevMap<MK, K> reversedMapKeys = mapKeys.reverse();
        ImRevMap<MV, V> reversedMapProps = mapProperties.reverse();

        areKeyValues = compile.areKeyValues.mapSetValues(reversedMapKeys.fnGetValue());
        arePropValues = compile.arePropValues.mapSetValues(reversedMapProps.fnGetValue());

        params = compile.params.mapRevKeys(new GetValue<ParseValue, ParseValue>() {
            public ParseValue getMapValue(ParseValue value) {
                if (value instanceof Value)
                    return mapValues.translate((Value) value);
                assert value instanceof StaticValueExpr;
                return value;
            }
        });

        keyOrder = compile.keyOrder.mapOrder(reversedMapKeys);
        propertyOrder = compile.propertyOrder.mapOrder(reversedMapProps);

        env = compile.env;

        assert checkQuery();
    }

    private static class FullSelect extends CompileSource {

        private FullSelect(KeyType keyType, Where fullWhere, ImRevMap<ParseValue, String> params, SQLSyntax syntax, MStaticExecuteEnvironment env, ImMap<KeyExpr, String> keySelect, ImMap<JoinData, String> joinData) {
            super(keyType, fullWhere, params, syntax, env);
            this.keySelect = keySelect;
            this.joinData = joinData;
        }

        public final ImMap<KeyExpr,String> keySelect;
        public final ImMap<JoinData,String> joinData;

        @Override
        public String getSource(KeyExpr key) {
            return keySelect.get(key);
        }

        public String getSource(Table.Join.Expr expr) {
            assert joinData.get(expr)!=null;
            return joinData.get(expr);
        }

        public String getSource(Table.Join.IsIn where) {
            assert joinData.get(where)!=null;
            return joinData.get(where);
        }

        public String getSource(QueryExpr queryExpr) {
            assert joinData.get(queryExpr)!=null;
            return joinData.get(queryExpr);
        }

        public String getSource(IsClassExpr classExpr) {
            assert joinData.get(classExpr)!=null;
            return joinData.get(classExpr);
        }
    }

    // многие субд сами не могут определить некоторые вещи, приходится им помогать
    public static <P> ImOrderMap<P, CompileOrder> getPackedCompileOrders(ImMap<P, Expr> orderExprs, Where where, ImOrderMap<P, Boolean> orders) {
        MOrderExclMap<P, CompileOrder> mResult = MapFact.mOrderExclMapMax(orders.size());
        MAddSet<KeyExpr> currentKeys = SetFact.mAddSet();
        orderExprs = PropertyChange.simplifyExprs(orderExprs, where); // чтобы KeyEquals еще учесть
        for(int i=0,size=orders.size();i<size;i++) {
            P order = orders.getKey(i);
            Expr orderExpr = orderExprs.get(order);
            if(!currentKeys.containsAll(BaseUtils.<ImSet<KeyExpr>>immutableCast(orderExpr.getOuterKeys()))) {
                boolean notNull = false;
                if(orderExpr instanceof KeyExpr) {
                    notNull = true;
                    currentKeys.add((KeyExpr)orderExpr);
                }
                mResult.exclAdd(order, new CompileOrder(orders.getValue(i), where.isFalse() ? NullReader.instance : orderExpr.getReader(where), notNull));
            }
        }
        return mResult.immutableOrder();
    }

    public CompiledQuery(final Query<K, V> query, SQLSyntax syntax, ImOrderMap<V, Boolean> orders, LimitOptions limit, SubQueryContext subcontext, boolean noExclusive, boolean noInline, ImMap<V, Type> exCastTypes) {

        Result<ImOrderSet<K>> resultKeyOrder = new Result<>(); Result<ImOrderSet<V>> resultPropertyOrder = new Result<>();

        keyNames = query.mapKeys.mapRevValues(new GenNameIndex("jkey", ""));
        propertyNames = query.properties.mapRevValues(new GenNameIndex("jprop",""));
        params = SetFact.addExclSet(query.getInnerValues(), query.getInnerStaticValues()).mapRevValues(new GenNameIndex("qwer", "ffd"));

        MStaticExecuteEnvironment mEnv = StaticExecuteEnvironmentImpl.mEnv();
        Result<Cost> mBaseCost = new Result<>(Cost.MIN);
        MExclMap<String, SQLQuery> mSubQueries = MapFact.mExclMap();

        String select;

        ImMap<K, ClassReader> keyReaders = query.mapKeys.mapValues(new GetValue<ClassReader, KeyExpr>() {
            public ClassReader getMapValue(KeyExpr value) {
                return query.where.isFalse() ? NullReader.instance : value.getType(query.where);
            }
        });

        ImMap<V, ClassReader> propertyReaders = query.properties.mapValues(new GetValue<ClassReader, Expr>() {
            public ClassReader getMapValue(Expr value) {
                return query.where.isFalse() ? NullReader.instance : value.getReader(query.where);
            }
        });

        ImOrderMap<V, CompileOrder> compileOrders = query.getPackedCompileOrders(orders);

        boolean useFJ = syntax.useFJ();
        noExclusive = noExclusive || Settings.get().isNoExclusiveCompile();
        Result<Boolean> unionAll = new Result<>();
        ImCol<GroupJoinsWhere> queryJoins = query.getWhereJoins(!useFJ && !noExclusive, unionAll,
                                limit.hasLimit() && syntax.orderTopProblem() ? orders.keyOrderSet().mapOrder(query.properties) : SetFact.<Expr>EMPTYORDER());
        boolean union = !useFJ && queryJoins.size() >= 2 && (unionAll.result || !Settings.get().isUseFJInsteadOfUnion());
        if (union) { // сложный UNION запрос
            ImMap<V, Type> castTypes = BaseUtils.immutableCast(
                    propertyReaders.filterFnValues(new SFunctionSet<ClassReader>() {
                        public boolean contains(ClassReader element) {
                            return element instanceof Type && !(element instanceof OrderClass); // так как для упорядочивания по выражению, оно должно быть в запросе - опасный хак, но собственно ORDER оператор и есть один большой хак
                        }
                    }));
            if(exCastTypes != null)
                castTypes = exCastTypes.override(castTypes);

            String fromString = "";
            for(GroupJoinsWhere queryJoin : queryJoins) {
                boolean orderUnion = syntax.orderUnion(); // нужно чтобы фигачило внутрь orders а то многие SQL сервера не видят индексы внутри union all
                fromString = (fromString.length()==0?"":fromString+" UNION " + (unionAll.result?"ALL ":"")) + "(" + getInnerSelect(query.mapKeys, queryJoin, query.properties, params, orderUnion?orders:MapFact.<V, Boolean>EMPTYORDER(), orderUnion? limit : LimitOptions.NOLIMIT, syntax, keyNames, propertyNames, resultKeyOrder, resultPropertyOrder, castTypes, subcontext, false, mEnv, mBaseCost, mSubQueries) + ")";
                if(!orderUnion) // собственно потому как union cast'ит к первому union'у (во всяком случае postgreSQL)
                    castTypes = null;
            }

            final String alias = "UALIAS";
            AddAlias addAlias = new AddAlias(alias);
            keySelect = keyNames.mapValues(addAlias);
            propertySelect = propertyNames.mapValues(addAlias);
            from = "(" + fromString + ") "+alias;
            whereSelect = SetFact.EMPTY();
            String topString = limit.getString();
            Result<Boolean> needSources = new Result<>();
            String orderBy = Query.stringOrder(resultPropertyOrder.result, query.mapKeys.size(), compileOrders, propertySelect, syntax, needSources);
            if(needSources.result)
                select = syntax.getSelect(from, "*",  "", orderBy, "", "", topString);
            else
                select = syntax.getUnionOrder(fromString, orderBy, topString);
            areKeyValues = SetFact.EMPTY(); arePropValues = SetFact.EMPTY();
        } else {
            if(queryJoins.size()==0) { // "пустой" запрос
                keySelect = query.mapKeys.mapValues(new GetStaticValue<String>() {
                    public String getMapValue() {
                        return SQLSyntax.NULL;
                    }
                });
                propertySelect = query.properties.mapValues(new GetStaticValue<String>() {
                    public String getMapValue() {
                        return SQLSyntax.NULL;
                    }});
                from = "empty";
                whereSelect = SetFact.EMPTY();                
                areKeyValues = query.mapKeys.keys(); arePropValues = query.properties.keys(); 
            } else {
                Result<ImMap<K, String>> resultKey = new Result<>(); Result<ImMap<V, String>> resultProperty = new Result<>();
                if(queryJoins.size()==1) { // "простой" запрос
                    Result<ImCol<String>> resultWhere = new Result<>();
                    Result<ImSet<K>> resultKeyValues = new Result<>(); Result<ImSet<V>> resultPropValues= new Result<>();
                    from = fillInnerSelect(query.mapKeys, queryJoins.single(), query.properties, resultKey, resultProperty, resultWhere, params, syntax, subcontext, mEnv, mBaseCost, mSubQueries, resultKeyValues, resultPropValues);
                    whereSelect = resultWhere.result;
                    areKeyValues = resultKeyValues.result; arePropValues = resultPropValues.result;
                } else { // "сложный" запрос с full join'ами
                    from = fillFullSelect(query.mapKeys, queryJoins, query.where, query.properties, orders, limit, resultKey, resultProperty, params, syntax, subcontext, mEnv, mBaseCost, mSubQueries);
                    whereSelect = SetFact.EMPTY();
                    areKeyValues = SetFact.EMPTY(); arePropValues = SetFact.EMPTY();
                }
                keySelect = resultKey.result; propertySelect = resultProperty.result;
            }

            select = getSelect(from, keySelect, keyNames, resultKeyOrder, propertySelect, propertyNames, resultPropertyOrder, whereSelect, syntax, compileOrders, limit, noInline);
        }

        env = mEnv.finish();
        sql = new SQLQuery(select, mBaseCost.result, mSubQueries.immutable(), env, keyNames.crossJoin(keyReaders), propertyNames.crossJoin(propertyReaders), union, false);
        queryExecEnv = getTypeExecEnv(null).create(sql);
        extraEnvs = new ExtraEnvs();
        keyOrder = resultKeyOrder.result; propertyOrder = resultPropertyOrder.result;

        assert checkQuery();
    }

    // в общем случае получить AndJoinQuery под которые подходит Where
    private static ImSet<AndJoinQuery> getWhereSubSet(ImSet<AndJoinQuery> andWheres, Where where) {

        MSet<AndJoinQuery> result = SetFact.mSet();
        CheckWhere resultWhere = Where.FALSE;
        while(result.size()< andWheres.size()) {
            // ищем куда закинуть заодно считаем
            AndJoinQuery lastQuery = null;
            CheckWhere lastWhere = null;
            for(AndJoinQuery and : andWheres)
                if(!result.contains(and)) {
                    lastQuery = and;
                    lastWhere = resultWhere.orCheck(lastQuery.innerSelect.getFullWhere());
                    if(where.means(lastWhere)) {
                        result.add(lastQuery);

                        return result.immutable();
                    }
                }
            resultWhere = lastWhere;
            result.add(lastQuery);
        }
        return result.immutable();
    }

    static class InnerSelect extends CompileSource {

        public final Map<KeyExpr,String> keySelect;
        private Stack<MRevMap<String, String>> stackTranslate = new Stack<>();
        private Stack<MSet<KeyExpr>> stackUsedPendingKeys = new Stack<>();
        private Stack<Result<Boolean>> stackUsedOuterPendingJoins = new Stack<>();
        private Set<KeyExpr> pending;

        void usedJoin(JoinSelect join) {
            if(!stackUsedOuterPendingJoins.isEmpty() && mOuterPendingJoins!=null && mOuterPendingJoins.contains(join))
                stackUsedOuterPendingJoins.peek().set(true);
        }

        public String getSource(KeyExpr key) {
            String source = keySelect.get(key);
            if(source == null) {
                source = "qxas" + keySelect.size() + "nbv";
                keySelect.put(key, source);
                pending.add(key);
            }

            if(pending.contains(key)) {
                stackUsedPendingKeys.peek().add(key); // если stackUsedPendingKeys пустой, значит висячий ключ, например PREV с датой ключем и в событии
            }

            return source;
        }

        final WhereJoins whereJoins;

        public InnerJoins getInnerJoins() {
            return whereJoins.getInnerJoins();
        }

        public boolean isInner(InnerJoin join) {
            return getInnerJoins().containsAll(join);
        }

        final UpWheres<WhereJoin> upWheres;

        final SubQueryContext subcontext;
        final KeyStat keyStat;
        private final ImSet<KeyExpr> keys;

        private final MExclMap<String, SQLQuery> mSubQueries;

        public InnerSelect(ImSet<KeyExpr> keys, KeyType keyType, KeyStat keyStat, Where fullWhere, WhereJoins whereJoins, UpWheres<WhereJoin> upWheres, SQLSyntax syntax, MExclMap<String, SQLQuery> mSubQueries, MStaticExecuteEnvironment env, ImRevMap<ParseValue, String> params, SubQueryContext subcontext) {
            super(keyType, fullWhere, params, syntax, env);

            this.keys = keys;
            this.keyStat = keyStat;
            this.subcontext = subcontext;
            this.whereJoins = whereJoins;
            this.upWheres = upWheres;
            this.keySelect = new HashMap<>(); // сложное рекурсивное заполнение
            this.pending = new HashSet<>();
            this.mSubQueries = mSubQueries;
        }

        int aliasNum=0;
        MList<JoinSelect> mJoins = ListFact.mList();
        ImList<JoinSelect> joins;
        MList<String> mExplicitWheres = ListFact.mList();
        MList<String> mImplicitJoins = ListFact.mList();
        MOrderExclSet<JoinSelect> mOuterPendingJoins = SetFact.mOrderExclSet();

        boolean whereCompiling;

        private abstract class JoinSelect<I extends InnerJoin> {

            final String alias; // final
            String join; // final
            final I innerJoin;

            protected abstract ImMap<String, BaseExpr> initJoins(I innerJoin, SQLSyntax syntax);

            protected boolean isInner() {
                return InnerSelect.this.isInner(innerJoin);
            }

            protected JoinSelect(I innerJoin) {
                alias = subcontext.wrapAlias("t" + (aliasNum++));
                this.innerJoin = innerJoin;
                boolean inner = isInner();
                boolean outerPending = false;

                // здесь проблема что keySelect может рекурсивно использоваться 2 раза, поэтому сначала пробежим не по ключам
                String joinString = "";
                ImMap<String, BaseExpr> initJoins = initJoins(innerJoin, syntax);
                MExclMap<String,KeyExpr> mJoinKeys = MapFact.mExclMapMax(initJoins.size());
                for(int i=0,size=initJoins.size();i<size;i++) {
                    BaseExpr expr = initJoins.getValue(i);
                    String keySource = alias + "." + initJoins.getKey(i);
                    if(expr instanceof KeyExpr && inner)
                        mJoinKeys.exclAdd(keySource, (KeyExpr) expr);
                    else {
                        stackUsedPendingKeys.push(SetFact.<KeyExpr>mSet());
                        stackTranslate.push(MapFact.<String, String>mRevMap());
                        stackUsedOuterPendingJoins.push(new Result<Boolean>());
                        String exprJoin = keySource + "=" + expr.getSource(InnerSelect.this);
                        ImSet<KeyExpr> usedPendingKeys = stackUsedPendingKeys.pop().immutable();
                        ImRevMap<String, String> translate = stackTranslate.pop().immutableRev(); // их надо перетранслировать
                        Result<Boolean> usedOuterPending = stackUsedOuterPendingJoins.pop();
                        exprJoin = translatePlainParam(exprJoin, translate);

                        boolean havePending = usedPendingKeys.size() > translate.size();
                        if(inner && (havePending || usedOuterPending.result != null)) { // какие-то ключи еще зависли, придется в implicitJoins закидывать
                            assert !havePending || usedPendingKeys.intersect(SetFact.fromJavaSet(pending));
                            mImplicitJoins.add(exprJoin);
                        } else { // можно explicit join делать, перетранслировав usedPending
                            joinString = (joinString.length() == 0 ? "" : joinString + " AND ") + exprJoin;
                            if(havePending)
                                outerPending = true;
                        }
                    }
                }
                ImMap<String, KeyExpr> joinKeys = mJoinKeys.immutable();

                for(int i=0,size=joinKeys.size();i<size;i++) { // дозаполним ключи
                    String keyString = joinKeys.getKey(i); KeyExpr keyExpr = joinKeys.getValue(i);
                    String keySource = keySelect.get(keyExpr);
                    if(keySource==null || pending.remove(keyExpr)) {
                        if(keySource!=null) { // нашли pending ключ, проставляем во все implicit joins
                            if(!stackUsedPendingKeys.isEmpty() && stackUsedPendingKeys.peek().contains(keyExpr)) // если ключ был использован, ну очень редкий случай
                                stackTranslate.peek().revAdd(keySource, keyString);
                            for(int j=0,sizeJ=mImplicitJoins.size();j<sizeJ;j++)
                                mImplicitJoins.set(j, mImplicitJoins.get(j).replace(keySource, keyString));
                            for(int j=0,sizeJ=mExplicitWheres.size();j<sizeJ;j++)
                                mExplicitWheres.set(j, mExplicitWheres.get(j).replace(keySource, keyString));
                            for(int j=0,sizeJ=mOuterPendingJoins.size();j<sizeJ;j++) {
                                JoinSelect pendingJoin = mOuterPendingJoins.get(j);
                                pendingJoin.join = pendingJoin.join.replace(keySource, keyString);
                            }
                        }
                        keySelect.put(keyExpr, keyString);
                    } else
                        joinString = (joinString.length()==0?"":joinString+" AND ") + keyString + "=" + keySource;
                }
                join = joinString;

                if(outerPending)
                    mOuterPendingJoins.exclAdd(this);
                else
                    mJoins.add(this);
            }

            public abstract String getSource();

            protected abstract Where getInnerWhere(); // assert что isInner
        }

        private Stat baseStat;
        @IdentityQuickLazy
        private boolean isOptAntiJoin(InnerJoin innerJoin) {
            assert !isInner(innerJoin);
            StatType type = StatType.ANTIJOIN;
            if(baseStat == null)
                baseStat = whereJoins.getStatKeys(keys, keyStat, type).getRows();
            // тут есть 2 стратегии : оптимистичная и пессимистичная
            // оптимистичная - если статистика остальные предикатов <= статистики этого join'а, то расчитываем что СУБД так их и выполнит, а потом будет LEFT JOIN делать и тогда уменьшение статистики будет релевантным
            return whereJoins.and(new WhereJoins(innerJoin)).getStatKeys(keys, keyStat, type).getRows().less(baseStat);
            // пессимистичная - дополнительно смотреть что если статистика join'а маленькая, потому как СУБД никто не мешает взять один из больших предикатов и нарушить верхнее предположение
        }

        public void fillInnerJoins(Result<Cost> mBaseCost, MCol<String> whereSelect) { // заполним Inner Joins, чтобы keySelect'ы были
            stackUsedPendingKeys.push(SetFact.<KeyExpr>mSet()); stackTranslate.push(MapFact.<String, String>mRevMap()); stackUsedOuterPendingJoins.push(new Result<Boolean>());

            innerWhere = whereJoins.fillInnerJoins(upWheres, mExplicitWheres, mBaseCost, this, keys, keyStat);

            MSet<KeyExpr> usedKeys = stackUsedPendingKeys.pop();
            MRevMap<String, String> translate = stackTranslate.pop();
            stackUsedOuterPendingJoins.pop();
            assert usedKeys.size() == translate.size();

            whereSelect.addAll(mExplicitWheres.immutableList().getCol());
            whereSelect.addAll(mImplicitJoins.immutableList().getCol());
            mJoins.addAll(mOuterPendingJoins.immutableOrder());
            assert pending.isEmpty();
            mExplicitWheres = null;
            mImplicitJoins = null;
            mOuterPendingJoins = null;
        }

        private Where innerWhere;
        // получает условия следующие из логики inner join'ов SQL
        private Where getInnerWhere() {
            Where result = innerWhere;
            for(InnerJoin innerJoin : getInnerJoins().it()) {
                JoinSelect joinSelect = getJoinSelect(innerJoin);
                if(joinSelect!=null)
                    result = result.and(joinSelect.getInnerWhere());
            }
            return result;
        }

        public String getFrom(Where where, MCol<String> whereSelect) {
            where.getSource(this);

            joins = mJoins.immutableList();
            mJoins = null;
            for(JoinSelect join : joins)
                if(join instanceof QuerySelect)
                    ((QuerySelect)join).finalIm();

            whereCompiling = true;
            whereSelect.add(where.followFalse(getInnerWhere().not()).getSource(this));
            whereCompiling = false;

            if(joins.isEmpty()) return "dumb";

            String from;
            Iterator<JoinSelect> ij = joins.iterator();
            JoinSelect first = ij.next();
            if(first.isInner()) {
                from = first.getSource() + " " + first.alias;
                if(!(first.join.length()==0))
                    whereSelect.add(first.join);
            } else {
                from = "dumb";
                ij = joins.iterator();
            }

            while(ij.hasNext()) {
                JoinSelect join = ij.next();
                from = from + (join.isInner() ?"":" LEFT")+" JOIN " + join.getSource() + " " + join.alias  + " ON " + (join.join.length()==0?Where.TRUE_STRING:join.join);
            }

            return from;
        }

        private class TableSelect extends JoinSelect<Table.Join> {
            private String source;

            protected ImMap<String, BaseExpr> initJoins(Table.Join table, final SQLSyntax syntax) {
                return table.joins.mapKeys(new GetValue<String, KeyField>() {
                    public String getMapValue(KeyField value) {
                        return value.getName(syntax);
                    }});
            }

            TableSelect(Table.Join join) {
                super(join);
                this.source = join.getQueryName(InnerSelect.this);
            }

            public String getSource() {
                return innerJoin.getQueryName(InnerSelect.this);
            }

            protected Where getInnerWhere() {
                return innerJoin.getWhere();
            }
        }

        final MAddExclMap<Table.Join, TableSelect> tables = MapFact.mAddExclMap();
        private String getAlias(Table.Join table) {
            TableSelect join = tables.get(table);
            if(join==null) {
                join = new TableSelect(table);
                tables.exclAdd(table,join);
            }
            usedJoin(join);
            return join.alias;
        }

        public String getSource(Table.Join.Expr expr) {
            return getAlias(expr.getInnerJoin())+"."+expr.property.getName(syntax);
        }
        public String getSource(Table.Join.IsIn where) {
            return getAlias(where.getJoin()) + "." + where.getFirstKey(syntax) + " IS NOT NULL";
        }
        public String getSource(IsClassExpr classExpr) {
            InnerExpr joinExpr = classExpr.getJoinExpr();
            if(joinExpr instanceof Table.Join.Expr)
                return getSource((Table.Join.Expr)joinExpr);
            else
                return getSource((SubQueryExpr)joinExpr);
        }

        private abstract class QuerySelect<K extends Expr, I extends OuterContext<I>,J extends QueryJoin<K,?,?,?>,E extends QueryExpr<K,I,J,?,?>> extends JoinSelect<J> {
            ImRevMap<String, K> group;

            protected ImMap<String, BaseExpr> initJoins(J groupJoin, SQLSyntax syntax) {
                group = groupJoin.group.mapRevValues(new GenNameIndex("k", "")).reverse();
                return group.join(groupJoin.group);
            }

            QuerySelect(J groupJoin) {
                super(groupJoin);
            }

            private MRevMap<I,String> mQueries = MapFact.mRevMap();
            private MExclMap<I,E> mExprs = MapFact.mExclMap(); // нужен для innerWhere и классовой информации, query транслированный -> в общее выражение

            public ImRevMap<String, I> queries;
            protected ImMap<I,E> exprs; // нужен для innerWhere и классовой информации, query транслированный -> в общее выражение
            public void finalIm() {
                queries = mQueries.immutableRev().reverse();
                mQueries = null;
                exprs = mExprs.immutable();
                mExprs = null;
            }

            public String add(I query,E expr) {
                if(mQueries!=null) { // из-за getInnerWhere во from'е
                    String name = mQueries.get(query);
                    if(name==null) {
                        name = "e"+ mQueries.size();
                        mQueries.revAdd(query, name);
                        mExprs.exclAdd(query, expr);
                    }
                    return alias + "." + name;
                } else
                    return alias + "." + queries.reverse().get(query);
            }

            protected boolean isEmptySelect(Where groupWhere, ImSet<KeyExpr> keys) {
                return groupWhere.pack().getPackWhereJoins(!syntax.useFJ() && !Settings.get().isNoExclusiveCompile(), keys, SetFact.<Expr>EMPTYORDER()).first.isEmpty();
            }

            protected SQLQuery getEmptySelect(final Where groupWhere) {
                return getSQLQuery(null, Cost.MIN, MapFact.<String, SQLQuery>EMPTY(), StaticExecuteEnvironmentImpl.mEnv(), groupWhere, false);
            }

            protected SQLQuery getSQLQuery(String select, Cost baseCost, ImMap<String, SQLQuery> subQueries, final MStaticExecuteEnvironment mSubEnv, final Where innerWhere, boolean recursionFunction) {
                ImMap<String, Type> keyTypes = group.mapValues(new GetValue<Type, K>() {
                    public Type getMapValue(K value) {
                        return value.getType(innerWhere);
                    }});
                ImMap<String, Type> propertyTypes = queries.mapValues(new GetValue<Type, I>() {
                    public Type getMapValue(I value) {
                        return exprs.get(value).getType();
                    }});
                if(select == null) {
                    GetValue<String, Type> nullGetter = new GetValue<String, Type>() {
                        public String getMapValue(Type value) {
                            return value.getCast(SQLSyntax.NULL, syntax, mSubEnv);
                        }};
                    ImMap<String, String> keySelect = keyTypes.mapValues(nullGetter);
                    ImMap<String, String> propertySelect = propertyTypes.mapValues(nullGetter);
                    select = "(" + syntax.getSelect("empty", SQLSession.stringExpr(keySelect, propertySelect), "", "", "", "", "") + ")";
                }
                return new SQLQuery(select, baseCost, subQueries, mSubEnv.finish(), keyTypes, propertyTypes, false, recursionFunction);
            }

            // чтобы разбить рекурсию
            protected boolean checkRecursivePush(Where fullWhere) {
                return false;
//                return fullWhere.getComplexity(false) > InnerSelect.this.fullWhere.getComplexity(false); // не проталкиваем если, полученная сложность больше сложности всего запроса
            }

            protected Where pushWhere(Where groupWhere, ImSet<KeyExpr> keys, ImMap<K, BaseExpr> innerJoins, StatKeys<K> statKeys, Result<SQLQuery> empty) {
                Where fullWhere = groupWhere;
                Where pushWhere;
                if((pushWhere = whereJoins.getPushWhere(innerJoins, upWheres, innerJoin, isInner(), keyStat, fullWhere, statKeys))!=null) // проталкивание предиката
                    fullWhere = fullWhere.and(pushWhere);
                if(isEmptySelect(fullWhere, keys)) { // может быть когда проталкивается верхнее условие, а внутри есть NOT оно же
                    // getKeyEquals - для надежности, так как идет перетранслирование ключей и условие может стать false, а это критично, так как в emptySelect есть cast'ы, а скажем в GroupSelect, может придти EMPTY, ключи NULL и "Class Cast'ы" будут
                    empty.set(getEmptySelect(groupWhere));
                    return null;
                }
                if(pushWhere!=null && checkRecursivePush(fullWhere))
                    fullWhere = groupWhere;
                return fullWhere;
            }


            public String getSource() {
                SQLQuery query = getSQLQuery();
                env.add(query.getEnv());
                if(Settings.get().isDisableCompiledSubQueries())
                    return query.getString();

                String sqName = subcontext.wrapSiblingSubQuery("jdfkjsd" + mSubQueries.size() + "ref");
                mSubQueries.exclAdd(sqName, query);
                return sqName;
            }

            protected abstract SQLQuery getSQLQuery();

        }

        private class GroupSelect extends QuerySelect<Expr, GroupExpr.Query,GroupJoin,GroupExpr> {

            final ImSet<KeyExpr> keys;

            GroupSelect(GroupJoin groupJoin) {
                super(groupJoin);
                keys = BaseUtils.immutableCast(groupJoin.getInnerKeys());
            }

            public SQLQuery getSQLQuery() {

                Where exprWhere = Where.FALSE;
                MSet<Expr> mQueryExprs = SetFact.mSet(); // так как может одновременно и SUM и MAX нужен
                for(GroupExpr.Query query : queries.valueIt()) {
                    mQueryExprs.addAll(query.getExprs());
                    exprWhere = exprWhere.or(query.getWhere());
                }
                ImSet<Expr> queryExprs = group.values().toSet().merge(mQueryExprs.immutable());

                Where groupWhere = exprWhere.and(Expr.getWhere(group));

                Result<SQLQuery> empty = new Result<>();
                groupWhere = pushWhere(groupWhere, keys, innerJoin.getJoins(), innerJoin.getInnerStatKeys(StatType.PUSH_INNER()), empty);
                if(groupWhere==null)
                    return empty.result;

                final MStaticExecuteEnvironment mSubEnv = StaticExecuteEnvironmentImpl.mEnv();
                final Result<ImCol<String>> whereSelect = new Result<>(); // проверить crossJoin
                final Result<ImMap<Expr,String>> fromPropertySelect = new Result<>();
                final Result<ImMap<String, SQLQuery>> subQueries = new Result<>();
                final Query<KeyExpr, Expr> query = new Query<>(keys.toRevMap(), queryExprs.toMap(), groupWhere);
                final CompiledQuery<KeyExpr, Expr> compiled = query.compile(new CompileOptions<Expr>(syntax, subcontext));
                String fromSelect = compiled.fillSelect(new Result<ImMap<KeyExpr, String>>(), fromPropertySelect, whereSelect, subQueries, params, mSubEnv);

                ImMap<String, String> keySelect = group.join(fromPropertySelect.result);
                ImMap<String, String> propertySelect = queries.mapValues(new GetValue<String, GroupExpr.Query>() {
                    public String getMapValue(GroupExpr.Query value) {
                        return value.getSource(fromPropertySelect.result, compiled.getMapPropertyReaders(), query, syntax, mSubEnv, exprs.get(value).getType());
                    }});
                ImSet<String> areKeyValues = group.filterValues(compiled.arePropValues).keys();

                ImCol<String> havingSelect;
                if(isSingle(innerJoin))
                    havingSelect = SetFact.singleton(propertySelect.get(queries.singleKey()) + " IS NOT NULL");
                else
                    havingSelect = SetFact.EMPTY();
                return getSQLQuery("(" + getGroupSelect(fromSelect, keySelect, propertySelect, whereSelect.result, havingSelect, areKeyValues) + ")", compiled.sql.baseCost, subQueries.result, mSubEnv, groupWhere, false);
            }

            protected Where getInnerWhere() {
                // бежим по всем exprs'ам и проверяем что нет AggrType'а
                Where result = Where.TRUE;
                for(int i=0,size=exprs.size();i<size;i++) {
                    if(exprs.getKey(i).type.canBeNull())
                        return Where.TRUE;
                    result = result.or(exprs.getValue(i).getWhere());
                }
                return result;
            }
        }

        private String getGroupBy(ImCol<String> keySelect) {
            return BaseUtils.evl(((ImList) (syntax.supportGroupNumbers() ? ListFact.consecutiveList(keySelect.size()) : keySelect.toList())).toString(","), "3+2");
        }

        private class PartitionSelect extends QuerySelect<KeyExpr, PartitionExpr.Query,PartitionJoin,PartitionExpr> {

            final ImMap<KeyExpr,BaseExpr> mapKeys;
            private PartitionSelect(PartitionJoin partitionJoin) {
                super(partitionJoin);
                mapKeys = partitionJoin.group;
            }

            public SQLQuery getSQLQuery() {

                MSet<Expr> mQueryExprs = SetFact.mSet();
                for(PartitionExpr.Query query : queries.valueIt())
                    mQueryExprs.addAll(query.getExprs());
                ImSet<Expr> queryExprs = mQueryExprs.immutable();

                Where innerWhere = innerJoin.getWhere();

                ImMap<KeyExpr, BaseExpr> joinMap = innerJoin.getJoins();
                joinMap = joinMap.filterIncl(BaseUtils.<ImSet<KeyExpr>>immutableCast(AbstractOuterContext.getOuterSetKeys(innerJoin.getPartitions())));

                Result<SQLQuery> empty = new Result<>();
                innerWhere = pushWhere(innerWhere, group.valuesSet(), joinMap, innerJoin.getInnerStatKeys(StatType.PUSH_INNER()), empty);
                if(innerWhere == null)
                    return empty.result;

                MStaticExecuteEnvironment mSubEnv = StaticExecuteEnvironmentImpl.mEnv();
                Result<ImMap<String,String>> keySelect = new Result<>();
                Result<ImMap<Expr,String>> fromPropertySelect = new Result<>();
                Result<ImCol<String>> whereSelect = new Result<>(); // проверить crossJoin
                Result<ImMap<String, SQLQuery>> subQueries = new Result<>();
                Query<String, Expr> subQuery = new Query<>(group, queryExprs.toMap(), innerWhere);
                CompiledQuery<String, Expr> compiledSubQuery = subQuery.compile(new CompileOptions<Expr>(syntax, subcontext));
                String fromSelect = compiledSubQuery.fillSelect(keySelect, fromPropertySelect, whereSelect, subQueries, params, mSubEnv);

                // обработка multi-level order'ов
                MExclMap<PartitionToken, String> mTokens = MapFact.<PartitionToken, String>mExclMap();
                ImRevValueMap<String, PartitionCalc> mResultNames = queries.mapItRevValues();// последействие (mTokens)
                for(int i=0,size=queries.size();i<size;i++) {
                    PartitionExpr.Query query = queries.getValue(i);
                    PartitionCalc calc = query.type.createAggr(mTokens,
                            query.exprs.mapList(fromPropertySelect.result),
                            subQuery.getCompileOrders(query.orders).map(fromPropertySelect.result),
                            query.partitions.map(fromPropertySelect.result).toSet(), syntax, query.getType(), mSubEnv);
                    mResultNames.mapValue(i, calc);
                }
                final ImRevMap<PartitionCalc, String> resultNames = mResultNames.immutableValueRev().reverse();
                ImMap<PartitionToken, String> tokens = mTokens.immutable();

                for(int i=1;;i++) {
                    MSet<PartitionToken> mNext = SetFact.mSet();
                    boolean last = true;
                    for(int j=0,size=tokens.size();j<size;j++) {
                        PartitionToken token = tokens.getKey(j);
                        ImSet<PartitionCalc> tokenNext = token.getNext();
                        boolean neededUp = tokenNext.isEmpty(); // верхний, надо протаскивать
                        last = true;
                        for(PartitionCalc usedToken : tokenNext) {
                            if(usedToken.getLevel() >= i) {
                                if(usedToken.getLevel() == i) // если тот же уровень
                                    mNext.add(usedToken);
                                else
                                    neededUp = true;
                                last = false;
                            }
                        }
                        if(neededUp)
                            mNext.add(token);
                    }
                    ImSet<PartitionToken> next = mNext.immutable();
                    
                    if(last)
                        return getSQLQuery(fromSelect, compiledSubQuery.sql.baseCost, subQueries.result, mSubEnv, innerWhere, false);

                    ImRevMap<PartitionToken, String> nextTokens = next.mapRevValues(new GetIndexValue<String, PartitionToken>() {
                        public String getMapValue(int i, PartitionToken token) {
                            return token.getNext().isEmpty() ? resultNames.get((PartitionCalc) token) : "ne" + i; // если верхний то нужно с нормальным именем тащить
                        }});
                    final ImMap<PartitionToken, String> ftokens = tokens;
                    ImMap<String, String> propertySelect = nextTokens.reverse().mapValues(new GetValue<String, PartitionToken>() {
                        public String getMapValue(PartitionToken value) {
//                            Type resultType = null; String resultName;
//                            if(value instanceof PartitionCalc && (resultName = resultNames.get((PartitionCalc) value))!=null)
//                                resultType = queries.get(resultName).getType();
                            return value.getSource(ftokens, syntax);
                        }});

                    fromSelect = "(" + syntax.getSelect(fromSelect + (i>1?" q":""), SQLSession.stringExpr(keySelect.result,propertySelect),
                        (i>1?"":whereSelect.result.toString(" AND ")),"","","", "") + ")";
                    keySelect.set(keySelect.result.keys().toMap()); // ключи просто превращаем в имена
                    tokens = nextTokens;
                }
            }

            protected Where getInnerWhere() {
                Where result = Where.TRUE;
                for(int i=0,size=exprs.size();i<size;i++)
                    if(!exprs.getKey(i).type.canBeNull())
                        result = result.and(exprs.getValue(i).getWhere());
                return result;
            }
        }

        private class SubQuerySelect extends QuerySelect<KeyExpr,Expr,SubQueryJoin,SubQueryExpr> {

            final ImMap<KeyExpr,BaseExpr> mapKeys;
            private SubQuerySelect(SubQueryJoin subQueryJoin) {
                super(subQueryJoin);
                mapKeys = subQueryJoin.group;
            }

            public SQLQuery getSQLQuery() {

                Where innerWhere = innerJoin.getWhere();

                Result<SQLQuery> empty = new Result<>();
                innerWhere = pushWhere(innerWhere, group.valuesSet(), innerJoin.getJoins(), innerJoin.getInnerStatKeys(StatType.PUSH_INNER()), empty);
                if(innerWhere==null)
                    return empty.result;

                MStaticExecuteEnvironment mSubEnv = StaticExecuteEnvironmentImpl.mEnv();
                Result<ImMap<String, String>> keySelect = new Result<>();
                Result<ImMap<String, String>> propertySelect = new Result<>();
                Result<ImCol<String>> whereSelect = new Result<>();
                Result<ImMap<String, SQLQuery>> subQueries = new Result<>();
                CompiledQuery<String, String> compiledQuery = new Query<>(group, queries, innerWhere).compile(new CompileOptions<String>(syntax, subcontext));
                String fromSelect = compiledQuery.fillSelect(keySelect, propertySelect, whereSelect, subQueries, params, mSubEnv);
                return getSQLQuery("(" + syntax.getSelect(fromSelect, SQLSession.stringExpr(keySelect.result,propertySelect.result),
                    whereSelect.result.toString(" AND "),"","","", "") + ")", compiledQuery.sql.baseCost, subQueries.result, mSubEnv, innerWhere, false);
            }

            protected Where getInnerWhere() {
                Where result = Where.TRUE;
                for(int i=0,size=exprs.size();i<size;i++)
                    result = result.and(exprs.getValue(i).getWhere());
                return result;
            }
        }


        protected String getGroupSelect(String fromSelect, ImOrderMap<String, String> keySelect, ImOrderMap<String, String> propertySelect, ImCol<String> whereSelect, ImCol<String> havingSelect, final ImSet<String> areKeyValues) {
            String groupBy;
            boolean supportGroupNumbers = syntax.supportGroupNumbers();
            ImOrderMap<String, String> fixedKeySelect = keySelect;
            if(!areKeyValues.isEmpty() && !supportGroupNumbers) {
                keySelect = keySelect.mapOrderValues(new GetKeyValue<String, String, String>() {
                    public String getMapValue(String key, String value) {
                        if(areKeyValues.contains(key))
                            return syntax.getAnyValueFunc() + "(" + value + ")";
                        return value;
                    }});
                fixedKeySelect = keySelect.filterOrder(new NotFunctionSet<>(areKeyValues));
            }
            if(fixedKeySelect.isEmpty()) {
                if(syntax.supportGroupSingleValue())
                    groupBy = "3+2";
                else {
                    groupBy = "";
                    havingSelect = havingSelect.addCol("COUNT(*) > 0");
                }
            } else
                groupBy =  BaseUtils.evl(((ImList) (supportGroupNumbers ? ListFact.consecutiveList(keySelect.size()) : fixedKeySelect.values().toList())).toString(","), "3+2");
            return syntax.getSelect(fromSelect, SQLSession.stringExpr(keySelect, propertySelect), whereSelect.toString(" AND "), "", groupBy, havingSelect.toString(" AND "), "");
        }
        protected String getGroupSelect(String fromSelect, ImMap<String, String> keySelect, ImMap<String, String> propertySelect, ImCol<String> whereSelect, ImCol<String> havingSelect, ImSet<String> areKeyValues) {
            return getGroupSelect(fromSelect, keySelect.toOrderMap(), propertySelect.toOrderMap(), whereSelect, havingSelect, areKeyValues);
        }

        private class RecursiveSelect extends QuerySelect<KeyExpr,RecursiveExpr.Query,RecursiveJoin,RecursiveExpr> {
            private RecursiveSelect(RecursiveJoin recJoin) {
                super(recJoin);
            }

            private String getSelect(ImRevMap<String, KeyExpr> keys, ImMap<String, Expr> props, final ImMap<String, Type> columnTypes, Where where, Result<ImOrderSet<String>> keyOrder, Result<ImOrderSet<String>> propOrder, boolean useRecursionFunction, boolean recursive, ImRevMap<ParseValue, String> params, SubQueryContext subcontext, Result<Cost> baseCost, Result<ImMap<String, SQLQuery>> subQueries, final MStaticExecuteEnvironment env) {
                ImRevMap<String, KeyExpr> itKeys = innerJoin.getMapIterate().mapRevKeys(new GenNameIndex("pv_", ""));

                Result<ImMap<String, String>> keySelect = new Result<>();
                Result<ImMap<String, String>> propertySelect = new Result<>();
                Result<ImCol<String>> whereSelect = new Result<>();
                Result<ImMap<String, SQLQuery>> innerSubQueries = new Result<>();
                CompiledQuery<String, String> compiledQuery = new Query<>(keys.addRevExcl(itKeys), props, where).compile(new CompileOptions<String>(syntax, subcontext, recursive && !useRecursionFunction));
                String fromSelect = compiledQuery.fillSelect(keySelect, propertySelect, whereSelect, innerSubQueries, params, env);

                ImMap<String, SQLQuery> compiledSubQueries = innerSubQueries.result;
                if(subQueries.result != null) // по аналогии с subEnv
                    compiledSubQueries = compiledSubQueries.addExcl(subQueries.result);
                subQueries.set(compiledSubQueries);

                Cost compiledBaseCost = compiledQuery.sql.baseCost;
                if(baseCost.result != null)
                    compiledBaseCost = compiledBaseCost.or(baseCost.result);
                baseCost.set(compiledBaseCost);

                ImOrderMap<String, String> orderKeySelect = SQLSession.mapNames(keySelect.result.filterIncl(keys.keys()), keyOrder);
                ImOrderMap<String, String> orderPropertySelect = SQLSession.mapNames(propertySelect.result, propOrder);
                ImSet<String> areKeyValues = compiledQuery.areKeyValues.filter(keys.keys());

                if(useRecursionFunction) {
                    ImOrderMap<String, String> orderCastKeySelect = orderKeySelect.mapOrderValues(new GetKeyValue<String, String, String>() {
                        public String getMapValue(String key, String value) {
                            return columnTypes.get(key).getCast(value, syntax, env);
                        }});
                    ImOrderMap<String, String> orderGroupPropertySelect = orderPropertySelect.mapOrderValues(new GetKeyValue<String, String, String>() {
                        public String getMapValue(String key, String value) {
                            Type type = columnTypes.get(key);
                            return type.getCast((type instanceof ArrayClass ? GroupType.AGGAR_SETADD : GroupType.SUM).getSource(
                                    ListFact.<String>singleton(value), ListFact.<ClassReader>singleton(type), MapFact.<String, CompileOrder>EMPTYORDER(), type, syntax, env), syntax, env);
                        }});
                    return getGroupSelect(fromSelect, orderCastKeySelect, orderGroupPropertySelect, whereSelect.result, SetFact.<String>EMPTY(), areKeyValues);
                } else
                    return syntax.getSelect(fromSelect, SQLSession.stringExpr(orderKeySelect, orderPropertySelect), whereSelect.result.toString(" AND "),"","","", "");
            }

            public SQLQuery getParamSource(final boolean useRecursionFunction, final boolean wrapStep) {
                ImRevMap<KeyExpr, KeyExpr> mapIterate = innerJoin.getMapIterate();

                Where initialWhere = innerJoin.getInitialWhere();
                final Where baseInitialWhere = initialWhere;

                final boolean isLogical = innerJoin.isLogical();
                final boolean cyclePossible = innerJoin.isCyclePossible();

                boolean single = isSingle(innerJoin);

                String rowPath = "qwpather";

                ImMap<String, Type> propTypes;
                final MStaticExecuteEnvironment mSubEnv = StaticExecuteEnvironmentImpl.mEnv();

                ImMap<String, String> propertySelect;
                if(isLogical) {
                    propTypes = MapFact.EMPTY();
                    propertySelect = queries.mapValues(new GetStaticValue<String>() {
                        public String getMapValue() {
                            return syntax.getBitString(true);
                        }});
                } else {
                    propTypes = queries.mapValues(new GetValue<Type, RecursiveExpr.Query>() {
                        public Type getMapValue(RecursiveExpr.Query value) {
                            return value.getType(); // тут возможно baseInitialWhere надо
                        }});
                    propertySelect = queries.mapValues(new GetKeyValue<String, String, RecursiveExpr.Query>() {
                        public String getMapValue(String key, RecursiveExpr.Query value) {
                            return GroupType.SUM.getSource(ListFact.singleton(key), null, MapFact.<String, CompileOrder>EMPTYORDER(), value.getType(), syntax, mSubEnv);
                        }});
                }

                Expr rowKeys = null; ArrayClass rowType = null; Expr rowSource = null;
                final boolean needRow = cyclePossible && (!isLogical || useRecursionFunction);
                if(needRow) {
                    rowKeys = ConcatenateExpr.create(mapIterate.keys().toOrderSet());
                    rowType = ArrayClass.get(rowKeys.getType(innerJoin.getClassWhere())); // classWhere а не initialWhere, чтобы общий тип был и не было проблем с cast'ом ConcatenateType'ов
                    propTypes = propTypes.addExcl(rowPath, rowType);

                    rowSource = FormulaExpr.createCustomFormula(syntax.getArrayConstructor("prm1", rowType, mSubEnv), rowType, rowKeys); // баг сервера, с какого-то бодуна ARRAY[char(8)] дает text[]
                }

                // проталкивание
                ImMap<KeyExpr, BaseExpr> staticGroup = innerJoin.getJoins().remove(mapIterate.keys());
                Result<SQLQuery> empty = new Result<>();
                initialWhere = pushWhere(initialWhere, staticGroup.keys(), staticGroup, initialWhere.getStatKeys(staticGroup.keys(), StatType.PUSH_INNER()), empty);
                if(initialWhere==null)
                    return empty.result;

                // чтение params (outer / inner и типов)
                boolean noDynamicSQL = syntax.noDynamicSQL();

                String outerParams = null;
                ImRevMap<ParseValue, String> innerParams;
                ImList<FunctionType> types = null;
                if(useRecursionFunction) {
                    ImSet<OuterContext> outerContext = SetFact.<OuterContext>merge(queries.valuesSet(), initialWhere);
                    ImSet<ParseValue> values = SetFact.addExclSet(AbstractOuterContext.getOuterColValues(outerContext), AbstractOuterContext.getOuterStaticValues(outerContext)); // не static values
                    outerParams = "";
                    ImRevValueMap<ParseValue, String> mvInnerParams = values.mapItRevValues(); // "совместная" обработка / последействие
                    MList<FunctionType> mParamTypes = ListFact.mListMax(values.size());
                    for(int i=0,size=values.size();i<size;i++) {
                        ParseValue value = values.get(i);
                        String paramValue = params.get(value);
                        if(!value.getParseInterface().isSafeString() || (noDynamicSQL && !(value instanceof StaticValueExpr))) {
                            outerParams = (outerParams.length() == 0 ? "" : outerParams + "," ) + paramValue;
                            mParamTypes.add(value.getFunctionType());
                            paramValue = syntax.getParamUsage(mParamTypes.size());
                        } else
                            mSubEnv.addNoPrepare();
                        mvInnerParams.mapValue(i, paramValue);
                    }
                    innerParams = mvInnerParams.immutableValueRev();
                    types = mParamTypes.immutableList();
                } else
                    innerParams = params;

                RecursiveJoin tableInnerJoin = innerJoin;
                if(!BaseUtils.hashEquals(initialWhere, baseInitialWhere)) // проверка на hashEquals - оптимизация, само такое проталкивание нужно чтобы у RecursiveTable - статистика была правильной
                    tableInnerJoin = new RecursiveJoin(innerJoin, initialWhere);

                ImRevMap<String, KeyExpr> recKeys = tableInnerJoin.genKeyNames();
                ImMap<String, Type> columnTypes = propTypes.addExcl(recKeys.mapValues(new GetValue<Type, KeyExpr>() {
                    public Type getMapValue(KeyExpr value) {
                        return value.getType(baseInitialWhere);
                    }
                }));

                SubQueryContext pushContext = subcontext.pushRecursion();// чтобы имена не пересекались
                
                Result<ImOrderSet<String>> keyOrder = new Result<>(); Result<ImOrderSet<String>> propOrder = new Result<>();
                Result<ImMap<String, SQLQuery>> rSubQueries = new Result<>();
                Result<Cost> baseCost = new Result<>();

                // INIT

                String initialSelect = getInitialSelect(initialWhere, recKeys, columnTypes, innerParams, useRecursionFunction, isLogical, pushContext, needRow, rowPath, rowSource, keyOrder, propOrder, mSubEnv, rSubQueries, baseCost);

                // STEP

                if(!Settings.get().isDisableCompiledSubQueries())
                    pushContext = pushContext.pushSiblingSubQuery();

                String recName = subcontext.wrapRecursion("rectable");

                Result<ImMap<String, SQLQuery>> stepSubQueries = new Result<>();
                String stepSelect = getStepSelect(tableInnerJoin, wrapStep, null, recName, recKeys, propTypes, columnTypes, innerParams, useRecursionFunction, pushContext, isLogical, needRow, rowPath, rowKeys, rowType, rowSource, keyOrder, propOrder, mSubEnv, stepSubQueries, baseCost);
                rSubQueries.set(rSubQueries.result.addExcl(stepSubQueries.result));

                int smallLimit = 0;
                String stepSmallSelect = "";
                if(useRecursionFunction) {
                    int adjustCount = Settings.get().getAdjustRecursionStat();
                    Stat adjustStat = new Stat(adjustCount);
                    if (adjustStat.less(tableInnerJoin.getStatKeys(StatType.ADJUST_RECURSION).getRows())) { // если статистика
                        // выполняем с тем же контекстом чтобы проверить протолкнется ли такой предикат или нет (одновременно с самим запросом не получилось бы из-за подзапросов)
                        Result<ImMap<String, SQLQuery>> smallSubQueries = new Result<>();
                        stepSmallSelect = getStepSelect(tableInnerJoin, wrapStep, adjustStat, recName, recKeys, propTypes, columnTypes, innerParams, useRecursionFunction, pushContext, isLogical, needRow, rowPath, rowKeys, rowType, rowSource, keyOrder, propOrder, StaticExecuteEnvironmentImpl.mEnv(), smallSubQueries, new Result<Cost>());
                        if(BaseUtils.hashEquals(stepSmallSelect, stepSelect) && BaseUtils.hashEquals(smallSubQueries.result, stepSubQueries.result)) { // в env'ы записываем только если протолкнулось
                            stepSmallSelect = "";
                        } else {
                            smallLimit = adjustCount;

                            if(!Settings.get().isDisableCompiledSubQueries())
                                pushContext = pushContext.pushSiblingSubQuery();

                            stepSmallSelect = getStepSelect(tableInnerJoin, wrapStep, adjustStat, recName, recKeys, propTypes, columnTypes, innerParams, useRecursionFunction, pushContext, isLogical, needRow, rowPath, rowKeys, rowType, rowSource, keyOrder, propOrder, mSubEnv, rSubQueries, baseCost);
                        }
                    }
                }

                // RESULT

                ImOrderSet<String> columnOrder = keyOrder.result.addOrderExcl(propOrder.result);
                ImMap<String, SQLQuery> subQueries = rSubQueries.result;

                ImCol<String> havingSelect;
                if(single)
                    havingSelect = SetFact.singleton(propertySelect.get(queries.singleKey()) + " IS NOT NULL");
                else
                    havingSelect = SetFact.EMPTY();

                ImMap<String, String> keySelect = group.crossValuesRev(recKeys);
                mSubEnv.addVolatileStats();
                String select;
                if(useRecursionFunction) {
                    mSubEnv.addNoReadOnly();
                    String fieldDeclare = Field.getDeclare(columnOrder.mapOrderMap(columnTypes), syntax, mSubEnv);
                    select = getGroupSelect(syntax.getRecursion(types, recName, initialSelect, stepSelect, stepSmallSelect, smallLimit, fieldDeclare, outerParams, mSubEnv),
                            keySelect, propertySelect, SetFact.<String>EMPTY(), havingSelect, SetFact.<String>EMPTY());
                } else {
                    if(SQLQuery.countMatches(stepSelect, recName, subQueries) > 1) // почти у всех SQL серверов ограничение что не больше 2-х раз CTE можно использовать
                        return null;
                    String recursiveWith = "WITH RECURSIVE " + recName + "(" + columnOrder.toString(",") + ") AS ((" + initialSelect +
                            ") UNION " + (isLogical && cyclePossible?"":"ALL ") + "(" + stepSelect + ")) ";
                    select = recursiveWith + (isLogical ? syntax.getSelect(recName, SQLSession.stringExpr(keySelect, propertySelect), "", "", "", "", "")
                            : getGroupSelect(recName, keySelect, propertySelect, SetFact.<String>EMPTY(), havingSelect, SetFact.<String>EMPTY()));
                }
                return getSQLQuery("(" + select + ")", baseCost.result, subQueries, mSubEnv, baseInitialWhere, useRecursionFunction);
            }

            private String getInitialSelect(Where initialWhere, ImRevMap<String, KeyExpr> keyNames, ImMap<String, Type> columnTypes, ImRevMap<ParseValue, String> innerParams, boolean useRecursionFunction, boolean isLogical, SubQueryContext pushContext, boolean needRow, String rowPath, Expr rowSource, Result<ImOrderSet<String>> keyOrder, Result<ImOrderSet<String>> propOrder, MStaticExecuteEnvironment mSubEnv, Result<ImMap<String, SQLQuery>> subQueries, Result<Cost> baseCost) {
                ImRevMap<String, Expr> initialExprs;
                if(isLogical) {
                    initialExprs = MapFact.EMPTYREV();
                } else {
                    initialExprs = queries.mapRevValues(new GetValue<Expr, RecursiveExpr.Query>() {
                        public Expr getMapValue(RecursiveExpr.Query value) {
                            return value.initial;
                        }});
                }

                if(needRow) {
                    initialExprs = initialExprs.addRevExcl(rowPath, rowSource); // заполняем начальный путь
                }

                assert initialExprs.addExcl(keyNames).mapValues(new GetValue<Type, Expr>() {
                    public Type getMapValue(Expr value) {
                        return value.getType(innerJoin.getInitialWhere());
                    }
                }).equals(columnTypes);

                return getSelect(keyNames, initialExprs, columnTypes, initialWhere, keyOrder, propOrder, useRecursionFunction, false, innerParams, pushContext, baseCost, subQueries, mSubEnv);
            }

            private String getStepSelect(RecursiveJoin tableJoin, final boolean wrapStep, Stat adjustStat, String tableName, ImRevMap<String, KeyExpr> keyNames, ImMap<String, Type> propTypes, ImMap<String, Type> columnTypes, ImRevMap<ParseValue, String> innerParams, boolean useRecursionFunction, SubQueryContext pushContext, boolean isLogical, boolean needRow, String rowPath, Expr rowKeys, ArrayClass rowType, Expr rowSource, Result<ImOrderSet<String>> keyOrder, Result<ImOrderSet<String>> propOrder, MStaticExecuteEnvironment mSubEnv, Result<ImMap<String, SQLQuery>> subQueries, Result<Cost> baseCost) {
                assert keyOrder.result != null && propOrder.result != null; // уже в initial должны быть заполнены

                Where stepWhere = innerJoin.getStepWhere();
                final Where wrapClassWhere = wrapStep ? tableJoin.getIsClassWhere() : null;
                if(wrapStep) // чтобы избавляться от проблем с 2-м использованием
                    stepWhere = SubQueryExpr.create(stepWhere.and(wrapClassWhere));

                final Join<String> recJoin = tableJoin.getRecJoin(propTypes, tableName, keyNames, adjustStat);

                ImMap<String, Expr> stepExprs;
                if(isLogical) {
                    stepExprs = MapFact.EMPTY();
                } else {
                    stepExprs = queries.mapValues(new GetKeyValue<Expr, String, RecursiveExpr.Query>() {
                        public Expr getMapValue(String key, RecursiveExpr.Query value) {
                            Expr step = value.step;
                            if (wrapStep)
                                step = SubQueryExpr.create(step.and(wrapClassWhere));
                            return recJoin.getExpr(key).mult(step, (IntegralClass) value.getType());
                        }});
                }

                Where recWhere;
                if(needRow) {
                    Expr prevPath = recJoin.getExpr(rowPath);

                    Where noNodeCycle = rowKeys.compare(prevPath, Compare.INARRAY).not();
                    if(isLogical)
                        recWhere = recJoin.getWhere().and(noNodeCycle);
                    else {
                        recWhere = Where.TRUE;
                        ImValueMap<String, Expr> mStepExprs = stepExprs.mapItValues(); // "совместное" заполнение
                        for(int i=0,size=stepExprs.size();i<size;i++) {
                            String key = stepExprs.getKey(i);
                            IntegralClass type = (IntegralClass)queries.get(key).getType();
                            Expr maxExpr = type.getStaticExpr(type.getSafeInfiniteValue());
                            mStepExprs.mapValue(i, stepExprs.getValue(i).ifElse(noNodeCycle, maxExpr)); // если цикл даем максимальное значение
                            recWhere = recWhere.and(recJoin.getExpr(key).compare(maxExpr, Compare.LESS)); // останавливаемся если количество значений становится очень большим
                        }
                        stepExprs = mStepExprs.immutableValue();
                    }

                    stepExprs = stepExprs.addExcl(rowPath, FormulaExpr.createCustomFormula(syntax.getArrayConcatenate(rowType, "prm1", "prm2", mSubEnv), rowType, prevPath, rowSource)); // добавляем тек. вершину
                } else
                    recWhere = recJoin.getWhere();

                return getSelect(keyNames, stepExprs, columnTypes, stepWhere.and(recWhere), keyOrder, propOrder, useRecursionFunction, true, innerParams, pushContext, baseCost, subQueries, mSubEnv);
            }

            private SQLQuery getCTESource(boolean wrapExpr) {
                return getParamSource(false, wrapExpr);
            }
            public SQLQuery getSQLQuery() {
                boolean isLogical = innerJoin.isLogical();
                boolean cyclePossible = innerJoin.isCyclePossible();

                // проверка на cyclePossible, потому как в противном случае количество записей в итерации (так как туда ключом попадет путь) будет расти экспоненциально
                if((isLogical || !cyclePossible) && syntax.enabledCTE()) { // если isLogical или !cyclePossible пытаемся обойтись рекурсивным CTE
                    SQLQuery cteSelect = getCTESource(false);
                    if(cteSelect!=null)
                        return cteSelect;
                    cteSelect = getCTESource(true);
                    if(cteSelect!=null)
                        return cteSelect;
                }
                return getParamSource(true, false);
            }

            protected Where getInnerWhere() {
                Where result = Where.TRUE;
                for(RecursiveExpr expr : exprs.valueIt())
                    result = result.and(expr.getWhere());
                return result;
            }
        }

        // выделять отдельный Join, в общем то чтобы нормально использовался ANTI-JOIN в некоторых СУБД, (а он нужен в свою очередь чтобы A LEFT JOIN B WHERE B.e IS NULL)
        // не используется, потому a) как пока нет механизма выявления что идет именно ANTI-JOIN, в момент getSource
        // b) anti-join не сильно быстрее обычной стратегии с left join + filter
        private boolean isSingle(QueryJoin join) {
            return Settings.get().isUseSingleJoins() && (join instanceof GroupJoin || join instanceof RecursiveJoin) && !isInner(join);
        }

        private QuerySelect<?,?,?,?> getSingleSelect(QueryExpr queryExpr) {
            assert isSingle(queryExpr.getInnerJoin());
            for(int i=0,size=queries.size();i<size;i++) {
                QuerySelect select = queries.getValue(i);
                if(select.queries.size()==1)
                    if(BaseUtils.hashEquals(queryExpr, select.exprs.singleValue()))
                        return select;
            }
            return null;
        }
        public String getNullSource(InnerExpr innerExpr, String defaultSource) {
            InnerJoin<?, ?> innerJoin = innerExpr.getInnerJoin();
            if (innerExpr instanceof QueryExpr) {
                QueryExpr queryExpr = (QueryExpr) innerExpr;
                if (isSingle((QueryJoin) innerJoin)) {
                    QuerySelect singleSelect = getSingleSelect(queryExpr);
                    ImSet keys = singleSelect.group.keys();
                    if (!keys.isEmpty())
                        return singleSelect.alias + "." + keys.get(0) + " IS NULL";
                }
            }
            String result = super.getNullSource(innerExpr, defaultSource);
            // решает частично ту же проблему что и верхняя проверка
            if(syntax.hasNullWhereEstimateProblem() && whereCompiling && !Settings.get().isDisableAntiJoinOptimization() && !isInner(innerJoin) && isOptAntiJoin(innerJoin)) { // тут даже assert isInner возможно
                result = "(" + result + " OR " + syntax.getAdjustSelectivityPredicate() + ")";
            }
            return result;
        }

        final MAddExclMap<QueryJoin, QuerySelect> queries = MapFact.mAddExclMap();
        final MAddExclMap<GroupExpr, String> groupExprSources = MapFact.mAddExclMap();
        public String getSource(QueryExpr queryExpr) {
            if(queryExpr instanceof GroupExpr) {
                GroupExpr groupExpr = (GroupExpr)queryExpr;
                if(Settings.get().getInnerGroupExprs() >0 && !isInner(groupExpr.getInnerJoin())) { // если left join
                    String groupExprSource = groupExprSources.get(groupExpr);
                    if(groupExprSource==null) {
                        groupExprSource = groupExpr.getExprSource(this, subcontext.pushAlias(groupExprSources.size()));
                        groupExprSources.exclAdd(groupExpr, groupExprSource);
                    }
                    return groupExprSource;
                }
            }

            QueryJoin exprJoin = queryExpr.getInnerJoin();
            if(isSingle(exprJoin)) {
                QuerySelect select = getSingleSelect(queryExpr);
                if(select!=null)
                    return select.add(queryExpr.query, queryExpr);
            } else
                for(int i=0,size=queries.size();i<size;i++) {
                    MapTranslate translator;
                    if((translator= exprJoin.mapInner(queries.getKey(i), false))!=null)
                        return queries.getValue(i).add(queryExpr.query.translateOuter(translator),queryExpr);
                }

            QuerySelect select;
            if(exprJoin instanceof GroupJoin) // нету группы - создаем, чтобы не нарушать модульность сделаем без наследования
                select = new GroupSelect((GroupJoin) exprJoin);
            else
            if(exprJoin instanceof PartitionJoin)
                select = new PartitionSelect((PartitionJoin) exprJoin);
            else
            if(exprJoin instanceof RecursiveJoin)
                select = new RecursiveSelect((RecursiveJoin)exprJoin);
            else
                select = new SubQuerySelect((SubQueryJoin)exprJoin);

            queries.exclAdd(exprJoin,select);
            usedJoin(select);
            return select.add(queryExpr.query,queryExpr);
        }
        private JoinSelect getJoinSelect(InnerJoin innerJoin) {
            if(innerJoin instanceof Table.Join)
                return tables.get((Table.Join)innerJoin);
            if(innerJoin instanceof QueryJoin)
                return queries.get((QueryJoin)innerJoin);
            throw new RuntimeException("no matching class");
        }
    }

    private static <V> ImMap<V, String> castProperties(ImMap<V, String> propertySelect, final ImMap<V, Type> castTypes, final SQLSyntax syntax, final TypeEnvironment typeEnv) { // проставим Cast'ы для null'ов
        return propertySelect.mapValues(new GetKeyValue<String, V, String>() {
            public String getMapValue(V key, String propertyString) {
                Type castType;
                // проблемы бывают когда NULL - автоматический cast к text'у, и когда результат blankPadded, а внутри могут быть нет
                if ((castType = castTypes.get(key)) != null && (propertyString.equals(SQLSyntax.NULL) || (castType instanceof StringClass && ((StringClass)castType).blankPadded)))
                    propertyString = castType.getCast(propertyString, syntax, typeEnv);
                return propertyString;
            }
        });
    }

    // castTypes параметр чисто для бага Postgre и может остальных
    private static <K,V> String getInnerSelect(ImRevMap<K, KeyExpr> mapKeys, GroupJoinsWhere innerSelect, ImMap<V, Expr> compiledProps, ImRevMap<ParseValue, String> params, ImOrderMap<V, Boolean> orders, LimitOptions limit, SQLSyntax syntax, ImRevMap<K, String> keyNames, ImRevMap<V, String> propertyNames, Result<ImOrderSet<K>> keyOrder, Result<ImOrderSet<V>> propertyOrder, ImMap<V, Type> castTypes, SubQueryContext subcontext, boolean noInline, MStaticExecuteEnvironment env, Result<Cost> mBaseCost, MExclMap<String, SQLQuery> mSubQueries) {
        compiledProps = innerSelect.getFullWhere().followTrue(compiledProps, !innerSelect.isComplex());

        Result<ImMap<K,String>> andKeySelect = new Result<>(); Result<ImCol<String>> andWhereSelect = new Result<>(); Result<ImMap<V,String>> andPropertySelect = new Result<>();
        String andFrom = fillInnerSelect(mapKeys, innerSelect, compiledProps, andKeySelect, andPropertySelect, andWhereSelect, params, syntax, subcontext, env, mBaseCost, mSubQueries, null, null);

        if(castTypes!=null)
            andPropertySelect.set(castProperties(andPropertySelect.result, castTypes, syntax, env));

        return getSelect(andFrom, andKeySelect.result, keyNames, keyOrder, andPropertySelect.result, propertyNames, propertyOrder, andWhereSelect.result, syntax, getPackedCompileOrders(compiledProps, innerSelect.getFullWhere(), orders), limit, noInline);
    }

    private static <K,V> String getSelect(String from, ImMap<K, String> keySelect, ImRevMap<K, String> keyNames, Result<ImOrderSet<K>> keyOrder, ImMap<V, String> propertySelect, ImRevMap<V, String> propertyNames, Result<ImOrderSet<V>> propertyOrder, ImCol<String> whereSelect, SQLSyntax syntax, ImOrderMap<V, CompileOrder> orders, LimitOptions limit, boolean noInline) {
        return syntax.getSelect(from, SQLSession.stringExpr(SQLSession.mapNames(keySelect, keyNames, keyOrder),
                SQLSession.mapNames(propertySelect, propertyNames, propertyOrder)) + (noInline && syntax.inlineTrouble()?",random()":""),
                whereSelect.toString(" AND "), Query.stringOrder(propertyOrder.result, keySelect.size(), orders, propertySelect, syntax, new Result<Boolean>()),
                "", "", limit.getString());
    }

    private static <K,AV> String fillSingleSelect(ImRevMap<K, KeyExpr> mapKeys, GroupJoinsWhere innerSelect, ImMap<AV, Expr> compiledProps, Result<ImMap<K, String>> resultKey, Result<ImMap<AV, String>> resultProperty, ImRevMap<ParseValue, String> params, SQLSyntax syntax, SubQueryContext subcontext, MStaticExecuteEnvironment mEnv, Result<Cost> mBaseCost, MExclMap<String, SQLQuery> mSubQueries) {
        return fillFullSelect(mapKeys, SetFact.singleton(innerSelect), innerSelect.getFullWhere(), compiledProps, MapFact.<AV, Boolean>EMPTYORDER(), LimitOptions.NOLIMIT, resultKey, resultProperty, params, syntax, subcontext, mEnv, mBaseCost, mSubQueries);

/*        FullSelect FJSelect = new FullSelect(innerSelect.where, params,syntax); // для keyType'а берем первый where

        MapWhere<JoinData> joinDatas = new MapWhere<JoinData>();
        for(Map.Entry<AV, Expr> joinProp : compiledProps.entrySet())
            joinProp.getValue().fillJoinWheres(joinDatas, Where.TRUE);

        String innerAlias = subcontext+"inalias";
        Map<String, Expr> joinProps = new HashMap<String, Expr>();
        // затем все данные по JoinSelect'ам по вариантам
        for(JoinData joinData : joinDatas.keys()) {
            String joinName = "join_" + joinProps.size();
            joinProps.put(joinName, joinData.getFJExpr());
            FJSelect.joinData.put(joinData,joinData.getFJString(innerAlias +'.'+joinName));
        }

        Map<K, String> keyNames = new HashMap<K, String>();
        for(K key : mapKeys.keySet()) {
            String keyName = "jkey" + keyNames.size();
            keyNames.put(key, keyName);
            keySelect.put(key, innerAlias +"."+ keyName);
            FJSelect.keySelect.put(mapKeys.get(key),innerAlias +"."+ keyName);
        }

        for(Map.Entry<AV, Expr> mapProp : compiledProps.entrySet())
            propertySelect.put(mapProp.getKey(), mapProp.getValue().getSource(FJSelect));

        return "(" + getInnerSelect(mapKeys, innerSelect, joinProps, params, new OrderedMap<String, Boolean>(),0 , syntax, keyNames, BaseUtils.toMap(joinProps.keySet()), new ArrayList<K>(), new ArrayList<String>(), null, subcontext, true) + ") " + innerAlias;*/
    }
    
    private static <K> void fillAreValues(ImMap<K, Expr> exprs, Result<ImSet<K>> result) {
        if(result != null) {
            result.set(exprs.filterFnValues(new SFunctionSet<Expr>() {
                public boolean contains(Expr element) {
                    return element.isValue();
                }
            }).keys());
        }
    }

    private static <K,AV> String fillInnerSelect(ImRevMap<K, KeyExpr> mapKeys, final GroupJoinsWhere innerSelect, ImMap<AV, Expr> compiledProps, Result<ImMap<K, String>> resultKey, Result<ImMap<AV, String>> resultProperty, Result<ImCol<String>> resultWhere, ImRevMap<ParseValue, String> params, SQLSyntax syntax, SubQueryContext subcontext, MStaticExecuteEnvironment env, Result<Cost> mBaseCost, MExclMap<String, SQLQuery> mSubQueries, Result<ImSet<K>> resultKeyValues, Result<ImSet<AV>> resultPropValues) {

        ImSet<KeyExpr> freeKeys = mapKeys.valuesSet().removeIncl(BaseUtils.<ImSet<KeyExpr>>immutableCast(innerSelect.keyEqual.keyExprs.keys()));
        final InnerSelect compile = new InnerSelect(freeKeys, innerSelect.where, innerSelect.where, innerSelect.where,innerSelect.joins,innerSelect.upWheres,syntax, mSubQueries, env, params, subcontext);

        if(Settings.get().getInnerGroupExprs() > 0) { // если не одни joinData
            final MAddSet<GroupExpr> groupExprs = SetFact.mAddSet(); final Counter repeats = new Counter();
            for(Expr property : compiledProps.valueIt())
                property.enumerate(new ExprEnumerator() {
                    public Boolean enumerate(OuterContext join) {
                        if (join instanceof JoinData) { // если JoinData то что внутри не интересует
                            if (join instanceof GroupExpr && !compile.isInner(((GroupExpr) join).getInnerJoin()) && !groupExprs.add((GroupExpr) join))
                                repeats.add();
                            return false;
                        }
                        return true;
                    }
                });
            if(repeats.getValue() > Settings.get().getInnerGroupExprs())
                return fillSingleSelect(mapKeys, innerSelect, compiledProps, resultKey, resultProperty, params, syntax, subcontext, env, mBaseCost, mSubQueries);
        }

        MCol<String> mWhereSelect = ListFact.mCol();
        compile.fillInnerJoins(mBaseCost, mWhereSelect);
        ExprTranslator keyEqualTranslator = innerSelect.keyEqual.getTranslator();

        compiledProps = keyEqualTranslator.translate(compiledProps);
        fillAreValues(compiledProps, resultPropValues);
        resultProperty.set(compiledProps.mapValues(compile.<Expr>GETSOURCE()));
        ImMap<K, Expr> compiledKeys = keyEqualTranslator.translate(mapKeys);
        fillAreValues(compiledKeys, resultKeyValues);
        resultKey.set(compiledKeys.mapValues(compile.<Expr>GETSOURCE()));

        String from = compile.getFrom(innerSelect.where, mWhereSelect);
        resultWhere.set(mWhereSelect.immutableCol());
        return from;
    }
    
    private final static class AddAlias implements GetValue<String, String> {
        private final String alias;
        private AddAlias(String alias) {
            this.alias = alias;
        }

        public String getMapValue(String value) {
            return alias + "." + value;
        }
    }
    public final static class GenNameIndex implements GetIndex<String> {
        private final String prefix;
        private final String postfix;
        public GenNameIndex(String prefix, String postfix) {
            this.prefix = prefix;
            this.postfix = postfix;
        }

        public String getMapValue(int index) {
            return prefix + index + postfix;
        }
    }
    private final static GetValue<String, String> coalesceValue = new GetValue<String, String>() {
        public String getMapValue(String value) {
            return "COALESCE(" + value + ")";
        }
    };

    private static <K,AV> String fillFullSelect(ImRevMap<K, KeyExpr> mapKeys, ImCol<GroupJoinsWhere> innerSelects, Where fullWhere, ImMap<AV, Expr> compiledProps, ImOrderMap<AV, Boolean> orders, LimitOptions limit, Result<ImMap<K, String>> resultKey, Result<ImMap<AV, String>> resultProperty, ImRevMap<ParseValue, String> params, SQLSyntax syntax, final SubQueryContext subcontext, MStaticExecuteEnvironment mEnv, Result<Cost> mBaseCost, MExclMap<String, SQLQuery> mSubQueries) {

        // создаем And подзапросыs
        final ImSet<AndJoinQuery> andProps = innerSelects.mapColSetValues(new GetIndexValue<AndJoinQuery, GroupJoinsWhere>() {
            public AndJoinQuery getMapValue(int i, GroupJoinsWhere value) {
                return new AndJoinQuery(value, subcontext.wrapAlias("f" + i));
            }
        });

        MMap<JoinData, Where> mJoinDataWheres = MapFact.mMap(AbstractWhere.<JoinData>addOr());
        for(int i=0,size=compiledProps.size();i<size;i++)
            if(!orders.containsKey(compiledProps.getKey(i)))
                compiledProps.getValue(i).fillJoinWheres(mJoinDataWheres, Where.TRUE);
        ImMap<JoinData, Where> joinDataWheres = mJoinDataWheres.immutable();

        // для JoinSelect'ов узнаем при каких условиях они нужны
        MMap<Object, Where> mJoinWheres = MapFact.mMapMax(joinDataWheres.size(), AbstractWhere.addOr());
        for(int i=0,size=joinDataWheres.size();i<size;i++)
            mJoinWheres.add(joinDataWheres.getKey(i).getFJGroup(),joinDataWheres.getValue(i));
        ImMap<Object, Where> joinWheres = mJoinWheres.immutable();

        // сначала распихиваем JoinSelect по And'ам
        ImMap<Object, ImSet<AndJoinQuery>> joinAnds = joinWheres.mapValues(new GetValue<ImSet<AndJoinQuery>, Where>() {
            public ImSet<AndJoinQuery> getMapValue(Where value) {
                return getWhereSubSet(andProps, value);
            }});

        // затем все данные по JoinSelect'ам по вариантам
        ImValueMap<JoinData, String> mvJoinData = joinDataWheres.mapItValues(); // последействие есть
        for(int i=0;i<joinDataWheres.size();i++) {
            JoinData joinData = joinDataWheres.getKey(i);
            String joinName = "join_" + i;
            Collection<AndJoinQuery> dataAnds = new ArrayList<>();
            for(AndJoinQuery and : getWhereSubSet(joinAnds.get(joinData.getFJGroup()), joinDataWheres.getValue(i))) {
                Expr joinExpr = joinData.getFJExpr();
                if(!and.innerSelect.getFullWhere().means(joinExpr.getWhere().not())) { // проверим что не всегда null
                    and.properties.exclAdd(joinName, joinExpr);
                    dataAnds.add(and);
                }
            }
            String joinSource = ""; // заполняем Source
            if(dataAnds.size()==0)
                throw new RuntimeException(ServerResourceBundle.getString("data.query.should.not.be"));
            else
            if(dataAnds.size()==1)
                joinSource = dataAnds.iterator().next().alias +'.'+joinName;
            else {
                for(AndJoinQuery and : dataAnds)
                    joinSource = (joinSource.length()==0?"":joinSource+",") + and.alias + '.' + joinName;
                joinSource = "COALESCE(" + joinSource + ")";
            }
            mvJoinData.mapValue(i, joinData.getFJString(joinSource));
        }
        ImMap<JoinData, String> joinData = mvJoinData.immutableValue();

        // order'ы отдельно обрабатываем, они нужны в каждом запросе генерируем имена для Order'ов
        MOrderExclMap<String, Boolean> mOrderAnds = MapFact.mOrderExclMap(limit.hasLimit() ? orders.size() : 0);
        ImValueMap<AV, String> mvPropertySelect = compiledProps.mapItValues(); // сложный цикл
        for(int i=0,size=compiledProps.size();i<size;i++) {
            AV prop = compiledProps.getKey(i);
            Boolean dir = orders.get(prop);
            if(dir!=null) {
                String orderName = "order_" + i;
                String orderFJ = "";
                for(AndJoinQuery and : andProps) {
                    and.properties.exclAdd(orderName, compiledProps.get(prop));
                    orderFJ = (orderFJ.length()==0?"":orderFJ+",") + and.alias + "." + orderName;
                }
                if(limit.hasLimit()) // если все то не надо упорядочивать, потому как в частности MS SQL не поддерживает
                    mOrderAnds.exclAdd(orderName, dir);
                mvPropertySelect.mapValue(i, "COALESCE(" + orderFJ + ")");
            }
        }
        ImOrderMap<String, Boolean> orderAnds = mOrderAnds.immutableOrder();

        ImRevMap<K, String> keyNames = mapKeys.mapRevValues(new GenNameIndex("jkey",""));

        // бежим по всем And'ам делаем JoinSelect запросы, потом объединяем их FULL'ами
        String compileFrom = "";
        boolean first = true; // для COALESCE'ов
        ImMap<K, String> keySelect = null;
        for(AndJoinQuery and : andProps) {
            // закинем в And.Properties OrderBy, все равно какие порядки ключей и выражений
            ImMap<String, Expr> andProperties = and.properties.immutable();
            String andSelect = "(" + getInnerSelect(mapKeys, and.innerSelect, andProperties, params, orderAnds, limit, syntax, keyNames, andProperties.keys().toRevMap(), new Result<ImOrderSet<K>>(), new Result<ImOrderSet<String>>(), null, subcontext, innerSelects.size()==1, mEnv, mBaseCost, mSubQueries) + ") " + and.alias;

            final ImRevMap<K, String> andKeySources = keyNames.mapRevValues(new AddAlias(and.alias));

            if(keySelect==null) {
                compileFrom = andSelect;
                keySelect = andKeySources;
            } else {
                String andJoin = andKeySources.crossJoin(first?keySelect:keySelect.mapValues(coalesceValue)).toString("=", " AND ");
                keySelect = keySelect.mapValues(new GetKeyValue<String, K, String>() {
                    public String getMapValue(K key, String value) {
                        return value + "," + andKeySources.get(key);
                    }});
                compileFrom = compileFrom + " FULL JOIN " + andSelect + " ON " + (andJoin.length()==0? Where.TRUE_STRING :andJoin);
                first = false;
            }
        }

        // полученные KeySelect'ы в Data
        if(innerSelects.size()>1)
            keySelect = keySelect.mapValues(coalesceValue);

        FullSelect FJSelect = new FullSelect(fullWhere, fullWhere, params, syntax, mEnv, mapKeys.crossJoin(keySelect), joinData); // для keyType'а берем первый where
        // закидываем PropertySelect'ы
        for(int i=0,size=compiledProps.size();i<size;i++) {
            AV prop = compiledProps.getKey(i);
            if(!orders.containsKey(prop)) // orders'ы уже обработаны
                mvPropertySelect.mapValue(i, compiledProps.getValue(i).getSource(FJSelect));
        }

        resultKey.set(keySelect);
        resultProperty.set(mvPropertySelect.immutableValue());
        return compileFrom;
    }

    // нерекурсивное транслирование параметров
    public static String translatePlainParam(String string,ImMap<String,String> paramValues) {
        for(int i=0,size=paramValues.size();i<size;i++)
            string = string.replace(paramValues.getKey(i), paramValues.getValue(i));
        return string;
    }

    // key - какие есть, value - которые должны быть
    private static String translateParam(String query,ImRevMap<String,String> paramValues) {
        // генерируем промежуточные имена, перетранслируем на них
        ImRevMap<String, String> preTranslate = paramValues.mapRevValues(new GenNameIndex("transp", "nt"));
        for(int i=0,size=preTranslate.size();i<size;i++)
            query = query.replace(preTranslate.getKey(i), preTranslate.getValue(i));

        // транслируем на те что должны быть
        ImRevMap<String, String> translateMap = preTranslate.crossJoin(paramValues);
        for(int i=0,size=translateMap.size();i<size;i++)
            query = query.replace(translateMap.getKey(i), translateMap.getValue(i));
        return query;
    }
    
    private final static GetValue<ParseInterface, ParseValue> GETPARSE = new GetValue<ParseInterface, ParseValue>() {
        public ParseInterface getMapValue(ParseValue value) {
            return value.getParseInterface();
        }
    };
    public ImMap<String, ParseInterface> getQueryParams(QueryEnvironment env) {
        return getQueryParams(env, 0);
    }
    public ImMap<String, ParseInterface> getQueryParams(QueryEnvironment env, final int limit) {
        MExclMap<String, ParseInterface> mMapValues = MapFact.mExclMap();
        if(limit > 0)
            mMapValues.exclAdd(SQLSession.limitParam, new StringParseInterface() {
                public String getString(SQLSyntax syntax, StringBuilder envString, boolean usedRecursion) {
                    return String.valueOf(limit);
                }
            });
        mMapValues.exclAdd(SQLSession.userParam, env.getSQLUser());
        mMapValues.exclAdd(SQLSession.computerParam, env.getSQLComputer());
        mMapValues.exclAdd(SQLSession.formParam, env.getSQLForm());
        mMapValues.exclAdd(SQLSession.connectionParam, env.getSQLConnection());
        mMapValues.exclAdd(SQLSession.isServerRestartingParam, env.getIsServerRestarting());
        mMapValues.exclAdd(SQLSession.isFullClientParam, env.getIsFullClient());
        mMapValues.exclAdd(SQLSession.isDebugParam, new LogicalParseInterface() {
            public boolean isTrue() {
                return SystemProperties.isDebug;
            }
        });
        return mMapValues.immutable().addExcl(params.reverse().mapValues(GETPARSE));
    }

    private String fillSelect(final ImRevMap<String, String> params, Result<ImMap<K, String>> fillKeySelect, Result<ImMap<V, String>> fillPropertySelect, Result<ImCol<String>> fillWhereSelect, Result<ImMap<String, SQLQuery>> fillSubQueries, MStaticExecuteEnvironment fillEnv) {
        GetValue<String, String> transValue = new GetValue<String, String>() {
            public String getMapValue(String value) {
                return translateParam(value, params);
            }};

        fillKeySelect.set(keySelect.mapValues(transValue));
        fillPropertySelect.set(propertySelect.mapValues(transValue));
        fillWhereSelect.set(whereSelect.mapColValues(transValue));
        fillEnv.add(sql.getEnv());
        fillSubQueries.set(SQLQuery.translate(sql.subQueries, new GetValue<String, String>() {
            public String getMapValue(String value) {
                return translateParam(value, params);
            }}));
        return translateParam(from, params);
    }

    private ImRevMap<String, String> getTranslate(ImRevMap<ParseValue, String> mapValues) {
        return params.crossJoin(mapValues);
    }

    // для подзапросов
    public String fillSelect(Result<ImMap<K, String>> fillKeySelect, Result<ImMap<V, String>> fillPropertySelect, Result<ImCol<String>> fillWhereSelect, Result<ImMap<String, SQLQuery>> fillSubQueries, ImRevMap<ParseValue, String> mapValues, MStaticExecuteEnvironment fillEnv) {
        return fillSelect(getTranslate(mapValues), fillKeySelect, fillPropertySelect, fillWhereSelect, fillSubQueries, fillEnv);
    }

    public void execute(SQLSession session, QueryEnvironment queryEnv, int limit, ResultHandler<K, V> resultHandler) throws SQLException, SQLHandledException {
        session.executeSelect(sql, getQueryExecEnv(session.userProvider), queryEnv.getOpOwner(), getQueryParams(queryEnv, limit), queryEnv.getTransactTimeout(), keyNames, propertyNames, resultHandler);
    }

    public void outSelect(SQLSession session, QueryEnvironment env) throws SQLException, SQLHandledException {
        sql.outSelect(session, getQueryExecEnv(session.userProvider), null, getQueryParams(env), env.getTransactTimeout(), env.getOpOwner());
    }

    public String readSelect(SQLSession session, QueryEnvironment env) throws SQLException, SQLHandledException {
        return sql.readSelect(session, getQueryExecEnv(session.userProvider), null, getQueryParams(env), env.getTransactTimeout(), env.getOpOwner());
    }
}

/*
        // для работы с cross-column статистикой
        // не получается сделать, не выстраивая порядок JOIN'ов, а это уже перебор, уж за это SQL Server сам должен отвечать
        // вообще надо adjustSelectivity бороться, делая из SQL сервера пессимиста

        private static class RightJoins {
            public Map<Table.Join, Map<String, Field>> map = new HashMap<Table.Join, Map<String, Field>>();

            public void add(Table.Join join, String key, Field field) {
                Map<String, Field> joinFields = map.get(join);
                if(joinFields==null) {
                    joinFields = new HashMap<String, Field>();
                    map.put(join, joinFields);
                }
                joinFields.put(key, field);
            }

            public void addAll(RightJoins add) {
                for(Map.Entry<Table.Join, Map<String, Field>> addEntry : add.map.entrySet())
                    for(Map.Entry<String, Field> addField : addEntry.getValue().entrySet())
                        add(addEntry.getKey(), addField.getKey(), addField.getValue());
            }

            public void addAll(Map<String, Pair<Table.Join, KeyField>> add) {
                for(Map.Entry<String, Pair<Table.Join, KeyField>> addEntry : add.entrySet())
                    add(addEntry.getValue().first, addEntry.getKey(), addEntry.getValue().second);
            }
        }

        // для работы с cross-column статистикой

        // перебор индексов по соединяемым таблицам
        public static interface RecJoinTables {
            void proceed(Collection<String> freeFields);
        }
        private static void recJoinTables(final int i, final List<Table.Join> list, final Map<Table.Join, Map<String, Field>> joinTables, final Stack<List<List<String>>> current, final Collection<String> freeFields, final RecJoinTables result) {
            if(i>=list.size()) {
                result.proceed(freeFields);
                return;
            }
            Table.Join tableJoin = list.get(i);
            tableJoin.getTable().recIndexTuples(joinTables.get(tableJoin), current, new Table.RecIndexTuples<String>() {
                public void proceed(Map<String, ? extends Field> restFields) { // можно не учитывать restFields так как join'ы не пересекаются
                    recJoinTables(i + 1, list, joinTables, current, BaseUtils.merge(freeFields, restFields.keySet()), result);
                }
            });
        }

        private static class Coverage { // именно в таком приоритете
            private final int notIndexed;
            private final int leftRight;
            private final int tuples;
            private final int indexes;

            private Coverage(int notIndexed, int leftRight, int tuples, int indexes) {
                this.notIndexed = notIndexed;
                this.leftRight = leftRight;
                this.tuples = tuples;
                this.indexes = indexes;
            }

            boolean better(Coverage cov) {
                if(notIndexed < cov.notIndexed) // минимум не индексированных полей
                    return true;
                if(notIndexed > cov.notIndexed)
                    return false;
                if(leftRight > cov.leftRight) // максимум 2-сторонних индексов
                    return true;
                if(leftRight < cov.leftRight)
                    return false;
                if(tuples < cov.tuples) // минимум tuples (чтобы лучше статистика была)
                    return true;
                if(tuples > cov.tuples)
                    return false;
                if(indexes < cov.indexes) // минимум индексов
                    return true;
                if(tuples > cov.tuples)
                    return false;

                return false;
            }
        }

        // перебираем "правые" индексы
        private static void recJoinTables(Map<Table.Join, Map<String, Field>> joinTables, Stack<List<List<String>>> current, RecJoinTables result) {
            recJoinTables(0, new ArrayList<Table.Join>(joinTables.keySet()), joinTables, current, new ArrayList<String>(), result);
        }

        // перебор табличных join'ов, из которых брать ключи
        public static interface RecJoinKeyTables {
            void proceed(Map<String, Pair<Table.Join, KeyField>> map); // mutable
        }
        private static void recJoinKeyTables(final int i, final List<String> list, Map<String, KeyExpr> mapKeys, Map<KeyExpr, Collection<Pair<Table.Join, KeyField>>> keyTables, Map<String, Pair<Table.Join, KeyField>> current, RecJoinKeyTables result) {
            if(i>=list.size()) {
                result.proceed(current);
                return;
            }

            String key = list.get(i);
            for(Pair<Table.Join, KeyField> keyTable : keyTables.get(mapKeys.get(key))) {
                current.put(key, keyTable);
                recJoinKeyTables(i + 1, list, mapKeys, keyTables, current, result);
                current.remove(key);
            }
        }
        private static void recJoinKeyTables(Map<String, KeyExpr> mapKeys, Map<KeyExpr, Collection<Pair<Table.Join, KeyField>>> keyTables, RecJoinKeyTables result) {
            recJoinKeyTables(0, new ArrayList<String>(mapKeys.keySet()), mapKeys, keyTables, new HashMap<String, Pair<Table.Join, KeyField>>(), result);
        }


        // для работы с cross-column статистикой
        private Map<KeyExpr, Collection<Pair<Table.Join, KeyField>>> keyTables = new HashMap<KeyExpr, Collection<Pair<Table.Join, KeyField>>>();

        private abstract class JoinSelect<I extends InnerJoin> {

            final String alias; // final
            final String join; // final
            final I innerJoin;

            protected abstract Map<String, BaseExpr> initJoins(I innerJoin);

            protected boolean isInner() {
                return InnerSelect.this.isInner(innerJoin);
            }

            protected JoinSelect(final I innerJoin) {
                alias = subcontext.wrapAlias("t" + (aliasNum++));
                this.innerJoin = innerJoin;

                useTuples = true && isInner();

                // здесь проблема что keySelect может рекурсивно использоваться 2 раза, поэтому сначала пробежим не по ключам
                RightJoins joinTables = null;
                if(useTuples)
                    joinTables = new RightJoins();

                Map<String, String> joinSources = new HashMap<String, String>();
                Map<String,KeyExpr> joinKeys = new HashMap<String, KeyExpr>();
                for(Map.Entry<String, BaseExpr> keyJoin : initJoins(innerJoin).entrySet()) {
                    String keySource = alias + "." + keyJoin.getKey();
                    if(keyJoin.getValue() instanceof KeyExpr)
                        joinKeys.put(keySource,(KeyExpr)keyJoin.getValue());
                    else {
                        joinSources.put(keySource, keyJoin.getValue().getSource(InnerSelect.this));

                        if(useTuples && keyJoin instanceof Table.Join.Expr) {
                            Table.Join.Expr tableExpr = (Table.Join.Expr) keyJoin;
                            joinTables.add(tableExpr.getInnerJoin(), keySource, tableExpr.property);
                        }
                    }
                }
                for(Map.Entry<String,KeyExpr> keyJoin : joinKeys.entrySet()) { // дозаполним ключи
                    String keySource = keySelect.get(keyJoin.getValue());
                    if(keySource==null) {
                        assert isInner();
                        keySelect.put(keyJoin.getValue(),keyJoin.getKey());
                    } else
                        joinSources.put(keyJoin.getKey(), keySource);
                }

                if(useTuples) {
                    if(this instanceof TableSelect) { // записываем себя к ключам
                        Map<String, KeyField> mapKeys = ((TableSelect)this).initFields((Table.Join) innerJoin);
                        for(Map.Entry<String, KeyExpr> keyJoin : joinKeys.entrySet()) {
                            Collection<Pair<Table.Join, KeyField>> keyList = keyTables.get(keyJoin.getValue());
                            if(keyList==null) {
                                keyList = new ArrayList<Pair<Table.Join, KeyField>>();
                                keyTables.put(keyJoin.getValue(), keyList);
                            }
                            keyList.add(new Pair<Table.Join, KeyField>((Table.Join)innerJoin, mapKeys.get(keyJoin.getKey())));
                        }
                    }
                    join = "";
                    this.joinTables = joinTables; this.joinSources = joinSources; this.joinKeys = joinKeys;
                } else {
                    Collection<String> joinSelect = new ArrayList<String>();
                    for(Map.Entry<String, String> joinSource : joinSources.entrySet())
                        joinSelect.add(joinSource.getKey() + "=" + joinSource.getValue());
                    join = BaseUtils.toString(joinSelect, " AND ");
                }

                InnerSelect.this.joins.add(this);
            }

            // для работы с cross-column статистикой, не очень красиво, но других очевидных вариантов не видно
            private final boolean useTuples;
            private RightJoins joinTables; private Map<String, String> joinSources; private Map<String, KeyExpr> joinKeys;
            private void tupleJoin(Collection<String> joinSelect) {
                final Map<String, KeyExpr> joinKeyTables = BaseUtils.filterValues(joinKeys, keyTables.keySet()); // интересуют только, те для которых есть таблицы

                final Table table;
                final Map<String, KeyField> mapKeys;
                if(this instanceof TableSelect) {
                    table = ((Table.Join) innerJoin).getTable(); mapKeys = ((TableSelect)this).initFields((Table.Join) innerJoin);
                } else {
                    table = null; mapKeys = null;
                }

                final Result<Pair<Coverage, List<List<String>>>> bestCoverage = new Result<Pair<Coverage, List<List<String>>>>();
                final Result<Map<String, String>> bestKeySources = new Result<Map<String, String>>();

                // перебираем использование ключей в таблицах
                recJoinKeyTables(joinKeyTables, keyTables, new RecJoinKeyTables() {
                    public void proceed(final Map<String, Pair<Table.Join, KeyField>> mapKeyFields) {
                        final RightJoins recJoinTables = new RightJoins();
                        recJoinTables.addAll(mapKeyFields);
                        recJoinTables.addAll(joinTables);

                        final Stack<List<List<String>>> leftIndexes = new Stack<List<List<String>>>(); // здесь левые индексы
                        final Stack<List<List<String>>> rightIndexes = new Stack<List<List<String>>>(); // здесь правые индексы
                        final RecJoinTables finalResult = new RecJoinTables() {
                            public void proceed(Collection<String> freeFields) {
                                int keys = freeFields.size();
                                int leftRight = 0;
                                int tuples = 0;
                                int indexes = 0;

                                indexes += leftIndexes.size();
                                for(List<List<String>> leftIndex : leftIndexes)
                                    tuples += leftIndex.size();

                                indexes += rightIndexes.size();
                                if(table!=null) { // если таблица, считаем кол-во 2-сторонних индексов
                                    for(List<List<String>> rightIndex : rightIndexes) {
                                        tuples += rightIndex.size();
                                        int maxCommon = 0;
                                        for(List<List<Field>> leftIndex : table.indexes) {
                                            int cc = 0; int ckeys = 0; List<KeyField> commonTuple;
                                            while((cc < rightIndex.size() && cc<leftIndex.size()) && ((commonTuple = mapList(rightIndex.get(cc), mapKeys)).equals(leftIndex.get(cc)))) {
                                                ckeys += commonTuple.size(); cc++;
                                            }
                                            maxCommon = max(maxCommon, ckeys);
                                        }
                                        leftRight += maxCommon;
                                    }
                                }

                                Coverage coverage = new Coverage(keys, leftRight, tuples, indexes);
                                if(bestCoverage.result == null || coverage.better(bestCoverage.result.first)) {
                                    List<List<String>> resultIndexes = new ArrayList<List<String>>();
                                    for(List<List<String>> leftIndex : leftIndexes)
                                        resultIndexes.addAll(leftIndex);
                                    for(List<List<String>> rightIndex : rightIndexes)
                                        resultIndexes.addAll(rightIndex);
                                    for(String freeField : freeFields)
                                        resultIndexes.add(Collections.singletonList(freeField));
                                    bestCoverage.set(new Pair<Coverage, List<List<String>>>(coverage, resultIndexes));

                                    Map<String, String> keySources = new HashMap<String, String>();
                                    for(Map.Entry<String, Pair<Table.Join, KeyField>> mapKeyField : mapKeyFields.entrySet())
                                        keySources.put(mapKeyField.getKey(), getAlias(mapKeyField.getValue().first) + "." + mapKeyField.getValue().second);
                                    bestKeySources.set(keySources);
                                }
                            }
                        };

                        if(table!=null) // перебираем "левые" индексы
                            recJoinTables(recJoinTables.map, rightIndexes, new RecJoinTables() {
                                public void proceed(Collection<String> freeFields) {
                                    table.recIndexTuples(filterKeys(mapKeys, freeFields), leftIndexes, new Table.RecIndexTuples<String>() {
                                        public void proceed(Map<String, ? extends Field> restFields) {
                                            finalResult.proceed(restFields.keySet());
                                        }
                                    });
                                }
                            });
                        else
                            recJoinTables(recJoinTables.map, rightIndexes, finalResult);
                    }
                });

                joinSources.putAll(bestKeySources.result); // берем реально использованные ключи
                for(List<String> tuple : bestCoverage.result.second)
                    joinSelect.add(Table.getTuple(tuple) + " = " + Table.getTuple(mapList(tuple, joinSources)));

                this.joinTables = null; this.joinSources = null; this.joinKeys = null;
            }

            public abstract String getSource(StaticExecuteEnvironment env);

            protected abstract Where getInnerWhere(); // assert что isInner
        }
        public void fillInnerJoins(Collection<String> whereSelect) { // заполним Inner Joins, чтобы чтобы keySelect'ы были
            innerWhere = whereJoins.fillInnerJoins(upWheres, whereSelect, this);
        }

        private Where innerWhere;
        // получает условия следующие из логики inner join'ов SQL
        private Where getInnerWhere() {
            Where result = innerWhere;
            for(InnerJoin innerJoin : getInnerJoins()) {
                JoinSelect joinSelect = getJoinSelect(innerJoin);
                if(joinSelect!=null)
                    result = result.and(joinSelect.getInnerWhere());
            }
            return result;
        }

        public String getFrom(Where where, Collection<String> whereSelect, StaticExecuteEnvironment env) {
            where.getSource(this);
            whereSelect.add(where.followFalse(getInnerWhere().not()).getSource(this));

            if(joins.isEmpty()) return "dumb";

            String from;
            Iterator<JoinSelect> ij = joins.iterator();
            JoinSelect first = ij.next();
            if(first.isInner()) {
                from = first.getSource(env) + " " + first.alias;
                if(!(first.join.length()==0))
                    whereSelect.add(first.join);

                if(first.useTuples)
                    first.tupleJoin(whereSelect);
            } else {
                from = "dumb";
                ij = joins.iterator();
            }

            while(ij.hasNext()) {
                JoinSelect join = ij.next();
                from = from + (join.isInner() ?"":" LEFT")+" JOIN " + join.getSource(env) + " " + join.alias  + " ON " + (join.join.length()==0?Where.TRUE_STRING:join.join);

                if(join.useTuples)
                    join.tupleJoin(whereSelect);
            }

            return from;
        }


            protected Map<String, KeyField> initFields(Table.Join table) {
                Map<String, KeyField> result = new HashMap<String, KeyField>();
                for(KeyField key : table.joins.keySet())
                    result.put(key.toString(),key);
                return result;
            }

 */

/* !!!!! UNION ALL код            // в Properties закинем Orders,
            HashMap<Object,Query> UnionProps = new HashMap<Object, Query>(query.property);
            LinkedHashMap<String,Boolean> OrderNames = new LinkedHashMap<String, Boolean>();
            int io = 0;
            for(Map.Entry<Query,Boolean> Order : query.orders.entrySet()) {
                String OrderName = "order_"+io;
                UnionProps.put(OrderName,Order.getKey());
                OrderNames.put(OrderName,Order.getValue());
            }

            From = "";
            while(true) {
                Where AndWhere = QueryJoins.iterator().next();
                Map<K,String> AndKeySelect = new HashMap<K, String>();
                LinkedHashMap<Object,String> AndPropertySelect = new LinkedHashMap<Object, String>();
                Collection<String> AndWhereSelect = new ArrayList<String>();
                String AndFrom = fillAndSelect(Query.Keys,AndWhere,UnionProps,new LinkedHashMap<Query,Boolean>(),AndKeySelect,
                    AndPropertySelect,AndWhereSelect,QueryParams,new LinkedHashMap<String,Boolean>(), Syntax);

                LinkedHashMap<String,String> NamedProperties = new LinkedHashMap<String, String>();
                for(V Property : Query.Properties.keySet()) {
                    NamedProperties.put(PropertyNames.get(Property),AndPropertySelect.get(Property));
                    if(From.length()==0) PropertyOrder.add(Property);
                }
                for(String Order : OrderNames.keySet())
                    NamedProperties.put(Order,AndPropertySelect.get(Order));

                From = (From.length()==0?"":From+" UNION ALL ") +
                    Syntax.getSelect(AndFrom,Source.stringExpr(Source.mapNames(AndKeySelect,KeyNames,From.length()==0?KeyOrder:new ArrayList<K>()),NamedProperties),
                            Source.stringWhere(AndWhereSelect),"","","");

                OrWhere = OrWhere.andNot(AndWhere).getOr();
                if(OrWhere.isFalse()) break;
                break;
            }

            String Alias = "G";
            for(K Key : query.keys.keySet())
                keySelect.put(Key, Alias + "." + keyNames.get(Key));
            for(V Property : query.property.keySet())
                propertySelect.put(Property, Alias + "." + propertyNames.get(Property));

            from = "(" + from + ") "+Alias;

            select = syntax.getUnionOrder(from,Query.stringOrder(OrderNames), query.top ==0?"":String.valueOf(query.top));
            */
