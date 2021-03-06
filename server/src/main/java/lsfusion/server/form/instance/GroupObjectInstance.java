package lsfusion.server.form.instance;

import lsfusion.base.BaseUtils;
import lsfusion.base.Pair;
import lsfusion.base.Result;
import lsfusion.base.SFunctionSet;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.*;
import lsfusion.base.col.interfaces.mutable.*;
import lsfusion.base.col.interfaces.mutable.add.MAddMap;
import lsfusion.base.col.interfaces.mutable.mapvalue.*;
import lsfusion.interop.ClassViewType;
import lsfusion.interop.Compare;
import lsfusion.interop.Order;
import lsfusion.interop.form.PropertyReadType;
import lsfusion.server.Settings;
import lsfusion.server.caches.IdentityLazy;
import lsfusion.server.classes.BaseClass;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.OrderClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.*;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.FormulaUnionExpr;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.expr.ValueExpr;
import lsfusion.server.data.expr.query.GroupExpr;
import lsfusion.server.data.expr.query.GroupType;
import lsfusion.server.data.expr.where.extra.CompareWhere;
import lsfusion.server.data.expr.where.ifs.IfExpr;
import lsfusion.server.data.query.MapKeysInterface;
import lsfusion.server.data.query.Query;
import lsfusion.server.data.type.Type;
import lsfusion.server.data.where.Where;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.GroupObjectEntity;
import lsfusion.server.form.entity.GroupObjectProp;
import lsfusion.server.form.entity.UpdateType;
import lsfusion.server.form.instance.filter.AndFilterInstance;
import lsfusion.server.form.instance.filter.FilterInstance;
import lsfusion.server.form.instance.filter.OrFilterInstance;
import lsfusion.server.form.instance.listener.CustomClassListener;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.*;
import lsfusion.server.profiler.ProfiledObject;
import lsfusion.server.session.*;
import lsfusion.server.stack.StackMessage;
import lsfusion.server.stack.ThisMessage;

import java.sql.SQLException;
import java.util.*;

import static lsfusion.base.BaseUtils.immutableCast;

public class GroupObjectInstance implements MapKeysInterface<ObjectInstance>, ProfiledObject {
    public final SeekOrderObjects SEEK_HOME = new SeekOrderObjects(false); 
    public final SeekOrderObjects SEEK_END = new SeekOrderObjects(true); 
    public SeekOrderObjects SEEK_PREV() {
        return new SeekOrderObjects(false, getGroupObjectValue());
    }

    public final CalcPropertyObjectInstance propertyBackground;
    public final CalcPropertyObjectInstance propertyForeground;
    private final boolean noClassFilter;
    final static int DIRECTION_DOWN = 1;
    final static int DIRECTION_UP = 2;
    final static int DIRECTION_CENTER = 3;

    RowBackgroundReaderInstance rowBackgroundReader = new RowBackgroundReaderInstance();
    RowForegroundReaderInstance rowForegroundReader = new RowForegroundReaderInstance();

    public final GroupObjectEntity entity;

    public static ImSet<ObjectInstance> getObjects(ImSet<GroupObjectInstance> groups) {
        MExclSet<ObjectInstance> mResult = SetFact.mExclSet();
        for(GroupObjectInstance group : groups)
            mResult.exclAddAll(group.objects);
        return mResult.immutable();
    }

    public static ImOrderSet<ObjectInstance> getOrderObjects(ImOrderSet<GroupObjectInstance> groups) {
        MOrderExclSet<ObjectInstance> mResult = SetFact.mOrderExclSet();
        for(GroupObjectInstance group : groups)
            mResult.exclAddAll(group.getOrderObjects());
        return mResult.immutableOrder();
    }

    public ImSet<ObjectInstance> objects; //
    private final ImOrderSet<ObjectInstance> orderObjects;
    public ImOrderSet<ObjectInstance> getOrderObjects() {
        return orderObjects;
    }

    public String toString() {
        return objects.toString();
    }

    // глобальный идентификатор чтобы писать во GroupObjectTable
    public int getID() {
        return entity.getID();
    }

    public String getSID() {
        return entity.getSID();
    }

    private Integer pageSize;
    public int getPageSize() {
        assert !isInTree();
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        if(entity.pageSize == null){
            if (!pageSize.equals(this.pageSize)) {
                updated |= UPDATED_PAGESIZE;
            }
            this.pageSize = pageSize;
        }
    }

    public GroupObjectInstance(GroupObjectEntity entity, ImOrderSet<ObjectInstance> objects, CalcPropertyObjectInstance propertyBackground, CalcPropertyObjectInstance propertyForeground, ImMap<ObjectInstance, CalcPropertyObjectInstance> parent, ImMap<GroupObjectProp, CalcPropertyRevImplement<ClassPropertyInterface, ObjectInstance>> props) {

        this.entity = entity;

        this.objects = objects.getSet();
        this.orderObjects = objects;

        this.propertyBackground = propertyBackground;
        this.propertyForeground = propertyForeground;

        for(ObjectInstance object : objects)
            object.groupTo = this;

        // текущее состояние
        if (this.curClassView != entity.initClassView) {
            this.curClassView = entity.initClassView;
            this.updated |= UPDATED_CLASSVIEW;
        }
        if(entity.pageSize != null) {
            this.pageSize = entity.pageSize;
        } else {
            this.pageSize = Settings.get().getPageSizeDefaultValue();
        }
        
        this.noClassFilter = entity.noClassFilter;
        this.parent = parent;
        this.props = props;
    }

    public ImRevMap<ObjectInstance, KeyExpr> getMapKeys() {
        return KeyExpr.getMapKeys(objects);
    }

    public Integer order = 0;

    // классовый вид включен или нет
    public ClassViewType curClassView = ClassViewType.DEFAULT;

    // закэшированные

    public ImSet<FilterInstance> setFilters = null;
    public ImSet<FilterInstance> getSetFilters() {
        if(setFilters==null) {
            FilterInstance userComboFilter = combineUserFilters(userFilters);
            ImSet<FilterInstance> userComboSet = userComboFilter != null ? SetFact.singleton(userComboFilter) : userFilters.immutableOrder().getSet();
            setFilters = fixedFilters.merge(userComboSet).merge(SetFact.fromJavaSet(regularFilters)).merge(SetFact.fromJavaSet(tempFilters));
        }
        return setFilters;
    }

    private FilterInstance combineUserFilters(MOrderSet<FilterInstance> filterSet) {
        FilterInstance comboFilter = null;
        List<List<FilterInstance>> organizedFilters = new ArrayList<>();
        List<FilterInstance> orFilters = new ArrayList<>();
        for (FilterInstance filter : filterSet.immutableOrder()) {
            orFilters.add(filter);
            if (filter.junction) {
                organizedFilters.add(orFilters);
                orFilters = new ArrayList<>();
            }
        }
        if (!orFilters.isEmpty())
            organizedFilters.add(orFilters);

        List<FilterInstance> ands = new ArrayList<>();
        for (List<FilterInstance> ors : organizedFilters) {
            FilterInstance filt = null;
            for (FilterInstance filter : ors) {
                if (filt == null) {
                    filt = filter;
                    continue;
                }
                filt = new OrFilterInstance(filt, filter);
            }
            ands.add(filt);
        }

        for (FilterInstance filter : ands) {
            if (comboFilter == null) {
                comboFilter = filter;
                continue;
            }
            comboFilter = new AndFilterInstance(comboFilter, filter);
        }
        return comboFilter;
    }

    // вообще все фильтры
    public ImSet<FilterInstance> fixedFilters = SetFact.EMPTY();

    private MOrderSet<FilterInstance> userFilters = SetFact.mOrderSet();
    public void clearUserFilters() {
        userFilters = SetFact.mOrderSet();

        setFilters = null;
        updated |= UPDATED_FILTER;
    }
    public void addUserFilter(FilterInstance addFilter) {
        userFilters.add(addFilter);

        setFilters = null;
        updated |= UPDATED_FILTER;
    }

    private Set<FilterInstance> regularFilters = new HashSet<>();
    public void addRegularFilter(FilterInstance filter) {
        regularFilters.add(filter);

        setFilters = null;
        updated |= UPDATED_FILTER;
    }

    public void removeRegularFilter(FilterInstance filter) {
        regularFilters.remove(filter);

        setFilters = null;
        updated |= UPDATED_FILTER;
    }

    private Set<FilterInstance> tempFilters = new HashSet<>();
    public void clearTempFilters() {
        tempFilters.clear();

        setFilters = null;
        updated |= UPDATED_FILTER;
    }
    public void addTempFilter(FilterInstance addFilter) {
        tempFilters.add(addFilter);

        setFilters = null;
        updated |= UPDATED_FILTER;
    }

    // с активным интерфейсом
    public ImSet<FilterInstance> filters;

    public ImOrderMap<OrderInstance, Boolean> fixedOrders = MapFact.EMPTYORDER();

    // обертку потому как сложный assertion
    private ImOrderMap<OrderInstance,Boolean> setOrders = null;
    public ImOrderMap<OrderInstance,Boolean> getSetOrders() {
        if(setOrders==null)
            setOrders = userOrders.mergeOrder(fixedOrders).mergeOrder(getOrderObjects().toOrderMap(false));
        return setOrders;
    }
    private ImOrderMap<OrderInstance,Boolean> userOrders = MapFact.EMPTYORDER();

    public void changeOrder(OrderInstance property, Order modiType) {
        ImOrderMap<OrderInstance, Boolean> newOrders;
        if (modiType == Order.REPLACE) {
            newOrders = MapFact.singletonOrder(property, false);
        } else if (modiType == Order.REMOVE) {
            newOrders = userOrders.removeOrderIncl(property);
        } else if (modiType == Order.DIR) {
            if(userOrders.containsKey(property))
                newOrders = userOrders.replaceValue(property, !userOrders.get(property));
            else
                newOrders = userOrders.addOrderExcl(property, true);
        } else {
            assert modiType == Order.ADD;
            newOrders = userOrders.addOrderExcl(property, false);
        }

        if(!BaseUtils.hashEquals(newOrders, userOrders)) {// оптимизация для пользовательских настроек
            userOrders = newOrders;
            setOrders = null;
            updated |= UPDATED_ORDER;
        }
    }

    public void clearOrders() {
        if(!userOrders.isEmpty()) { // оптимизация для пользовательских настроек
            userOrders = MapFact.EMPTYORDER();
            setOrders = null;
            updated |= UPDATED_ORDER;
        }
    }

    // с активным интерфейсом, assertion что содержит все ObjectInstance
    public ImOrderMap<OrderInstance,Boolean> orders = MapFact.EMPTYORDER();

    boolean upKeys, downKeys;
    public ImOrderMap<ImMap<ObjectInstance,DataObject>,ImMap<OrderInstance,ObjectValue>> keys = MapFact.EMPTYORDER();

    // 0 !!! - изменился объект, 1 - класс объекта, 2 !!! - отбор, 3 !!! - хоть один класс, 4 !!! - классовый вид

    public final static int UPDATED_OBJECT = (1);
    public final static int UPDATED_KEYS = (1 << 2);
    public final static int UPDATED_GRIDCLASS = (1 << 3);
    public final static int UPDATED_CLASSVIEW = (1 << 4);
    public final static int UPDATED_ORDER = (1 << 5);
    public final static int UPDATED_FILTER = (1 << 6);
    public final static int UPDATED_EXPANDS = (1 << 7);
    public final static int UPDATED_PAGESIZE = (1 << 8);

    public int updated = UPDATED_GRIDCLASS | UPDATED_ORDER | UPDATED_FILTER;

    private boolean assertNull() {
        Iterator<ObjectInstance> it = objects.iterator();
        boolean isNull = it.next().getObjectValue() instanceof NullValue; 
        for(ObjectInstance object : objects)
            if((object.getObjectValue() instanceof NullValue)!=isNull)
                return false;
        return true;
    }

    public boolean isNull() {
//        assert assertNull();
//        return objects.iterator().next().getObjectValue() instanceof NullValue;
        for (ObjectInstance object : GroupObjectInstance.getObjects(getUpTreeGroups()))
            if (object.getObjectValue() instanceof NullValue) return true;
        return false;
    }

    public ImMap<ObjectInstance,DataObject> getGroupObjectValue() {
        if(isNull())
            return MapFact.EMPTY();

        return GroupObjectInstance.getObjects(getUpTreeGroups()).mapValues(new GetValue<DataObject, ObjectInstance>() {
            public DataObject getMapValue(ObjectInstance value) {
                return value.getDataObject();
            }});
    }

    public ImMap<ObjectInstance,DataObject> findGroupObjectValue(ImMap<ObjectInstance,Object> map) {
        for(ImMap<ObjectInstance, DataObject> keyRow : keys.keyIt()) {
            boolean equal = true;
            for(int i=0,size=keyRow.size();i<size;i++) {
                if(!keyRow.getValue(i).object.equals(map.get(keyRow.getKey(i)))) {
                    equal = false;
                    break;
                }
            }
            if(equal) {
                return keyRow;
            }
        }

        // из-за работы с вебом, может прийти несинхронный запрос на изменение объекта
        // и соответственно ключ будет не найден, поэтому возвращаем null, вместо исключения,
        // чтобы игнорировать этот запрос
        return null;
//        throw new RuntimeException("key not found");
    }

    public UpdateType getUpdateType() throws SQLException, SQLHandledException {
        return entity.getUpdateType(this);
    }

    @Override
    public Object getProfiledObject() {
        return entity;
    }

    public interface FilterProcessor {
        ImMap<ObjectInstance, ? extends Expr> process(FilterInstance filt, ImMap<ObjectInstance, ? extends Expr> mapKeys);

        ImSet<FilterInstance> getFilters();
    }

    public Where getFilterWhere(ImMap<ObjectInstance, ? extends Expr> mapKeys, Modifier modifier, ReallyChanged reallyChanged, FilterProcessor filterProcessor, MSet<CalcProperty> mUsedProps) throws SQLException, SQLHandledException {
        Where where = Where.TRUE;
        for(FilterInstance filt : (filterProcessor != null ? filterProcessor.getFilters() : filters)) {
            if(filterProcessor != null) {
                ImMap<ObjectInstance, ? extends Expr> overridedKeys = filterProcessor.process(filt, mapKeys);
                if(overridedKeys == null)
                    continue;
                mapKeys = overridedKeys;
            }
            where = where.and(filt.getWhere(mapKeys, modifier, reallyChanged, mUsedProps));
        }
        return where;
    }

    public static ImMap<ObjectInstance, ValueClass> getGridClasses(ImSet<ObjectInstance> objects) {
        return objects.mapValues(new GetValue<ValueClass, ObjectInstance>() {
            public ValueClass getMapValue(ObjectInstance value) {
                return value.getGridClass();
            }});
    }
    public Where getClassWhere(ImMap<ObjectInstance, ? extends Expr> mapKeys, Modifier modifier, MSet<CalcProperty> mUsedProps) throws SQLException, SQLHandledException {
        if(noClassFilter)
            return Where.TRUE;
        return IsClassProperty.getWhere(getGridClasses(objects), mapKeys, modifier, mUsedProps);
    }

    public Where getWhere(ImMap<ObjectInstance, ? extends Expr> mapKeys, Modifier modifier, ReallyChanged reallyChanged) throws SQLException, SQLHandledException {
        return getWhere(mapKeys, modifier, reallyChanged, (MSet<CalcProperty>) null);
    }
    public Where getWhere(ImMap<ObjectInstance, ? extends Expr> mapKeys, Modifier modifier, ReallyChanged reallyChanged, MSet<CalcProperty> mUsedProps) throws SQLException, SQLHandledException {
        return getWhere(mapKeys, modifier, reallyChanged, null, mUsedProps);
    }
    public Where getWhere(ImMap<ObjectInstance, ? extends Expr> mapKeys, Modifier modifier, ReallyChanged reallyChanged, FilterProcessor processor) throws SQLException, SQLHandledException {
        return getWhere(mapKeys, modifier, reallyChanged, processor, null);
    }
    public Where getWhere(ImMap<ObjectInstance, ? extends Expr> mapKeys, Modifier modifier, ReallyChanged reallyChanged, FilterProcessor processor, MSet<CalcProperty> mUsedProps) throws SQLException, SQLHandledException {
        return getFilterWhere(mapKeys, modifier, reallyChanged, processor, mUsedProps).and(getClassWhere(mapKeys, modifier, mUsedProps));
    }

    public Where getWhere(ImMap<ObjectInstance, ? extends Expr> mapKeys, Modifier modifier) throws SQLException, SQLHandledException {
        return getWhere(mapKeys, modifier, null);
    }

    public ImMap<ObjectInstance,ObjectValue> getNulls() {
        return NullValue.getMap(getObjects(getUpTreeGroups()));
    }

    boolean isSolid() {
        Iterator<ObjectInstance> i = objects.iterator();
        boolean read = i.next() instanceof CustomObjectInstance;
        while(i.hasNext())
            if(read != i.next() instanceof CustomObjectInstance)
                return false;
        return true;
    }

    public void setClassListener(CustomClassListener classListener) {
        for (ObjectInstance object : objects)
            if (object instanceof CustomObjectInstance)
                ((CustomObjectInstance)object).setClassListener(classListener);
    }

    public boolean isInTree() {
        return treeGroup!=null || parent!=null;
    }

    public TreeGroupInstance treeGroup = null;
    public ImOrderSet<GroupObjectInstance> getOrderDownTreeGroups() {
        return treeGroup!=null?treeGroup.getDownTreeGroups(this):SetFact.<GroupObjectInstance>EMPTYORDER();
    }
    public ImSet<GroupObjectInstance> getDownTreeGroups() {
        return getOrderDownTreeGroups().getSet();
    }
    public GroupObjectInstance getUpTreeGroup() {
        return treeGroup!=null?treeGroup.getUpTreeGroup(this):null;
    }
    public ImOrderSet<GroupObjectInstance> getOrderUpTreeGroups() {
        return treeGroup!=null?treeGroup.getUpTreeGroups(this):SetFact.singletonOrder(this);
    }
    public ImSet<GroupObjectInstance> getUpTreeGroups() {
        return getOrderUpTreeGroups().getSet();
    }

    public static ImSet<GroupObjectInstance> getUpTreeGroups(ImSet<GroupObjectInstance> groups) {
        MSet<GroupObjectInstance> mResult = SetFact.mSet();
        for(GroupObjectInstance group : groups)
            mResult.addAll(group.getUpTreeGroups());
        return mResult.immutable();
    }

    public final ImMap<ObjectInstance, CalcPropertyObjectInstance> parent;

    // поиски по свойствам\объектам
    public SeekObjects userSeeks = null;

    public void addSeek(OrderInstance order, ObjectValue value, boolean addSeek) {
        if(userSeeks==null || userSeeks == SEEK_NULL)
            userSeeks = SEEK_PREV();
        userSeeks = ((SeekOrderObjects)userSeeks).add(order, value, addSeek);
    }

    public void dropSeek(ObjectInstance object) {
        if(userSeeks==null || userSeeks == SEEK_NULL)
            userSeeks = SEEK_PREV();
        userSeeks = ((SeekOrderObjects)userSeeks).remove(object);
    }

    public SeekObjects getSeekObjects(UpdateType seek) {
        switch (seek) {
            case FIRST:
                return SEEK_HOME;
            case LAST:
                return SEEK_END;
            case NULL:
                return SEEK_NULL;
            case PREV:
                return SEEK_PREV();
        }
        throw new UnsupportedOperationException();
    }
    public void seek(UpdateType seek) {
        userSeeks = getSeekObjects(seek);
    }
    public void seek(boolean end) {
        userSeeks = end ? SEEK_END : SEEK_HOME;
    }

    @IdentityLazy
    public ImSet<DataObjectInstance> getFreeDataObjects() throws SQLException, SQLHandledException {

        final ImRevMap<ObjectInstance, KeyExpr> mapKeys = getMapKeys();

        final ImSet<KeyExpr> usedContext = immutableCast(getFilterWhere(mapKeys, Property.defaultModifier, null, null, null).getOuterKeys());

        return immutableCast(objects.filterFn(new SFunctionSet<ObjectInstance>() {
            public boolean contains(ObjectInstance object) { // если DataObject и нету ключей
                return object instanceof DataObjectInstance && !usedContext.contains(mapKeys.get(object));
            }
        }));
    }

    public NoPropertyTableUsage<ObjectInstance> keyTable = null;
    public NoPropertyTableUsage<ObjectInstance> expandTable = null;

    // так как в updateKeys мы не drop'аем таблицу (чтобы не collapse'илось дерево) надо обновлять классы
    public void updateExpandClasses(final UpdateCurrentClassesSession session) throws SQLException, SQLHandledException {
        if(expandTable != null) {
            final SessionData sessionData = expandTable.saveData();
            expandTable.updateCurrentClasses(session);
            session.addRollbackInfo(new SQLRunnable() {
                public void run() throws SQLException, SQLHandledException {
                    OperationOwner owner = session.env.getOpOwner();
                    expandTable.drop(session.sql, owner);
                    expandTable.rollData(session.sql, sessionData, owner);
                }
            });
        }
    }

    private Where getExpandWhere(ImMap<ObjectInstance, ? extends Expr> mapKeys) {
        if(expandTable==null)
            return Where.FALSE;
        else
            return expandTable.getWhere(mapKeys);
    }

    private ImOrderMap<ImMap<ObjectInstance, DataObject>, ImMap<Object, ObjectValue>> executeTree(SQLSession session, QueryEnvironment env, final Modifier modifier, BaseClass baseClass, ReallyChanged reallyChanged) throws SQLException, SQLHandledException {
        assert isInTree();

        final ImRevMap<ObjectInstance, KeyExpr> mapKeys = KeyExpr.getMapKeys(GroupObjectInstance.getObjects(getUpTreeGroups()));

        MExclMap<Object, Expr> mPropertyExprs = MapFact.mExclMap();

        Where expandWhere;
        if(getUpTreeGroup()!=null)
            expandWhere = getUpTreeGroup().getExpandWhere(mapKeys); // для верхней группы брать только из expandTable'а
        else
            expandWhere = Where.TRUE;

        if (parent != null) {
            ImMap<ObjectInstance, Expr> parentExprs = parent.mapValuesEx(new GetExValue<Expr, CalcPropertyObjectInstance, SQLException, SQLHandledException>() {
                public Expr getMapValue(CalcPropertyObjectInstance value) throws SQLException, SQLHandledException {
                    return value.getExpr(mapKeys, modifier);
                }
            });

            Where nullWhere = Where.FALSE;
            for (Expr parentExpr : parentExprs.valueIt()) {
                nullWhere = nullWhere.or(parentExpr.getWhere().not());
            }
            expandWhere = expandWhere.and(nullWhere).or(getExpandWhere(MapFact.override(mapKeys, parentExprs))); // если есть parent, то те, чей parent равен null или expanded

            mPropertyExprs.exclAddAll(parentExprs);

            mPropertyExprs.exclAdd("expandable", GroupExpr.create(parentExprs, ValueExpr.TRUE, GroupType.LOGICAL(), mapKeys.filter(parentExprs.keys()))); // boolean
            if (treeGroup != null) {
                GroupObjectInstance subGroup = treeGroup.getDownTreeGroup(this);
                if (subGroup != null) {
                    //если не последняя группа
                    mPropertyExprs.exclAdd("expandable2", subGroup.getHasSubElementsExpr(mapKeys, modifier, reallyChanged));
                }
            }
        }

        ImOrderMap<Expr, Boolean> orderExprs = orders.mapMergeOrderKeysEx(new GetExValue<Expr, OrderInstance, SQLException, SQLHandledException>() {
            public Expr getMapValue(OrderInstance value) throws SQLException, SQLHandledException {
                return value.getExpr(mapKeys, modifier);
            }
        });

        return new Query<>(mapKeys, mPropertyExprs.immutable(), getWhere(mapKeys, modifier, reallyChanged).and(expandWhere)).
                    executeClasses(session, env, baseClass, orderExprs);
    }

    private Expr getHasSubElementsExpr(final ImRevMap<ObjectInstance, KeyExpr> outerMapKeys, final Modifier modifier, final ReallyChanged reallyChanged) throws SQLException, SQLHandledException {
        final ImRevMap<ObjectInstance, KeyExpr> mapKeys = KeyExpr.getMapKeys(GroupObjectInstance.getObjects(getUpTreeGroups()));

        Where subGroupWhere = Where.TRUE;

        if (parent != null) {
            ImMap<ObjectInstance, Expr> parentExprs = parent.mapValuesEx(new GetExValue<Expr, CalcPropertyObjectInstance, SQLException, SQLHandledException>() {
                public Expr getMapValue(CalcPropertyObjectInstance value) throws SQLException, SQLHandledException {
                    return value.getExpr(mapKeys, modifier);
                }
            });

            Where nullWhere = Where.FALSE;
            for (Expr parentExpr : parentExprs.valueIt()) {
                nullWhere = nullWhere.or(parentExpr.getWhere().not());
            }
            subGroupWhere = subGroupWhere.and(nullWhere);
        }

        subGroupWhere = subGroupWhere.and(getWhere(mapKeys, modifier, reallyChanged));

        Expr validSubElementExpr = IfExpr.create(subGroupWhere, ValueExpr.TRUE, ValueExpr.NULL);

        final ImMap<ObjectInstance, KeyExpr> upKeys = mapKeys.remove(objects);
        return GroupExpr.create(upKeys, validSubElementExpr, GroupType.LOGICAL(), outerMapKeys); // boolean
    }

    public void change(SessionChanges session, ImMap<ObjectInstance, DataObject> value, FormInstance eventForm, ExecutionStack stack) throws SQLException, SQLHandledException {
        // проставим все объектам метки изменений
        ImSet<ObjectInstance> upGroups = GroupObjectInstance.getObjects(getUpTreeGroups());
        assert value.isEmpty() || value.keys().equals(upGroups);
        for (ObjectInstance object : upGroups)
            object.changeValue(session, value.isEmpty()?NullValue.instance:value.get(object));
        ImSet<ObjectInstance> downGroups = GroupObjectInstance.getObjects(getDownTreeGroups());
        for(ObjectInstance object : downGroups)
            object.changeValue(session, NullValue.instance);

        eventForm.changeGroupObject(upGroups.addExcl(downGroups), stack);
    }

    public void update(SessionChanges session, MFormChanges changes, FormInstance eventForm, ImMap<ObjectInstance, DataObject> value, ExecutionStack stack) throws SQLException, SQLHandledException {
        changes.objects.exclAdd(this, value.isEmpty() ? NullValue.getMap(getObjects(getUpTreeGroups())) : value);
        change(session, value, eventForm, stack);
    }

    public ImMap<GroupObjectProp, CalcPropertyRevImplement<ClassPropertyInterface, ObjectInstance>> props;

    // вообще касается всего что идет после проверки на hidden, можно было бо обобщить, но пока нет смысла
    private boolean pendingHiddenUpdateKeys;
    private boolean pendingHiddenUpdateObjects;
    
    private boolean hasUpdateEnvironmentIncrementProp(GroupObjectProp propType) { // оптимизация
        return props.get(propType) != null;
    }
    
    private MAddMap<GroupObjectProp, ImSet<CalcProperty>> usedEnvironmentIncrementProps = MapFact.mAddOverrideMap();
    public ImSet<CalcProperty> getUsedEnvironmentIncrementProps(GroupObjectProp propType) {
        return usedEnvironmentIncrementProps.get(propType);
    }
            
    public void updateEnvironmentIncrementProp(IncrementChangeProps environmentIncrement, final Modifier modifier, Result<ChangedData> changedProps, final ReallyChanged reallyChanged, GroupObjectProp propType, boolean propsChanged, boolean dataChanged) throws SQLException, SQLHandledException {
        CalcPropertyRevImplement<ClassPropertyInterface, ObjectInstance> mappedProp = props.get(propType);
        if(mappedProp != null) {
            MSet<CalcProperty> mUsedProps = null;
            if(propsChanged)
                mUsedProps = SetFact.mSet();
            
            final ImRevMap<ObjectInstance, KeyExpr> mapKeys = getMapKeys();
            PropertyChange<ClassPropertyInterface> change;
            switch (propType) {
                case FILTER:
                    change = new PropertyChange<>(mappedProp.mapping.join(mapKeys), ValueExpr.TRUE, getWhere(mapKeys, modifier, reallyChanged));
                    break;
                case ORDER:
                    if(orders.isEmpty())
                        change = mappedProp.property.getNoChange();
                    else {
                        final MSet<CalcProperty> fmUsedProps = mUsedProps;
                        ImOrderMap<Expr, Boolean> orderExprs = orders.mapOrderKeysEx(new GetExValue<Expr, OrderInstance, SQLException, SQLHandledException>() {
                            public Expr getMapValue(OrderInstance value) throws SQLException, SQLHandledException {
                                return value.getExpr(mapKeys, modifier, reallyChanged, fmUsedProps);
                            }
                        });
                        OrderClass orderClass = OrderClass.get(orders.keyOrderSet().mapListValues(new GetValue<Type, OrderInstance>() {
                            public Type getMapValue(OrderInstance value) {
                                return value.getType();
                            }}), orderExprs.valuesList());
                        change = new PropertyChange<>(mappedProp.mapping.join(mapKeys), FormulaUnionExpr.create(orderClass, orderExprs.keyOrderSet()));
                    }
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            environmentIncrement.add(mappedProp.property, change, dataChanged);
            if(propsChanged)
                usedEnvironmentIncrementProps.add(propType, mUsedProps.immutable());

            if(changedProps != null)
                changedProps.set(changedProps.result.merge(new ChangedData(SetFact.singleton((CalcProperty) mappedProp.property), false)));
        }
    }

    @StackMessage("{message.form.update.group.keys}")
    @ThisMessage
    public ImMap<ObjectInstance, DataObject> updateKeys(SQLSession sql, QueryEnvironment env, final Modifier modifier, IncrementChangeProps environmentIncrement, ExecutionEnvironment execEnv, BaseClass baseClass, boolean hidden, final boolean refresh, MFormChanges result, Result<ChangedData> changedProps, ReallyChanged reallyChanged) throws SQLException, SQLHandledException {
        if (refresh || (updated & UPDATED_CLASSVIEW) != 0) {
            result.classViews.exclAdd(this, curClassView);
        }

        if (keyTable == null) // в общем то только для hidden'а но может и потом понадобиться
            keyTable = createKeyTable("upktable-" + System.identityHashCode(execEnv));

        boolean isGrid = curClassView.isGrid();

        // если изменились класс грида или представление
        boolean updateFilters = refresh || (updated & (UPDATED_GRIDCLASS | UPDATED_CLASSVIEW)) != 0;
        
        boolean objectsUpdated = false;

        ImSet<FilterInstance> setFilters = getSetFilters();
        ImSet<GroupObjectInstance> sThis = SetFact.singleton(this);

        if (FilterInstance.ignoreInInterface) {
            updateFilters |= (updated & UPDATED_FILTER) != 0;
            filters = setFilters;
        } else {
            if ((updated & UPDATED_FILTER) != 0) {
                ImSet<FilterInstance> newFilters = setFilters.filterFn(new SFunctionSet<FilterInstance>() {
                    public boolean contains(FilterInstance filt) {
                        return filt.isInInterface(GroupObjectInstance.this);
                    }
                });

                updateFilters |= !BaseUtils.hashEquals(newFilters, filters);
                filters = newFilters;
            } else { // остались те же setFilters
                for (FilterInstance filt : setFilters)
                    if (refresh || filt.classUpdated(sThis)) {
                        boolean inInterface = filt.isInInterface(this);
                        if(inInterface != filters.contains(filt)) {
                            if(inInterface)
                                filters = filters.addExcl(filt);
                            else
                                filters = filters.removeIncl(filt);
                            updateFilters = true;
                        }
                    }
            }
        }

        if (!updateFilters) // изменились "верхние" объекты для фильтров
            for (FilterInstance filt : filters)
                if (filt.objectUpdated(sThis)) {
                    updateFilters = true;
                    objectsUpdated = true;
                    break;
                }

        if (!updateFilters) // изменились данные по фильтрам
            for (FilterInstance filt : filters)
                if (filt.dataUpdated(changedProps.result, reallyChanged, modifier, hidden, sThis)) {
                    updateFilters = true;
                    break;
                }
        if (!updateFilters) // классы удалились\добавились
            for (ObjectInstance object : objects)
                if (object.classChanged(changedProps.result)) {  // || object.classUpdated() сомнительный or
                    updateFilters = true;
                    break;
                }

        if(updateFilters) // изменились фильтры, надо обновить свойства созданные при помощи соответствующих операторов форм, сейчас будет определенная избыточность для dataUpdated (так как через eventChange уже должны изменится), но пока не критично
            updateEnvironmentIncrementProp(environmentIncrement, modifier, changedProps, reallyChanged, GroupObjectProp.FILTER, true, true);

        boolean updateOrders = false;
        boolean hasOrderProperty = hasUpdateEnvironmentIncrementProp(GroupObjectProp.ORDER); // оптимизация

        // порядки
        if(OrderInstance.ignoreInInterface) {
            updateOrders |= (updated & UPDATED_ORDER) != 0;
            orders = getSetOrders();
        } else {
            ImOrderMap<OrderInstance, Boolean> setOrders = getSetOrders();
            ImOrderMap<OrderInstance, Boolean> newOrders;
            if ((updated & UPDATED_ORDER) != 0) {
                newOrders = setOrders.filterOrder(new SFunctionSet<OrderInstance>() {
                    public boolean contains(OrderInstance orderInstance) {
                        return orderInstance.isInInterface(GroupObjectInstance.this);
                    }
                });
            } else { // значит setOrders не изменился
                newOrders = setOrders.filterOrder(new SFunctionSet<OrderInstance>() {
                    public boolean contains(OrderInstance orderInstance) {
                        boolean isInInterface = orders.containsKey(orderInstance);
                        if ((refresh || orderInstance.classUpdated(SetFact.singleton(GroupObjectInstance.this))) && !(orderInstance.isInInterface(GroupObjectInstance.this) == isInInterface)) {
                            isInInterface = !isInInterface;
                        }
                        return isInInterface;
                    }});
            }
            updateOrders |= !orders.equals(newOrders);
            orders = newOrders;
        }

        if (!updateOrders && (!updateFilters || hasOrderProperty)) // изменились "верхние" объекты для порядков
            for (OrderInstance order : orders.keyIt())
                if (order.objectUpdated(sThis)) {
                    updateOrders = true;
                    objectsUpdated = true;
                    break;
                }
        if (!updateOrders && (!updateFilters || hasOrderProperty)) // изменились данные по порядкам
            for (OrderInstance order : orders.keyIt())
                if (order.dataUpdated(changedProps.result, reallyChanged, modifier, hidden, sThis)) {
                    updateOrders = true;
                    break;
                }

        if(updateOrders) // изменились порядки, надо обновить свойства созданные при помощи соответствующих операторов форм, сейчас будет определенная избыточность для dataUpdated (так как через eventChange уже должны изменится), но пока не критично
            updateEnvironmentIncrementProp(environmentIncrement, modifier, changedProps, reallyChanged, GroupObjectProp.ORDER, true, true);

        boolean updateKeys = updateFilters || updateOrders;

        if(hidden) {
            pendingHiddenUpdateKeys |= updateKeys;
            pendingHiddenUpdateObjects |= objectsUpdated;
            return null;
        } else {
            updateKeys |= pendingHiddenUpdateKeys;
            objectsUpdated |= pendingHiddenUpdateObjects;
            pendingHiddenUpdateKeys = false;
            pendingHiddenUpdateObjects = false;
        }

        ImMap<ObjectInstance, DataObject> currentObject = getGroupObjectValue();
        SeekObjects seeks = null;
        int direction = DIRECTION_CENTER;

        if(isInTree()) {
            if (!updateKeys && (getUpTreeGroup() != null && ((getUpTreeGroup().updated & UPDATED_EXPANDS) != 0))) {
                updateKeys = true;
            }
            if(parent != null) {
                if(!updateKeys && (updated & UPDATED_EXPANDS) != 0)
                    updateKeys = true;
                if(!updateKeys) {
                    for(CalcPropertyObjectInstance parentProp : parent.valueIt()) {
                        if (parentProp.objectUpdated(sThis)) {
                            updateKeys = true;
                            break;
                        }
                    }
                    for(CalcPropertyObjectInstance parentProp : parent.valueIt()) {
                        if (parentProp.dataUpdated(changedProps.result, reallyChanged, modifier, hidden, sThis)) {
                            updateKeys = true;
                            break;
                        }
                    }
                }
            }
            if (userSeeks!=null) // if there is user seek, force refresh (because delete for example uses user seeks to update view), this hack will be gone when seeks will be supported in tree
                updateKeys = true;
            seeks = SEEK_HOME;
        } else {
            if (userSeeks!=null) { // пользовательский поиск
                seeks = userSeeks;
                updateKeys = true;
                currentObject = MapFact.EMPTY();
                userSeeks = null;
            } else if (updateKeys) {
                UpdateType updateType = getUpdateType();
                if(!objectsUpdated) // не изменились фильтры, порядки, вид, ищем текущий объект
                    updateType = UpdateType.PREV;
                seeks = getSeekObjects(updateType);
            }

            if (!updateKeys && isGrid && !currentObject.isEmpty() && (updated & (UPDATED_OBJECT | UPDATED_PAGESIZE)) != 0) { // скроллирование
                int keyNum = keys.indexOf(currentObject);
                if (upKeys && keyNum < getPageSize()) { // если меньше PageSize осталось и сверху есть ключи
                    updateKeys = true;

                    int lowestInd = getPageSize() * 2 - 1;
                    if (lowestInd >= keys.size()) // по сути END
                        seeks = SEEK_END;
                    else {
                        direction = DIRECTION_UP;
                        seeks = new SeekOrderObjects(keys.getValue(lowestInd), false);
                    }
                } else // наоборот вниз
                    if (downKeys && keyNum >= keys.size() - getPageSize()) { // assert что pageSize не null
                        updateKeys = true;

                        int highestInd = keys.size() - getPageSize() * 2;
                        if (highestInd < 0) // по сути HOME
                            seeks = SEEK_HOME;
                        else {
                            direction = DIRECTION_DOWN;
                            seeks = new SeekOrderObjects(keys.getValue(highestInd), false);
                        }
                    }
            }
        }

        if (updateKeys) {
            assert seeks != null;

            updated = (updated | UPDATED_KEYS);

            if (!isGrid) { // панель
                ImMap<ObjectInstance, DataObject> readKeys = seeks.readKeys(sql, env, modifier, baseClass, reallyChanged);
                updateViewProperty(execEnv, readKeys);
                return readKeys;
            } else {
                int activeRow = -1; // какой ряд выбранным будем считать
                SeekOrderObjects orderSeeks;
                if(seeks == SEEK_NULL)
                    orderSeeks = SEEK_HOME;
                else
                    orderSeeks = (SeekOrderObjects)seeks;

                if (isInTree()) { // если дерево, то без поиска, но возможно с parent'ами
                    assert orderSeeks.values.isEmpty() && !orderSeeks.end;
                    
//                    if(updateFilters) { // неудобно когда дерево сворачивается каждый раз
//                        if(expandTable !=null) { // потому как могут уже скажем классы стать не актуальными после применения
//                            expandTable.drop(sql, env.getOpOwner());
//                            expandTable = null;
//                        }
//                    }

                    ImOrderMap<ImMap<ObjectInstance, DataObject>, ImMap<Object, ObjectValue>> treeElements = executeTree(sql, env, modifier, baseClass, reallyChanged);

                    ImList<ImMap<ObjectInstance, DataObject>> expandParents = treeElements.mapListValues(
                            new GetValue<ImMap<ObjectInstance, DataObject>, ImMap<Object, ObjectValue>>() {
                                @Override
                                public ImMap<ObjectInstance, DataObject> getMapValue(ImMap<Object, ObjectValue> value) {
                                    return immutableCast(
                                            value.filterFn(
                                                    new GetKeyValue<Boolean, Object, ObjectValue>() {
                                                        @Override
                                                        public Boolean getMapValue(Object key, ObjectValue value) {
                                                            return key instanceof ObjectInstance && value instanceof DataObject;
                                                        }
                                                    }));
                                }
                            });
                    keys = treeElements.mapOrderValues(new GetStaticValue<ImMap<OrderInstance, ObjectValue>>() {
                        public ImMap<OrderInstance, ObjectValue> getMapValue() {
                            return MapFact.EMPTY();
                        }});

                    ImOrderMap<ImMap<ObjectInstance, DataObject>, Boolean> groupExpandables =
                            treeElements
                                    .filterOrderValuesMap(
                                            new SFunctionSet<ImMap<Object, ObjectValue>>() {
                                                @Override
                                                public boolean contains(ImMap<Object, ObjectValue> element) {
                                                    return element.containsKey("expandable") || element.containsKey("expandable2");
                                                }
                                            })
                                    .mapOrderValues(
                                            new GetValue<Boolean, ImMap<Object, ObjectValue>>() {
                                                @Override
                                                public Boolean getMapValue(ImMap<Object, ObjectValue> value) {
                                                    ObjectValue expandable1 = value.get("expandable");
                                                    ObjectValue expandable2 = value.get("expandable2");
                                                    return expandable1 instanceof DataObject || expandable2 instanceof DataObject;
                                                }
                                            });

                    result.expandables.exclAdd(this, groupExpandables.getMap());
                    result.parentObjects.exclAdd(this, expandParents);
                    activeRow = keys.size() == 0 ? -1 : 0;
                } else {
                    keys = MapFact.EMPTYORDER();

                    if (!orders.starts(orderSeeks.values.keys())) // если не "хватает" спереди ключей, дочитываем
                        orderSeeks = orderSeeks.readValues(sql, env, modifier, baseClass, reallyChanged);

                    if (direction == DIRECTION_CENTER) { // оптимизируем если HOME\END, то читаем одним запросом
                        if(orderSeeks.values.isEmpty()) {
                            if (orderSeeks.end) { // END
                                direction = DIRECTION_UP;
                                downKeys = false;
                            } else { // HOME
                                direction = DIRECTION_DOWN;
                                upKeys = false;
                            }
                        }
                    } else {
                        downKeys = true;
                        upKeys = true;
                        assert !orderSeeks.values.isEmpty();
                    }

                    int readSize = getPageSize() * 3 / (direction == DIRECTION_CENTER ? 2 : 1);

                    if (direction == DIRECTION_UP || direction == DIRECTION_CENTER) { // сначала Up
                        keys = keys.addOrderExcl(orderSeeks.executeOrders(sql, env, modifier, baseClass, readSize, false, reallyChanged).reverseOrder());
                        upKeys = (keys.size() == readSize);
                        activeRow = keys.size() - 1;
                    }
                    if (direction == DIRECTION_DOWN || direction == DIRECTION_CENTER) { // затем Down
                        ImOrderMap<ImMap<ObjectInstance, DataObject>, ImMap<OrderInstance, ObjectValue>> executeList = orderSeeks.executeOrders(sql, env, modifier, baseClass, readSize, true, reallyChanged);
                        if (executeList.size() > 0 && !(orderSeeks.end && activeRow>0)) activeRow = keys.size(); // не выбираем если идет seekDown и уже выбран ряд - это то что надо
                        keys = keys.addOrderExcl(executeList);
                        downKeys = (executeList.size() == readSize);
                    }
                }

                // параллельно будем обновлять ключи чтобы JoinSelect'ить
                keyTable.writeKeys(sql, keys.keys(), env.getOpOwner());
                result.gridObjects.exclAdd(this, keys.keyOrderSet());

                updateViewProperty(execEnv, keyTable);
                
                if(seeks == SEEK_NULL)
                    return MapFact.EMPTY();

                if (!keys.containsKey(currentObject)) { // если нету currentObject'а, его нужно изменить
                    if(getUpTreeGroup()==null) // если верхняя группа
                        return activeRow>=0?keys.getKey(activeRow):MapFact.<ObjectInstance,DataObject>EMPTY();
                    else // иначе assertion что activeRow < 0, выбираем верхнюю
                        return keys.size()>0 && !currentObject.isEmpty()?keys.getKey(0):null;
                } else // так как сейчас клиент требует прислать ему groupObject даже если он не изменился, если приходят ключи
                    return currentObject;
            }
        }

        return null; // ничего не изменилось
    }

    private void updateViewProperty(ExecutionEnvironment execEnv, ImMap<ObjectInstance, DataObject> keys) throws SQLException, SQLHandledException {
        CalcPropertyRevImplement<ClassPropertyInterface, ObjectInstance> viewProperty = props.get(GroupObjectProp.VIEW);
        if(viewProperty != null) {
            updateViewProperty(execEnv, viewProperty, keys.isEmpty() ? new PropertyChange<>(viewProperty.property.getMapKeys(), ValueExpr.TRUE, Where.FALSE) :
                    new PropertyChange<>(ValueExpr.TRUE, viewProperty.mapping.join(keys)));
        }
    }
    
    private void updateViewProperty(ExecutionEnvironment execEnv, NoPropertyTableUsage<ObjectInstance> keyTable1) throws SQLException, SQLHandledException {
        CalcPropertyRevImplement<ClassPropertyInterface, ObjectInstance> viewProperty = props.get(GroupObjectProp.VIEW);
        if(viewProperty != null) {
            ImRevMap<ObjectInstance, KeyExpr> mapKeys = getMapKeys();
            updateViewProperty(execEnv, viewProperty, new PropertyChange<>(viewProperty.mapping.join(mapKeys), ValueExpr.TRUE, keyTable1.join(mapKeys).getWhere()));
        }
    }

    private void updateViewProperty(ExecutionEnvironment execEnv, CalcPropertyRevImplement<ClassPropertyInterface, ObjectInstance> viewProperty, PropertyChange<ClassPropertyInterface> change) throws SQLException, SQLHandledException {
        execEnv.getSession().dropChanges((SessionDataProperty)viewProperty.property);
        execEnv.change(viewProperty.property, change);
    }

    public ImOrderMap<ImMap<ObjectInstance, DataObject>, ImMap<OrderInstance, ObjectValue>> seekObjects(SQLSession sql, QueryEnvironment env, Modifier modifier, BaseClass baseClass, int readSize) throws SQLException, SQLHandledException {
        SeekOrderObjects orderSeeks = new SeekOrderObjects(keys.getValue(keys.indexOf(getGroupObjectValue())), false);
        return orderSeeks.executeOrders(sql, env, modifier, baseClass, readSize, true, null);
    }

    public ImOrderSet<ImMap<ObjectInstance, DataObject>> createObjects(DataSession session, FormInstance form, int quantity, ExecutionStack stack) throws SQLException, SQLHandledException {
        if (objects.size() > 1) {
            return SetFact.EMPTYORDER();
        }
        MOrderExclSet<ImMap<ObjectInstance, DataObject>> mResultSet = SetFact.mOrderExclSet(quantity);
        for (int i = 0; i < quantity; i++) {
            ImFilterValueMap<ObjectInstance, DataObject> mvObjectKeys = objects.mapFilterValues();
            for (int j=0,size=objects.size();j<size;j++) {
                ObjectInstance objectInstance = objects.get(j);
                if (objectInstance.getBaseClass() instanceof ConcreteCustomClass)
                    mvObjectKeys.mapValue(j, form.addFormObject((CustomObjectInstance)objectInstance, (ConcreteCustomClass) objectInstance.getBaseClass(), null, stack));
            }
            mResultSet.exclAdd(mvObjectKeys.immutableValue());
        }
        return mResultSet.immutableOrder();
    }

    public ImOrderMap<ImMap<ObjectInstance, DataObject>, ImMap<OrderInstance, ObjectValue>> readKeys(SQLSession session, QueryEnvironment env, final Modifier modifier, BaseClass baseClass) throws SQLException, SQLHandledException {
        return SEEK_HOME.executeOrders(session, env, modifier, baseClass, 0, true, null);
    }
    
    public interface SeekObjects {
        ImMap<ObjectInstance, DataObject> readKeys(SQLSession session, QueryEnvironment env, Modifier modifier, BaseClass baseClass, ReallyChanged reallyChanged) throws SQLException, SQLHandledException;
    }
    
    public class SeekNullObjects implements SeekObjects {

        private SeekNullObjects() {
        }

        @Override
        public ImMap<ObjectInstance, DataObject> readKeys(SQLSession session, QueryEnvironment env, Modifier modifier, BaseClass baseClass, ReallyChanged reallyChanged) throws SQLException, SQLHandledException {
            return MapFact.EMPTY();
        }
    }
    private final SeekNullObjects SEEK_NULL = new SeekNullObjects();

    public class SeekOrderObjects implements SeekObjects {
        public ImMap<OrderInstance, ObjectValue> values;
        public boolean end;

        public SeekOrderObjects(ImMap<OrderInstance, ObjectValue> values, boolean end) {
            this.values = values;
            this.end = end;
        }

        public SeekOrderObjects(boolean end, ImMap<ObjectInstance, DataObject> values) {
            this(BaseUtils.<ImMap<OrderInstance, ObjectValue>>immutableCast(values), end);
        }

        public SeekOrderObjects(boolean end) {
            this(MapFact.<OrderInstance, ObjectValue>EMPTY(), end);
        }

        public SeekOrderObjects add(OrderInstance order, ObjectValue value, boolean down) {
            return new SeekOrderObjects(values.override(order, value), this.end || down);
        }

        public SeekOrderObjects remove(ObjectInstance object) {
            return new SeekOrderObjects(values.remove(object), end);
        }

        // оптимизация, так как при Limit 1 некоторые СУБД начинают чудить
        private ImOrderMap<ImMap<ObjectInstance, DataObject>, ImMap<OrderInstance, ObjectValue>> executeSingleOrders(SQLSession session, QueryEnvironment env, final Modifier modifier, BaseClass baseClass, ReallyChanged reallyChanged, Result<ImMap<OrderInstance, ObjectValue>> fullOrderValues) throws SQLException, SQLHandledException {
            assert !isInTree();

            ImMap<ObjectInstance, ObjectValue> objectValues;
            ImMap<ObjectInstance, DataObject> dataObjects;
            if(!(Settings.get().isEnableSingleReadObjectsOptimization() && // работает, только, если в поиске есть все объекты и все DataObject
                    (objectValues = values.filter(objects)).size() == objects.size() &&
                    (dataObjects = DataObject.onlyDataObjects(objectValues)) != null)) {
                fullOrderValues.set(values);
                return MapFact.EMPTYORDER();
            }

            final ImRevMap<ObjectInstance, KeyExpr> mapKeys = getMapKeys();

            assert orders.keys().containsAll(values.keys());

            ImMap<OrderInstance, Expr> orderExprs = orders.getMap().mapKeyValuesEx(new GetExValue<Expr, OrderInstance, SQLException, SQLHandledException>() {
                public Expr getMapValue(OrderInstance value) throws SQLException, SQLHandledException {
                    return value.getExpr(mapKeys, modifier);
                }
            });

            String filterKey = "filter";
            ImOrderMap<ImMap<ObjectInstance, DataObject>, ImMap<Object, ObjectValue>> orderFilterValues = 
                    new Query<>(mapKeys, MapFact.addExcl(orderExprs, filterKey, ValueExpr.get(getWhere(mapKeys, modifier, reallyChanged))), CompareWhere.compareInclValues(orderExprs, dataObjects)).
                        executeClasses(session, env, baseClass);

            ObjectValue filterValue = orderFilterValues.singleValue().get(filterKey);
            ImMap<OrderInstance, ObjectValue> orderValues = BaseUtils.immutableCast(orderFilterValues.singleValue().removeIncl(filterKey));
            if(filterValue.isNull()) { // не попал в фильтр
                fullOrderValues.set(orderValues);
                return MapFact.EMPTYORDER();
            } else {
                return MapFact.singletonOrder(orderFilterValues.singleKey(), orderValues);
            }
        }

        // возвращает OrderInstance из orderSeeks со значениями, а также если есть parent, то parent'ы
        public ImOrderMap<ImMap<ObjectInstance, DataObject>, ImMap<OrderInstance, ObjectValue>> executeOrders(SQLSession session, QueryEnvironment env, final Modifier modifier, BaseClass baseClass, int readSize, boolean down, ReallyChanged reallyChanged) throws SQLException, SQLHandledException {
            assert !isInTree();

            final ImRevMap<ObjectInstance, KeyExpr> mapKeys = getMapKeys();

            // assertion что group.orders начинается с orderSeeks
            ImOrderMap<OrderInstance, Boolean> orders;
            if (readSize == 1)
                orders = GroupObjectInstance.this.orders.moveStart(values.keys());
            else
                orders = GroupObjectInstance.this.orders;

            assert orders.starts(values.keys());

            ImMap<OrderInstance, Expr> orderExprs = orders.getMap().mapKeyValuesEx(new GetExValue<Expr, OrderInstance, SQLException, SQLHandledException>() {
                public Expr getMapValue(OrderInstance value) throws SQLException, SQLHandledException {
                    return value.getExpr(mapKeys, modifier);
                }
            });

            Where orderWhere = end?Where.FALSE:Where.TRUE; // строим условия на упорядочивание
            ImOrderMap<OrderInstance, Boolean> reverseOrder = orders.reverseOrder();
            for (int i=0,size=reverseOrder.size();i<size;i++) {
                OrderInstance orderInstance = reverseOrder.getKey(i);
                ObjectValue toSeek = values.get(orderInstance);
                if (toSeek != null)
                    orderWhere = toSeek.order(orderExprs.get(orderInstance), reverseOrder.getValue(i), orderWhere);
            }

            if(!down)
                orderWhere = orderWhere.not();

            if (readSize == 1) { // в частном случае если есть "висячие" ключи не в фильтре и нужна одна запись ставим равно вместо >
                for(DataObjectInstance freeObject : getFreeDataObjects()) {
                    ObjectValue freeValue = values.get(freeObject);
                    if(freeValue==null || !(freeValue instanceof DataObject))
                        freeValue = freeObject.getBaseClass().getDefaultObjectValue();
                    orderWhere = orderWhere.and(end==!down?mapKeys.get(freeObject).compare((DataObject)freeValue, Compare.EQUALS):Where.FALSE); // seekDown==!down, чтобы и вверх и вниз не попали одни и те же ключи
                }
            }

            return new Query<>(mapKeys, orderExprs, getWhere(mapKeys, modifier, reallyChanged).and(orderWhere)).
                        executeClasses(session, down ? orders : Query.reverseOrder(orders), readSize, baseClass, env);
        }

        // считывает одну запись
        private Pair<ImMap<ObjectInstance, DataObject>, ImMap<OrderInstance, ObjectValue>> readObjects(SQLSession session, QueryEnvironment env, Modifier modifier, BaseClass baseClass, ReallyChanged reallyChanged) throws SQLException, SQLHandledException {
            Result<ImMap<OrderInstance, ObjectValue>> fullOrderValues = new Result<>();
            ImOrderMap<ImMap<ObjectInstance, DataObject>, ImMap<OrderInstance, ObjectValue>> result = executeSingleOrders(session, env, modifier, baseClass, reallyChanged, fullOrderValues);
            if(result.size() == 0)
                result = new SeekOrderObjects(fullOrderValues.result, end).executeOrders(session, env, modifier, baseClass, 1, !end, reallyChanged);
            if (result.size() == 0)
                result = new SeekOrderObjects(fullOrderValues.result, !end).executeOrders(session, env, modifier, baseClass, 1, end, reallyChanged);
            if (result.size() > 0)
                return new Pair<>(result.singleKey(), result.singleValue());
            else
                return null;
        }

        public ImMap<ObjectInstance, DataObject> readKeys(SQLSession session, QueryEnvironment env, Modifier modifier, BaseClass baseClass, ReallyChanged reallyChanged) throws SQLException, SQLHandledException {
            Pair<ImMap<ObjectInstance, DataObject>, ImMap<OrderInstance, ObjectValue>> objects = readObjects(session, env, modifier, baseClass, reallyChanged);
            if (objects != null)
                return objects.first;
            else
                return MapFact.EMPTY();
        }

        public SeekOrderObjects readValues(SQLSession session, QueryEnvironment env, Modifier modifier, BaseClass baseClass, ReallyChanged reallyChanged) throws SQLException, SQLHandledException {
            Pair<ImMap<ObjectInstance, DataObject>, ImMap<OrderInstance, ObjectValue>> objects = readObjects(session, env, modifier, baseClass, reallyChanged);
            if (objects != null)
                return new SeekOrderObjects(objects.second, end);
            else
                return SEEK_HOME;
        }
    }

    public NoPropertyTableUsage<ObjectInstance> createKeyTable(String debugInfo) {
        return new NoPropertyTableUsage<>(debugInfo, GroupObjectInstance.getOrderObjects(getOrderUpTreeGroups()), new Type.Getter<ObjectInstance>() {
            public Type getType(ObjectInstance key) {
                return key.getType();
            }
        });                
    }

    public class RowBackgroundReaderInstance implements PropertyReaderInstance {
        public CalcPropertyObjectInstance getPropertyObjectInstance() {
            return propertyBackground;
        }

        public byte getTypeID() {
            return PropertyReadType.ROW_BACKGROUND;
        }

        public int getID() {
            return GroupObjectInstance.this.getID();
        }

        @Override
        public PropertyType getPropertyType(FormEntity formEntity) {
            return null;
        }

        @Override
        public String toString() {
            return ThreadLocalContext.localize("{logics.background} (") + GroupObjectInstance.this.toString() + ")";
        }

        @Override
        public Object getProfiledObject() {
            return entity.propertyBackground;
        }
    }

    public class RowForegroundReaderInstance implements PropertyReaderInstance {
        public CalcPropertyObjectInstance getPropertyObjectInstance() {
            return propertyForeground;
        }

        public byte getTypeID() {
            return PropertyReadType.ROW_FOREGROUND;
        }

        public int getID() {
            return GroupObjectInstance.this.getID();
        }

        @Override
        public PropertyType getPropertyType(FormEntity formEntity) {
            return null;
        }

        @Override
        public String toString() {
            return ThreadLocalContext.localize("{logics.foreground}") + " (" + GroupObjectInstance.this.toString() + ")";
        }

        @Override
        public Object getProfiledObject() {
            return entity.propertyForeground;
        }
    }
}
