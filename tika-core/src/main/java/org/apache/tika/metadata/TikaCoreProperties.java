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
     * Use this to prefix metadata properties that store information
     * about the parsing process.  Users should be able to distinguish
     * between metadata that was contained within the document and
     * metadata about the parsing process.
     * In Tika 2.0 (or earlier?), let's change X-ParsedBy to X-TIKA-Parsed-By.
     */
    public static String TIKA_META_PREFIX = "X-TIKA"+Metadata.NAMESPACE_PREFIX_DELIMITER;

    /**
     * Use this to store parse exception information in the Metadata object.
     */
    public static String TIKA_META_EXCEPTION_PREFIX = TIKA_META_PREFIX+"EXCEPTION"+
            Metadata.NAMESPACE_PREFIX_DELIMITER;

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
    public static final Property CONTENT_TYPE_HINT =
            Property.internalText(HttpHeaders.CONTENT_TYPE+"-Hint");

    Property CONTENT_TYPE_OVERRIDE =
            Property.internalText(HttpHeaders.CONTENT_TYPE+"-Override");

    /**
     * @see DublinCore#FORMAT
     */
    public static final Property FORMAT = Property.composite(DublinCore.FORMAT, 
            new Property[] { Property.internalText(Metadata.FORMAT) });
    
   /**
    * @see DublinCore#IDENTIFIER
    */
   public static final Property IDENTIFIER = Property.composite(DublinCore.IDENTIFIER, 
            new Property[] { Property.internalText(Metadata.IDENTIFIER) });
    
   /**
    * @see DublinCore#CONTRIBUTOR
    */
    public static final Property CONTRIBUTOR = Property.composite(DublinCore.CONTRIBUTOR, 
            new Property[] { Property.internalText(Metadata.CONTRIBUTOR) });
    
   /**
    * @see DublinCore#COVERAGE
    */
    public static final Property COVERAGE = Property.composite(DublinCore.COVERAGE, 
            new Property[] { Property.internalText(Metadata.COVERAGE) });
    
   /**
    * @see DublinCore#CREATOR
    */
    public static final Property CREATOR = Property.composite(DublinCore.CREATOR, 
            new Property[] { 
                Office.AUTHOR,
                Property.internalTextBag(Metadata.CREATOR),
                Property.internalTextBag(Metadata.AUTHOR)
            });
    
    /**
     * @see Office#LAST_AUTHOR
     */
     public static final Property MODIFIER = Property.composite(Office.LAST_AUTHOR, 
             new Property[] { Property.internalText(Metadata.LAST_AUTHOR) });
    
    /**
     * @see XMP#CREATOR_TOOL
     */
     public static final Property CREATOR_TOOL = XMP.CREATOR_TOOL;
    
   /**
    * @see DublinCore#LANGUAGE
    */
    public static final Property LANGUAGE = Property.composite(DublinCore.LANGUAGE, 
            new Property[] { Property.internalText(Metadata.LANGUAGE) });
    
   /**
    * @see DublinCore#PUBLISHER
    */
    public static final Property PUBLISHER = Property.composite(DublinCore.PUBLISHER, 
            new Property[] { Property.internalText(Metadata.PUBLISHER) });
    
   /**
    * @see DublinCore#RELATION
    */
    public static final Property RELATION = Property.composite(DublinCore.RELATION, 
            new Property[] { Property.internalText(Metadata.RELATION) });
    
   /**
    * @see DublinCore#RIGHTS
    */
    public static final Property RIGHTS = Property.composite(DublinCore.RIGHTS, 
            new Property[] { Property.internalText(Metadata.RIGHTS) });
    
   /**
    * @see DublinCore#SOURCE
    */
    public static final Property SOURCE = Property.composite(DublinCore.SOURCE, 
            new Property[] { Property.internalText(Metadata.SOURCE) });
    
   /**
    * @see DublinCore#TYPE
    */
    public static final Property TYPE = Property.composite(DublinCore.TYPE, 
            new Property[] { Property.internalText(Metadata.TYPE) });

    
    // Descriptive properties
    
    /**
     * @see DublinCore#TITLE
     */
    public static final Property TITLE = Property.composite(DublinCore.TITLE, 
            new Property[] { Property.internalText(Metadata.TITLE) });
     
    /**
     * @see DublinCore#DESCRIPTION
     */
    public static final Property DESCRIPTION = Property.composite(DublinCore.DESCRIPTION, 
            new Property[] { Property.internalText(Metadata.DESCRIPTION) });
     
    /**
     * @see DublinCore#SUBJECT
     * @see Office#KEYWORDS
     */
    public static final Property KEYWORDS = Property.composite(DublinCore.SUBJECT,
            new Property[] { 
                Office.KEYWORDS, 
                Property.internalTextBag(MSOffice.KEYWORDS),
                Property.internalTextBag(Metadata.SUBJECT)
            });
    
    // Date related properties
    
     /** 
      * @see DublinCore#DATE 
      * @see Office#CREATION_DATE 
      */
     public static final Property CREATED = Property.composite(DublinCore.CREATED,
             new Property[] { 
                     Office.CREATION_DATE, 
                     MSOffice.CREATION_DATE
             });
     
     /** 
      * @see DublinCore#MODIFIED
      * @see Metadata#DATE
      * @see Office#SAVE_DATE 
      */
     public static final Property MODIFIED = Property.composite(DublinCore.MODIFIED,
             new Property[] { 
                     Metadata.DATE,
                     Office.SAVE_DATE, 
                     MSOffice.LAST_SAVED, 
                     Property.internalText(Metadata.MODIFIED),
                     Property.internalText("Last-Modified")
             });
     
     /** @see Office#PRINT_DATE */
     public static final Property PRINT_DATE = Property.composite(Office.PRINT_DATE, 
             new Property[] { MSOffice.LAST_PRINTED });
     
     /**
      * @see XMP#METADATA_DATE
      */
     public static final Property METADATA_DATE = XMP.METADATA_DATE;
    
     
    // Geographic related properties
     
    /**
     * @see Geographic#LATITUDE
     */
    public static final Property LATITUDE = Geographic.LATITUDE;
    
    /**
     * @see Geographic#LONGITUDE
     */
    public static final Property LONGITUDE = Geographic.LONGITUDE;
    
    /**
     * @see Geographic#ALTITUDE
     */
    public static final Property ALTITUDE = Geographic.ALTITUDE;
    
    
    // Comment and rating properties
    
    /**
     * @see XMP#RATING
     */
    public static final Property RATING = XMP.RATING;
    
    /** 
     * @see OfficeOpenXMLExtended#COMMENTS 
     */
    public static final Property COMMENTS = Property.composite(OfficeOpenXMLExtended.COMMENTS, 
            new Property[] { 
                Property.internalTextBag(ClimateForcast.COMMENT),
                Property.internalTextBag(MSOffice.COMMENTS)
            });
    
    // TODO: Remove transition properties in Tika 2.0
    
    /** 
     * @see DublinCore#SUBJECT 
     * @deprecated use TikaCoreProperties#KEYWORDS
     */
    @Deprecated
    public static final Property TRANSITION_KEYWORDS_TO_DC_SUBJECT = Property.composite(DublinCore.SUBJECT, 
            new Property[] { Property.internalTextBag(MSOffice.KEYWORDS) });
    
    /** 
     * @see OfficeOpenXMLExtended#COMMENTS 
     * @deprecated use TikaCoreProperties#DESCRIPTION
     */
    @Deprecated
    public static final Property TRANSITION_SUBJECT_TO_DC_DESCRIPTION = Property.composite(DublinCore.DESCRIPTION, 
            new Property[] { Property.internalText(Metadata.SUBJECT) });
    
    /** 
     * @see DublinCore#TITLE 
     * @deprecated use TikaCoreProperties#TITLE
     */
    @Deprecated
    public static final Property TRANSITION_SUBJECT_TO_DC_TITLE = Property.composite(DublinCore.TITLE, 
            new Property[] { Property.internalText(Metadata.SUBJECT) });
    
    /** 
     * @see OfficeOpenXMLCore#SUBJECT 
     * @deprecated use OfficeOpenXMLCore#SUBJECT
     */
    @Deprecated
    public static final Property TRANSITION_SUBJECT_TO_OO_SUBJECT = Property.composite(OfficeOpenXMLCore.SUBJECT, 
            new Property[] { Property.internalText(Metadata.SUBJECT) });

    /**
     * See {@link #EMBEDDED_RESOURCE_TYPE}
     */
    public static final Property EMBEDDED_RESOURCE_TYPE = 
            Property.internalClosedChoise(TikaMetadataKeys.EMBEDDED_RESOURCE_TYPE, 
                    new String[]{EmbeddedResourceType.ATTACHMENT.toString(), EmbeddedResourceType.INLINE.toString()});

    
}
