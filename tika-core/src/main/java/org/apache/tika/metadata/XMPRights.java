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

    /** The xmpRights prefix followed by the colon delimiter */
    String PREFIX_ = PREFIX_XMP_RIGHTS + ":";

    /**
     * A Web URL for a rights management certificate.
     */
    Property CERTIFICATE = Property.internalText(PREFIX_ + "Certificate");

    /**
     * When true, indicates that this is a rights-managed resource. When
     * false, indicates that this is a public-domain resource. Omit if the
     * state is unknown.
     */
    Property MARKED = Property.internalBoolean(PREFIX_ + "Marked");

    /**
     * A list of legal owners of the resource.
     */
    Property OWNER = Property.internalTextBag(PREFIX_ + "Owner");

    /**
     * A word or short phrase that identifies a resource as a member of a userdefined collection.
     * TODO This is actually a language alternative property
     */
    Property USAGE_TERMS = Property.internalText(PREFIX_ + "UsageTerms");

    /**
     * A Web URL for a statement of the ownership and usage rights for this resource.
     */
    Property WEB_STATEMENT = Property.internalText(PREFIX_ + "WebStatement");

}
