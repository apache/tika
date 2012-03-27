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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

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
    private static List<Parser> getDefaultParsers(ServiceLoader loader) {
        List<Parser> parsers =
                loader.loadStaticServiceProviders(Parser.class);
        Collections.sort(parsers, new Comparator<Parser>() {
            public int compare(Parser p1, Parser p2) {
                String n1 = p1.getClass().getName();
                String n2 = p2.getClass().getName();
                boolean t1 = n1.startsWith("org.apache.tika.");
                boolean t2 = n2.startsWith("org.apache.tika.");
                if (t1 == t2) {
                    return n1.compareTo(n2);
                } else if (t1) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });
        return parsers;
    }

    private transient final ServiceLoader loader;

    public DefaultParser(MediaTypeRegistry registry, ServiceLoader loader) {
        super(registry, getDefaultParsers(loader));
        this.loader = loader;
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
            for (Parser parser
                    : loader.loadDynamicServiceProviders(Parser.class)) {
                for (MediaType type : parser.getSupportedTypes(context)) {
                    map.put(registry.normalize(type), parser);
                }
            }
        }

        return map;
    }

}
