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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

    private static class RankedService implements Comparable<RankedService> {
        private Object service;
        private int rank;

        public RankedService(Object service, int rank) {
            this.service = service;
            this.rank = rank;
        }

        public boolean isInstanceOf(Class<?> iface) {
            return iface.isAssignableFrom(service.getClass());
        }

        public int compareTo(RankedService that) {
            return that.rank - rank; // highest number first
        }

    }

    /**
     * The dynamic set of services available in an OSGi environment.
     * Managed by the {@link TikaActivator} class and used as an additional
     * source of service instances in the {@link #loadServiceProviders(Class)}
     * method.
     */
    private static final Map<Object, RankedService> services =
            new HashMap<Object, RankedService>();

    /**
     * Returns the context class loader of the current thread. If such
     * a class loader is not available, then the loader of this class or
     * finally the system class loader is returned.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-441">TIKA-441</a>
     * @return context class loader, or <code>null</code> if no loader
     *         is available
     */
    static ClassLoader getContextClassLoader() {
        ClassLoader loader = contextClassLoader;
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

    static void addService(Object reference, Object service, int rank) {
        synchronized (services) {
            services.put(reference, new RankedService(service, rank));
        }
    }

    static Object removeService(Object reference) {
        synchronized (services) {
            return services.remove(reference);
        }
    }

    private final ClassLoader loader;

    private final LoadErrorHandler handler;
    private final InitializableProblemHandler initializableProblemHandler;

    private final boolean dynamic;

    public ServiceLoader(
            ClassLoader loader, LoadErrorHandler handler,
            InitializableProblemHandler initializableProblemHandler, boolean dynamic) {
        this.loader = loader;
        this.handler = handler;
        this.initializableProblemHandler = initializableProblemHandler;
        this.dynamic = dynamic;

    }
    public ServiceLoader(
            ClassLoader loader, LoadErrorHandler handler, boolean dynamic) {
        this(loader, handler, InitializableProblemHandler.WARN, dynamic);
    }

    public ServiceLoader(ClassLoader loader, LoadErrorHandler handler) {
        this(loader, handler, false);
    }

    public ServiceLoader(ClassLoader loader) {
    	this(loader, Boolean.getBoolean("org.apache.tika.service.error.warn") 
    			? LoadErrorHandler.WARN:LoadErrorHandler.IGNORE);
    }

    public ServiceLoader() {
    	this(getContextClassLoader(), Boolean.getBoolean("org.apache.tika.service.error.warn") 
    			? LoadErrorHandler.WARN:LoadErrorHandler.IGNORE, true);
    }
    
    /**
     * Returns if the service loader is static or dynamic
     * 
     * @return dynamic or static loading
     * @since Apache Tika 1.10
     */
    public boolean isDynamic() {
        return dynamic;
    }

    /**
     * Returns the load error handler used by this loader.
     *
     * @return load error handler
     * @since Apache Tika 1.3
     */
    public LoadErrorHandler getLoadErrorHandler() {
        return handler;
    }

    /**
     * Returns the handler for problems with initializables
     *
     * @return handler for problems with initializables
     * @since Apache Tika 1.15.1
     */
    public InitializableProblemHandler getInitializableProblemHandler() {
        return initializableProblemHandler;
    }

    /**
     * Returns an input stream for reading the specified resource from the
     * configured class loader.
     *
     * @param name resource name
     * @return input stream, or <code>null</code> if the resource was not found
     * @see ClassLoader#getResourceAsStream(String)
     * @since Apache Tika 1.1
     */
    public InputStream getResourceAsStream(String name) {
        if (loader != null) {
            return loader.getResourceAsStream(name);
        } else {
            return null;
        }
    }

    /**
     *
     * @return ClassLoader used by this ServiceLoader
     * @see #getContextClassLoader() for the context's ClassLoader
     * @since Apache Tika 1.15.1
     */
    public ClassLoader getLoader() {
        return loader;
    }

    /**
     * Loads and returns the named service class that's expected to implement
     * the given interface.
     * 
     * Note that this class does not use the {@link LoadErrorHandler}, a
     *  {@link ClassNotFoundException} is always returned for unknown
     *  classes or classes of the wrong type
     *
     * @param iface service interface
     * @param name service class name
     * @return service class
     * @throws ClassNotFoundException if the service class can not be found
     *                                or does not implement the given interface
     * @see Class#forName(String, boolean, ClassLoader)
     * @since Apache Tika 1.1
     */
    @SuppressWarnings("unchecked")
    public <T> Class<? extends T> getServiceClass(Class<T> iface, String name)
            throws ClassNotFoundException {
        if (loader == null) {
            throw new ClassNotFoundException(
                    "Service class " + name + " is not available");
        }
        Class<?> klass = Class.forName(name, true, loader);
        if (klass.isInterface()) {
            throw new ClassNotFoundException(
                    "Service class " + name + " is an interface");
        } else if (!iface.isAssignableFrom(klass)) {
            throw new ClassNotFoundException(
                    "Service class " + name
                    + " does not implement " + iface.getName());
        } else {
            return (Class<? extends T>) klass;
        }
    }

    /**
     * Returns all the available service resources matching the
     *  given pattern, such as all instances of tika-mimetypes.xml 
     *  on the classpath, or all org.apache.tika.parser.Parser 
     *  service files.
     */
    public Enumeration<URL> findServiceResources(String filePattern) {
       try {    	  
          Enumeration<URL> resources = loader.getResources(filePattern);
          return resources;
       } catch (IOException ignore) {
          // We couldn't get the list of service resource files
          List<URL> empty = Collections.emptyList();
          return Collections.enumeration( empty );
      }
    }

    /**
     * Returns all the available service providers of the given type.
     *
     * @param iface service provider interface
     * @return available service providers
     */
    public <T> List<T> loadServiceProviders(Class<T> iface) {
        List<T> providers = new ArrayList<T>();
        providers.addAll(loadDynamicServiceProviders(iface));
        providers.addAll(loadStaticServiceProviders(iface));
        return providers;
    }

    /**
     * Returns the available dynamic service providers of the given type.
     * The returned list is newly allocated and may be freely modified
     * by the caller.
     *
     * @since Apache Tika 1.2
     * @param iface service provider interface
     * @return dynamic service providers
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> loadDynamicServiceProviders(Class<T> iface) {
        if (dynamic) {
            synchronized (services) {
                List<RankedService> list =
                        new ArrayList<RankedService>(services.values());
                Collections.sort(list);

                List<T> providers = new ArrayList<T>(list.size());
                for (RankedService service : list) {
                    if (service.isInstanceOf(iface)) {
                        providers.add((T) service.service);
                    }
                }
                return providers;
            }
        } else {
            return new ArrayList<T>(0);
        }
    }

    /**
     * Returns the defined static service providers of the given type, without
     * attempting to load them.
     * The providers are loaded using the service provider mechanism using
     * the configured class loader (if any).
     *
     * @since Apache Tika 1.6
     * @param iface service provider interface
     * @return static list of uninitialised service providers
     */
    protected <T> List<String> identifyStaticServiceProviders(Class<T> iface) {
        List<String> names = new ArrayList<String>();

        if (loader != null) {
            String serviceName = iface.getName();
            Enumeration<URL> resources =
                    findServiceResources("META-INF/services/" + serviceName);
            for (URL resource : Collections.list(resources)) {
                try {
                    collectServiceClassNames(resource, names);
                } catch (IOException e) {
                    handler.handleLoadError(serviceName, e);
                }
            }
        }
        
        return names;
    }

    /**
     * Returns the available static service providers of the given type.
     * The providers are loaded using the service provider mechanism using
     * the configured class loader (if any). The returned list is newly
     * allocated and may be freely modified by the caller.
     *
     * @since Apache Tika 1.2
     * @param iface service provider interface
     * @return static service providers
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> loadStaticServiceProviders(Class<T> iface) {
        List<T> providers = new ArrayList<T>();

        if (loader != null) {
            List<String> names = identifyStaticServiceProviders(iface);

            for (String name : names) {
                try {
                    Class<?> klass = loader.loadClass(name);
                    if (iface.isAssignableFrom(klass)) {
                        T instance = (T) klass.newInstance();
                        if (instance instanceof Initializable) {
                            ((Initializable)instance).checkInitialization(initializableProblemHandler);
                        }
                        providers.add(instance);
                    }
                } catch (Throwable t) {
                    handler.handleLoadError(name, t);
                }
            }
        }
        return providers;
    }

    private static final Pattern COMMENT = Pattern.compile("#.*");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private void collectServiceClassNames(URL resource, Collection<String> names)
            throws IOException {
        try (InputStream stream = resource.openStream()) {
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(stream, UTF_8));
            String line = reader.readLine();
            while (line != null) {
                line = COMMENT.matcher(line).replaceFirst("");
                line = WHITESPACE.matcher(line).replaceAll("");
                if (line.length() > 0) {
                    names.add(line);
                }
                line = reader.readLine();
            }
        }
    }

}
