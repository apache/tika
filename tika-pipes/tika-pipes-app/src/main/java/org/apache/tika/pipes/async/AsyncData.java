package org.apache.tika.pipes.async;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.EmitData;

import java.util.List;

public class AsyncData extends EmitData {

    private final AsyncTask asyncTask;

    public AsyncData(AsyncTask asyncTask, List<Metadata> metadataList) {
        super(asyncTask.getEmitKey(), metadataList);
        this.asyncTask = asyncTask;
    }

    public AsyncTask getAsyncTask() {
        return asyncTask;
    }
}
