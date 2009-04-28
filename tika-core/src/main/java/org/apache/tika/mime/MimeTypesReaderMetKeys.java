/**
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

package org.apache.tika.mime;

/**
 * 
 * Met Keys used by the {@link MimeTypesReader}.
 */
public interface MimeTypesReaderMetKeys {

    public static final String MIME_INFO_TAG = "mime-info";

    public static final String MIME_TYPE_TAG = "mime-type";

    public static final String MIME_TYPE_TYPE_ATTR = "type";

    public static final String COMMENT_TAG = "_comment";

    public static final String GLOB_TAG = "glob";

    public static final String ISREGEX_ATTR = "isregex";

    public static final String PATTERN_ATTR = "pattern";

    public static final String MAGIC_TAG = "magic";

    public static final String ALIAS_TAG = "alias";

    public static final String ALIAS_TYPE_ATTR = "type";

    public static final String ROOT_XML_TAG = "root-XML";

    public static final String SUB_CLASS_OF_TAG = "sub-class-of";

    public static final String SUB_CLASS_TYPE_ATTR = "type";

    public static final String MAGIC_PRIORITY_ATTR = "priority";

    public static final String MATCH_TAG = "match";

    public static final String MATCH_OFFSET_ATTR = "offset";

    public static final String MATCH_TYPE_ATTR = "type";

    public static final String MATCH_VALUE_ATTR = "value";

    public static final String MATCH_MASK_ATTR = "mask";

    public static final String NS_URI_ATTR = "namespaceURI";

    public static final String LOCAL_NAME_ATTR = "localName";

}
