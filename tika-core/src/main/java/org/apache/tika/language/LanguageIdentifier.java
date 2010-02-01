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
package org.apache.tika.language;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Identifier of the language that best matches a given content profile.
 * The content profile is compared to generic language profiles based on
 * material from various sources.
 *
 * @since Apache Tika 0.5
 * @see <a href="http://www.iccs.inf.ed.ac.uk/~pkoehn/publications/europarl/">
 *      Europarl: A Parallel Corpus for Statistical Machine Translation</a>
 * @see <a href="http://www.w3.org/WAI/ER/IG/ert/iso639.htm">
 *      ISO 639 Language Codes</a>
 */
public class LanguageIdentifier {

    /**
     * The available language profiles.
     */
    private static final Map<String, LanguageProfile> PROFILES =
        new HashMap<String, LanguageProfile>();

    private static void addProfile(String language) {
        try {
            LanguageProfile profile = new LanguageProfile();

            InputStream stream =
                LanguageIdentifier.class.getResourceAsStream(language + ".ngp");
            try {
                BufferedReader reader =
                    new BufferedReader(new InputStreamReader(stream, "UTF-8"));
                String line = reader.readLine();
                while (line != null) {
                    if (line.length() > 0 && !line.startsWith("#")) {
                        int space = line.indexOf(' ');
                        profile.add(
                                line.substring(0, space),
                                Long.parseLong(line.substring(space + 1)));
                    }
                    line = reader.readLine();
                }
            } finally {
                stream.close();
            }

            PROFILES.put(language, profile);
        } catch (Throwable t) {
            // Failed to load this language profile. Log the problem?
        }
    }

    static {
        addProfile("da"); // Danish
        addProfile("de"); // German
        addProfile("ee");
        addProfile("el"); // Greek
        addProfile("en"); // English
        addProfile("es"); // Spanish
        addProfile("fi"); // Finnish
        addProfile("fr"); // French
        addProfile("hu"); // Hungarian
        addProfile("is"); // Icelandic
        addProfile("it"); // Italian
        addProfile("nl"); // Dutch
        addProfile("no"); // Norwegian
        addProfile("pl"); // Polish
        addProfile("pt"); // Portuguese
        addProfile("ru"); // Russian
        addProfile("sv"); // Swedish
        addProfile("th"); // Thai
    }

    private final String language;

    private final double distance;

    public LanguageIdentifier(LanguageProfile profile) {
        String minLanguage = "unknown";
        double minDistance = 1.0;
        for (Map.Entry<String, LanguageProfile> entry : PROFILES.entrySet()) {
            double distance = profile.distance(entry.getValue());
            if (distance < minDistance) {
                minDistance = distance;
                minLanguage = entry.getKey();
            }
        }

        this.language = minLanguage;
        this.distance = minDistance;
    }

    public LanguageIdentifier(String content) {
        this(new LanguageProfile(content));
    }

    public String getLanguage() {
        return language;
    }

    public boolean isReasonablyCertain() {
        return distance < 0.022;
    }

    @Override
    public String toString() {
        return language + " (" + distance + ")";
    }

}
