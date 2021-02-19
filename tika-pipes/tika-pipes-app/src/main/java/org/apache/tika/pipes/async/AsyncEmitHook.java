package org.apache.tika.pipes.async;

public interface AsyncEmitHook {

    void onSuccess(AsyncTask task);

    void onFail(AsyncTask task);
}
