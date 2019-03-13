package lsfusion.server.logics;

import lsfusion.base.DateConverter;
import lsfusion.base.ExceptionUtils;
import lsfusion.base.Pair;
import lsfusion.interop.exception.*;
import lsfusion.server.classes.AbstractCustomClass;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.sets.ResolveClassSet;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.language.linear.LCP;
import lsfusion.server.logics.property.CurrentConnectionFormulaProperty;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.antlr.runtime.RecognitionException;

import java.io.IOException;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class SystemEventsLogicsModule extends ScriptingLogicsModule {

    private final AuthenticationLogicsModule authenticationLM;

    public AbstractCustomClass exception;
    public ConcreteCustomClass clientException;
    public ConcreteCustomClass webClientException;
    public ConcreteCustomClass remoteServerException;
    public ConcreteCustomClass fatalHandledRemoteException;
    public ConcreteCustomClass nonFatalHandledRemoteException;
    public ConcreteCustomClass unhandledRemoteException;
    public ConcreteCustomClass serverException;
    public ConcreteCustomClass launch;
    public ConcreteCustomClass connection;
    public ConcreteCustomClass connectionStatus;
    public ConcreteCustomClass session;

    public LCP computerConnection;
    public LCP remoteAddressConnection;
    public LCP userConnection;
    public LCP userLoginConnection;
    public LCP osVersionConnection;
    public LCP processorConnection;
    public LCP architectureConnection;
    public LCP coresConnection;
    public LCP physicalMemoryConnection;
    public LCP totalMemoryConnection;
    public LCP maximumMemoryConnection;
    public LCP freeMemoryConnection;
    public LCP javaVersionConnection;
    public LCP is64JavaConnection;
    public LCP screenSizeConnection;
    public LCP<PropertyInterface> connectionStatusConnection;
    public LCP connectTimeConnection;
    public LCP launchConnection;

    public LCP currentConnection;

    public LCP currentLaunch;
    public LCP computerLaunch;
    public LCP timeLaunch;
    public LCP revisionLaunch;

    public LCP messageException;
    public LCP dateException;
    public LCP erTraceException;
    public LCP lsfTraceException;
    public LCP typeException;
    public LCP clientClientException;
    public LCP loginClientException;

    private LCP reqIdHandledException;
    private LCP countNonFatalHandledException;
    private LCP abandonedNonFatalHandledException;

    public LCP connectionFormCount;

    public LCP<?> currentSession;
    public LCP connectionSession;
    public LCP formSession;
    public LCP quantityAddedClassesSession;
    public LCP quantityRemovedClassesSession;
    public LCP quantityChangedClassesSession;
    public LCP changesSession;

    public LCP pingComputerDateTimeFromDateTimeTo;
    public LCP minTotalMemoryComputerDateTimeFromDateTimeTo;
    public LCP maxTotalMemoryComputerDateTimeFromDateTimeTo;
    public LCP minUsedMemoryComputerDateTimeFromDateTimeTo;
    public LCP maxUsedMemoryComputerDateTimeFromDateTimeTo;

    public SystemEventsLogicsModule(BusinessLogics BL, BaseLogicsModule baseLM) throws IOException {
        super(SystemEventsLogicsModule.class.getResourceAsStream("/system/SystemEvents.lsf"), "/system/SystemEvents.lsf", baseLM, BL);
        this.authenticationLM = BL.authenticationLM;
    }

    @Override
    public void initMetaAndClasses() throws RecognitionException {
        super.initMetaAndClasses();

        clientException = (ConcreteCustomClass) findClass("ClientException");
        webClientException = (ConcreteCustomClass) findClass("WebClientException");
        remoteServerException = (ConcreteCustomClass) findClass("RemoteServerException");
        fatalHandledRemoteException = (ConcreteCustomClass) findClass("FatalHandledException");
        nonFatalHandledRemoteException = (ConcreteCustomClass) findClass("NonFatalHandledException");
        unhandledRemoteException = (ConcreteCustomClass) findClass("UnhandledException");
        serverException = (ConcreteCustomClass) findClass("ServerException");
        launch = (ConcreteCustomClass) findClass("Launch");
        connection = (ConcreteCustomClass) findClass("Connection");
        connectionStatus = (ConcreteCustomClass) findClass("ConnectionStatus");
        session = (ConcreteCustomClass) findClass("Session");
    }

    @Override
    public void initMainLogic() throws RecognitionException {
        currentConnection = addProperty(null, new LCP<>(new CurrentConnectionFormulaProperty(connection)));
        makePropertyPublic(currentConnection, "currentConnection", new ArrayList<ResolveClassSet>());

        super.initMainLogic();

        // Подключения к серверу
        computerConnection = findProperty("computer[Connection]");
        remoteAddressConnection = findProperty("remoteAddress[Connection]");
        userConnection = findProperty("user[Connection]");
        userLoginConnection = findProperty("userLogin[Connection]");
        osVersionConnection = findProperty("osVersion[Connection]");
        processorConnection = findProperty("processor[Connection]");
        architectureConnection = findProperty("architecture[Connection]");
        coresConnection = findProperty("cores[Connection]");
        physicalMemoryConnection = findProperty("physicalMemory[Connection]");
        totalMemoryConnection = findProperty("totalMemory[Connection]");
        maximumMemoryConnection = findProperty("maximumMemory[Connection]");
        freeMemoryConnection = findProperty("freeMemory[Connection]");
        javaVersionConnection = findProperty("javaVersion[Connection]");
        is64JavaConnection = findProperty("is64Java[Connection]");
        screenSizeConnection = findProperty("screenSize[Connection]");
        connectionStatusConnection = (LCP<PropertyInterface>) findProperty("connectionStatus[Connection]");

        connectTimeConnection = findProperty("connectTime[Connection]");
        launchConnection = findProperty("launch[Connection]");

        // Логирование старта сервера
        currentLaunch = findProperty("currentLaunch[]");
        computerLaunch = findProperty("computer[Launch]");
        timeLaunch = findProperty("time[Launch]");
        revisionLaunch = findProperty("revision[Launch]");

        // Ошибки выполнения
        messageException = findProperty("message[Exception]");
        dateException = findProperty("date[Exception]");
        erTraceException = findProperty("erTrace[Exception]");
        lsfTraceException = findProperty("lsfStackTrace[Exception]");
        typeException =  findProperty("type[Exception]");
        clientClientException = findProperty("client[ClientException]");
        loginClientException = findProperty("login[ClientException]");
        reqIdHandledException = findProperty("reqId[HandledException]");
        countNonFatalHandledException = findProperty("count[NonFatalHandledException]");
        abandonedNonFatalHandledException = findProperty("abandoned[NonFatalHandledException]");

        // Открытые формы во время подключения
        connectionFormCount = findProperty("connectionFormCount[Connection,Form]");

        // Сессия
        currentSession = findProperty("currentSession[]");
        connectionSession = findProperty("connection[Session]");
        formSession = findProperty("form[Session]");
        quantityAddedClassesSession = findProperty("quantityAddedClasses[Session]");
        quantityRemovedClassesSession = findProperty("quantityRemovedClasses[Session]");
        quantityChangedClassesSession = findProperty("quantityChangedClasses[Session]");
        changesSession = findProperty("changes[Session]");
//        baseLM.objectClassName.makeLoggable(this, true);

        pingComputerDateTimeFromDateTimeTo = findProperty("pingFromTo[Computer,DATETIME,DATETIME]");
        minTotalMemoryComputerDateTimeFromDateTimeTo = findProperty("minTotalMemoryFromTo[Computer,DATETIME,DATETIME]");
        maxTotalMemoryComputerDateTimeFromDateTimeTo = findProperty("maxTotalMemoryFromTo[Computer,DATETIME,DATETIME]");
        minUsedMemoryComputerDateTimeFromDateTimeTo = findProperty("minUsedMemoryFromTo[Computer,DATETIME,DATETIME]");
        maxUsedMemoryComputerDateTimeFromDateTimeTo = findProperty("maxUsedMemoryFromTo[Computer,DATETIME,DATETIME]");
    }

    public void logException(BusinessLogics bl, ExecutionStack stack, Throwable t, DataObject user, String clientName, boolean client, boolean web) throws SQLException, SQLHandledException {
        assert t.getCause() == null;

        String message = replaceNonUTFCharacters(t.getMessage());
        String errorType = t.getClass().getName();

        Pair<String, String> exStacks = RemoteInternalException.getExStacks(t);
        String javaStack = replaceNonUTFCharacters(exStacks.first);
        String lsfStack = exStacks.second;

        String time = new SimpleDateFormat().format(Calendar.getInstance().getTime());
        logger.error( message + " at '" + time + "' from '" + clientName + "': " + '\n' + ExceptionUtils.getExStackTrace(javaStack, lsfStack));

        try (DataSession session = ThreadLocalContext.createSession()) {
            DataObject exceptionObject;
            if (client) {
                if (t instanceof RemoteServerException) {
                    exceptionObject = session.addObject(remoteServerException);
                } else if (t instanceof RemoteException) {
                    exceptionObject = session.addObject(unhandledRemoteException);
                } else if (t instanceof RemoteClientException) {
                    RemoteClientException handled = (RemoteClientException) t;

                    if (t instanceof FatalRemoteClientException)
                        exceptionObject = session.addObject(fatalHandledRemoteException);
                    else {
                        exceptionObject = session.addObject(nonFatalHandledRemoteException);

                        NonFatalRemoteClientException nonFatal = (NonFatalRemoteClientException) t;
                        countNonFatalHandledException.change(nonFatal.count, session, exceptionObject);
                        abandonedNonFatalHandledException.change(nonFatal.abandoned, session, exceptionObject);
                    }

                    reqIdHandledException.change(handled.reqId, session, exceptionObject);
                } else if (web) {
                    exceptionObject = session.addObject(webClientException);
                } else {
                    exceptionObject = session.addObject(clientException);    
                }
                clientClientException.change(clientName, session, exceptionObject);
                if(user != null) {
                    String userLogin = (String) authenticationLM.loginCustomUser.read(session, user);
                    loginClientException.change(userLogin, session, exceptionObject);
                }
            } else {
                exceptionObject = session.addObject(serverException);
            }
            messageException.change(message, session, exceptionObject);
            typeException.change(errorType, session, exceptionObject);
            erTraceException.change(javaStack, session, exceptionObject);
            lsfTraceException.change(lsfStack, session, exceptionObject);
            dateException.change(DateConverter.dateToStamp(Calendar.getInstance().getTime()), session, exceptionObject);

            session.applyException(bl, stack);
        }
    }

    private String replaceNonUTFCharacters(String value) {
        return value == null ? null : value.replace('\u0000', '?');
    }
}
