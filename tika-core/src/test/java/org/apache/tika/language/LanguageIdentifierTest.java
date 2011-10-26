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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.HashMap;

import junit.framework.TestCase;

import org.apache.tika.io.IOUtils;

/**
 * JUnit based test of class {@link LanguageIdentifier}.
 *
 * @author Sami Siren
 * @author Jerome Charron - http://frutch.free.fr/
 */
public class LanguageIdentifierTest extends TestCase {

    private static final String[] languages = new String[] {
        // TODO - currently Estonian and Greek fail these tests.
        // Enable when language detection works better.
        "da", "de", /* "et", "el", */ "en", "es", "fi", "fr", "it",
        "lt", "nl", "pt", "sv"
    };

    public void setUp() {
        LanguageIdentifier.initProfiles();
    }
    
    public void testLanguageDetection() throws IOException {
        for (String language : languages) {
            ProfilingWriter writer = new ProfilingWriter();
            writeTo(language, writer);
            LanguageIdentifier identifier = null;
            identifier = new LanguageIdentifier(writer.getProfile());
            assertEquals(language, identifier.getLanguage());
            // Lithuanian is detected but isn't reasonably certain:
            if (!language.equals("lt")) {
                assertTrue(identifier.toString(), identifier.isReasonablyCertain());
            }
        }
    }

    public void testClearAddAndInitProfiles() throws IOException {
        // Prepare english and german language profiles
        ProfilingWriter enWriter = new ProfilingWriter();
        writeTo("en", enWriter);
        LanguageProfile enProfile = enWriter.getProfile();
        ProfilingWriter deWriter = new ProfilingWriter();
        writeTo("de", deWriter);
        LanguageProfile deProfile = deWriter.getProfile();

        // Out of the box profiles
        LanguageIdentifier identifier = null;
        identifier = new LanguageIdentifier(enProfile);
        assertEquals("en", identifier.getLanguage());
        assertTrue(identifier.isReasonablyCertain());

        // No profiles
        LanguageIdentifier.clearProfiles();
        identifier = new LanguageIdentifier(enProfile);
        assertFalse(identifier.isReasonablyCertain());

        // Only English profile
        LanguageIdentifier.addProfile("en", enProfile);
        identifier = new LanguageIdentifier(enProfile);
        assertEquals("en", identifier.getLanguage());
        assertTrue(identifier.isReasonablyCertain());

        // English and German profiles loaded explicitly from initProfiles method
        HashMap<String, LanguageProfile> profilesMap = new HashMap<String, LanguageProfile>();
        profilesMap.put("en", enProfile);
        profilesMap.put("de", deProfile);
        LanguageIdentifier.initProfiles(profilesMap);
        identifier = new LanguageIdentifier(enProfile);
        assertEquals("en", identifier.getLanguage());
        assertTrue(identifier.isReasonablyCertain());
        identifier = new LanguageIdentifier(deProfile);
        assertEquals("de", identifier.getLanguage());
        assertTrue(identifier.isReasonablyCertain());
  }

  public void testMixedLanguages() throws IOException {
        for (String language : languages) {
            for (String other : languages) {
                if (!language.equals(other)) {
                    if (language.equals("lt") || other.equals("lt")) {
                        continue;
                    }
                    ProfilingWriter writer = new ProfilingWriter();
                    writeTo(language, writer);
                    writeTo(other, writer);
                    LanguageIdentifier identifier = null;
                    identifier = new LanguageIdentifier(writer.getProfile());
                    assertFalse("mix of " + language + " and " + other + " incorrectly detected as " + identifier, identifier.isReasonablyCertain());
                }
            }
        }
    }

    // TIKA-453: Fix up language identifier used for Estonian
    public void testEstonia() throws Exception {
        final String estonian = "et";
        ProfilingWriter writer = new ProfilingWriter();
        writeTo(estonian, writer);
        LanguageIdentifier identifier =
            new LanguageIdentifier(writer.getProfile());
        assertEquals(estonian, identifier.getLanguage());
    }

    private void writeTo(String language, Writer writer) throws IOException {
        InputStream stream =
            LanguageIdentifierTest.class.getResourceAsStream(language + ".test");
        try {
            IOUtils.copy(new InputStreamReader(stream, "UTF-8"), writer);
        } finally {
            stream.close();
        }
    }

}
