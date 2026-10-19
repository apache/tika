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

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

/**
 * Value types of metadata keys for typed JSON output.
 * <p>
 * The {@link Property} registry knows a key only once its declaring class has loaded in this JVM.
 * A pipes parent (tika-server's {@code /rmeta}, for one) serializes metadata from parsers and
 * plugins it never loads, so the fork reports the types of the keys it wrote
 * ({@link #describe}) and the parent records them ({@link #learn}) before serializing.
 */
public final class MetadataValueTypes {

    // bounds what a misbehaving fork can add
    static final int MAX_LEARNED = 10_000;

    private static final Set<Property.ValueType> TYPED = EnumSet.of(Property.ValueType.BOOLEAN,
            Property.ValueType.INTEGER, Property.ValueType.REAL, Property.ValueType.JSON);

    private static final Map<String, Property.ValueType> LEARNED = new ConcurrentHashMap<>();

    private MetadataValueTypes() {
    }

    /**
     * @return the key's type from its {@link Property} declaration in this JVM, else as reported
     * by a fork, else null
     */
    public static Property.ValueType get(String key) {
        Property property = Property.get(key);
        return property != null ? property.getValueType() : LEARNED.get(key);
    }

    /**
     * @return the keys in the list that typed output writes as other than strings, with their
     * types
     */
    public static Map<String, Property.ValueType> describe(List<Metadata> metadataList) {
        Map<String, Property.ValueType> types = new TreeMap<>();
        for (Metadata metadata : metadataList) {
            for (String name : metadata.names()) {
                Property.ValueType type = get(name);
                if (type != null && TYPED.contains(type)) {
                    types.put(name, type);
                }
            }
        }
        return types;
    }

    /**
     * Records a type reported by a fork. Ignored when this JVM declares the key itself, the key
     * was already learned, the type name is unknown or written as a string, or the cap is reached.
     */
    public static void learn(String key, String typeName) {
        if (Property.get(key) != null || LEARNED.size() >= MAX_LEARNED) {
            return;
        }
        Property.ValueType type;
        try {
            type = Property.ValueType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (TYPED.contains(type)) {
            LEARNED.putIfAbsent(key, type);
        }
    }
}
