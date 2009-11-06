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
        "da", "de", /* "el", */ "en", "es", "fi", "fr", "it", "nl", "pt", "sv"
    };

    public void testLanguageDetection() throws IOException {
        for (String language : languages) {
            ProfilingWriter writer = new ProfilingWriter();
            writeTo(language, writer);
            LanguageIdentifier identifier =
                new LanguageIdentifier(writer.getProfile());
            assertTrue(identifier.toString(), identifier.isReasonablyCertain());
            assertEquals(language, identifier.getLanguage());
        }
    }

    public void testMixedLanguages() throws IOException {
        for (String language : languages) {
            for (String other : languages) {
                if (!language.equals(other)) {
                    ProfilingWriter writer = new ProfilingWriter();
                    writeTo(language, writer);
                    writeTo(other, writer);
                    LanguageIdentifier identifier =
                        new LanguageIdentifier(writer.getProfile());
                    assertFalse(identifier.isReasonablyCertain());
                }
            }
        }
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
