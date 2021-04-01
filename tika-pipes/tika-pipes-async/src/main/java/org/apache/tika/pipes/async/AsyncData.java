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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;

public class AsyncData extends EmitData {

    private final long taskId;
    private final FetchKey fetchKey;
    private final FetchEmitTuple.ON_PARSE_EXCEPTION onParseException;

    public AsyncData(@JsonProperty("taskId") long taskId,
                     @JsonProperty("fetchKey") FetchKey fetchKey,
                     @JsonProperty("emitKey") EmitKey emitKey, @JsonProperty("onParseException")
                             FetchEmitTuple.ON_PARSE_EXCEPTION onParseException,
                     @JsonProperty("metadataList") List<Metadata> metadataList) {
        super(emitKey, metadataList);
        this.taskId = taskId;
        this.fetchKey = fetchKey;
        this.onParseException = onParseException;
    }

    public FetchKey getFetchKey() {
        return fetchKey;
    }

    public long getTaskId() {
        return taskId;
    }

    public FetchEmitTuple.ON_PARSE_EXCEPTION getOnParseException() {
        return onParseException;
    }
}
