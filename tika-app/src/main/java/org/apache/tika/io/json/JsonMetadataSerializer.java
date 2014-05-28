package org.apache.tika.io.json;

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
import java.util.Arrays;

import org.apache.tika.metadata.Metadata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;


public class JsonMetadataSerializer implements JsonSerializer<Metadata> {

    /**
     * 
     * @param metadata
     * @param type
     * @param context
     * @return JsonObject with key/value(s) pairs or JsonNull if metadata is null.
     */
    @Override
    public JsonElement serialize(Metadata metadata, Type type, JsonSerializationContext context) {
        if (metadata == null){
            return new JsonNull();
        }
        String[] names = getNames(metadata);
        if (names == null) {
            return new JsonNull();
        }

        JsonObject root = new JsonObject();

        for (String n : names) {
            
            String[] vals = metadata.getValues(n);
            if (vals == null) {
                //silently skip?
                continue;
            }
            
            if (vals.length == 1) {
                root.addProperty(n, vals[0]);
            } else {
                JsonArray jArr = new JsonArray();
                for (int i = 0; i < vals.length; i++) {
                    jArr.add(new JsonPrimitive(vals[i]));
                }
                root.add(n, jArr);
            }
        }
        return root;
    }
    
    /**
     * Override to get a custom sort order
     * or to filter names.
     * 
     * @param metadata
     * @return
     */
    protected String[] getNames(Metadata metadata) {
        String[] names = metadata.names();
        Arrays.sort(names);
        return names;
    }

}
