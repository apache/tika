package org.apache.tika.server.core.resource;

import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;

import java.util.List;

public class AsyncRequest {
    private final String id;
    private final List<FetchEmitTuple> tuples;

    public AsyncRequest(String id, List<FetchEmitTuple> tuples) {
        this.id = id;
        this.tuples = tuples;
    }

    public String getId() {
        return id;
    }

    public List<FetchEmitTuple> getTuples() {
        return tuples;
    }
}
