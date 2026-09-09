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
package org.apache.tika.parser.enricher;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;

/**
 * Media-type-keyed registry of content enrichers: ordinary {@link Parser}s that a container
 * parser <em>invokes</em> on bytes it has already parsed (OCR text for an image or a
 * rendered PDF page), rather than being dispatched to by the composite parser. Configured
 * as the top-level {@code "content-enrichers"} list, mirroring {@code "renderers"}.
 * <p>
 * Members advertise their <em>real</em> media types ({@code image/png}). A legacy engine
 * still advertising the retired {@code image/ocr-*} pseudo-types is keyed under the real
 * type, with a warning, until 5.0. An enricher does not compete with the parser registered
 * for the same type: that parser still runs and calls the enricher.
 *
 * @since Apache Tika 4.1
 */
public class CompositeContentEnricher implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<MediaType, List<Parser>> enricherMap;

    public CompositeContentEnricher(List<Parser> enrichers) {
        Map<MediaType, List<Parser>> tmp = new HashMap<>();
        ParseContext empty = new ParseContext();
        for (Parser enricher : enrichers) {
            Set<MediaType> excluded = excludedRealTypes(enricher);
            for (MediaType mediaType : enricher.getSupportedTypes(empty)) {
                if (ContentEnrichers.isLegacyOcrType(mediaType)) {
                    ContentEnrichers.warnLegacyAdvertisement(unwrap(enricher));
                }
                MediaType keyType = ContentEnrichers.stripLegacyOcrPrefix(mediaType.getBaseType());

                if (excluded.contains(keyType)) {
                    continue;
                }
                List<Parser> forType = tmp.computeIfAbsent(keyType, k -> new ArrayList<>());
                if (!forType.contains(enricher)) {
                    forType.add(enricher);
                }
            }
        }
        tmp.replaceAll((k, v) -> Collections.unmodifiableList(v));
        this.enricherMap = Collections.unmodifiableMap(tmp);
    }

    // decorator excludes are literal; "image/tiff" must also drop a legacy "image/ocr-tiff"
    private static Set<MediaType> excludedRealTypes(Parser enricher) {
        if (!(enricher instanceof ParserDecorator.MimeFilteringDecorator decorator)) {
            return Collections.emptySet();
        }
        Set<MediaType> excluded = new HashSet<>();
        for (MediaType excludeType : decorator.getExcludeTypes()) {
            excluded.add(excludeType.getBaseType());
        }
        return excluded;
    }

    private static Parser unwrap(Parser parser) {
        while (parser instanceof ParserDecorator decorator) {
            parser = decorator.getWrappedParser();
        }
        return parser;
    }

    /**
     * @return the enrichers for this media type in config order, empty when none;
     *         parameters are ignored, alias normalization is the caller's job
     */
    public List<Parser> getEnrichers(MediaType mediaType) {
        List<Parser> enrichers = enricherMap.get(mediaType.getBaseType());
        return enrichers == null ? Collections.emptyList() : enrichers;
    }

    public Set<MediaType> getSupportedTypes() {
        return enricherMap.keySet();
    }
}
