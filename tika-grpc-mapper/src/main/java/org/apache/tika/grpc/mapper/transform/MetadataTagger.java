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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.protobuf.Timestamp;

import org.apache.tika.grpc.v1.BooleanValues;
import org.apache.tika.grpc.v1.Document;
import org.apache.tika.grpc.v1.IntegerValues;
import org.apache.tika.grpc.v1.MetadataField;
import org.apache.tika.grpc.v1.MetadataValue;
import org.apache.tika.grpc.v1.NumberValues;
import org.apache.tika.grpc.v1.StringValues;
import org.apache.tika.grpc.v1.TimestampValues;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.utils.DateUtils;

/**
 * Builds the tagged metadata tail. A Tika metadata entry is an array of values under
 * one key ({@link Metadata} is String[]-backed), so every tagged value is a typed
 * ARRAY: the tag comes from Tika's <em>declared</em> {@link Property} value type and
 * applies to every element -- a declared integer sequence like {@code pdf:charsPerPage}
 * arrives as int64s, one per page, not as strings. Keys with no declared type are
 * emitted as strings, never guessed -- so we never turn "8 8 8 8" into a broken
 * integer. If any element refuses its declared type, the whole entry falls back to
 * strings: lossless beats typed when the two conflict.
 */
public final class MetadataTagger {

    /**
     * {@link Property#get(String)} can only see Property constants whose declaring class
     * has already been initialized by the JVM. In the gRPC server the parse runs in a
     * forked pipes process, so nothing in the server JVM necessarily touches e.g.
     * {@code org.apache.tika.metadata.PDF} before mapping -- and declared types
     * ({@code pdf:encrypted} is a boolean, ...) would silently degrade to plain strings
     * depending on class-loading order. Force-initialize the tika-core property
     * vocabularies once so tagging is deterministic.
     */
    private static final Class<?>[] PROPERTY_VOCABULARIES = {
            org.apache.tika.metadata.AccessPermissions.class,
            org.apache.tika.metadata.ClimateForcast.class,
            org.apache.tika.metadata.CreativeCommons.class,
            org.apache.tika.metadata.Database.class,
            org.apache.tika.metadata.DublinCore.class,
            org.apache.tika.metadata.DWG.class,
            org.apache.tika.metadata.Epub.class,
            org.apache.tika.metadata.ExternalProcess.class,
            org.apache.tika.metadata.FileSystem.class,
            org.apache.tika.metadata.Font.class,
            org.apache.tika.metadata.Geographic.class,
            org.apache.tika.metadata.HTML.class,
            org.apache.tika.metadata.HttpHeaders.class,
            org.apache.tika.metadata.IPTC.class,
            org.apache.tika.metadata.MachineMetadata.class,
            org.apache.tika.metadata.MAPI.class,
            org.apache.tika.metadata.Message.class,
            org.apache.tika.metadata.Office.class,
            org.apache.tika.metadata.OfficeOpenXMLCore.class,
            org.apache.tika.metadata.OfficeOpenXMLExtended.class,
            org.apache.tika.metadata.PageAnchoring.class,
            org.apache.tika.metadata.PagedText.class,
            org.apache.tika.metadata.PDF.class,
            org.apache.tika.metadata.Photoshop.class,
            org.apache.tika.metadata.PST.class,
            org.apache.tika.metadata.QuattroPro.class,
            org.apache.tika.metadata.Rendering.class,
            org.apache.tika.metadata.RTFMetadata.class,
            org.apache.tika.metadata.TIFF.class,
            org.apache.tika.metadata.TikaCoreProperties.class,
            org.apache.tika.metadata.TikaPagedText.class,
            org.apache.tika.metadata.WARC.class,
            org.apache.tika.metadata.WordPerfect.class,
            org.apache.tika.metadata.XMP.class,
            org.apache.tika.metadata.XMPDC.class,
            org.apache.tika.metadata.XMPDM.class,
            org.apache.tika.metadata.XMPIdq.class,
            org.apache.tika.metadata.XMPMM.class,
            org.apache.tika.metadata.XMPPDF.class,
            org.apache.tika.metadata.XMPRights.class,
            org.apache.tika.metadata.Zip.class,
    };

    static {
        for (Class<?> vocabulary : PROPERTY_VOCABULARIES) {
            try {
                // A class literal alone does NOT initialize the class; forName(…, true, …) does.
                Class.forName(vocabulary.getName(), true, vocabulary.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    // The same date formats Metadata.getDate uses; DateUtils is not thread-safe, so
    // access is synchronized exactly as tika-core does (see TIKA-495).
    private static final DateUtils DATE_UTILS = new DateUtils();

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

    /**
     * Tag one key's value array by its declared Property element type; fall back to
     * strings for undeclared keys or elements the declared type cannot parse.
     */
    public static MetadataValue tag(Metadata tika, String key) {
        String[] raw = tika.getValues(key);
        if (raw == null || raw.length == 0) {
            return null;
        }
        List<String> values = new ArrayList<>(raw.length);
        for (String v : raw) {
            if (v != null && !v.trim().isEmpty()) {
                values.add(v.trim());
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        Property property = Property.get(key);
        if (property != null) {
            MetadataValue typed = tagByDeclaredType(property, values);
            if (typed != null) {
                return typed;
            }
        }
        StringValues.Builder strings = StringValues.newBuilder();
        strings.addAllValues(values);
        return MetadataValue.newBuilder().setStrings(strings).build();
    }

    private static MetadataValue tagByDeclaredType(Property property, List<String> values) {
        switch (property.getPrimaryProperty().getValueType()) {
            case INTEGER: {
                IntegerValues.Builder integers = IntegerValues.newBuilder();
                for (String v : values) {
                    try {
                        integers.addValues(Long.parseLong(v));
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
                return MetadataValue.newBuilder().setIntegers(integers).build();
            }
            case REAL:
            case RATIONAL: {
                NumberValues.Builder numbers = NumberValues.newBuilder();
                for (String v : values) {
                    try {
                        numbers.addValues(Double.parseDouble(v));
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
                return MetadataValue.newBuilder().setNumbers(numbers).build();
            }
            case BOOLEAN: {
                BooleanValues.Builder booleans = BooleanValues.newBuilder();
                for (String v : values) {
                    booleans.addValues(parseBoolean(v));
                }
                return MetadataValue.newBuilder().setBooleans(booleans).build();
            }
            case DATE: {
                TimestampValues.Builder timestamps = TimestampValues.newBuilder();
                for (String v : values) {
                    Date date = parseDate(v);
                    if (date == null) {
                        return null;
                    }
                    timestamps.addValues(toTimestamp(date));
                }
                return MetadataValue.newBuilder().setTimestamps(timestamps).build();
            }
            default:
                return null; // not a declared scalar type -> caller falls back to strings
        }
    }

    private static synchronized Date parseDate(String value) {
        return DATE_UTILS.tryToParse(value);
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
