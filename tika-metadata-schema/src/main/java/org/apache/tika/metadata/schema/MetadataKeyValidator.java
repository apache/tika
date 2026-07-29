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
package org.apache.tika.metadata.schema;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Registry-driven lint: classifies a metadata key against the committed registries rather than a
 * hand-coded rule. A key is legitimate iff it is an enumerated closed key
 * ({@code metadata-keys.json}), sits under a registered passthrough prefix
 * ({@code metadata-open-namespaces.json}), or is a documented template instance. Anything else is
 * {@link Classification#UNKNOWN} — a typo, an unregistered namespace, or a key someone forgot to
 * declare.
 *
 * <p>Reads the JSON snapshots (which {@code MetadataSchemaTest} gates against the live declarations),
 * so it needs no parser classes loaded and stays dependency-free.
 */
public final class MetadataKeyValidator {

    public enum Classification { CLOSED, OPEN, TEMPLATE, UNKNOWN }

    private static final String KEYS = "/org/apache/tika/metadata/metadata-keys.json";
    private static final String OPEN = "/org/apache/tika/metadata/metadata-open-namespaces.json";

    // Conservative BCP-47 subset for the XMP lang-alt template suffix (<closed-key>:<lang>).
    private static final Pattern LANG_TAG =
            Pattern.compile("x-default|[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*");

    private final Set<String> closedKeys;
    private final List<String> openPrefixes;   // longest-first: most specific prefix wins

    MetadataKeyValidator(Set<String> closedKeys, List<String> openPrefixes) {
        this.closedKeys = Set.copyOf(closedKeys);
        List<String> sorted = new ArrayList<>(openPrefixes);
        sorted.sort(Comparator.comparingInt(String::length).reversed());
        this.openPrefixes = List.copyOf(sorted);
    }

    /** Loads the validator from the committed registries on the classpath. */
    public static MetadataKeyValidator fromClasspath() {
        return new MetadataKeyValidator(
                new HashSet<>(readValues(KEYS, "key")), readValues(OPEN, "prefix"));
    }

    public Classification classify(String key) {
        if (key == null || key.isEmpty()) {
            return Classification.UNKNOWN;
        }
        if (closedKeys.contains(key)) {
            return Classification.CLOSED;
        }
        for (String prefix : openPrefixes) {
            if (key.length() > prefix.length() && key.startsWith(prefix)) {
                return Classification.OPEN;
            }
        }
        if (isLangAltInstance(key)) {
            return Classification.TEMPLATE;
        }
        return Classification.UNKNOWN;
    }

    public boolean isLegitimate(String key) {
        return classify(key) != Classification.UNKNOWN;
    }

    /** The unregistered keys among {@code names}, in encounter order. */
    public List<String> findUnknown(Iterable<String> names) {
        List<String> unknown = new ArrayList<>();
        for (String name : names) {
            if (classify(name) == Classification.UNKNOWN) {
                unknown.add(name);
            }
        }
        return unknown;
    }

    /** {@code <closed-key>:<lang>} — the XMP rdf:Alt language variants (dc:title:fr, dc:title:x-default). */
    private boolean isLangAltInstance(String key) {
        int i = key.lastIndexOf(':');
        if (i <= 0 || i == key.length() - 1) {
            return false;
        }
        return closedKeys.contains(key.substring(0, i)) && LANG_TAG.matcher(key.substring(i + 1)).matches();
    }

    private static List<String> readValues(String resource, String field) {
        String json = readResource(resource);
        List<String> out = new ArrayList<>();
        String marker = '"' + field + "\":\"";
        int i = 0;
        while ((i = json.indexOf(marker, i)) >= 0) {
            i += marker.length();
            StringBuilder sb = new StringBuilder();
            while (i < json.length()) {
                char c = json.charAt(i++);
                if (c == '\\' && i < json.length()) {
                    sb.append(json.charAt(i++));   // unescape \" and \\
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            out.add(sb.toString());
        }
        return out;
    }

    private static String readResource(String resource) {
        try (InputStream in = MetadataKeyValidator.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing registry resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
