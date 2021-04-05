/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.pipes.async;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;

public class AsyncTask extends FetchEmitTuple {

    public static final AsyncTask SHUTDOWN_SEMAPHORE =
            new AsyncTask(-1, (short) -1, new FetchEmitTuple(null, null, null));
    private final short retry;
    private long taskId;

    public AsyncTask(@JsonProperty("taskId") long taskId, @JsonProperty("retry") short retry,
                     @JsonProperty("fetchEmitTuple") FetchEmitTuple fetchEmitTuple) {
        super(fetchEmitTuple.getFetchKey(), fetchEmitTuple.getEmitKey(),
                fetchEmitTuple.getMetadata());
        this.taskId = taskId;
        this.retry = retry;
    }

    public long getTaskId() {
        return taskId;
    }

    public void setTaskId(long taskId) {
        this.taskId = taskId;
    }

    public short getRetry() {
        return retry;
    }

    @Override
    public String toString() {
        return "AsyncTask{" + "taskId=" + taskId + ", retry=" + retry + '}';
    }
}
