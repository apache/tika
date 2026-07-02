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

import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

import com.google.protobuf.Timestamp;

import org.apache.tika.grpc.v1.Document;
import org.apache.tika.grpc.v1.MetadataField;
import org.apache.tika.grpc.v1.MetadataValue;
import org.apache.tika.grpc.v1.StringList;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

/**
 * Builds the tagged metadata tail. The tag comes from Tika's <em>declared</em>
 * {@link Property} value type. Keys with no declared type are emitted as strings,
 * never guessed - so we never turn "8 8 8 8" into a broken integer.
 */
public final class MetadataTagger {

    private MetadataTagger() {
    }

    /** Append every key not already consumed by a typed field into {@code Document.extra}. */
    public static void appendTail(Metadata tika, Set<String> consumed, Document.Builder document) {
        for (String key : tika.names()) {
            if (consumed.contains(key)) {
                continue;
            }
            MetadataValue value = tag(tika, key);
            if (value != null) {
                document.addExtra(MetadataField.newBuilder().setKey(key).setValue(value).build());
            }
        }
    }

    /** Tag one key by its declared Property type; fall back to multivalue strings. */
    public static MetadataValue tag(Metadata tika, String key) {
        String[] values = tika.getValues(key);
        if (values == null || values.length == 0) {
            return null;
        }
        Property property = Property.get(key);
        if (values.length == 1 && property != null) {
            MetadataValue typed = tagByDeclaredType(tika, property, values[0].trim());
            if (typed != null) {
                return typed;
            }
        }
        StringList.Builder strings = StringList.newBuilder();
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                strings.addValues(v.trim());
            }
        }
        return strings.getValuesCount() == 0
                ? null
                : MetadataValue.newBuilder().setStrings(strings).build();
    }

    private static MetadataValue tagByDeclaredType(Metadata tika, Property property, String raw) {
        switch (property.getPrimaryProperty().getValueType()) {
            case INTEGER:
                try {
                    return MetadataValue.newBuilder().setInteger(Long.parseLong(raw)).build();
                } catch (NumberFormatException ignored) {
                    return null;
                }
            case REAL:
            case RATIONAL:
                try {
                    return MetadataValue.newBuilder().setNumber(Double.parseDouble(raw)).build();
                } catch (NumberFormatException ignored) {
                    return null;
                }
            case BOOLEAN:
                return MetadataValue.newBuilder().setBoolean(parseBoolean(raw)).build();
            case DATE:
                Date date = tika.getDate(property);
                return date == null
                        ? null
                        : MetadataValue.newBuilder().setTimestamp(toTimestamp(date)).build();
            default:
                return null; // not a declared scalar -> caller falls back to strings
        }
    }

    static boolean parseBoolean(String v) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        return "true".equals(s) || "yes".equals(s) || "1".equals(s);
    }

    static Timestamp toTimestamp(Date date) {
        Instant i = date.toInstant();
        return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
    }
}
