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
import java.util.Iterator;
import java.util.List;

import javax.imageio.spi.ServiceRegistry;

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

    /**
     * Returns the context class loader of the current thread. If such
     * a class loader is not available, then the loader of this class or
     * finally the system class loader is returned.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-441">TIKA-441</a>
     * @return context class loader, or <code>null</code> if no loader
     *         is available
     */
    private static ClassLoader getContextClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = DefaultParser.class.getClassLoader();
        }
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        return loader;
    }

    /**
     * Returns all the parsers available through the given class loader.
     *
     * @param loader class loader 
     * @return available parsers
     */
    private static List<Parser> loadParsers(ClassLoader loader) {
        List<Parser> parsers = new ArrayList<Parser>();
        if (loader != null) {
            Iterator<Parser> iterator =
                ServiceRegistry.lookupProviders(Parser.class, loader);
            while (iterator.hasNext()) {
                parsers.add(iterator.next());
            }
        }
        return parsers;
    }

    public DefaultParser(ClassLoader loader) {
        super(new MediaTypeRegistry(), loadParsers(loader));
    }

    public DefaultParser() {
        this(getContextClassLoader());
    }

}
