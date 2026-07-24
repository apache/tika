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
import org.apache.tika.grpc.mapper.transform.DocumentTransformers;
import org.apache.tika.grpc.v1.Document;
import org.apache.tika.grpc.v1.ParseStatus;
import org.apache.tika.grpc.v1.SourceOrigin;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Builds a {@link Document} from Tika's parse output: the envelope (content type,
 * origin, status), the typed Dublin Core metadata via {@link DocumentTransformers},
 * and the lossless tagged tail for everything else.
 */
public final class DocumentBuilder {

    private static final DocumentTransformers TRANSFORMERS = DocumentTransformers.defaults();

    // The metadata key DigestDef.metadataKey() produces for SHA-256 with the default hex
    // encoding; the parse pipeline records the source digest there when a digester is
    // configured.
    private static final String SHA256_DIGEST_KEY = TikaCoreProperties.TIKA_META_PREFIX
            + "digest" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "SHA256";

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

        if (primary == null) {
            if (status.getStatus() != ParseStatus.Status.SUCCESS) {
                status.addErrors("No metadata returned from parse");
            }
            return document.setStatus(status.build()).build();
        }

        // Keys the envelope maps below are consumed up front so the tagged tail never
        // carries them a second time. X-TIKA:content is consumed without a typed home
        // yet: the reply's fields map still carries the flat content, and the structured
        // content tree is a planned additive follow-up -- duplicating the whole body
        // into `extra` as a string would defeat both.
        Set<String> consumed = new HashSet<>();
        consumed.add(TikaCoreProperties.TIKA_CONTENT.getName());

        String contentType = primary.get(Metadata.CONTENT_TYPE);
        if (contentType != null && !contentType.isBlank()) {
            document.setContentType(contentType.trim());
            consumed.add(Metadata.CONTENT_TYPE);
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
        String contentLength = primary.get(Metadata.CONTENT_LENGTH);
        if (contentLength != null && !contentLength.isBlank()) {
            try {
                origin.setByteSize(Long.parseLong(contentLength.trim()));
                consumed.add(Metadata.CONTENT_LENGTH);
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
     * a clean success (e.g. {@code PARSE_SUCCESS}, {@code EMIT_SUCCESS}) is {@code SUCCESS};
     * a success that happened alongside a caught exception (e.g.
     * {@code PARSE_SUCCESS_WITH_EXCEPTION}) is {@code PARTIAL}; everything else -- including
     * process crashes like {@code TIMEOUT} and {@code OOM}, which are not partial successes --
     * is {@code FAILED}.
     */
    private static ParseStatus.Status mapPipesStatus(String pipesStatus) {
        if (pipesStatus == null || pipesStatus.isEmpty()) {
            return ParseStatus.Status.UNSPECIFIED;
        }
        return switch (pipesStatus) {
            case "EMPTY_OUTPUT", "PARSE_SUCCESS", "EMIT_SUCCESS", "EMIT_SUCCESS_PASSBACK" ->
                    ParseStatus.Status.SUCCESS;
            case "PARSE_SUCCESS_WITH_EXCEPTION", "PARSE_EXCEPTION_NO_EMIT", "EMIT_SUCCESS_PARSE_EXCEPTION" ->
                    ParseStatus.Status.PARTIAL;
            default -> ParseStatus.Status.FAILED;
        };
    }
}
