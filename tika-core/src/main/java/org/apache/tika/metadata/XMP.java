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

public interface XMP {

    String NAMESPACE_URI = "http://ns.adobe.com/xap/1.0/";

    String PREFIX = "xmp";

    /** The xmp prefix followed by the colon delimiter */
    String PREFIX_ = PREFIX + Metadata.NAMESPACE_PREFIX_DELIMITER;

    /**
     * The date and time the resource was created. For a digital file, this need not
     * match a file-system creation time. For a freshly created resource, it should
     * be close to that time, modulo the time taken to write the file. Later file
     * transfer, copying, and so on, can make the file-system time arbitrarily different.
     */
    Property CREATE_DATE = Property.externalDate(PREFIX_ + "CreateDate");

    /**
     * The name of the first known tool used to create the resource.
     */
    Property CREATOR_TOOL = Property.externalText(PREFIX_ + "CreatorTool");

    /**
     * An unordered array of text strings that unambiguously identify the resource
     * within a given context. An array item may be qualified with xmpidq:Scheme
     * (see 8.7, “xmpidq namespace”) to denote the formal identification system to
     * which that identifier conforms.
     */
    Property IDENTIFIER = Property.externalTextBag(PREFIX_ + "Identifier");

    /**
     * A word or short phrase that identifies a resource as a member of a userdefined collection.
     */
    Property LABEL = Property.externalDate(PREFIX_ + "Label");

    /**
     * The date and time that any metadata for this resource was last changed. It
     * should be the same as or more recent than xmp:ModifyDate
     */
    Property METADATA_DATE = Property.externalDate(PREFIX_ + "MetadataDate");

    /**
     * The date and time the resource was last modified.
     */
    Property MODIFY_DATE = Property.externalDate(PREFIX_ + "ModifyDate");

    /**
     * A user-assigned rating for this file. The value shall be -1 or in the range
     * [0..5], where -1 indicates “rejected” and 0 indicates “unrated”. If xmp:Rating
     * is not present, a value of 0 should be assumed.
     */
    Property RATING = Property.externalReal(PREFIX_ + "Rating");

}
