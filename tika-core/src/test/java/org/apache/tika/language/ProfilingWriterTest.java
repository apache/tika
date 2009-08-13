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

import org.apache.tika.io.IOUtils;

import junit.framework.TestCase;

public class ProfilingWriterTest extends TestCase {

    public void testProfilingWriter() throws IOException {
        assertProfile("da");
        assertProfile("de");
        assertProfile("el");
        assertProfile("en");
        assertProfile("es");
        assertProfile("fi");
        assertProfile("fr");
        assertProfile("it");
        assertProfile("nl");
        assertProfile("pt");
        assertProfile("sv");
    }

    private void assertProfile(String lang) throws IOException {
        InputStream stream =
            ProfilingWriterTest.class.getResourceAsStream(lang + ".test");
        try {
            ProfilingWriter writer = new ProfilingWriter();
            IOUtils.copy(new InputStreamReader(stream, "UTF-8"), writer);
            NGramProfile profile = writer.getProfile();
            assertEquals(lang, new LanguageIdentifier(profile).identify());
        } finally {
            stream.close();
        }
    }

}
