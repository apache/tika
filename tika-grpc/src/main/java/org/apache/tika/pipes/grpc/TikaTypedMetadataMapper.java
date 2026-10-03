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
package org.apache.tika.pipes.grpc;

import com.google.protobuf.Timestamp;
import org.apache.tika.DublinCoreMetadata;
import org.apache.tika.EmailTypedMetadata;
import org.apache.tika.GenericTypedMetadata;
import org.apache.tika.ImageTypedMetadata;
import org.apache.tika.MediaTypedMetadata;
import org.apache.tika.OfficeTypedMetadata;
import org.apache.tika.PdfTypedMetadata;
import org.apache.tika.TikaTextContent;
import org.apache.tika.TikaTypedParseStatus;
import org.apache.tika.TikaTypedResponse;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps a list of Tika {@link Metadata} objects (as produced by the pipes client) into a
 * {@link TikaTypedResponse} protobuf message.
 *
 * <p>The first metadata entry in the list is treated as the primary document metadata; subsequent
 * entries represent embedded documents.  The plain-text body is stored under the key
 * {@code X-TIKA:content} in the first metadata entry.
 *
 * <p>This class is experimental (TIKA-4727) and tracks the typed schema introduced to address
 * the performance and type-safety concerns raised about the existing flat {@code map<string,string>}
 * representation.
 */
public class TikaTypedMetadataMapper {

    private static final Logger LOG = LoggerFactory.getLogger(TikaTypedMetadataMapper.class);

    // ---- well-known Tika metadata key constants ----

    private static final String CONTENT_TYPE        = "Content-Type";
    private static final String TIKA_CONTENT        = "X-TIKA:content";
    private static final String PARSED_BY           = "X-Parsed-By";
    private static final String TIKA_DETECTED_LANG  = "X-TIKA:detected_language";
    private static final String TIKA_LANG_CONF      = "X-TIKA:detected_language_confidence";
    private static final String TIKA_WARNINGS       = "X-TIKA:EXCEPTION:warn";
    private static final String EMBED_RESOURCE_TYPE = "X-TIKA:embedded_resource_type";
    private static final String RESOURCE_NAME       = "resourceName";

    // Dublin Core
    private static final String DC_TITLE       = "dc:title";
    private static final String DC_CREATOR     = "dc:creator";
    private static final String DC_DESCRIPTION = "dc:description";
    private static final String DC_SUBJECT     = "dc:subject";
    private static final String DC_PUBLISHER   = "dc:publisher";
    private static final String DC_CONTRIBUTOR = "dc:contributor";
    private static final String DC_DATE        = "dc:date";
    private static final String DC_TYPE        = "dc:type";
    private static final String DC_FORMAT      = "dc:format";
    private static final String DC_IDENTIFIER  = "dc:identifier";
    private static final String DC_SOURCE      = "dc:source";
    private static final String DC_LANGUAGE    = "dc:language";
    private static final String DC_RELATION    = "dc:relation";
    private static final String DC_COVERAGE    = "dc:coverage";
    private static final String DC_RIGHTS      = "dc:rights";
    private static final String DCTERMS_CREATED  = "dcterms:created";
    private static final String DCTERMS_MODIFIED = "dcterms:modified";
    private static final String XMP_CREATOR_TOOL = "xmp:CreatorTool";
    private static final String COMMENTS          = "comments";
    private static final String RATING            = "xmp:Rating";
    private static final String TITLE_ALT         = "title";
    private static final String LAST_MODIFIED     = "Last-Modified";

    // PDF
    private static final String PDF_VERSION      = "pdf:PDFVersion";
    private static final String PDF_ENCRYPTED    = "pdf:encrypted";
    private static final String PDF_PRODUCER     = "pdf:producer";
    private static final String PDF_HAS_XFA      = "pdf:hasXFA";
    private static final String PDF_HAS_XMP      = "pdf:hasXMP";
    private static final String PDF_HAS_ACROFORM = "pdf:hasAcroFormFields";
    private static final String PDF_HAS_MARKED   = "pdf:hasMarkedContent";
    private static final String PDF_HAS_COLLECTION = "pdf:hasCollection";
    private static final String PDF_HAS_3D       = "pdf:has3D";
    private static final String PDF_SIGNATURE    = "pdf:hasSignature";
    private static final String PDF_CAN_PRINT    = "pdf:print";
    private static final String PDF_CAN_PRINT_FAITHFUL = "pdf:printFaithful";
    private static final String PDF_CAN_MODIFY   = "pdf:modify";
    private static final String PDF_CAN_MODIFY_ANNOTS = "pdf:modifyAnnotations";
    private static final String PDF_CAN_EXTRACT  = "pdf:extract";
    private static final String PDF_CAN_ASSEMBLE = "pdf:assemble";
    private static final String PDF_CAN_FILL_FORM = "pdf:fillInForm";
    private static final String PDF_CAN_EXTRACT_ACCESSIBILITY = "pdf:extractForAccessibility";
    private static final String PDF_TOTAL_UNMAPPED_UNICODE = "pdf:totalUnmappedUnicodeChars";
    private static final String PDF_PCT_UNMAPPED  = "pdf:overallPercentageUnmappedUnicodeChars";
    private static final String PDF_DAMAGED_FONT  = "pdf:containsDamagedFont";
    private static final String PDF_NON_EMBEDDED_FONT = "pdf:containsNonEmbeddedFont";
    private static final String PDF_OCR_PAGES     = "pdf:ocrPageCount";
    private static final String PDF_ACTION_TYPES  = "pdf:actionTypes";
    private static final String PDF_ANNOTATION_TYPES = "pdf:annotationTypes";
    private static final String PDF_INCREMENTAL_UPDATES = "pdf:incrementalUpdateNumber";
    private static final String PDF_PDFA_VERSION  = "pdfa:PDFVersion";
    private static final String PDF_PDFAID_CONFORMANCE = "pdfaid:conformance";
    private static final String PDF_PDFAID_PART   = "pdfaid:part";
    private static final String PDF_DOC_INFO_CREATOR = "pdf:docinfo:creator";
    private static final String PDF_DOC_INFO_CREATOR_TOOL = "pdf:docinfo:creator_tool";
    private static final String PDF_DOC_INFO_CREATED = "pdf:docinfo:created";
    private static final String PDF_DOC_INFO_MODIFIED = "pdf:docinfo:modified";
    private static final String PDF_DOC_INFO_PRODUCER = "pdf:docinfo:producer";
    private static final String PDF_DOC_INFO_KEYWORDS = "pdf:docinfo:keywords";
    private static final String PDF_DOC_INFO_SUBJECT = "pdf:docinfo:subject";
    private static final String PDF_DOC_INFO_TITLE = "pdf:docinfo:title";
    private static final String XMPTP_N_PAGES    = "xmpTPg:NPages";

    // Office
    private static final String META_AUTHOR      = "meta:author";
    private static final String META_LAST_AUTHOR = "meta:last-author";
    private static final String META_INIT_AUTHOR  = "meta:initial-author";
    private static final String META_CREATION_DATE = "meta:creation-date";
    private static final String META_SAVE_DATE   = "meta:save-date";
    private static final String META_PRINT_DATE  = "meta:print-date";
    private static final String META_PAGE_COUNT  = "meta:page-count";
    private static final String META_WORD_COUNT  = "meta:word-count";
    private static final String META_CHAR_COUNT  = "meta:character-count";
    private static final String META_CHAR_SPACES = "meta:character-count-with-spaces";
    private static final String META_PARA_COUNT  = "meta:paragraph-count";
    private static final String META_LINE_COUNT  = "meta:line-count";
    private static final String META_SLIDE_COUNT = "meta:slide-count";
    private static final String META_IMAGE_COUNT = "meta:image-count";
    private static final String META_TABLE_COUNT = "meta:table-count";
    private static final String EXT_APPLICATION  = "extended-properties:Application";
    private static final String EXT_APP_VERSION  = "extended-properties:AppVersion";
    private static final String EXT_TEMPLATE     = "extended-properties:Template";
    private static final String EXT_COMPANY      = "extended-properties:Company";
    private static final String EXT_MANAGER      = "extended-properties:Manager";
    private static final String EXT_PRES_FORMAT  = "extended-properties:PresentationFormat";
    private static final String EXT_NOTES        = "extended-properties:Notes";
    private static final String CP_REVISION      = "cp:revision";
    private static final String CP_CATEGORY      = "cp:category";
    private static final String CP_CONTENT_STATUS = "cp:contentStatus";
    private static final String CP_LAST_MOD_BY   = "cp:lastModifiedBy";
    private static final String CP_LAST_PRINTED  = "cp:lastPrinted";
    private static final String DOC_SECURITY     = "extended-properties:DocSecurity";
    private static final String OFFICE_HAS_TRACK_CHANGES = "meta:has-track-changes";
    private static final String OFFICE_HAS_HIDDEN_TEXT   = "meta:has-hidden-text";
    private static final String OFFICE_HAS_COMMENTS      = "meta:has-comments";
    private static final String OFFICE_HAS_HIDDEN_SHEETS = "meta:has-hidden-sheets";
    private static final String OFFICE_HAS_ANIMATIONS    = "meta:has-animations";
    private static final String IS_ENCRYPTED     = "protected";
    private static final String HAS_SIGNATURE    = "signature";

    // Image / EXIF / TIFF
    private static final String TIFF_WIDTH       = "tiff:ImageWidth";
    private static final String TIFF_HEIGHT      = "tiff:ImageLength";
    private static final String TIFF_BITS        = "tiff:BitsPerSample";
    private static final String TIFF_SAMPLES     = "tiff:SamplesPerPixel";
    private static final String TIFF_X_RES       = "tiff:XResolution";
    private static final String TIFF_Y_RES       = "tiff:YResolution";
    private static final String TIFF_RES_UNIT    = "tiff:ResolutionUnit";
    private static final String TIFF_COMPRESSION = "tiff:Compression";
    private static final String TIFF_ORIENTATION = "tiff:Orientation";
    private static final String EXIF_DATETIME_ORIG = "exif:DateTimeOriginal";
    private static final String EXIF_DATETIME_DIG  = "exif:DateTimeDigitized";
    private static final String EXIF_MAKE         = "tiff:Make";
    private static final String EXIF_MODEL        = "tiff:Model";
    private static final String EXIF_SOFTWARE     = "tiff:Software";
    private static final String EXIF_EXPOSURE     = "exif:ExposureTime";
    private static final String EXIF_F_NUMBER     = "exif:FNumber";
    private static final String EXIF_ISO          = "exif:ISOSpeedRatings";
    private static final String EXIF_FOCAL        = "exif:FocalLength";
    private static final String EXIF_FLASH        = "exif:Flash";
    private static final String EXIF_METERING     = "exif:MeteringMode";
    private static final String EXIF_WHITE_BAL    = "exif:WhiteBalance";
    private static final String GEO_LAT           = "geo:lat";
    private static final String GEO_LONG          = "geo:long";
    private static final String GEO_ALT           = "geo:alt";

    // Email / Message
    private static final String MSG_FROM         = "Message-From";
    private static final String MSG_TO           = "Message-To";
    private static final String MSG_CC           = "Message-Cc";
    private static final String MSG_BCC          = "Message-Bcc";
    private static final String MSG_SUBJECT      = "dc:title";
    private static final String MSG_DATE         = "dcterms:created";
    private static final String MSG_ID           = "Message-ID";
    private static final String MSG_IN_REPLY_TO  = "In-Reply-To";
    private static final String MSG_MULTIPART    = "multipart";

    // Media / AV
    private static final String XMPDM_DURATION       = "xmpDM:duration";
    private static final String XMPDM_VIDEO_WIDTH     = "xmpDM:videoFrameWidth";
    private static final String XMPDM_VIDEO_HEIGHT    = "xmpDM:videoFrameHeight";
    private static final String XMPDM_VIDEO_FRAME_RATE = "xmpDM:videoFrameRate";
    private static final String XMPDM_VIDEO_COMPRESSOR = "xmpDM:videoCompressor";
    private static final String XMPDM_AUDIO_SAMPLE_RATE = "xmpDM:audioSampleRate";
    private static final String XMPDM_AUDIO_CHANNELS  = "xmpDM:audioChannelType";
    private static final String XMPDM_AUDIO_COMPRESSOR = "xmpDM:audioCompressor";
    private static final String XMPDM_AUDIO_BITS      = "xmpDM:audioBitsPerSample";
    private static final String XMPDM_BIT_RATE        = "xmpDM:fileDataRate";

    // Fields consumed by the typed mapper — not forwarded to overflow_fields.
    private static final Set<String> MAPPED_KEYS = new HashSet<>(Arrays.asList(
            CONTENT_TYPE, TIKA_CONTENT, PARSED_BY, TIKA_DETECTED_LANG, TIKA_LANG_CONF,
            TIKA_WARNINGS, EMBED_RESOURCE_TYPE, RESOURCE_NAME,
            DC_TITLE, DC_CREATOR, DC_DESCRIPTION, DC_SUBJECT, DC_PUBLISHER, DC_CONTRIBUTOR,
            DC_DATE, DC_TYPE, DC_FORMAT, DC_IDENTIFIER, DC_SOURCE, DC_LANGUAGE, DC_RELATION,
            DC_COVERAGE, DC_RIGHTS, DCTERMS_CREATED, DCTERMS_MODIFIED, XMP_CREATOR_TOOL,
            COMMENTS, RATING, TITLE_ALT, LAST_MODIFIED
    ));

    private TikaTypedMetadataMapper() {}

    /**
     * Builds a {@link TikaTypedResponse} from the list of metadata entries returned by the
     * Tika pipes client.  The first entry is treated as the primary document.
     */
    public static TikaTypedResponse map(List<Metadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return TikaTypedResponse.getDefaultInstance();
        }

        Metadata primary = metadataList.get(0);
        TikaTypedResponse.Builder response = TikaTypedResponse.newBuilder();

        String contentType = primary.get(CONTENT_TYPE);
        if (contentType != null && contentType.contains(";")) {
            contentType = contentType.split(";")[0].trim();
        }

        mapTextContent(primary, response);
        mapDublinCore(primary, response);
        mapDocumentMetadata(primary, contentType, response);
        mapParseStatus(primary, response);
        mapOverflow(primary, contentType, response);

        return response.build();
    }

    private static void mapTextContent(Metadata m, TikaTypedResponse.Builder b) {
        TikaTextContent.Builder c = TikaTextContent.newBuilder();
        boolean any = false;
        String body = m.get(TIKA_CONTENT);
        if (body != null) {
            c.setBody(body);
            c.setContentLength(body.codePointCount(0, body.length()));
            any = true;
        }
        String title = firstNonNull(m.get(DC_TITLE), m.get(TITLE_ALT));
        if (title != null) {
            c.setTitle(title);
            any = true;
        }
        String desc = m.get(DC_DESCRIPTION);
        if (desc != null) {
            c.setDescription(desc);
            any = true;
        }
        String kw = m.get(DC_SUBJECT);
        if (kw != null) {
            c.setKeywords(kw);
            any = true;
        }
        if (any) {
            b.setContent(c.build());
        }
    }

    private static void mapDublinCore(Metadata m, TikaTypedResponse.Builder b) {
        DublinCoreMetadata.Builder dc = DublinCoreMetadata.newBuilder();
        boolean any = false;

        any |= setString(m, DC_TITLE, dc::setTitle);
        any |= addStrings(m, DC_CREATOR, dc::addCreator);
        any |= setString(m, DC_DESCRIPTION, dc::setDescription);
        any |= addStrings(m, DC_SUBJECT, dc::addSubject);
        any |= setString(m, DC_PUBLISHER, dc::setPublisher);
        any |= addStrings(m, DC_CONTRIBUTOR, dc::addContributor);
        any |= setString(m, DC_TYPE, dc::setType);
        any |= setString(m, DC_FORMAT, dc::setFormat);
        any |= setString(m, DC_IDENTIFIER, dc::setIdentifier);
        any |= setString(m, DC_SOURCE, dc::setSource);
        any |= addStrings(m, DC_LANGUAGE, dc::addLanguage);
        any |= setString(m, DC_RELATION, dc::setRelation);
        any |= setString(m, DC_COVERAGE, dc::setCoverage);
        any |= setString(m, DC_RIGHTS, dc::setRights);
        any |= setString(m, XMP_CREATOR_TOOL, dc::setCreatorTool);
        any |= setString(m, COMMENTS, dc::setComments);
        any |= setString(m, RATING, dc::setRating);

        String createdRaw = m.get(DCTERMS_CREATED);
        if (createdRaw != null) {
            dc.setCreatedRaw(createdRaw);
            Timestamp ts = parseTimestamp(createdRaw);
            if (ts != null) {
                dc.setCreated(ts);
            }
            any = true;
        }
        String modifiedRaw = firstNonNull(m.get(DCTERMS_MODIFIED), m.get(LAST_MODIFIED));
        if (modifiedRaw != null) {
            dc.setModifiedRaw(modifiedRaw);
            Timestamp ts = parseTimestamp(modifiedRaw);
            if (ts != null) {
                dc.setModified(ts);
            }
            any = true;
        }

        if (any) {
            b.setDublinCore(dc.build());
        }
    }

    private static void mapDocumentMetadata(Metadata m, String contentType,
                                            TikaTypedResponse.Builder b) {
        if (contentType == null) {
            b.setGeneric(buildGeneric(m));
            return;
        }
        if (contentType.equals("application/pdf")) {
            b.setPdf(buildPdf(m));
        } else if (isOfficeMimeType(contentType)) {
            b.setOffice(buildOffice(m));
        } else if (contentType.startsWith("image/")) {
            b.setImage(buildImage(m));
        } else if (isEmailMimeType(contentType)) {
            b.setEmail(buildEmail(m));
        } else if (contentType.startsWith("audio/") || contentType.startsWith("video/")) {
            b.setMedia(buildMedia(m));
        } else {
            b.setGeneric(buildGeneric(m));
        }
    }

    private static boolean isOfficeMimeType(String ct) {
        return ct.startsWith("application/vnd.openxmlformats-officedocument.")
                || ct.startsWith("application/vnd.ms-")
                || ct.startsWith("application/vnd.oasis.opendocument.")
                || ct.equals("application/msword")
                || ct.equals("application/vnd.ms-excel")
                || ct.equals("application/vnd.ms-powerpoint");
    }

    private static boolean isEmailMimeType(String ct) {
        return ct.equals("message/rfc822")
                || ct.equals("application/mbox")
                || ct.startsWith("message/");
    }

    private static PdfTypedMetadata buildPdf(Metadata m) {
        PdfTypedMetadata.Builder b = PdfTypedMetadata.newBuilder();
        setString(m, PDF_VERSION, b::setPdfVersion);
        setBool(m, PDF_ENCRYPTED, b::setIsEncrypted);
        setInt(m, XMPTP_N_PAGES, b::setPageCount);
        setString(m, PDF_PRODUCER, b::setProducer);
        setString(m, PDF_DOC_INFO_CREATOR, b::setDocInfoCreator);
        setString(m, PDF_DOC_INFO_CREATOR_TOOL, b::setDocInfoCreatorTool);
        setString(m, PDF_DOC_INFO_PRODUCER, b::setDocInfoProducer);
        setString(m, PDF_DOC_INFO_KEYWORDS, b::setDocInfoKeywords);
        setString(m, PDF_DOC_INFO_SUBJECT, b::setDocInfoSubject);
        setString(m, PDF_DOC_INFO_TITLE, b::setDocInfoTitle);
        setTimestamp(m, PDF_DOC_INFO_CREATED, b::setDocInfoCreated, b::setDocInfoCreatedRaw);
        setTimestamp(m, PDF_DOC_INFO_MODIFIED, b::setDocInfoModified, b::setDocInfoModifiedRaw);
        setString(m, PDF_PDFA_VERSION, b::setPdfaVersion);
        setString(m, PDF_PDFAID_CONFORMANCE, b::setPdfaidConformance);
        setInt(m, PDF_PDFAID_PART, b::setPdfaidPart);
        setBool(m, PDF_HAS_XFA, b::setHasXfa);
        setBool(m, PDF_HAS_XMP, b::setHasXmp);
        setBool(m, PDF_HAS_ACROFORM, b::setHasAcroformFields);
        setBool(m, PDF_HAS_MARKED, b::setHasMarkedContent);
        setBool(m, PDF_HAS_COLLECTION, b::setHasCollection);
        setBool(m, PDF_HAS_3D, b::setHas3D);
        setBool(m, PDF_SIGNATURE, b::setHasSignature);
        setBool(m, PDF_CAN_PRINT, b::setCanPrint);
        setBool(m, PDF_CAN_PRINT_FAITHFUL, b::setCanPrintFaithful);
        setBool(m, PDF_CAN_MODIFY, b::setCanModifyDocument);
        setBool(m, PDF_CAN_MODIFY_ANNOTS, b::setCanModifyAnnotations);
        setBool(m, PDF_CAN_EXTRACT, b::setCanExtractContent);
        setBool(m, PDF_CAN_ASSEMBLE, b::setCanAssembleDocument);
        setBool(m, PDF_CAN_FILL_FORM, b::setCanFillInForm);
        setBool(m, PDF_CAN_EXTRACT_ACCESSIBILITY, b::setCanExtractForAccessibility);
        setInt(m, PDF_TOTAL_UNMAPPED_UNICODE, b::setTotalUnmappedUnicodeChars);
        setDouble(m, PDF_PCT_UNMAPPED, b::setOverallPctUnmappedUnicodeChars);
        setBool(m, PDF_DAMAGED_FONT, b::setContainsDamagedFont);
        setBool(m, PDF_NON_EMBEDDED_FONT, b::setContainsNonEmbeddedFont);
        setInt(m, PDF_OCR_PAGES, b::setOcrPageCount);
        setInt(m, PDF_INCREMENTAL_UPDATES, b::setIncrementalUpdateNumber);
        addStrings(m, PDF_ACTION_TYPES, b::addActionTypes);
        addStrings(m, PDF_ANNOTATION_TYPES, b::addAnnotationTypes);
        addStrings(m, PARSED_BY, b::addParsedBy);
        setString(m, CONTENT_TYPE, b::setContentType);
        return b.build();
    }

    private static OfficeTypedMetadata buildOffice(Metadata m) {
        OfficeTypedMetadata.Builder b = OfficeTypedMetadata.newBuilder();
        setString(m, EXT_APPLICATION, b::setApplication);
        setString(m, EXT_APP_VERSION, b::setAppVersion);
        setString(m, EXT_TEMPLATE, b::setTemplate);
        addStrings(m, META_AUTHOR, b::addAuthor);
        setString(m, META_LAST_AUTHOR, b::setLastAuthor);
        setString(m, META_INIT_AUTHOR, b::setInitialAuthor);
        setString(m, CP_LAST_MOD_BY, b::setLastModifiedBy);
        setString(m, EXT_COMPANY, b::setCompany);
        addStrings(m, EXT_MANAGER, b::addManager);
        setTimestamp(m, META_CREATION_DATE, b::setCreationDate, b::setCreationDateRaw);
        setTimestamp(m, META_SAVE_DATE, b::setSaveDate, b::setSaveDateRaw);
        setTimestamp(m, META_PRINT_DATE, b::setPrintDate, b::setPrintDateRaw);
        setTimestamp(m, CP_LAST_PRINTED, b::setLastPrinted, b::setLastPrintedRaw);
        setInt(m, META_PAGE_COUNT, b::setPageCount);
        setInt(m, META_WORD_COUNT, b::setWordCount);
        setInt(m, META_CHAR_COUNT, b::setCharacterCount);
        setInt(m, META_CHAR_SPACES, b::setCharacterCountWithSpaces);
        setInt(m, META_PARA_COUNT, b::setParagraphCount);
        setInt(m, META_LINE_COUNT, b::setLineCount);
        setInt(m, META_SLIDE_COUNT, b::setSlideCount);
        setInt(m, META_IMAGE_COUNT, b::setImageCount);
        setInt(m, META_TABLE_COUNT, b::setTableCount);
        setString(m, CP_REVISION, b::setRevision);
        setString(m, CP_CATEGORY, b::setCategory);
        setString(m, CP_CONTENT_STATUS, b::setContentStatus);
        setString(m, EXT_PRES_FORMAT, b::setPresentationFormat);
        setString(m, EXT_NOTES, b::setNotes);
        setInt(m, DOC_SECURITY, b::setDocSecurity);
        setBool(m, OFFICE_HAS_TRACK_CHANGES, b::setHasTrackChanges);
        setBool(m, OFFICE_HAS_HIDDEN_TEXT, b::setHasHiddenText);
        setBool(m, OFFICE_HAS_COMMENTS, b::setHasComments);
        setBool(m, IS_ENCRYPTED, b::setIsEncrypted);
        setBool(m, HAS_SIGNATURE, b::setHasSignature);
        setBool(m, OFFICE_HAS_HIDDEN_SHEETS, b::setHasHiddenSheets);
        setBool(m, OFFICE_HAS_ANIMATIONS, b::setHasAnimations);
        addStrings(m, PARSED_BY, b::addParsedBy);
        setString(m, CONTENT_TYPE, b::setContentType);
        return b.build();
    }

    private static ImageTypedMetadata buildImage(Metadata m) {
        ImageTypedMetadata.Builder b = ImageTypedMetadata.newBuilder();
        setInt(m, TIFF_WIDTH, b::setImageWidth);
        setInt(m, TIFF_HEIGHT, b::setImageHeight);
        setInt(m, TIFF_BITS, b::setBitsPerSample);
        setInt(m, TIFF_SAMPLES, b::setSamplesPerPixel);
        setDouble(m, TIFF_X_RES, b::setXResolution);
        setDouble(m, TIFF_Y_RES, b::setYResolution);
        setString(m, TIFF_RES_UNIT, b::setResolutionUnit);
        setString(m, TIFF_COMPRESSION, b::setCompression);
        setString(m, TIFF_ORIENTATION, b::setOrientation);
        setTimestamp(m, EXIF_DATETIME_ORIG, b::setDatetimeOriginal, b::setDatetimeOriginalRaw);
        setTimestamp(m, EXIF_DATETIME_DIG, b::setDatetimeDigitized, b::setDatetimeDigitizedRaw);
        setString(m, EXIF_MAKE, b::setMake);
        setString(m, EXIF_MODEL, b::setModel);
        setString(m, EXIF_SOFTWARE, b::setSoftware);
        setDouble(m, EXIF_EXPOSURE, b::setExposureTime);
        setDouble(m, EXIF_F_NUMBER, b::setFNumber);
        setInt(m, EXIF_ISO, b::setIsoSpeedRatings);
        setDouble(m, EXIF_FOCAL, b::setFocalLength);
        setString(m, EXIF_FLASH, b::setFlash);
        setString(m, EXIF_METERING, b::setMeteringMode);
        setString(m, EXIF_WHITE_BAL, b::setWhiteBalance);
        setDouble(m, GEO_LAT, b::setGpsLatitude);
        setDouble(m, GEO_LONG, b::setGpsLongitude);
        setDouble(m, GEO_ALT, b::setGpsAltitude);
        addStrings(m, PARSED_BY, b::addParsedBy);
        setString(m, CONTENT_TYPE, b::setContentType);
        return b.build();
    }

    private static EmailTypedMetadata buildEmail(Metadata m) {
        EmailTypedMetadata.Builder b = EmailTypedMetadata.newBuilder();
        setString(m, MSG_FROM, b::setMessageFrom);
        addStrings(m, MSG_TO, b::addMessageTo);
        addStrings(m, MSG_CC, b::addMessageCc);
        addStrings(m, MSG_BCC, b::addMessageBcc);
        setString(m, DC_TITLE, b::setSubject);
        setTimestamp(m, DCTERMS_CREATED, b::setMessageDate, b::setMessageDateRaw);
        setString(m, MSG_ID, b::setMessageId);
        setString(m, MSG_IN_REPLY_TO, b::setInReplyTo);
        addStrings(m, PARSED_BY, b::addParsedBy);
        setString(m, CONTENT_TYPE, b::setContentType);
        return b.build();
    }

    private static MediaTypedMetadata buildMedia(Metadata m) {
        MediaTypedMetadata.Builder b = MediaTypedMetadata.newBuilder();
        setString(m, XMPDM_DURATION, b::setDurationRaw);
        setDouble(m, XMPDM_DURATION, b::setDurationSeconds);
        setInt(m, XMPDM_VIDEO_WIDTH, b::setVideoWidth);
        setInt(m, XMPDM_VIDEO_HEIGHT, b::setVideoHeight);
        setDouble(m, XMPDM_VIDEO_FRAME_RATE, b::setVideoFrameRate);
        setString(m, XMPDM_VIDEO_COMPRESSOR, b::setVideoCompressor);
        setInt(m, XMPDM_AUDIO_SAMPLE_RATE, b::setAudioSampleRate);
        setString(m, XMPDM_AUDIO_CHANNELS, b::setAudioChannels);
        setString(m, XMPDM_AUDIO_COMPRESSOR, b::setAudioCompressor);
        setInt(m, XMPDM_AUDIO_BITS, b::setAudioBitsPerSample);
        setString(m, CONTENT_TYPE, b::setContentType);
        addStrings(m, PARSED_BY, b::addParsedBy);
        return b.build();
    }

    private static GenericTypedMetadata buildGeneric(Metadata m) {
        GenericTypedMetadata.Builder b = GenericTypedMetadata.newBuilder();
        setString(m, CONTENT_TYPE, b::setContentType);
        addStrings(m, PARSED_BY, b::addParsedBy);
        setString(m, TIKA_DETECTED_LANG, b::setDetectedLanguage);
        setDouble(m, TIKA_LANG_CONF, b::setDetectedLanguageConfidence);
        setString(m, RESOURCE_NAME, b::setResourceName);
        return b.build();
    }

    private static void mapParseStatus(Metadata m, TikaTypedResponse.Builder b) {
        TikaTypedParseStatus.Builder s = TikaTypedParseStatus.newBuilder();
        s.setStatus(TikaTypedParseStatus.Status.STATUS_SUCCESS);
        String[] parsedBy = m.getValues(PARSED_BY);
        if (parsedBy != null) {
            for (String parser : parsedBy) {
                s.addParsersUsed(parser);
            }
        }
        String warnings = m.get(TIKA_WARNINGS);
        if (warnings != null) {
            s.addWarnings(warnings);
        }
        b.setParseStatus(s.build());
    }

    private static void mapOverflow(Metadata m, String contentType, TikaTypedResponse.Builder b) {
        for (String name : m.names()) {
            if (!MAPPED_KEYS.contains(name)) {
                String value = m.get(name);
                if (value != null) {
                    b.putOverflowFields(name, value);
                }
            }
        }
    }

    // ---- helper methods ----

    @FunctionalInterface
    interface StringSetter {
        void accept(String s);
    }

    @FunctionalInterface
    interface TimestampSetter {
        void accept(Timestamp t);
    }

    private static boolean setString(Metadata m, String key, StringSetter setter) {
        String v = m.get(key);
        if (v != null) {
            setter.accept(v);
            return true;
        }
        return false;
    }

    private static boolean addStrings(Metadata m, String key, StringSetter adder) {
        String[] values = m.getValues(key);
        if (values == null || values.length == 0) {
            return false;
        }
        for (String v : values) {
            if (v != null) {
                adder.accept(v);
            }
        }
        return true;
    }

    private static boolean setBool(Metadata m, String key,
                                   java.util.function.Consumer<Boolean> setter) {
        String v = m.get(key);
        if (v == null) {
            return false;
        }
        setter.accept(Boolean.parseBoolean(v) || "yes".equalsIgnoreCase(v) || "1".equals(v));
        return true;
    }

    private static boolean setInt(Metadata m, String key,
                                  java.util.function.Consumer<Integer> setter) {
        String v = m.get(key);
        if (v == null) {
            return false;
        }
        try {
            setter.accept(Integer.parseInt(v.trim()));
            return true;
        } catch (NumberFormatException e) {
            LOG.debug("Could not parse int for key {}: {}", key, v);
            return false;
        }
    }

    private static boolean setDouble(Metadata m, String key,
                                     java.util.function.Consumer<Double> setter) {
        String v = m.get(key);
        if (v == null) {
            return false;
        }
        try {
            setter.accept(Double.parseDouble(v.trim()));
            return true;
        } catch (NumberFormatException e) {
            LOG.debug("Could not parse double for key {}: {}", key, v);
            return false;
        }
    }

    private static void setTimestamp(Metadata m, String key, TimestampSetter tsSetter,
                                     StringSetter rawSetter) {
        String v = m.get(key);
        if (v == null) {
            return;
        }
        rawSetter.accept(v);
        Timestamp ts = parseTimestamp(v);
        if (ts != null) {
            tsSetter.accept(ts);
        }
    }

    private static Timestamp parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Instant instant = Instant.parse(raw);
            return Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano())
                    .build();
        } catch (DateTimeParseException e) {
            LOG.debug("Could not parse timestamp: {}", raw);
            return null;
        }
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
