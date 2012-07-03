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
 * A collection of Microsoft Office and Open Document property names.
 * 
 * This is being replaced with cleaner, better defined properties in
 *  {@link Office}.
 */
public interface MSOffice {

    @Deprecated String KEYWORDS = "Keywords";

    @Deprecated String COMMENTS = "Comments";

    @Deprecated String LAST_AUTHOR = "Last-Author";

    @Deprecated String AUTHOR = "Author";

    @Deprecated String APPLICATION_NAME = "Application-Name";

    @Deprecated String REVISION_NUMBER = "Revision-Number";

    @Deprecated String TEMPLATE = "Template";

    @Deprecated String TOTAL_TIME = "Total-Time";

    @Deprecated String PRESENTATION_FORMAT = "Presentation-Format";

    @Deprecated String NOTES = "Notes";

    @Deprecated String MANAGER = "Manager";

    @Deprecated String APPLICATION_VERSION = "Application-Version";

    @Deprecated String VERSION = "Version";

    @Deprecated String CONTENT_STATUS = "Content-Status";

    @Deprecated String CATEGORY = "Category";

    @Deprecated String COMPANY = "Company";

    @Deprecated String SECURITY = "Security";

    
    /** The number of Slides are there in the (presentation) document */
    @Deprecated Property SLIDE_COUNT = 
       Property.internalInteger("Slide-Count");
    
    /** The number of Pages are there in the (paged) document */
    @Deprecated Property PAGE_COUNT = 
       Property.internalInteger("Page-Count");

    /** The number of individual Paragraphs in the document */ 
    @Deprecated Property PARAGRAPH_COUNT = 
       Property.internalInteger("Paragraph-Count");
    
    /** The number of lines in the document */
    @Deprecated Property LINE_COUNT = 
       Property.internalInteger("Line-Count");

    /** The number of Words in the document */
    @Deprecated Property WORD_COUNT = 
       Property.internalInteger("Word-Count");

    /** The number of Characters in the document */
    @Deprecated Property CHARACTER_COUNT = 
       Property.internalInteger("Character Count");
    
    /** The number of Characters in the document, including spaces */
    @Deprecated Property CHARACTER_COUNT_WITH_SPACES = 
       Property.internalInteger("Character-Count-With-Spaces");

    /** The number of Tables in the document */
    @Deprecated Property TABLE_COUNT = 
       Property.internalInteger("Table-Count");
    
    /** The number of Images in the document */
    @Deprecated Property IMAGE_COUNT = 
       Property.internalInteger("Image-Count");
    
    /** 
     * The number of Objects in the document.
     * This is typically non-Image resources embedded in the
     *  document, such as other documents or non-Image media. 
     */
    @Deprecated Property OBJECT_COUNT = 
       Property.internalInteger("Object-Count");

    
    /** How long has been spent editing the document? */ 
    String EDIT_TIME = "Edit-Time"; 

    /** When was the document created? */
    @Deprecated Property CREATION_DATE = 
        Property.internalDate("Creation-Date");

    /** When was the document last saved? */
    @Deprecated Property LAST_SAVED = 
       Property.internalDate("Last-Save-Date");
    
    /** When was the document last printed? */
    @Deprecated Property LAST_PRINTED = 
       Property.internalDate("Last-Printed");
    
    /** 
     * For user defined metadata entries in the document,
     *  what prefix should be attached to the key names.
     * eg <meta:user-defined meta:name="Info1">Text1</meta:user-defined> becomes custom:Info1=Text1
     */
    String USER_DEFINED_METADATA_NAME_PREFIX = "custom:";
}
