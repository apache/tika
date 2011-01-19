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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Identifier of the language that best matches a given content profile.
 * The content profile is compared to generic language profiles based on
 * material from various sources.
 *
 * @since Apache Tika 0.5
 * @see <a href="http://www.iccs.inf.ed.ac.uk/~pkoehn/publications/europarl/">
 *      Europarl: A Parallel Corpus for Statistical Machine Translation</a>
 * @see <a href="http://www.loc.gov/standards/iso639-2/php/code_list.php">
 *      ISO 639 Language Codes</a>
 */
public class LanguageIdentifier {
    
    /**
     * The available language profiles.
     */
    private static final Map<String, LanguageProfile> PROFILES =
        new HashMap<String, LanguageProfile>();
    private static final String PROFILE_SUFFIX = ".ngp";
    private static final String PROFILE_ENCODING = "UTF-8";

    private static Properties props = new Properties();
    private static String errors = "";
    
    private static final String PROPERTIES_OVERRIDE_FILE = "tika.language.override.properties";
    private static final String PROPERTIES_FILE = "tika.language.properties";
    private static final String LANGUAGES_KEY = "languages";
    private static final double CERTAINTY_LIMIT = 0.022;

    private final String language;

    private final double distance;

    /*
     * Always attempt initializing language profiles when class is loaded first time
     */
    static {
        initProfiles();
    }
    
    /*
     * Add one language profile based on config in property file
     */
    private static void addProfile(String language) throws Exception {
        try {
            LanguageProfile profile = new LanguageProfile();

            InputStream stream =
                LanguageIdentifier.class.getResourceAsStream(language + PROFILE_SUFFIX);
            try {
                BufferedReader reader =
                    new BufferedReader(new InputStreamReader(stream, PROFILE_ENCODING));
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

            addProfile(language, profile);
        } catch (Throwable t) {
            throw new Exception("Failed trying to load language profile for language \""+language+"\". Error: "+t.getMessage());
        }
    }
    
    /**
     * Adds a single language profile
     * @param language an ISO 639 code representing language
     * @param profile the language profile
     */
    public static void addProfile(String language, LanguageProfile profile) {
        PROFILES.put(language, profile);
    }
    
    /**
     * Constructs a language identifier based on a LanguageProfile
     * @param profile the language profile
     */
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

    /**
     * Constructs a language identifier based on a String of text content
     * @param content the text
     */
    public LanguageIdentifier(String content) {
        this(new LanguageProfile(content));
    }

    /**
     * Gets the identified language
     * @return an ISO 639 code representing the detected language
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Tries to judge whether the identification is certain enough
     * to be trusted.
     * WARNING: Will never return true for small amount of input texts. 
     * @return <code>true</code> if the distance is smaller then {@value #CERTAINTY_LIMIT}, <code>false</code> otherwise
     */
    public boolean isReasonablyCertain() {
        return distance < CERTAINTY_LIMIT;
    }

    /**
     * Builds the language profiles.
     * The list of languages are fetched from a property file named "tika.language.properties"
     * If a file called "tika.language.override.properties" is found on classpath, this is used instead
     * The property file contains a key "languages" with values being comma-separated language codes
     */
    public static void initProfiles() {
        clearProfiles();
        
        errors = "";
        InputStream stream;
        stream = LanguageIdentifier.class.getResourceAsStream(PROPERTIES_OVERRIDE_FILE);
        if(stream == null) {
            stream = LanguageIdentifier.class.getResourceAsStream(PROPERTIES_FILE);
        }

        if(stream != null){
            try {
                props = new Properties();
                props.load(stream);
            } catch (IOException e) {
                errors += "IOException while trying to load property file. Message: " + e.getMessage() + "\n";
            }
        }
        
        String[] languages = props.getProperty(LANGUAGES_KEY).split(",");
        for(String language : languages) {
            language = language.trim();
            String name = props.getProperty("name."+language, "Unknown");
            try {
                addProfile(language);
            } catch (Exception e) {
                errors += "Language " + language + " (" + name + ") not initialized. Message: " + e.getMessage() + "\n";
            }
        }
    }

    /**
     * Initializes the language profiles from a user supplied initialized Map.
     * This overrides the default set of profiles initialized at startup,
     * and provides an alternative to configuring profiles through property file
     *
     * @param profilesMap map of language profiles
     */
    public static void initProfiles(Map<String, LanguageProfile> profilesMap) {
        clearProfiles();
        for(Map.Entry<String, LanguageProfile> entry : profilesMap.entrySet()) {
            addProfile(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Clears the current map of language profiles
     */
    public static void clearProfiles() {
        PROFILES.clear();
    }
    
    /**
     * Tests whether there were errors initializing language config
     * @return true if there are errors. Use getErrors() to retrieve.
     */
    public static boolean hasErrors() {
        return errors != "";
    }
    
    /**
     * Returns a string of error messages related to initializing langauge profiles
     * @return the String containing the error messages
     */
    public static String getErrors() {
        return errors;
    }
    
    /**
     * Returns what languages are supported for language identification
     * @return A set of Strings being the ISO 639 language codes
     */
    public static Set<String> getSupportedLanguages() {
        return PROFILES.keySet();
    }

    @Override
    public String toString() {
        return language + " (" + distance + ")";
    }

}
