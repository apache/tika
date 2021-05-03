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
package org.apache.tika.eval.core.util;

import java.util.Collections;
import java.util.Map;

public class ContentTags {

    public static final ContentTags EMPTY_CONTENT_TAGS = new ContentTags();
    final Map<String, Integer> tags;
    final String content;
    boolean parseException;

    private ContentTags() {
        this("", Collections.EMPTY_MAP, false);
    }

    public ContentTags(String content) {
        this(content, Collections.emptyMap(), false);
    }

    public ContentTags(String content, boolean parseException) {
        this(content, Collections.emptyMap(), parseException);
    }

    public ContentTags(String content, Map<String, Integer> tags) {
        this(content, tags, false);
    }

    private ContentTags(String content, Map<String, Integer> tags, boolean parseException) {
        this.content = content;
        this.tags = tags;
        this.parseException = parseException;
    }

    public String getContent() {
        return content;
    }

    public Map<String, Integer> getTags() {
        return tags;
    }

    public boolean getParseException() {
        return parseException;
    }

    public void setParseException(boolean parseException) {
        this.parseException = parseException;
    }
}
