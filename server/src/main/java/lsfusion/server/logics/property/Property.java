package lsfusion.server.logics.property;

import com.google.common.base.Throwables;
import lsfusion.base.BaseUtils;
import lsfusion.base.ListPermutations;
import lsfusion.base.Pair;
import lsfusion.base.col.ListFact;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.implementations.simple.EmptyOrderMap;
import lsfusion.base.col.implementations.simple.EmptyRevMap;
import lsfusion.base.col.interfaces.immutable.*;
import lsfusion.base.col.interfaces.mutable.*;
import lsfusion.base.col.interfaces.mutable.add.MAddCol;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetIndex;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetIndexValue;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.base.col.lru.LRUSVSMap;
import lsfusion.base.col.lru.LRUUtil;
import lsfusion.interop.ClassViewType;
import lsfusion.interop.Compare;
import lsfusion.interop.form.ServerResponse;
import lsfusion.server.Settings;
import lsfusion.server.caches.ManualLazy;
import lsfusion.server.classes.ActionClass;
import lsfusion.server.classes.LogicalClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.classes.sets.AndClassSet;
import lsfusion.server.classes.sets.ResolveClassSet;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.type.ObjectType;
import lsfusion.server.data.type.Type;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.PropertyDrawEntity;
import lsfusion.server.form.view.PropertyDrawView;
import lsfusion.server.logics.*;
import lsfusion.server.logics.debug.DebugInfo;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.mutables.NFLazy;
import lsfusion.server.logics.mutables.Version;
import lsfusion.server.logics.property.actions.edit.DefaultChangeActionProperty;
import lsfusion.server.logics.property.group.AbstractGroup;
import lsfusion.server.logics.property.group.AbstractPropertyNode;
import lsfusion.server.session.Modifier;
import lsfusion.server.session.PropertyChanges;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.Callable;

import static lsfusion.interop.form.ServerResponse.*;

public abstract class Property<T extends PropertyInterface> extends AbstractPropertyNode {
    public static final GetIndex<PropertyInterface> genInterface = new GetIndex<PropertyInterface>() {
        public PropertyInterface getMapValue(int i) {
            return new PropertyInterface(i);
        }};

    private int ID = 0;
    private String dbName;
    protected String canonicalName;
    public String annotation;

    private boolean local = false;
    
    // вот отсюда идут свойства, которые отвечают за логику представлений и подставляются автоматически для PropertyDrawEntity и PropertyDrawView
    public LocalizedString caption;

    public LocalizedString localizedToString() {
        LocalizedString result = LocalizedString.create(getSID());
        if (caption != null) {
            result = LocalizedString.concatList(result, " '", caption, "'");    
        }
        if (debugInfo != null) {
            result = LocalizedString.concat(result, " [" + debugInfo + "]");
        }
        return result;
    } 
    
    public String toString() {
        String result;
        if (canonicalName != null) {
            result = canonicalName;
        } else {
            String topName = getTopName();
            result = topName != null ? "at " + topName : getPID();
        }
        
        LocalizedString caption;
        if (this.caption != null && this.caption != LocalizedString.NONAME) {
            caption = this.caption;
        } else {
            caption = getTopCaption();
        }
        if (caption != null) {
            result += " '" + ThreadLocalContext.localize(caption) + "'";
        }

        if (debugInfo != null) {
            result += " [" + debugInfo + "]";
        }
        return result;
    }

    protected DebugInfo debugInfo;
    
    public abstract DebugInfo getDebugInfo();

    public boolean isField() {
        return false;
    }

    public int getID() {
        return ID;
    }

    public void setID(int ID) {
        this.ID = ID;
    }

    public Type getType() {
        ValueClass valueClass = getValueClass(ClassType.typePolicy);
        return valueClass != null ? valueClass.getType() : null;
    }

    public abstract ValueClass getValueClass(ClassType classType);

    public ValueClass[] getInterfaceClasses(ImOrderSet<T> listInterfaces, ClassType classType) { // notification, load, lazy, dc, obsolete, в конструкторах при определении классов действий в основном
        return listInterfaces.mapList(getInterfaceClasses(classType)).toArray(new ValueClass[listInterfaces.size()]);
    }
    public abstract ImMap<T, ValueClass> getInterfaceClasses(ClassType type);

    public abstract boolean isInInterface(ImMap<T, ? extends AndClassSet> interfaceClasses, boolean isAny);

    public Property(LocalizedString caption, ImOrderSet<T> interfaces) {
        this.ID = BaseLogicsModule.generateStaticNewID();
        this.caption = caption;
        this.interfaces = interfaces.getSet();
        this.orderInterfaces = interfaces;

        setContextMenuAction(ServerResponse.GROUP_CHANGE, LocalizedString.create("{logics.property.groupchange}"));

//        notFinalized.put(this, ExceptionUtils.getStackTrace());
    }

    public final ImSet<T> interfaces;
    private final ImOrderSet<T> orderInterfaces;
    protected ImOrderSet<T> getOrderInterfaces() {
        return orderInterfaces;
    }

    public int getInterfaceCount() {
        return interfaces.size();
    }
    
    public ImOrderSet<T> getReflectionOrderInterfaces() {
        return orderInterfaces;
    }
    
    public ImOrderSet<T> getFriendlyOrderInterfaces() { 
        return orderInterfaces; 
    }

    public static Modifier defaultModifier = new Modifier() {
        public PropertyChanges getPropertyChanges() {
            return PropertyChanges.EMPTY;
        }
    };

    public Type getInterfaceType(T propertyInterface) {
        return getInterfaceType(propertyInterface, ClassType.materializeChangePolicy);
    }

    public Type getWhereInterfaceType(T propertyInterface) {
        return getInterfaceType(propertyInterface, ClassType.wherePolicy);
    }

    public Type getInterfaceType(T propertyInterface, ClassType classType) {
        ValueClass valueClass = getInterfaceClasses(classType).get(propertyInterface);
        return valueClass != null ? valueClass.getType() : null;
    }

    public abstract boolean isSetNotNull();

    public String getDBName() {
        return dbName;
    }

    public String getName() {
        if (isNamed()) {
            return PropertyCanonicalNameParser.getName(canonicalName);
        }
        return null;
    }

    public String getNamespace() {
        if (isNamed()) {
            return PropertyCanonicalNameParser.getNamespace(canonicalName);
        }
        return null;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public void setCanonicalName(String namespace, String name, List<ResolveClassSet> signature, ImOrderSet<T> signatureOrder, DBNamingPolicy policy) {
        assert name != null && namespace != null;
        this.canonicalName = PropertyCanonicalNameUtils.createName(namespace, name, signature);
        this.dbName = policy.transformPropertyCNToDBName(canonicalName);

        setExplicitClasses(signatureOrder, signature);
    }

    public void setCanonicalName(String canonicalName, DBNamingPolicy policy) {
        checkCanonicalName(canonicalName);
        this.canonicalName = canonicalName;
        this.dbName = policy.transformPropertyCNToDBName(canonicalName);
    }

    private void checkCanonicalName(String canonicalName) {
        assert canonicalName != null;
        PropertyCanonicalNameParser.getName(canonicalName);
        PropertyCanonicalNameParser.getNamespace(canonicalName);
    }

    final public boolean isNamed() {
        return canonicalName != null;
    }

    // для всех    
    private String mouseBinding;
    private Object keyBindings;
    private Object contextMenuBindings;
    private Object editActions;

    public void setMouseAction(String actionSID) {
        setMouseBinding(actionSID);
    }

    public void setMouseBinding(String mouseBinding) {
        this.mouseBinding = mouseBinding;
    }

    public void setKeyAction(KeyStroke ks, String actionSID) {
        if (keyBindings == null) {
            keyBindings = MapFact.mMap(MapFact.override());
        }
        ((MMap<KeyStroke, String>)keyBindings).add(ks, actionSID);
    }

    public String getMouseBinding() {
        return mouseBinding;
    }

    public ImMap<KeyStroke, String> getKeyBindings() {
        return (ImMap<KeyStroke, String>)(keyBindings == null ? MapFact.EMPTY() : keyBindings);
    }

    @NFLazy
    public void setContextMenuAction(String actionSID, LocalizedString caption) {
        if (contextMenuBindings == null || contextMenuBindings instanceof EmptyOrderMap) {
            contextMenuBindings = MapFact.mOrderMap(MapFact.override());
        }
        ((MOrderMap<String, LocalizedString>)contextMenuBindings).add(actionSID, caption);
    }

    public ImOrderMap<String, LocalizedString> getContextMenuBindings() {
        return (ImOrderMap<String, LocalizedString>)(contextMenuBindings == null ? MapFact.EMPTYORDER() : contextMenuBindings);
    }

    @NFLazy
    public void setEditAction(String editActionSID, ActionPropertyMapImplement<?, T> editActionImplement) {
        if (editActions == null || editActions instanceof EmptyRevMap) {
            editActions = MapFact.mMap(MapFact.override());
        }
        ((MMap<String, ActionPropertyMapImplement<?, T>>)editActions).add(editActionSID, editActionImplement);
    }

    @LongMutable
    private ImMap<String, ActionPropertyMapImplement<?, T>> getEditActions() {
        return (ImMap<String, ActionPropertyMapImplement<?, T>>)(editActions == null ? MapFact.EMPTY() : editActions);
    }

    public ActionPropertyMapImplement<?, T> getEditAction(String editActionSID) {
        return getEditAction(editActionSID, null);
    }

    public ActionPropertyMapImplement<?, T> getEditAction(String editActionSID, CalcProperty filterProperty) {
        ActionPropertyMapImplement<?, T> editAction = getEditActions().get(editActionSID);
        if (editAction != null) {
            return editAction;
        }

        if(GROUP_CHANGE.equals(editActionSID))
            return null;

        assert CHANGE.equals(editActionSID) || CHANGE_WYS.equals(editActionSID) || EDIT_OBJECT.equals(editActionSID);

        return getDefaultEditAction(editActionSID, filterProperty);
    }

    public abstract ActionPropertyMapImplement<?, T> getDefaultEditAction(String editActionSID, CalcProperty filterProperty);

    public boolean checkEquals() {
        return this instanceof CalcProperty;
    }

    public ImRevMap<T, T> getIdentityInterfaces() {
        return interfaces.toRevMap();
    }

    public boolean hasChild(Property prop) {
        return prop.equals(this);
    }

    public boolean hasNFChild(Property prop, Version version) {
        return hasChild(prop);
    }
    
    public ImOrderSet<Property> getProperties() {
        return SetFact.singletonOrder((Property) this);
    }
    
    public static void cleanPropCaches() {
        hashProps.clear();
    }

    private static class CacheEntry {
        private final Property property;
        private final ImMap<ValueClass, ImSet<ValueClassWrapper>> mapClasses;
        private final boolean useObjSets;
        private final boolean anyInInterface;
        
        private ImList<PropertyClassImplement> result;
        
        public CacheEntry(Property property, ImMap<ValueClass, ImSet<ValueClassWrapper>> mapClasses, boolean useObjSets, boolean anyInInterface) {
            this.property = property;
            this.mapClasses = mapClasses;
            this.useObjSets = useObjSets;
            this.anyInInterface = anyInInterface;
        }

        public ImRevMap<ValueClassWrapper, ValueClassWrapper> map(CacheEntry entry) {
            if(!(useObjSets == entry.useObjSets && anyInInterface == entry.anyInInterface && mapClasses.size() == entry.mapClasses.size() && BaseUtils.hashEquals(property, entry.property)))
                return null;

            MRevMap<ValueClassWrapper, ValueClassWrapper> mResult = MapFact.mRevMap();
            for(int i=0,size=mapClasses.size();i<size;i++) {
                ImSet<ValueClassWrapper> wrappers = mapClasses.getValue(i);
                ImSet<ValueClassWrapper> entryWrappers = entry.mapClasses.get(mapClasses.getKey(i));
                if(entryWrappers == null || wrappers.size() != entryWrappers.size())
                    return null;
                for(int j=0,sizeJ=wrappers.size();j<sizeJ;j++)
                    mResult.revAdd(wrappers.get(j), entryWrappers.get(j));
            }
            return mResult.immutableRev();
        }
        
        public int hash() {
            int result = 0;
            for(int i=0,size=mapClasses.size();i<size;i++) {
                result += mapClasses.getKey(i).hashCode() ^ mapClasses.getValue(i).size();
            }
            
            return 31 * (31 * ( 31 * result + (useObjSets ? 1 : 0)) + (anyInInterface ? 1 : 0)) + property.hashCode(); 
        }
    }    
    final static LRUSVSMap<Integer, MAddCol<CacheEntry>> hashProps = new LRUSVSMap<>(LRUUtil.G2);

    // вся оптимизация в общем то для drillDown
    protected ImList<PropertyClassImplement> getProperties(ImSet<ValueClassWrapper> valueClasses, ImMap<ValueClass, ImSet<ValueClassWrapper>> mapClasses, boolean useObjSubsets, boolean anyInInterface, Version version) {
        if(valueClasses.size() == 1) { // доп оптимизация для DrillDown
            if(interfaces.size() == 1 && isInInterface(MapFact.singleton(interfaces.single(), valueClasses.single().valueClass.getUpSet()), anyInInterface))
                return ListFact.<PropertyClassImplement>singleton(createClassImplement(valueClasses.toOrderSet(), SetFact.singletonOrder(interfaces.single())));
            return ListFact.EMPTY();
        }            
            
        CacheEntry entry = new CacheEntry(this, mapClasses, useObjSubsets, anyInInterface); // кэширование
        int hash = entry.hash();
        MAddCol<CacheEntry> col = hashProps.get(hash);
        if(col == null) {
            col = ListFact.mAddCol();
            hashProps.put(hash, col);                    
        } else {
            synchronized (col) {
                for (CacheEntry cachedEntry : col.it()) {
                    final ImRevMap<ValueClassWrapper, ValueClassWrapper> map = cachedEntry.map(entry);
                    if (map != null) {
                        return cachedEntry.result.mapListValues(new GetValue<PropertyClassImplement, PropertyClassImplement>() {
                            public PropertyClassImplement getMapValue(PropertyClassImplement value) {
                                return value.map(map);
                            }
                        });
                    }
                }
            }
        }
        
        ImList<PropertyClassImplement> result = getProperties(FormEntity.getSubsets(valueClasses, useObjSubsets), anyInInterface); 
        
        entry.result = result;
        synchronized (col) {
            col.add(entry);
        }
        
        return result;
    }
    
    private ImList<PropertyClassImplement> getProperties(ImCol<ImSet<ValueClassWrapper>> classLists, boolean anyInInterface) {
        MList<PropertyClassImplement> mResultList = ListFact.mList();
        for (ImSet<ValueClassWrapper> classes : classLists) {
            if (interfaces.size() == classes.size()) {
                final ImOrderSet<ValueClassWrapper> orderClasses = classes.toOrderSet();
                for (ImOrderSet<T> mapping : new ListPermutations<>(getOrderInterfaces())) {
                    ImMap<T, AndClassSet> propertyInterface = mapping.mapOrderValues(new GetIndexValue<AndClassSet, T>() {
                        public AndClassSet getMapValue(int i, T value) {
                            return orderClasses.get(i).valueClass.getUpSet();
                        }});
                    if (isInInterface(propertyInterface, anyInInterface)) {
                        mResultList.add(createClassImplement(orderClasses, mapping));
                    }
                }
            }
        }
        return mResultList.immutableList();
    }
    
    protected abstract PropertyClassImplement<T, ?> createClassImplement(ImOrderSet<ValueClassWrapper> classes, ImOrderSet<T> mapping);

    public T getInterfaceById(int iID) {
        for (T inter : interfaces) {
            if (inter.getID() == iID) {
                return inter;
            }
        }

        return null;
    }

    @Override
    public List<AbstractGroup> fillGroups(List<AbstractGroup> groupsList) {
        return groupsList;
    }

    protected boolean finalized = false;
    public void finalizeInit() {
        assert !finalized;
        finalized = true;
    }

//    private static ConcurrentHashMap<Property, String> notFinalized = new ConcurrentHashMap<Property, String>();

    public void finalizeAroundInit() {
        super.finalizeAroundInit();

//        notFinalized.remove(this);
        
        editActions = editActions == null ? MapFact.EMPTY() : ((MMap)editActions).immutable();
        keyBindings = keyBindings == null ? MapFact.EMPTY() : ((MMap)keyBindings).immutable();
        contextMenuBindings = contextMenuBindings == null ? MapFact.EMPTYORDER() : ((MOrderMap)contextMenuBindings).immutableOrder();
    }

    public void prereadCaches() {
        getInterfaceClasses(ClassType.strictPolicy);
        getInterfaceClasses(ClassType.signaturePolicy);
    }

    protected abstract ImCol<Pair<Property<?>, LinkType>> calculateLinks(boolean events);

    private ImSet<Link> links;
    @ManualLazy
    public ImSet<Link> getLinks(boolean events) { // чисто для лексикографики
        if(links==null) {
            links = calculateLinks(events).mapMergeSetValues(new GetValue<Link, Pair<Property<?>, LinkType>>() {
                public Link getMapValue(Pair<Property<?>, LinkType> value) {
                    return new Link(Property.this, value.first, value.second);
                }});
        }
        return links;
    }
    public void dropLinks() {
        links = null;
    }
    public abstract ImSet<SessionCalcProperty> getSessionCalcDepends(boolean events);

    public abstract ImSet<OldProperty> getParseOldDepends(); // именно так, а не через getSessionCalcDepends, так как может использоваться до инициализации логики

    public ImSet<OldProperty> getOldDepends() {
        // без событий, так как либо используется в глобальных событиях когда вычисляемые события \ удаления отдельно отрабатываются
        // в локальных же событиях вычисляемые и должны браться на начало сессии
        return getSessionCalcDepends(false).mapMergeSetValues(new GetValue<OldProperty, SessionCalcProperty>() {
            public OldProperty getMapValue(SessionCalcProperty value) {
                return value.getOldProperty();
            }});
    }

    // не сильно структурно поэтому вынесено в метод
    public <V> ImRevMap<T, V> getMapInterfaces(final ImOrderSet<V> list) {
        return getOrderInterfaces().mapOrderRevValues(new GetIndexValue<V, T>() {
            public V getMapValue(int i, T value) {
                return list.get(i);
            }
        });
    }

    public boolean drillDownInNewSession() {
        return false;
    }

    public Property showDep; // assert что не null когда events не isEmpty

    protected static <T extends PropertyInterface> ImMap<T, ResolveClassSet> getPackedSignature(ImOrderSet<T> interfaces, List<ResolveClassSet> signature) {
        return interfaces.mapList(ListFact.fromJavaList(signature)).removeNulls();
    }

    public void setExplicitClasses(ImOrderSet<T> interfaces, List<ResolveClassSet> signature) {
        this.explicitClasses = getPackedSignature(interfaces, signature);
    }
    
    public String getPID() {
        return "p" + ID;
    }
    
    public String getSID() {
        return canonicalName != null ? canonicalName : getPID(); 
    }
    
    public String getTopName() {
        if (debugInfo != null) {
            return debugInfo.getTopName();
        }
        return null;
    }
    
    public LocalizedString getTopCaption() {
        if (debugInfo != null) {
            return debugInfo.getTopCaption();
        }
        return null;
    }

    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    protected ImMap<T, ResolveClassSet> explicitClasses; // без nulls

    protected interface Checker<V> {
        boolean checkEquals(V expl, V calc);
    }

    //
    protected static <T, V> ImMap<T, V> getExplicitCalcInterfaces(ImSet<T> interfaces, ImMap<T, V> explicitInterfaces, Callable<ImMap<T,V>> calcInterfaces, String caption, Property property, Checker<V> checker) {
        
        ImMap<T, V> inferred = null;
        if (explicitInterfaces != null)
            inferred = explicitInterfaces;

        if (inferred == null || inferred.size() < interfaces.size() || AlgType.checkExplicitInfer) {
            try {
                ImMap<T, V> calcInferred = calcInterfaces.call();
                if (calcInferred == null) {
                    return null;
                }
                if (inferred == null)
                    inferred = calcInferred;
                else {
                    if (AlgType.checkExplicitInfer) checkExplicitCalcInterfaces(checker, caption + property, inferred, calcInferred);
                    inferred = calcInferred.override(inferred); // тут возможно replaceValues достаточно, но не так просто оценить
                }
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        return inferred;
    }

    private static  <T, V> boolean checkExplicitCalcInterfaces(Checker<V> checker, String caption, ImMap<T, V> inferred, ImMap<T, V> calcInferred) {
        for(int i=0, size = inferred.size(); i<size; i++) {
            T key = inferred.getKey(i);
            V calcValue = calcInferred.get(key);
            V inferValue = inferred.getValue(i);
            if((calcValue != null || inferValue != null) && (calcValue == null || inferValue == null || !checker.checkEquals(calcValue, inferValue))) {
                System.out.println(caption + ", CALC : " + calcInferred + ", INF : " + inferred);
                return false;
            }
        }
        return true;
    }

    public String getChangeExtSID() {
        return null;
    }

    public void inheritCaption(Property property) {
        caption = property.caption;         
    }
    
    public interface DefaultProcessor {
        // из-за inherit entity и view могут быть другого свойства
        void proceedDefaultDraw(PropertyDrawEntity entity, FormEntity form);
        void proceedDefaultDesign(PropertyDrawView propertyView);
    }

    // + caption, который одновременно и draw и не draw
    public static class DrawOptions {
        
        // свойства, но пока реализовано как для всех
        private int charWidth;
        private Dimension valueSize;
        private Boolean valueFlex;

        // свойства, но пока реализовано как для всех
        private String regexp;
        private String regexpMessage;
        private Boolean echoSymbols;

        // действия, но пока реализовано как для всех
        private Boolean askConfirm;
        private String askConfirmMessage;

        // свойства, но пока реализовано как для всех
        private String eventID;

        // для всех
        private ImageIcon image;
        private String iconPath;

        // для всех
        private Compare defaultCompare;

        // для всех
        private KeyStroke changeKey;
        private Boolean showChangeKey;

        // для всех
        private Boolean shouldBeLast;

        // для всех
        private ClassViewType forceViewType;
        
        // для всех 
        private ImList<DefaultProcessor> processors = ListFact.EMPTY();
        
        public void proceedDefaultDraw(PropertyDrawEntity<?> entity, FormEntity form) {
            if (entity.shouldBeLast == null)
                entity.shouldBeLast = BaseUtils.nvl(shouldBeLast, false);
            if (entity.forceViewType == null)
                entity.forceViewType = forceViewType;
            if (entity.askConfirm == null)
                entity.askConfirm = BaseUtils.nvl(askConfirm, false);
            if (entity.askConfirmMessage == null)
                entity.askConfirmMessage = askConfirmMessage;
            if (entity.eventID == null)
                entity.eventID = eventID;

            for(DefaultProcessor processor : processors)
                processor.proceedDefaultDraw(entity, form);
        }

        public void proceedDefaultDesign(PropertyDrawView propertyView) {
            if(propertyView.getType() instanceof LogicalClass)
                propertyView.editOnSingleClick = Settings.get().getEditLogicalOnSingleClick();
            if(propertyView.getType() instanceof ActionClass)
                propertyView.editOnSingleClick = Settings.get().getEditActionOnSingleClick();

            if(propertyView.getCharWidth() == 0)
                propertyView.setCharWidth(charWidth);
            if(propertyView.getValueFlex() == null)
                propertyView.setValueFlex(valueFlex);
            if(propertyView.getValueSize() == null)
                propertyView.setValueSize(valueSize);
            if (propertyView.design.imagePath == null && iconPath != null) {
                propertyView.design.imagePath = iconPath;
                propertyView.design.setImage(image);
            }
            if (propertyView.changeKey == null)
                propertyView.changeKey = changeKey;
            if (propertyView.showChangeKey == null)
                propertyView.showChangeKey = BaseUtils.nvl(showChangeKey, true);
            if (propertyView.regexp == null)
                propertyView.regexp = regexp;
            if (propertyView.regexpMessage == null)
                propertyView.regexpMessage = regexpMessage;
            if (propertyView.echoSymbols == null)
                propertyView.echoSymbols = BaseUtils.nvl(echoSymbols, false);

            for(DefaultProcessor processor : processors)
                processor.proceedDefaultDesign(propertyView);
        }
        
        public void inheritDrawOptions(DrawOptions options) {
            if(charWidth == 0)
                setCharWidth(options.charWidth);

            if(iconPath == null) {
                setImage(options.image);
                setIconPath(options.iconPath);
            }

            if(defaultCompare == null)
                setDefaultCompare(options.defaultCompare);

            if(regexp == null)
                setRegexp(options.regexp);
            if(regexpMessage == null)
                setRegexpMessage(options.regexpMessage);
            if(echoSymbols == null)
                setEchoSymbols(options.echoSymbols);
            
            if(askConfirm == null)
                setAskConfirm(options.askConfirm);
            if(askConfirmMessage == null)
                setAskConfirmMessage(options.askConfirmMessage);
            
            if(eventID == null)
                setEventID(options.eventID);
            
            if(changeKey == null)
                setChangeKey(options.changeKey);
            if(showChangeKey == null)
                setShowChangeKey(options.showChangeKey);
            
            if(shouldBeLast == null)
                setShouldBeLast(options.shouldBeLast);
            
            if(forceViewType == null)
                setForceViewType(options.forceViewType);
            
            processors = options.processors.addList(processors);
        }

        // setters
        
        public void addProcessor(DefaultProcessor processor) {
            processors = processors.addList(processor);
        }

        public void setFixedCharWidth(int charWidth) {
            setCharWidth(charWidth);
            setValueFlex(false);
        }

        public void setImage(String iconPath) {
            this.setIconPath(iconPath);
            setImage(new ImageIcon(Property.class.getResource("/images/" + iconPath)));
        }

        public Compare getDefaultCompare() {
            return defaultCompare;
        }

        public void setDefaultCompare(String defaultCompare) {
            this.defaultCompare = PropertyUtils.stringToCompare(defaultCompare);
        }

        public void setDefaultCompare(Compare defaultCompare) {
            this.defaultCompare = defaultCompare;
        }


        public void setCharWidth(int charWidth) {
            this.charWidth = charWidth;
        }
        public void setValueFlex(Boolean flex) {
            this.valueFlex = flex;
        }

        public void setRegexp(String regexp) {
            this.regexp = regexp;
        }

        public void setRegexpMessage(String regexpMessage) {
            this.regexpMessage = regexpMessage;
        }

        public void setEchoSymbols(Boolean echoSymbols) {
            this.echoSymbols = echoSymbols;
        }

        public void setAskConfirm(Boolean askConfirm) {
            this.askConfirm = askConfirm;
        }

        public void setAskConfirmMessage(String askConfirmMessage) {
            this.askConfirmMessage = askConfirmMessage;
        }

        public void setEventID(String eventID) {
            this.eventID = eventID;
        }

        public void setImage(ImageIcon image) {
            this.image = image;
        }

        public void setIconPath(String iconPath) {
            this.iconPath = iconPath;
        }

        public void setChangeKey(KeyStroke changeKey) {
            this.changeKey = changeKey;
        }

        public void setShowChangeKey(Boolean showEditKey) {
            this.showChangeKey = showEditKey;
        }

        public void setShouldBeLast(Boolean shouldBeLast) {
            this.shouldBeLast = shouldBeLast;
        }

        public void setForceViewType(ClassViewType forceViewType) {
            this.forceViewType = forceViewType;
        }
    }

    public DrawOptions drawOptions = new DrawOptions();
    
    protected ApplyGlobalEvent event;
    // важно кэшировать так как equals'ов пока нет, а они важны (в общем то только для Stored, и для RemoveClasses )
    public ApplyGlobalEvent getApplyEvent() {
        return null;        
    }
}
