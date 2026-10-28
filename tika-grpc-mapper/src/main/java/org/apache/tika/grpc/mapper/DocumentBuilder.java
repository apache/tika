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
package org.apache.tika.grpc.mapper;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.Tika;
import org.apache.tika.digest.DigestDef;
import org.apache.tika.grpc.mapper.transform.DocumentTransformers;
import org.apache.tika.grpc.v2.Document;
import org.apache.tika.grpc.v2.ParseStatus;
import org.apache.tika.grpc.v2.SourceOrigin;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Builds a {@link Document} from Tika's parse output: the envelope (content type,
 * origin, status), the typed Dublin Core metadata via {@link DocumentTransformers},
 * and the tagged tail for the remaining metadata.
 */
public final class DocumentBuilder {

    private static final DocumentTransformers TRANSFORMERS = DocumentTransformers.defaults();

    // The key the parse pipeline records the source digest under when a digester is
    // configured. Derived from DigestDef so it cannot drift from the producer: a
    // hand-rolled "...SHA256" copy of it did, silently, when the algorithm keys moved
    // to their Java names ("SHA-256").
    private static final String SHA256_DIGEST_KEY =
            new DigestDef(DigestDef.Algorithm.SHA256, DigestDef.Encoding.HEX).metadataKey();

    private DocumentBuilder() {
    }

    /**
     * Maps Tika parse output to {@link Document}. Pass {@code primary == null} when the
     * pipes result carried no metadata at all (e.g. a fetch failure, or a crash before
     * the parse produced anything): the returned Document then has only an id and a
     * status. The status still derives from {@code pipesStatus} -- {@code EMPTY_OUTPUT}
     * with no metadata is a legitimate success -- and an explicit error is recorded for
     * the non-success cases.
     */
    public static Document build(Metadata primary, String docId, String pipesStatus,
                                  long fetchParseTimeMs) {
        return build(primary, docId, pipesStatus, null, fetchParseTimeMs);
    }

    /**
     * {@link #build(Metadata, String, String, long)} with the error the pipes runtime
     * reported for this parse (its message, or the container stack trace of a parse that
     * succeeded alongside an exception). A non-blank {@code error} is recorded in
     * {@code ParseStatus.errors} whatever the status, so a failed or partial Document says
     * why.
     */
    public static Document build(Metadata primary, String docId, String pipesStatus,
                                  String error, long fetchParseTimeMs) {
        ParseStatus.Builder status = ParseStatus.newBuilder()
                .setStatus(mapPipesStatus(pipesStatus))
                .setFetchParseTimeMs(fetchParseTimeMs)
                .setTikaVersion(Tika.getString());
        if (pipesStatus != null && !pipesStatus.isEmpty()) {
            status.setPipesStatus(pipesStatus);
        }

        Document.Builder document = Document.newBuilder();
        if (docId != null && !docId.isEmpty()) {
            document.setId(docId);
        }

        boolean hasError = error != null && !error.isBlank();
        if (hasError) {
            status.addErrors(error.strip());
        } else if (primary != null
                && (status.getStatus() == ParseStatus.Status.STATUS_FAILED
                || status.getStatus() == ParseStatus.Status.STATUS_PARTIAL)) {
            // A failed or partial Document always says why, even when pipes gave no message.
            status.addErrors("parse ended with pipes status " + pipesStatus);
        }

        if (primary == null) {
            if (!hasError && status.getStatus() != ParseStatus.Status.STATUS_SUCCESS) {
                status.addErrors("No metadata returned from parse");
            }
            return document.setStatus(status.build()).build();
        }

        // Keys the envelope maps below are consumed up front so the tagged tail never
        // carries them a second time. tk:content is consumed too: the Document does not
        // carry the extracted text, and copying the whole body into `extra` as a string
        // would bloat every reply.
        Set<String> consumed = new HashSet<>();
        consumed.add(TikaCoreProperties.TIKA_CONTENT.getName());

        String contentType = primary.get(HttpHeaders.CONTENT_TYPE);
        if (contentType != null && !contentType.isBlank()) {
            document.setContentType(contentType.trim());
            consumed.add(HttpHeaders.CONTENT_TYPE.getName());
        }

        Instant now = Instant.now();
        document.setParsedAt(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build());

        SourceOrigin.Builder origin = SourceOrigin.newBuilder();
        String resourceName = primary.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (resourceName != null && !resourceName.isBlank()) {
            origin.setFilename(resourceName.trim());
            consumed.add(TikaCoreProperties.RESOURCE_NAME_KEY.getName());
        }
        String contentLength = primary.get(HttpHeaders.CONTENT_LENGTH);
        if (contentLength != null && !contentLength.isBlank()) {
            try {
                origin.setByteSize(Long.parseLong(contentLength.trim()));
                consumed.add(HttpHeaders.CONTENT_LENGTH.getName());
            } catch (NumberFormatException ignored) {
                // leave unconsumed; falls through to the tagged tail
            }
        }
        String parserClass = primary.get(TikaCoreProperties.TIKA_PARSED_BY);
        if (parserClass != null && !parserClass.isBlank()) {
            origin.setParser(parserClass.trim());
        }
        String sha256 = primary.get(SHA256_DIGEST_KEY);
        if (sha256 != null && !sha256.isBlank()) {
            origin.setSha256(sha256.trim());
            consumed.add(SHA256_DIGEST_KEY);
        }
        document.setOrigin(origin.build());

        String[] parsedByFull = primary.getValues(TikaCoreProperties.TIKA_PARSED_BY_FULL_SET);
        if (parsedByFull == null || parsedByFull.length == 0) {
            parsedByFull = primary.getValues(TikaCoreProperties.TIKA_PARSED_BY);
        }
        for (String parser : parsedByFull) {
            if (parser != null && !parser.isBlank()) {
                status.addParsersUsed(parser.trim());
            }
        }
        consumed.add(TikaCoreProperties.TIKA_PARSED_BY.getName());
        consumed.add(TikaCoreProperties.TIKA_PARSED_BY_FULL_SET.getName());
        document.setStatus(status.build());

        TRANSFORMERS.transform(primary, document, consumed);

        return document.build();
    }

    /**
     * Maps {@code org.apache.tika.pipes.api.PipesResult.RESULT_STATUS.name()} to
     * {@link ParseStatus.Status}. Values are named after the real enum constants (not
     * guessed) since this module intentionally has no compile dependency on tika-pipes-api:
     * a clean success (e.g. {@code PARSE_SUCCESS}, {@code EMIT_SUCCESS}) is
     * {@code STATUS_SUCCESS}; a success that happened alongside a caught exception (e.g.
     * {@code PARSE_SUCCESS_WITH_EXCEPTION}) or that the deadline cut short but still emitted
     * ({@code PARTIAL_TIMEOUT}) is {@code STATUS_PARTIAL}; everything else -- including process
     * crashes like {@code TIMEOUT} and {@code OOM}, which are not partial successes -- is
     * {@code STATUS_FAILED}. {@code PARSE_EXCEPTION_NO_EMIT} is in the pipes success category
     * but is {@code STATUS_FAILED} here: it is a parse that threw under
     * {@code ON_PARSE_EXCEPTION.SKIP} (what tika-grpc sends), and it carries no metadata, so
     * there is nothing partial to return.
     */
    private static ParseStatus.Status mapPipesStatus(String pipesStatus) {
        if (pipesStatus == null || pipesStatus.isEmpty()) {
            return ParseStatus.Status.STATUS_UNSPECIFIED;
        }
        return switch (pipesStatus) {
            case "EMPTY_OUTPUT", "PARSE_SUCCESS", "EMIT_SUCCESS", "EMIT_SUCCESS_PASSBACK" ->
                    ParseStatus.Status.STATUS_SUCCESS;
            case "PARSE_SUCCESS_WITH_EXCEPTION", "EMIT_SUCCESS_PARSE_EXCEPTION", "PARTIAL_TIMEOUT" ->
                    ParseStatus.Status.STATUS_PARTIAL;
            default -> ParseStatus.Status.STATUS_FAILED;
        };
    }
}
