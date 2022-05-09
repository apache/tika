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
 * Metadata properties for paged text, metadata appropriate
 * for an individual page (useful for embedded document handlers
 * called on individual pages).
 *
 * Use {@link PagedText} where possible
 */
public interface TikaPagedText {
    String TIKA_PAGED_TEXT_PREFIX = "tika_pg:";
    /**
     * 1-based page number for a specific page
     */
    Property PAGE_NUMBER = Property.internalInteger(TIKA_PAGED_TEXT_PREFIX + "page_number");

    Property PAGE_ROTATION = Property.internalRational(TIKA_PAGED_TEXT_PREFIX + "page_rotation");

}
