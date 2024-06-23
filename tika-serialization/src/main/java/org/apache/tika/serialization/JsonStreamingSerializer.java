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
package org.apache.tika.serialization;


import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamReadConstraints;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;


public class JsonStreamingSerializer implements AutoCloseable {

    private final Writer writer;
    boolean hasStartedArray = false;
    private JsonGenerator jsonGenerator;

    public JsonStreamingSerializer(Writer writer) {
        this.writer = writer;
    }

    public void add(Metadata metadata) throws IOException {
        if (!hasStartedArray) {
            jsonGenerator = new JsonFactory()
                    .setStreamReadConstraints(StreamReadConstraints
                            .builder()
                            .maxStringLength(TikaConfig.getMaxJsonStringFieldLength())
                            .build())
                    .createGenerator(writer);
            jsonGenerator.writeStartArray();
            hasStartedArray = true;
        }
        String[] names = metadata.names();
        Arrays.sort(names);
        JsonMetadata.writeMetadataObject(metadata, jsonGenerator, false);
    }

    @Override
    public void close() throws IOException {
        jsonGenerator.writeEndArray();
        jsonGenerator.flush();
        jsonGenerator.close();
    }
}
