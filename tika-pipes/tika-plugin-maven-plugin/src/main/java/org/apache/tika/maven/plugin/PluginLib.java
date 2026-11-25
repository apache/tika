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
package org.apache.tika.maven.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The list of jars that belong in a plugin's {@code lib/}, carried inside the plugin jar.
 * One line per jar: {@code groupId:artifactId:extension[:classifier]:version sha256}.
 * The plugin's own jar is not listed.
 */
public final class PluginLib {

    public static final String PATH = "META-INF/tika/plugin-lib.txt";

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._+-]+");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private PluginLib() {
    }

    public record Entry(String groupId, String artifactId, String extension, String classifier,
                        String version, String sha256) {

        public Entry {
            classifier = classifier == null ? "" : classifier;
            requireSegment("groupId", groupId);
            requireSegment("artifactId", artifactId);
            requireSegment("extension", extension);
            requireSegment("version", version);
            if (!classifier.isEmpty()) {
                requireSegment("classifier", classifier);
            }
            if (sha256 == null || !SHA256.matcher(sha256).matches()) {
                throw new IllegalArgumentException("not a sha256: " + sha256);
            }
        }

        public String coordinates() {
            return groupId + ":" + artifactId + ":" + extension
                    + (classifier.isEmpty() ? "" : ":" + classifier) + ":" + version;
        }

        public String fileName() {
            return libFileName(artifactId, version, classifier, extension);
        }

        public boolean isSnapshot() {
            return version.endsWith("-SNAPSHOT");
        }
    }

    public static String libFileName(String artifactId, String version, String classifier,
                                     String extension) {
        return artifactId + "-" + version
                + (classifier == null || classifier.isEmpty() ? "" : "-" + classifier)
                + "." + extension;
    }

    public static List<Entry> parse(InputStream is) throws IOException {
        List<Entry> entries = new ArrayList<>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split(" ");
            if (fields.length != 2) {
                throw new IOException("expected '<coordinates> <sha256>': " + line);
            }
            String[] c = fields[0].split(":", -1);
            try {
                if (c.length == 4) {
                    entries.add(new Entry(c[0], c[1], c[2], "", c[3], fields[1]));
                } else if (c.length == 5) {
                    entries.add(new Entry(c[0], c[1], c[2], c[3], c[4], fields[1]));
                } else {
                    throw new IOException("bad coordinates: " + line);
                }
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage() + " in: " + line, e);
            }
        }
        return entries;
    }

    public static String format(List<Entry> entries) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(Entry::coordinates));
        StringBuilder sb = new StringBuilder();
        sb.append("# groupId:artifactId:extension[:classifier]:version sha256\n");
        for (Entry e : sorted) {
            sb.append(e.coordinates()).append(' ').append(e.sha256()).append('\n');
        }
        return sb.toString();
    }

    private static void requireSegment(String name, String value) {
        if (value == null || !SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException("bad " + name + ": " + value);
        }
    }
}
