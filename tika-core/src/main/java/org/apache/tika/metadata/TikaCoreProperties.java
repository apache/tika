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
 *  will attempt to supply (where the file format permits). These are all
 *  defined in terms of other standard namespaces.
 *  
 * Users of Tika who wish to have consistent metadata across file formats
 *  can make use of these Properties, knowing that where present they will
 *  have consistent semantic meaning between different file formats. (No 
 *  matter if one file format calls it Title, another Long-Title and another
 *  Long-Name, if they all mean the same thing as defined by 
 *  {@link DublinCore#TITLE} then they will all be present as such)
 *
 * For now, most of these properties are composite ones including the deprecated
 *  non-prefixed String properties from the Metadata class. In Tika 2.0, most
 *  of these will revert back to simple assignments.
 * 
 * @since Apache Tika 1.2
 */
@SuppressWarnings("deprecation")
public interface TikaCoreProperties {

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
     * Not all parsers have yet implemented this.
     *
     */
    public enum EmbeddedResourceType {
        INLINE,
        ATTACHMENT,
        MACRO
    };

    /**
     * The common delimiter used between the namespace abbreviation and the property name
     */
    String NAMESPACE_PREFIX_DELIMITER = ":";

    /**
     * Use this to prefix metadata properties that store information
     * about the parsing process.  Users should be able to distinguish
     * between metadata that was contained within the document and
     * metadata about the parsing process.
     * In Tika 2.0 (or earlier?), let's change X-ParsedBy to X-TIKA-Parsed-By.
     */
    public static String TIKA_META_PREFIX = "X-TIKA"+NAMESPACE_PREFIX_DELIMITER;

    /**
     * Use this to store parse exception information in the Metadata object.
     */
    public static String TIKA_META_EXCEPTION_PREFIX = TIKA_META_PREFIX+"EXCEPTION"+
            NAMESPACE_PREFIX_DELIMITER;

    /**
     * Use this to store exceptions caught during a parse that are
     * non-fatal, e.g. if a parser is in lenient mode and more
     * content can be extracted if we ignore an exception thrown by
     * a dependency.
     */
    public static final Property TIKA_META_EXCEPTION_WARNING =
            Property.internalTextBag(TIKA_META_EXCEPTION_PREFIX+"warn");

    /**
     * Use this to store exceptions caught while trying to read the
     * stream of an embedded resource.  Do not use this if there is
     * a parse exception on the embedded resource.
     */
    Property TIKA_META_EXCEPTION_EMBEDDED_STREAM =
            Property.internalTextBag(TIKA_META_EXCEPTION_PREFIX+"embedded_stream_exception");


    String RESOURCE_NAME_KEY = "resourceName";

    String PROTECTED = "protected";

    String EMBEDDED_RELATIONSHIP_ID = "embeddedRelationshipId";

    String EMBEDDED_STORAGE_CLASS_ID = "embeddedStorageClassId";

    String EMBEDDED_RESOURCE_TYPE_KEY = "embeddedResourceType";

    /**
     * Some file formats can store information about their original
     * file name/location or about their attachment's original file name/location.
     */
    public static final Property ORIGINAL_RESOURCE_NAME =
            Property.internalTextBag(TIKA_META_PREFIX+"origResourceName");

    /**
     * This is currently used to identify Content-Type that may be
     * included within a document, such as in html documents
     * (e.g. <meta http-equiv="content-type" content="text/html; charset=UTF-8">)
     , or the value might come from outside the document.  This information
     * may be faulty and should be treated only as a hint.
     */
    Property CONTENT_TYPE_HINT =
            Property.internalText(HttpHeaders.CONTENT_TYPE+"-Hint");

    Property CONTENT_TYPE_OVERRIDE =
            Property.internalText(HttpHeaders.CONTENT_TYPE+"-Override");

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
     Property CREATOR = Property.composite(DublinCore.CREATOR,
            new Property[] { 
                Office.AUTHOR,
            });
    
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
    
    // Descriptive properties
    
    /**
     * @see DublinCore#TITLE
     */
     Property TITLE = DublinCore.TITLE;
     
    /**
     * @see DublinCore#DESCRIPTION
     */
     Property DESCRIPTION = DublinCore.DESCRIPTION;
     
    /**
     * {@link DublinCore#SUBJECT}; should include both subject and keywords
     *  if a document format has both.  See also {@link Office#KEYWORDS}
     *  and {@link OfficeOpenXMLCore#SUBJECT}.
     */
     Property SUBJECT = DublinCore.SUBJECT;

    // Date related properties
    
     /** 
      * @see DublinCore#DATE 
      * @see Office#CREATION_DATE 
      */
      Property CREATED = Property.composite(DublinCore.CREATED,
             new Property[] { 
                     Office.CREATION_DATE, 
             });
     
     /** 
      * @see DublinCore#MODIFIED
      * @see Office#SAVE_DATE
      */
      Property MODIFIED = Property.composite(DublinCore.MODIFIED,
             new Property[] { 
                     Office.SAVE_DATE,
                     Property.internalText("Last-Modified")
             });
     
     /** @see Office#PRINT_DATE */
      Property PRINT_DATE = Office.PRINT_DATE;
     
     /**
      * @see XMP#METADATA_DATE
      */
      Property METADATA_DATE = XMP.METADATA_DATE;
    
     
    // Geographic related properties
     
    /**
     * @see Geographic#LATITUDE
     */
     Property LATITUDE = Geographic.LATITUDE;
    
    /**
     * @see Geographic#LONGITUDE
     */
     Property LONGITUDE = Geographic.LONGITUDE;
    
    /**
     * @see Geographic#ALTITUDE
     */
     Property ALTITUDE = Geographic.ALTITUDE;
    
    
    // Comment and rating properties
    
    /**
     * @see XMP#RATING
     */
     Property RATING = XMP.RATING;
    
    /** 
     * @see OfficeOpenXMLExtended#COMMENTS 
     */
     Property COMMENTS = Property.composite(OfficeOpenXMLExtended.COMMENTS,
            new Property[] { 
                Property.internalTextBag(ClimateForcast.COMMENT)
            });

    /**
     * Embedded resource type property
     */
     Property EMBEDDED_RESOURCE_TYPE = Property.internalClosedChoise(EMBEDDED_RESOURCE_TYPE_KEY,
                                                                     EmbeddedResourceType.ATTACHMENT.toString(),
                                                                     EmbeddedResourceType.INLINE.toString());

    
}
