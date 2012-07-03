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
 * Core properties as defined in the Office Open XML specification part Two that are not
 * in the DublinCore namespace.
 * There is also a keyword property definition in the specification which is omitted here, 
 * because Tika should stick to the DublinCore/IPTC definition. 
 * 
 * @see <a href="http://www.iso.org/iso/iso_catalogue/catalogue_tc/catalogue_detail.htm?csnumber=59575"
 *        >ISO document of Office Open XML specification</a>
 * @see <a href="http://www.ecma-international.org/publications/standards/Ecma-376.htm
 *        >ECMA document of Office Open XML specification</a> 
 */
public interface OfficeOpenXMLCore 
{
	String NAMESPACE_URI = "http://schemas.openxmlformats.org/package/2006/metadata/core-properties/";
	String PREFIX = "cp";
	
	/**
     * A categorization of the content of this package.
     */
    Property CATEGORY = Property.externalText(
    		PREFIX + Metadata.NAMESPACE_PREFIX_DELIMITER + "category");
    
    /**
     * The status of the content.
     */
    Property CONTENT_STATUS = Property.externalText(
    		PREFIX + Metadata.NAMESPACE_PREFIX_DELIMITER + "contentStatus");
    
    /**
     * The user who performed the last modification. The identification is environment-specific.
     */
    Property LAST_MODIFIED_BY = Property.externalText(
    		PREFIX + Metadata.NAMESPACE_PREFIX_DELIMITER + "lastModifiedBy");
    
    /**
     * The date and time of the last printing.
     */
    Property LAST_PRINTED = Property.externalDate(
    		PREFIX + Metadata.NAMESPACE_PREFIX_DELIMITER + "lastPrinted");
    
    /**
     * The revision number.
     */
    Property REVISION = Property.externalText(
    		PREFIX + Metadata.NAMESPACE_PREFIX_DELIMITER + "revision");
    
    /**
     * The version number. This value is set by the user or by the application.
     */
    Property VERSION = Property.externalText(
    		PREFIX + Metadata.NAMESPACE_PREFIX_DELIMITER + "version");
    
    /**
     * The document's subject.
     */
    Property SUBJECT = Property.externalText(
                PREFIX + Metadata.NAMESPACE_PREFIX_DELIMITER + "subject");
}
