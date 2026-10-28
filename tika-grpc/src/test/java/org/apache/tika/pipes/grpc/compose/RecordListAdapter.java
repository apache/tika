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
package org.apache.tika.pipes.grpc.compose;

import java.time.Instant;
import java.util.Date;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import io.grpc.MethodDescriptor;

import org.apache.tika.grpc.adapter.AdapterContribution;
import org.apache.tika.grpc.adapter.AdapterRequest;
import org.apache.tika.grpc.adapter.ExternalParserAdapter;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.pipes.grpc.compose.records.Header;
import org.apache.tika.pipes.grpc.compose.records.ListRecordsRequest;
import org.apache.tika.pipes.grpc.compose.records.ListRecordsResponse;
import org.apache.tika.pipes.grpc.compose.records.Record;
import org.apache.tika.pipes.grpc.compose.records.RecordError;
import org.apache.tika.pipes.grpc.compose.records.RecordListServiceGrpc;

/**
 * The test adapter: maps {@link FakeRecordList}'s replies, over the test-only
 * {@code tika.test.v1} stubs, as a real adapter jar would map its parser's. Each WARC record
 * becomes a child {@code /record-N} keyed as Tika's WARCParser keys records; the parent gets
 * {@code warc:record-count}. Its one option is {@code strict}, a boolean.
 */
public class RecordListAdapter
        implements ExternalParserAdapter<ListRecordsRequest, ListRecordsResponse> {

    public static final String PRODUCER_ID = "records";

    static final Property RECORD_COUNT = Property.externalInteger("warc:record-count");

    static final Property STREAM_POS = Property.externalInteger("warc:stream-pos");

    private static final Set<MediaType> TYPES = Set.of(
            MediaType.application("warc"), MediaType.application("warc+gz"));

    @Override
    public String producerId() {
        return PRODUCER_ID;
    }

    @Override
    public Set<MediaType> supportedTypes() {
        return TYPES;
    }

    @Override
    public MethodDescriptor<ListRecordsRequest, ListRecordsResponse> method() {
        return RecordListServiceGrpc.getListRecordsMethod();
    }

    @Override
    public String keyPrefix() {
        return "warc:";
    }

    @Override
    public void validateOptions(JsonNode options) {
        options.fieldNames().forEachRemaining(name -> {
            if (!name.equals("strict") || !options.get(name).isBoolean()) {
                throw new IllegalArgumentException("records takes only a boolean 'strict', got: "
                        + name);
            }
        });
    }

    @Override
    public ListRecordsRequest buildRequest(AdapterRequest request) {
        return ListRecordsRequest.newBuilder()
                .setArchive(request.content())
                .setStrict(request.options().path("strict").asBoolean(false))
                .build();
    }

    @Override
    public AdapterContribution map(ListRecordsResponse reply, AdapterRequest request) {
        AdapterContribution.Builder contribution = request.contribution()
                .parentKey(RECORD_COUNT.getName(), Integer.toString(reply.getRecordsCount()));
        for (int i = 0; i < reply.getRecordsCount(); i++) {
            contribution.addChild("/record-" + (i + 1), record(reply.getRecords(i)));
        }
        for (RecordError error : reply.getErrorsList()) {
            String message = "record at byte " + error.getStreamPos() + ": " + error.getMessage();
            if (error.getRecoverable()) {
                contribution.warn(message);
            } else if (reply.getRecordsCount() > 0) {
                contribution.partialFailure(message);
            } else {
                contribution.fail(message);
            }
        }
        return contribution.build();
    }

    private static Metadata record(Record record) {
        Metadata metadata = new Metadata();
        for (Header header : record.getWarcHeadersList()) {
            metadata.add("warc:" + header.getName(), header.getValue());
            if (header.getName().equalsIgnoreCase("WARC-Record-ID")) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, header.getValue());
            }
        }
        if (record.hasDate()) {
            metadata.set(TikaCoreProperties.CREATED, Date.from(Instant.ofEpochSecond(
                    record.getDate().getSeconds(), record.getDate().getNanos())));
        }
        metadata.set(STREAM_POS, Long.toString(record.getStreamPos()));
        if (!record.getHttpStatusLine().isEmpty()) {
            String[] parts = record.getHttpStatusLine().trim().split(" ", 3);
            if (parts.length >= 2) {
                metadata.set("warc:http:status", parts[1]);
            }
            if (parts.length == 3) {
                metadata.set("warc:http:status:reason", parts[2].trim());
            }
        }
        for (Header header : record.getHttpHeadersList()) {
            metadata.add("warc:http:" + header.getName(), header.getValue());
            if (header.getName().equalsIgnoreCase("Content-Type")) {
                metadata.set(HttpHeaders.CONTENT_TYPE, header.getValue());
            }
        }
        return metadata;
    }
}
