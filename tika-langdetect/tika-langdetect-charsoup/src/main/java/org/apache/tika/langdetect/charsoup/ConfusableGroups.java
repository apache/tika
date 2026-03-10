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
package org.apache.tika.langdetect.charsoup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the shared confusable language groups from
 * {@code confusables.txt} on the classpath. This is the single source
 * of truth used by {@link CharSoupLanguageDetector} (production inference),
 * {@code CompareDetectors} (evaluation), and {@code TrainLanguageModel}
 * (filterPool). The Python contamination filter reads the same file directly.
 */
public final class ConfusableGroups {

    static final String RESOURCE =
            "/org/apache/tika/langdetect/charsoup/confusables.txt";

    private ConfusableGroups() {
    }

    /**
     * Load and return the confusable groups. Each entry is an array of
     * ISO 639-3 codes that are considered mutually confusable.
     *
     * @throws RuntimeException if the resource cannot be read
     */
    public static String[][] load() {
        List<String[]> groups = new ArrayList<>();
        try (InputStream is = ConfusableGroups.class.getResourceAsStream(RESOURCE)) {
            if (is == null) {
                throw new RuntimeException(
                        "Confusables resource not found on classpath: " + RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    groups.add(line.split(","));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to load confusables resource: " + RESOURCE, e);
        }
        return groups.toArray(new String[0][]);
    }
}
