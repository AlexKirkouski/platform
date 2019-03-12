package lsfusion.server.remote;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.MapFact;
import lsfusion.interop.action.ServerResponse;
import lsfusion.server.ServerLoggers;
import lsfusion.server.Settings;
import lsfusion.server.context.Context;
import lsfusion.server.context.ThreadLocalContext;
import org.apache.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Aspect
public class RemoteLoggerAspect {
    private final static Logger logger = ServerLoggers.remoteLogger;

    public static final Map<Long, UserActivity> userActivityMap = MapFact.getGlobalConcurrentHashMap();
    public static final Map<String, Map<Long, List<Long>>> pingInfoMap = MapFact.getGlobalConcurrentHashMap();
    private static final Map<Long, Timestamp> dateTimeCallMap = MapFact.getGlobalConcurrentHashMap();
    private static Map<Long, Boolean> remoteLoggerDebugEnabled = MapFact.getGlobalConcurrentHashMap();

    @Around(RemoteContextAspect.allRemoteCalls)
    public Object executeRemoteMethod(ProceedingJoinPoint thisJoinPoint, Object target) throws Throwable {
        //final long id = Thread.currentThread().getId();
        //putDateTimeCall(id, new Timestamp(System.currentTimeMillis()));
        //try {
            ContextAwarePendingRemoteObject remoteObject = (ContextAwarePendingRemoteObject) target;
            Context context = remoteObject.getContext();
            assert context == ThreadLocalContext.get();

            long startTime = System.currentTimeMillis();
            Object result = thisJoinPoint.proceed();
            long runTime = System.currentTimeMillis() - startTime;
            
            if(result instanceof ServerResponse)
                ((ServerResponse)result).timeSpent = runTime;

            Long user = context.getCurrentUser();
            Long computer = context.getCurrentComputer();
//            Long connection = context.getCurrentConnection();
            userActivityMap.put(user, new UserActivity(computer, startTime));

            boolean debugEnabled = user != null && isRemoteLoggerDebugEnabled(user);

            if (debugEnabled || runTime > Settings.get().getRemoteLogTime()) {
                logger.info(logCall(thisJoinPoint, runTime));
            }

            return result;
        //} finally {
        //    removeDateTimeCall(id);
        //}
    }

    private String logCall(ProceedingJoinPoint thisJoinPoint, long runTime) {
        return String.format(
                "Executing remote method (time: %1$d ms.): %2$s(%3$s)",
                runTime,
                thisJoinPoint.getSignature().getName(),
                BaseUtils.toString(", ", thisJoinPoint.getArgs())
        );
    }

    public static void setRemoteLoggerDebugEnabled(Long user, Boolean enabled) {
        remoteLoggerDebugEnabled.put(user, enabled != null && enabled);
    }

    public boolean isRemoteLoggerDebugEnabled(Long user) {
        Boolean lde = remoteLoggerDebugEnabled.get(user);
        return lde != null && lde;
    }

    public static Timestamp getDateTimeCall(long pid) {
        return dateTimeCallMap.get(pid);
    }

    public static void putDateTimeCall(long pid, Timestamp timestamp) {
        dateTimeCallMap.put(pid, timestamp);
    }

    public static void removeDateTimeCall(long pid) {
        dateTimeCallMap.remove(pid);
    }
}
