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
 *
 * IPTC Metadata Descriptions taken from the IPTC Photo Metadata (July 2010) 
 * standard. These parts Copyright 2010 International Press Telecommunications 
 * Council.
 */
package org.apache.tika.metadata;

/**
 * XMP Rights management schema. 
 * 
 * A collection of property constants for the 
 * rights management properties defined in the XMP 
 * standard.
 * 
 * @since Apache Tika 1.2
 * @see <a href="http://partners.adobe.com/public/developer/en/xmp/sdk/XMPspecification.pdf">XMP Photoshop</a>
 */
public interface XMPRights {

    String NAMESPACE_URI_XMP_RIGHTS = "http://ns.adobe.com/xap/1.0/rights/";
    String PREFIX_XMP_RIGHTS = "xmpRights";
    
    Property CERTIFICATE = Property.internalText(
            PREFIX_XMP_RIGHTS + Metadata.NAMESPACE_PREFIX_DELIMITER + "Certificate");
    
    Property MARKED = Property.internalBoolean(
            PREFIX_XMP_RIGHTS + Metadata.NAMESPACE_PREFIX_DELIMITER + "Marked");
    
    Property OWNER = Property.internalTextBag(
            PREFIX_XMP_RIGHTS + Metadata.NAMESPACE_PREFIX_DELIMITER + "Owner");
    
    Property USAGE_TERMS = Property.internalText(
            PREFIX_XMP_RIGHTS + Metadata.NAMESPACE_PREFIX_DELIMITER + "UsageTerms");
    
    Property WEB_STATEMENT = Property.internalText(
            PREFIX_XMP_RIGHTS + Metadata.NAMESPACE_PREFIX_DELIMITER + "WebStatement");

}