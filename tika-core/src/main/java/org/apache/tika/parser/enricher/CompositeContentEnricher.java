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
import java.util.IdentityHashMap;
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
 * as the top-level {@code "content-enrichers"} list, mirroring {@code "renderers"}; with no
 * list, the loader builds one from the enrichers among the loaded parsers
 * ({@link ContentEnrichers#resolve}).
 * <p>
 * Members are keyed by their real media types; a legacy {@code image/ocr-*} advertisement
 * is keyed under the real type, with a warning, until 5.0.
 *
 * @since Apache Tika 4.1
 */
public class CompositeContentEnricher implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<MediaType, List<Parser>> enricherMap;

    // members that only advertise retired pseudo-types; counted as text recognizers until 5.0
    private final Set<Parser> legacyMembers;

    public CompositeContentEnricher(List<Parser> enrichers) {
        Map<MediaType, List<Parser>> tmp = new HashMap<>();
        Set<Parser> legacy = identitySet();
        ParseContext empty = new ParseContext();
        for (Parser enricher : enrichers) {
            Set<MediaType> excluded = excludedRealTypes(enricher);
            for (MediaType mediaType : enricher.getSupportedTypes(empty)) {
                if (ContentEnrichers.isLegacyOcrType(mediaType)) {
                    ContentEnrichers.warnLegacyAdvertisement(unwrap(enricher));
                    if (!(unwrap(enricher) instanceof ContentEnricher)) {
                        legacy.add(enricher);
                    }
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
        this.legacyMembers = legacy;
    }

    private CompositeContentEnricher(Map<MediaType, Parser> winners, Set<Parser> legacy) {
        Map<MediaType, List<Parser>> tmp = new HashMap<>();
        for (Map.Entry<MediaType, Parser> e : winners.entrySet()) {
            tmp.put(e.getKey(), Collections.singletonList(e.getValue()));
        }
        this.enricherMap = Collections.unmodifiableMap(tmp);
        this.legacyMembers = legacy;
    }

    /** One engine per type, as {@link ContentEnrichers#resolve} picked them. */
    static CompositeContentEnricher resolved(Map<MediaType, Parser> winners,
                                             Set<Parser> legacyMembers) {
        Set<Parser> legacy = identitySet();
        legacy.addAll(legacyMembers);
        return new CompositeContentEnricher(winners, legacy);
    }

    private static Set<Parser> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
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

    /** True when no type has an enricher: enrichment is off. */
    public boolean isEmpty() {
        return enricherMap.isEmpty();
    }

    /**
     * Whether a member returned by {@link #getEnrichers} is a pre-4.1 engine that advertises
     * only {@code image/ocr-*}; such an engine counts as a text recognizer until 5.0.
     */
    public boolean isLegacyClaimant(Parser member) {
        return legacyMembers.contains(member);
    }
}
