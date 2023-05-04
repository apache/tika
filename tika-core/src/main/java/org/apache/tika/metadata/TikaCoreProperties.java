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
 * Contains a core set of basic Tika metadata properties, which all parsers
 * will attempt to supply (where the file format permits). These are all
 * defined in terms of other standard namespaces.
 * <p>
 * Users of Tika who wish to have consistent metadata across file formats
 * can make use of these Properties, knowing that where present they will
 * have consistent semantic meaning between different file formats. (No
 * matter if one file format calls it Title, another Long-Title and another
 * Long-Name, if they all mean the same thing as defined by
 * {@link DublinCore#TITLE} then they will all be present as such)
 * <p>
 * For now, most of these properties are composite ones including the deprecated
 * non-prefixed String properties from the Metadata class. In Tika 2.0, most
 * of these will revert back to simple assignments.
 *
 * @since Apache Tika 1.2
 */
@SuppressWarnings("deprecation")
public interface TikaCoreProperties {

    /**
     * The common delimiter used between the namespace abbreviation and the property name
     */
    String NAMESPACE_PREFIX_DELIMITER = ":";

    /**
     * Use this to prefix metadata properties that store information
     * about the parsing process.  Users should be able to distinguish
     * between metadata that was contained within the document and
     * metadata about the parsing process.
     */
    String TIKA_META_PREFIX = "X-TIKA" + NAMESPACE_PREFIX_DELIMITER;
    Property EMBEDDED_DEPTH = Property.internalInteger(TIKA_META_PREFIX + "embedded_depth");

    /**
     * This tracks the embedded file paths based on the name of embedded files
     * where available.  There is a small risk that there may be path collisions
     * and that these paths may not be unique within a file.
     *
     * For a more robust path, see {@link TikaCoreProperties#EMBEDDED_ID_PATH}.
     */
    Property EMBEDDED_RESOURCE_PATH =
            Property.internalText(TIKA_META_PREFIX + "embedded_resource_path");

    /**
     * This tracks the embedded file paths based on the embedded file's
     * {@link TikaCoreProperties#EMBEDDED_ID}.
     */
    Property EMBEDDED_ID_PATH =
            Property.internalText(TIKA_META_PREFIX + "embedded_id_path");

    /**
     * This is a 1-index counter for embedded files, used by the RecursiveParserWrapper
     */
    Property EMBEDDED_ID =
            Property.internalInteger(TIKA_META_PREFIX + "embedded_id");

    Property PARSE_TIME_MILLIS = Property.internalText(TIKA_META_PREFIX + "parse_time_millis");
    /**
     * Simple class name of the content handler
     */
    Property TIKA_CONTENT_HANDLER = Property.internalText(TIKA_META_PREFIX + "content_handler");
    Property TIKA_CONTENT = Property.internalText(TIKA_META_PREFIX + "content");
    /**
     * Use this to store parse exception information in the Metadata object.
     */
    String TIKA_META_EXCEPTION_PREFIX = TIKA_META_PREFIX + "EXCEPTION" + NAMESPACE_PREFIX_DELIMITER;

    /**
     * Use this to store warnings that happened during the parse.
     */
    String TIKA_META_WARN_PREFIX = TIKA_META_PREFIX + "WARN" + NAMESPACE_PREFIX_DELIMITER;

    //exception in main file
    Property CONTAINER_EXCEPTION =
            Property.internalText(TIKA_META_EXCEPTION_PREFIX + "container_exception");

    //exception in an embedded file
    Property EMBEDDED_EXCEPTION =
            Property.internalTextBag(TIKA_META_EXCEPTION_PREFIX + "embedded_exception");

    //warning while parsing in an embedded file
    Property EMBEDDED_WARNING =
            Property.internalTextBag(TIKA_META_EXCEPTION_PREFIX + "embedded_warning");

    Property WRITE_LIMIT_REACHED =
            Property.internalBoolean(TIKA_META_EXCEPTION_PREFIX + "write_limit_reached");
    /**
     * Use this to store exceptions caught during a parse that are
     * non-fatal, e.g. if a parser is in lenient mode and more
     * content can be extracted if we ignore an exception thrown by
     * a dependency.
     */
    Property TIKA_META_EXCEPTION_WARNING =
            Property.internalTextBag(TIKA_META_EXCEPTION_PREFIX + "warn");

    /**
     * This means that metadata keys or metadata values were truncated.
     * If there is an "include" filter, this should not be set if
     * a field is not in the "include" set.
     */
    Property TRUNCATED_METADATA =
            Property.internalBoolean(TIKA_META_WARN_PREFIX + "truncated_metadata");

    /**
     * Use this to store exceptions caught while trying to read the
     * stream of an embedded resource.  Do not use this if there is
     * a parse exception on the embedded resource.
     */
    Property TIKA_META_EXCEPTION_EMBEDDED_STREAM =
            Property.internalTextBag(TIKA_META_EXCEPTION_PREFIX + "embedded_stream_exception");
    Property TIKA_PARSED_BY = Property.internalTextBag(TIKA_META_PREFIX + "Parsed-By");

    /**
     * Use this to store a record of all parsers that touched a given file
     * in the container file's metadata.
     */
    Property TIKA_PARSED_BY_FULL_SET = Property.internalTextBag(TIKA_META_PREFIX + "Parsed-By-Full-Set");

    Property TIKA_DETECTED_LANGUAGE = Property.externalTextBag(TIKA_META_PREFIX +
            "detected_language");

    Property TIKA_DETECTED_LANGUAGE_CONFIDENCE = Property.externalTextBag(TIKA_META_PREFIX +
            "detected_language_confidence");

    Property TIKA_DETECTED_LANGUAGE_CONFIDENCE_RAW = Property.externalRealSeq(TIKA_META_PREFIX +
            "detected_language_confidence_raw");

    String RESOURCE_NAME_KEY = "resourceName";
    String PROTECTED = "protected";
    String EMBEDDED_RELATIONSHIP_ID = "embeddedRelationshipId";
    String EMBEDDED_STORAGE_CLASS_ID = "embeddedStorageClassId";
    String EMBEDDED_RESOURCE_TYPE_KEY = "embeddedResourceType";
    /**
     * Some file formats can store information about their original
     * file name/location or about their attachment's original file name/location
     * within the file.
     */
    Property ORIGINAL_RESOURCE_NAME =
            Property.internalTextBag(TIKA_META_PREFIX + "origResourceName");
    /**
     * This should be used to store the path (relative or full)
     * of the source file, including the file name,
     * e.g. doc/path/to/my_pdf.pdf
     * <p>
     * This can also be used for a primary key within a database.
     */
    Property SOURCE_PATH = Property.internalText(TIKA_META_PREFIX + "sourcePath");
    /**
     * This is currently used to identify Content-Type that may be
     * included within a document, such as in html documents
     * (e.g. <meta http-equiv="content-type" content="text/html; charset=UTF-8">)
     * , or the value might come from outside the document.  This information
     * may be faulty and should be treated only as a hint.
     */
    Property CONTENT_TYPE_HINT = Property.internalText(HttpHeaders.CONTENT_TYPE + "-Hint");
    /**
     * This is used by users to override detection with the override detector.
     */
    Property CONTENT_TYPE_USER_OVERRIDE =
            Property.internalText(HttpHeaders.CONTENT_TYPE + "-Override");
    /**
     * This is used by parsers to override detection of embedded resources
     * with the override detector.
     */
    Property CONTENT_TYPE_PARSER_OVERRIDE =
            Property.internalText(HttpHeaders.CONTENT_TYPE + "-Parser-Override");
    /**
     * @see DublinCore#FORMAT
     */
    Property FORMAT = DublinCore.FORMAT;
    /**
     * @see DublinCore#IDENTIFIER
     */
    Property IDENTIFIER = DublinCore.IDENTIFIER;
    /**
     * @see DublinCore#CONTRIBUTOR
     */
    Property CONTRIBUTOR = DublinCore.CONTRIBUTOR;
    /**
     * @see DublinCore#COVERAGE
     */
    Property COVERAGE = DublinCore.COVERAGE;
    /**
     * @see DublinCore#CREATOR
     */
    Property CREATOR = DublinCore.CREATOR;
    /**
     * @see Office#LAST_AUTHOR
     */
    Property MODIFIER = Office.LAST_AUTHOR;
    /**
     * @see XMP#CREATOR_TOOL
     */
    Property CREATOR_TOOL = XMP.CREATOR_TOOL;
    /**
     * @see DublinCore#LANGUAGE
     */
    Property LANGUAGE = DublinCore.LANGUAGE;
    /**
     * @see DublinCore#PUBLISHER
     */
    Property PUBLISHER = DublinCore.PUBLISHER;
    /**
     * @see DublinCore#RELATION
     */
    Property RELATION = DublinCore.RELATION;
    /**
     * @see DublinCore#RIGHTS
     */
    Property RIGHTS = DublinCore.RIGHTS;
    /**
     * @see DublinCore#SOURCE
     */
    Property SOURCE = DublinCore.SOURCE;
    /**
     * @see DublinCore#TYPE
     */
    Property TYPE = DublinCore.TYPE;
    /**
     * @see DublinCore#TITLE
     */
    Property TITLE = DublinCore.TITLE;

    // Descriptive properties
    /**
     * @see DublinCore#DESCRIPTION
     */
    Property DESCRIPTION = DublinCore.DESCRIPTION;
    /**
     * {@link DublinCore#SUBJECT}; should include both subject and keywords
     * if a document format has both.  See also {@link Office#KEYWORDS}
     * and {@link OfficeOpenXMLCore#SUBJECT}.
     */
    Property SUBJECT = DublinCore.SUBJECT;
    /**
     * @see DublinCore#DATE
     */
    Property CREATED = DublinCore.CREATED;

    // Date related properties
    /**
     * @see DublinCore#MODIFIED
     * @see Office#SAVE_DATE
     */
    Property MODIFIED = DublinCore.MODIFIED;
    /**
     * @see Office#PRINT_DATE
     */
    Property PRINT_DATE = Office.PRINT_DATE;
    /**
     * @see XMP#METADATA_DATE
     */
    Property METADATA_DATE = XMP.METADATA_DATE;
    /**
     * @see Geographic#LATITUDE
     */
    Property LATITUDE = Geographic.LATITUDE;


    // Geographic related properties
    /**
     * @see Geographic#LONGITUDE
     */
    Property LONGITUDE = Geographic.LONGITUDE;
    /**
     * @see Geographic#ALTITUDE
     */
    Property ALTITUDE = Geographic.ALTITUDE;
    /**
     * @see XMP#RATING
     */
    Property RATING = XMP.RATING;

    /**
     * This is the number of images (as in a multi-frame gif) returned by
     * Java's {@link javax.imageio.ImageReader#getNumImages(boolean)}.  See
     * the javadocs for known limitations.
     */
    Property NUM_IMAGES = Property.internalInteger("imagereader:NumImages");

    // Comment and rating properties
    /**
     * @see OfficeOpenXMLExtended#COMMENTS
     */
    Property COMMENTS = OfficeOpenXMLExtended.COMMENTS;
    /**
     * Embedded resource type property
     */
    Property EMBEDDED_RESOURCE_TYPE = Property.internalClosedChoise(EMBEDDED_RESOURCE_TYPE_KEY,
            EmbeddedResourceType.ATTACHMENT.toString(), EmbeddedResourceType.INLINE.toString(),
            EmbeddedResourceType.METADATA.toString(), EmbeddedResourceType.MACRO.toString(),
            EmbeddedResourceType.THUMBNAIL.toString(), EmbeddedResourceType.RENDERING.toString());
    Property HAS_SIGNATURE = Property.internalBoolean("hasSignature");

    Property SIGNATURE_NAME = Property.internalTextBag("signature:name");
    Property SIGNATURE_DATE = Property.internalDateBag("signature:date");
    Property SIGNATURE_LOCATION = Property.internalTextBag("signature:location");
    Property SIGNATURE_REASON = Property.internalTextBag("signature:reason");
    Property SIGNATURE_FILTER = Property.internalTextBag("signature:filter");
    Property SIGNATURE_CONTACT_INFO = Property.internalTextBag("signature:contact-info");

    //is the file encrypted
    Property IS_ENCRYPTED = Property.internalBoolean(TIKA_META_PREFIX + "encrypted");

    /**
     * A file might contain different types of embedded documents.
     * The most common is the ATTACHMENT.
     * <p>
     * An INLINE embedded resource should be used for embedded image
     * files that are used to render the page image (as in PDXObjImages in PDF files).
     * <p>
     * A MACRO is code that is embedded in the document and is intended
     * to be executable within the application that opens the document.  This
     * includes traditional macros within Microsoft Office files and
     * javascript within PDFActions.  This would not include, e.g., an
     * .exe file embedded in a .zip file.
     * <p>
     * A VERSION is an earlier version of the file as in incremental updates.
     * The initial use case for this is incremental updates in PDFs, but
     * it could be applied to other file formats as well where earlier versions
     * are recoverable. See also {@link PDF#INCREMENTAL_UPDATE_NUMBER}
     * <p>
     * Not all parsers have yet implemented this.
     */
    enum EmbeddedResourceType {
        INLINE, //image that is intended to be displayed in a rendering of the file
        ATTACHMENT,//standard attachment as in email
        MACRO, //any code that is intended to be run by the application
        METADATA, //e.g. xmp, xfa
        FONT,//embedded font files
        THUMBNAIL, //TODO: set this in parsers that handle thumbnails
        RENDERING, //if a file has been rendered
        VERSION //an earlier version of a file
    }
}
