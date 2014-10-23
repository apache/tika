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

import java.util.Arrays;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.tika.metadata.Metadata;

public class JsonMetadataBase {


    static Gson defaultInit() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeHierarchyAdapter(Metadata.class, new JsonMetadataSerializer());
        builder.registerTypeHierarchyAdapter(Metadata.class, new JsonMetadataDeserializer());
        return builder.create();
    }

    static Gson prettyInit() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeHierarchyAdapter(Metadata.class, new SortedJsonMetadataSerializer());
        builder.registerTypeHierarchyAdapter(Metadata.class, new JsonMetadataDeserializer());
        builder.setPrettyPrinting();
        return builder.create();
    }

    private static class SortedJsonMetadataSerializer extends JsonMetadataSerializer {
        @Override
        public String[] getNames(Metadata m) {
            String[] names = m.names();
            Arrays.sort(names, new PrettyMetadataKeyComparator());
            return names;
        }
    }
}
