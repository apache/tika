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
 */
public final class DocumentBuilder {

    private static final DocumentTransformers TRANSFORMERS = DocumentTransformers.defaults();

    private DocumentBuilder() {
    }

    /** Maps Tika parse output (primary metadata plus optional embedded metadata list) to {@link Document}. */
    public static Document build(Metadata primary, List<Metadata> allMetadata, String markdownBody,
                                  String docId, String pipesStatus, long parseTimeMs) {
        if (primary == null) {
            return Document.newBuilder()
                    .setStatus(ParseStatus.newBuilder()
                            .setStatus(ParseStatus.Status.FAILED)
                            .setParseTimeMs(parseTimeMs)
                            .addErrors("No metadata returned from parse")
                            .build())
                    .build();
        }

        Document.Builder document = buildOne(primary, markdownBody, docId);

        String parserClass = primary.get(TikaCoreProperties.TIKA_PARSED_BY);
        ParseStatus.Builder statusBuilder = ParseStatus.newBuilder()
                .setStatus(mapPipesStatus(pipesStatus))
                .setParseTimeMs(parseTimeMs);
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
                document.addEmbedded(buildOne(embedded, embeddedBody, docId + "#" + i).build());
            }
        }

        return document.build();
    }

    private static Document.Builder buildOne(Metadata tika, String markdownBody, String docId) {
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
        document.setOrigin(origin.build());

        if (markdownBody != null && !markdownBody.isEmpty()) {
            document.setMarkdown(markdownBody);
            document.addAllBlocks(MarkdownBlockTreeBuilder.toBlocks(markdownBody));
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
