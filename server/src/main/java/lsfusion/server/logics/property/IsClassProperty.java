package lsfusion.server.logics.property;

import lsfusion.base.BaseUtils;
import lsfusion.base.Pair;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImCol;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.MExclSet;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.base.col.interfaces.mutable.add.MAddExclMap;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetIndex;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.server.caches.ManualLazy;
import lsfusion.server.classes.*;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.ValueExpr;
import lsfusion.server.data.where.Where;
import lsfusion.server.data.where.WhereBuilder;
import lsfusion.server.data.where.classes.ClassWhere;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.mutables.NFStaticLazy;
import lsfusion.server.logics.property.actions.ChangeEvent;
import lsfusion.server.logics.property.derived.DerivedProperty;
import lsfusion.server.logics.property.infer.ExClassSet;
import lsfusion.server.logics.property.infer.InferType;
import lsfusion.server.logics.property.infer.Inferred;
import lsfusion.server.session.Modifier;
import lsfusion.server.session.PropertyChanges;
import lsfusion.server.session.StructChanges;

import java.sql.SQLException;

public class IsClassProperty extends SimpleIncrementProperty<ClassPropertyInterface> {

    public IsClassProperty(LocalizedString caption, ValueClass valueClass) {
        super(caption, getInterfaces(new ValueClass[]{valueClass}));

        finalizeInit();
    }

    public static ImMap<ClassPropertyInterface, ValueClass> getMapClasses(ImSet<ClassPropertyInterface> interfaces) {
        return interfaces.mapValues(new GetValue<ValueClass, ClassPropertyInterface>() {
            public ValueClass getMapValue(ClassPropertyInterface value) {
                return value.interfaceClass;
            }});
    }

    // по аналогии с SessionDataProperty
    public final static MAddExclMap<ImMap<ValueClass, Integer>, CalcPropertyImplement<?, ValueClass>> cacheClasses = MapFact.mBigStrongMap();
    @ManualLazy
    @NFStaticLazy
    public static <T, P extends PropertyInterface> CalcPropertyRevImplement<?, T> getProperty(ImMap<T, ValueClass> classes) {
        ImMap<ValueClass, Integer> multiClasses = classes.values().multiSet();
        synchronized (cacheClasses) {
            CalcPropertyImplement<P, ValueClass> implement = (CalcPropertyImplement<P, ValueClass>) cacheClasses.get(multiClasses);
            if(implement==null) {
                CalcPropertyRevImplement<?, T> classImplement = DerivedProperty.createCProp(LogicalClass.instance, true, classes);
                cacheClasses.exclAdd(multiClasses, classImplement.mapImplement(classes));
                return classImplement;
            } else
                return new CalcPropertyRevImplement<>(implement.property, MapFact.mapValues(implement.mapping, classes));
        }
    }

    public static <T extends PropertyInterface> CalcPropertyMapImplement<?, T> getMapProperty(ImMap<T, ValueClass> classes) {
        return CalcPropertyRevImplement.mapPropertyImplement(getProperty(classes));
    }

    public static <T> CalcPropertyRevImplement<?, T> getProperty(ValueClass valueClass, T map) {
        IsClassProperty classProperty = valueClass.getProperty();
        return new CalcPropertyRevImplement<>(classProperty, MapFact.singletonRev(classProperty.interfaces.single(), map));
    }

    public static CalcPropertyMapImplement<?, ClassPropertyInterface> getProperty(ImSet<ClassPropertyInterface> interfaces) {
        return getMapProperty(getMapClasses(interfaces));
     }

    public static <T> Where getWhere(ImMap<T, ValueClass> joinClasses, ImMap<T, ? extends Expr> joinImplement, Modifier modifier, MSet<CalcProperty> mUsedProps) throws SQLException, SQLHandledException {
        CalcPropertyRevImplement<?, T> property = getProperty(joinClasses);
        if(mUsedProps != null)
            mUsedProps.add(property.property);
        return property.mapExpr(joinImplement, modifier.getPropertyChanges(), null).getWhere();
    }
    public static Where getWhere(ValueClass valueClass, Expr valueExpr, Modifier modifier, MSet<CalcProperty> mUsedProps) throws SQLException, SQLHandledException {
        CalcPropertyRevImplement<?, String> property = getProperty(valueClass, "value");
        if(mUsedProps != null)
            mUsedProps.add(property.property);
        return property.mapExpr(MapFact.singleton("value", valueExpr), modifier.getPropertyChanges(), null).getWhere();
    }

    public static ImOrderSet<ClassPropertyInterface> getInterfaces(final ValueClass[] classes) {
        return SetFact.toOrderExclSet(classes.length, new GetIndex<ClassPropertyInterface>() {
            public ClassPropertyInterface getMapValue(int i) {
                return new ClassPropertyInterface(i, classes[i]);
            }});
    }

    public static boolean fitClass(ConcreteClass concreteClass, ValueClass valueClass) {
        if(valueClass == null)
            return true;

        // unknown, custom, concatenateClassSet
        if(concreteClass instanceof ValueClass)
            return valueClass.getUpSet().containsAll(concreteClass, true); // при изменениях, вызове custom action
        else {
            assert concreteClass instanceof UnknownClass; // с concatenate'ами надо будет разбираться
            return false;
        }
    }

    public static boolean fitInterfaceClasses(ImMap<ClassPropertyInterface, ConcreteClass> mapValues) {
        for(int i=0,size=mapValues.size();i<size;i++)
            if(!fitClass(mapValues.getValue(i), mapValues.getKey(i).interfaceClass))
                return false;
        return true;
    }

    public static boolean fitClasses(ImMap<ClassPropertyInterface, ConcreteClass> mapValues, ValueClass valueClass, ConcreteClass value) { // оптимизация
        return !(value != null && !fitClass(value, valueClass)) && fitInterfaceClasses(mapValues);
    }

    public Where getDroppedWhere(Expr expr, Modifier modifier) throws SQLException, SQLHandledException {
        return getDroppedWhere(expr, modifier.getPropertyChanges());
    }

//    @Override
//    protected void fillDepends(MSet<CalcProperty> depends, boolean events) {
//        if(events)
//            depends.addAll(getClassDataProps());
//    }

    public ImSet<CalcProperty> getSingleApplyDroppedIsClassProps() {
        ValueClass interfaceClass = getInterfaceClass();
        if(interfaceClass instanceof CustomClass) { // возвращаем и parent'ы и children'ов (так как при удалении надо и конкретные и абстрактные свойства смотреть)
            CustomClass customClass = (CustomClass) interfaceClass;
            MSet<CustomClass> mDependClasses = SetFact.mSet();
            for(CalcProperty upAggrProp : customClass.getUpAggrProps()) {
                ValueClass valueClass = upAggrProp.getValueClass(ClassType.materializeChangePolicy);
                if(valueClass instanceof CustomClass) { // нас также интересует класс значения корреляций (так как они тоже могут single apply'ся, а значения корреляций никто не обновит) 
                    CustomClass customAggrClass = (CustomClass) valueClass;
                    mDependClasses.addAll(customAggrClass.getAllChildrenParents());
                }
            }
            mDependClasses.addAll(customClass.getAllChildrenParents());
            return mDependClasses.immutable().mapSetValues(new GetValue<CalcProperty, CustomClass>() {
                public CalcProperty getMapValue(CustomClass value) {
                    return value.getProperty().getChanged(IncrementType.DROP, ChangeEvent.scope);
                }});
        }
        return SetFact.EMPTY();
    }
    
    public ImSet<ClassDataProperty> getClassDataProps() {
        ValueClass interfaceClass = getInterfaceClass();
        if(interfaceClass instanceof CustomClass)
            return BaseUtils.immutableCast(((CustomClass) interfaceClass).getUpObjectClassFields().keys());
        return SetFact.EMPTY();
    }
    
    @Override
    protected ImCol<Pair<Property<?>, LinkType>> calculateLinks(boolean events) {
        assert events; // так как при ONLY_DATA IsClassProperty вообще не может попасть в список

        ImCol<Pair<Property<?>, LinkType>> actionChangeProps = getActionChangeProps(); // чтобы обnull'ить использование
        assert actionChangeProps.isEmpty();
        assert getDepends().isEmpty();

        MExclSet<CalcProperty> mResult = SetFact.mExclSet();
        fillChangedProps(mResult, IncrementType.DROP);
        fillChangedProps(mResult, IncrementType.SET);

        return mResult.immutable().mapSetValues(new GetValue<Pair<Property<?>, LinkType>, CalcProperty>() {
            public Pair<Property<?>, LinkType> getMapValue(CalcProperty value) {
                return new Pair<Property<?>, LinkType>(value, LinkType.DEPEND);
            }});
    }

    public void fillChangedProps(MExclSet<CalcProperty> mSet, IncrementType type) {
        for(PrevScope scope : PrevScope.values())
            mSet.exclAdd(getChanged(type, scope));
    }

    @Override
    public ClassWhere<Object> calcClassValueWhere(CalcClassType type) {
        return new ClassWhere<>(MapFact.<Object, ValueClass>addExcl(IsClassProperty.getMapClasses(interfaces), "value", LogicalClass.instance), true);
    }

//    public static boolean checkSession(OuterContext context) {
//        final Result<Boolean> found = new Result<>(false);
//        context.enumerate(new ExprEnumerator() {
//            @Override
//            public Boolean enumerate(OuterContext join) {
//                if(join instanceof Table.Join && !((Table.Join) join).isSession()) {
//                    found.set(true);
//                    return null;
//                }
//                return true;
//            }
//        });    
//        return !found.result;
//    }
    
    public ValueClass getInterfaceClass() {
        return interfaces.single().interfaceClass;
    }
    public Expr calculateExpr(ImMap<ClassPropertyInterface, ? extends Expr> joinImplement, CalcType calcType, PropertyChanges propChanges, WhereBuilder changedWhere) {
        ValueClass interfaceClass = getInterfaceClass();
        if(calcType instanceof CalcClassType && (((CalcClassType)calcType).replaceIs() || interfaceClass instanceof BaseClass)) // жесткий хак
            return getVirtualTableExpr(joinImplement, ((CalcClassType) calcType));

        Where hadClass = joinImplement.singleValue().isUpClass(interfaceClass, calcType.isRecalc());
//        if(!hasChanges(propChanges))
        return ValueExpr.get(hadClass);

        // реализовано на уровне getPropertyChange (так как здесь не хватает части информации, depends от classdata слишком грубая) 
//        // has : (W1E1 OR .. WnEn)
//        // changed : (W1 OR .. Wn)  
//        // new is : has OR !changed*had  
//        // changed is : !had*has OR changed*had*!has
//
//        ImMap<ClassDataProperty, ObjectValueClassSet> upClassDataProps = getUpClassDataProps();
//        Where hasClass = Where.FALSE;
//        Where dataChangedWhere = Where.FALSE;
//        for(int i=0,size=upClassDataProps.size();i<size;i++) {
//            ClassDataProperty dataProperty = upClassDataProps.getKey(i);
//            ObjectValueClassSet classSet = upClassDataProps.getValue(i);
//
//            if(!dataProperty.hasChanges(propChanges)) // оптимизация
//                continue;
//            
//            WhereBuilder dataChangedOpWhere = new WhereBuilder();
//            Expr dataExpr = dataProperty.getExpr(MapFact.singleton(dataProperty.interfaces.single(), joinImplement.singleValue()), calcType, propChanges, dataChangedOpWhere);
//            Expr changedDataExpr = dataExpr.and(dataChangedOpWhere.toWhere());
//            assert checkSession(changedDataExpr) && checkSession(dataChangedOpWhere.toWhere());
//            
//            hasClass = hasClass.or(ClassChanges.isStaticValueClass(changedDataExpr, classSet, dataProperty.set));
//            dataChangedWhere = dataChangedWhere.or(dataChangedOpWhere.toWhere());
//        }
//        
//        if(changedWhere != null)
//            changedWhere.add(hasClass.and(hadClass.not()).or(dataChangedWhere.and(hadClass).and(hasClass.not())));        
//        return ValueExpr.get(hasClass.or(hadClass.and(dataChangedWhere.not())));
    }

    @Override
    public ActionPropertyMapImplement<?, ClassPropertyInterface> getSetNotNullAction(boolean notNull) {
        ValueClass valueClass = getInterfaceClass();
        if(valueClass instanceof ConcreteCustomClass) {
            ActionProperty<PropertyInterface> changeClassAction = (notNull ? (ConcreteCustomClass) valueClass : ((ConcreteCustomClass) valueClass).getBaseClass().unknown).getChangeClassAction();
            return new ActionPropertyMapImplement<>(changeClassAction, MapFact.singletonRev(changeClassAction.interfaces.single(), interfaces.single()));
        }
        return null;
    }

    public ImSet<CalcProperty> getRemoveUsedChanges(StructChanges newChanges) {
        return getChanged(IncrementType.DROP, ChangeEvent.scope).getUsedChanges(newChanges);
    }

    public Where getDroppedWhere(Expr joinExpr, PropertyChanges newChanges) {
        return getChanged(IncrementType.DROP, ChangeEvent.scope).getExpr(MapFact.singleton(interfaces.single(), joinExpr), newChanges).getWhere();
    }

    public Inferred<ClassPropertyInterface> calcInferInterfaceClasses(ExClassSet commonValue, InferType inferType) { // calcClassValueWhere - начинает какой-то ерундой страдать, разбивая условия на is'ы чтобы уменьшить кол-во таблиц
        return new Inferred<>(ExClassSet.toExValue(IsClassProperty.getMapClasses(interfaces)));
    }

    public ExClassSet calcInferValueClass(ImMap<ClassPropertyInterface, ExClassSet> inferred, InferType inferType) {
        return ExClassSet.logical;
    }

    @Override
    public boolean aspectDebugHasAlotKeys() { // оптимизация см. CaseUnionProperty
        return getInterfaceClass() instanceof DataClass;
    }
}
