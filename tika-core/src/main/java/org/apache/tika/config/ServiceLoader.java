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
package org.apache.tika.config;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.spi.ServiceRegistry;

/**
 * Internal utility class that Tika uses to look up service providers.
 *
 * @since Apache Tika 0.9
 */
public class ServiceLoader {

    /**
     * The default context class loader to use for all threads, or
     * <code>null</code> to automatically select the context class loader.
     */
    private static volatile ClassLoader contextClassLoader = null;

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
        ClassLoader loader = contextClassLoader;
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        if (loader == null) {
            loader = ServiceLoader.class.getClassLoader();
        }
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        return loader;
    }

    /**
     * Sets the context class loader to use for all threads that access
     * this class. Used for example in an OSGi environment to avoid problems
     * with the default context class loader.
     *
     * @param loader default context class loader,
     *               or <code>null</code> to automatically pick the loader
     */
    public static void setContextClassLoader(ClassLoader loader) {
        contextClassLoader = loader;
    }

    private final ClassLoader loader;

    public ServiceLoader(ClassLoader loader) {
        this.loader = loader;
    }

    public ServiceLoader() {
        this(getContextClassLoader());
    }

    /**
     * Returns all the available service providers of the given type.
     *
     * @param service service provider interface
     * @return available service providers
     */
    public <T> List<T> loadServiceProviders(Class<T> service) {
        List<T> providers = new ArrayList<T>();

        if (loader != null) {
            Iterator<T> iterator =
                ServiceRegistry.lookupProviders(service, loader);
            while (iterator.hasNext()) {
                providers.add(iterator.next());
            }
        }

        return providers;
    }

}
