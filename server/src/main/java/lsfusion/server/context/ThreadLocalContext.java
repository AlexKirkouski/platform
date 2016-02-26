package lsfusion.server.context;

import lsfusion.base.ConcurrentWeakHashMap;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.interop.action.ClientAction;
import lsfusion.server.Settings;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.DataClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.form.entity.PropertyDrawEntity;
import lsfusion.server.form.entity.filter.FilterEntity;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.form.instance.FormSessionScope;
import lsfusion.server.form.navigator.LogInfo;
import lsfusion.server.logics.*;
import lsfusion.server.logics.SecurityManager;
import lsfusion.server.logics.property.DialogRequest;
import lsfusion.server.logics.property.PullChangeProperty;
import lsfusion.server.remote.RemoteForm;
import lsfusion.server.session.DataSession;
import lsfusion.server.session.UpdateCurrentClasses;
import lsfusion.server.stack.ExecutionStackItem;
import lsfusion.server.stack.ProgressStackItem;
import org.apache.log4j.MDC;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

public class ThreadLocalContext {
    private static final ThreadLocal<Context> context = new ThreadLocal<Context>();
    public static ConcurrentWeakHashMap<Thread, LogInfo> logInfoMap = MapFact.getGlobalConcurrentWeakHashMap();
    public static Context get() {
        return context.get();
    }

    public static void set(Context c) {
        context.set(c);
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
            }
        }
    }

    public static LogicsInstance getLogicsInstance() {
        return get().getLogicsInstance();
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
        return getLogicsInstance().getSettings();
    }

    public static FormInstance getFormInstance() {
        return get().getFormInstance();
    }

    public static FormInstance createFormInstance(FormEntity formEntity, ImMap<ObjectEntity, ? extends ObjectValue> mapObjects, UpdateCurrentClasses outerUpdateCurrentClasses, DataSession session, boolean isModal, boolean isAdd, FormSessionScope sessionScope, boolean checkOnOk, boolean showDrop, boolean interactive, ImSet<FilterEntity> contextFilters, PropertyDrawEntity initFilterProperty, ImSet<PullChangeProperty> pullProps) throws SQLException, SQLHandledException {
        return get().createFormInstance(formEntity, mapObjects, session, isModal, isAdd, sessionScope, outerUpdateCurrentClasses, checkOnOk, showDrop, interactive, contextFilters, initFilterProperty, pullProps);
    }

    public static RemoteForm createRemoteForm(FormInstance formInstance) {
        return get().createRemoteForm(formInstance);
    }

    public static ObjectValue requestUserObject(DialogRequest dialogRequest) throws SQLException, SQLHandledException {
        return get().requestUserObject(dialogRequest);
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

    public static String getLogMessage() {
        return get().getLogMessage();
    }

    public static void delayUserInteraction(ClientAction action) {
        get().delayUserInteraction(action);
    }

    public static Object requestUserInteraction(ClientAction action) {
        return get().requestUserInteraction(action);
    }

    public static boolean canBeProcessed() {
        return get().canBeProcessed();
    }

    public static ScheduledExecutorService getExecutorService() {
        return get().getExecutorService();
    }

    public static Object[] requestUserInteraction(ClientAction... actions) {
        return get().requestUserInteraction(actions);
    }

    public static String getActionMessage() {
        return get() != null ? get().getActionMessage() : "";
    }

    public static List<Object> getActionMessageList() {
        return get() != null ? get().getActionMessageList() : new ArrayList<>();
    }

    public static Thread getLastThread() {
        return get() != null ? get().getLastThread() : null;
    }

    public static ProgressStackItem pushProgressMessage(String message, Integer progress, Integer total) {
        ProgressStackItem progressStackItem = new ProgressStackItem(message, progress, total);
        pushActionMessage(progressStackItem);
        return progressStackItem;
    }

    public static void pushActionMessage(ExecutionStackItem stackItem) {
        if (get() != null) {
            get().pushActionMessage(stackItem);
        }
    }

    public static void popActionMessage(ExecutionStackItem stackItem) {
        if(get() != null && stackItem != null)
            get().popActionMessage(stackItem);
    }
}
