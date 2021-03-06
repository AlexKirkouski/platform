package lsfusion.server.context;

import com.google.common.base.Throwables;
import lsfusion.base.ConcurrentWeakHashMap;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.interop.ModalityType;
import lsfusion.interop.action.ClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.Settings;
import lsfusion.server.WrapperContext;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.DataClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.ManageSessionType;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.form.entity.filter.FilterEntity;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.form.instance.listener.CustomClassListener;
import lsfusion.server.form.navigator.LogInfo;
import lsfusion.server.lifecycle.EventServer;
import lsfusion.server.lifecycle.MonitorServer;
import lsfusion.server.logics.*;
import lsfusion.server.logics.SecurityManager;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.DialogRequest;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.property.PullChangeProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.remote.ContextAwarePendingRemoteObject;
import lsfusion.server.remote.RemoteLoggerAspect;
import lsfusion.server.remote.RmiServer;
import lsfusion.server.session.DataSession;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.log4j.MDC;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public class ThreadLocalContext {
    private static final ThreadLocal<Context> context = new ThreadLocal<>();
    private static final ThreadLocal<Settings> settings = new ThreadLocal<>();
    private static final ThreadLocal<Stack<Settings>> prevSettings = new ThreadLocal<>();
    private static ConcurrentHashMap<Long, Settings> roleSettingsMap = new ConcurrentHashMap<>();
    public static ConcurrentWeakHashMap<Thread, LogInfo> logInfoMap = new ConcurrentWeakHashMap<>();
    public static ConcurrentWeakHashMap<Thread, Boolean> activeMap = new ConcurrentWeakHashMap<>();
    public static Context get() { // временно, потом надо private сделать
        return context.get();
    }
    public static void assureContext(Context context) { // временно, должно уйти
        assure(context, null);
    }

    private static void set(Context c) {
        context.set(c);
    }

    private static void setLogInfo(Context c) {
        if (c != null) {
            LogInfo logInfo = c.getLogInfo();
            if (logInfo != null) {
                if (logInfo.userName != null)
                    MDC.put("client", logInfo.userName);
                if (logInfo.hostnameComputer != null)
                    MDC.put("computer", logInfo.hostnameComputer);
                if (logInfo.remoteAddress != null)
                    MDC.put("remoteAddress", logInfo.remoteAddress);
                logInfoMap.put(Thread.currentThread(), logInfo);
                activeMap.put(Thread.currentThread(), true);
            }
        } else
            activeMap.put(Thread.currentThread(), false);
    }

    public static void setSettings() {
        try {
            settings.set(getRoleSettings(getCurrentRole()));
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static void dropSettings() {
        settings.set(null);
    }

    public static void pushSettings(String nameProperty, String valueProperty) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException, CloneNotSupportedException {
        Settings overrideSettings = settings.get().cloneSettings();
        setPropertyValue(overrideSettings, nameProperty, valueProperty);
        if(prevSettings.get() == null)
            prevSettings.set(new Stack());
        prevSettings.get().push(settings.get());
        settings.set(overrideSettings);
    }

    public static void popSettings(String nameProperty) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        //на данный момент не важно, какое свойство передано в pop, выбирается верхнее по стеку и нет никакой проверки
        settings.set(prevSettings.get().pop());
    }

    public static void setPropertyValue(Settings settings, String nameProperty, String valueProperty) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Class type = PropertyUtils.getPropertyType(settings, nameProperty);
        if(type == Boolean.TYPE)
            BeanUtils.setProperty(settings, nameProperty, valueProperty.equals("true"));
        else if(type == Integer.TYPE)
            BeanUtils.setProperty(settings, nameProperty, Integer.valueOf(valueProperty));
        else if(type == Double.TYPE)
            BeanUtils.setProperty(settings, nameProperty, Double.valueOf(valueProperty));
        else if(type == Long.TYPE)
            BeanUtils.setProperty(settings, nameProperty, Long.valueOf(valueProperty));
        else
            BeanUtils.setProperty(settings, nameProperty, trimToEmpty(valueProperty));
    }

    public static Long getCurrentUser() {
        return get().getCurrentUser();
    }

    public static Long getCurrentRole() {
        return get().getCurrentUserRole();
    }

    public static LogicsInstance getLogicsInstance() {
        return get().getLogicsInstance();
    }

    public static CustomClassListener getClassListener() {
        return get().getClassListener();
    }

    public static BusinessLogics getBusinessLogics() {
        return getLogicsInstance().getBusinessLogics();
    }

    public static NavigatorsManager getNavigatorsManager() {
        return getLogicsInstance().getNavigatorsManager();
    }

    public static RestartManager getRestartManager() {
        return getLogicsInstance().getRestartManager();
    }

    public static SecurityManager getSecurityManager() {
        return getLogicsInstance().getSecurityManager();
    }

    public static DBManager getDbManager() {
        return getLogicsInstance().getDbManager();
    }

    public static RMIManager getRmiManager() {
        return getLogicsInstance().getRmiManager();
    }

    public static Settings getSettings() {
        return settings.get();
    }

    public static Settings getRoleSettings(Long role) throws CloneNotSupportedException, IllegalAccessException, NoSuchMethodException, InvocationTargetException, SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        if (role == null) //системный процесс или пользователь без роли
            return getLogicsInstance().getSettings();
        else {
            Settings roleSettings = roleSettingsMap.get(role);
            if (roleSettings == null) {

                //клонируем settings для новой роли
                roleSettings = getLogicsInstance().getSettings().cloneSettings();

                roleSettingsMap.put(role, roleSettings);
            }
            return roleSettings;
        }
    }

    public static FormInstance getFormInstance() {
        return get().getFormInstance();
    }

    public static FormInstance createFormInstance(FormEntity formEntity, ImMap<ObjectEntity, ? extends ObjectValue> mapObjects, ExecutionStack stack, DataSession session, boolean isModal, Boolean noCancel, ManageSessionType manageSession, boolean checkOnOk, boolean showDrop, boolean interactive, ImSet<FilterEntity> contextFilters, ImSet<PullChangeProperty> pullProps, boolean readonly) throws SQLException, SQLHandledException {
        return get().createFormInstance(formEntity, mapObjects, session, isModal, noCancel, manageSession, stack, checkOnOk, showDrop, interactive, contextFilters, pullProps, readonly);
    }

    public static ObjectValue requestUserObject(DialogRequest dialogRequest, ExecutionStack stack) throws SQLException, SQLHandledException {
        return get().requestUserObject(dialogRequest, stack);
    }

    public static ObjectValue requestUserData(DataClass dataClass, Object oldValue) {
        return get().requestUserData(dataClass, oldValue);
    }

    public static DataObject getConnection() {
        return get().getConnection();
    }

    public static ObjectValue requestUserClass(CustomClass baseClass, CustomClass defaultValue, boolean concrete) {
        return get().requestUserClass(baseClass, defaultValue, concrete);
    }

    public static void pushLogMessage() {
        get().pushLogMessage();
    }
    public static String popLogMessage() {
        return get().popLogMessage();
    }

    public static void delayUserInteraction(ClientAction action) {
        get().delayUserInteraction(action);
    }

    public static Object requestUserInteraction(ClientAction action) {
        return get().requestUserInteraction(action);
    }

    public static void requestFormUserInteraction(FormInstance remoteForm, ModalityType modalityType, boolean forbidDuplicate, ExecutionStack stack) throws SQLException, SQLHandledException {
        get().requestFormUserInteraction(remoteForm, modalityType, forbidDuplicate, stack);
    }

    public static boolean canBeProcessed() {
        return get().canBeProcessed();
    }

    public static Object[] requestUserInteraction(ClientAction... actions) {
        return get().requestUserInteraction(actions);
    }

    // есть пока всего одна ветка с assertTop (кроме wrapContext) - rmicontextobject, да и то не до конца понятно в каких стеках

    private static final ThreadLocal<NewThreadExecutionStack> stack = new ThreadLocal<>();
    private static void assure(Context context, NewThreadExecutionStack stack) {
        // nullable stack временно в assure
        Context prevContext = ThreadLocalContext.get();
        if(prevContext == null || !prevContext.equals(context)) {
            ServerLoggers.assertLog(false, "DIFFERENT CONTEXT, PREV : " + prevContext + ", SET : " + context);
            ThreadLocalContext.set(context);
            ThreadLocalContext.setLogInfo(context);
        }

        if(stack != null) {
            NewThreadExecutionStack prevStack = ThreadLocalContext.getStack();
            if(prevStack == null || !prevStack.checkStack(stack)) {
                ServerLoggers.assertLog(false, "DIFFERENT STACK, PREV : " + prevStack + ", SET : " + stack);
                ThreadLocalContext.stack.set(stack);
            }
        }
    }
    public static NewThreadExecutionStack getStack() {
        return stack.get();
    }
    
    private static void checkThread(Context prevContext, boolean assertTop, ThreadInfo threadInfo) {
        ServerLoggers.assertLog(!(assertTop && prevContext != null), "ASSERT TOP EXECUTION");
        if(assertTop && (Thread.currentThread() instanceof ExecutorFactory.ClosableDaemonThreadFactory.ClosableThread) != (threadInfo instanceof ExecutorFactoryThreadInfo))
            ServerLoggers.assertLog(false, "CLOSABLE THREAD != EXECUTOR FACTORY THREAD");
    }
    
    private static Context aspectBefore(Context context, boolean assertTop, ThreadInfo threadInfo, NewThreadExecutionStack stack, SyncType type) { // type - не null, значит новый поток с заданным типом синхронизации
        Context prevContext = ThreadLocalContext.get();

        ThreadLocalContext.set(context);
        if(prevContext == null) {
            //необходимо выполнить раньше вызова setLogInfo
            ThreadLocalContext.setSettings();
        }
        ThreadLocalContext.setLogInfo(context);
        ThreadLocalContext.stack.set(stack);

        if(prevContext == null) {
            Thread currentThread = Thread.currentThread();
            long pid = currentThread.getId();
            RemoteLoggerAspect.putDateTimeCall(pid, new Timestamp(System.currentTimeMillis()));

            if(threadInfo instanceof EventThreadInfo) { // можно было попытаться старое имя сохранить, но оно по идее может меняться тогда очень странная логика получится
                currentThread.setName(((EventThreadInfo) threadInfo).getEventName() + " - " + currentThread.getId());
            }
        }

        checkThread(prevContext, assertTop, threadInfo);

        return prevContext;
    }
    private static void aspectAfter(Context prevContext, boolean assertTop, ThreadInfo threadInfo) {
        checkThread(prevContext, assertTop, threadInfo);

        if(prevContext == null) {
            long pid = Thread.currentThread().getId();
            RemoteLoggerAspect.removeDateTimeCall(pid);

            if(threadInfo instanceof EventThreadInfo) {
                // тут можно было бы сбросить имя потока, но пока сохраним (также как и в SQLSession)                
                if (Settings.get().isEnableCloseThreadLocalSqlInNativeThreads()) // закрываем connection, чтобы не мусорить
                    ThreadLocalContext.getLogicsInstance().getDbManager().closeThreadLocalSql();
            }
            ThreadLocalContext.dropSettings();
        }


        ThreadLocalContext.set(prevContext);
        ThreadLocalContext.setLogInfo(prevContext);
    }

    private static void assureEvent(Context context, NewThreadExecutionStack stack) {
        assure(context, stack);
    }
    private static Context aspectBeforeEvent(Context context, boolean assertTop, ThreadInfo threadInfo, NewThreadExecutionStack stack, SyncType type) {
        return aspectBefore(context, assertTop, threadInfo, stack, type); // здесь stack не вызова, а "общий" стек, поэтому оборачивать его по сути не надо
    }
    private static void aspectAfterEvent(Context prevContext, boolean assertTop, ThreadInfo threadInfo) {
        aspectAfter(prevContext, assertTop, threadInfo);
    }
    private static void assureEvent(LogicsInstance instance, NewThreadExecutionStack stack) {
        assure(instance.getContext(), stack);
    }
    private static void aspectBeforeEvent(LogicsInstance instance, NewThreadExecutionStack stack, ThreadInfo threadInfo, SyncType mirror) {
        aspectBeforeEvent(instance, stack, threadInfo, true, mirror);
    }
    private static Context aspectBeforeEvent(LogicsInstance instance, NewThreadExecutionStack stack, ThreadInfo threadInfo, boolean assertTop, SyncType mirror) {
        return aspectBeforeEvent(instance.getContext(), assertTop, threadInfo, stack, mirror);
    }
    private static void aspectAfterEvent(ThreadInfo threadInfo) {
        aspectAfterEvent(null, true, threadInfo);
    }
    private static NewThreadExecutionStack eventStack(EventServer eventServer) {
        return eventServer.getTopStack();
    }

    // RMI вызовы
    private static NewThreadExecutionStack rmiStack(ContextAwarePendingRemoteObject context) {
        return context.getRmiStack();
    }
    public static void assureRmi(ContextAwarePendingRemoteObject context) { // не уверен что всегда устанавливается
        assureEvent(context.getContext(), rmiStack(context));
    }
    public static Context aspectBeforeRmi(ContextAwarePendingRemoteObject object, boolean assertTop, ThreadInfo threadInfo) { // rmi + unreferenced и несколько мелочей
        return aspectBeforeRmi(object, assertTop, threadInfo, null);
    }
    public static Context aspectBeforeRmi(ContextAwarePendingRemoteObject object, boolean assertTop, ThreadInfo threadInfo, SyncType mirror) { // rmi + unreferenced и несколько мелочей
        return aspectBeforeEvent(object.getContext(), assertTop, threadInfo, rmiStack(object), mirror);
    }
    public static void aspectAfterRmi(Context prevContext, boolean assertTop, ThreadInfo threadInfo) {
        aspectAfterEvent(prevContext, assertTop, threadInfo);
    }
    public static void aspectAfterRmi(ThreadInfo threadInfo) {
        aspectAfterEvent(threadInfo);
    }
    private static NewThreadExecutionStack rmiStack(RmiServer remoteServer) {
        return eventStack(remoteServer);
    }
    public static void assureRmi(RmiServer remoteServer) { // Андрей уверяет что не всегда устанавливается
        assureEvent(remoteServer.getLogicsInstance(), rmiStack(remoteServer));
    }
    public static Context aspectBeforeRmi(RmiServer remoteServer, boolean assertTop, ThreadInfo threadInfo) {
        return aspectBeforeEvent(remoteServer.getLogicsInstance(), rmiStack(remoteServer), threadInfo, assertTop, null);
    }

    // MONITOR вызовы, когда вызов осуществляется "чужим" потоком (чтение из socket'а, servlet'ом и т.п.)
    private static NewThreadExecutionStack monitorStack(MonitorServer monitor) {
        return eventStack(monitor);
    }
    public static void assureMonitor(MonitorServer monitor) {
        assureEvent(monitor.getLogicsInstance(), monitorStack(monitor));
    }
    public static void aspectBeforeMonitor(MonitorServer monitor, ThreadInfo threadInfo) {
        aspectBeforeMonitor(monitor, threadInfo, null);
    }
    public static void aspectBeforeMonitor(MonitorServer monitor, ThreadInfo threadInfo, SyncType type) {
        aspectBeforeEvent(monitor.getLogicsInstance(), monitorStack(monitor), threadInfo, type);
    }
    public static void aspectAfterMonitor(ThreadInfo threadInfo) {
        aspectAfterEvent(threadInfo);
    }

    public static void aspectBeforeMonitorHTTP(MonitorServer monitor) {
        aspectBeforeMonitor(monitor, EventThreadInfo.HTTP(monitor));
    }
    public static void aspectAfterMonitorHTTP(MonitorServer monitor) {
        aspectAfterMonitor(EventThreadInfo.HTTP(monitor));
    }
    
    // СТАРТ СЕРВЕРА

    private final static TopExecutionStack lifecycleStack = new TopExecutionStack("init");
    public static void assureLifecycle(LogicsInstance logicsInstance) { // nullable
        assureEvent(logicsInstance, lifecycleStack);
    }
    public static void aspectBeforeLifecycle(LogicsInstance context, ThreadInfo threadInfo) {
        aspectBeforeLifecycle(context, threadInfo, null);
    }
    public static void aspectBeforeLifecycle(LogicsInstance context, ThreadInfo threadInfo, SyncType mirror) {
        aspectBeforeEvent(context, lifecycleStack, threadInfo, mirror);
    }
    public static void aspectAfterLifecycle(ThreadInfo threadInfo) {
        aspectAfterEvent(threadInfo);
    }

    // вызов из другого контекста в стеке
    public static Context assureContext(ExecutionContext<PropertyInterface> context) {
        return get();
//        assure(get(), getStack());
    }
    public static void aspectBeforeContext(Context exContext, ExecutionContext<PropertyInterface> context, SyncType type) { // проблема в том, что ExecutionContext не умеет возвращать свой контекст и его приходится передавать в явну.
        aspectBefore(exContext, true, ExecutorFactoryThreadInfo.instance, SyncExecutionStack.newThread(context.stack, "new-thread", type), type);
    }
    public static void aspectAfterContext() {
        aspectAfter(null, true, ExecutorFactoryThreadInfo.instance);
    }

    public static Context wrapContext(WrapperContext context) {
        Context prevContext = get();
        context.setContext(prevContext);
        aspectBefore(context, false, ExecutorFactoryThreadInfo.instance, getStack(), SyncType.SYNC);
        return prevContext;
    }

    public static void unwrapContext(Context prevContext) {
        aspectAfter(prevContext, false, ExecutorFactoryThreadInfo.instance);
    }

    public static String localize(LocalizedString s) {
        return s == null ? null : safeLocalize(s);    
    }
    
    public static String localize(String s) {
        return s == null ? null : safeLocalize(LocalizedString.create(s));
    } 

    public static String localize(LocalizedString s, Locale locale) {
        return s == null ? null : safeLocalize(s, locale);
    }
    
    private static String safeLocalize(LocalizedString s) {
        Context context = get();
        if(context == null) { // так как может вызываться в toString, а он в свою очередь может вызываться в случайных потоках (например см. ContextAwarePendingRemoteObject.toString), поэтому на всякий случай проверяем 
            ServerLoggers.assertLog(false, "NO CONTEXT WHEN LOCALIZED");
            return s.getSourceString();
        }
        return context.localize(s); 
    }
    private static String safeLocalize(LocalizedString s, Locale locale) {
        Context context = get();
        if(context == null) { // так как может вызываться в toString, а он в свою очередь может вызываться в случайных потоках (например см. ContextAwarePendingRemoteObject.toString), поэтому на всякий случай проверяем
            ServerLoggers.assertLog(false, "NO CONTEXT WHEN LOCALIZED");
            return s.getSourceString();
        }
        return context.localize(s, locale);
    }
}
