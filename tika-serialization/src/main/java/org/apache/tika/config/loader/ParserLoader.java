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
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.RenderingParser;
import org.apache.tika.renderer.Renderer;

/**
 * Loader for parsers with support for:
 * <ul>
 *   <li>SPI fallback via "default-parser" marker with exclusions</li>
 *   <li>Mime type filtering decorations (_mime-include, _mime-exclude)</li>
 *   <li>EncodingDetector and Renderer dependency injection</li>
 * </ul>
 */
public class ParserLoader extends AbstractSpiComponentLoader<Parser> {

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
    protected Parser postProcess(Parser parser, LoaderContext context)
            throws TikaConfigException {
        // Inject EncodingDetector and Renderer into parsers that need them
        EncodingDetector encodingDetector = context.getEncodingDetector();
        Renderer renderer = context.getRenderer();
        injectDependenciesRecursively(parser, encodingDetector, renderer);
        return parser;
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
