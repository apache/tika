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
package org.apache.tika.config.loader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.enricher.CompositeContentEnricher;
import org.apache.tika.parser.enricher.ContentEnrichers;

/**
 * Loads the top-level {@code "text-recognizers"} list: parsers selected by component name
 * that container parsers invoke for derived content (OCR, ...). Members come from the same
 * registry as {@code "parsers"} entries but never join the composite's media-type dispatch.
 * Null when the key is absent: {@link ParserLoader} then resolves the enrichers from the
 * loaded parsers.
 */
class ContentEnricherLoader implements ComponentLoader<CompositeContentEnricher> {

    private static final Logger LOG = LoggerFactory.getLogger(ContentEnricherLoader.class);

    @Override
    public CompositeContentEnricher load(TikaJsonConfig config, LoaderContext context)
            throws TikaConfigException {
        List<Map.Entry<String, JsonNode>> entries = config.getArrayComponents("text-recognizers");
        if (entries.isEmpty()) {
            // [] is an explicit "nothing", authoritative; an absent key means "find them"
            return config.hasComponentSection("text-recognizers")
                    ? new CompositeContentEnricher(List.of()) : null;
        }
        List<Parser> enrichers = new ArrayList<>();
        ParseContext empty = new ParseContext();
        for (Map.Entry<String, JsonNode> entry : entries) {
            Parser enricher;
            try {
                ObjectNode wrapper = context.getObjectMapper().createObjectNode();
                wrapper.set(entry.getKey(), entry.getValue());
                enricher = context.getObjectMapper().treeToValue(wrapper, Parser.class);
            } catch (Exception e) {
                throw new TikaConfigException(
                        "Failed to load text recognizer: " + entry.getKey(), e);
            }
            // lifetime snapshot: an empty engine must fail load, not go inert; ask the
            // engine itself, since a _mime-include answers for the decorator
            if (unwrap(enricher).getSupportedTypes(empty).isEmpty()) {
                throw new TikaConfigException("Text recognizer \"" + entry.getKey()
                        + "\" advertises no media types (a _mime-include list does not "
                        + "count). Is the engine unavailable (missing native binary, "
                        + "unreachable inference server) or configured to skip enrichment?");
            }
            if (ContentEnrichers.asTextRecognizer(enricher) == null && !advertisesLegacyOcr(enricher)) {
                LOG.warn("\"text-recognizers\" entry \"{}\" recognizes no text; it runs as an "
                        + "annotator on the images and pages it is offered (4.2 gives annotators "
                        + "a list of their own)", entry.getKey());
            }
            enrichers.add(enricher);
        }
        return new CompositeContentEnricher(enrichers);
    }

    private static Parser unwrap(Parser parser) {
        while (parser instanceof ParserDecorator decorator) {
            parser = decorator.getWrappedParser();
        }
        return parser;
    }

    // a pre-4.1 engine advertising image/ocr-* counts as a recognizer until 5.0
    private static boolean advertisesLegacyOcr(Parser enricher) {
        for (org.apache.tika.mime.MediaType type
                : unwrap(enricher).getSupportedTypes(new ParseContext())) {
            if (ContentEnrichers.isLegacyOcrType(type)) {
                return true;
            }
        }
        return false;
    }
}
