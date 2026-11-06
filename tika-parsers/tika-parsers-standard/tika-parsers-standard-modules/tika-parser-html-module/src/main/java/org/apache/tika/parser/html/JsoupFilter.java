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
package org.apache.tika.parser.html;

import java.io.Serializable;

import org.jsoup.nodes.Document;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Rewrites the jsoup document {@link JSoupParser#parse} builds, before any SAX events are
 * emitted. Off unless one is set in the {@link ParseContext}: a single filter, or several in
 * order via {@link CompositeJsoupFilter} ({@code "jsoup-filters": [...]}).
 * <p>
 * To add one: implement this, annotate it
 * {@code @TikaComponent(name = "...", spi = false, contextKey = JsoupFilter.class)}, configure it
 * through bean setters. One instance serves concurrent parses, so keep it stateless.
 * The API is jsoup's, so a jsoup major-version change can break implementations.
 *
 * @since Apache Tika 4.3
 */
public interface JsoupFilter extends Serializable {

    /**
     * @return the document to emit: {@code document}, modified in place, or a replacement
     */
    Document filter(Document document, Metadata metadata, ParseContext context);
}
