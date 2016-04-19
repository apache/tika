package org.apache.tika.metadata.serialization;

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

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map;

import org.apache.tika.metadata.Metadata;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;


/**
 * Deserializer for Metadata
 *
 * If overriding this, remember that this is called from a static context.
 * Share state only with great caution.
 */
public class JsonMetadataDeserializer implements JsonDeserializer<Metadata> {

    /**
     * Deserializes a json object (equivalent to: Map<String, String[]>) 
     * into a Metadata object.
     * 
     * @param element to serialize
     * @param type (ignored)
     * @param context (ignored)
     * @return Metadata 
     * @throws JsonParseException if element is not able to be parsed
     */
    @Override
    public Metadata deserialize(JsonElement element, Type type,
            JsonDeserializationContext context) throws JsonParseException {

        final JsonObject obj = element.getAsJsonObject();
        Metadata m = new Metadata();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()){
            String key = entry.getKey();
            JsonElement v = entry.getValue();
            if (v.isJsonPrimitive()){
                m.set(key, v.getAsString());
            } else if (v.isJsonArray()){
                JsonArray vArr = v.getAsJsonArray();
                Iterator<JsonElement> itr = vArr.iterator();
                while (itr.hasNext()){
                    JsonElement valueItem = itr.next();
                    m.add(key, valueItem.getAsString());
                }

            }
        }
        return m;
    }
}
