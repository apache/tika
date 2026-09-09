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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.RenderingParser;
import org.apache.tika.parser.enricher.CompositeContentEnricher;
import org.apache.tika.parser.enricher.ContentEnrichers;
import org.apache.tika.parser.enricher.EnrichingParser;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.utils.ParserUtils;

/**
 * Loader for parsers with support for:
 * <ul>
 *   <li>SPI fallback via "default-parser" marker with exclusions</li>
 *   <li>Mime type filtering decorations (_mime-include, _mime-exclude)</li>
 *   <li>EncodingDetector, Renderer and content-enricher dependency injection</li>
 * </ul>
 */
public class ParserLoader extends AbstractSpiComponentLoader<Parser> {

    private static final Logger LOG = LoggerFactory.getLogger(ParserLoader.class);

    public ParserLoader() {
        super("parsers", "default-parser", Parser.class);
    }

    @Override
    protected Parser loadComponent(String name, JsonNode configNode,
                                    LoaderContext context) throws TikaConfigException {
        try {
            // Extract framework config (decorations like _mime-include/_mime-exclude)
            FrameworkConfig framework = FrameworkConfig.extract(
                    configNode, context.getObjectMapper());

            // Instantiate the parser
            Parser parser = context.instantiate(name, framework.getComponentConfigNode());

            // Apply mime filtering decorations if present
            if (framework.getDecoration() != null && framework.getDecoration().hasFiltering()) {
                parser = applyMimeFiltering(parser, framework.getDecoration());
            }

            return parser;
        } catch (IOException e) {
            throw new TikaConfigException("Failed to load parser: " + name, e);
        }
    }

    @Override
    protected Parser createDefaultComposite(Set<Class<? extends Parser>> exclusions,
                                             LoaderContext context) {
        return new DefaultParser(
                TikaLoader.getMediaTypeRegistry(),
                new ServiceLoader(context.getClassLoader()),
                exclusions);
    }

    @Override
    protected Set<String> getAllowedMarkerKeys() {
        // ParserLoader honors framework mime-filter decorators on default-parser
        // in addition to the standard "exclude" key.
        return Set.of("exclude", "_mime-include", "_mime-exclude");
    }

    @Override
    protected Parser decorateDefaultComposite(Parser parser, JsonNode configNode,
                                               LoaderContext context) throws TikaConfigException {
        if (configNode == null) {
            return parser;
        }

        try {
            FrameworkConfig framework = FrameworkConfig.extract(
                    configNode, context.getObjectMapper());

            if (framework.getDecoration() != null && framework.getDecoration().hasFiltering()) {
                return applyMimeFiltering(parser, framework.getDecoration());
            }
        } catch (IOException e) {
            throw new TikaConfigException("Failed to apply mime filtering to default-parser", e);
        }

        return parser;
    }

    @Override
    protected Parser wrapInComposite(List<Parser> parsers, LoaderContext context) {
        return new CompositeParser(TikaLoader.getMediaTypeRegistry(), parsers);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<? extends Parser> unwrapClass(Parser component) {
        if (component instanceof ParserDecorator pd) {
            return (Class<? extends Parser>) pd.getWrappedParser().getClass();
        }
        return component.getClass();
    }

    @Override
    protected Parser postProcess(Parser parser, LoaderContext context)
            throws TikaConfigException {
        EncodingDetector encodingDetector = context.getEncodingDetector();
        Renderer renderer = context.getRenderer();
        injectDependenciesRecursively(parser, encodingDetector, renderer);
        return parser;
    }

    /**
     * Content enrichers are settled once, over the whole tree: the configured list, or
     * with none, one engine per type resolved from the enrichers among the loaded parsers.
     * Either way every {@link EnrichingParser} gets a composite (possibly empty), the
     * effective engines are logged, and an enricher under {@code "parsers"} that nothing
     * dispatches to is called out.
     */
    @Override
    protected Parser finish(Parser root, LoaderContext context) throws TikaConfigException {
        CompositeContentEnricher configured = context.getContentEnrichers();
        CompositeContentEnricher enrichers =
                configured != null ? configured : ContentEnrichers.resolve(root);
        logEnrichers(enrichers, configured != null);
        for (Parser inert : undispatchedEnrichers(root)) {
            warnUndispatched(inert, enrichers, configured != null);
        }
        injectContentEnrichers(root, enrichers);
        return root;
    }

    /**
     * Enrichers named directly under {@code "parsers"} that the composite never dispatches
     * to: every type they advertise is claimed by another parser there, or they advertise
     * none (engine unavailable, or told to skip). Both shapes look configured and do
     * nothing as parsers.
     */
    static List<Parser> undispatchedEnrichers(Parser root) {
        List<Parser> inert = new ArrayList<>();
        if (!(root instanceof CompositeParser composite) || root instanceof DefaultParser) {
            return inert;
        }
        ParseContext empty = new ParseContext();
        Map<MediaType, Parser> dispatch = composite.getParsers(empty);
        MediaTypeRegistry registry = composite.getMediaTypeRegistry();
        for (Parser member : composite.getAllComponentParsers()) {
            if (!ContentEnrichers.isEnricher(member)) {
                continue;
            }
            boolean dispatched = false;
            for (MediaType type : member.getSupportedTypes(empty)) {
                if (dispatch.get(registry.normalize(type)) == member) {
                    dispatched = true;
                    break;
                }
            }
            if (!dispatched) {
                inert.add(member);
            }
        }
        return inert;
    }

    private static void warnUndispatched(Parser inert, CompositeContentEnricher enrichers,
                                         boolean listConfigured) {
        String name = ParserUtils.getParserClassname(inert);
        Set<MediaType> advertised = inert.getSupportedTypes(new ParseContext());
        if (advertised.isEmpty()) {
            LOG.warn("{} under \"parsers\" advertises no media types (engine unavailable, or "
                    + "configured to skip) and never runs. To turn enrichment off, set "
                    + "\"content-enrichers\": [] instead.", name);
            return;
        }
        Set<MediaType> enriching = new TreeSet<>();
        for (MediaType type : enrichers.getSupportedTypes()) {
            if (enrichers.getEnrichers(type).contains(inert)) {
                enriching.add(type);
            }
        }
        if (enriching.isEmpty()) {
            LOG.warn("{} under \"parsers\" is never dispatched to (every type it advertises is "
                    + "claimed by another parser) and {}, so it never runs. Name it under "
                    + "\"content-enrichers\" to invoke it, or exclude the parser that "
                    + "claims its types to dispatch to it.", name, listConfigured
                    ? "\"content-enrichers\" does not name it"
                    : "another enricher is preferred for those types");
        } else {
            LOG.warn("{} under \"parsers\" is never dispatched to (every type it advertises is "
                    + "claimed by another parser); it acts only as the content enricher for "
                    + "{}. Name it under \"content-enrichers\" to say so.", name, enriching);
        }
    }

    private static void logEnrichers(CompositeContentEnricher enrichers, boolean configured) {
        if (enrichers.isEmpty()) {
            LOG.info("content enrichers: none{}; images and rendered pages are not enriched",
                    configured ? " (\"content-enrichers\": [])"
                            : " found among the loaded parsers");
            return;
        }
        Map<Parser, Set<MediaType>> byEngine = new IdentityHashMap<>();
        for (MediaType type : enrichers.getSupportedTypes()) {
            for (Parser member : enrichers.getEnrichers(type)) {
                byEngine.computeIfAbsent(member, k -> new TreeSet<>()).add(type);
            }
        }
        for (Map.Entry<Parser, Set<MediaType>> e : byEngine.entrySet()) {
            LOG.info("content enricher {} for {}{}", ParserUtils.getParserClassname(e.getKey()),
                    e.getValue(), configured ? ""
                            : " (found among the loaded parsers; name it under "
                            + "\"content-enrichers\" to pin it)");
        }
    }

    private static void injectContentEnrichers(Parser parser,
                                               CompositeContentEnricher enrichers) {
        if (parser instanceof EnrichingParser ep) {
            ep.setContentEnrichers(enrichers);
        }
        if (parser instanceof CompositeParser cp) {
            for (Parser child : cp.getAllComponentParsers()) {
                injectContentEnrichers(child, enrichers);
            }
        } else if (parser instanceof ParserDecorator pd) {
            injectContentEnrichers(pd.getWrappedParser(), enrichers);
        }
    }

    /**
     * Recursively inject dependencies into a parser and its children.
     */
    private void injectDependenciesRecursively(Parser parser, EncodingDetector encodingDetector,
                                                Renderer renderer) {
        if (encodingDetector != null && parser instanceof AbstractEncodingDetectorParser aedp) {
            aedp.setEncodingDetector(encodingDetector);
        }
        if (renderer != null && parser instanceof RenderingParser rp) {
            rp.setRenderer(renderer);
        }
        if (parser instanceof CompositeParser cp) {
            for (Parser child : cp.getAllComponentParsers()) {
                injectDependenciesRecursively(child, encodingDetector, renderer);
            }
        } else if (parser instanceof ParserDecorator pd) {
            injectDependenciesRecursively(pd.getWrappedParser(), encodingDetector, renderer);
        }
    }

    /**
     * Apply mime type filtering to a parser.
     * Uses ParserDecorator.withMimeFilters() which creates a MimeFilteringDecorator
     * that the serializer knows how to handle for round-trip support.
     */
    private Parser applyMimeFiltering(Parser parser,
                                       FrameworkConfig.ParserDecoration decoration)
            throws TikaConfigException {
        Set<MediaType> includeTypes = new HashSet<>();
        Set<MediaType> excludeTypes = new HashSet<>();

        for (String mimeStr : decoration.getMimeInclude()) {
            includeTypes.add(ComponentInstantiator.parseFilterType(mimeStr));
        }

        for (String mimeStr : decoration.getMimeExclude()) {
            excludeTypes.add(ComponentInstantiator.parseFilterType(mimeStr));
        }

        return ParserDecorator.withMimeFilters(parser, includeTypes, excludeTypes);
    }
}
