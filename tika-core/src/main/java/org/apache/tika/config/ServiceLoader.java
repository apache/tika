/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.ServiceLoaderUtils;

public class ServiceLoader {
    private static final Map<Object, RankedService> SERVICES = new HashMap<>();
    private static final ClassLoader CONTEXT_CLASS_LOADER = null;
    private final ClassLoader loader;
    private final LoadErrorHandler handler;
    private final InitializableProblemHandler initializableProblemHandler;
    private final boolean dynamic;

    public ServiceLoader(ClassLoader loader, LoadErrorHandler handler,
                         InitializableProblemHandler initializableProblemHandler, boolean dynamic) {
        this.loader = loader;
        this.handler = handler;
        this.initializableProblemHandler = initializableProblemHandler;
        this.dynamic = dynamic;
    }

    public ServiceLoader(ClassLoader loader, LoadErrorHandler handler, boolean dynamic) {
        this(loader, handler, InitializableProblemHandler.WARN, dynamic);
    }

    public ServiceLoader(ClassLoader loader, LoadErrorHandler handler) {
        this(loader, handler, false);
    }

    public ServiceLoader(ClassLoader loader) {
        this(loader,
                Boolean.getBoolean("org.apache.tika.service.error.warn") ? LoadErrorHandler.WARN :
                        LoadErrorHandler.IGNORE);
    }

    public ServiceLoader() {
        this(getContextClassLoader(),
                Boolean.getBoolean("org.apache.tika.service.error.warn") ? LoadErrorHandler.WARN :
                        LoadErrorHandler.IGNORE, true);
    }

    public static ClassLoader getContextClassLoader() {
        ClassLoader loader = CONTEXT_CLASS_LOADER;
        if (loader == null) loader = ServiceLoader.class.getClassLoader();
        if (loader == null) loader = ClassLoader.getSystemClassLoader();
        return loader;
    }

    static void addService(Object reference, Object service, int rank) {
        synchronized (SERVICES) {
            SERVICES.put(reference, new RankedService(service, rank));
        }
    }

    static void removeService(Object reference) {
        synchronized (SERVICES) {
            SERVICES.remove(reference);
        }
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public LoadErrorHandler getLoadErrorHandler() {
        return handler;
    }

    public InitializableProblemHandler getInitializableProblemHandler() {
        return initializableProblemHandler;
    }

    public InputStream getResourceAsStream(String name) {
        return loader != null ? loader.getResourceAsStream(name) : null;
    }

    public ClassLoader getLoader() {
        return loader;
    }

    public <T> Class<? extends T> getServiceClass(Class<T> iface, String name) throws ClassNotFoundException {
        if (loader == null) {
            throw new ClassNotFoundException("Service class " + name + " is not available");
        }
        Class<?> klass = Class.forName(name, true, loader);
        if (klass.isInterface()) {
            throw new ClassNotFoundException("Service class " + name + " is an interface");
        } else if (!iface.isAssignableFrom(klass)) {
            throw new ClassNotFoundException("Service class " + name + " does not implement " + iface.getName());
        } else {
            return (Class<? extends T>) klass;
        }
    }

    public Enumeration<URL> findServiceResources(String filePattern) {
        try {
            return loader.getResources(filePattern);
        } catch (IOException ignore) {
            return Collections.enumeration(Collections.emptyList());
        }
    }

    public <T> List<T> loadServiceProviders(Class<T> iface) {
        List<T> tmp = new ArrayList<>();
        tmp.addAll(loadDynamicServiceProviders(iface));
        tmp.addAll(loadStaticServiceProviders(iface));

        List<T> providers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (T provider : tmp) {
            if (!seen.contains(provider.getClass().getCanonicalName())) {
                providers.add(provider);
                seen.add(provider.getClass().getCanonicalName());
            }
        }
        return providers;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> loadDynamicServiceProviders(Class<T> iface) {
        if (dynamic) {
            synchronized (SERVICES) {
                List<RankedService> list = new ArrayList<>(SERVICES.values());
                Collections.sort(list);

                List<T> providers = new ArrayList<>(list.size());
                for (RankedService service : list) {
                    if (service.isInstanceOf(iface)) {
                        providers.add((T) service.service);
                    }
                }
                return providers;
            }
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    protected <T> List<String> identifyStaticServiceProviders(Class<T> iface) {
        List<String> names = new ArrayList<>();

        if (loader != null) {
            String serviceName = iface.getName();
            Enumeration<URL> resources = findServiceResources("META-INF/services/" + serviceName);
            for (URL resource : Collections.list(resources)) {
                try {
                    ServiceResourceUtils.collectServiceClassNames(resource, names);
                } catch (IOException e) {
                    handler.handleLoadError(serviceName, e);
                }
            }
        }

        return names;
    }

    public <T> List loadStaticServiceProviders(Class<T> iface) {
        return loadStaticServiceProviders(iface, Collections.EMPTY_SET);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> loadStaticServiceProviders(Class<T> iface, Collection<Class<? extends T>> excludes) {
        List<T> providers = new ArrayList<>();

        if (loader != null) {
            List<String> names = identifyStaticServiceProviders(iface);
            for (String name : names) {
                try {
                    Class<?> klass = loader.loadClass(name);
                    if (iface.isAssignableFrom(klass)) {
                        boolean shouldExclude = false;
                        for (Class<? extends T> ex : excludes) {
                            if (ex.isAssignableFrom(klass)) {
                                shouldExclude = true;
                                break;
                            }
                        }
                        if (!shouldExclude) {
                            T instance = ServiceLoaderUtils.newInstance(klass, this);
                            if (instance instanceof Initializable) {
                                ((Initializable) instance).initialize(Collections.EMPTY_MAP);
                                ((Initializable) instance).checkInitialization(initializableProblemHandler);
                            }
                            providers.add(instance);
                        }
                    } else {
                        throw new TikaConfigException("Class " + name + " is not of type: " + iface);
                    }
                } catch (Throwable t) {
                    handler.handleLoadError(name, t);
                }
            }
        }
        return providers;
    }
}
