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
package org.apache.tika.mime;

/**
 * Met Keys used by the {@link MimeTypesReader}.
 */
public interface MimeTypesReaderMetKeys {

    String MIME_INFO_TAG = "mime-info";

    String MIME_TYPE_TAG = "mime-type";

    String MIME_TYPE_TYPE_ATTR = "type";

    String COMMENT_TAG = "_comment";

    String GLOB_TAG = "glob";

    String ISREGEX_ATTR = "isregex";

    String PATTERN_ATTR = "pattern";

    String MAGIC_TAG = "magic";

    String ALIAS_TAG = "alias";

    String ALIAS_TYPE_ATTR = "type";

    String ROOT_XML_TAG = "root-XML";

    String SUB_CLASS_OF_TAG = "sub-class-of";

    String SUB_CLASS_TYPE_ATTR = "type";

    String MAGIC_PRIORITY_ATTR = "priority";

    String MATCH_TAG = "match";

    String MATCH_OFFSET_ATTR = "offset";

    String MATCH_TYPE_ATTR = "type";

    String MATCH_VALUE_ATTR = "value";

    String MATCH_MASK_ATTR = "mask";

    String NS_URI_ATTR = "namespaceURI";

    String LOCAL_NAME_ATTR = "localName";

}
