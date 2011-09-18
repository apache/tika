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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;

import junit.framework.Assert;
import junit.framework.TestCase;
import org.apache.tika.exception.TikaException;

public class LanguageProfilerBuilderTest extends TestCase {
    /* Test members */
    private LanguageProfilerBuilder ngramProfile = null;
    private LanguageProfile langProfile = null;
    private final String profileName = "../tika-core/src/test/resources/org/apache/tika/language/langbuilder/"
            + LanguageProfilerBuilderTest.class.getName();
    private final String corpusName = "langbuilder/welsh_corpus.txt";
    private final String encoding = "UTF-8";
    private final String FILE_EXTENSION = "ngp";
    private final String LANGUAGE = "welsh";
    private final int maxlen = 1000;

    public void testCreateProfile() throws TikaException, IOException, URISyntaxException {
        InputStream is =
                LanguageProfilerBuilderTest.class.getResourceAsStream(corpusName);
        try {
            ngramProfile = LanguageProfilerBuilder.create(profileName, is , encoding);
        } finally {
            is.close();
        }

        File f = new File(profileName + "." + FILE_EXTENSION);
        FileOutputStream fos = new FileOutputStream(f);
        ngramProfile.save(fos);
        fos.close();
        Assert.assertEquals(maxlen, ngramProfile.getSorted().size());
    }

    public void testNGramProfile() throws IOException, TikaException, URISyntaxException {
        createLanguageProfile();
        LanguageIdentifier.addProfile(LANGUAGE, langProfile);
        LanguageIdentifier identifier = new LanguageIdentifier(langProfile);
        Assert.assertEquals(LANGUAGE, identifier.getLanguage());
        Assert.assertTrue(identifier.isReasonablyCertain());
    }

    private void createLanguageProfile() throws IOException, TikaException, URISyntaxException {
        // Sort of dependency injection
        if (ngramProfile == null)
            testCreateProfile();

        langProfile = new LanguageProfile();

        InputStream stream = new FileInputStream(new File(profileName + "."
                + FILE_EXTENSION));
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    stream, encoding));
            String line = reader.readLine();
            while (line != null) {
                if (line.length() > 0 && !line.startsWith("#")) {// skips the
                                                                 // ngp
                                                                 // header/comment
                    int space = line.indexOf(' ');
                    langProfile.add(line.substring(0, space),
                            Long.parseLong(line.substring(space + 1)));
                }
                line = reader.readLine();
            }
        } finally {
            stream.close();
        }
    }

    public void tearDown() throws Exception {
        File profile = new File(profileName + "." + FILE_EXTENSION);
        if (profile.exists())
            profile.delete();
    }
}
