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
package org.apache.tika.metadata;

/**
 * PDF properties collection.
 *
 * @since Apache Tika 1.14
 */
public interface PDF {

    String PDF_PREFIX = "pdf" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;
    String PDFA_PREFIX = "pdfa" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;
    String PDFAID_PREFIX = "pdfaid" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;


    Property PDF_INCREMENTAL_UPDATES = Property.externalInteger(PDF_PREFIX + "incrementalUpdates");

    Property EOF_OFFSETS = Property.externalRealSeq(PDF_PREFIX + "eofOffsets");

    /**
     * Prefix to be used for properties that record what was stored
     * in the docinfo section (as opposed to XMP)
     */
    String PDF_DOC_INFO_PREFIX =
            PDF_PREFIX + "docinfo" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    String PDF_DOC_INFO_CUSTOM_PREFIX =
            PDF_DOC_INFO_PREFIX + "custom" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    Property DOC_INFO_CREATED = Property.internalDate(PDF_DOC_INFO_PREFIX + "created");

    Property DOC_INFO_CREATOR = Property.internalText(PDF_DOC_INFO_PREFIX + "creator");

    Property DOC_INFO_CREATOR_TOOL = Property.internalText(PDF_DOC_INFO_PREFIX + "creator_tool");

    Property DOC_INFO_MODIFICATION_DATE = Property.internalDate(PDF_DOC_INFO_PREFIX + "modified");

    Property DOC_INFO_KEY_WORDS = Property.internalText(PDF_DOC_INFO_PREFIX + "keywords");

    Property DOC_INFO_PRODUCER = Property.internalText(PDF_DOC_INFO_PREFIX + "producer");

    Property DOC_INFO_SUBJECT = Property.internalText(PDF_DOC_INFO_PREFIX + "subject");

    Property DOC_INFO_TITLE = Property.internalText(PDF_DOC_INFO_PREFIX + "title");

    Property DOC_INFO_TRAPPED = Property.internalText(PDF_DOC_INFO_PREFIX + "trapped");

    Property PDF_VERSION = Property.internalRational(PDF_PREFIX + "PDFVersion");
    Property PDFA_VERSION = Property.internalRational(PDFA_PREFIX + "PDFVersion");

    Property PDF_EXTENSION_VERSION = Property.internalRational(PDF_PREFIX + "PDFExtensionVersion");

    Property PDFAID_CONFORMANCE = Property.internalText(PDFAID_PREFIX + "conformance");

    Property PDFAID_PART = Property.internalInteger(PDFAID_PREFIX + "part");

    Property PDFUAID_PART = Property.internalInteger("pdfuaid:part");

    Property PDFVT_VERSION = Property.internalText("pdfvt:version");

    Property PDFVT_MODIFIED = Property.internalDate("pdfvt:modified");
    Property PDFXID_VERSION = Property.internalText("pdfxid:version");

    Property PDFX_VERSION = Property.internalText("pdfx:version");

    Property PDFX_CONFORMANCE = Property.internalText("pdfx:conformance");
    Property ILLUSTRATOR_TYPE = Property.internalText("pdf:illustrator:type");

    Property IS_ENCRYPTED = Property.internalBoolean(PDF_PREFIX + "encrypted");

    Property PRODUCER = Property.internalText(PDF_PREFIX + "producer");

    /**
     * This specifies where an action or destination would be found/triggered
     * in the document: on document open, before close, etc.
     *
     * This is included in the embedded document (js only for now?), not the container PDF.
     */
    Property ACTION_TRIGGER = Property.internalText(PDF_PREFIX + "actionTrigger");

    /**
     * This is a list of all action or destination triggers contained
     * within a given PDF.
     */
    Property ACTION_TRIGGERS = Property.internalTextBag(PDF_PREFIX + "actionTriggers");

    Property ACTION_TYPES = Property.internalTextBag(PDF_PREFIX + "actionTypes");

    Property CHARACTERS_PER_PAGE = Property.internalIntegerSequence(PDF_PREFIX + "charsPerPage");

    Property UNMAPPED_UNICODE_CHARS_PER_PAGE =
            Property.internalIntegerSequence(PDF_PREFIX + "unmappedUnicodeCharsPerPage");

    Property TOTAL_UNMAPPED_UNICODE_CHARS =
            Property.internalInteger(PDF_PREFIX + "totalUnmappedUnicodeChars");

    Property OVERALL_PERCENTAGE_UNMAPPED_UNICODE_CHARS =
            Property.internalReal(PDF_PREFIX + "overallPercentageUnmappedUnicodeChars");

    /**
     * Contains at least one damaged font for at least one character
     */
    Property CONTAINS_DAMAGED_FONT =
            Property.internalBoolean(PDF_PREFIX + "containsDamagedFont");

    /**
     * Contains at least one font that is not embedded
     */
    Property CONTAINS_NON_EMBEDDED_FONT =
            Property.internalBoolean(PDF_PREFIX + "containsNonEmbeddedFont");

    /**
     * Has XFA
     */
    Property HAS_XFA = Property.internalBoolean(PDF_PREFIX + "hasXFA");

    /**
     * Has XMP, whether or not it is valid
     */
    Property HAS_XMP = Property.internalBoolean(PDF_PREFIX + "hasXMP");

    /**
     * If xmp is extracted by, e.g. the XMLProfiler, where did it come from?
     * The document's document catalog or a specific page...or?
     */
    Property XMP_LOCATION = Property.internalText(PDF_PREFIX + "xmpLocation");

    /**
     * Has > 0 AcroForm fields
     */
    Property HAS_ACROFORM_FIELDS = Property.internalBoolean(PDF_PREFIX + "hasAcroFormFields");

    Property HAS_MARKED_CONTENT = Property.internalBoolean(PDF_PREFIX + "hasMarkedContent");

    /**
     * Has a collection element in the root.  If true, this is likely a PDF Portfolio.
     */
    Property HAS_COLLECTION = Property.internalBoolean(PDF_PREFIX + "hasCollection");

    Property EMBEDDED_FILE_DESCRIPTION = Property.externalText(PDF_PREFIX +
            "embeddedFileDescription");

    /**
     * If the file came from an annotation and there was a type
     */
    Property EMBEDDED_FILE_ANNOTATION_TYPE = Property.internalText(PDF_PREFIX +
            "embeddedFileAnnotationType");

    /**
     *     literal string from the PDEmbeddedFile#getSubtype(), should be what the PDF
     *     alleges is the embedded file's mime type
     */
    Property EMBEDDED_FILE_SUBTYPE = Property.internalText(PDF_PREFIX +
            "embeddedFileSubtype");
    /**
     * If the PDF has an annotation of type 3D
     */
    Property HAS_3D = Property.internalBoolean(PDF_PREFIX + "has3D");

    Property ANNOTATION_TYPES = Property.internalTextBag(PDF_PREFIX + "annotationTypes");

    Property ANNOTATION_SUBTYPES = Property.internalTextBag(PDF_PREFIX + "annotationSubtypes");

    /**
     * Number of 3D annotations a PDF contains.  This makes {@link PDF#HAS_3D} redundant.
     */
    Property NUM_3D_ANNOTATIONS = Property.internalInteger(PDF_PREFIX + "num3DAnnotations");

    Property ASSOCIATED_FILE_RELATIONSHIP = Property.internalText(PDF_PREFIX +
            "associatedFileRelationship");
}
