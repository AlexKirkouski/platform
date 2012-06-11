package platform.server.form.instance.remote;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import platform.base.ExceptionUtils;
import platform.interop.action.ClientAction;
import platform.interop.action.LogMessageClientAction;
import platform.interop.form.ServerResponse;
import platform.server.RemoteContextObject;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

public abstract class RemotePausableInvocation extends PausableInvocation<ServerResponse, RemoteException> {
    private final RemoteContextObject remoteObject;

    /**
     * @param invocationsExecutor
     */
    public RemotePausableInvocation(ExecutorService invocationsExecutor, RemoteContextObject remoteObject) {
        super(invocationsExecutor);
        this.remoteObject = remoteObject;
    }

    private ServerResponse invocationResult = null;

    protected List<ClientAction> delayedActions = new ArrayList<ClientAction>();
    private int neededActionResultsCnt = -1;

    private Object[] actionResults;
    private Exception clientException;

    public final String getLogMessage() {
        String result = "";
        for (ClientAction action : delayedActions) {
            if (action instanceof LogMessageClientAction) {
                result = (result.length() == 0 ? "" : result + '\n') + ((LogMessageClientAction) action).message;
            }
        }
        return result;
    } 
    
    /**
     * рабочий поток
     */
    public final void delayUserInterfaction(ClientAction action) {
        delayedActions.add(action);
    }

    /**
     * рабочий поток
     */
    public final Object[] pauseForUserInteraction(ClientAction... actions) {
        neededActionResultsCnt = actions.length;
        Collections.addAll(delayedActions, actions);

        try {
            pause();
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread was interrupted");
        }

        if (clientException != null) {
            Exception ex = clientException;
            clientException = null;
            Throwables.propagate(ex);
        }

        return Arrays.copyOfRange(actionResults, actionResults.length - neededActionResultsCnt, actionResults.length);
    }

    /**
     * основной поток
     */
    public final ServerResponse resumeAfterUserInteraction(Object[] actionResults) throws RemoteException {
        Preconditions.checkState(isPaused(), "can't resume after user interaction - wasn't paused for user interaction");

        this.actionResults = actionResults;
        return resume();
    }

    public final ServerResponse resumWithException(Exception clientException) throws RemoteException {
        this.clientException = clientException;
        return resume();
    }

    /**
     * <b>рабочий поток</b><br/>
     * по умолчанию вызывает {@link RemotePausableInvocation#callInvocation()}, и возвращает его результат из {@link RemotePausableInvocation#handleFinished()}
     * @throws Throwable
     */
    protected void runInvocation() throws Exception {
        remoteObject.threads.add(Thread.currentThread());
        try {
            invocationResult = callInvocation();
        } finally {
            remoteObject.threads.remove(Thread.currentThread());
        }
    }

    /**
     * по умолчанию просто возвращает null,
     * переопредлять нужно либо данный метод, возвращая из него результат, либо {@link PausableInvocation#runInvocation()}
     * вместе с {@link RemotePausableInvocation#handleFinished()}
     *
     * @return
     * @throws Throwable
     */
    protected ServerResponse callInvocation() throws Exception {
        return null;
    }

    /**
     * <b>основной поток</b><br/>
     * по умолчанию возвращает результат выполнения {@link RemotePausableInvocation#callInvocation()}
     * @return
     * @throws RemoteException
     */
    protected ServerResponse handleFinished() throws RemoteException {
        return invocationResult;
    }

    @Override
    protected ServerResponse handleThrows(Throwable t) throws RemoteException {
        throw ExceptionUtils.propogateRemoteException(t);
    }

    /**
     * основной поток
     */
    @Override
    protected final ServerResponse handlePaused() throws RemoteException {
        try {
            ServerResponse result = new ServerResponse(delayedActions.toArray(new ClientAction[delayedActions.size()]));
            delayedActions.clear();

            return result;
        } catch (Exception e) {
            throw ExceptionUtils.propogateRemoteException(e);
        }
    }
}
