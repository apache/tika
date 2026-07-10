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

import org.apache.tika.grpc.v1.DocumentMetadata;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Helpers for transformers: set a typed Document field from a Tika Property and mark the
 * key consumed so it is not duplicated into the tagged tail.
 */
public final class TransformSupport {

    private TransformSupport() {
    }

    /**
     * Maps the common, cross-format fields -- title, description, authors, keywords,
     * languages, publishers, identifiers, created, modified -- into typed
     * {@link DocumentMetadata}. These are the Dublin Core descriptive elements; keywords
     * come from {@link TikaCoreProperties#SUBJECT}. Kept in one place so the common
     * mapping cannot drift between format transformers.
     */
    public static void mapCommonFields(Metadata md, DocumentMetadata.Builder meta, Set<String> consumed) {
        mapCommonFields(md, meta, consumed, TikaCoreProperties.SUBJECT);
    }

    /**
     * Same as {@link #mapCommonFields(Metadata, DocumentMetadata.Builder, Set)} but with
     * the keyword source overridden, for formats whose keyword bag lives under a
     * different property (e.g. Office and RTF use {@code meta:keyword}); {@code dc:subject}
     * then stays in the tagged tail because those formats treat it as a distinct field.
     */
    public static void mapCommonFields(Metadata md, DocumentMetadata.Builder meta, Set<String> consumed,
                                       Property keywordsProperty) {
        setString(md, TikaCoreProperties.TITLE, meta::setTitle, consumed);
        setString(md, TikaCoreProperties.DESCRIPTION, meta::setDescription, consumed);
        addStrings(md, TikaCoreProperties.CREATOR, meta::addAllAuthors, consumed);
        addStrings(md, keywordsProperty, meta::addAllKeywords, consumed);
        addStrings(md, TikaCoreProperties.LANGUAGE, meta::addAllLanguages, consumed);
        addStrings(md, TikaCoreProperties.PUBLISHER, meta::addAllPublishers, consumed);
        addStrings(md, TikaCoreProperties.IDENTIFIER, meta::addAllIdentifiers, consumed);
        setTimestamp(md, TikaCoreProperties.CREATED, meta::setCreated, consumed);
        setTimestamp(md, TikaCoreProperties.MODIFIED, meta::setModified, consumed);
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
