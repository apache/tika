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
 * A collection of Microsoft Office documents property names.
 */
public interface MSOffice {

    String KEYWORDS = "Keywords";

    String COMMENTS = "Comments";

    String LAST_AUTHOR = "Last-Author";

    String APPLICATION_NAME = "Application-Name";

    String REVISION_NUMBER = "Revision-Number";

    String TEMPLATE = "Template";

    String AUTHOR = "Author";

    String TOTAL_TIME = "Total-Time";

    String PRESENTATION_FORMAT = "Presentation-Format";

    String NOTES = "Notes";

    String MANAGER = "Manager";

    String APPLICATION_VERSION = "Application-Version";

    String VERSION = "Version";

    String CONTENT_STATUS = "Content-Status";

    String CATEGORY = "Category";

    String COMPANY = "Company";

    String SECURITY = "Security";

    
    /** The number of Slides are there in the (presentation) document */
    Property SLIDE_COUNT = 
       Property.internalInteger("Slide-Count");
    
    /** The number of Pages are there in the (paged) document */
    Property PAGE_COUNT = 
       Property.internalInteger("Page-Count");

    /** The number of individual Paragraphs in the document */ 
    Property PARAGRAPH_COUNT = 
       Property.internalInteger("Paragraph-Count");
    
    /** The number of lines in the document */
    Property LINE_COUNT = 
       Property.internalInteger("Line-Count");

    /** The number of Words in the document */
    Property WORD_COUNT = 
       Property.internalInteger("Word-Count");

    /** The number of Characters in the document */
    Property CHARACTER_COUNT = 
       Property.internalInteger("Character Count");
    
    /** The number of Characters in the document, including spaces */
    Property CHARACTER_COUNT_WITH_SPACES = 
       Property.internalInteger("Character-Count-With-Spaces");

    /** The number of Tables in the document */
    Property TABLE_COUNT = 
       Property.internalInteger("Table-Count");
    
    /** The number of Images in the document */
    Property IMAGE_COUNT = 
       Property.internalInteger("Image-Count");
    
    /** 
     * The number of Objects in the document.
     * This is typically non-Image resources embedded in the
     *  document, such as other documents or non-Image media. 
     */
    Property OBJECT_COUNT = 
       Property.internalInteger("Object-Count");

    
    /** How long has been spent editing the document? */ 
    String EDIT_TIME = "Edit-Time"; 

    /** When was the document created? */
    Property CREATION_DATE = 
        Property.internalDate("Creation-Date");

    /** When was the document last saved? */
    Property LAST_SAVED = 
       Property.internalDate("Last-Save-Date");
    
    /** When was the document last printed? */
    Property LAST_PRINTED = 
       Property.internalDate("Last-Printed");
    
    /** 
     * For user defined metadata entries in the document,
     *  what prefix should be attached to the key names.
     * eg <meta:user-defined meta:name="Info1">Text1</meta:user-defined> becomes custom:Info1=Text1
     */
    String USER_DEFINED_METADATA_NAME_PREFIX = "custom:";
}
