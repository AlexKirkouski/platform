package lsfusion.server.remote;

import com.google.common.base.Throwables;
import lsfusion.base.ApiResourceBundle;
import lsfusion.base.BaseUtils;
import lsfusion.base.NavigatorInfo;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.GUIPreferences;
import lsfusion.interop.RemoteLogicsInterface;
import lsfusion.interop.VMOptions;
import lsfusion.interop.action.ReportPath;
import lsfusion.interop.event.IDaemonTask;
import lsfusion.interop.exceptions.RemoteMessageException;
import lsfusion.interop.form.screen.ExternalScreen;
import lsfusion.interop.form.screen.ExternalScreenParameters;
import lsfusion.interop.navigator.RemoteNavigatorInterface;
import lsfusion.server.ServerLoggers;
import lsfusion.server.Settings;
import lsfusion.server.SystemProperties;
import lsfusion.server.auth.User;
import lsfusion.server.classes.*;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.data.JDBCTable;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.data.type.ParseException;
import lsfusion.server.data.type.Type;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.lifecycle.LifecycleEvent;
import lsfusion.server.lifecycle.LifecycleListener;
import lsfusion.server.logics.*;
import lsfusion.server.logics.SecurityManager;
import lsfusion.server.logics.linear.LAP;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassType;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.property.actions.external.ExternalHTTPActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.session.DataSession;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static lsfusion.server.context.ThreadLocalContext.localize;

public class RemoteLogics<T extends BusinessLogics> extends ContextAwarePendingRemoteObject implements RemoteLogicsInterface, InitializingBean, LifecycleListener {
    protected final static Logger logger = ServerLoggers.remoteLogger;

    protected T businessLogics;
    protected BaseLogicsModule baseLM;

    protected NavigatorsManager navigatorsManager;

    protected RestartManager restartManager;
    
    protected lsfusion.server.logics.SecurityManager securityManager;

    protected DBManager dbManager;

    private VMOptions clientVMOptions;

    private String displayName;
    private String logicsLogo;
    private String name;
    
    private String clientHideMenu;

    public void setBusinessLogics(BusinessLogics businessLogics) {
        this.businessLogics = (T) businessLogics;
    }

    public void setLogicsInstance(LogicsInstance logicsInstance) {
        setContext(logicsInstance.getContext());
    }

    public void setNavigatorsManager(NavigatorsManager navigatorsManager) {
        this.navigatorsManager = navigatorsManager;
    }

    public void setRestartManager(RestartManager restartManager) {
        this.restartManager = restartManager;
    }

    public void setSecurityManager(SecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    public void setDbManager(DBManager dbManager) {
        this.dbManager = dbManager;
    }

    public void setClientVMOptions(VMOptions clientVMOptions) {
        this.clientVMOptions = clientVMOptions;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setLogicsLogo(String logicsLogo) {
        this.logicsLogo = logicsLogo;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public void setClientHideMenu(String clientHideMenu) {
        this.clientHideMenu = clientHideMenu;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(businessLogics, "businessLogics must be specified");
        Assert.notNull(navigatorsManager, "navigatorsManager must be specified");
        Assert.notNull(restartManager, "restartManager must be specified");
        Assert.notNull(securityManager, "securityManager must be specified");
        Assert.notNull(dbManager, "dbManager must be specified");
        Assert.notNull(clientVMOptions, "clientVMOptions must be specified");
        //assert logicsInstance by checking the context
        Assert.notNull(getContext(), "logicsInstance must be specified");

        if (name == null) {
            name = businessLogics.getClass().getSimpleName();
        }
    }

    @Override
    public int getOrder() {
        return BLLOADER_ORDER - 1;
    }

    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if (LifecycleEvent.INIT.equals(event.getType())) {
            this.baseLM = businessLogics.LM;
        }
    }

    public RemoteNavigatorInterface createNavigator(boolean isFullClient, NavigatorInfo navigatorInfo, boolean reuseSession) {
        if (restartManager.isPendingRestart() && (navigatorInfo.login == null || !navigatorInfo.login.equals("admin")))
            throw new RemoteMessageException(ApiResourceBundle.getString("exceptions.server.is.restarting"));

        return navigatorsManager.createNavigator(getStack(), isFullClient, navigatorInfo, reuseSession);
    }

    protected DataSession createSession() throws SQLException {
        return dbManager.createSession();
    }

    public Long getComputer(String strHostName) {
        return dbManager.getComputer(strHostName, getStack());
    }

    @Override
    public ArrayList<IDaemonTask> getDaemonTasks(long compId) throws RemoteException {
        return businessLogics.getDaemonTasks(compId);
    }

    public ExternalScreen getExternalScreen(int screenID) {
        return businessLogics.getExternalScreen(screenID);
    }

    public ExternalScreenParameters getExternalScreenParameters(int screenID, long computerId) throws RemoteException {
        return businessLogics.getExternalScreenParameters(screenID, computerId);
    }

    public void ping() throws RemoteException {
        //for filterIncl-alive
    }

    @Override
    public Integer getApiVersion() {
        return BaseUtils.getApiVersion();
    }

    @Override
    public String getPlatformVersion() {
        return BaseUtils.getPlatformVersion();
    }

    public GUIPreferences getGUIPreferences() throws RemoteException {
        byte[] logicsLogoBytes = null;
        try {
            if (logicsLogo != null && !logicsLogo.isEmpty())
                logicsLogoBytes = IOUtils.toByteArray(getClass().getResourceAsStream("/" + logicsLogo));
        } catch (Exception e) {
            logger.error("Error reading logics logo: ", e);
        }
        return new GUIPreferences(name, displayName, null, logicsLogoBytes, Boolean.parseBoolean(clientHideMenu));
    }

    public void sendPingInfo(Long computerId, Map<Long, List<Long>> pingInfoMap) {
        Map<Long, List<Long>> pingInfoEntry = RemoteLoggerAspect.pingInfoMap.get(computerId);
        pingInfoEntry = pingInfoEntry != null ? pingInfoEntry : MapFact.<Long, List<Long>>getGlobalConcurrentHashMap();
        pingInfoEntry.putAll(pingInfoMap);
        RemoteLoggerAspect.pingInfoMap.put(computerId, pingInfoEntry);
    }

    @Override
    public List<String> authenticateUser(String userName, String password) throws RemoteException {
        DataSession session;
        try {
            session = dbManager.createSession();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        try {
            User user = securityManager.authenticateUser(session, userName, password, getStack());
            if (user != null) {
                return securityManager.getUserRolesNames(userName, getExtraUserRoleNames(userName));    
            }
        } catch (Exception e) {
            Throwables.propagate(e);
        }
        return null;
    }

    @Override
    public VMOptions getClientVMOptions() throws RemoteException {
        return clientVMOptions;
    }

    @Override
    public long generateID() throws RemoteException {
        return dbManager.generateID();
    }

    protected List<String> getExtraUserRoleNames(String username) {
        return new ArrayList<>();
    }

    protected Long getUserByEmail(DataSession session, String email) throws SQLException, SQLHandledException {
        return (Long) businessLogics.authenticationLM.contactEmail.read(session, new DataObject(email));
    }

    @Override
    public void remindPassword(String email, String localeLanguage) throws RemoteException {
        assert email != null;
        //todo: в будущем нужно поменять на проставление локали в Context
//            ServerResourceBundle.load(localeLanguage);
        try {
            try (DataSession session = createSession()) {
                Long userId = getUserByEmail(session, email);
                if (userId == null) {
                    throw new RuntimeException(localize("{mail.user.not.found}") + ": " + email);
                }

                businessLogics.emailLM.emailUserPassUser.execute(session, getStack(), new DataObject(userId, businessLogics.authenticationLM.customUser));
            }
        } catch (Exception e) {
            logger.error("Error reminding password: ", e);
            throw new RemoteMessageException(localize("{mail.error.sending.password.remind}"), e);
        }
    }

    public boolean isSingleInstance() throws RemoteException {
        return Settings.get().isSingleInstance();
    }

    @Override
    public byte[] readFile(String canonicalName, String... params) throws RemoteException {
        LCP<PropertyInterface> property = businessLogics.findProperty(canonicalName);
        if (property != null) {
            if (!(property.property.getType() instanceof FileClass)) {
                throw new RuntimeException("Property type is distinct from FileClass");
            }
            ImOrderSet<PropertyInterface> interfaces = property.listInterfaces;
            DataObject[] objects = new DataObject[interfaces.size()];
            byte[] fileBytes;
            try {
                DataSession session = createSession();
                ImMap<PropertyInterface, ValueClass> interfaceClasses = property.property.getInterfaceClasses(ClassType.filePolicy);
                for (int i = 0; i < interfaces.size(); i++) {
                    ValueClass valueClass = interfaceClasses.get(interfaces.get(i));
                    objects[i] = session.getDataObject(valueClass, valueClass.getType().parseString(params[i]));
                }
                fileBytes = (byte[]) property.read(session, objects);

                if (fileBytes != null && !(property.property.getType() instanceof DynamicFormatFileClass)) {
                    fileBytes = BaseUtils.mergeFileAndExtension(fileBytes, ((StaticFormatFileClass) property.property.getType()).getOpenExtension(fileBytes).getBytes());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return fileBytes;
        } else {
            throw new RuntimeException("Property was not found");
        }
    }

    @Override
    public List<Object> exec(String action, String[] returnCanonicalNames, Object[] params, Charset charset) {
        List<Object> returnList;
        try {
            LAP property = businessLogics.findActionByCompoundName(action);
            if (property != null) {
                returnList = executeExternal(property, returnCanonicalNames, params, charset);
            } else {
                throw new RuntimeException(String.format("Action %s was not found", action));
            }
        } catch (ParseException | SQLHandledException | SQLException | IOException e) {
            throw new RuntimeException(e);
        }
        return returnList;
    }

    @Override
    public List<Object> eval(boolean action, Object paramScript, String[] returnCanonicalNames, Object[] params, String charset) {
        List<Object> returnList = new ArrayList<>();
        if (paramScript != null) {
            try {
                String script = StringClass.text.parseHTTP(paramScript, Charset.forName(charset));
                LAP<?> runAction = businessLogics.evaluateRun(script, action);
                if (runAction != null)
                    returnList = executeExternal(runAction, returnCanonicalNames, params, Charset.forName(charset));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("Eval script was not found");
        }
        return returnList;
    }

    @Override
    public List<Object> read(String property, Object[] params, Charset charset) {
        try {
            LCP lcp = businessLogics.findPropertyByCompoundName(property);
            if (lcp != null) {
                try (DataSession session = createSession()) {
                    return readReturnProperty(session, lcp, ExternalHTTPActionProperty.getParams(session, lcp, params, charset));
                }
            } else
                throw new RuntimeException(String.format("Property %s was not found", property));

        } catch (SQLException | SQLHandledException | ParseException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Object> executeExternal(LAP property, String[] returnCanonicalNames, Object[] params, Charset charset) throws SQLException, ParseException, SQLHandledException, IOException {
        List<Object> returnList = new ArrayList<>();
        try (DataSession session = createSession()) {
            ExecutionStack stack = getStack();

            property.execute(session, stack, ExternalHTTPActionProperty.getParams(session, property, params, charset));

            if (returnCanonicalNames != null && returnCanonicalNames.length > 0) {
                for (String returnCanonicalName : returnCanonicalNames) {
                    LCP returnProperty = businessLogics.findProperty(returnCanonicalName);
                    if (returnProperty != null) {
                        returnList.addAll(readReturnProperty(session, returnProperty));
                    } else
                        throw new RuntimeException(String.format("Return property %s was not found", returnCanonicalName));
                }
            } else {
                returnList.addAll(readReturnProperty(session, businessLogics.LM.exportFile));
            }

            session.apply(businessLogics, stack);
        }
        return returnList;
    }

    private List<Object> readReturnProperty(DataSession session, LCP returnProperty, ObjectValue... params) throws SQLException, SQLHandledException, IOException {
        Object returnValue = returnProperty.read(session, params);
        Type returnType = returnProperty.property.getType();
        return readReturnProperty(returnValue, returnType);
    }

    private List<Object> readReturnProperty(Object returnValue, Type returnType) throws IOException {
        List<Object> returnList = new ArrayList<>();
        boolean jdbcSingleRow = false;
        if (returnType instanceof DynamicFormatFileClass && returnValue != null) {
            if (BaseUtils.getExtension((byte[]) returnValue).equals("jdbc")) {
                JDBCTable jdbcTable = JDBCTable.deserializeJDBC(BaseUtils.getFile((byte[]) returnValue));
                if (jdbcTable.singleRow) {
                    ImMap<String, Object> row = jdbcTable.set.isEmpty() ? null : jdbcTable.set.get(0);
                    for (String field : jdbcTable.fields) {
                        Type fieldType = jdbcTable.fieldTypes.get(field);
                        if(row == null)
                            returnList.add(null);
                        else
                            returnList.addAll(readReturnProperty(row.get(field), fieldType));
                    }
                    jdbcSingleRow = true;
                }
            }
        }
        if (!jdbcSingleRow)
            returnList.add(returnType.formatHTTP(returnValue, null));
        return returnList;
    }

    @Override
    public String addUser(String username, String email, String password, String firstName, String lastName, String localeLanguage) throws RemoteException {
        return securityManager.addUser(username, email, password, firstName, lastName, localeLanguage, getStack());
    }

    @Override
    public Map<String, String> readMemoryLimits() {
        Map<String, String> memoryLimitMap = new HashMap<>();
        try (DataSession session = createSession()) {
            KeyExpr memoryLimitExpr = new KeyExpr("memoryLimit");
            ImRevMap<Object, KeyExpr> memoryLimitKeys = MapFact.singletonRev((Object) "memoryLimit", memoryLimitExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(memoryLimitKeys);

            String[] names = new String[]{"name", "maxHeapSize", "vmargs"};
            LCP[] properties = businessLogics.securityLM.findProperties("name[MemoryLimit]", "maxHeapSize[MemoryLimit]",
                    "vmargs[MemoryLimit]");
            for (int j = 0; j < properties.length; j++) {
                query.addProperty(names[j], properties[j].getExpr(memoryLimitExpr));
            }
            query.and(businessLogics.securityLM.findProperty("name[MemoryLimit]").getExpr(memoryLimitExpr).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String name = (String) entry.get("name");
                String line = "";
                String maxHeapSize = (String) entry.get("maxHeapSize");
                if(maxHeapSize != null)
                    line += "maxHeapSize=" + maxHeapSize;
                String vmargs = (String) entry.get("vmargs");
                if(vmargs != null)
                    line += (line.isEmpty() ? "" : "&") + "vmargs=" + URLEncoder.encode(vmargs, "utf-8");
                memoryLimitMap.put(name, line);
            }
        } catch (ScriptingErrorLog.SemanticErrorException | SQLException | SQLHandledException | UnsupportedEncodingException e) {
            logger.error("Error reading MemoryLimit: ", e);
        }
        return memoryLimitMap;
    }

    public Long getCurrentUser() {
        return dbManager.getSystemUserObject();
    }

    public Long getCurrentComputer() {
        return (Long) dbManager.getServerComputerObject(getStack()).getValue();
    }

    @Override
    protected boolean isEnabledUnreferenced() { // иначе когда отключаются все клиенты логика закрывается
        return false;
    }

    @Override
    public String getSID() {
        return "logics";
    }

    @Override
    protected boolean isUnreferencedSyncedClient() { // если ушли все ссылки считаем синхронизированным, так как клиент уже ни к чему обращаться не может
        return true;
    }

    @Override
    public List<ReportPath> saveAndGetCustomReportPathList(String formSID, boolean recreate) throws RemoteException {
        try {
            return FormInstance.saveAndGetCustomReportPathList(businessLogics.findForm(formSID), recreate);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Object getProfiledObject() {
        return "l";
    }
}

