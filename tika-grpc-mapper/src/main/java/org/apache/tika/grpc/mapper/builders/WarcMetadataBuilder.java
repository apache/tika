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
package org.apache.tika.grpc.mapper.builders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;

import org.apache.tika.grpc.v1.BaseFields;
import org.apache.tika.grpc.v1.WarcHttpHeader;
import org.apache.tika.grpc.v1.WarcMetadata;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.WARC;

/**
 * Builds {@link WarcMetadata} protobuf messages from Tika {@link Metadata} for WARC web-archive
 * records. Maps WARC record fields, HTTP fields, content-analysis fields, and archive-processing
 * fields into strongly-typed protobuf fields, collects all remaining keys into an additional-metadata
 * {@link Struct}, and populates the shared base fields.
 */
public class WarcMetadataBuilder {

    /**
     * Creates a new {@code WarcMetadataBuilder}.
     */
    public WarcMetadataBuilder() {
    }

    /**
     * Builds a {@link WarcMetadata} message from the given Tika metadata. Maps WARC, HTTP,
     * content-analysis, and archive-processing fields into strongly-typed protobuf fields, places
     * every key not mapped (and not in {@code excludedKeys}) into the additional-metadata struct, and
     * attaches base fields describing the parser and Tika version.
     *
     * @param md the Tika metadata extracted from the WARC record
     * @param parserClass the Tika parser class name used to parse the record
     * @param tikaVersion the Tika version used
     * @param excludedKeys keys already consumed by other shared builders, to be treated as mapped
     * @return the populated {@link WarcMetadata} message
     */
    public static WarcMetadata build(Metadata md, String parserClass, String tikaVersion, Set<String> excludedKeys) {
        WarcMetadata.Builder b = WarcMetadata.newBuilder();
        Set<String> mapped = new HashSet<>(excludedKeys);

        mapWarc(md, b, mapped);
        mapHttp(md, b, mapped);
        mapContentAnalysis(md, b, mapped);
        mapArchiveProcessing(md, b, mapped);

        Struct additional = MetadataUtils.buildAdditionalMetadata(md, mapped);
        b.setAdditionalMetadata(additional);

        BaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, md);
        b.setBaseFields(base);

        return b.build();
    }

    private static void mapWarc(Metadata md, WarcMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapRepeatedStringField(md, WARC.WARC_WARNING, b::addAllWarcWarnings, mapped);
        MetadataUtils.mapStringField(md, WARC.WARC_RECORD_CONTENT_TYPE, b::setWarcRecordContentType, mapped);
        MetadataUtils.mapStringField(md, WARC.WARC_PAYLOAD_CONTENT_TYPE, b::setWarcPayloadContentType, mapped);
        MetadataUtils.mapStringField(md, WARC.WARC_RECORD_ID, b::setWarcRecordId, mapped);
        // Also map from literal header if present
        MetadataUtils.mapStringField(md, "warc:WARC-Record-ID", b::setWarcRecordId, mapped);

        // Core headers as literal keys from WARCParser
        MetadataUtils.mapStringField(md, "warc:WARC-Type", b::setWarcType, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Target-URI", b::setWarcTargetUri, mapped);
        mapTimestampFromString(md, "warc:WARC-Date", b::setWarcDate, mapped);
        MetadataUtils.mapLongField(md, Metadata.CONTENT_LENGTH, b::setWarcContentLength, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Filename", b::setWarcFilename, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Refers-To", b::setWarcRefersTo, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Concurrent-To", b::setWarcConcurrentTo, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Warcinfo-ID", b::setWarcWarcinfoId, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-IP-Address", b::setWarcIpAddress, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Block-Digest", b::setWarcBlockDigest, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Payload-Digest", b::setWarcPayloadDigest, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Truncated", b::setWarcTruncated, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Identified-Payload-Type", b::setWarcIdentifiedPayloadType, mapped);
    }

    private static void mapHttp(Metadata md, WarcMetadata.Builder b, Set<String> mapped) {
        // Status
        MetadataUtils.mapIntField(md, "warc:http:status", b::setHttpStatusCode, mapped);
        MetadataUtils.mapStringField(md, "warc:http:status:reason", b::setHttpStatusReason, mapped);

        // Headers (collect all warc:http:* excluding status fields)
        List<WarcHttpHeader> headers = new ArrayList<>();
        for (String name : md.names()) {
            if (!name.startsWith("warc:http:")) continue;
            if (name.equals("warc:http:status") || name.equals("warc:http:status:reason")) continue;
            for (String v : md.getValues(name)) {
                WarcHttpHeader h = WarcHttpHeader.newBuilder()
                        .setName(name.substring("warc:http:".length()))
                        .setValue(v)
                        .build();
                headers.add(h);
            }
        }
        if (!headers.isEmpty()) {
            b.addAllHttpHeaders(headers);
        }
    }

    private static void mapContentAnalysis(Metadata md, WarcMetadata.Builder b, Set<String> mapped) {
        // Content language / encoding might be under various keys; map standard ones if present
        MetadataUtils.mapStringField(md, "Content-Language", b::setContentLanguage, mapped);
        MetadataUtils.mapStringField(md, "Content-Encoding", b::setContentEncoding, mapped);
    }

    private static void mapArchiveProcessing(Metadata md, WarcMetadata.Builder b, Set<String> mapped) {
        // Best-effort mapping; many of these may not be present
        MetadataUtils.mapStringField(md, "warc:software", b::setWarcCreatedBy, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Format", b::setWarcFormatVersion, mapped);
        MetadataUtils.mapStringField(md, "warc:collection", b::setWarcCollection, mapped);
        MetadataUtils.mapStringField(md, "warc:crawl", b::setWarcCrawlId, mapped);
        MetadataUtils.mapStringField(md, "warc:robots", b::setWarcRobotPolicy, mapped);
    }

    private static void mapTimestampFromString(Metadata md, String key, java.util.function.Consumer<Timestamp> setter, Set<String> mapped) {
        String v = md.get(key);
        if (v == null || v.trim().isEmpty()) return;
        try {
            Instant i = Instant.parse(v.trim());
            setter.accept(Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build());
            mapped.add(key);
        } catch (Exception ignore) {
        }
    }
}
