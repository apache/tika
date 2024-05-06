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
 * Extended properties as defined in the Office Open XML specification part Four. Those properties
 * are omitted which have equivalent properties defined in the ODF namespace like "word count". Also
 * not all properties from the specification are defined here, yet. Only those which have been in
 * use by the parsers so far.
 *
 * @see <a
 *     href="http://www.iso.org/iso/iso_catalogue/catalogue_tc/catalogue_detail.htm?csnumber=59575"
 *     >ISO document of Office Open XML specification</a>
 * @see <a href="http://www.ecma-international.org/publications/standards/Ecma-376.htm" >ECMA
 *     document of Office Open XML specification</a>
 */
public interface OfficeOpenXMLExtended {
    String NAMESPACE_URI =
            "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties/";
    String WORD_PROCESSING_NAMESPACE_URI =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    String PREFIX = "extended-properties";
    String WORD_PROCESSING_PREFIX = "w";
    String SECURITY_NONE = "None";
    String SECURITY_PASSWORD_PROTECTED = "PasswordProtected";
    String SECURITY_READ_ONLY_RECOMMENDED = "ReadOnlyRecommended";
    String SECURITY_READ_ONLY_ENFORCED = "ReadOnlyEnforced";
    String SECURITY_LOCKED_FOR_ANNOTATIONS = "LockedForAnnotations";
    String SECURITY_UNKNOWN = "Unknown";

    Property TEMPLATE =
            Property.externalText(
                    PREFIX + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "Template");

    Property MANAGER =
            Property.externalTextBag(
                    PREFIX + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "Manager");

    Property COMPANY =
            Property.externalText(
                    PREFIX + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "Company");

    Property PRESENTATION_FORMAT =
            Property.externalText(
                    PREFIX + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "PresentationFormat");

    Property NOTES =
            Property.externalInteger(
                    PREFIX + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "Notes");

    Property TOTAL_TIME =
            Property.externalInteger(
                    PREFIX + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "TotalTime");

    Property HIDDEN_SLIDES =
            Property.externalInteger(
                    PREFIX + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "HiddedSlides");

    Property APPLICATION =
            Property.externalText(
                    PREFIX + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "Application");

    Property APP_VERSION =
            Property.externalText(
                    PREFIX + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "AppVersion");
    // Integer flag
    Property DOC_SECURITY =
            Property.externalInteger(
                    PREFIX + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "DocSecurity");

    // Human readable string explaining doc security flag
    Property DOC_SECURITY_STRING =
            Property.externalClosedChoise(
                    PREFIX + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "DocSecurityString",
                    SECURITY_NONE,
                    SECURITY_PASSWORD_PROTECTED,
                    SECURITY_READ_ONLY_RECOMMENDED,
                    SECURITY_READ_ONLY_ENFORCED,
                    SECURITY_LOCKED_FOR_ANNOTATIONS,
                    SECURITY_UNKNOWN);

    Property COMMENTS =
            Property.externalTextBag(
                    WORD_PROCESSING_PREFIX
                            + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER
                            + "Comments");
}
