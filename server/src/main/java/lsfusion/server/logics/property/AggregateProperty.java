package lsfusion.server.logics.property;

import lsfusion.base.BaseUtils;
import lsfusion.base.Pair;
import lsfusion.base.ProgressBar;
import lsfusion.base.col.ListFact;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.*;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetIndex;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetIndexValue;
import lsfusion.interop.Compare;
import lsfusion.server.Settings;
import lsfusion.server.SystemProperties;
import lsfusion.server.caches.IdentityLazy;
import lsfusion.server.caches.IdentityStartLazy;
import lsfusion.server.classes.BaseClass;
import lsfusion.server.data.*;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.expr.NullableKeyExpr;
import lsfusion.server.data.expr.query.AggrType;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.data.expr.query.StatType;
import lsfusion.server.data.query.Query;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.data.query.stat.StatKeys;
import lsfusion.server.data.where.Where;
import lsfusion.server.data.where.classes.ClassWhere;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DBManager;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.infer.ExClassSet;
import lsfusion.server.logics.property.infer.InferType;
import lsfusion.server.logics.property.infer.Inferred;
import lsfusion.server.session.DataSession;
import lsfusion.server.session.PropertyChanges;
import lsfusion.server.session.StructChanges;
import lsfusion.server.stack.StackMessage;
import lsfusion.server.stack.StackProgress;
import lsfusion.server.stack.ThisMessage;

import java.sql.SQLException;
import java.sql.Timestamp;

public abstract class AggregateProperty<T extends PropertyInterface> extends CalcProperty<T> {

    protected static void fillDepends(MSet<CalcProperty> depends, ImCol<? extends CalcPropertyInterfaceImplement> propImplements) {
        for(CalcPropertyInterfaceImplement propImplement : propImplements)
            propImplement.mapFillDepends(depends);
    }


    protected <I extends PropertyInterface> Inferred<I> inferInnerInterfaceClasses(ImList<CalcPropertyInterfaceImplement<I>> used, final boolean isSelect, final ExClassSet commonValue, ImOrderMap<CalcPropertyInterfaceImplement<I>, Boolean> orders, boolean ordersNotNull, int skipNotNull, InferType inferType) {
        ImList<ExClassSet> valueClasses = ListFact.toList(used.size(), new GetIndex<ExClassSet>() {
            public ExClassSet getMapValue(int i) {
                return isSelect && i == 0 ? commonValue : ExClassSet.notNull(commonValue);
            }});
        return inferInnerInterfaceClasses(used, orders, ordersNotNull, skipNotNull, valueClasses, inferType);
    }

    protected <I extends PropertyInterface> Inferred<I> inferInnerInterfaceClasses(ImList<CalcPropertyInterfaceImplement<I>> used, ImOrderMap<CalcPropertyInterfaceImplement<I>, Boolean> orders, boolean ordersNotNull, int skipNotNull, ImList<ExClassSet> valueClasses, InferType inferType) {
        return op(used.addList(orders.keyOrderSet()), valueClasses.addList(ListFact.toList(ExClassSet.NULL, orders.size())),
                used.size() + (ordersNotNull ? orders.size() : 0), skipNotNull, inferType, false);
    }

    protected <I extends PropertyInterface> ExClassSet inferInnerValueClass(ImList<CalcPropertyInterfaceImplement<I>> used, ImMap<I, ExClassSet> inferred, AggrType aggrType, InferType inferType) {
        ExClassSet valueClass = used.get(aggrType.getMainIndex()).mapInferValueClass(inferred, inferType);
        if(aggrType.isSelect())
            return valueClass;

        if(valueClass == null) {
            assert inferType == InferType.RESOLVE;
            return null;
        }
        return ExClassSet.toExType(aggrType.getType(ExClassSet.fromExType(valueClass)));
    }

    public ImSet<CalcProperty> calculateUsedChanges(StructChanges propChanges) {
        return propChanges.getUsedChanges(getDepends());
    }

    public boolean isStored() {
        assert (field!=null) == (mapTable!=null);
        return mapTable!=null && !DataSession.reCalculateAggr; // для тестирования 2-е условие
    }

    protected AggregateProperty(LocalizedString caption, ImOrderSet<T> interfaces) {
        super(caption,interfaces);
    }

    public String checkAggregation(SQLSession session, BaseClass baseClass) throws SQLException, SQLHandledException {
        return checkAggregation(session, null, baseClass, null);
    }

    public String checkAggregation(SQLSession session, BaseClass baseClass, ProgressBar progressBar) throws SQLException, SQLHandledException {
        return checkAggregation(session, null, baseClass, progressBar);
    }

    // проверяет агрегацию для отладки
    @ThisMessage
    @StackProgress
    public String checkAggregation(SQLSession session, QueryEnvironment env, BaseClass baseClass, @StackProgress ProgressBar progressBar) throws SQLException, SQLHandledException {
        session.pushVolatileStats(OperationOwner.unknown);
        
        try {

            boolean useRecalculate = Settings.get().isUseRecalculateClassesInsteadOfInconsisentExpr();

            String message = "";

            String checkClasses = "";
            if(useRecalculate)
                checkClasses = env == null ? DataSession.checkClasses(this, session, baseClass) : DataSession.checkClasses(this, session, env, baseClass);
    
            ImOrderMap<ImMap<T, Object>, ImMap<String, Object>> checkResult = env == null ? getRecalculateQuery(true, baseClass, !useRecalculate).execute(session, OperationOwner.unknown)
                    : getRecalculateQuery(true, baseClass, !useRecalculate).execute(session, env);
            if(checkResult.size() > 0 || !checkClasses.isEmpty()) {
                message += "---- Checking Aggregations : " + this + "-----" + '\n';
                message += checkClasses;
                for(int i=0,size=checkResult.size();i<size;i++)
                    message += "Keys : " + checkResult.getKey(i) + " : " + checkResult.getValue(i) + '\n';
            }

            return message;
        } finally {
            session.popVolatileStats(OperationOwner.unknown);
        }
    }

//    public List<Double> checkStats(SQLSession session, BaseClass baseClass) throws SQLException, SQLHandledException {
//
//        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(SetFact.EMPTY());
//        Where where = getExpr(getMapKeys()).getWhere();
//        try {
//            Stat statRows = where.getStatRows();
//            Stat maxStat = new Stat(5, true);
//            boolean timeouted = false;
//            if(!where.isFalse() && statRows.lessEquals(maxStat)) {
//                ValueExpr one = new ValueExpr(1, IntegerClass.instance);
//                query.addProperty("count", GroupExpr.create(MapFact.<Integer, Expr>EMPTY(), one,
//                        where, GroupType.SUM, MapFact.<Integer, Expr>EMPTY()));
//                Integer cnt;
//                try {
//                    cnt = (Integer)query.execute(session, DataSession.emptyEnv(OperationOwner.debug)).singleValue().singleValue();
//                    if(cnt == null)
//                        cnt = 0;
//                } catch (SQLTimeoutException e) {
//                    cnt = maxStat.getWeight();
//                    timeouted = true;
//                }
//
//                List<Double> list = new ArrayList<Double>();
//                for(int i=0;i<4;i++) {
//                    Settings.get().setPessStatType(i);
//                    
//                    Stat iStat = where.getStatRows();
//                    if(timeouted)
//                        iStat = iStat.min(maxStat);
//                    
//                    Stat calcStat = new Stat(cnt);
//                    double diff = calcStat.getWeight() - (double) iStat.getWeight();
//                    if (diff > 5.0)
//                        diff = 5.0;
//                    if (diff < -5.0)
//                        diff = -5.0;
//                    list.add(diff);
//                }
//                return list;
//            }
//        } catch (RuntimeException r) {
//            assert r.getMessage().contains("no classes") || r.getMessage().contains("not supported");
//            return null;
//        }
//        return null;
//    }
//
    public Expr calculateExpr(ImMap<T, ? extends Expr> joinImplement) {
        return calculateExpr(joinImplement, CalcType.EXPR, PropertyChanges.EMPTY, null);
    }

    public Expr calculateStatExpr(ImMap<T, ? extends Expr> joinImplement, boolean alotHeur) { // вызывается до stored, поэтому чтобы не было проблем с кэшами, сделано так
        return calculateExpr(joinImplement, alotHeur ? CalcType.STAT_ALOT : CalcType.STAT, PropertyChanges.EMPTY, null);
    }

    public Expr calculateInconsistentExpr(ImMap<T, ? extends Expr> joinImplement) { // вызывается до stored, поэтому чтобы не было проблем с кэшами, сделано так
        return calculateExpr(joinImplement, CalcType.RECALC, PropertyChanges.EMPTY, null);
    }

    private Query<T, String> getRecalculateQuery(boolean outDB, BaseClass baseClass, boolean checkInconsistence) {
        assert isStored();
        
        QueryBuilder<T, String> query = new QueryBuilder<>(this);

        Expr dbExpr = checkInconsistence ? getInconsistentExpr(query.getMapExprs(), baseClass) : getExpr(query.getMapExprs());
        Expr calculateExpr = isFullProperty() ? calculateInconsistentExpr(query.getMapExprs()) : calculateExpr(query.getMapExprs());  // оптимизация - только для FULL свойств, так как в остальных лучше использовать кэш по EXPR
        if(outDB)
            query.addProperty("dbvalue", dbExpr);
        query.addProperty("calcvalue", calculateExpr);
        query.and(dbExpr.getWhere().or(calculateExpr.getWhere()));
        if(outDB || !DBManager.RECALC_REUPDATE)
            query.and(dbExpr.compare(calculateExpr, Compare.EQUALS).not().and(dbExpr.getWhere().or(calculateExpr.getWhere())));
        return query.getQuery();
    }

    private boolean isFullProperty() {
        IsClassField fullField;
        return (fullField = mapTable.table.getFullField()) != null && BaseUtils.hashEquals(fullField.getField(), field);
    }

    public static AggregateProperty recalculate = null;

    public void recalculateAggregation(BusinessLogics BL, DataSession session, SQLSession sql, BaseClass baseClass) throws SQLException, SQLHandledException {
        recalculateAggregation(sql, null, baseClass);

        ObjectValue propertyObject = BL.reflectionLM.propertyCanonicalName.readClasses(session, new DataObject(getCanonicalName()));
        if (propertyObject instanceof DataObject)
            BL.reflectionLM.lastRecalculateProperty.change(new Timestamp(System.currentTimeMillis()), session, (DataObject) propertyObject);
    }

    @StackMessage("{logics.info.recalculation.of.aggregated.property}")
    @ThisMessage
    public void recalculateAggregation(SQLSession session, QueryEnvironment env, BaseClass baseClass) throws SQLException, SQLHandledException {
        boolean useRecalculate = Settings.get().isUseRecalculateClassesInsteadOfInconsisentExpr();
        if(useRecalculate)
            recalculateClasses(session, env, baseClass);

        session.pushVolatileStats(OperationOwner.unknown);
        try {
            session.modifyRecords(env == null ?
                    new ModifyQuery(mapTable.table, getRecalculateQuery(false, baseClass, !useRecalculate).map(
                            mapTable.mapKeys.reverse(), MapFact.singletonRev(field, "calcvalue")), OperationOwner.unknown, TableOwner.global) :
                    new ModifyQuery(mapTable.table, getRecalculateQuery(false, baseClass, !useRecalculate).map(
                            mapTable.mapKeys.reverse(), MapFact.singletonRev(field, "calcvalue")), env, TableOwner.global));
        } finally {
            session.popVolatileStats(OperationOwner.unknown);
        }
    }
    
    @IdentityStartLazy
    private Pair<ImRevMap<T,NullableKeyExpr>, Expr> calculateQueryExpr(CalcType calcType) {
        ImRevMap<T,NullableKeyExpr> mapExprs = getMapNotNullKeys();
        return new Pair<>(mapExprs, calculateExpr(mapExprs, calcType));
    }
    
    @IdentityStartLazy
    protected ClassWhere<Object> calcClassValueWhere(CalcClassType calcType) {
        Pair<ImRevMap<T, NullableKeyExpr>, Expr> query = calculateQueryExpr(calcType == CalcClassType.PREVSAME && noOld() ? CalcClassType.PREVBASE : calcType); // оптимизация
        ClassWhere<Object> result = Query.getClassWhere(Where.TRUE, query.first, MapFact.singleton((Object) "value", query.second)); 
        if(calcType == CalcClassType.PREVSAME) // для того чтобы докинуть orAny, собсно только из-за этого infer необходим в любом случае
            result = result.and(inferClassValueWhere(InferType.PREVSAME));
        return result;
    }

    @Override
    @IdentityLazy
    protected boolean calcNotNull(ImSet<T> checkInterfaces, CalcInfoType calcType) {
        Pair<ImRevMap<T, NullableKeyExpr>, Expr> query = calculateQueryExpr(calcType); // оптимизация
        return query.second.getWhere().means(Expr.getWhere(query.first));
    }

    @Override
    @IdentityLazy
    protected boolean calcEmpty(CalcInfoType calcType) {
        return calculateQueryExpr(calcType).second.getWhere().isFalse();
    }

    private ImRevMap<T, NullableKeyExpr> getMapNotNullKeys() {
        return interfaces.mapRevValues(new GetIndexValue<NullableKeyExpr, T>() {
            public NullableKeyExpr getMapValue(int i, T value) {
                return new NullableKeyExpr(i);
            }});
    }

    public StatKeys<T> getInterfaceClassStats() {
        return getInterfaceClassStats(false);
    }
    
    @IdentityStartLazy
    @StackMessage("{message.core.property.get.interface.class.stats}")
    @ThisMessage
    public StatKeys<T> getInterfaceClassStats(boolean alotHeur) {
        ImRevMap<T,KeyExpr> mapKeys = getMapKeys();
        Where where = calculateStatExpr(mapKeys, alotHeur).getWhere();
        mapKeys = mapKeys.filterInclValuesRev(BaseUtils.<ImSet<KeyExpr>>immutableCast(where.getOuterKeys()));
        return where.getFullStatKeys(mapKeys.valuesSet(), StatType.PROP_STATS).mapBack(mapKeys);
    }

    public boolean hasAlotKeys() {
//        if(1==1) return false;
        if(SystemProperties.lightStart) {
            if (!isFull(AlgType.statAlotType))
                return true;
            if (isStored())
                return false;
            return aspectDebugHasAlotKeys();
        }
        return Stat.ALOT.lessEquals(getInterfaceClassStats().getRows());
    }

    public boolean aspectDebugHasAlotKeys() {
        return Stat.ALOT.lessEquals(getInterfaceClassStats(true).getRows());
    }
}
