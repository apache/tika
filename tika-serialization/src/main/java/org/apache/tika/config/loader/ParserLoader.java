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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.RenderingParser;
import org.apache.tika.parser.enricher.CompositeContentEnricher;
import org.apache.tika.parser.enricher.EnrichingParser;
import org.apache.tika.renderer.Renderer;

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
        CompositeContentEnricher contentEnrichers = context.getContentEnrichers();
        injectDependenciesRecursively(parser, encodingDetector, renderer, contentEnrichers);
        if (contentEnrichers == null) {
            warnOnAmbiguousOcrRegistrations(parser);
        }
        return parser;
    }

    /**
     * Recursively inject dependencies into a parser and its children.
     */
    private void injectDependenciesRecursively(Parser parser, EncodingDetector encodingDetector,
                                                Renderer renderer,
                                                CompositeContentEnricher contentEnrichers) {
        if (encodingDetector != null && parser instanceof AbstractEncodingDetectorParser aedp) {
            aedp.setEncodingDetector(encodingDetector);
        }
        if (renderer != null && parser instanceof RenderingParser rp) {
            rp.setRenderer(renderer);
        }
        if (contentEnrichers != null && parser instanceof EnrichingParser dp) {
            dp.setContentEnrichers(contentEnrichers);
        }
        if (parser instanceof CompositeParser cp) {
            for (Parser child : cp.getAllComponentParsers()) {
                injectDependenciesRecursively(child, encodingDetector, renderer, contentEnrichers);
            }
        } else if (parser instanceof ParserDecorator pd) {
            injectDependenciesRecursively(pd.getWrappedParser(), encodingDetector, renderer,
                    contentEnrichers);
        }
    }

    /**
     * Several OCR engines can claim the same image/ocr-* pseudo-type -- availability is
     * environmental -- and the composite resolves the collision silently by last
     * registration; name the collision and the winner once at load. The caller skips this
     * when content-enrichers is configured: that list is authoritative, so legacy dispatch
     * never runs and the advice is already taken.
     */
    private void warnOnAmbiguousOcrRegistrations(Parser parser) {
        if (!(parser instanceof CompositeParser cp)) {
            return;
        }
        ParseContext empty = new ParseContext();
        Map<MediaType, List<Parser>> duplicates = cp.findDuplicateParsers(empty);
        if (duplicates.isEmpty()) {
            return;
        }
        Map<MediaType, Parser> winners = cp.getParsers(empty);
        for (Map.Entry<MediaType, List<Parser>> e : duplicates.entrySet()) {
            if (!e.getKey().getSubtype().startsWith("ocr-")) {
                continue;
            }
            StringBuilder claimants = new StringBuilder();
            for (Parser p : e.getValue()) {
                if (claimants.length() > 0) {
                    claimants.append(", ");
                }
                claimants.append(p.getClass().getName());
            }
            Parser winner = winners.get(e.getKey());
            LOG.warn("Multiple OCR engines claim {}: [{}]; {} wins by registration order. "
                            + "Select one explicitly with \"content-enrichers\".",
                    e.getKey(), claimants,
                    winner == null ? "unknown" : winner.getClass().getName());
        }
    }

    /**
     * Apply mime type filtering to a parser.
     * Uses ParserDecorator.withMimeFilters() which creates a MimeFilteringDecorator
     * that the serializer knows how to handle for round-trip support.
     */
    private Parser applyMimeFiltering(Parser parser,
                                       FrameworkConfig.ParserDecoration decoration) {
        Set<MediaType> includeTypes = new HashSet<>();
        Set<MediaType> excludeTypes = new HashSet<>();

        for (String mimeStr : decoration.getMimeInclude()) {
            includeTypes.add(MediaType.parse(mimeStr));
        }

        for (String mimeStr : decoration.getMimeExclude()) {
            excludeTypes.add(MediaType.parse(mimeStr));
        }

        return ParserDecorator.withMimeFilters(parser, includeTypes, excludeTypes);
    }
}
