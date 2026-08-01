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

public interface RTFMetadata {
    // TIKA-4794: rtf_meta -> rtf (bare format name, matches pdf:/zip:/dwg:; "_meta" was redundant).
    String PREFIX_RTF_META = "rtf";

    // TIKA-4794: rtf_pict -> rtf:pict (sub-namespace, no underscore). Kept SEPARATE from the fixed
    // rtf: keys below: the suffix here is file-controlled (the RTF \sn property name), so a crafted
    // doc must not be able to forge a fixed rtf: key. PICT registers it as an open namespace.
    String RTF_PICT_META_PREFIX = "rtf:pict:";

    /** Open (file-controlled) RTF embedded-object property names built off {@link #RTF_PICT_META_PREFIX}. */
    PassthroughPrefix PICT = PassthroughPrefix.file(RTF_PICT_META_PREFIX,
            "RTF embedded picture-object property pairs (sn/sv); file-controlled names");

    /**
     * if set to true, this means that an image file is probably a "thumbnail"
     * any time a pict/emf/wmf is in an object
     */
    Property THUMBNAIL = Property.internalBoolean(
            PREFIX_RTF_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "thumbnail");

    /**
     * if an application and version is given as part of the
     * embedded object, this is the literal string
     */
    Property EMBEDDED_APP_VERSION = Property.internalText(
            PREFIX_RTF_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "embedded-app-version");

    Property EMBEDDED_CLASS = Property.internalText(
            PREFIX_RTF_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "embedded-class");

    Property EMBEDDED_TOPIC = Property.internalText(
            PREFIX_RTF_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "embedded-topic");

    Property EMBEDDED_ITEM = Property.internalText(
            PREFIX_RTF_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "embedded-item");

    Property CONTAINS_ENCAPSULATED_HTML = Property.internalBoolean(
            PREFIX_RTF_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "contains-encapsulated-html");

}
