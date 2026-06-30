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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.grpc.mapper.builders.CreativeCommonsMetadataBuilder;
import org.apache.tika.grpc.mapper.builders.DocumentTypeDetector;
import org.apache.tika.grpc.mapper.builders.EmailMetadataBuilder;
import org.apache.tika.grpc.mapper.builders.EpubMetadataBuilder;
import org.apache.tika.grpc.mapper.builders.FontMetadataBuilder;
import org.apache.tika.grpc.mapper.builders.HtmlMetadataBuilder;
import org.apache.tika.grpc.mapper.builders.ImageMetadataBuilder;
import org.apache.tika.grpc.mapper.builders.MediaMetadataBuilder;
import org.apache.tika.grpc.mapper.builders.MetadataUtils;
import org.apache.tika.grpc.mapper.builders.OfficeMetadataBuilder;
import org.apache.tika.grpc.mapper.builders.PdfMetadataBuilder;
import org.apache.tika.grpc.mapper.builders.RtfMetadataBuilder;
import org.apache.tika.grpc.mapper.builders.WarcMetadataBuilder;
import org.apache.tika.grpc.v1.BaseFields;
import org.apache.tika.grpc.v1.DublinCoreMetadata;
import org.apache.tika.grpc.v1.EmbeddedDocument;
import org.apache.tika.grpc.v1.GenericMetadata;
import org.apache.tika.grpc.v1.ParseContent;
import org.apache.tika.grpc.v1.ParseResponse;
import org.apache.tika.grpc.v1.ParseStatus;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Main orchestrator for extracting comprehensive metadata from Tika Metadata objects.
 * 
 * This class:
 * 1. Detects document type from Tika metadata
 * 2. Routes to appropriate metadata builder
 * 3. Builds Dublin Core metadata
 * 4. Assembles complete ParseResponse with oneof document_metadata
 * 
 * Follows the principle: "Whatever Tika extracts, we save - strongly-typed if we recognize it, struct if we don't."
 */
public class ParseResponseMapper {
    
    private static final Logger LOG = LoggerFactory.getLogger(ParseResponseMapper.class);

    /**
     * Creates a new {@code TikaMetadataExtractor}.
     */
    public ParseResponseMapper() {
    }

    /**
     * Maps Tika parse output (primary metadata plus optional embedded metadata list) to {@link ParseResponse}.
     */
    public static ParseResponse map(Metadata primary, List<Metadata> allMetadata, String extractedText,
                                    String docId, String pipesStatus, long parseTimeMs) {
        return map(ParseMapContext.of(primary, allMetadata, extractedText, docId),
                pipesStatus, parseTimeMs, Collections.emptyList());
    }

    /**
     * Maps Tika parse output and applies optional {@link ParseResponseDecorator} extensions
     * (for example document outlines) after core metadata mapping.
     */
    public static ParseResponse map(ParseMapContext context, String pipesStatus, long parseTimeMs,
                                    List<ParseResponseDecorator> decorators) {
        Metadata primary = context.getPrimary();
        if (primary == null) {
            return ParseResponse.newBuilder()
                    .setStatus(ParseStatus.newBuilder()
                            .setStatus(ParseStatus.Status.STATUS_FAILED)
                            .setParseTimeMs(parseTimeMs)
                            .addErrors("No metadata returned from parse")
                            .build())
                    .build();
        }
        String docId = context.getDocId();
        List<Metadata> allMetadata = context.getAllMetadata();
        String extractedText = context.getExtractedText();
        String parserClass = primary.get(TikaCoreProperties.TIKA_PARSED_BY);
        if (parserClass == null || parserClass.isEmpty()) {
            parserClass = primary.get(TikaCoreProperties.TIKA_PARSED_BY_FULL_SET);
        }
        ParseResponse.Builder responseBuilder = extractComprehensiveMetadata(
                primary, parserClass, extractedText, docId).toBuilder();

        responseBuilder.setParseId(UUID.randomUUID().toString());
        Instant now = Instant.now();
        responseBuilder.setParsedAt(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build());

        ParseStatus.Builder statusBuilder = ParseStatus.newBuilder()
                .setParseTimeMs(parseTimeMs)
                .setStatus(mapPipesStatus(pipesStatus));
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
        responseBuilder.setStatus(statusBuilder.build());

        if (allMetadata != null && allMetadata.size() > 1) {
            for (int i = 1; i < allMetadata.size(); i++) {
                Metadata embedded = allMetadata.get(i);
                EmbeddedDocument.Builder embeddedBuilder = EmbeddedDocument.newBuilder()
                        .setId(String.valueOf(i))
                        .setEmbedDepth(1);
                String contentType = embedded.get(Metadata.CONTENT_TYPE);
                if (contentType != null) {
                    embeddedBuilder.setContentType(contentType);
                }
                String resourceName = embedded.get(TikaCoreProperties.RESOURCE_NAME_KEY);
                if (resourceName != null) {
                    embeddedBuilder.setFilename(resourceName);
                    embeddedBuilder.setPath(resourceName);
                }
                String embeddedBody = embedded.get(TikaCoreProperties.TIKA_CONTENT);
                String embeddedParser = embedded.get(TikaCoreProperties.TIKA_PARSED_BY);
                embeddedBuilder.setParsedContent(extractComprehensiveMetadata(
                        embedded, embeddedParser, embeddedBody, docId + "#" + i));
                responseBuilder.addEmbeddedDocs(embeddedBuilder.build());
            }
        }

        if (decorators != null) {
            for (ParseResponseDecorator decorator : decorators) {
                if (decorator != null) {
                    decorator.decorate(responseBuilder, context);
                }
            }
        }

        return responseBuilder.build();
    }

    private static ParseStatus.Status mapPipesStatus(String pipesStatus) {
        if (pipesStatus == null || pipesStatus.isEmpty()) {
            return ParseStatus.Status.STATUS_UNSPECIFIED;
        }
        return switch (pipesStatus) {
            case "OK" -> ParseStatus.Status.STATUS_SUCCESS;
            case "TIMEOUT" -> ParseStatus.Status.STATUS_TIMEOUT;
            case "PARSE_EXCEPTION", "FETCH_EXCEPTION", "EMIT_EXCEPTION", "COMPLETED_WITH_EXCEPTION" ->
                    ParseStatus.Status.STATUS_FAILED;
            default -> ParseStatus.Status.STATUS_PARTIAL;
        };
    }

    /**
     * Extracts comprehensive metadata from Tika Metadata object.
     * 
     * @param tikaMetadata The Tika metadata extracted from document
     * @param parserClass The Tika parser class name used
     * @param extractedText The text content extracted by Tika
     * @param docId The document ID
     * @return Complete ParseResponse with strongly-typed metadata and flexible struct data
     */
    public static ParseResponse extractComprehensiveMetadata(
            Metadata tikaMetadata, 
            String parserClass, 
            String extractedText,
            String docId) {
        
        LOG.debug(String.format(Locale.ROOT, "Extracting comprehensive metadata for document %s using parser %s", docId, parserClass));
        
        // Detect document type
        DocumentTypeDetector.DocumentType docType = DocumentTypeDetector.detect(tikaMetadata);
        LOG.debug(String.format(Locale.ROOT, "Detected document type: %s", docType));
        
        // Build ParseResponse
        ParseResponse.Builder responseBuilder = ParseResponse.newBuilder();
        
        // Set document ID
        if (docId != null && !docId.isEmpty()) {
            responseBuilder.setDocId(docId);
        }
        
        // Build content
        ParseContent.Builder contentBuilder = ParseContent.newBuilder();
        if (extractedText != null && !extractedText.isEmpty()) {
            contentBuilder.setBody(extractedText);
        }

        String title = tikaMetadata.get(TikaCoreProperties.TITLE);
        if (title == null || title.isEmpty()) {
            title = tikaMetadata.get(DublinCore.TITLE);
        }
        if (title != null && !title.isEmpty()) {
            contentBuilder.setTitle(title);
        }
        String description = tikaMetadata.get(TikaCoreProperties.DESCRIPTION);
        if (description == null || description.isEmpty()) {
            description = tikaMetadata.get(DublinCore.DESCRIPTION);
        }
        if (description != null && !description.isEmpty()) {
            contentBuilder.setDescription(description);
        }
        String keywords = tikaMetadata.get("Keywords");
        if (keywords != null && !keywords.isEmpty()) {
            contentBuilder.setKeywords(keywords);
        }
        
        String contentLength = tikaMetadata.get("Content-Length");
        if (contentLength != null && !contentLength.isEmpty()) {
            try {
                long length = Long.parseLong(contentLength);
                contentBuilder.setContentLength(length);
            } catch (NumberFormatException e) {
                LOG.warn(String.format(Locale.ROOT, "Failed to parse content length: %s", contentLength));
            }
        }
        
        responseBuilder.setContent(contentBuilder.build());
        
        // Build Dublin Core metadata (common to all document types)
        // Collect consumed keys so document-type builders can exclude them from additional_metadata
        Set<String> dublinCoreKeys = new HashSet<>();
        DublinCoreMetadata dublinCore = buildDublinCoreMetadata(tikaMetadata, dublinCoreKeys);
        responseBuilder.setDublinCore(dublinCore);

        // Also exclude XMP-sourced Dublin Core duplicates and x-default variants
        collectDublinCoreRelatedKeys(tikaMetadata, dublinCoreKeys);

        // Exclude Tika internal fields common to all document types
        collectTikaInternalKeys(tikaMetadata, dublinCoreKeys);

        // Get Tika version
        String tikaVersion = MetadataUtils.getTikaVersion();

        // Route to appropriate metadata builder based on document type
        switch (docType) {
            case PDF:
                responseBuilder.setPdf(PdfMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;
                
            case OFFICE:
                responseBuilder.setOffice(OfficeMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case IMAGE:
                responseBuilder.setImage(ImageMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case EMAIL:
                responseBuilder.setEmail(EmailMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case MEDIA:
                responseBuilder.setMedia(MediaMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case HTML:
                responseBuilder.setHtml(HtmlMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case RTF:
                responseBuilder.setRtf(RtfMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case DATABASE:
                responseBuilder.setDatabase(org.apache.tika.grpc.mapper.builders.DatabaseMetadataBuilder
                        .build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case FONT:
                responseBuilder.setFont(FontMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case EPUB:
                responseBuilder.setEpub(EpubMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case WARC:
                responseBuilder.setWarc(WarcMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case CLIMATE_FORECAST:
                responseBuilder.setClimateForecast(org.apache.tika.grpc.mapper.builders.ClimateForecastMetadataBuilder
                        .build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case CREATIVE_COMMONS:
                responseBuilder.setCreativeCommons(CreativeCommonsMetadataBuilder.build(
                        tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                responseBuilder.setGeneric(buildGenericMetadata(tikaMetadata, parserClass, tikaVersion));
                break;

            case GENERIC:
            default:
                responseBuilder.setGeneric(buildGenericMetadata(tikaMetadata, parserClass, tikaVersion));
                break;
        }
        
        // Overlay: attach Creative Commons metadata when present, regardless of primary type
        try {
            if (DocumentTypeDetector.detect(tikaMetadata) != DocumentTypeDetector.DocumentType.CREATIVE_COMMONS) {
                if (hasXmpRights(tikaMetadata)) {
                    responseBuilder.setCreativeCommons(CreativeCommonsMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                }
            }
        } catch (Exception ignored) { }

        ParseResponse response = responseBuilder.build();
        LOG.debug("Built comprehensive metadata response for document { } with { } total metadata fields", docId, tikaMetadata.names().length);
        
        return response;
    }

    private static boolean hasXmpRights(org.apache.tika.metadata.Metadata md) {
        String[] names = md.names();
        for (String n : names) {
            String ln = n.toLowerCase(Locale.ROOT);
            if (ln.contains("xmprights") || ln.contains("xmp-rights") || ln.contains(":rights") || ln.contains("xmp.rights")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Builds Dublin Core metadata from Tika metadata.
     * Populates consumedKeys with the Tika metadata key names that were consumed,
     * so document-type builders can exclude them from additional_metadata.
     */
    private static DublinCoreMetadata buildDublinCoreMetadata(Metadata tikaMetadata, Set<String> consumedKeys) {
        DublinCoreMetadata.Builder builder = DublinCoreMetadata.newBuilder();

        mapDcField(tikaMetadata, DublinCore.TITLE, builder::setTitle, consumedKeys);
        mapDcRepeatedField(tikaMetadata, DublinCore.CREATOR, builder::addCreators, consumedKeys);
        mapDcRepeatedField(tikaMetadata, DublinCore.SUBJECT, builder::addSubjects, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.DESCRIPTION, builder::setDescription, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.PUBLISHER, builder::setPublisher, consumedKeys);
        mapDcRepeatedField(tikaMetadata, DublinCore.CONTRIBUTOR, builder::addContributors, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.TYPE, builder::setType, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.FORMAT, builder::setFormat, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.IDENTIFIER, builder::setIdentifier, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.SOURCE, builder::setSource, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.LANGUAGE, builder::setLanguage, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.RELATION, builder::setRelation, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.COVERAGE, builder::setCoverage, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.RIGHTS, builder::setRights, consumedKeys);

        // Date fields
        try {
            java.util.Date created = tikaMetadata.getDate(DublinCore.CREATED);
            if (created != null) {
                builder.setCreated(toTimestamp(created));
                consumedKeys.add(DublinCore.CREATED.getName());
            }
        } catch (Exception e) {
            LOG.debug(String.format(Locale.ROOT, "Could not parse Dublin Core created date: %s", e.getMessage()));
        }

        try {
            java.util.Date modified = tikaMetadata.getDate(DublinCore.MODIFIED);
            if (modified != null) {
                builder.setModified(toTimestamp(modified));
                consumedKeys.add(DublinCore.MODIFIED.getName());
            }
        } catch (Exception e) {
            LOG.debug(String.format(Locale.ROOT, "Could not parse Dublin Core modified date: %s", e.getMessage()));
        }

        try {
            java.util.Date date = tikaMetadata.getDate(DublinCore.DATE);
            if (date != null) {
                builder.setDate(toTimestamp(date));
                consumedKeys.add(DublinCore.DATE.getName());
            }
        } catch (Exception e) {
            LOG.debug(String.format(Locale.ROOT, "Could not parse Dublin Core date: %s", e.getMessage()));
        }

        return builder.build();
    }

    /**
     * Collects all Dublin Core related keys from Tika metadata, including
     * XMP-sourced duplicates (xmp:dc:*) and x-default locale variants (*:x-default).
     * These are all representations of the same Dublin Core data and should be
     * excluded from document-type builder additional_metadata.
     */
    private static void collectDublinCoreRelatedKeys(Metadata tikaMetadata, Set<String> keys) {
        for (String name : tikaMetadata.names()) {
            // dc:* and dcterms:* (normalized Dublin Core)
            if (name.startsWith("dc:") || name.startsWith("dcterms:")) {
                keys.add(name);
            }
            // xmp:dc:* (XMP-sourced Dublin Core duplicates)
            else if (name.startsWith("xmp:dc:")) {
                keys.add(name);
            }
            // meta:keyword is a Tika alias for dc:subject
            else if (name.equals("meta:keyword")) {
                keys.add(name);
            }
        }
    }

    /**
     * Collects Tika internal processing metadata keys that are common to ALL document types.
     * These are not document metadata — they describe Tika's parsing behavior.
     * They're already captured in BaseFields.raw_metadata, so excluding them from
     * document-type additional_metadata prevents duplication.
     */
    private static void collectTikaInternalKeys(Metadata tikaMetadata, Set<String> keys) {
        for (String name : tikaMetadata.names()) {
            // X-TIKA:* fields (Parsed-By, Parsed-By-Full-Set, versionCount, etc.)
            if (name.startsWith("X-TIKA:")) {
                keys.add(name);
            }
            // Content-Type-Magic-Detected — Tika's magic detection
            else if (name.equals("Content-Type-Magic-Detected")) {
                keys.add(name);
            }
            // resourceName — the original filename passed to Tika
            else if (name.equals("resourceName")) {
                keys.add(name);
            }
            // zip:detectorZipFileOpened — Tika ZIP detector internal
            else if (name.startsWith("zip:")) {
                keys.add(name);
            }
        }
    }

    private static void mapDcField(Metadata metadata, org.apache.tika.metadata.Property prop,
                                    java.util.function.Consumer<String> setter, Set<String> consumedKeys) {
        String key = prop.getName();
        String value = metadata.get(key);
        if (value != null && !value.trim().isEmpty()) {
            setter.accept(value.trim());
            consumedKeys.add(key);
        }
    }

    private static void mapDcRepeatedField(Metadata metadata, org.apache.tika.metadata.Property prop,
                                            java.util.function.Consumer<String> adder, Set<String> consumedKeys) {
        String key = prop.getName();
        String[] values = metadata.getValues(key);
        if (values != null) {
            for (String v : values) {
                if (v != null && !v.trim().isEmpty()) {
                    adder.accept(v.trim());
                }
            }
            if (values.length > 0) {
                consumedKeys.add(key);
            }
        }
    }

    private static com.google.protobuf.Timestamp toTimestamp(java.util.Date date) {
        java.time.Instant instant = date.toInstant();
        return com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
    
    /**
     * Builds generic metadata as fallback.
     * TODO: Replace with proper GenericMetadataBuilder when implemented.
     */
    private static GenericMetadata buildGenericMetadata(
            Metadata tikaMetadata, String parserClass, String tikaVersion) {
        
        GenericMetadata.Builder builder = 
                GenericMetadata.newBuilder();
        
        // Basic identification
        String mimeType = tikaMetadata.get("Content-Type");
        if (mimeType != null && !mimeType.trim().isEmpty()) {
            builder.setDetectedMimeType(mimeType.trim());
        }
        
        String resourceName = tikaMetadata.get("resourceName");
        if (resourceName != null && !resourceName.trim().isEmpty()) {
            // Extract file extension
            int lastDot = resourceName.lastIndexOf('.');
            if (lastDot > 0 && lastDot < resourceName.length() - 1) {
                builder.setFileExtension(resourceName.substring(lastDot + 1));
            }
        }
        
        if (parserClass != null && !parserClass.trim().isEmpty()) {
            builder.setTikaParserClass(parserClass.trim());
        }
        
        // Put all metadata in the flexible struct
        com.google.protobuf.Struct allMetadata = MetadataUtils.buildAdditionalMetadata(tikaMetadata, new java.util.HashSet<>());
        builder.setAllMetadata(allMetadata);
        
        // Build base fields
        BaseFields baseFields = 
                MetadataUtils.buildBaseFields(parserClass, tikaVersion, tikaMetadata);
        builder.setBaseFields(baseFields);
        
        return builder.build();
    }
}
