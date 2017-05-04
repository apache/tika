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
package org.apache.tika.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.utils.ServiceLoaderUtils;

/**
 * A composite parser based on all the {@link Parser} implementations
 * available through the
 * {@link javax.imageio.spi.ServiceRegistry service provider mechanism}.
 *
 * @since Apache Tika 0.8
 */
public class DefaultParser extends CompositeParser {

    /** Serial version UID */
    private static final long serialVersionUID = 3612324825403757520L;

    /**
     * Finds all statically loadable parsers and sort the list by name,
     * rather than discovery order. CompositeParser takes the last
     * parser for any given media type, so put the Tika parsers first
     * so that non-Tika (user supplied) parsers can take precedence.
     *
     * @param loader service loader
     * @return ordered list of statically loadable parsers
     */
    private static List<Parser> getDefaultParsers(ServiceLoader loader,
                                                  EncodingDetector encodingDetector) {
        List<Parser> parsers = loader.loadStaticServiceProviders(Parser.class);

        if (encodingDetector != null) {
            for (Parser p : parsers) {
                setEncodingDetector(p, encodingDetector);
            }
        }
        ServiceLoaderUtils.sortLoadedClasses(parsers);
        return parsers;
    }

    //recursively go through the parsers and set the encoding detector
    //as configured in the config file
    private static void setEncodingDetector(Parser p, EncodingDetector encodingDetector) {
        if (p instanceof AbstractEncodingDetectorParser) {
            ((AbstractEncodingDetectorParser)p).setEncodingDetector(encodingDetector);
        } else if (p instanceof CompositeParser) {
            for (Parser child : ((CompositeParser)p).getAllComponentParsers()) {
                setEncodingDetector(child, encodingDetector);
            }
        } else if (p instanceof ParserDecorator) {
            setEncodingDetector(((ParserDecorator)p).getWrappedParser(), encodingDetector);
        }
    }

    private transient final ServiceLoader loader;

    public DefaultParser(MediaTypeRegistry registry, ServiceLoader loader,
                         Collection<Class<? extends Parser>> excludeParsers,
                         EncodingDetector encodingDetector) {
        super(registry, getDefaultParsers(loader, encodingDetector), excludeParsers);
        this.loader = loader;
    }

    public DefaultParser(MediaTypeRegistry registry, ServiceLoader loader,
                         Collection<Class<? extends Parser>> excludeParsers) {
        super(registry, getDefaultParsers(loader, new DefaultEncodingDetector(loader)), excludeParsers);
        this.loader = loader;
    }

    public DefaultParser(MediaTypeRegistry registry, ServiceLoader loader, EncodingDetector encodingDetector) {
        this(registry, loader, null, encodingDetector);
    }

    public DefaultParser(MediaTypeRegistry registry, ServiceLoader loader) {
        this(registry, loader, null, new DefaultEncodingDetector(loader));
    }

    public DefaultParser(MediaTypeRegistry registry, ClassLoader loader) {
        this(registry, new ServiceLoader(loader));
    }

    public DefaultParser(ClassLoader loader) {
        this(MediaTypeRegistry.getDefaultRegistry(), new ServiceLoader(loader));
    }

    public DefaultParser(MediaTypeRegistry registry) {
        this(registry, new ServiceLoader());
    }

    public DefaultParser() {
        this(MediaTypeRegistry.getDefaultRegistry());
    }

    @Override
    public Map<MediaType, Parser> getParsers(ParseContext context) {
        Map<MediaType, Parser> map = super.getParsers(context);

        if (loader != null) {
            // Add dynamic parser service (they always override static ones)
            MediaTypeRegistry registry = getMediaTypeRegistry();
            List<Parser> parsers =
                    loader.loadDynamicServiceProviders(Parser.class);
            Collections.reverse(parsers); // best parser last
            for (Parser parser : parsers) {
                for (MediaType type : parser.getSupportedTypes(context)) {
                    map.put(registry.normalize(type), parser);
                }
            }
        }

        return map;
    }

    @Override
    public List<Parser> getAllComponentParsers() {
        List<Parser> parsers = super.getAllComponentParsers();
        if (loader != null) {
            parsers = new ArrayList<Parser>(parsers);
            parsers.addAll(loader.loadDynamicServiceProviders(Parser.class));
        }
        return parsers;
    }
}
