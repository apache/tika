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

import java.util.List;

import org.jsoup.nodes.Document;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Runs {@link JsoupFilter}s in order, each on the previous one's result. Configured as
 * {@code "parse-context": {"jsoup-filters": ["my-filter", {"readability-jsoup-filter": {...}}]}}.
 *
 * @since Apache Tika 4.3
 */
@TikaComponent(name = "jsoup-filters", spi = false, contextKey = JsoupFilter.class)
public class CompositeJsoupFilter implements JsoupFilter {

    private static final long serialVersionUID = 1L;

    private final List<JsoupFilter> filters;

    public CompositeJsoupFilter(List<JsoupFilter> filters) {
        this.filters = List.copyOf(filters);
    }

    public List<JsoupFilter> getFilters() {
        return filters;
    }

    @Override
    public Document filter(Document document, Metadata metadata, ParseContext context) {
        for (JsoupFilter f : filters) {
            document = f.filter(document, metadata, context);
        }
        return document;
    }
}
