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

import com.google.gson.stream.JsonWriter;
import org.apache.tika.metadata.Metadata;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;


public class JsonStreamingSerializer implements AutoCloseable {

    private final JsonWriter jsonWriter;
    boolean hasStartedArray = false;
    public JsonStreamingSerializer(Writer writer) {
        this.jsonWriter = new JsonWriter(writer);
    }

    public void add(Metadata metadata) throws IOException {
        if (!hasStartedArray) {
            jsonWriter.beginArray();
            hasStartedArray = true;
        }
        String[] names = metadata.names();
        Arrays.sort(names);
        jsonWriter.beginObject();
        for (String n : names) {
            jsonWriter.name(n);
            String[] values = metadata.getValues(n);
            if (values.length == 1) {
                jsonWriter.value(values[0]);
            } else {
                jsonWriter.beginArray();
                for (String v : values) {
                    jsonWriter.value(v);
                }
                jsonWriter.endArray();
            }
        }
        jsonWriter.endObject();
    }

    @Override
    public void close() throws IOException {
        jsonWriter.endArray();
        jsonWriter.flush();
        jsonWriter.close();
    }
}
