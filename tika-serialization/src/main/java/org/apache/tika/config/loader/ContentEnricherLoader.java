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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.apache.tika.parser.enricher.SizeGatedEnricher;
import org.apache.tika.parser.enricher.TextRecognizer;
import org.apache.tika.parser.inference.Engine;
import org.apache.tika.parser.inference.EngineRegistry;

/**
 * Loads {@code "text-recognizers"}. An entry either names an engine from {@code "engines"}
 * ({@code {"engine": "tesseract", "_mime-include": [...]}}) or carries the engine inline
 * ({@code {"tesseract-ocr-parser": {...}}}), the 4.0 form. An empty list is authoritative;
 * an absent key means the recognizers are resolved from the loaded parsers.
 */
class ContentEnricherLoader implements ComponentLoader<CompositeContentEnricher> {

    static final String KEY = "text-recognizers";
    private static final Logger LOG = LoggerFactory.getLogger(ContentEnricherLoader.class);
    private static final Set<String> REFERENCE_KEYS = Set.of("engine", "_mime-include",
            "_mime-exclude", "_min-width", "_min-height");

    @Override
    public CompositeContentEnricher load(TikaJsonConfig config, LoaderContext context)
            throws TikaConfigException {
        JsonNode node = config.getRootNode().get(KEY);
        if (node == null) {
            return null;
        }
        if (!node.isArray()) {
            throw new TikaConfigException("\"" + KEY + "\" must be an array");
        }
        List<Parser> enrichers = new ArrayList<>();
        ParseContext empty = new ParseContext();
        for (JsonNode item : node) {
            String label;
            Parser enricher;
            if (item.isObject() && item.has("engine")) {
                label = "engine \"" + item.get("engine").asText() + "\"";
                int[] gate = sizeGate(item, label);
                enricher = sizeGated(referenced(item, context), gate);
            } else {
                Map.Entry<String, JsonNode> entry = inline(item, context);
                label = "\"" + entry.getKey() + "\"";
                // taken before the engine is built, which would refuse the keys
                int[] gate = sizeGate(entry.getValue(), label);
                enricher = sizeGated(instantiate(entry, context), gate);
            }
            // lifetime snapshot: an empty engine must fail load, not go inert; ask the
            // engine itself, since a _mime-include answers for the decorator
            if (unwrap(enricher).getSupportedTypes(empty).isEmpty()) {
                throw new TikaConfigException("Text recognizer " + label
                        + " advertises no media types (a _mime-include list does not "
                        + "count). Is the engine unavailable (missing native binary, "
                        + "unreachable inference server) or configured to skip enrichment?");
            }
            // an engine that declines to recognize (a VLM with textRecognizer false) warns too
            TextRecognizer recognizer = ContentEnrichers.asTextRecognizer(enricher);
            if ((recognizer == null || !recognizer.recognizesText(empty))
                    && !advertisesLegacyOcr(enricher)) {
                LOG.warn("\"text-recognizers\" entry {} recognizes no text; it runs as an "
                        + "annotator on the images and pages it is offered (4.2 gives annotators "
                        + "a list of their own)", label);
            }
            enrichers.add(enricher);
        }
        return new CompositeContentEnricher(enrichers);
    }

    /** The engine the entry names, from {@code "engines"}, behind the entry's mime filters. */
    private static Parser referenced(JsonNode item, LoaderContext context)
            throws TikaConfigException {
        Iterator<String> names = item.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!REFERENCE_KEYS.contains(name)) {
                throw new TikaConfigException("\"" + KEY + "\" entry naming an engine has "
                        + "unknown key \"" + name + "\"; known: " + REFERENCE_KEYS
                        + ". The engine's own settings belong under \"engines\".");
            }
        }
        JsonNode nameNode = item.get("engine");
        if (!nameNode.isTextual()) {
            throw new TikaConfigException("\"" + KEY + "\" entry: \"engine\" must be a name "
                    + "from \"engines\"");
        }
        String engineName = nameNode.asText();
        EngineRegistry engines = context.get(EngineRegistry.class);
        Engine engine = engines == null ? null : engines.get(engineName);
        if (engine == null) {
            throw new TikaConfigException("\"" + KEY + "\" entry names engine \"" + engineName
                    + "\", which is not in \"engines\"");
        }
        if (!(engine instanceof Parser parser) || !ContentEnrichers.isEnricher(parser)) {
            throw new TikaConfigException("\"" + KEY + "\" entry names engine \"" + engineName
                    + "\" (" + engine.getClass().getName() + "), which is not a text "
                    + "recognizer: a recognizer implements ContentEnricher");
        }
        return ComponentInstantiator.withMimeFilters(parser, item);
    }

    /**
     * The entry's {@code _min-width}/{@code _min-height}, removed from the node so the engine
     * never sees them; null when neither is set. An image of unknown size passes the gate,
     * and one below {@link ContentEnrichers#MIN_PIXELS} never reaches dispatch anyway.
     */
    private static int[] sizeGate(JsonNode settings, String label) throws TikaConfigException {
        if (!settings.isObject()) {
            return null;
        }
        Integer minWidth = minimum(settings, "_min-width", label);
        Integer minHeight = minimum(settings, "_min-height", label);
        if (minWidth == null && minHeight == null) {
            return null;
        }
        return new int[] {minWidth == null ? 0 : minWidth, minHeight == null ? 0 : minHeight};
    }

    private static Parser sizeGated(Parser enricher, int[] gate) {
        return gate == null ? enricher : new SizeGatedEnricher(enricher, gate[0], gate[1]);
    }

    private static Integer minimum(JsonNode settings, String field, String label)
            throws TikaConfigException {
        JsonNode v = settings.get(field);
        if (v == null) {
            return null;
        }
        if (!v.isIntegralNumber() || v.asInt() < 0) {
            throw new TikaConfigException("\"" + KEY + "\" entry " + label + ": \"" + field
                    + "\" must be a non-negative integer");
        }
        ((ObjectNode) settings).remove(field);
        return v.asInt();
    }

    /** The 4.0 form: {@code "name"} or {@code {"name": {...}}}, first field only. */
    private static Map.Entry<String, JsonNode> inline(JsonNode item, LoaderContext context)
            throws TikaConfigException {
        if (item.isTextual()) {
            return Map.entry(item.asText(), (JsonNode) context.getObjectMapper().createObjectNode());
        }
        if (item.isObject() && !item.isEmpty()) {
            Map.Entry<String, JsonNode> first = item.fields().next();
            return Map.entry(first.getKey(), first.getValue());
        }
        throw new TikaConfigException("\"" + KEY + "\" entries are {\"engine\": \"<name>\"} "
                + "or {\"<recognizer>\": {...}}; got " + item);
    }

    private static Parser instantiate(Map.Entry<String, JsonNode> entry, LoaderContext context)
            throws TikaConfigException {
        try {
            ObjectNode wrapper = context.getObjectMapper().createObjectNode();
            wrapper.set(entry.getKey(), entry.getValue());
            return context.getObjectMapper().treeToValue(wrapper, Parser.class);
        } catch (Exception e) {
            throw new TikaConfigException("Failed to load text recognizer: " + entry.getKey(), e);
        }
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
