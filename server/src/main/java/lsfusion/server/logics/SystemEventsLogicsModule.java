package lsfusion.server.logics;

import com.google.common.base.Throwables;
import lsfusion.base.DateConverter;
import lsfusion.base.ExceptionUtils;
import lsfusion.server.classes.AbstractCustomClass;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.linear.LAP;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.antlr.runtime.RecognitionException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;

public class SystemEventsLogicsModule extends ScriptingLogicsModule {

    private final AuthenticationLogicsModule authenticationLM;

    public AbstractCustomClass exception;
    public ConcreteCustomClass clientException;
    public ConcreteCustomClass serverException;
    public ConcreteCustomClass launch;
    public ConcreteCustomClass connection;
    public ConcreteCustomClass connectionStatus;
    public ConcreteCustomClass session;

    public LCP computerConnection;
    public LCP remoteAddressConnection;
    public LCP userConnection;
    public LCP userLoginConnection;
    public LCP<PropertyInterface> connectionStatusConnection;
    public LCP connectTimeConnection;
    public LCP disconnectTimeConnection;
    public LAP disconnectConnection;

    public LCP computerLaunch;
    public LCP timeLaunch;
    public LCP revisionLaunch;

    public LCP messageException;
    public LCP dateException;
    public LCP erTraceException;
    public LCP typeException;
    public LCP clientClientException;
    public LCP loginClientException;

    public LCP connectionFormCount;

    public LCP currentSession;
    public LCP connectionSession;
    public LCP navigatorElementSession;
    public LCP quantityAddedClassesSession;
    public LCP quantityRemovedClassesSession;
    public LCP quantityChangedClassesSession;
    public LCP changesSession;

    public SystemEventsLogicsModule(BusinessLogics BL, BaseLogicsModule baseLM) throws IOException {
        super(SystemEventsLogicsModule.class.getResourceAsStream("/lsfusion/system/SystemEvents.lsf"), "/lsfusion/system/SystemEvents.lsf", baseLM, BL);
        setBaseLogicsModule(baseLM);
        this.authenticationLM = BL.authenticationLM;
    }

    @Override
    public void initClasses() throws RecognitionException {
        super.initClasses();

        clientException = (ConcreteCustomClass) getClassByName("ClientException");
        serverException = (ConcreteCustomClass) getClassByName("ServerException");
        launch = (ConcreteCustomClass) getClassByName("Launch");
        connection = (ConcreteCustomClass) getClassByName("Connection");
        connectionStatus = (ConcreteCustomClass) getClassByName("ConnectionStatus");
        session = (ConcreteCustomClass) getClassByName("Session");
    }

    @Override
    public void initProperties() throws RecognitionException {
        super.initProperties();

        // Подключения к серверу
        computerConnection = getLCPByOldName("computerConnection");
        remoteAddressConnection = getLCPByOldName("remoteAddressConnection");
        userConnection = getLCPByOldName("userConnection");
        userLoginConnection = getLCPByOldName("userLoginConnection");
        connectionStatusConnection = (LCP<PropertyInterface>) getLCPByOldName("connectionStatusConnection");

        connectTimeConnection = getLCPByOldName("connectTimeConnection");
        disconnectConnection = getLAPByOldName("disconnectConnection");
        addIfAProp(baseGroup, "Отключить", true, getLCPByOldName("disconnectTimeConnection"), 1, disconnectConnection, 1);

        // Логирование старта сервера
        computerLaunch = getLCPByOldName("computerLaunch");
        timeLaunch = getLCPByOldName("timeLaunch");
        revisionLaunch = getLCPByOldName("revisionLaunch");

        // Ошибки выполнения
        messageException = getLCPByOldName("messageException");
        dateException = getLCPByOldName("dateException");
        erTraceException = getLCPByOldName("erTraceException");
        typeException =  getLCPByOldName("typeException");
        clientClientException = getLCPByOldName("clientClientException");
        loginClientException = getLCPByOldName("loginClientException");

        // Открытые формы во время подключения
        connectionFormCount = getLCPByOldName("connectionFormCount");

        // Сессия
        currentSession = getLCPByOldName("currentSession");
        connectionSession = getLCPByOldName("connectionSession");
        navigatorElementSession = getLCPByOldName("navigatorElementSession");
        quantityAddedClassesSession = getLCPByOldName("quantityAddedClassesSession");
        quantityRemovedClassesSession = getLCPByOldName("quantityRemovedClassesSession");
        quantityChangedClassesSession = getLCPByOldName("quantityChangedClassesSession");
        changesSession = getLCPByOldName("changesSession");
//        baseLM.objectClassName.makeLoggable(this, true);
    }

    public void logException(BusinessLogics bl, Throwable t, DataObject user, String clientName, boolean client) throws SQLException, SQLHandledException {
        @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
        String message = Throwables.getRootCause(t).getLocalizedMessage();
        String errorType = t.getClass().getName();
        String erTrace = ExceptionUtils.getStackTraceString(t);

        DataSession session = createSession();
        DataObject exceptionObject;
        if (client) {
            exceptionObject = session.addObject(clientException);
            clientClientException.change(clientName, session, exceptionObject);
            String userLogin = (String) authenticationLM.loginCustomUser.read(session, user);
            loginClientException.change(userLogin, session, exceptionObject);
        } else {
            exceptionObject = session.addObject(serverException);
        }
        messageException.change(message, session, exceptionObject);
        typeException.change(errorType, session, exceptionObject);
        erTraceException.change(erTrace, session, exceptionObject);
        dateException.change(DateConverter.dateToStamp(Calendar.getInstance().getTime()), session, exceptionObject);

        session.apply(bl);
        session.close();
    }
}
