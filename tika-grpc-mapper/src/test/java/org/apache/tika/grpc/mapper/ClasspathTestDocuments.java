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
package org.apache.tika.grpc.mapper;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Discovers {@code test-documents/*} fixtures contributed by parser module test-jars on the classpath.
 */
final class ClasspathTestDocuments {

    private static final String TEST_DOCUMENTS_DIR = "test-documents";

    private ClasspathTestDocuments() {
    }

    static List<String> listByExtension(String extension) throws IOException {
        String suffix = extension.startsWith(".") ? extension : "." + extension;
        Set<String> found = new TreeSet<>();
        ClassLoader classLoader = ClasspathTestDocuments.class.getClassLoader();
        Enumeration<URL> roots = classLoader.getResources(TEST_DOCUMENTS_DIR);
        while (roots.hasMoreElements()) {
            collectFromUrl(roots.nextElement(), suffix, found);
        }
        return new ArrayList<>(found);
    }

    static List<String> listByExtensions(String... extensions) throws IOException {
        Set<String> found = new TreeSet<>();
        for (String extension : extensions) {
            found.addAll(listByExtension(extension));
        }
        return new ArrayList<>(found);
    }

    private static void collectFromUrl(URL url, String suffix, Set<String> found) throws IOException {
        if ("file".equals(url.getProtocol())) {
            try {
                Path dir = Paths.get(url.toURI());
                if (Files.isDirectory(dir)) {
                    collectFromDirectory(dir, suffix, found);
                }
            } catch (java.net.URISyntaxException e) {
                throw new IOException("Bad test-documents URL: " + url, e);
            }
            return;
        }
        if ("jar".equals(url.getProtocol())) {
            collectFromJarUrl(url, suffix, found);
        }
    }

    private static void collectFromDirectory(Path dir, String suffix, Set<String> found) throws IOException {
        try (var paths = Files.walk(dir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .forEach(path -> found.add(path.getFileName().toString()));
        }
    }

    private static void collectFromJarUrl(URL url, String suffix, Set<String> found) throws IOException {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        String prefix = connection.getEntryName();
        if (prefix == null) {
            prefix = TEST_DOCUMENTS_DIR;
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        JarFile jarFile = connection.getJarFile();
        final String entryPrefix = prefix;
        for (JarEntry entry : jarFile.stream().toList()) {
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (!name.startsWith(entryPrefix) || !name.endsWith(suffix)) {
                continue;
            }
            int slash = name.lastIndexOf('/');
            found.add(slash >= 0 ? name.substring(slash + 1) : name);
        }
    }

}
