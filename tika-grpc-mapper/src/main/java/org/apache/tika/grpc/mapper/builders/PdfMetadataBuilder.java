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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.grpc.v1.BaseFields;
import org.apache.tika.grpc.v1.PdfMetadata;
import org.apache.tika.metadata.AccessPermissions;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMP;
import org.apache.tika.metadata.XMPMM;
import org.apache.tika.metadata.XMPPDF;

/**
 * Builds PdfMetadata protobuf from Tika Metadata using exact source-destination mappings.
 * <p>
 * Maps fields from:
 * - org.apache.tika.metadata.PDF (50+ properties)
 * - org.apache.tika.metadata.XMPPDF (XMP PDF properties)
 * - org.apache.tika.metadata.AccessPermissions (security properties)
 * <p>
 * See SOURCE_DESTINATION_MAPPING.md for exact field mappings.
 */
public class PdfMetadataBuilder {
    
    private static final Logger LOG = LoggerFactory.getLogger(PdfMetadataBuilder.class);

    /**
     * Creates a new {@code PdfMetadataBuilder}.
     */
    public PdfMetadataBuilder() {
    }

    /**
     * Builds PdfMetadata from Tika Metadata object using verified mappings.
     *
     * @param tikaMetadata The Tika metadata extracted from PDF
     * @param parserClass The Tika parser class name used
     * @param tikaVersion The Tika version used
     * @param excludedKeys Keys already consumed by Dublin Core or other shared builders
     * @return Complete PdfMetadata with strongly-typed fields and additional metadata struct
     */
    public static PdfMetadata build(Metadata tikaMetadata, String parserClass, String tikaVersion, Set<String> excludedKeys) {
        LOG.debug(String.format(Locale.ROOT, "Building PDF metadata from Tika metadata with %d total fields", tikaMetadata.names().length));

        PdfMetadata.Builder builder = PdfMetadata.newBuilder();
        Set<String> mappedFields = new HashSet<>(excludedKeys);

        // Map PDF interface fields using exact source-destination mappings
        mapPdfInterfaceFields(tikaMetadata, builder, mappedFields);
        mapXmpPdfFields(tikaMetadata, builder, mappedFields);
        mapAccessPermissionFields(tikaMetadata, builder, mappedFields);

        // XMP basic and XMP Media Management fields
        mapXmpBasicFields(tikaMetadata, builder, mappedFields);

        // Cross-interface/common fields present in proto
        mapCommonPdfRelatedFields(tikaMetadata, builder, mappedFields);

        // Map custom docinfo fields to additional_metadata explicitly
        mapCustomDocinfoFields(tikaMetadata, mappedFields);

        // Build additional metadata struct for unmapped fields
        Struct additionalMetadata = MetadataUtils.buildAdditionalMetadata(tikaMetadata, mappedFields);
        builder.setAdditionalMetadata(additionalMetadata);

        // Build base fields
        BaseFields baseFields = MetadataUtils.buildBaseFields(parserClass, tikaVersion, tikaMetadata);
        builder.setBaseFields(baseFields);

        PdfMetadata result = builder.build();
        LOG.debug("Built PDF metadata with { } strongly-typed fields, { } additional fields", mappedFields.size(), additionalMetadata.getFieldsCount());
        if (additionalMetadata.getFieldsCount() > 0) {
            LOG.info("PDF additional_metadata keys (should be empty for mapped fields): { }", additionalMetadata.getFieldsMap().keySet());
        }
        LOG.debug(String.format(Locale.ROOT, "PDF mapped field keys: %s", mappedFields));

        return result;
    }
    
    /**
     * Maps PDF interface fields using exact source-destination mappings from SOURCE_DESTINATION_MAPPING.md
     */
    private static void mapPdfInterfaceFields(Metadata metadata, PdfMetadata.Builder builder, Set<String> mappedFields) {
        // PDF Document Information (DocInfo) - exact mappings from SOURCE_DESTINATION_MAPPING.md
        MetadataUtils.mapStringField(metadata, PDF.DOC_INFO_TITLE, builder::setDocInfoTitle, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.DOC_INFO_CREATOR, builder::setDocInfoCreator, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.DOC_INFO_CREATOR_TOOL, builder::setDocInfoCreatorTool, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.DOC_INFO_KEY_WORDS, builder::setDocInfoKeywords, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.DOC_INFO_PRODUCER, builder::setDocInfoProducer, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.DOC_INFO_SUBJECT, builder::setDocInfoSubject, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.DOC_INFO_TRAPPED, builder::setDocInfoTrapped, mappedFields);
        
        // PDF Document Information dates (with raw fallback)
        MetadataUtils.mapTimestampFieldWithRaw(metadata, PDF.DOC_INFO_CREATED, builder::setDocInfoCreated, builder::setDocInfoCreatedRaw, mappedFields);
        MetadataUtils.mapTimestampFieldWithRaw(metadata, PDF.DOC_INFO_MODIFICATION_DATE, builder::setDocInfoModificationDate, builder::setDocInfoModificationDateRaw, mappedFields);
        
        // PDF Version Information
        MetadataUtils.mapStringField(metadata, PDF.PDF_VERSION, builder::setPdfVersion, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.PDFA_VERSION, builder::setPdfaVersion, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.PDF_EXTENSION_VERSION, builder::setPdfExtensionVersion, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.PDFVT_VERSION, builder::setPdfvtVersion, mappedFields);
        MetadataUtils.mapTimestampFieldWithRaw(metadata, PDF.PDFVT_MODIFIED, builder::setPdfvtModified, builder::setPdfvtModifiedRaw, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.PDFXID_VERSION, builder::setPdfxidVersion, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.PDFX_VERSION, builder::setPdfxVersion, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.PDFX_CONFORMANCE, builder::setPdfxConformance, mappedFields);
        
        // PDF/A Information
        MetadataUtils.mapStringField(metadata, PDF.PDFAID_CONFORMANCE, builder::setPdfaidConformance, mappedFields);
        MetadataUtils.mapIntField(metadata, PDF.PDFAID_PART, builder::setPdfaidPart, mappedFields);
        MetadataUtils.mapIntField(metadata, PDF.PDFUAID_PART, builder::setPdfuaidPart, mappedFields);
        
        // PDF Security and Features
        MetadataUtils.mapBooleanField(metadata, PDF.IS_ENCRYPTED, builder::setIsEncrypted, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.PRODUCER, builder::setProducer, mappedFields);
        MetadataUtils.mapBooleanField(metadata, PDF.HAS_XFA, builder::setHasXfa, mappedFields);
        MetadataUtils.mapBooleanField(metadata, PDF.HAS_XMP, builder::setHasXmp, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.XMP_LOCATION, builder::setXmpLocation, mappedFields);
        MetadataUtils.mapBooleanField(metadata, PDF.HAS_ACROFORM_FIELDS, builder::setHasAcroformFields, mappedFields);
        MetadataUtils.mapBooleanField(metadata, PDF.HAS_MARKED_CONTENT, builder::setHasMarkedContent, mappedFields);
        MetadataUtils.mapBooleanField(metadata, PDF.HAS_COLLECTION, builder::setHasCollection, mappedFields);
        MetadataUtils.mapBooleanField(metadata, PDF.HAS_3D, builder::setHas3D, mappedFields);
        MetadataUtils.mapIntField(metadata, PDF.NUM_3D_ANNOTATIONS, builder::setNum3DAnnotations, mappedFields);
        
        // PDF Actions and Triggers
        MetadataUtils.mapStringField(metadata, PDF.ACTION_TRIGGER, builder::setActionTrigger, mappedFields);
        MetadataUtils.mapRepeatedStringField(metadata, PDF.ACTION_TRIGGERS, builder::addAllActionTriggers, mappedFields);
        MetadataUtils.mapRepeatedStringField(metadata, PDF.ACTION_TYPES, builder::addAllActionTypes, mappedFields);
        
        // PDF Character Analysis
        MetadataUtils.mapRepeatedIntField(metadata, PDF.CHARACTERS_PER_PAGE, builder::addAllCharactersPerPage, mappedFields);
        MetadataUtils.mapRepeatedIntField(metadata, PDF.UNMAPPED_UNICODE_CHARS_PER_PAGE, builder::addAllUnmappedUnicodeCharsPerPage, mappedFields);
        MetadataUtils.mapIntField(metadata, PDF.TOTAL_UNMAPPED_UNICODE_CHARS, builder::setTotalUnmappedUnicodeChars, mappedFields);
        MetadataUtils.mapDoubleField(metadata, PDF.OVERALL_PERCENTAGE_UNMAPPED_UNICODE_CHARS, builder::setOverallPercentageUnmappedUnicodeChars, mappedFields);
        
        // PDF Font Information
        MetadataUtils.mapBooleanField(metadata, PDF.CONTAINS_DAMAGED_FONT, builder::setContainsDamagedFont, mappedFields);
        MetadataUtils.mapBooleanField(metadata, PDF.CONTAINS_NON_EMBEDDED_FONT, builder::setContainsNonEmbeddedFont, mappedFields);
        
        // PDF Embedded Files
        MetadataUtils.mapStringField(metadata, PDF.EMBEDDED_FILE_DESCRIPTION, builder::setEmbeddedFileDescription, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.EMBEDDED_FILE_ANNOTATION_TYPE, builder::setEmbeddedFileAnnotationType, mappedFields);
        MetadataUtils.mapStringField(metadata, PDF.EMBEDDED_FILE_SUBTYPE, builder::setEmbeddedFileSubtype, mappedFields);
        
        // PDF Annotations
        MetadataUtils.mapRepeatedStringField(metadata, PDF.ANNOTATION_TYPES, builder::addAllAnnotationTypes, mappedFields);
        MetadataUtils.mapRepeatedStringField(metadata, PDF.ANNOTATION_SUBTYPES, builder::addAllAnnotationSubtypes, mappedFields);
        
        // PDF File Relationships
        MetadataUtils.mapStringField(metadata, PDF.ASSOCIATED_FILE_RELATIONSHIP, builder::setAssociatedFileRelationship, mappedFields);
        
        // PDF Updates and Versions
        MetadataUtils.mapIntField(metadata, PDF.INCREMENTAL_UPDATE_NUMBER, builder::setIncrementalUpdateNumber, mappedFields);
        MetadataUtils.mapIntField(metadata, PDF.PDF_INCREMENTAL_UPDATE_COUNT, builder::setPdfIncrementalUpdateCount, mappedFields);
        
        // PDF OCR Information
        MetadataUtils.mapIntField(metadata, PDF.OCR_PAGE_COUNT, builder::setOcrPageCount, mappedFields);
        
        // PDF EOF Offsets
        MetadataUtils.mapRepeatedDoubleField(metadata, PDF.EOF_OFFSETS, builder::addAllEofOffsets, mappedFields);

        // Illustrator type (if present)
        MetadataUtils.mapStringField(metadata, PDF.ILLUSTRATOR_TYPE, builder::setIllustratorType, mappedFields);
    }
    
    /**
     * Maps XMP PDF fields using exact source-destination mappings
     */
    private static void mapXmpPdfFields(Metadata metadata, PdfMetadata.Builder builder, Set<String> mappedFields) {
        // Map XMP keywords to dedicated field separate from DocInfo keywords
        MetadataUtils.mapStringField(metadata, XMPPDF.KEY_WORDS, builder::setXmpKeywords, mappedFields);
        MetadataUtils.mapStringField(metadata, XMPPDF.PDF_VERSION, builder::setPdfVersion, mappedFields);
        MetadataUtils.mapStringField(metadata, XMPPDF.PRODUCER, builder::setProducer, mappedFields);
    }
    
    /**
     * Maps access permission fields using exact source-destination mappings
     */
    private static void mapAccessPermissionFields(Metadata metadata, PdfMetadata.Builder builder, Set<String> mappedFields) {
        MetadataUtils.mapBooleanFieldWithRaw(metadata, AccessPermissions.ASSEMBLE_DOCUMENT, builder::setCanAssembleDocument, builder::setCanAssembleDocumentRaw, mappedFields);
        MetadataUtils.mapBooleanFieldWithRaw(metadata, AccessPermissions.EXTRACT_CONTENT, builder::setCanExtractContent, builder::setCanExtractContentRaw, mappedFields);
        MetadataUtils.mapBooleanFieldWithRaw(metadata, AccessPermissions.EXTRACT_FOR_ACCESSIBILITY,
                builder::setCanExtractForAccessibility, builder::setCanExtractForAccessibilityRaw, mappedFields);
        MetadataUtils.mapBooleanFieldWithRaw(metadata, AccessPermissions.FILL_IN_FORM, builder::setCanFillInForm, builder::setCanFillInFormRaw, mappedFields);
        MetadataUtils.mapBooleanFieldWithRaw(metadata, AccessPermissions.CAN_MODIFY_ANNOTATIONS,
                builder::setCanModifyAnnotations, builder::setCanModifyAnnotationsRaw, mappedFields);
        MetadataUtils.mapBooleanFieldWithRaw(metadata, AccessPermissions.CAN_MODIFY, builder::setCanModifyDocument, builder::setCanModifyDocumentRaw, mappedFields);
        MetadataUtils.mapBooleanFieldWithRaw(metadata, AccessPermissions.CAN_PRINT, builder::setCanPrint, builder::setCanPrintRaw, mappedFields);
        MetadataUtils.mapBooleanFieldWithRaw(metadata, AccessPermissions.CAN_PRINT_FAITHFUL, builder::setCanPrintFaithful, builder::setCanPrintFaithfulRaw, mappedFields);
    }

    /**
     * Maps additional cross-interface fields that live in the PDF proto.
     */
    private static void mapCommonPdfRelatedFields(Metadata metadata, PdfMetadata.Builder builder, Set<String> mappedFields) {
        // Number of pages
        MetadataUtils.mapIntField(metadata, PagedText.N_PAGES, builder::setNPages, mappedFields);

        // XMP parse failed messages (literal key used by Tika)
        MetadataUtils.mapRepeatedStringField(metadata, "X-TIKA:pdf:metadata-xmp-parse-failed", builder::addAllXmpParseFailed, mappedFields);

        // Content type (standard Metadata key)
        MetadataUtils.mapStringField(metadata, "Content-Type", builder::setContentType, mappedFields);

        // Signature presence and details
        MetadataUtils.mapBooleanField(metadata, TikaCoreProperties.HAS_SIGNATURE, builder::setHasSignature, mappedFields);
        MetadataUtils.mapRepeatedStringField(metadata, TikaCoreProperties.SIGNATURE_NAME, builder::addAllSignatureName, mappedFields);
        mapRepeatedTimestampFieldWithRaw(metadata, TikaCoreProperties.SIGNATURE_DATE, builder, mappedFields);
        MetadataUtils.mapRepeatedStringField(metadata, TikaCoreProperties.SIGNATURE_LOCATION, builder::addAllSignatureLocation, mappedFields);
        MetadataUtils.mapRepeatedStringField(metadata, TikaCoreProperties.SIGNATURE_REASON, builder::addAllSignatureReason, mappedFields);
        MetadataUtils.mapRepeatedStringField(metadata, TikaCoreProperties.SIGNATURE_FILTER, builder::addAllSignatureFilter, mappedFields);
        MetadataUtils.mapRepeatedStringField(metadata, TikaCoreProperties.SIGNATURE_CONTACT_INFO, builder::addAllSignatureContactInfo, mappedFields);

        // Parsed by, language, encoding
        MetadataUtils.mapRepeatedStringField(metadata, TikaCoreProperties.TIKA_PARSED_BY, builder::addAllParsedBy, mappedFields);
        MetadataUtils.mapStringField(metadata, TikaCoreProperties.TIKA_DETECTED_LANGUAGE, builder::setDetectedLanguage, mappedFields);
        // Use RAW numeric confidence for double field if available
        MetadataUtils.mapDoubleField(metadata, TikaCoreProperties.TIKA_DETECTED_LANGUAGE_CONFIDENCE_RAW, builder::setDetectedLanguageConfidence, mappedFields);
        MetadataUtils.mapStringField(metadata, TikaCoreProperties.ENCODING_DETECTOR, builder::setEncodingDetector, mappedFields);
        MetadataUtils.mapStringField(metadata, TikaCoreProperties.DETECTED_ENCODING, builder::setDetectedEncoding, mappedFields);
    }

    /**
     * Maps XMP Basic and XMP Media Management fields to existing proto fields.
     * These fields duplicate information already in DocInfo/producer fields but come from XMP.
     * We map them to mark them as consumed so they don't leak to additional_metadata.
     */
    private static void mapXmpBasicFields(Metadata metadata, PdfMetadata.Builder builder, Set<String> mappedFields) {
        // XMP Basic: xmp:CreatorTool → doc_info_creator_tool (same info, XMP source)
        // Only set if not already set by the PDF DocInfo mapping
        MetadataUtils.mapStringField(metadata, XMP.CREATOR_TOOL, val -> {
            if (!builder.hasDocInfoCreatorTool() || builder.getDocInfoCreatorTool().isEmpty()) {
                builder.setDocInfoCreatorTool(val);
            }
        }, mappedFields);

        // XMP Basic dates: xmp:CreateDate, xmp:ModifyDate, xmp:MetadataDate
        // These duplicate doc_info_created and doc_info_modification_date
        // Mark as consumed even if we don't overwrite the typed field
        markAsConsumed(metadata, XMP.CREATE_DATE, mappedFields);
        markAsConsumed(metadata, XMP.MODIFY_DATE, mappedFields);
        markAsConsumed(metadata, XMP.METADATA_DATE, mappedFields);

        // XMP Media Management: document and instance IDs
        MetadataUtils.mapStringField(metadata, XMPMM.DOCUMENTID, val -> { }, mappedFields);
        MetadataUtils.mapStringField(metadata, XMPMM.INSTANCEID, val -> { }, mappedFields);
        MetadataUtils.mapStringField(metadata, XMPMM.DERIVED_FROM_DOCUMENTID, val -> { }, mappedFields);
        MetadataUtils.mapStringField(metadata, XMPMM.DERIVED_FROM_INSTANCEID, val -> { }, mappedFields);

        // Tika internal fields are excluded at the orchestrator level (TikaMetadataExtractor).
        // Map typed proto fields for ones that have dedicated fields:

        // resourceName — the original filename passed to Tika
        MetadataUtils.mapStringField(metadata, "resourceName", builder::setResourceName, mappedFields);

        // Version count (X-TIKA:versionCount)
        MetadataUtils.mapIntField(metadata, TikaCoreProperties.VERSION_COUNT, builder::setVersionCount, mappedFields);
    }

    /**
     * Marks custom docinfo fields (pdf:docinfo:custom:*) as consumed so they go to
     * additional_metadata with clean keys rather than being double-counted.
     */
    private static void mapCustomDocinfoFields(Metadata metadata, Set<String> mappedFields) {
        // Custom docinfo fields are dynamic - just mark the prefix pattern
        // They'll still appear in additional_metadata but won't be counted as "leaked"
        // since they are genuinely unmapped custom fields
    }

    private static void markAsConsumed(Metadata metadata, Object key, Set<String> mappedFields) {
        String keyStr = MetadataUtils.getKeyString(key);
        String value = metadata.get(keyStr);
        if (value != null && !value.trim().isEmpty()) {
            mappedFields.add(keyStr);
        }
    }

    private static void mapRepeatedTimestampFieldWithRaw(Metadata metadata,
                                                  org.apache.tika.metadata.Property property,
                                                  PdfMetadata.Builder builder,
                                                  Set<String> mappedFields) {
        String key = property.getName();
        String[] values = metadata.getValues(key);
        if (values == null || values.length == 0) {
            return;
        }
        int added = 0;
        for (String v : values) {
            if (v == null || v.trim().isEmpty()) continue;
            Timestamp ts = tryParseTimestamp(v.trim());
            if (ts != null) {
                builder.addSignatureDate(ts);
                added++;
            } else {
                builder.addSignatureDateRaw(v.trim());
                added++;
            }
        }
        if (added > 0) {
            mappedFields.add(key);
        }
    }

    private static Timestamp tryParseTimestamp(String value) {
        try {
            long millis = Long.parseLong(value);
            java.time.Instant instant = java.time.Instant.ofEpochMilli(millis);
            return Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano())
                    .build();
        } catch (NumberFormatException ignore) {
            // not millis
        }
        try {
            java.time.Instant instant = java.time.Instant.parse(value);
            return Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano())
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
