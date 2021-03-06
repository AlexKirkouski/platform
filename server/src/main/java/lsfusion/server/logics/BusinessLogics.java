package lsfusion.server.logics;

import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import lsfusion.base.*;
import lsfusion.base.col.ListFact;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.implementations.HMap;
import lsfusion.base.col.implementations.HSet;
import lsfusion.base.col.interfaces.immutable.*;
import lsfusion.base.col.interfaces.mutable.*;
import lsfusion.base.col.interfaces.mutable.add.MAddExclMap;
import lsfusion.base.col.interfaces.mutable.add.MAddMap;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.base.col.lru.LRULogger;
import lsfusion.base.col.lru.LRUUtil;
import lsfusion.base.col.lru.LRUWSASVSMap;
import lsfusion.interop.Compare;
import lsfusion.interop.event.IDaemonTask;
import lsfusion.interop.exceptions.LogMessageLogicsException;
import lsfusion.interop.form.screen.ExternalScreen;
import lsfusion.interop.form.screen.ExternalScreenParameters;
import lsfusion.server.ServerLoggers;
import lsfusion.server.Settings;
import lsfusion.server.SystemProperties;
import lsfusion.server.caches.CacheStats;
import lsfusion.server.caches.CacheStats.CacheType;
import lsfusion.server.caches.IdentityLazy;
import lsfusion.server.caches.IdentityStrongLazy;
import lsfusion.server.caches.ManualLazy;
import lsfusion.server.classes.*;
import lsfusion.server.classes.sets.OrObjectClassSet;
import lsfusion.server.classes.sets.ResolveClassSet;
import lsfusion.server.classes.sets.ResolveOrObjectClassSet;
import lsfusion.server.context.EExecutionStackRunnable;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.daemons.DiscountCardDaemonTask;
import lsfusion.server.daemons.ScannerDaemonTask;
import lsfusion.server.daemons.WeightDaemonTask;
import lsfusion.server.data.*;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.expr.ValueExpr;
import lsfusion.server.data.expr.query.GroupExpr;
import lsfusion.server.data.expr.query.GroupType;
import lsfusion.server.data.query.MapCacheAspect;
import lsfusion.server.data.query.Query;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.data.query.stat.StatKeys;
import lsfusion.server.data.type.ObjectType;
import lsfusion.server.data.type.Type;
import lsfusion.server.data.where.Where;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.instance.listener.CustomClassListener;
import lsfusion.server.form.navigator.LogInfo;
import lsfusion.server.form.navigator.NavigatorElement;
import lsfusion.server.form.navigator.RemoteNavigator;
import lsfusion.server.form.window.AbstractWindow;
import lsfusion.server.lifecycle.LifecycleAdapter;
import lsfusion.server.lifecycle.LifecycleEvent;
import lsfusion.server.logics.i18n.DefaultLocalizer;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.linear.LAP;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.linear.LP;
import lsfusion.server.logics.mutables.NFLazy;
import lsfusion.server.logics.mutables.Version;
import lsfusion.server.logics.property.*;
import lsfusion.server.logics.property.actions.SessionEnvEvent;
import lsfusion.server.logics.property.actions.SystemEvent;
import lsfusion.server.logics.property.cases.AbstractCase;
import lsfusion.server.logics.property.cases.graph.Graph;
import lsfusion.server.logics.property.group.AbstractGroup;
import lsfusion.server.logics.resolving.*;
import lsfusion.server.logics.scripted.EvalUtils;
import lsfusion.server.logics.scripted.MetaCodeFragment;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.logics.table.ImplementTable;
import lsfusion.server.logics.table.MapKeysTable;
import lsfusion.server.logics.tasks.PublicTask;
import lsfusion.server.logics.tasks.TaskRunner;
import lsfusion.server.mail.NotificationActionProperty;
import lsfusion.server.remote.FormReportManager;
import lsfusion.server.session.ApplyFilter;
import lsfusion.server.session.DataSession;
import lsfusion.server.session.SessionCreator;
import lsfusion.server.session.SingleKeyTableUsage;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static lsfusion.base.BaseUtils.*;
import static lsfusion.server.logics.BusinessLogicsResolvingUtils.findElementByCanonicalName;
import static lsfusion.server.logics.BusinessLogicsResolvingUtils.findElementByCompoundName;

public abstract class BusinessLogics<T extends BusinessLogics<T>> extends LifecycleAdapter implements InitializingBean {
    protected final static Logger logger = ServerLoggers.systemLogger;
    protected final static Logger sqlLogger = ServerLoggers.sqlLogger;
    protected final static Logger startLogger = ServerLoggers.startLogger;
    protected final static Logger lruLogger = ServerLoggers.lruLogger;
    protected final static Logger allocatedBytesLogger = ServerLoggers.allocatedBytesLogger;

    public static final List<String> defaultExcludedScriptPaths = Collections.singletonList("/system");
    public static final List<String> defaultIncludedScriptPaths = Collections.singletonList("");

    private ModuleList modules = new ModuleList();
    
    private Map<String, List<LogicsModule>> namespaceToModules = new HashMap<>();

    private final List<ExternalScreen> externalScreens = new ArrayList<>();

    private final Map<Long, Integer> excessAllocatedBytesMap = new HashMap<>();

    public BaseLogicsModule<T> LM;
    public ServiceLogicsModule serviceLM;
    public ReflectionLogicsModule reflectionLM;
    public AuthenticationLogicsModule authenticationLM;
    public SecurityLogicsModule securityLM;
    public SystemEventsLogicsModule systemEventsLM;
    public EmailLogicsModule emailLM;
    public SchedulerLogicsModule schedulerLM;
    public TimeLogicsModule timeLM;

    private String topModule;

    private String orderDependencies;

    private LocalizedString.Localizer localizer;
    
    private PublicTask initTask;

    //чтобы можно было использовать один инстанс логики с несколькими инстансами, при этом инициализировать только один раз
    private final AtomicBoolean initialized = new AtomicBoolean();

    public BusinessLogics() {
        super(LOGICS_ORDER);
    }

    // жестковато, но учитывая что пока есть несколько других кэшей со strong ref'ами на этот action, завязаных на IdentityLazy то цикл жизни у всех этих кэшей будет приблизительно одинаковый
    @IdentityLazy
    public LAP<?> evaluateRun(String script, boolean action) throws EvalUtils.EvaluationException, ScriptingErrorLog.SemanticErrorException  {
        ScriptingLogicsModule module = EvalUtils.evaluate(this, script, action);
        String runName = module.getName() + ".run";
        return module.findAction(runName);
    }

    public void setTopModule(String topModule) {
        this.topModule = topModule;
    }

    public void setOrderDependencies(String orderDependencies) {
        this.orderDependencies = orderDependencies;
    }

    public void setInitTask(PublicTask initTask) {
        this.initTask = initTask;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(initTask, "initTask must be specified");
        
        LRUUtil.initLRUTuner(new LRULogger() {
            @Override
            public void log(String log) {
                lruLogger.info(log);
            }
        });
    }

    @Override
    protected void onInit(LifecycleEvent event) {
        if (initialized.compareAndSet(false, true)) {
            startLogger.info("Initializing BusinessLogics");
            try {
                getDbManager().ensureLogLevel();
                
                new TaskRunner(this).runTask(initTask, startLogger);
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("Error initializing BusinessLogics: ", e);
            }
        }
    }

    public LRUWSASVSMap<Object, Method, Object, Object> startLruCache = new LRUWSASVSMap<>(LRUUtil.G2);
    public void cleanCaches() {
        startLruCache = null;
        MapCacheAspect.cleanClassCaches();
        CalcProperty.cleanPropCaches();

        startLogger.info("Obsolete caches were successfully cleaned");
    }
    
    public ScriptingLogicsModule getModule(String name) {
        return (ScriptingLogicsModule) getSysModule(name);
    }
    
    public LogicsModule getSysModule(String name) {
        return modules.get(name);
    }

    public ConcreteClass getDataClass(Object object, Type type) {
        try {
            try (DataSession session = getDbManager().createSession()) {
                return type.getDataClass(object, session.sql, LM.baseClass.getUpSet(), LM.baseClass, OperationOwner.unknown);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ArrayList<IDaemonTask> getDaemonTasks(long compId) {
        ArrayList<IDaemonTask> daemons = new ArrayList<>();

        Integer scannerComPort;
        Boolean scannerSingleRead;
        boolean useDiscountCardReader;
        Integer scalesComPort;

        try {
            try(DataSession session = getDbManager().createSession()) {
                DataObject computerObject = new DataObject(compId, authenticationLM.computer);
                scannerComPort = (Integer) authenticationLM.scannerComPortComputer.read(session, computerObject);
                scannerSingleRead = (Boolean) authenticationLM.scannerSingleReadComputer.read(session, computerObject);
                useDiscountCardReader = authenticationLM.useDiscountCardReaderComputer.read(session, computerObject) != null;
                scalesComPort = (Integer) authenticationLM.scalesComPortComputer.read(session, computerObject);
            }
        } catch (SQLException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }
        if(useDiscountCardReader)
            daemons.add(new DiscountCardDaemonTask());
        if (scannerComPort != null) {
            IDaemonTask task = new ScannerDaemonTask(scannerComPort, ((Boolean)true).equals(scannerSingleRead));
            daemons.add(task);
        }
        if (scalesComPort != null) {
            IDaemonTask task = new WeightDaemonTask(scalesComPort);
            daemons.add(task);
        }
        return daemons;
    }

    protected void addExternalScreen(ExternalScreen screen) {
        externalScreens.add(screen);
    }

    public ExternalScreen getExternalScreen(int screenID) {
        for (ExternalScreen screen : externalScreens) {
            if (screen.getID() == screenID) {
                return screen;
            }
        }
        return null;
    }

    public ExternalScreenParameters getExternalScreenParameters(int screenID, long computerId) throws RemoteException {
        return null;
    }

    protected <M extends LogicsModule> M addModule(M module) {
        modules.add(module);
        return module;
    }

    public void createModules() throws IOException {
        LM = addModule(new BaseLogicsModule(this, getDBNamingPolicy()));
        serviceLM = addModule(new ServiceLogicsModule(this, LM));
        reflectionLM = addModule(new ReflectionLogicsModule(this, LM));
        authenticationLM = addModule(new AuthenticationLogicsModule(this, LM));
        securityLM = addModule(new SecurityLogicsModule(this, LM));
        systemEventsLM = addModule(new SystemEventsLogicsModule(this, LM));
        emailLM = addModule(new EmailLogicsModule(this, LM));
        schedulerLM = addModule(new SchedulerLogicsModule(this, LM));
        timeLM = addModule(new TimeLogicsModule(this, LM));
    }

    private DBNamingPolicy getDBNamingPolicy() {
        DBNamingPolicy dbNamingPolicy = null;
        try {
            String policyName = getDbManager().getDbNamingPolicy();
            if (policyName != null && !policyName.isEmpty()) {
                Integer maxIdLength = getDbManager().getDbMaxIdLength();
                Class cls = Class.forName(policyName);
                dbNamingPolicy = (DBNamingPolicy) cls.getConstructors()[0].newInstance(maxIdLength);
            }
        } catch (InvocationTargetException | ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            logger.error("Failed to get DBNamingPolicy, used default", e);
        }
        return dbNamingPolicy == null ? new DefaultDBNamingPolicy(63) : dbNamingPolicy;
    }

    protected void addModulesFromResource(List<String> paths, List<String> excludedPaths) throws IOException {
        if (excludedPaths == null || excludedPaths.isEmpty()) {
            excludedPaths = defaultExcludedScriptPaths;
        } else {
            excludedPaths = new ArrayList<>(excludedPaths);
            excludedPaths.addAll(defaultExcludedScriptPaths);
        }

        List<String> excludedLSF = new ArrayList<>();

        for (String filePath : excludedPaths) {
            if(!filePath.startsWith("/"))
                filePath = "/" + filePath;
            
            if (filePath.contains("*")) {
                filePath += filePath.endsWith(".lsf") ? "" : ".lsf";
                Pattern pattern = Pattern.compile(filePath.replace("*", ".*"));
                Collection<String> list = ResourceUtils.getResources(pattern);
                for (String name : list) {
                    excludedLSF.add(name);
                }
            } else if (filePath.endsWith(".lsf")) {
                excludedLSF.add(filePath);
            } else {
                Pattern pattern = Pattern.compile(filePath + ".*\\.lsf");
                Collection<String> list = ResourceUtils.getResources(pattern);
                for (String name : list) {
                    excludedLSF.add(name);
                }
            }
        }

        for (String filePath : paths) {
            if(!filePath.startsWith("/"))
                filePath = "/" + filePath;

            if (filePath.contains("*")) {
                filePath += filePath.endsWith(".lsf") ? "" : ".lsf";
                Pattern pattern = Pattern.compile(filePath.replace("*", ".*"));
                Collection<String> list = ResourceUtils.getResources(pattern);
                for (String name : list) {
                    if (!excludedLSF.contains(name)) {
                        addModulesFromResource(name);
                    }
                }
            } else if (filePath.endsWith(".lsf")) {
                if (!excludedLSF.contains(filePath)) {
                    addModulesFromResource(filePath);
                }
            } else {
                Pattern pattern = Pattern.compile(filePath + ".*\\.lsf");
                Collection<String> list = ResourceUtils.getResources(pattern);
                for (String name : list) {
                    if (!excludedLSF.contains(name)) {
                        addModulesFromResource(name);
                    }
                }
            }
        }
    }

    private void addModulesFromResource(String... paths) throws IOException {
        for (String path : paths) {
            addModuleFromResource(path);
        }
    }

    private void addModuleFromResource(String path) throws IOException {
        InputStream is = getClass().getResourceAsStream(path);
        if (is == null)
            throw new RuntimeException(String.format("[error]:\tmodule '%s' cannot be found", path));
        addModule(new ScriptingLogicsModule(is, path, LM, this));
    }
    
    public void initObjectClass() {
        LM.baseClass.initObjectClass(LM.getVersion(), CanonicalNameUtils.createCanonicalName(LM.getNamespace(), "CustomObjectClass"));
        LM.storeCustomClass(LM.baseClass.objectClass);
    }

    public void initLocalizer() {
        localizer = new DefaultLocalizer();
    }
    
    public LocalizedString.Localizer getLocalizer() {
        return localizer;
    }

    public void initModuleOrders() {
        modules.fillNameToModules();
        if (!isRedundantString(topModule)) {
            modules.filterWithTopModule(topModule);
        }
        modules.setOrderDependencies(orderDependencies);
        modules.orderModules();

        fillNamespaceToModules();
        fillModulesVisibleAndOrder();
    }

    private void fillNamespaceToModules() {
        for (LogicsModule module : modules.all()) {
            String namespace = module.getNamespace();
            if (!namespaceToModules.containsKey(namespace)) {
                namespaceToModules.put(namespace, new ArrayList<LogicsModule>());
            }
            namespaceToModules.get(namespace).add(module);
        }
    }
    
    private void fillModulesVisibleAndOrder() {
        Map<LogicsModule, ImSet<LogicsModule>> recRequiredModules = new HashMap<>();
        
        for (LogicsModule module : modules.all()) {
            MSet<LogicsModule> mRecDep = SetFact.mSet();
            mRecDep.add(module);
            for (String requiredName : module.getRequiredNames())
                mRecDep.addAll(recRequiredModules.get(modules.get(requiredName)));
            recRequiredModules.put(module, mRecDep.immutable());
        }

        int moduleNumber = 0;
        for (LogicsModule module : modules.all()) {
            module.visible = recRequiredModules.get(module).mapSetValues(new GetValue<Version, LogicsModule>() {
                public Version getMapValue(LogicsModule value) {
                    return value.getVersion();
                }});
            module.order = (moduleNumber++);
        }
    }
    
    public void initFullSingleTables() {
        for(ImplementTable table : LM.tableFactory.getImplementTables()) {
            if(table.markedFull && !table.isFull())  // для второго условия все и делается, чтобы не создавать лишние св-ва
                LM.markFull(table, table.getMapFields().singleValue());
        }
    }

    private boolean needIndex(ObjectValueClassSet classSet) {
        ImSet<ConcreteCustomClass> set = classSet.getSetConcreteChildren();
        if(set.size() > 1) { // оптимизация
//            int count = classSet.getCount(); // it's dangerous because if updateStats fails for some reason, then server starts dropping large indices 
//            if(count >= Settings.get().getMinClassDataIndexCount()) {
//                Stat totStat = new Stat(count);
//                for (ConcreteCustomClass customClass : set)
//                    if (new Stat(customClass.getCount()).less(totStat))
                        return true;
//            }
        }
        return false;
    }

    public void initClassDataProps() {
        ImMap<ImplementTable, ImSet<ConcreteCustomClass>> groupTables = getConcreteCustomClasses().group(new BaseUtils.Group<ImplementTable, ConcreteCustomClass>() {
            public ImplementTable group(ConcreteCustomClass customClass) {
                return LM.tableFactory.getClassMapTable(MapFact.singletonOrder("key", (ValueClass) customClass)).table;
            }
        });

        for(int i=0,size=groupTables.size();i<size;i++) {
            ImplementTable table = groupTables.getKey(i);
            ImSet<ConcreteCustomClass> set = groupTables.getValue(i);

            ObjectValueClassSet classSet = OrObjectClassSet.fromSetConcreteChildren(set);

            CustomClass tableClass = (CustomClass) table.getMapFields().singleValue();
            // помечаем full tables
            assert tableClass.getUpSet().containsAll(classSet, false); // должны быть все классы по определению, исходя из логики раскладывания классов по таблицам
            boolean isFull = classSet.containsAll(tableClass.getUpSet(), false);
            if(isFull) // важно чтобы getInterfaceClasses дал тот же tableClass
                classSet = tableClass.getUpSet();

            ClassDataProperty dataProperty = new ClassDataProperty(LocalizedString.create(classSet.toString(), false), classSet);
            LCP<ClassPropertyInterface> lp = new LCP<>(dataProperty);
            LM.addProperty(null, new LCP<>(dataProperty));
            LM.makePropertyPublic(lp, PropertyCanonicalNameUtils.classDataPropPrefix + table.getName(), Collections.<ResolveClassSet>singletonList(ResolveOrObjectClassSet.fromSetConcreteChildren(set)));
            // именно такая реализация, а не implementTable, из-за того что getInterfaceClasses может попасть не в "класс таблицы", а мимо и тогда нарушится assertion что должен попасть в ту же таблицу, это в принципе проблема getInterfaceClasses
            dataProperty.markStored(LM.tableFactory, new MapKeysTable<>(table, MapFact.singletonRev(dataProperty.interfaces.single(), table.keys.single())));

            // помечаем dataProperty
            for(ConcreteCustomClass customClass : set)
                customClass.dataProperty = dataProperty;
            if(isFull) // неважно implicit или нет
                table.setFullField(dataProperty);
        }
    }
    
    // если добавлять CONSTRAINT SETCHANGED не забыть задание в графе запусков перетащить
    public void initClassAggrProps() {
        MOrderExclSet<CalcProperty> queue = SetFact.mOrderExclSet();

        for(Property property : getProperties())
            if(property instanceof CalcProperty) {
                CalcProperty calcProperty = (CalcProperty) property;
                if(calcProperty.isAggr())
                    queue.exclAdd(calcProperty);
            }

        MAddExclMap<CustomClass, MSet<CalcProperty>> classAggrProps = MapFact.mAddExclMap();
            
        for(int i=0,size=queue.size();i<size;i++) {
            CalcProperty<?> property = queue.get(i);
            ImMap<?, ValueClass> interfaceClasses = property.getInterfaceClasses(ClassType.materializeChangePolicy);
            if(interfaceClasses.size() == 1) {
                ValueClass valueClass = interfaceClasses.singleValue();
                if(valueClass instanceof CustomClass) {
                    CustomClass customClass = (CustomClass) valueClass;
                    MSet<CalcProperty> mAggrProps = classAggrProps.get(customClass);
                    if(mAggrProps == null) {
                        mAggrProps = SetFact.mSet();
                        classAggrProps.exclAdd(customClass, mAggrProps);
                    }
                    mAggrProps.add(property);
                }
            }
           
            // все implement'ы тоже помечаем как aggr
            for(CalcProperty implement : property.getImplements())
                if(!queue.contains(implement)) {
                    queue.exclAdd(implement);
                    size++;
                }
        }

        for(int i=0,size=classAggrProps.size();i<size;i++)
            classAggrProps.getKey(i).aggrProps = classAggrProps.getValue(i).immutable();
    }

    public void initClassDataIndices() {
        for(ObjectClassField classField : LM.baseClass.getUpObjectClassFields().keyIt()) {
            ClassDataProperty classProperty = classField.getProperty();
            if(needIndex(classProperty.set))
                LM.addIndex(classProperty);
        }
    }

    public void initReflectionEvents() {

        try {
            SQLSession sql = getDbManager().getThreadLocalSql();
            boolean prevSuppressErrorLogging = sql.suppressErrorLogging;
            try {                
                sql.suppressErrorLogging = true;

                //временное решение
                try {
                    updateStats(sql, true);
                } catch (Exception ignored) {
                    startLogger.info("Error updating stats, while initializing reflection events occurred. Probably this is the first database synchronization. Look to the exinfo log for details.");
                    ServerLoggers.exInfoLogger.error("Error updating stats, while initializing reflection events", ignored);
                }

                startLogger.info("Setting user logging for properties");
                setUserLoggableProperties(sql);

                startLogger.info("Setting user not null constraints for properties");
                setNotNullProperties(sql);

                startLogger.info("Setting user notifications for property changes");
                setupPropertyNotifications(sql);
            } finally {
                sql.suppressErrorLogging = prevSuppressErrorLogging;
            }
        } catch (Exception ignored) {
            startLogger.info("Error while initializing reflection events occurred. Probably this is the first database synchronization. Look to the exinfo log for details.");
            ServerLoggers.exInfoLogger.error("Error while initializing reflection events", ignored);
        }
    }

    public Long readCurrentUser() {
        try {
            return (Long) authenticationLM.currentUser.read(getDbManager().createSession());
        } catch (Exception e) {
            return null;
        }
    }

    // временный хак для перехода на явную типизацию
    public static boolean useReparse = false;
    public static final ThreadLocal<ImMap<String, String>> reparse = new ThreadLocal<>();

    private void setUserLoggableProperties(SQLSession sql) throws SQLException, IllegalAccessException, InstantiationException, ClassNotFoundException, SQLHandledException {
        Map<String, String> changes = getDbManager().getPropertyCNChanges(sql);
        
        Integer maxStatsProperty = null;
        try {
            maxStatsProperty = (Integer) reflectionLM.maxStatsProperty.read(sql, Property.defaultModifier, DataSession.emptyEnv(OperationOwner.unknown));
        } catch (Exception ignored) {
        }

        LCP<PropertyInterface> isProperty = LM.is(reflectionLM.property);
        ImRevMap<PropertyInterface, KeyExpr> keys = isProperty.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<PropertyInterface, Object> query = new QueryBuilder<>(keys);
        query.addProperty("CNProperty", reflectionLM.canonicalNameProperty.getExpr(key));
        query.addProperty("overStatsProperty", reflectionLM.overStatsProperty.getExpr(key));
        query.and(reflectionLM.userLoggableProperty.getExpr(key).getWhere());
        ImOrderMap<ImMap<PropertyInterface, Object>, ImMap<Object, Object>> result = query.execute(sql, OperationOwner.unknown);

        for (ImMap<Object, Object> values : result.valueIt()) {
            String canonicalName = values.get("CNProperty").toString().trim();
            if (changes.containsKey(canonicalName)) {
                canonicalName = changes.get(canonicalName);
            }
            LCP<?> lcp = null;
            try {
                lcp = findProperty(canonicalName);
            } catch (Exception ignored) {
            }
            if(lcp != null) { // temporary for migration, так как могут на действиях стоять
                Integer statsProperty = (Integer) values.get("overStatsProperty");
                statsProperty = statsProperty == null ? getStatsProperty(lcp.property) : statsProperty;
                if (statsProperty == null || maxStatsProperty == null || statsProperty < maxStatsProperty) {
                    LM.makeUserLoggable(systemEventsLM, lcp);
                }
            }            
        }
    }

    public Integer getStatsProperty (Property property) {
        Integer statsProperty = null;
        if (property instanceof AggregateProperty) {
            StatKeys classStats = ((AggregateProperty) property).getInterfaceClassStats();
            if (classStats != null && classStats.getRows() != null)
                statsProperty = classStats.getRows().getCount();
        }
        return statsProperty;
    }

    private void setNotNullProperties(SQLSession sql) throws SQLException, IllegalAccessException, InstantiationException, ClassNotFoundException, SQLHandledException {
        
        LCP isProperty = LM.is(reflectionLM.property);
        ImRevMap<Object, KeyExpr> keys = isProperty.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("CNProperty", reflectionLM.canonicalNameProperty.getExpr(key));
        query.and(reflectionLM.isSetNotNullProperty.getExpr(key).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(sql, OperationOwner.unknown);

        for (ImMap<Object, Object> values : result.valueIt()) {
            LCP<?> prop = findProperty(values.get("CNProperty").toString().trim());
            if(prop != null) {
                prop.property.reflectionNotNull = true;
                LM.setNotNull(prop, ListFact.<PropertyFollowsDebug>EMPTY());
            }
        }
    }

    private void setupPropertyNotifications(SQLSession sql) throws SQLException, IllegalAccessException, InstantiationException, ClassNotFoundException, SQLHandledException {

        KeyExpr notificationExpr = new KeyExpr("notification");
        KeyExpr propertyExpr = new KeyExpr("property");

        ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "notification", notificationExpr, "property", propertyExpr);

        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

        String[] notificationNames = new String[]{"isDerivedChange", "subject", "text", "emailFrom", "emailTo", "emailToCC", "emailToBC"};
        LCP[] notificationProperties = new LCP[]{emailLM.isEventNotification, emailLM.subjectNotification, emailLM.textNotification,
                emailLM.emailFromNotification, emailLM.emailToNotification, emailLM.emailToCCNotification, emailLM.emailToBCNotification};
        for (int i = 0; i < notificationProperties.length; i++) {
            query.addProperty(notificationNames[i], notificationProperties[i].getExpr(notificationExpr));
        }
        query.addProperty("CNProperty", reflectionLM.canonicalNameProperty.getExpr(propertyExpr));
        query.and(emailLM.textNotification.getExpr(notificationExpr).getWhere());
        query.and(emailLM.inNotificationProperty.getExpr(notificationExpr, propertyExpr).getWhere());

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(sql, OperationOwner.unknown);

        for (ImMap<Object, Object> entry : result.values()) {

            String cnProperty = trim((String) entry.get("CNProperty"));
            if(cnProperty != null) {
                LCP prop = findProperty(cnProperty);

                boolean isDerivedChange = entry.get("isDerivedChange") != null;
                String subject = trim((String) entry.get("subject"));
                String text = trim((String) entry.get("text"));
                String emailFrom = trim((String) entry.get("emailFrom"));
                String emailTo = trim((String) entry.get("emailTo"));
                String emailToCC = trim((String) entry.get("emailToCC"));
                String emailToBC = trim((String) entry.get("emailToBC"));
                LAP emailNotificationProperty = LM.addProperty(LM.actionGroup, new LAP(new NotificationActionProperty(LocalizedString.create("emailNotificationProperty"), prop, subject, text, emailFrom, emailTo, emailToCC, emailToBC, emailLM)));

                Integer[] params = new Integer[prop.listInterfaces.size()];
                for (int j = 0; j < prop.listInterfaces.size(); j++)
                    params[j] = j + 1;
                if (isDerivedChange)
                    emailNotificationProperty.setEventAction(LM, prop, params);
                else
                    emailNotificationProperty.setEventSetAction(LM, prop, params);
            }
        }
    }

    public void updateClassStats(SQLSession session, boolean useSIDs) throws SQLException, SQLHandledException {
        if(useSIDs)
            updateClassSIDStats(session);
        else
            updateClassStats(session);
    }
    public void updateClassStats(SQLSession session) throws SQLException, SQLHandledException {
        ImMap<Long, Integer> customObjectClassMap = readClassStatsFromDB(session);

        for(CustomClass customClass : LM.baseClass.getAllClasses()) {
            if(customClass instanceof ConcreteCustomClass) {
                ((ConcreteCustomClass) customClass).updateStat(customObjectClassMap);
            }
        }
    }

    public ImMap<Long, Integer> readClassStatsFromDB(SQLSession session) throws SQLException, SQLHandledException {
        KeyExpr customObjectClassExpr = new KeyExpr("customObjectClass");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object)"key", customObjectClassExpr);

        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("statCustomObjectClass", LM.statCustomObjectClass.getExpr(customObjectClassExpr));

        query.and(LM.statCustomObjectClass.getExpr(customObjectClassExpr).getWhere());

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session, OperationOwner.unknown);

        MExclMap<Long, Integer> mCustomObjectClassMap = MapFact.mExclMap(result.size());
        for (int i=0,size=result.size();i<size;i++) {
            Integer statCustomObjectClass = (Integer) result.getValue(i).get("statCustomObjectClass");
            mCustomObjectClassMap.exclAdd((Long) result.getKey(i).get("key"), statCustomObjectClass);
        }
        return mCustomObjectClassMap.immutable();
    }

    public void updateClassSIDStats(SQLSession session) throws SQLException, SQLHandledException {
        ImMap<String, Integer> customSIDObjectClassMap = readClassSIDStatsFromDB(session);

        for(CustomClass customClass : LM.baseClass.getAllClasses()) {
            if(customClass instanceof ConcreteCustomClass) {
                ((ConcreteCustomClass) customClass).updateSIDStat(customSIDObjectClassMap);
            }
        }
    }

    public ImMap<String, Integer> readClassSIDStatsFromDB(SQLSession session) throws SQLException, SQLHandledException {
        KeyExpr customObjectClassExpr = new KeyExpr("customObjectClass");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object)"key", customObjectClassExpr);

        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("statCustomObjectClass", LM.statCustomObjectClass.getExpr(customObjectClassExpr));
        query.addProperty("staticName", LM.staticName.getExpr(customObjectClassExpr));

        query.and(LM.statCustomObjectClass.getExpr(customObjectClassExpr).getWhere());

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session, OperationOwner.unknown);

        MExclMap<String, Integer> mCustomObjectClassMap = MapFact.mExclMapMax(result.size());
        for (int i=0,size=result.size();i<size;i++) {
            Integer statCustomObjectClass = (Integer) result.getValue(i).get("statCustomObjectClass");
            String sID = (String)result.getValue(i).get("staticName");
            if(sID != null)
                mCustomObjectClassMap.exclAdd(sID.trim(), statCustomObjectClass);
        }
        return mCustomObjectClassMap.immutable();
    }

    public ImMap<String, Integer> updateStats(SQLSession sql, boolean useSIDsForClasses) throws SQLException, SQLHandledException {
        ImMap<String, Integer> result = updateTableStats(sql, true); // чтобы сами таблицы статистики получили статистику
        updateFullClassStats(sql, useSIDsForClasses);
        if(SystemProperties.doNotCalculateStats)
            return result;
        return updateTableStats(sql, false);
    }
    
    private void updateFullClassStats(SQLSession sql, boolean useSIDsForClasses) throws SQLException, SQLHandledException {
        updateClassStats(sql, useSIDsForClasses);

        adjustClassStats(sql);        
    }

    private void adjustClassStats(SQLSession sql) throws SQLException, SQLHandledException {
        ImMap<String, Integer> tableStats = readStatsFromDB(sql, reflectionLM.tableSID, reflectionLM.rowsTable, null);
        ImMap<String, Integer> keyStats = readStatsFromDB(sql, reflectionLM.tableKeySID, reflectionLM.overQuantityTableKey, null);

        MMap<CustomClass, Integer> mClassFullStats = MapFact.mMap(MapFact.<CustomClass>max());
        for (ImplementTable dataTable : LM.tableFactory.getImplementTables()) {
            dataTable.fillFullClassStat(tableStats, keyStats, mClassFullStats);
        }
        ImMap<CustomClass, Integer> classFullStats = mClassFullStats.immutable();

        // правим статистику по классам
        ImOrderMap<CustomClass, Integer> orderedClassFullStats = classFullStats.sort(BaseUtils.<Comparator<CustomClass>>immutableCast(ValueClass.comparator));// для детерминированности
        for(int i=0,size=orderedClassFullStats.size();i<size;i++) {
            CustomClass customClass = orderedClassFullStats.getKey(i);
            int quantity = orderedClassFullStats.getValue(i);
            ImOrderSet<ConcreteCustomClass> concreteChildren = customClass.getUpSet().getSetConcreteChildren().sortSet(BaseUtils.<Comparator<ConcreteCustomClass>>immutableCast(ValueClass.comparator));// для детерминированности
            int childrenStat = 0;
            for(ConcreteCustomClass child : concreteChildren) {
                childrenStat += child.getCount();
            }
            quantity = quantity - childrenStat; // сколько дораспределить
            for(ConcreteCustomClass child : concreteChildren) {
                int count = child.getCount();
                int newCount = (int)((long)quantity * (long)count / (long)childrenStat);
                child.stat = count + newCount;
                assert child.stat >= 0;
                quantity -= newCount;
                childrenStat -= count;
            }
        }
    }

    public ImMap<String, Integer> updateTableStats(SQLSession sql, boolean statDefault) throws SQLException, SQLHandledException {
        ImMap<String, Integer> tableStats;
        ImMap<String, Integer> keyStats;
        ImMap<String, Pair<Integer, Integer>> propStats;
        if(statDefault) {
            tableStats = MapFact.EMPTY();
            keyStats = MapFact.EMPTY();
            propStats = MapFact.EMPTY();
        } else {
            tableStats = readStatsFromDB(sql, reflectionLM.tableSID, reflectionLM.rowsTable, null);
            keyStats = readStatsFromDB(sql, reflectionLM.tableKeySID, reflectionLM.overQuantityTableKey, null);
            propStats = readStatsFromDB(sql, reflectionLM.tableColumnLongSID, reflectionLM.overQuantityTableColumn, reflectionLM.notNullQuantityTableColumn);
        }

        for (ImplementTable dataTable : LM.tableFactory.getImplementTables()) {
            dataTable.updateStat(tableStats, keyStats, propStats, null, statDefault);
        }
        return tableStats;
    }

    public <V> ImMap<String, V> readStatsFromDB(SQLSession sql, LCP sIDProp, LCP statsProp, final LCP notNullProp) throws SQLException, SQLHandledException {
        QueryBuilder<String, String> query = new QueryBuilder<>(SetFact.toSet("key"));
        Expr sidToObject = sIDProp.getExpr(query.getMapExprs().singleValue());
        query.and(sidToObject.getWhere());
        query.addProperty("property", statsProp.getExpr(sidToObject));
        if(notNullProp!=null)
            query.addProperty("notNull", notNullProp.getExpr(sidToObject));
        return query.execute(sql, OperationOwner.unknown).getMap().mapKeyValues(new GetValue<String, ImMap<String, Object>>() {
            public String getMapValue(ImMap<String, Object> key) {
                return ((String) key.singleValue()).trim();
            }}, new GetValue<V, ImMap<String, Object>>() {
            public V getMapValue(ImMap<String, Object> value) {
                if(notNullProp!=null) {
                    return (V) new Pair<>((Integer) value.get("property"), (Integer) value.get("notNull"));
                } else
                    return (V)value.singleValue();
            }});
    }

    private void finishLogInit() {
        // с одной стороны нужно отрисовать на форме логирования все свойства из recognizeGroup, с другой - LogFormEntity с Action'ом должен уже существовать
        // поэтому makeLoggable делаем сразу, а LogFormEntity при желании заполняем здесь
        for (Property property : getOrderProperties()) {
            finishLogInit(property);
        }
    }

    public <P extends PropertyInterface> void finishLogInit(Property property) {
        if (property instanceof CalcProperty && ((CalcProperty)property).isLoggable()) {
            ActionProperty<P> logActionProperty = (ActionProperty<P>) ((CalcProperty)property).getLogFormProperty().property;

            //добавляем в контекстное меню пункт для показа формы
            property.setContextMenuAction(property.getSID(), logActionProperty.caption);
            property.setEditAction(property.getSID(), logActionProperty.getImplement(property.getReflectionOrderInterfaces()));
        }
    }

    @NFLazy
    public void setupPropertyPolicyForms(LAP<?> setupPolicyForPropByCN, Property property, boolean actions) {
        if (property.isNamed()) {
            String propertyCN = property.getCanonicalName();
            
            // issue #47 Потенциальное совпадение канонических имен различных свойств
            // Приходится разделять эти свойства только по имени, а имя приходится создавать из канонического имени 
            // базового свойства, заменив спецсимволы на подчеркивания
            String setupPolicyActionName = (actions ? PropertyCanonicalNameUtils.policyPropPrefix : PropertyCanonicalNameUtils.policyActionPrefix) + PropertyCanonicalNameUtils.makeSafeName(propertyCN); 
            LAP<?> setupPolicyLAP = LM.addJoinAProp(LM.propertyPolicyGroup, LocalizedString.create("{logics.property.propertypolicy.action}"),
                    setupPolicyForPropByCN, LM.addCProp(StringClass.get(propertyCN.length()), LocalizedString.create(propertyCN, false)));
            
            ActionProperty setupPolicyAction = setupPolicyLAP.property;
            LM.makeActionPublic(setupPolicyLAP, setupPolicyActionName, new ArrayList<ResolveClassSet>());
            property.setContextMenuAction(setupPolicyAction.getSID(), setupPolicyAction.caption);
            property.setEditAction(setupPolicyAction.getSID(), setupPolicyAction.getImplement());
        }
    }

    public void prereadCaches() {
        getApplyEvents(ApplyFilter.ONLYCHECK);
        getApplyEvents(ApplyFilter.NO);
        if(Settings.get().isEnableApplySingleStored())
            getOrderMapSingleApplyDepends(ApplyFilter.NO);
    }

    protected void initAuthentication(SecurityManager securityManager) throws SQLException, SQLHandledException {
        securityManager.setupDefaultAdminUser();
    }

    public ImOrderSet<Property> getOrderProperties() {
        return LM.rootGroup.getProperties();
    }

    public ImSet<Property> getProperties() {
        return getOrderProperties().getSet();
    }

    public Iterable<LCP<?>> getNamedProperties() {
        List<Iterable<LCP<?>>> namedProperties = new ArrayList<>();
        for (LogicsModule module : modules.all()) {
            namedProperties.add(module.getNamedProperties());
        }
        return Iterables.concat(namedProperties);
    }

    @IdentityLazy
    public ImOrderSet<CalcProperty> getAutoSetProperties() {
        MOrderExclSet<CalcProperty> mResult = SetFact.mOrderExclSet();
        for (LCP<?> lp : getNamedProperties()) {
            if (lp.property.autoset)
                mResult.exclAdd(lp.property);
        }
        return mResult.immutableOrder();                    
    }

    private Collection<String> customReports;
    @ManualLazy
    public Collection<String> getAllCustomReports() {
        if (SystemProperties.inDevMode || customReports == null) {
            customReports = calculateAllCustomReports();
        }
        return customReports;
    }

    public Collection<String> calculateAllCustomReports() {
        Pattern pattern = Pattern.compile("/"+FormReportManager.reportsDir+".*\\.jrxml");
        return ResourceUtils.getResources(pattern);
    }
    
    public <P extends PropertyInterface> void resolveAutoSet(DataSession session, ConcreteCustomClass customClass, DataObject dataObject, CustomClassListener classListener) throws SQLException, SQLHandledException {

        for (CalcProperty<P> property : getAutoSetProperties()) {
            ValueClass interfaceClass = property.getInterfaceClasses(ClassType.autoSetPolicy).singleValue();
            ValueClass valueClass = property.getValueClass(ClassType.autoSetPolicy);
            if (valueClass instanceof CustomClass && interfaceClass instanceof CustomClass &&
                    customClass.isChild((CustomClass) interfaceClass)) { // в общем то для оптимизации
                Long obj = classListener.getObject((CustomClass) valueClass);
                if (obj != null)
                    property.change(MapFact.singleton(property.interfaces.single(), dataObject), session, obj);
            }
        }
    }
    
    private static class NamedDecl {
        public final LP prop;
        public final String namespace;
        public final boolean defaultNamespace;
        public final List<ResolveClassSet> signature;
        public final Version version;

        public NamedDecl(LP prop, String namespace, boolean defaultNamespace, List<ResolveClassSet> signature, Version version) {
            this.prop = prop;
            this.namespace = namespace;
            this.defaultNamespace = defaultNamespace;
            this.signature = signature;
            this.version = version;
        }
    }

    public Map<String, List<NamedDecl>> getNamedPropertiesWithDeclInfo() {
        Map<String, List<NamedDecl>> result = new HashMap<>();
        for (Map.Entry<String, List<LogicsModule>> namespaceToModule : namespaceToModules.entrySet()) {
            String namespace = namespaceToModule.getKey();
            for (LogicsModule module : namespaceToModule.getValue()) {
                for (LP<?, ?> property : Iterables.concat(module.getNamedProperties(), module.getNamedActions())) {
                    String propertyName = property.property.getName();
                    
                    if (result.get(propertyName) == null) {
                        result.put(propertyName, new ArrayList<NamedDecl>());
                    }
                    List<NamedDecl> resultProps = result.get(propertyName);
                    
                    resultProps.add(new NamedDecl(property, namespace, module.isDefaultNamespace(), module.getParamClasses(property), module.getVersion()));
                }
            }
        }
        return result;
    }
    
    public <A extends PropertyInterface, I extends PropertyInterface> void fillImplicitCases() {
//        ImMap<ImOrderSet<String>, ImSet<String>> mp = getCustomClasses().group(new BaseUtils.Group<String, CustomClass>() {
//            @Override
//            public String group(CustomClass key) {
//                String name = key.getCanonicalName();
//                return name.substring(name.lastIndexOf(".") + 1);
//            }
//        }).filterFnValues(new SFunctionSet<ImSet<CustomClass>>() {
//            @Override
//            public boolean contains(ImSet<CustomClass> element) {
//                return element.size() > 1;
//            }
//        }).mapValues(new GetValue<ImOrderSet<String>, ImSet<CustomClass>>() {
//            @Override
//            public ImOrderSet<String> getMapValue(ImSet<CustomClass> value) {
//                return value.mapSetValues(new GetValue<String, CustomClass>() {
//                    @Override
//                    public String getMapValue(CustomClass value) {
//                        String name = value.getCanonicalName();
//                        return name.substring(0, name.indexOf("."));
//                    }
//                }).sort();
//            }
//        }).groupValues();
//        System.out.println(mp);
//
//        ImMap<ImOrderSet<String>, ImSet<String>> fm = getFormEntities().group(new BaseUtils.Group<String, FormEntity>() {
//            @Override
//            public String group(FormEntity key) {
//                String name = key.getCanonicalName();
//                return name.substring(name.lastIndexOf(".") + 1);
//            }
//        }).filterFnValues(new SFunctionSet<ImSet<FormEntity>>() {
//            @Override
//            public boolean contains(ImSet<FormEntity> element) {
//                return element.size() > 1;
//            }
//        }).mapValues(new GetValue<ImOrderSet<String>, ImSet<FormEntity>>() {
//            @Override
//            public ImOrderSet<String> getMapValue(ImSet<FormEntity> value) {
//                return value.mapSetValues(new GetValue<String, FormEntity>() {
//                    @Override
//                    public String getMapValue(FormEntity value) {
//                        String name = value.getCanonicalName();
//                        return name.substring(0, name.indexOf("."));
//                    }
//                }).sort();
//            }
//        }).groupValues();
//        System.out.println(fm);
        if(!disableImplicitCases) {
            Map<String, List<NamedDecl>> namedProps = getNamedPropertiesWithDeclInfo();
            for (List<NamedDecl> props : namedProps.values()) {
                // бежим по всем парам смотрим подходят друг другу или нет
                for (NamedDecl absDecl : props) {
                    String absNamespace = absDecl.namespace;
                    if (AbstractCase.preFillImplicitCases(absDecl.prop)) {
                        for (NamedDecl impDecl : props)
                            AbstractCase.fillImplicitCases(absDecl.prop, impDecl.prop, absDecl.signature, impDecl.signature, absNamespace.equals(impDecl.namespace), impDecl.version);
                    }
                }
            }
        }
    }
    
    public static final boolean disableImplicitCases = true;
    
    public List<AbstractGroup> getParentGroups() {
        return LM.rootGroup.getParentGroups();
    }

    public ImOrderSet<Property> getPropertyList() {
        return getPropertyListWithGraph(ApplyFilter.NO).first;
    }

    private void fillActionChangeProps() { // используется только для getLinks, соответственно построения лексикографики и поиска зависимостей
        for (Property property : getOrderProperties()) {
            if (property instanceof ActionProperty && !((ActionProperty) property).getEvents().isEmpty()) { // вырежем Action'ы без Event'ов, они нигде не используются, а дают много компонент связности
                ImMap<CalcProperty, Boolean> change = ((ActionProperty<?>) property).getChangeExtProps();
                for (int i = 0, size = change.size(); i < size; i++) // вообще говоря DataProperty и IsClassProperty
                    change.getKey(i).addActionChangeProp(new Pair<ActionProperty<?>, LinkType>((ActionProperty<?>) property, change.getValue(i) ? LinkType.RECCHANGE : LinkType.DEPEND));
            }
        }
    }

    private void dropActionChangeProps() { // для экономии памяти - симметричное удаление ссылок
        for (Property property : getOrderProperties()) {
            if (property instanceof ActionProperty && !((ActionProperty) property).getEvents().isEmpty()) {
                ImMap<CalcProperty, Boolean> change = ((ActionProperty<?>) property).getChangeExtProps();
                for (int i = 0, size = change.size(); i < size; i++)
                    change.getKey(i).dropActionChangeProps();
            }
        }
    }

    // находит свойство входящее в "верхнюю" сильносвязную компоненту
    private static HSet<Link> buildOrder(Property<?> property, MAddMap<Property, HSet<Link>> linksMap, List<Property> order, ImSet<Link> removedLinks, boolean include, ImSet<Property> component, boolean events, boolean recursive, boolean checkNotRecursive) {
        HSet<Link> linksIn = linksMap.get(property);
        if (linksIn == null) { // уже были, linksMap - одновременно используется и как пометки, и как список, и как обратный обход
            assert !(recursive && checkNotRecursive);
            linksIn = new HSet<>();
            linksMap.add(property, linksIn);

            ImSet<Link> links = property.getLinks(events);
            for (int i = 0,size = links.size(); i < size; i++) {
                Link link = links.get(i);
                if (!removedLinks.contains(link) && component.contains(link.to) == include)
                    buildOrder(link.to, linksMap, order, removedLinks, include, component, events, true, checkNotRecursive).add(link);
            }
            if(order != null)
                order.add(property);
        }
        return linksIn;
    }

    private static class PropComparator implements Comparator<Property> {

        private final boolean strictCompare;

        public PropComparator(boolean strictCompare) {
            this.strictCompare = strictCompare;
        }

        public int compare(Property o1, Property o2) {

            String c1 = o1.getCanonicalName();
            String c2 = o2.getCanonicalName();
            if(c1 == null && c2 == null) {
                return ActionProperty.compareChangeExtProps(o1, o2, strictCompare);
            }

            if(c1 == null)
                return 1;

            if(c2 == null)
                return -1;

            assert !(c1.equals(c2) && !BaseUtils.hashEquals(o1,o2) && !(o1 instanceof SessionDataProperty) && !(o2 instanceof SessionDataProperty));
            return c1.compareTo(c2);
        }
    }

    private final static Comparator<Property> strictComparator = new PropComparator(true);
    private final static Comparator<Property> comparator = new PropComparator(false);


    private static int compare(LinkType aType, Property aProp, LinkType bType, Property bProp) {
        int compare = Integer.compare(aType.getNum(), bType.getNum());
        if(compare != 0) // меньше тот у кого связь слабее (num больше)
            return -compare;
        
        return strictComparator.compare(aProp, bProp);        
    }
    // ищем вершину в компоненту (нужно для детерминированности, иначе можно было бы с findMinCycle совместить) - вершину с самыми слабыми исходящими связями (эвристика, потом возможно надо все же объединить все с findMinCycle и искать минимальный цикл с минимальным вырезаемым типом ребра)
    private static Property<?> findMinProperty(HMap<Property, LinkType> component) {
        Property minProp = null;
        LinkType minLinkType = null; 
        for (int i = 0; i < component.size; i++) {
            Property prop = component.getKey(i);
            LinkType linkType = component.getValue(i);
            if(minProp == null || compare(minLinkType, minProp, linkType, prop) > 0) {
                minProp = prop;
                minLinkType = linkType;
            }
        }
        return minProp;
    }
    
    // ищем компоненту (нужно для детерминированности, иначе можно было бы с findMinCycle совместить)
    private static void findComponent(Property<?> property, LinkType linkType, MAddMap<Property, HSet<Link>> linksMap, HSet<Property> proceeded, HMap<Property, LinkType> component) {
        boolean checked = component.containsKey(property);
        component.add(property, linkType);
        if (checked)
            return;

        HSet<Link> linksIn = linksMap.get(property);
        for (int i = 0; i < linksIn.size; i++) {
            Link link = linksIn.get(i);
            if (!proceeded.contains(link.from)) { // если не в верхней компоненте
                findComponent(link.from, link.type, linksMap, proceeded, component);
            }
        }
    }

    private static int compareCycles(List<Link> cycle1, List<Link> cycle2) {
        assert cycle1.size() == cycle2.size();
        for(int i=0,size=cycle1.size();i<size;i++) {
            Link link1 = cycle1.get(i);
            Link link2 = cycle2.get(i);

            int cmp = Integer.compare(link1.type.getNum(), link2.type.getNum());
            if(cmp != 0)
                return cmp;
            cmp = comparator.compare(link1.from, link2.from);
            if(cmp != 0)
                return cmp;
        }

        return strictComparator.compare(cycle1.get(0).from, cycle2.get(0).from);
    }

    private static List<Link> findMinCycle(Property<?> property, MAddMap<Property, HSet<Link>> linksMap, ImSet<Property> component) {
        // поиск в ширину
        HSet<Property> inQueue = new HSet<>();
        Link[] queue = new Link[component.size()];
        Integer[] from = new Integer[component.size()];
        int left = -1; int right = 0;
        int sright = right;
        List<Link> minCycle = null;

        while(true) {
            Property current = left >= 0 ? queue[left].from : property;
            HSet<Link> linksIn = linksMap.get(current);
            for (int i = 0; i < linksIn.size; i++) {
                Link link = linksIn.get(i);

                if(BaseUtils.hashEquals(link.from, property)) { // нашли цикл
                    List<Link> cycle = new ArrayList<>();
                    cycle.add(link);

                    int ifrom = left;
                    while(ifrom != -1) {
                        cycle.add(queue[ifrom]);
                        ifrom = from[ifrom];
                    }

                    if(minCycle == null || compareCycles(minCycle, cycle) > 0) // для детерменированности
                        minCycle = cycle;
                }
                if (component.contains(link.from) && !inQueue.add(link.from)) { // если не в очереди
                    queue[right] = link;
                    from[right++] = left;
                }
            }
            left++;
            if(left == sright) { // новая длина пути
                if(minCycle != null)
                    return minCycle;
                sright = right;
            }
//            if(left == right)
//                break;
        }
    }

    private static Link getMinLink(List<Link> result) {

        // одновременно ведем минимум, путь от начала и link с максимальной длинной
        int firstMinIndex = 0;
        int lastMinIndex = 0;
        int bestMinIndex = 0;
        int maxPath = 0;
        for(int i=1;i<result.size();i++) {
            Link link = result.get(i);

            Link minLink = result.get(lastMinIndex);
            int num = link.type.getNum();
            int minNum = minLink.type.getNum();
            if (num > minNum) {
                firstMinIndex = lastMinIndex = bestMinIndex = i;
                maxPath = 0;
            } else if (num == minNum) { // выбираем с меньшей длиной пути
                int path = i - lastMinIndex;
                if(path > maxPath) { // тут тоже надо детерминировать, когда равны ? (хотя если сверху выставляем минверщину, то не надо)
                    maxPath = path;
                    bestMinIndex = i;
                }
                lastMinIndex = i;
            }
        }

        int roundPath = result.size() - lastMinIndex + firstMinIndex;
        if(roundPath > maxPath) { // замыкаем круг
            bestMinIndex = lastMinIndex;
        }

        return result.get(bestMinIndex);
    }

    // upComponent нужен так как изначально неизвестны все элементы
    private static HSet<Property> buildList(ImSet<Property> props, HSet<Property> exclude, HSet<Link> removedLinks, MOrderExclSet<Property> mResult, boolean events) {
        HSet<Property> proceeded;

        List<Property> order = new ArrayList<>();
        MAddMap<Property, HSet<Link>> linksMap = MapFact.mAddOverrideMap();
        for (int i = 0, size = props.size(); i < size ; i++) {
            Property property = props.get(i);
            if (linksMap.get(property) == null) // проверка что не было
                buildOrder(property, linksMap, order, removedLinks, exclude == null, exclude != null ? exclude : props, events, false, false);
        }

        proceeded = new HSet<>();
        for (int i = 0; i < order.size(); i++) { // тут нужн
            Property orderProperty = order.get(order.size() - 1 - i);
            if (!proceeded.contains(orderProperty)) {
                HMap<Property, LinkType> innerComponentOutTypes = new HMap<>(LinkType.<Property>minLinkAdd());                
                findComponent(orderProperty, LinkType.MAX, linksMap, proceeded, innerComponentOutTypes);

                Property minProperty = findMinProperty(innerComponentOutTypes);
                ImSet<Property> innerComponent = innerComponentOutTypes.keys();
                        
                assert innerComponent.size() > 0;
                if (innerComponent.size() == 1) // если цикла нет все ОК
                    mResult.exclAdd(innerComponent.single());
                else { // нашли цикл
                    // assert что minProperty один из ActionProperty.getChangeExtProps
                    List<Link> minCycle = findMinCycle(minProperty, linksMap, innerComponent);
                    assert BaseUtils.hashEquals(minCycle.get(0).from, minProperty) && BaseUtils.hashEquals(minCycle.get(minCycle.size()-1).to, minProperty);

                    Link minLink = getMinLink(minCycle);
                    removedLinks.exclAdd(minLink);

//                    printCycle("Features", minLink, innerComponent, minCycle);
                    if (minLink.type.equals(LinkType.DEPEND)) { // нашли сильный цикл
                        MOrderExclSet<Property> mCycle = SetFact.mOrderExclSet();
                        buildList(innerComponent, null, removedLinks, mCycle, events);
                        ImOrderSet<Property> cycle = mCycle.immutableOrder();

                        String print = "";
                        for (Property property : cycle)
                            print = (print.length() == 0 ? "" : print + " -> ") + property.toString();
                        throw new RuntimeException(ThreadLocalContext.localize("{message.cycle.detected}") + " : " + print + " -> " + minLink.to);
                    }
                    buildList(innerComponent, null, removedLinks, mResult, events);
                }
                proceeded.exclAddAll(innerComponent);
            }
        }

        return proceeded;
    }

    private static void printCycle(String property, Link minLink, ImSet<Property> innerComponent, List<Link> minCycle) {

        int showCycle = 0;

        for(Property prop : innerComponent) {
            if(prop.toString().contains(property))
                showCycle = 1;
        }

        for(Link link : minCycle) {
            if(link.from.toString().contains(property))
                showCycle = 2;
        }

        if(showCycle > 0) {
            String result = "";
            for(Link link : minCycle) {
                result += " " + link.from;
            }
            System.out.println(showCycle + " LEN " + minCycle.size() + " COMP " + innerComponent.size() + " MIN " + minLink.from + " " + result);
        }
    }

    private static boolean findDependency(Property<?> property, Property<?> with, HSet<Property> proceeded, Stack<Link> path, LinkType desiredType) {
        if (property.equals(with))
            return true;

        if (proceeded.add(property))
            return false;

        for (Link link : property.getLinks(true)) {
            path.push(link);
            if (link.type.getNum() <= desiredType.getNum() && findDependency(link.to, with, proceeded, path, desiredType))
                return true;
            path.pop();
        }
        property.dropLinks();

        return false;
    }

    private static boolean findCalcDependency(CalcProperty<?> property, CalcProperty<?> with, HSet<CalcProperty> proceeded, Stack<CalcProperty> path) {
        if (property.equals(with))
            return true;

        if (proceeded.add(property))
            return false;

        for (CalcProperty link : property.getDepends()) {
            path.push(link);
            if (findCalcDependency(link, with, proceeded, path))
                return true;
            path.pop();
        }

        return false;
    }

    private static String outDependency(String direction, Property property, Stack<Link> path) {
        String result = direction + " : " + property;
        for (Link link : path)
            result += " " + link.type + " " + link.to;
        return result;
    }

    private static String outCalcDependency(String direction, CalcProperty property, Stack<CalcProperty> path) {
        String result = direction + " : " + property;
        for (CalcProperty link : path)
            result += " " + link;
        return result;
    }

    private static Property checkJoinProperty(Property<?> property) {
        if(property instanceof JoinProperty) {
            JoinProperty joinProperty = (JoinProperty) property;
            if(joinProperty.isIdentity()) {
                return checkJoinProperty(joinProperty.implement.property);
            }
        }
        return property;
    }
    private static String findDependency(Property<?> property1, Property<?> property2, LinkType desiredType) {
        property1 = checkJoinProperty(property1);
        property2 = checkJoinProperty(property2);

        String result = findEventDependency(property1, property2, desiredType);
        if(property1 instanceof CalcProperty && property2 instanceof CalcProperty)
            result += findCalcDependency((CalcProperty)property1, (CalcProperty)property2);

        return result;
    }

    private static String findEventDependency(Property<?> property1, Property<?> property2, LinkType desiredType) {
        String result = "";

        Stack<Link> forward = new Stack<>();
        if (findDependency(property1, property2, new HSet<Property>(), forward, desiredType))
            result += outDependency("FORWARD (" + forward.size() + ")", property1, forward) + '\n';

        Stack<Link> backward = new Stack<>();
        if (findDependency(property2, property1, new HSet<Property>(), backward, desiredType))
            result += outDependency("BACKWARD (" + backward.size() + ")", property2, backward) + '\n';

        if (result.isEmpty())
            result += "NO DEPENDENCY " + property1 + " " + property2 + '\n';
        
        return result;
    }

    public static String findCalcDependency(CalcProperty<?> property1, CalcProperty<?> property2) {
        String result = "";

        Stack<CalcProperty> forward = new Stack<>();
        if (findCalcDependency(property1, property2, new HSet<CalcProperty>(), forward))
            result += outCalcDependency("FORWARD CALC (" + forward.size() + ")", property1, forward) + '\n';

        Stack<CalcProperty> backward = new Stack<>();
        if (findCalcDependency(property2, property1, new HSet<CalcProperty>(), backward))
            result += outCalcDependency("BACKWARD CALC (" + backward.size() + ")", property2, backward) + '\n';

        if (result.isEmpty())
            result += "NO CALC DEPENDENCY " + property1 + " " + property2 + '\n';
        return result;
    }

    public void showDependencies() {
        String show = "";

        boolean found = false; // оптимизация, так как showDep не так часто используется

        for (Property property : getOrderProperties())
            if (property.showDep != null) {
                if(!found) {
                    fillActionChangeProps();
                    found = true;
                }
                show += findDependency(property, property.showDep, LinkType.USEDACTION);
            }
        if (!show.isEmpty()) {
            logger.debug("Dependencies: " + show);
        }

        if(found)
            dropActionChangeProps();
    }

    @IdentityLazy
    public Graph<ActionProperty> getRecalculateFollowsGraph() {
        return BaseUtils.immutableCast(getPropertyGraph().filterGraph(new SFunctionSet<Property>() {
            public boolean contains(Property element) {
                return element instanceof ActionProperty && ((ActionProperty) element).hasResolve();
            }
        }));
    }

    @IdentityLazy
    public Graph<AggregateProperty> getAggregateStoredGraph() {
        return BaseUtils.immutableCast(getPropertyGraph().filterGraph(new SFunctionSet<Property>() {
            public boolean contains(Property element) {
                return element instanceof AggregateProperty && ((AggregateProperty) element).isStored();
            }
        }));
    }

    public Graph<AggregateProperty> getRecalculateAggregateStoredGraph() {
        QueryBuilder<String, Object> query = new QueryBuilder<>(SetFact.singleton("key"));

        ImSet<String> skipProperties = SetFact.EMPTY();
        try (final DataSession dataSession = getDbManager().createSession()) {

            Expr expr = reflectionLM.disableAggregationsTableColumnSID.getExpr(query.getMapExprs().singleValue());
            query.and(expr.getWhere());
            skipProperties = query.execute(dataSession).keys().mapSetValues(new GetValue<String, ImMap<String,Object>>() {
                @Override
                public String getMapValue(ImMap<String, Object> value) {
                    return (String)value.singleValue();
                }
            });

        } catch (SQLException | SQLHandledException e) {
            serviceLogger.info(e.getMessage());
        }


        final ImSet<String> fSkipProperties = skipProperties;
        return getAggregateStoredGraph().filterGraph(new SFunctionSet<AggregateProperty>() {
            public boolean contains(AggregateProperty element) {
                return !fSkipProperties.contains(element.getDBName());
            }
        });
    }

    public Graph<Property> getPropertyGraph() {
        return getPropertyListWithGraph(ApplyFilter.NO).second;
    }

    @IdentityStrongLazy // глобальное очень сложное вычисление
    public Pair<ImOrderSet<Property>, Graph<Property>> getPropertyListWithGraph(ApplyFilter filter) {
        // жестковато тут конечно написано, но пока не сильно времени жрет

        fillActionChangeProps();

        // сначала бежим по Action'ам с cancel'ами
        HSet<Property> cancelActions = new HSet<>();
        HSet<Property> rest = new HSet<>();
        for (Property property : getOrderProperties())
            if(filter.contains(property)) {
                if (ApplyFilter.isCheck(property))
                    cancelActions.add(property);
                else
                    rest.add(property);
            }
        boolean events = filter != ApplyFilter.ONLY_DATA;

        MOrderExclSet<Property> mCancelResult = SetFact.mOrderExclSet();
        HSet<Link> firstRemoved = new HSet<>();
        HSet<Property> proceeded = buildList(cancelActions, new HSet<Property>(), firstRemoved, mCancelResult, events);
        ImOrderSet<Property> cancelResult = mCancelResult.immutableOrder();

        // потом бежим по всем остальным, за исключением proceeded
        MOrderExclSet<Property> mRestResult = SetFact.mOrderExclSet();
        HSet<Property> removed = new HSet<>();
        removed.addAll(rest.remove(proceeded));
        HSet<Link> secondRemoved = new HSet<>();
        buildList(removed, proceeded, secondRemoved, mRestResult, events); // потом этот cast уберем
        ImOrderSet<Property> restResult = mRestResult.immutableOrder();

        // затем по всем кроме proceeded на прошлом шаге
        assert cancelResult.getSet().disjoint(restResult.getSet());
        ImOrderSet<Property> result = cancelResult.reverseOrder().addOrderExcl(restResult.reverseOrder());

        Graph<Property> graph = null;
        if(filter == ApplyFilter.NO) {
            graph = buildGraph(result, firstRemoved.addExcl(secondRemoved));
        }

        for(Property property : result) {
            property.dropLinks();
            if(property instanceof CalcProperty)
                ((CalcProperty)property).dropActionChangeProps();
        }
        return new Pair<>(result, graph);
    }

    private static Graph<Property> buildGraph(ImOrderSet<Property> props, ImSet<Link> removedLinks) {
        MAddMap<Property, HSet<Link>> linksMap = MapFact.mAddOverrideMap();
        for (int i = 0, size = props.size(); i < size; i++) {
            Property property = props.get(i);
            if (linksMap.get(property) == null) // проверка что не было
                buildOrder(property, linksMap, null, removedLinks, true, props.getSet(), true, false, true);
        }

        MExclMap<Property, ImSet<Property>> mEdgesIn = MapFact.mExclMap(linksMap.size());
        for(int i=0,size=linksMap.size();i<size;i++) {
            final Property property = linksMap.getKey(i);
            HSet<Link> links = linksMap.getValue(i);
            mEdgesIn.exclAdd(property, links.mapSetValues(new GetValue<Property, Link>() {
                public Property getMapValue(Link value) {
                    assert BaseUtils.hashEquals(value.to, property);
                    return value.from;
                }
            }));
        }
        return new Graph<>(mEdgesIn.immutable());
    }

    public AggregateProperty getAggregateStoredProperty(String propertyCanonicalName) {
        for (Property property : getStoredProperties()) {
            if (property instanceof AggregateProperty && propertyCanonicalName.equals(property.getCanonicalName()))
                return (AggregateProperty) property;
        }
        return null;
    }

    // используется не в task'ах
    public List<CalcProperty> getAggregateStoredProperties(boolean ignoreCheck) {
        List<CalcProperty> result = new ArrayList<>();
        try (final DataSession dataSession = getDbManager().createSession()) {
            for (Property property : getStoredProperties())
                if (property instanceof AggregateProperty) {
                    boolean recalculate = ignoreCheck || reflectionLM.disableAggregationsTableColumn.read(dataSession, reflectionLM.tableColumnSID.readClasses(dataSession, new DataObject(property.getDBName()))) == null;
                    if(recalculate)
                        result.add((AggregateProperty) property);
                }
        } catch (SQLException | SQLHandledException e) {
            serviceLogger.info(e.getMessage());
        }
        return result;
    }

    public ImOrderSet<CalcProperty> getStoredDataProperties() {
        try (final DataSession dataSession = getDbManager().createSession()) {
            return BaseUtils.immutableCast(getStoredProperties().filterOrder(new SFunctionSet<CalcProperty>() {
                public boolean contains(CalcProperty property) {
                    boolean recalculate = true;
                    try {
                        recalculate = reflectionLM.disableClassesTableColumn.read(dataSession, reflectionLM.tableColumnSID.readClasses(dataSession, new DataObject(property.getDBName()))) == null;
                    } catch (SQLException | SQLHandledException e) {
                        serviceLogger.error(e.getMessage());
                    }
                    return recalculate && (property instanceof StoredDataProperty || property instanceof ClassDataProperty);
                }
            }));
        } catch (SQLException e) {
            serviceLogger.info(e.getMessage());
        }
        return SetFact.EMPTYORDER();
    }

    @IdentityLazy
    public ImOrderSet<CalcProperty> getStoredProperties() {
        return BaseUtils.immutableCast(getPropertyList().filterOrder(new SFunctionSet<Property>() {
            public boolean contains(Property property) {
                return property instanceof CalcProperty && ((CalcProperty) property).isStored();
            }
        }));
    }

    public ImSet<CustomClass> getCustomClasses() {
        return LM.baseClass.getAllClasses();
    }

    public ImSet<ConcreteCustomClass> getConcreteCustomClasses() {
        return BaseUtils.immutableCast(getCustomClasses().filterFn(new SFunctionSet<CustomClass>() {
            public boolean contains(CustomClass property) {
                return property instanceof ConcreteCustomClass;
            }
        }));
    }

    @IdentityLazy
    public ImOrderMap<ActionProperty, SessionEnvEvent> getSessionEvents() {
        ImOrderSet<Property> list = getPropertyList();
        MOrderExclMap<ActionProperty, SessionEnvEvent> mResult = MapFact.mOrderExclMapMax(list.size());
        SessionEnvEvent sessionEnv;
        for (Property property : list)
            if (property instanceof ActionProperty && (sessionEnv = ((ActionProperty) property).getSessionEnv(SystemEvent.SESSION))!=null)
                mResult.exclAdd((ActionProperty) property, sessionEnv);
        return mResult.immutableOrder();
    }

    @IdentityLazy
    public ImSet<CalcProperty> getDataChangeEvents() {
        ImOrderSet<Property> propertyList = getPropertyList();
        MSet<CalcProperty> mResult = SetFact.mSetMax(propertyList.size());
        for (int i=0,size=propertyList.size();i<size;i++) {
            Property property = propertyList.get(i);
            if (property instanceof DataProperty && ((DataProperty) property).event != null)
                mResult.add((((DataProperty) property).event).getWhere());
        }
        return mResult.immutable();
    }

    @IdentityLazy
    public ImOrderMap<ApplyGlobalEvent, SessionEnvEvent> getApplyEvents(ApplyFilter increment) {
        // здесь нужно вернуть список stored или тех кто
        ImOrderSet<Property> list = getPropertyListWithGraph(increment).first;
        MOrderExclMap<ApplyGlobalEvent, SessionEnvEvent> mResult = MapFact.mOrderExclMapMax(list.size());
        for (Property property : list) {
            ApplyGlobalEvent applyEvent = property.getApplyEvent();
            if(applyEvent != null)
                mResult.exclAdd(applyEvent, applyEvent.getSessionEnv());
        }
        return mResult.immutableOrder();
    }

    public ImOrderSet<ApplyGlobalEvent> getApplyEvents(DataSession session) {
        return session.filterOrderEnv(getApplyEvents(session.applyFilter));
    }
    
    private static ImSet<CalcProperty> getSingleApplyDepends(CalcProperty<?> fill, Result<Boolean> canBeOutOfDepends, ApplySingleEvent event) {
        ImSet<CalcProperty> depends = fill.getDepends(false);// вычисляемые события отдельно отрабатываются (собственно обрабатываются как обычные события)

        if (fill instanceof DataProperty) { // отдельно обрабатывается так как в getDepends передается false (так как в локальных событиях удаление это скорее вычисляемое событие, а в глобальных - императивное, приходится делать такой хак)
             assert depends.isEmpty();
             canBeOutOfDepends.set(true); // могут не быть в propertyList
             return ((DataProperty) fill).getSingleApplyDroppedIsClassProps();
        }
        if (fill instanceof IsClassProperty) {
             assert depends.isEmpty();
             canBeOutOfDepends.set(true); // могут не быть в propertyList
             return ((IsClassProperty) fill).getSingleApplyDroppedIsClassProps();
        }
        if (fill instanceof ObjectClassProperty) {
             assert depends.isEmpty();
             canBeOutOfDepends.set(true); // могут не быть в propertyList
             return ((ObjectClassProperty) fill).getSingleApplyDroppedIsClassProps();
        }
        return depends;
    }

    private static void fillSingleApplyDependFrom(CalcProperty<?> prop, ApplySingleEvent applied, SessionEnvEvent appliedSet, MExclMap<ApplyCalcEvent, MOrderMap<ApplySingleEvent, SessionEnvEvent>> mapDepends, boolean canBeOutOfDepends) {
        ApplyCalcEvent applyEvent = prop.getApplyEvent();
        if (applyEvent != null && !applyEvent.equals(applied)) {
            MOrderMap<ApplySingleEvent, SessionEnvEvent> fillDepends = mapDepends.get(applyEvent);
            
            boolean propCanBeOutOfDepends = canBeOutOfDepends;
            if(prop instanceof ChangedProperty && (applied instanceof ApplyStoredEvent)) // applied может идти до DROPPED(класс), но в этом и смысл, так как если он идет до то удаление уже прошло и это удаление "фейковое" (не влияет на этот applied)
                propCanBeOutOfDepends = true;
            
            if(!(propCanBeOutOfDepends && fillDepends==null))
                fillDepends.add(applied, appliedSet);
        } else {
            Result<Boolean> rCanBeOutOfDepends = new Result<>(canBeOutOfDepends);
            for (CalcProperty depend : getSingleApplyDepends(prop, rCanBeOutOfDepends, applied))
                fillSingleApplyDependFrom(depend, applied, appliedSet, mapDepends, rCanBeOutOfDepends.result);
        }
    }

    @IdentityLazy
    private ImMap<ApplyCalcEvent, ImOrderMap<ApplySingleEvent, SessionEnvEvent>> getOrderMapSingleApplyDepends(ApplyFilter increment) {
        assert Settings.get().isEnableApplySingleStored();

        ImOrderMap<ApplyGlobalEvent, SessionEnvEvent> applyEvents = getApplyEvents(increment);

        // нам нужны будут сами persistent свойства + prev'ы у action'ов
        boolean canBeOutOfDepends = increment != ApplyFilter.NO;
        MExclMap<ApplyCalcEvent, MOrderMap<ApplySingleEvent, SessionEnvEvent>> mMapDepends = MapFact.mExclMap();
        MAddMap<OldProperty, SessionEnvEvent> singleAppliedOld = MapFact.mAddMap(SessionEnvEvent.<OldProperty>mergeSessionEnv());
        for(int i = 0, size = applyEvents.size(); i<size; i++) {
            ApplyGlobalEvent applyEvent = applyEvents.getKey(i);
            SessionEnvEvent sessionEnv = applyEvents.getValue(i);
            singleAppliedOld.addAll(applyEvent.getEventOldDepends().toMap(sessionEnv));
            
            if(applyEvent instanceof ApplyCalcEvent) { // сначала классы и stored обрабатываем
                mMapDepends.exclAdd((ApplyCalcEvent) applyEvent, MapFact.mOrderMap(SessionEnvEvent.<ApplySingleEvent>mergeSessionEnv()));
                if(applyEvent instanceof ApplyStoredEvent) { // так как бежим в нужном порядке, то и stored будут заполняться в нужном порядке (так как он соответствует порядку depends)
                    ApplyStoredEvent applyStoredEvent = (ApplyStoredEvent) applyEvent;
                    fillSingleApplyDependFrom(applyStoredEvent.property, applyStoredEvent, sessionEnv, mMapDepends, canBeOutOfDepends);
                }
            }
        }
        for (int i=0,size= singleAppliedOld.size();i<size;i++) { // old'ы по идее не важно в каком порядке будут (главное что stored до)
            OldProperty old = singleAppliedOld.getKey(i);
            fillSingleApplyDependFrom(old.property, new ApplyUpdatePrevEvent(old), singleAppliedOld.getValue(i), mMapDepends, canBeOutOfDepends);
        }

        return mMapDepends.immutable().mapValues(new GetValue<ImOrderMap<ApplySingleEvent, SessionEnvEvent>, MOrderMap<ApplySingleEvent, SessionEnvEvent>>() {
            public ImOrderMap<ApplySingleEvent, SessionEnvEvent> getMapValue(MOrderMap<ApplySingleEvent, SessionEnvEvent> value) {
                return value.immutableOrder();
            }});
    }

    // определяет для stored свойства зависимые от него stored свойства, а также свойства которым необходимо хранить изменения с начала транзакции (constraints и derived'ы)
    public ImOrderSet<ApplySingleEvent> getSingleApplyDependFrom(ApplyCalcEvent event, DataSession session) {
        return session.filterOrderEnv(getOrderMapSingleApplyDepends(session.applyFilter).get(event));
    }

    @IdentityLazy
    public List<CalcProperty> getCheckConstrainedProperties() {
        List<CalcProperty> result = new ArrayList<>();
        for (Property property : getPropertyList()) {
            if (property instanceof CalcProperty && ((CalcProperty) property).checkChange != CalcProperty.CheckType.CHECK_NO) {
                result.add((CalcProperty) property);
            }
        }
        return result;
    }

    public List<CalcProperty> getCheckConstrainedProperties(CalcProperty<?> changingProp) {
        List<CalcProperty> result = new ArrayList<>();
        for (CalcProperty property : getCheckConstrainedProperties()) {
            if (property.checkChange == CalcProperty.CheckType.CHECK_ALL ||
                    property.checkChange == CalcProperty.CheckType.CHECK_SOME && property.checkProperties.contains(changingProp)) {
                result.add(property);
            }
        }
        return result;
    }

    public List<LogicsModule> getLogicModules() {
        return modules.all();
    }

    public void firstRecalculateStats() {
        try(DataSession session = getDbManager().createSession()) {
            if(reflectionLM.hasNotNullQuantity.read(session) == null) {
                recalculateStats(session);
                session.apply(this, ThreadLocalContext.getStack());
            }
        } catch (SQLException | SQLHandledException e) {
            ServerLoggers.serviceLogger.error("FirstRecalculateStats Error: ", e);
        }
    }

    public void recalculateStats(DataSession session) throws SQLException, SQLHandledException {
        int count = 0;
        ImSet<ImplementTable> tables = LM.tableFactory.getImplementTables(getDbManager().getDisableStatsTableSet());
        for (ImplementTable dataTable : tables) {
            count++;
            long start = System.currentTimeMillis();
            serviceLogger.info(String.format("Recalculate Stats %s of %s: %s", count, tables.size(), String.valueOf(dataTable)));
            dataTable.recalculateStat(this.reflectionLM, getDbManager().getDisableStatsTableColumnSet(), session);
            long time = System.currentTimeMillis() - start;
            serviceLogger.info(String.format("Recalculate Stats: %s, %sms", String.valueOf(dataTable), time));
        }
        recalculateClassStats(session, true);
    }

    public void overCalculateStats(DataSession session, Integer maxQuantityOverCalculate) throws SQLException, SQLHandledException {
        int count = 0;
        MSet<Long> propertiesSet = getOverCalculatePropertiesSet(session, maxQuantityOverCalculate);
        ImSet<ImplementTable> tables = LM.tableFactory.getImplementTables(getDbManager().getDisableStatsTableSet());
        for (ImplementTable dataTable : tables) {
            count++;
            long start = System.currentTimeMillis();
            if(dataTable.overCalculateStat(this.reflectionLM, session, propertiesSet, getDbManager().getDisableStatsTableColumnSet(),
                    new ProgressBar("Recalculate Stats", count, tables.size(), String.format("Table: %s (%s of %s)", dataTable, count, tables.size())))) {
                long time = System.currentTimeMillis() - start;
                serviceLogger.info(String.format("Recalculate Stats: %s, %sms", String.valueOf(dataTable), time));
            }
        }
    }

    public MSet<Long> getOverCalculatePropertiesSet(DataSession session, Integer maxQuantity) throws SQLException, SQLHandledException {
        KeyExpr propertyExpr = new KeyExpr("Property");
        ImRevMap<Object, KeyExpr> propertyKeys = MapFact.singletonRev((Object) "Property", propertyExpr);

        QueryBuilder<Object, Object> propertyQuery = new QueryBuilder<>(propertyKeys);
        propertyQuery.and(reflectionLM.canonicalNameProperty.getExpr(propertyExpr).getWhere());
        if(maxQuantity == null)
            propertyQuery.and(reflectionLM.notNullQuantityProperty.getExpr(propertyExpr).getWhere().not()); //null quantityProperty
        else
            propertyQuery.and(reflectionLM.notNullQuantityProperty.getExpr(propertyExpr).getWhere().not().or( //null quantityProperty
                    reflectionLM.notNullQuantityProperty.getExpr(propertyExpr).compare(new DataObject(maxQuantity).getExpr(), Compare.LESS_EQUALS))); //less or equals then maxQuantity

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> propertyResult = propertyQuery.execute(session);

        MSet<Long> resultSet = SetFact.mSet();
        for (int i = 0, size = propertyResult.size(); i < size; i++) {
            resultSet.add((Long) propertyResult.getKey(i).get("Property"));
        }
        return resultSet;
    }

    public void recalculateClassStats(DataSession session, boolean log) throws SQLException, SQLHandledException {
        for (ObjectValueClassSet tableClasses : LM.baseClass.getUpObjectClassFields().valueIt()) {
            recalculateClassStat(tableClasses, session, log);
        }
    }

    public ImMap<Long, Integer> recalculateClassStat(ObjectValueClassSet tableClasses, DataSession session, boolean log) throws SQLException, SQLHandledException {
        long start = System.currentTimeMillis();
        if(log)
            serviceLogger.info(String.format("Recalculate Class Stats: %s", String.valueOf(tableClasses)));
        QueryBuilder<Integer, Integer> classes = new QueryBuilder<>(SetFact.singleton(0));

        KeyExpr countKeyExpr = new KeyExpr("count");
        Expr countExpr = GroupExpr.create(MapFact.singleton(0, countKeyExpr.classExpr(LM.baseClass)),
                ValueExpr.COUNT, countKeyExpr.isClass(tableClasses), GroupType.SUM, classes.getMapExprs());

        classes.addProperty(0, countExpr);
        classes.and(countExpr.getWhere());

        ImOrderMap<ImMap<Integer, Object>, ImMap<Integer, Object>> classStats = classes.execute(session);
        ImSet<ConcreteCustomClass> concreteChilds = tableClasses.getSetConcreteChildren();
        MExclMap<Long, Integer> mResult = MapFact.mExclMap(concreteChilds.size());
        for (int i = 0, size = concreteChilds.size(); i < size; i++) {
            ConcreteCustomClass customClass = concreteChilds.get(i);
            ImMap<Integer, Object> classStat = classStats.get(MapFact.singleton(0, (Object) customClass.ID));
            int statValue = classStat == null ? 1 : (Integer) classStat.singleValue();
            mResult.exclAdd(customClass.ID, statValue);
            LM.statCustomObjectClass.change(statValue, session, customClass.getClassObject());
        }
        long time = System.currentTimeMillis() - start;
        if(log)
            serviceLogger.info(String.format("Recalculate Class Stats: %s, %sms", String.valueOf(tableClasses), time));
        return mResult.immutable();
    }

    public String recalculateFollows(SessionCreator creator, boolean isolatedTransaction, final ExecutionStack stack) throws SQLException, SQLHandledException {
        final List<String> messageList = new ArrayList<>();
        final long maxRecalculateTime = Settings.get().getMaxRecalculateTime();
        for (Property property : getPropertyList())
            if (property instanceof ActionProperty) {
                final ActionProperty<?> action = (ActionProperty) property;
                if (action.hasResolve()) {
                    long start = System.currentTimeMillis();
                    try {
                        DBManager.runData(creator, isolatedTransaction, new DBManager.RunServiceData() {
                            public void run(SessionCreator session) throws SQLException, SQLHandledException {
                                ((DataSession) session).resolve(action, stack);
                            }
                        });
                    } catch (LogMessageLogicsException e) { // suppress'им так как понятная ошибка
                        serviceLogger.info(e.getMessage());
                    }
                    long time = System.currentTimeMillis() - start;
                    String message = String.format("Recalculate Follows: %s, %sms", property.getSID(), time);
                    serviceLogger.info(message);
                    if (time > maxRecalculateTime)
                        messageList.add(message);
                }
            }
        return formatMessageList(messageList);
    }
    
    protected String formatMessageList(List<String> messageList) {
        if(messageList.isEmpty())
            return null;
        else {
            String result = "";
            for (String message : messageList)
                result += message + '\n';
            return result;
        }
    }

    public String checkClasses(SQLSession session) throws SQLException, SQLHandledException {
        String message = DataSession.checkClasses(session, LM.baseClass);
        for(ImplementTable implementTable : LM.tableFactory.getImplementTables(getDbManager().getDisableClassesTableSet())) {
            message += DataSession.checkTableClasses(implementTable, session, LM.baseClass, false); // так как снизу есть проверка классов
        }
        for (CalcProperty property : getStoredDataProperties())
            message += DataSession.checkClasses(property, session, LM.baseClass);
        return message;
    }

    public void checkIndices(SQLSession session) throws SQLException, SQLHandledException {
        try {
            for (Map.Entry<NamedTable, Map<List<Field>, Boolean>> mapIndex : getDbManager().getIndicesMap().entrySet()) {
                session.startTransaction(DBManager.START_TIL, OperationOwner.unknown);
                NamedTable table = mapIndex.getKey();
                for (Map.Entry<List<Field>, Boolean> index : mapIndex.getValue().entrySet()) {
                    ImOrderSet<Field> fields = SetFact.fromJavaOrderSet(index.getKey());
                    if (!getDbManager().getThreadLocalSql().checkIndex(table, table.keys, fields, index.getValue()))
                        session.addIndex(table, table.keys, fields, index.getValue(), sqlLogger);
                }
                session.addConstraint(table);
                session.checkExtraIndices(getDbManager().getThreadLocalSql(), table, table.keys, sqlLogger);
                session.commitTransaction();
            }
        } catch (Exception e) {
            session.rollbackTransaction();
            throw e;
        }
    }

    public void recalculateExclusiveness(final SQLSession session, boolean isolatedTransactions) throws SQLException, SQLHandledException {
        DBManager.run(session, isolatedTransactions, new DBManager.RunService() {
            public void run(final SQLSession sql) throws SQLException, SQLHandledException {
                DataSession.runExclusiveness(new DataSession.RunExclusiveness() {
                    public void run(Query<String, String> query) throws SQLException, SQLHandledException {
                        SingleKeyTableUsage<String> table = new SingleKeyTableUsage<>("recexcl", ObjectType.instance, SetFact.toOrderExclSet("sum", "agg"), new Type.Getter<String>() {
                            public Type getType(String key) {
                                return key.equals("sum") ? ValueExpr.COUNTCLASS : StringClass.getv(false, ExtInt.UNLIMITED);
                            }
                        });

                        table.writeRows(sql, query, LM.baseClass, DataSession.emptyEnv(OperationOwner.unknown), SessionTable.nonead);
                        
                        MExclMap<ConcreteCustomClass, MExclSet<String>> mRemoveClasses = MapFact.mExclMap();
                        for(Object distinct : table.readDistinct("agg", sql, OperationOwner.unknown)) { // разновидности agg читаем
                            String classes = (String)distinct;
                            ConcreteCustomClass keepClass = null;
                            for(String singleClass : classes.split(",")) {
                                ConcreteCustomClass customClass = LM.baseClass.findConcreteClassID(Long.parseLong(singleClass));
                                if(customClass != null) {
                                    if(keepClass == null)
                                        keepClass = customClass;
                                    else {
                                        ConcreteCustomClass removeClass;
                                        if(keepClass.isChild(customClass)) {
                                            removeClass = keepClass;
                                            keepClass = customClass;
                                        } else
                                            removeClass = customClass;
                                        
                                        MExclSet<String> mRemoveStrings = mRemoveClasses.get(removeClass);
                                        if(mRemoveStrings == null) {
                                            mRemoveStrings = SetFact.mExclSet();
                                            mRemoveClasses.exclAdd(removeClass, mRemoveStrings);
                                        }
                                        mRemoveStrings.exclAdd(classes);
                                    }
                                }
                            }
                        }
                        ImMap<ConcreteCustomClass, ImSet<String>> removeClasses = MapFact.immutable(mRemoveClasses);

                        for(int i=0,size=removeClasses.size();i<size;i++) {
                            KeyExpr key = new KeyExpr("key");
                            Expr aggExpr = table.join(key).getExpr("agg");
                            Where where = Where.FALSE;
                            for(String removeString : removeClasses.getValue(i))
                                where = where.or(aggExpr.compare(new DataObject(removeString, StringClass.text), Compare.EQUALS));
                            removeClasses.getKey(i).dataProperty.dropInconsistentClasses(session, LM.baseClass, key, where, OperationOwner.unknown);
                        }                            
                    }
                }, sql, LM.baseClass);
            }});
    }

    public String recalculateClasses(SQLSession session, boolean isolatedTransactions) throws SQLException, SQLHandledException {
        recalculateExclusiveness(session, isolatedTransactions);

        final List<String> messageList = new ArrayList<>();
        final long maxRecalculateTime = Settings.get().getMaxRecalculateTime();
        for (final ImplementTable implementTable : LM.tableFactory.getImplementTables(getDbManager().getDisableClassesTableSet())) {
            DBManager.run(session, isolatedTransactions, new DBManager.RunService() {
                public void run(SQLSession sql) throws SQLException, SQLHandledException {
                    long start = System.currentTimeMillis();
                    DataSession.recalculateTableClasses(implementTable, sql, LM.baseClass);
                    long time = System.currentTimeMillis() - start;
                    String message = String.format("Recalculate Table Classes: %s, %sms", implementTable.toString(), time);
                    serviceLogger.info(message);
                    if (time > maxRecalculateTime)
                        messageList.add(message);
                }
            });
        }

        for (final CalcProperty property : getStoredDataProperties())
            DBManager.run(session, isolatedTransactions, new DBManager.RunService() {
                public void run(SQLSession sql) throws SQLException, SQLHandledException {
                    long start = System.currentTimeMillis();
                    property.recalculateClasses(sql, LM.baseClass);
                    long time = System.currentTimeMillis() - start;
                    String message = String.format("Recalculate Class: %s, %sms", property.getSID(), time);
                    serviceLogger.info(message);
                    if (time > maxRecalculateTime)
                        messageList.add(message);
                }
            });
        return formatMessageList(messageList);
    }

    public LP findSafeProperty(String canonicalName) {
        LCP lp = null;
        try {
            lp = findProperty(canonicalName);
        } catch (Exception e) {
        }
        return lp;
    }

    public LP<?,?> findPropertyElseAction(String canonicalName) {
        LP<?,?> property = findProperty(canonicalName);
        if(property == null)
            property = findAction(canonicalName);
        return property;
    }
    
    public LCP<?> findProperty(String canonicalName) {
        return BusinessLogicsResolvingUtils.findPropertyByCanonicalName(this, canonicalName, new ModuleEqualLCPFinder(false));
    }
    
    public LAP<?> findAction(String canonicalName) {
        return BusinessLogicsResolvingUtils.findPropertyByCanonicalName(this, canonicalName, new ModuleEqualLAPFinder());
    }
    
    public LAP<?> findActionByCompoundName(String compoundName) {
        return BusinessLogicsResolvingUtils.findLPByCompoundName(this, compoundName, new ModuleLAPFinder());
    }
    
    public LCP<?> findPropertyByCompoundName(String compoundName) {
        return BusinessLogicsResolvingUtils.findLPByCompoundName(this, compoundName, new ModuleLCPFinder());
    }

    public CustomClass findClassByCompoundName(String compoundName) {
        return findElementByCompoundName(this, compoundName, null, new ModuleClassFinder());
    }

    public CustomClass findClass(String canonicalName) {
        return findElementByCanonicalName(this, canonicalName, null, new ModuleClassFinder());
    }

    public AbstractGroup findGroup(String canonicalName) {
        return findElementByCanonicalName(this, canonicalName, null, new ModuleGroupFinder());
    }

    public ImplementTable findTable(String canonicalName) {
        return findElementByCanonicalName(this, canonicalName, null, new ModuleTableFinder());
    }

    public AbstractWindow findWindow(String canonicalName) {
        return findElementByCanonicalName(this, canonicalName, null, new ModuleWindowFinder());
    }

    public NavigatorElement findNavigatorElement(String canonicalName) {
        return findElementByCanonicalName(this, canonicalName, null, new ModuleNavigatorElementFinder());
    }

    public FormEntity findForm(String canonicalName) {
        return findElementByCanonicalName(this, canonicalName, null, new ModuleFormFinder());
    }

    public MetaCodeFragment findMetaCodeFragment(String canonicalName, int paramCnt) {
        return findElementByCanonicalName(this, canonicalName, paramCnt, new ModuleMetaCodeFragmentFinder());
    }

    public Collection<String> getNamespacesList() {
        return namespaceToModules.keySet();
    }
    
    public List<LogicsModule> getNamespaceModules(String namespace) {
        if (namespaceToModules.containsKey(namespace)) {
            return namespaceToModules.get(namespace);
        } else {
            return Collections.emptyList();
        }
    }
    
    private void outputPersistent() {
        String result = "";

        result += ThreadLocalContext.localize("\n{logics.info.by.tables}\n\n");
        ImOrderSet<CalcProperty> storedProperties = getStoredProperties();
        for (Map.Entry<ImplementTable, Collection<CalcProperty>> groupTable : BaseUtils.group(new BaseUtils.Group<ImplementTable, CalcProperty>() {
            public ImplementTable group(CalcProperty key) {
                return key.mapTable.table;
            }
        }, storedProperties).entrySet()) {
            result += groupTable.getKey().outputKeys() + '\n';
            for (CalcProperty property : groupTable.getValue())
                result += '\t' + property.outputStored(false) + '\n';
        }
        result += ThreadLocalContext.localize("\n{logics.info.by.properties}\n\n");
        for (CalcProperty property : storedProperties)
            result += property.outputStored(true) + '\n';
        System.out.println(result);
    }

    public ImSet<FormEntity> getFormEntities(){
        MExclSet<FormEntity> mResult = SetFact.mExclSet();
        for(LogicsModule logicsModule : modules.all()) {
            for(FormEntity entry : logicsModule.getNamedForms())
                mResult.exclAdd(entry);
        }
        return mResult.immutable();
    }

    public ImSet<NavigatorElement> getNavigatorElements() {
        MExclSet<NavigatorElement> mResult = SetFact.mExclSet();
        for(LogicsModule logicsModule : modules.all()) {
            for(NavigatorElement entry : logicsModule.getNavigatorElements())
                mResult.exclAdd(entry);            
        }
        return mResult.immutable();
    }

    // в том числе и приватные
    public ImSet<FormEntity> getAllForms() {
        MExclSet<FormEntity> mResult = SetFact.mExclSet();
        for(LogicsModule logicsModule : modules.all()) {
            for(FormEntity entry : logicsModule.getAllModuleForms())
                mResult.exclAdd(entry);
        }
        return mResult.immutable();
    }
    
    // todo [dale]: Может быть можно заменить на поиск по каноническому имени
    public FormEntity getFormEntityBySID(String formSID){
        for (LogicsModule logicsModule : modules.all()) {
            for (FormEntity element : logicsModule.getNamedForms()) {
                if (formSID.equals(element.getSID())) {
                    return element;
                }
            }
        }
        return null;
    }

    public void checkForDuplicateElements() {
        new DuplicateSystemElementsChecker(modules.all()).check();
    }
    
    public DBManager getDbManager() {
        return ThreadLocalContext.getDbManager();
    }
    
    public String getDataBaseName() {
        return getDbManager().getDataBaseName();
    }

    private void updateThreadAllocatedBytesMap() {
        if(!Settings.get().isReadAllocatedBytes())
            return;

        final long excessAllocatedBytes = Settings.get().getExcessThreadAllocatedBytes();
        final long maxAllocatedBytes = Settings.get().getMaxThreadAllocatedBytes();
        final int cacheMissesStatsLimit = Settings.get().getCacheMissesStatsLimit();

        ThreadMXBean tBean = ManagementFactory.getThreadMXBean();
        if (tBean instanceof com.sun.management.ThreadMXBean && ((com.sun.management.ThreadMXBean) tBean).isThreadAllocatedMemorySupported()) {
            long time = System.currentTimeMillis();
            long bytesSum = 0;
            long totalBytesSum = 0;
            SQLSession.updateThreadAllocatedBytesMap();
            Map<Long, Thread> threadMap = ThreadUtils.getThreadMap();

            ConcurrentHashMap<Long, HashMap<CacheType, Long>> hitStats = MapFact.getGlobalConcurrentHashMap(CacheStats.getCacheHitStats());
            ConcurrentHashMap<Long, HashMap<CacheType, Long>> missedStats = MapFact.getGlobalConcurrentHashMap(CacheStats.getCacheMissedStats());
            CacheStats.resetStats();

            long totalHit = 0;
            long totalMissed = 0;
            HashMap<CacheType, Long> totalHitMap = new HashMap<>();
            HashMap<CacheType, Long> totalMissedMap = new HashMap<>();
            long exceededMisses = 0;
            long exceededMissesHits = 0;
            HashMap<CacheType, Long> exceededHitMap = new HashMap<>();
            HashMap<CacheType, Long> exceededMissedMap = new HashMap<>();

            boolean logTotal = false;
            List<AllocatedInfo> infos = new ArrayList<>();
            Set<Long> excessAllocatedBytesSet = new HashSet<>();

            for (Map.Entry<Long, Long> bEntry : SQLSession.threadAllocatedBytesBMap.entrySet()) {
                Long id = bEntry.getKey();
                if (id != null) {
                    Long bBytes = bEntry.getValue();
                    Long aBytes = SQLSession.threadAllocatedBytesAMap.get(bEntry.getKey());

                    Long deltaBytes = bBytes != null && aBytes != null ? (bBytes - aBytes) : 0;
                    totalBytesSum += deltaBytes;

                    long userMissed = 0;
                    long userHit = 0;

                    HashMap<CacheType, Long> userHitMap = hitStats.get(id) != null ? hitStats.get(id) : new HashMap<CacheType, Long>();
                    HashMap<CacheType, Long> userMissedMap = missedStats.get(id) != null ? missedStats.get(id) : new HashMap<CacheType, Long>();
                    for (CacheType cacheType : CacheType.values()) {
                        Long hit = nullToZero(userHitMap.get(cacheType));
                        Long missed = nullToZero(userMissedMap.get(cacheType));
                        userHit += hit;
                        userMissed += missed;
                    }
                    totalHit += userHit;
                    totalMissed += userMissed;
                    sumMap(totalHitMap, userHitMap);
                    sumMap(totalMissedMap, userMissedMap);

                    if (deltaBytes > excessAllocatedBytes) {
                        if (!isSystem(threadMap, id) && ThreadUtils.isActiveJavaProcess(ManagementFactory.getThreadMXBean().getThreadInfo(id, Integer.MAX_VALUE))) {
                            excessAllocatedBytesSet.add(id);
                        }
                    }

                    if (deltaBytes > maxAllocatedBytes || userMissed > cacheMissesStatsLimit) {
                        logTotal = true;

                        bytesSum += deltaBytes;

                        exceededMisses += userMissed;
                        exceededMissesHits += userHit;
                        sumMap(exceededHitMap, userHitMap);
                        sumMap(exceededMissedMap, userMissedMap);

                        Thread thread = threadMap.get(id);
                        LogInfo logInfo = thread == null ? null : ThreadLocalContext.logInfoMap.get(thread);
                        String computer = logInfo == null ? null : logInfo.hostnameComputer;
                        String user = logInfo == null ? null : logInfo.userName;
                        String userRole = logInfo == null ? null : logInfo.userRole;
                        String threadName = "";
                        ThreadInfo threadInfo = thread == null ? null : tBean.getThreadInfo(thread.getId());
                        if (threadInfo != null) {
                            threadName = threadInfo.getThreadName();
                        }

                        infos.add(new AllocatedInfo(user, userRole, computer, threadName, bEntry.getKey(), deltaBytes, userMissed, userHit, userHitMap, userMissedMap));
                    }
                }
            }

            checkExceededAllocatedBytes(threadMap, excessAllocatedBytesSet);

            Collections.sort(infos, new Comparator<AllocatedInfo>() {
                @Override
                public int compare(AllocatedInfo o1, AllocatedInfo o2) {
                    long delta = o1.bytes - o2.bytes;
                    return delta > 0 ? 1 : (delta < 0 ? -1 : 0);
                }
            });
            for (AllocatedInfo info : infos) {
                allocatedBytesLogger.info(info);
            }
            
            if (logTotal) {
                allocatedBytesLogger.info(String.format("Exceeded: sum: %s, \t\t\tmissed-hit: All: %s-%s, %s",
                        humanReadableByteCount(bytesSum), exceededMisses, exceededMissesHits, getStringMap(exceededHitMap, exceededMissedMap)));
                allocatedBytesLogger.info(String.format("Total: sum: %s, elapsed %sms, missed-hit: All: %s-%s, %s",
                        humanReadableByteCount(totalBytesSum), System.currentTimeMillis() - time, totalMissed, totalHit, getStringMap(totalHitMap, totalMissedMap)));
            }
        }
    }

    private boolean isSystem(Map<Long, Thread> threadMap, long id) {
        boolean system = ThreadLocalContext.activeMap.get(threadMap.get(id)) == null || !ThreadLocalContext.activeMap.get(threadMap.get(id));
        if (!system) {
            Thread thread = threadMap.get(id);
            LogInfo logInfo = thread == null ? null : ThreadLocalContext.logInfoMap.get(thread);
            system = logInfo == null || logInfo.allowExcessAllocatedBytes;
        }
        return system;
    }

    private void checkExceededAllocatedBytes(Map<Long, Thread> threadMap, Set<Long> excessAllocatedBytesSet) {

        int accessInterruptCount = Settings.get().getAccessInterruptCount();

        for (Iterator<Map.Entry<Long, Integer>> it = excessAllocatedBytesMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Integer> entry = it.next();
            Long id = entry.getKey();
            if (excessAllocatedBytesSet.contains(id)) {
                Integer count = entry.getValue();
                excessAllocatedBytesSet.remove(id);
                count = (count == null ? 0 : count) + 1;
                excessAllocatedBytesMap.put(id, count);
                allocatedBytesLogger.info(String.format("Process %s allocated too much bytes, %s cycles", id, count));
                if(count >= accessInterruptCount) {
                    logger.info(String.format("Process %s allocated too much bytes for %s cycles, will be interrupted", id, count));
                    try {
                        ThreadUtils.interruptThread(getDbManager(), threadMap.get(id));
                    } catch (SQLException | SQLHandledException e) {
                        logger.info(String.format("Failed to interrupt process %s", id));
                    }
                }
            } else
                it.remove();
        }
        for (Long id : excessAllocatedBytesSet)
            excessAllocatedBytesMap.put(id, 1);
    }

    public static String humanReadableByteCount(long bytes) {
        int unit = 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    private String getStringMap(HashMap<CacheType, Long> hitStats, HashMap<CacheType, Long> missedStats) {
        String result = "";
        for (int i = 0; i < CacheType.values().length; i++) {
            CacheType type = CacheType.values()[i];
            result += type + ": " + nullToZero(missedStats.get(type)) + "-" + nullToZero(hitStats.get(type));
            if (i < CacheType.values().length - 1) {
                result += "; ";
            }
        }
        return result;
    }

    private void sumMap(HashMap<CacheType, Long> target, HashMap<CacheType, Long> source) {
        for (CacheType type : CacheType.values()) {
            target.put(type, nullToZero(target.get(type)) + nullToZero(source.get(type)));
        }
    }

    public List<Scheduler.SchedulerTask> getSystemTasks(Scheduler scheduler) {
        if(SystemProperties.inDevMode) // чтобы не мешать при включенных breakPoint'ах
            return new ArrayList<>();

        List<Scheduler.SchedulerTask> result = new ArrayList<>();
        result.add(getOpenFormCountUpdateTask(scheduler));
        result.add(getUserLastActivityUpdateTask(scheduler));
        result.add(getInitPingInfoUpdateTask(scheduler));
        result.add(getAllocatedBytesUpdateTask(scheduler));
        result.add(getCleanTempTablesTask(scheduler));
        result.add(getFlushPendingTransactionCleanersTask(scheduler));
        result.add(getRestartConnectionsTask(scheduler));
        result.add(getUpdateSavePointsInfoTask(scheduler));
        result.addAll(resetCustomReportsCacheTasks(scheduler));
        result.add(getProcessDumpTask(scheduler));
        return result;
    }

    private Scheduler.SchedulerTask getOpenFormCountUpdateTask(Scheduler scheduler) {
        return scheduler.createSystemTask(new EExecutionStackRunnable() {
            public void run(ExecutionStack stack) throws Exception {
                RemoteNavigator.updateOpenFormCount(BusinessLogics.this, stack);
            }
        }, false, Settings.get().getUpdateFormCountPeriod(), false, "Open Form Count");
    }

    private Scheduler.SchedulerTask getUserLastActivityUpdateTask(Scheduler scheduler) {
        return scheduler.createSystemTask(new EExecutionStackRunnable() {
            public void run(ExecutionStack stack) throws Exception {
                RemoteNavigator.updateUserLastActivity(BusinessLogics.this, stack);
            }
        }, false, Settings.get().getUpdateUserLastActivity(), false, "User Last Activity");
    }

    private Scheduler.SchedulerTask getInitPingInfoUpdateTask(Scheduler scheduler) {
        return scheduler.createSystemTask(new EExecutionStackRunnable() {
            public void run(ExecutionStack stack) throws Exception {
                RemoteNavigator.updatePingInfo(BusinessLogics.this, stack);
            }
        }, false, Settings.get().getUpdatePingInfo(), false, "Ping Info");
    }

    private Scheduler.SchedulerTask getCleanTempTablesTask(Scheduler scheduler) {
        return scheduler.createSystemTask(new EExecutionStackRunnable() {
            public void run(ExecutionStack stack) throws Exception {
                SQLSession.cleanTemporaryTables();
            }
        }, false, Settings.get().getTempTablesTimeThreshold(), false, "Drop Temp Tables");
    }

    private Scheduler.SchedulerTask getFlushPendingTransactionCleanersTask(Scheduler scheduler) {
        return scheduler.createSystemTask(new EExecutionStackRunnable() {
            public void run(ExecutionStack stack) throws Exception {
                DataSession.flushPendingTransactionCleaners();
            }
        }, false, Settings.get().getFlushPendingTransactionCleanersThreshold(), false, "Flush Pending Transaction Cleaners");
    }
    
    private Scheduler.SchedulerTask getRestartConnectionsTask(Scheduler scheduler) {
        final Result<Double> prevStart = new Result<>(0.0);
        return scheduler.createSystemTask(new EExecutionStackRunnable() {
            public void run(ExecutionStack stack) throws Exception {
                SQLSession.restartConnections(prevStart);
            }
        }, false, Settings.get().getPeriodRestartConnections(), false, "Connection restart");
    }

    private Scheduler.SchedulerTask getUpdateSavePointsInfoTask(Scheduler scheduler) {
        final Result<Long> prevResult = new Result<>(null);
        return scheduler.createSystemTask(new EExecutionStackRunnable() {
            public void run(ExecutionStack stack) throws Exception {
                getDbManager().getAdapter().updateSavePointsInfo(prevResult);
            }
        }, false, Settings.get().getUpdateSavePointsPeriod(), false, "Update save points thresholds");
    }


    private Scheduler.SchedulerTask getProcessDumpTask(Scheduler scheduler) {
        return scheduler.createSystemTask(new EExecutionStackRunnable() {
            public void run(ExecutionStack stack) throws Exception {
                try(DataSession session = getDbManager().createSession()) {
                    serviceLM.makeProcessDumpAction.execute(session, stack);
                }
            }
        }, false, Settings.get().getPeriodProcessDump(), false, "Process Dump");
    }

    private Scheduler.SchedulerTask getAllocatedBytesUpdateTask(Scheduler scheduler) {
        return scheduler.createSystemTask(new EExecutionStackRunnable() {
            public void run(ExecutionStack stack) throws Exception {
                updateThreadAllocatedBytesMap();
            }
        }, false, Settings.get().getThreadAllocatedMemoryPeriod() / 2, false, "Allocated Bytes");
    }

    private List<Scheduler.SchedulerTask> resetCustomReportsCacheTasks(Scheduler scheduler) {
        List<Scheduler.SchedulerTask> tasks = new ArrayList<>();
        for (String element : ResourceUtils.getClassPathElements()) {
            if (!isRedundantString(element)) {
                final Path path = Paths.get(BaseUtils.removeTrailingSlash(element + "/" + FormReportManager.reportsDir));
//                logger.info("Reset reports cache: processing path : " + path);
                if (Files.isDirectory(path)) {
//                    logger.info("Reset reports cache: path is directory: " + path);
                    tasks.add(scheduler.createSystemTask(new EExecutionStackRunnable() {
                        @Override
                        public void run(ExecutionStack stack) throws Exception {
                            logger.info("Reset reports cache: run scheduler task for " + path);
                            ResourceUtils.watchPathForChange(path, new Runnable() {
                                @Override
                                public void run() {
                                    logger.info("Reset reports cache: directory changed: " + path + " - reset cache");
                                    customReports = null;
                                }
                            }, Pattern.compile(".*\\.jrxml"));
                        }
                    }, true, null, false, "Custom Reports"));
                }
            }
        }
        return tasks;
    }

    private class AllocatedInfo {
        private final String user;
        private final String userRole;
        private final String computer;
        private final String threadName;
        private final Long pid;
        private final Long bytes;
        private final long userMissed;
        private final long userHit;
        private final HashMap<CacheType, Long> userHitMap;
        private final HashMap<CacheType, Long> userMissedMap;

        AllocatedInfo(String user, String userRole, String computer, String threadName, Long pid, Long bytes, long userMissed, long userHit, HashMap<CacheType, Long> userHitMap, HashMap<CacheType, Long> userMissedMap) {
            this.user = user;
            this.userRole = userRole;
            this.computer = computer;
            this.threadName = threadName;
            this.pid = pid;
            this.bytes = bytes;
            this.userMissed = userMissed;
            this.userHit = userHit;
            this.userHitMap = userHitMap;
            this.userMissedMap = userMissedMap;
        }

        @Override
        public String toString() {
            String userMessage = String.format("PID %s: %s, Thread %s", pid, humanReadableByteCount(bytes), threadName);
            if (user != null) {
                userMessage += String.format(", Comp. %s, User %s, Role %s", computer == null ? "unknown" : computer, user, userRole);
            }
            userMessage += String.format(", missed-hit: All: %s-%s, %s", userMissed, userHit, getStringMap(userHitMap, userMissedMap));

            return userMessage;
        }
    }
}
