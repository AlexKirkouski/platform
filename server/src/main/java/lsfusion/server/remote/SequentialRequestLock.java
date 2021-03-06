package lsfusion.server.remote;

import com.google.common.base.Throwables;
import lsfusion.interop.exceptions.RemoteInterruptedException;
import lsfusion.interop.exceptions.RemoteServerException;
import lsfusion.server.ServerLoggers;
import org.apache.log4j.Logger;
import org.thavam.util.concurrent.BlockingHashMap;
import org.thavam.util.concurrent.BlockingMap;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;

public class SequentialRequestLock {

    private static final Object LOCK_OBJECT = new Object();

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private ArrayBlockingQueue requestLock = new ArrayBlockingQueue(1, true);

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private BlockingMap<Long, Object> sequentialRequestLock = new BlockingHashMap<>();

    public SequentialRequestLock() {
        initRequestLock();
    }

    private void initRequestLock() {
        try {
            sequentialRequestLock.offer(0L, LOCK_OBJECT);
            requestLock.put(LOCK_OBJECT);
        } catch (InterruptedException e) {
            throw new RemoteInterruptedException(e);
        }
    }

    public void acquireRequestLock(String ownerSID, long requestIndex) {
        ServerLoggers.pausableLog("Acquiring request lock for " + ownerSID + " for request #" + requestIndex);
        try {
            if (requestIndex >= 0) {
                sequentialRequestLock.take(requestIndex);
            }
            requestLock.take();
            ServerLoggers.pausableLog("Acquired request lock for " + ownerSID + " for request #" + requestIndex);
        } catch (InterruptedException e) {
            ServerLoggers.pausableLog("Interrupted request lock for " + ownerSID + " for request #" + requestIndex);
            throw new RemoteInterruptedException(e);
        }
    }

    public void releaseRequestLock(String ownerSID, long requestIndex) {
        ServerLoggers.pausableLog("Releasing request lock for " + ownerSID + " for request #" + requestIndex);
        try {
            requestLock.put(LOCK_OBJECT);
            if (requestIndex >= 0) {
                sequentialRequestLock.offer(requestIndex + 1, LOCK_OBJECT);
            }
            ServerLoggers.pausableLog("Released request lock for " + ownerSID + " for request #" + requestIndex);
        } catch (InterruptedException e) {
            throw new RemoteInterruptedException(e);
        }
    }
}
