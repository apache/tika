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
 *  Office / Productivity Documents of all forms, including (but not limited 
 *  to) MS Office and OpenDocument formats.
 * This is a logical collection of properties, which may be drawn from a
 *  few different external definitions.
 * 
 * Note that some of the legacy properties from the {@link MSOffice}
 *  collection still need to be migrated over
 *  
 * @since Apache Tika 1.2
 */
public interface Office {
   // These are taken from the OpenDocumentFormat specification
   public static final String NAMESPACE_URI_DOC_META = "urn:oasis:names:tc:opendocument:xmlns:meta:1.0";
   public static final String PREFIX_DOC_META = "meta";

   /** 
    * For user defined metadata entries in the document,
    *  what prefix should be attached to the key names.
    * eg <meta:user-defined meta:name="Info1">Text1</meta:user-defined> becomes custom:Info1=Text1
    */
   public static final String USER_DEFINED_METADATA_NAME_PREFIX = "custom:";

   
   /**
    * Keywords pertaining to a document. 
    */
   Property KEYWORDS = Property.internalTextBag(
         PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "keyword");

   /**
    * Name of the initial creator/author of a document
    */
   Property INITIAL_AUTHOR = Property.internalText(
         PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "initial-author");

   /**
    * Name of the last (most recent) author of a document
    */
   Property LAST_AUTHOR = Property.internalText(
         PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "last-author");

   /**
    * Name of the principal author(s) of a document
    */
   Property AUTHOR = Property.internalTextBag(
         PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "author");

   
   /** When was the document created? */
   Property CREATION_DATE = Property.internalDate(
         PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "creation-date");

   /** When was the document last saved? */
   Property SAVE_DATE = Property.internalDate(
         PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "save-date");
   
   /** When was the document last printed? */
   Property PRINT_DATE = Property.internalDate(
         PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "print-date");


    
    /** The number of Slides are there in the (presentation) document */
    Property SLIDE_COUNT = Property.internalInteger(
    	  PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "slide-count");
    
    /** The number of Pages are there in the (paged) document */
    Property PAGE_COUNT = Property.internalInteger(
          PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "page-count");

    /** The number of individual Paragraphs in the document */ 
    Property PARAGRAPH_COUNT = Property.internalInteger(
          PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "paragraph-count");
    
    /** The number of lines in the document */
    Property LINE_COUNT = Property.internalInteger(
          PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "line-count");

    /** The number of Words in the document */
    Property WORD_COUNT = Property.internalInteger(
          PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "word-count");

    /** The number of Characters in the document */
    Property CHARACTER_COUNT = Property.internalInteger(
          PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "character-count");
    
    /** The number of Characters in the document, including spaces */
    Property CHARACTER_COUNT_WITH_SPACES = Property.internalInteger(
          PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "character-count-with-spaces");

    /** The number of Tables in the document */
    Property TABLE_COUNT = Property.internalInteger(
          PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "table-count");
    
    /** The number of Images in the document */
    Property IMAGE_COUNT = Property.internalInteger(
          PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "image-count");
    
    /** 
     * The number of Objects in the document. These are typically non-Image resources 
     * embedded in the document, such as other documents or non-Image media. 
     */
    Property OBJECT_COUNT = Property.internalInteger(
          PREFIX_DOC_META + Metadata.NAMESPACE_PREFIX_DELIMITER + "object-count");
}
