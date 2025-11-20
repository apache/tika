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

import org.apache.tika.metadata.Metadata;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class MetadataDeserializer extends JsonDeserializer<Metadata> {

    @Override
    public Metadata deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws IOException, JacksonException {
        Metadata metadata = new Metadata();
        boolean keepGoing = true;
        while (keepGoing) {
            keepGoing = addField(jsonParser, metadata);
        }
        return metadata;
    }

    private boolean addField(JsonParser jsonParser, Metadata metadata) throws IOException {
        String field = jsonParser.nextFieldName();
        if (field == null) {
            return false;
        }
        JsonToken token = jsonParser.nextValue();

        if (token == null) {
            return false;
        }

        if (token.isScalarValue()) {
            metadata.set(field, jsonParser.getText());
        } else if (jsonParser.isExpectedStartArrayToken()) {
            token = jsonParser.nextToken();
            while (token != null) {
                if (token == JsonToken.END_ARRAY) {
                    return true;
                } else if (token.isScalarValue()) {
                    metadata.add(field, jsonParser.getText());
                } else {
                    break;
                }
                token = jsonParser.nextToken();
            }
        } else {
            return false;
        }
        return true;
    }
}
