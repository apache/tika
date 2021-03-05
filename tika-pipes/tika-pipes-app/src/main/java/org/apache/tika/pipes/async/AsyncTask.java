package org.apache.tika.pipes.async;

import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;

public class AsyncTask extends FetchEmitTuple {

    public static final AsyncTask SHUTDOWN_SEMAPHORE
            = new AsyncTask(-1, (short)-1, new FetchEmitTuple(null, null, null));

    private long taskId;
    private final short retry;

    public AsyncTask(long taskId, short retry,
                     FetchEmitTuple fetchEmitTuple) {
        super(fetchEmitTuple.getFetchKey(), fetchEmitTuple.getEmitKey(), fetchEmitTuple.getMetadata());
        this.taskId = taskId;
        this.retry = retry;
    }

    public long getTaskId() {
        return taskId;
    }

    public short getRetry() {
        return retry;
    }

    public void setTaskId(long taskId) {
        this.taskId = taskId;
    }
    @Override
    public String toString() {
        return "AsyncTask{" +
                "taskId=" + taskId +
                ", retry=" + retry +
                '}';
    }
}
