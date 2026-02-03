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
 * Office Document properties collection. These properties apply to
 * Office / Productivity Documents of all forms, including (but not limited
 * to) MS Office and OpenDocument formats.
 * This is a logical collection of properties, which may be drawn from a
 * few different external definitions.
 *
 * @since Apache Tika 1.2
 */
public interface Office {
    // These are taken from the OpenDocumentFormat specification
    String NAMESPACE_URI_DOC_META = "urn:oasis:names:tc:opendocument:xmlns:meta:1.0";
    String PREFIX_DOC_META = "meta";

    /**
     * For user defined metadata entries in the document,
     * what prefix should be attached to the key names.
     * eg <meta:user-defined meta:name="Info1">Text1</meta:user-defined> becomes custom:Info1=Text1
     */
    String USER_DEFINED_METADATA_NAME_PREFIX = "custom:";


    /**
     * Keywords pertaining to a document. Also populates {@link DublinCore#SUBJECT}.
     */
    Property KEYWORDS = Property.composite(Property.internalTextBag(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "keyword"),
            new Property[]{DublinCore.SUBJECT,});

    /**
     * Name of the initial creator/author of a document
     */
    Property INITIAL_AUTHOR = Property.internalText(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "initial-author");

    /**
     * Name of the last (most recent) author of a document
     */
    Property LAST_AUTHOR = Property.internalText(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "last-author");

    /**
     * Name of the principal author(s) of a document
     */
    Property AUTHOR = Property.internalTextBag(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "author");


    /**
     * When was the document created?
     */
    Property CREATION_DATE = Property.internalDate(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "creation-date");

    /**
     * When was the document last saved?
     */
    Property SAVE_DATE = Property.internalDate(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "save-date");

    /**
     * When was the document last printed?
     */
    Property PRINT_DATE = Property.internalDate(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "print-date");


    /**
     * The number of Slides are there in the (presentation) document
     */
    Property SLIDE_COUNT = Property.internalInteger(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "slide-count");

    /**
     * The number of Pages are there in the (paged) document
     */
    Property PAGE_COUNT = Property.internalInteger(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "page-count");

    /**
     * The number of individual Paragraphs in the document
     */
    Property PARAGRAPH_COUNT = Property.internalInteger(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "paragraph-count");

    /**
     * The number of lines in the document
     */
    Property LINE_COUNT = Property.internalInteger(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "line-count");

    /**
     * The number of Words in the document
     */
    Property WORD_COUNT = Property.internalInteger(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "word-count");

    /**
     * The number of Characters in the document
     */
    Property CHARACTER_COUNT = Property.internalInteger(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "character-count");

    /**
     * The number of Characters in the document, including spaces
     */
    Property CHARACTER_COUNT_WITH_SPACES = Property.internalInteger(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER +
                    "character-count-with-spaces");

    /**
     * The number of Tables in the document
     */
    Property TABLE_COUNT = Property.internalInteger(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "table-count");

    /**
     * The number of Images in the document
     */
    Property IMAGE_COUNT = Property.internalInteger(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "image-count");

    /**
     * The number of Objects in the document. These are typically non-Image resources
     * embedded in the document, such as other documents or non-Image media.
     */
    Property OBJECT_COUNT = Property.internalInteger(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "object-count");

    /**
     * MAPI message class.  What type of .msg/MAPI file is it?
     */
    Property MAPI_MESSAGE_CLASS = Property.internalClosedChoise(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "mapi-message-class",
            "APPOINTMENT", "CONTACT", "NOTE", "STICKY_NOTE", "POST", "TASK", "UNKNOWN",
            "UNSPECIFIED");

    Property MAPI_SENT_BY_SERVER_TYPE = Property.internalText(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER +
                    "mapi-sent-by-server-type");

    Property MAPI_FROM_REPRESENTING_NAME = Property.internalText(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER +
                    "mapi-from-representing-name");

    Property MAPI_FROM_REPRESENTING_EMAIL = Property.internalText(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER +
                    "mapi-from-representing-email");

    Property MAPI_MESSAGE_CLIENT_SUBMIT_TIME = Property.internalDate(
            PREFIX_DOC_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER +
                    "mapi-msg-client-submit-time");

    /**
     * Embedded files may have a "progID" associated with them, such as
     * Word.Document.12 or AcroExch.Document.DC
     */
    Property PROG_ID = Property.internalText("msoffice:progID");

    Property OCX_NAME = Property.internalText("msoffice:ocxName");
    Property MAPI_RECIPIENTS_STRING = Property.internalText(PREFIX_DOC_META +
            TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "mapi-recipients-string");
    Property MAPI_IMPORTANCE = Property.internalInteger(PREFIX_DOC_META +
            TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "mapi-importance");
    Property MAPI_PRIORTY = Property.internalInteger(PREFIX_DOC_META +
            TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "mapi-importance");
    Property MAPI_IS_FLAGGED = Property.internalBoolean(PREFIX_DOC_META +
            TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "mapi-is-flagged");

    Property HAS_HIDDEN_SHEETS = Property.internalBoolean("msoffice:excel:has-hidden-sheets");

    Property HAS_HIDDEN_COLUMNS = Property.internalBoolean("msoffice:excel:has-hidden-cols");

    Property HAS_HIDDEN_ROWS = Property.internalBoolean("msoffice:excel:has-hidden-rows");

    Property HAS_VERY_HIDDEN_SHEETS = Property.internalBoolean("msoffice:excel:has-very-hidden-sheets");

    Property HIDDEN_SHEET_NAMES = Property.internalTextBag("msoffice:excel:hidden-sheet-names");

    Property VERY_HIDDEN_SHEET_NAMES = Property.internalTextBag("msoffice:excel:very-hidden-sheet-names");

    Property PROTECTED_WORKSHEET = Property.internalBoolean("msoffice:excel:protected-worksheet");

    Property WORKBOOK_CODENAME = Property.internalText("msoffice:excel:workbook-codename");

    Property HAS_DATA_CONNECTIONS = Property.internalBoolean("msoffice:excel:has-data-connections");

    Property HAS_EXTERNAL_LINKS = Property.internalBoolean("msoffice:excel:has-external-links");

    Property HAS_WEB_QUERIES = Property.internalBoolean("msoffice:excel:has-web-queries");

    Property HAS_EXTERNAL_OLE_OBJECTS = Property.internalBoolean("msoffice:has-external-ole-objects");

    Property HAS_FIELD_HYPERLINKS = Property.internalBoolean("msoffice:has-field-hyperlinks");

    Property HAS_HOVER_HYPERLINKS = Property.internalBoolean("msoffice:has-hover-hyperlinks");

    Property HAS_VML_HYPERLINKS = Property.internalBoolean("msoffice:has-vml-hyperlinks");

    Property HAS_COMMENTS = Property.internalBoolean("msoffice:has-comments");

    Property COMMENT_PERSONS = Property.internalTextBag("msoffice:comment-person-display-name");

    Property HAS_HIDDEN_SLIDES = Property.internalBoolean("msoffice:ppt:has-hidden-slides");

    Property NUM_HIDDEN_SLIDES = Property.internalInteger("msoffice:ppt:num-hidden-slides");

    Property HAS_ANIMATIONS = Property.internalBoolean("msoffice:ppt:has-animations");

    //w:vanish or isVanish or isFldVanish
    Property HAS_HIDDEN_TEXT = Property.internalBoolean("msoffice:doc:has-hidden-text");

    Property HAS_TRACK_CHANGES = Property.internalBoolean("msoffice:has-track-changes");

    // Security-relevant: DDE (Dynamic Data Exchange) links can execute commands
    Property HAS_DDE_LINKS = Property.internalBoolean("msoffice:excel:has-dde-links");

    // Security-relevant: Mail merge can reference external data sources
    Property HAS_MAIL_MERGE = Property.internalBoolean("msoffice:doc:has-mail-merge");

    // Security-relevant: Attached templates can be fetched from external URLs
    Property HAS_ATTACHED_TEMPLATE = Property.internalBoolean("msoffice:doc:has-attached-template");

    // Security-relevant: SubDocuments reference external documents in master docs
    Property HAS_SUBDOCUMENTS = Property.internalBoolean("msoffice:doc:has-subdocuments");

    // Security-relevant: Pivot tables can reference external OLAP/database sources
    Property HAS_EXTERNAL_PIVOT_DATA = Property.internalBoolean("msoffice:excel:has-external-pivot-data");

    // Security-relevant: Power Query can contain URLs and connection strings
    Property HAS_POWER_QUERY = Property.internalBoolean("msoffice:excel:has-power-query");

    // Security-relevant: OLE objects can link to external files (vs embedded)
    Property HAS_LINKED_OLE_OBJECTS = Property.internalBoolean("msoffice:has-linked-ole-objects");

    // Security-relevant: Charts can reference external workbook data
    Property HAS_EXTERNAL_CHART_DATA = Property.internalBoolean("msoffice:has-external-chart-data");

    // Security-relevant: Framesets can load external URLs
    Property HAS_FRAMESETS = Property.internalBoolean("msoffice:doc:has-framesets");
}
