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
package org.apache.tika.pipes.core.serialization;

import static org.apache.tika.serialization.ParseContextSerializer.PARSE_CONTEXT;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.apache.tika.pipes.core.FetchEmitTuple;
import org.apache.tika.utils.StringUtils;

public class FetchEmitTupleSerializer extends JsonSerializer<FetchEmitTuple> {
    public static final String ID = "id";
    public static final String FETCHER = "fetcher";
    public static final String FETCH_KEY = "fetchKey";
    public static final String FETCH_RANGE_START = "fetchRangeStart";
    public static final String FETCH_RANGE_END = "fetchRangeEnd";
    public static final String EMITTER = "emitter";
    public static final String EMIT_KEY = "emitKey";
    public static final String METADATA_KEY = "metadata";
    public static final String ON_PARSE_EXCEPTION = "onParseException";

    public void serialize(FetchEmitTuple t, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {

        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField(ID, t.getId());
        jsonGenerator.writeStringField(FETCHER, t.getFetchKey().getFetcherPluginId());
        jsonGenerator.writeStringField(FETCH_KEY, t.getFetchKey().getFetchKey());
        if (t.getFetchKey().hasRange()) {
            jsonGenerator.writeNumberField(FETCH_RANGE_START, t.getFetchKey().getRangeStart());
            jsonGenerator.writeNumberField(FETCH_RANGE_END, t.getFetchKey().getRangeEnd());
        }
        jsonGenerator.writeStringField(EMITTER, t.getEmitKey().getEmitterName());
        if (!StringUtils.isBlank(t.getEmitKey().getEmitKey())) {
            jsonGenerator.writeStringField(EMIT_KEY, t.getEmitKey().getEmitKey());
        }
        if (t.getMetadata().size() > 0) {
            jsonGenerator.writeObjectField(METADATA_KEY, t.getMetadata());
        }
        jsonGenerator.writeStringField(ON_PARSE_EXCEPTION, t.getOnParseException().name().toLowerCase(Locale.US));
        if (!t.getParseContext().isEmpty()) {
            jsonGenerator.writeObjectField(PARSE_CONTEXT, t.getParseContext());
        }
        jsonGenerator.writeEndObject();
    }
}
