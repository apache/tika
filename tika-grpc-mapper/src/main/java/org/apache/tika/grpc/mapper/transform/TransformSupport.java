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
package org.apache.tika.grpc.mapper.transform;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.protobuf.Timestamp;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

/**
 * Helpers for transformers: set a typed Document field from a Tika Property and mark the
 * key consumed so it is not duplicated into the tagged tail.
 */
public final class TransformSupport {

    private TransformSupport() {
    }

    public static void setString(Metadata md, Property key, Consumer<String> setter, Set<String> consumed) {
        String v = md.get(key);
        if (v != null && !v.trim().isEmpty()) {
            setter.accept(v.trim());
            consumed.add(key.getName());
        }
    }

    public static void addStrings(Metadata md, Property key, Consumer<Iterable<String>> setter, Set<String> consumed) {
        String[] values = md.getValues(key);
        if (values != null && values.length > 0) {
            List<String> list = Arrays.stream(values)
                    .filter(s -> s != null && !s.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toList());
            if (!list.isEmpty()) {
                setter.accept(list);
                consumed.add(key.getName());
            }
        }
    }

    public static void setInt(Metadata md, Property key, Consumer<Integer> setter, Set<String> consumed) {
        Integer v = md.getInt(key);
        if (v != null) {
            setter.accept(v);
            consumed.add(key.getName());
        }
    }

    public static void setLong(Metadata md, Property key, Consumer<Long> setter, Set<String> consumed) {
        Integer v = md.getInt(key);
        if (v != null) {
            setter.accept(v.longValue());
            consumed.add(key.getName());
        }
    }

    public static void setDouble(Metadata md, Property key, Consumer<Double> setter, Set<String> consumed) {
        String v = md.get(key);
        if (v != null && !v.trim().isEmpty()) {
            try {
                setter.accept(Double.parseDouble(v.trim()));
                consumed.add(key.getName());
            } catch (NumberFormatException ignored) {
                // leave unconsumed; falls through to the tagged tail
            }
        }
    }

    public static void setTimestamp(Metadata md, Property key, Consumer<Timestamp> setter, Set<String> consumed) {
        Date d = md.getDate(key);
        if (d != null) {
            setter.accept(MetadataTagger.toTimestamp(d));
            consumed.add(key.getName());
        }
    }
}
