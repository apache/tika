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
import java.util.List;

import org.apache.tika.Tika;
import org.apache.tika.grpc.mapper.content.MarkdownBlockTreeBuilder;
import org.apache.tika.grpc.mapper.transform.DocumentTransformers;
import org.apache.tika.grpc.mapper.transform.FormatCategoryDetector;
import org.apache.tika.grpc.v1.Document;
import org.apache.tika.grpc.v1.ParseStatus;
import org.apache.tika.grpc.v1.SourceOrigin;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Builds a {@link Document} from Tika's parse output: the pipes default content handler
 * renders the extracted content as markdown, so this class parses that markdown into
 * the structured {@code Block} tree once (format-agnostic) and delegates metadata
 * mapping to {@link DocumentTransformers} (format-specific, one transformer per
 * concern). Embedded documents recurse into {@link Document#getEmbeddedList()}.
 *
 * <p>The block tree is the canonical content and is always populated. The flat
 * {@code markdown} field is a rendering of the same content and is only carried when
 * the caller asks for it, so a reply does not ship the content twice.</p>
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
     * Maps Tika parse output (primary metadata plus optional embedded metadata list) to
     * {@link Document}. Pass {@code primary == null} when the pipes result carried no
     * metadata at all (e.g. a fetch failure, or a crash before the parse produced
     * anything): the returned Document then has only an id and a status. The status still
     * derives from {@code pipesStatus} -- {@code EMPTY_OUTPUT} with no metadata is a
     * legitimate success -- and an explicit error is recorded for the non-success cases.
     *
     * @param renderMarkdown whether to also carry the flat markdown rendering in
     *                       {@code Document.markdown} (the block tree is populated either
     *                       way)
     */
    public static Document build(Metadata primary, List<Metadata> allMetadata, String markdownBody,
                                  String docId, String pipesStatus, long fetchParseTimeMs,
                                  boolean renderMarkdown) {
        if (primary == null) {
            ParseStatus.Builder statusBuilder = ParseStatus.newBuilder()
                    .setStatus(mapPipesStatus(pipesStatus))
                    .setFetchParseTimeMs(fetchParseTimeMs)
                    .setTikaVersion(Tika.getString());
            if (pipesStatus != null && !pipesStatus.isEmpty()) {
                statusBuilder.setPipesStatus(pipesStatus);
            }
            if (statusBuilder.getStatus() != ParseStatus.Status.SUCCESS) {
                statusBuilder.addErrors("No metadata returned from parse");
            }
            Document.Builder document = Document.newBuilder().setStatus(statusBuilder.build());
            if (docId != null && !docId.isEmpty()) {
                document.setId(docId);
            }
            return document.build();
        }

        Document.Builder document = buildOne(primary, markdownBody, docId, renderMarkdown);

        String parserClass = primary.get(TikaCoreProperties.TIKA_PARSED_BY);
        ParseStatus.Builder statusBuilder = ParseStatus.newBuilder()
                .setStatus(mapPipesStatus(pipesStatus))
                .setFetchParseTimeMs(fetchParseTimeMs)
                .setTikaVersion(Tika.getString());
        if (pipesStatus != null && !pipesStatus.isEmpty()) {
            statusBuilder.setPipesStatus(pipesStatus);
        }
        if (parserClass != null && !parserClass.isEmpty()) {
            statusBuilder.setParserUsed(parserClass);
        }
        String parsedByFull = primary.get(TikaCoreProperties.TIKA_PARSED_BY_FULL_SET);
        if (parsedByFull != null && !parsedByFull.isEmpty()) {
            for (String p : parsedByFull.split(",")) {
                if (!p.trim().isEmpty()) {
                    statusBuilder.addParsersUsed(p.trim());
                }
            }
        }
        document.setStatus(statusBuilder.build());

        if (allMetadata != null && allMetadata.size() > 1) {
            for (int i = 1; i < allMetadata.size(); i++) {
                Metadata embedded = allMetadata.get(i);
                String embeddedBody = embedded.get(TikaCoreProperties.TIKA_CONTENT);
                document.addEmbedded(
                        buildOne(embedded, embeddedBody, docId + "#" + i, renderMarkdown).build());
            }
        }

        return document.build();
    }

    private static Document.Builder buildOne(Metadata tika, String markdownBody, String docId,
                                             boolean renderMarkdown) {
        Document.Builder document = Document.newBuilder();
        if (docId != null && !docId.isEmpty()) {
            document.setId(docId);
        }

        String contentType = tika.get(Metadata.CONTENT_TYPE);
        if (contentType != null && !contentType.isBlank()) {
            document.setContentType(contentType.trim());
        }
        document.setFormatCategory(FormatCategoryDetector.detect(tika));

        Instant now = Instant.now();
        document.setParsedAt(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build());

        SourceOrigin.Builder origin = SourceOrigin.newBuilder();
        String resourceName = tika.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (resourceName != null && !resourceName.isBlank()) {
            origin.setFilename(resourceName.trim());
        }
        String parserClass = tika.get(TikaCoreProperties.TIKA_PARSED_BY);
        if (parserClass != null && !parserClass.isBlank()) {
            origin.setParser(parserClass.trim());
        }
        String sha256 = tika.get(SHA256_DIGEST_KEY);
        if (sha256 != null && !sha256.isBlank()) {
            origin.setSha256(sha256.trim());
        }
        document.setOrigin(origin.build());

        if (markdownBody != null && !markdownBody.isEmpty()) {
            document.addAllBlocks(MarkdownBlockTreeBuilder.toBlocks(markdownBody));
            if (renderMarkdown) {
                document.setMarkdown(markdownBody);
            }
        }

        TRANSFORMERS.transform(tika, document);

        return document;
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
