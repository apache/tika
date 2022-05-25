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

package org.apache.tika.langdetect.tika;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaException;

public class LanguageProfilerBuilderTest {
    private final String corpusName = "langbuilder/welsh_corpus.txt";
    private final String FILE_EXTENSION = "ngp";
    private final String LANGUAGE = "welsh";
    private final int maxlen = 1000;
    String profileName = "test-profile";
    private Path tmpProfileModel;

    @BeforeEach
    public void setUp() throws Exception {
        tmpProfileModel = Files.createTempFile("tika-lang", ".ngp");
        try (InputStream is = LanguageProfilerBuilderTest.class.getResourceAsStream(corpusName)) {
            LanguageProfilerBuilder ngramProfileBuilder =
                    LanguageProfilerBuilder.create(profileName, is, UTF_8.name());
            try (OutputStream os = Files.newOutputStream(tmpProfileModel)) {
                ngramProfileBuilder.save(os);;
                assertEquals(maxlen, ngramProfileBuilder.getSorted().size());
            }
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (Files.isRegularFile(tmpProfileModel)) {
            Files.delete(tmpProfileModel);
        }
    }

    @Test
    public void testNGramProfile() throws IOException, TikaException, URISyntaxException {
        LanguageProfile langProfile = loadProfile();
        LanguageIdentifier.addProfile(LANGUAGE, langProfile);
        LanguageIdentifier identifier = new LanguageIdentifier(langProfile);
        assertEquals(LANGUAGE, identifier.getLanguage());
        assertTrue(identifier.isReasonablyCertain());
    }

    private LanguageProfile loadProfile() throws IOException, TikaException, URISyntaxException {


        LanguageProfile langProfile = new LanguageProfile();

        try (InputStream stream = Files.newInputStream(tmpProfileModel)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8));
            String line = reader.readLine();
            while (line != null) {
                if (line.length() > 0 && !line.startsWith("#")) { // skips the
                    // ngp
                    // header/comment
                    int space = line.indexOf(' ');
                    langProfile.add(line.substring(0, space),
                            Long.parseLong(line.substring(space + 1)));
                }
                line = reader.readLine();
            }
        }
        return langProfile;
    }
}
