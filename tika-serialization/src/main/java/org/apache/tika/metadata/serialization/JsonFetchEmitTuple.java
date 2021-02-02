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
package org.apache.tika.metadata.serialization;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.apache.tika.utils.StringUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public class JsonFetchEmitTuple {

    private static final String FETCHER = "fetcher";
    private static final String FETCHKEY = "fetchKey";
    private static final String EMITTER = "emitter";
    private static final String EMITKEY = "emitKey";
    private static final String METADATAKEY = "metadata";

    public static FetchEmitTuple fromJson(Reader reader) throws IOException {
        JsonParser jParser = new JsonFactory().createParser(reader);
        JsonToken token =jParser.nextToken();
        if (token != JsonToken.START_OBJECT) {
            throw new IOException("require start object, but see: "+token.name());
        }
        return parseFetchEmitTuple(jParser);
    }


    private static FetchEmitTuple parseFetchEmitTuple(JsonParser jParser) throws IOException {
        JsonToken token = jParser.nextToken();
        String fetcherName = null;
        String fetchKey = null;
        String emitterName = null;
        String emitKey = null;
        Metadata metadata = new Metadata();
        while (token != JsonToken.END_OBJECT) {
            if (token != JsonToken.FIELD_NAME) {
                throw new IOException("required field name, but see: " + token.name());
            }
            String name = jParser.getCurrentName();
            if (FETCHER.equals(name)) {
                fetcherName = getValue(jParser);
            } else if (FETCHKEY.equals(name)) {
                fetchKey = getValue(jParser);
            } else if (EMITTER.equals(name)) {
                emitterName = getValue(jParser);
            } else if (EMITKEY.equals(name)) {
                emitKey = getValue(jParser);
            } else if (METADATAKEY.equals(name)) {
                token = jParser.nextToken();
                if (token != JsonToken.START_OBJECT) {
                    throw new IOException("required start object, but see: " + token.name());
                }
                metadata = JsonMetadata.readMetadataObject(jParser);
            }
            token = jParser.nextToken();
        }

        return new FetchEmitTuple(
                new FetchKey(fetcherName, fetchKey),
                new EmitKey(emitterName, emitKey), metadata
        );
    }

    private static String getValue(JsonParser jParser) throws IOException {
        JsonToken token = jParser.nextToken();
        if (token != JsonToken.VALUE_STRING) {
            throw new IOException("required value string, but see: "+token.name());
        }
        return jParser.getValueAsString();
    }

    public static void toJson(FetchEmitTuple t, Writer writer) throws IOException {

        try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField(FETCHER, t.getFetchKey().getFetcherName());
            jsonGenerator.writeStringField(FETCHKEY, t.getFetchKey().getKey());
            jsonGenerator.writeStringField(EMITTER, t.getEmitKey().getEmitterName());
            if (!StringUtils.isBlank(t.getEmitKey().getKey())) {
                jsonGenerator.writeStringField(EMITKEY, t.getEmitKey().getKey());
            }
            if (t.getMetadata().size() > 0) {
                jsonGenerator.writeFieldName(METADATAKEY);
                JsonMetadata.writeMetadataObject(t.getMetadata(), jsonGenerator, false);
            }
            jsonGenerator.writeEndObject();
        }
    }
}
