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
package org.apache.tika.readability;

import org.jsoup.nodes.Element;

/**
 * Result of {@link Readability#parse()}. Any field but {@code content} may be null.
 *
 * @param content detached wrapper element; its single child is
 *                {@code <div id="readability-page-1" class="page">}
 */
public record Article(String title, String byline, String dir, String lang, Element content,
                      String textContent, String excerpt, String siteName, String publishedTime) {

    /** The {@code content} Readability.js returns: the wrapper's inner HTML. */
    public String contentHtml() {
        return content.html();
    }

    public int length() {
        return textContent.length();
    }
}
