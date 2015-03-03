package org.apache.tika.metadata;

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

/**
 * Until we can find a common standard, we'll use these options.  They
 * were mostly derived from PDFBox's AccessPermission, but some can
 * apply to other document formats, especially CAN_MODIFY and FILL_IN_FORM.
 */
public interface AccessPermissions {

    final static String PREFIX = "access_permission"+Metadata.NAMESPACE_PREFIX_DELIMITER;

    /**
     * Can any modifications be made to the document
     */
    Property CAN_MODIFY = Property.externalTextBag(PREFIX+"can_modify");

    /**
     * Should content be extracted, generally.
     */
    Property EXTRACT_CONTENT = Property.externalText(PREFIX+"extract_content");

    /**
     * Should content be extracted for the purposes
     * of accessibility.
     */
    Property EXTRACT_FOR_ACCESSIBILITY = Property.externalText(PREFIX + "extract_for_accessibility");

    /**
     * Can the user insert/rotate/delete pages.
     */
    Property ASSEMBLE_DOCUMENT = Property.externalText(PREFIX+"assemble_document");


    /**
     * Can the user fill in a form
     */
    Property FILL_IN_FORM = Property.externalText(PREFIX+"fill_in_form");

    /**
     * Can the user modify annotations
     */
    Property CAN_MODIFY_ANNOTATIONS = Property.externalText(PREFIX+"modify_annotations");

    /**
     * Can the user print the document
     */
    Property CAN_PRINT = Property.externalText(PREFIX+"can_print");

    /**
     * Can the user print an image-degraded version of the document.
     */
    Property CAN_PRINT_DEGRADED = Property.externalText(PREFIX+"can_print_degraded");

}
