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

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in mechanism for adding user-supplied "extras" jars (extra
 * {@code EncodingDetector}s, {@code Parser}s, etc.) to Tika's SPI discovery
 * without repackaging the application.
 *
 * <p><b>Off by default.</b> Nothing is loaded unless the
 * {@value #EXTRAS_DIR_PROPERTY} system property points at a directory; then every
 * {@code *.jar} in it is made visible to service-loading.  There is no implicit
 * directory, and the working directory is never used.
 *
 * <p><b>Security:</b> this is a trusted code directory — anything in it runs with
 * the full privileges of the Tika process.  Treat write access to it exactly like
 * write access to {@code lib/}; it must not be writable by less-trusted principals
 * (for servers, not reachable by request handling).  Being opt-in keeps "we are
 * now loading extra code" an explicit, auditable choice.
 */
public final class TikaExtras {

    /** System property naming the extras directory.  Unset = feature off. */
    public static final String EXTRAS_DIR_PROPERTY = "tika.extras.dir";

    private static final Logger LOG = LoggerFactory.getLogger(TikaExtras.class);

    private TikaExtras() {
    }

    /**
     * If {@value #EXTRAS_DIR_PROPERTY} is set, installs a classloader over the
     * {@code *.jar} files in that directory as the thread + Tika
     * {@link ServiceLoader} context classloader, so they join SPI discovery.
     * No-op (returns {@code null}) when the property is unset or the directory is
     * missing/empty.  Call once, before any Tika component is loaded.
     *
     * @return the installed classloader, or {@code null} if extras are off/empty
     */
    public static ClassLoader install() {
        List<Path> jars = extraJars();
        if (jars.isEmpty()) {
            return null;
        }
        List<URL> urls = new ArrayList<>(jars.size());
        List<Path> loaded = new ArrayList<>(jars.size());
        for (Path jar : jars) {
            try {
                urls.add(jar.toUri().toURL());
                loaded.add(jar);
            } catch (Exception e) {
                LOG.warn("Skipping extra jar {}: {}", jar, e.toString());
            }
        }
        if (urls.isEmpty()) {
            return null;
        }
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        if (parent == null) {
            parent = TikaExtras.class.getClassLoader();
        }
        URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), parent);
        Thread.currentThread().setContextClassLoader(cl);
        ServiceLoader.setContextClassLoader(cl);
        LOG.info("{}: loaded {} extra jar(s): {}", EXTRAS_DIR_PROPERTY, loaded.size(), loaded);
        return cl;
    }

    /**
     * The {@code *.jar} files in the {@value #EXTRAS_DIR_PROPERTY} directory — for
     * callers that extend a forked process's classpath rather than installing a
     * classloader.  Empty when the property is unset or the directory is
     * missing/has no jars.
     */
    public static List<Path> extraJars() {
        Path dir = extrasDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path jar : stream) {
                jars.add(jar);
            }
        } catch (Exception e) {
            LOG.warn("Could not scan {}={}: {}", EXTRAS_DIR_PROPERTY, dir, e.toString());
        }
        // Sort by file name so jar load order (classloader URL order / forked-child
        // classpath order, hence SPI precedence) is deterministic across platforms
        // and filesystems rather than depending on directory iteration order.
        jars.sort(Comparator.comparing(jar -> jar.getFileName().toString()));
        return jars;
    }

    /**
     * Appends the {@link #extraJars()} (as absolute paths, joined with the
     * platform path separator) to the given classpath string — for extending a
     * forked process's {@code -cp} with the extras jars.  Returns {@code classpath}
     * unchanged when the feature is off or the directory has no jars.
     *
     * @param classpath the base classpath to extend
     * @return the classpath with any extras jars appended
     */
    public static String appendJarsToClasspath(String classpath) {
        List<Path> jars = extraJars();
        if (jars.isEmpty()) {
            return classpath;
        }
        String separator = System.getProperty("path.separator");
        StringBuilder sb = new StringBuilder();
        if (classpath != null && !classpath.isEmpty()) {
            sb.append(classpath);
        }
        for (Path jar : jars) {
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(jar.toAbsolutePath());
        }
        return sb.toString();
    }

    /** The configured extras directory, or {@code null} if the feature is off. */
    public static Path extrasDir() {
        String prop = System.getProperty(EXTRAS_DIR_PROPERTY);
        if (prop == null || prop.isBlank()) {
            return null;
        }
        return Path.of(prop.trim());
    }
}
