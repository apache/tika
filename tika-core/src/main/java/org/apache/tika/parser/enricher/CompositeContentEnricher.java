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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Media-type-keyed registry of content enrichers: ordinary {@link Parser}s that a
 * container parser <em>invokes</em> on bytes it has already parsed to obtain derived
 * content (OCR text for an image, for a rendered PDF page, ...), rather than being
 * dispatched to by the composite parser.
 * <p>
 * Configured as the top-level {@code "content-enrichers"} list, mirroring
 * {@code "renderers"}; members advertise their <em>real</em> media types
 * ({@code image/png}). Legacy OCR engines that still advertise the {@code image/ocr-*}
 * pseudo-types are keyed under the corresponding real type, so they are nameable here
 * without modification. An enricher registered here does not
 * compete with the parser registered for the same type: the parser still runs and calls
 * the enricher.
 * <p>
 * <b>Every</b> enricher matching a media type runs, in config order — e.g. an OCR engine
 * followed by a VLM tagger for the same image. Output lands at the caller's chosen
 * position in that order. Failures are best-effort: one enricher's failure does not stop
 * the others; the first failure is rethrown after the chain completes with later ones
 * suppressed. Timeouts, SecurityException and SAXException abort the chain immediately.
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
            for (MediaType mediaType : enricher.getSupportedTypes(empty)) {
                // legacy engines (Tesseract, VLM, ...) still advertise the image/ocr-*
                // pseudo-types; key them under the real type so they are nameable here
                MediaType keyType = stripLegacyOcrPrefix(mediaType.getBaseType());
                List<Parser> forType = tmp.computeIfAbsent(keyType, k -> new ArrayList<>());
                if (!forType.contains(enricher)) {
                    forType.add(enricher);
                }
            }
        }
        tmp.replaceAll((k, v) -> Collections.unmodifiableList(v));
        this.enricherMap = Collections.unmodifiableMap(tmp);
    }

    private static MediaType stripLegacyOcrPrefix(MediaType mediaType) {
        String subtype = mediaType.getSubtype();
        if (subtype.startsWith(LegacyDispatchEnricher.OCR_MEDIATYPE_PREFIX)) {
            return new MediaType(mediaType.getType(),
                    subtype.substring(LegacyDispatchEnricher.OCR_MEDIATYPE_PREFIX.length()));
        }
        return mediaType;
    }

    /**
     * @return the enrichers configured for this media type (parameters ignored; alias
     *         normalization is the caller's job), in config order; empty when none
     */
    public List<Parser> getEnrichers(MediaType mediaType) {
        List<Parser> enrichers = enricherMap.get(mediaType.getBaseType());
        return enrichers == null ? Collections.emptyList() : enrichers;
    }

    public Set<MediaType> getSupportedTypes() {
        return enricherMap.keySet();
    }
}
