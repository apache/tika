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

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.enricher.CompositeContentEnricher;

/**
 * Loads the top-level {@code "content-enrichers"} list: parsers selected by component name
 * that container parsers invoke for derived content (OCR, ...). Members come from the same
 * registry as {@code "parsers"} entries but never join the composite's media-type dispatch.
 * Null when the key is absent: {@link ParserLoader} then resolves the enrichers from the
 * loaded parsers.
 */
class ContentEnricherLoader implements ComponentLoader<CompositeContentEnricher> {

    @Override
    public CompositeContentEnricher load(TikaJsonConfig config, LoaderContext context)
            throws TikaConfigException {
        List<Map.Entry<String, JsonNode>> entries = config.getArrayComponents("content-enrichers");
        if (entries.isEmpty()) {
            // [] is an explicit "nothing", authoritative; an absent key means "find them"
            return config.hasComponentSection("content-enrichers")
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
                        "Failed to load content enricher: " + entry.getKey(), e);
            }
            // lifetime snapshot: an empty engine must fail load, not go inert; ask the
            // engine itself, since a _mime-include answers for the decorator
            if (unwrap(enricher).getSupportedTypes(empty).isEmpty()) {
                throw new TikaConfigException("Content enricher \"" + entry.getKey()
                        + "\" advertises no media types (a _mime-include list does not "
                        + "count). Is the engine unavailable (missing native binary, "
                        + "unreachable inference server) or configured to skip enrichment?");
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
}
