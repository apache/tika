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
 * Keys written by {@code org.apache.tika.parser.html.ReadabilityJsoupFilter}. Values are
 * Readability's own strings;
 * {@link #PUBLISHED_TIME} is not parsed.
 *
 * @since Apache Tika 4.3
 */
public interface ReadabilityMetadata {

    String PREFIX = TikaCoreProperties.TIKA_META_PREFIX + "readability:";

    String STATUS_EXTRACTED = "extracted";
    /** {@code isProbablyReaderable} said no and it is required; the whole document was emitted. */
    String STATUS_NOT_READERABLE = "not-readerable";
    /** Nothing article-like was found; the whole document was emitted. */
    String STATUS_NO_ARTICLE = "no-article";
    /** A size or work limit was exceeded; the whole document was emitted. */
    String STATUS_LIMIT = "limit-exceeded";
    /** Readability failed; the whole document was emitted. */
    String STATUS_ERROR = "error";

    Property STATUS = Property.reservedInternalClosedChoice(PREFIX + "status",
            STATUS_EXTRACTED, STATUS_NOT_READERABLE, STATUS_NO_ARTICLE, STATUS_LIMIT, STATUS_ERROR);
    /** Result of Readability's {@code isProbablyReaderable} pre-check, recorded whether or not it is required. */
    Property READERABLE = Property.reservedInternalBoolean(PREFIX + "readerable");
    Property TITLE = Property.reservedInternalText(PREFIX + "title");
    Property BYLINE = Property.reservedInternalText(PREFIX + "byline");
    Property EXCERPT = Property.reservedInternalText(PREFIX + "excerpt");
    Property SITE_NAME = Property.reservedInternalText(PREFIX + "site-name");
    Property PUBLISHED_TIME = Property.reservedInternalText(PREFIX + "published-time");
    Property LANG = Property.reservedInternalText(PREFIX + "lang");
    Property DIR = Property.reservedInternalText(PREFIX + "dir");
}
