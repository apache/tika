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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.imageio.spi.ServiceRegistry;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.mime.MediaTypeRegistry;

/**
 * A composite parser based on all the {@link Parser} implementations
 * available through the {@link ServiceRegistry service provider mechanism}.
 *
 * @since Apache Tika 0.8
 */
public class DefaultParser extends CompositeParser {
    /** Serial version UID */
    private static final long serialVersionUID = 3612324825403757520L;

    private static List<Parser> getDefaultParsers(ServiceLoader loader) {
        // Find all the Parsers available as services
        List<Parser> svcParsers = loader.loadServiceProviders(Parser.class);
        List<Parser> parsers = new ArrayList<Parser>(svcParsers.size());

        // Sort the list by classname, rather than discovery order 
        Collections.sort(svcParsers, new Comparator<Parser>() {
           public int compare(Parser p1, Parser p2) {
              return p1.getClass().getName().compareTo(
                   p2.getClass().getName());
           }
        });
        
        // CompositeParser takes the last parser for any given mime type, so put the 
        // TikaParsers first so that non-Tika (user supplied) parsers can take presidence
        for (Parser p : svcParsers) {
           if (p.getClass().getName().startsWith("org.apache.tika")) {
              parsers.add(p);
           }
        }
        for (Parser p : svcParsers) {
           if (!p.getClass().getName().startsWith("org.apache.tika")) {
              parsers.add(p);
           }
        }
        
        return parsers;
    }

    public DefaultParser(MediaTypeRegistry registry, ServiceLoader loader) {
        super(registry, getDefaultParsers(loader));
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

}
