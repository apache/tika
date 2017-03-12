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
package org.apache.tika.langdetect;

import org.apache.tika.io.IOUtils;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.language.detect.LanguageWriter;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Test harness for the {@link org.apache.tika.langdetect.Lingo24LangDetector}.
 */
public class Lingo24LangDetectorTest {

    @Test
    public void testLanguageLoad() {
        LanguageDetector detector = new Lingo24LangDetector();
        assumeTrue(((Lingo24LangDetector) detector).isAvailable());

        // Only check a couple
        assertTrue(detector.hasModel("ar"));
        assertTrue(detector.hasModel("de"));
    }


    @Test
    public void testLanguageDetection() throws Exception {
        LanguageDetector detector = new Lingo24LangDetector();
        assumeTrue(((Lingo24LangDetector) detector).isAvailable());
        LanguageWriter writer = new LanguageWriter(detector);

        // Reusing the test data from OptimaizeLangDetectorTest
        List<String> lines = IOUtils.readLines(Lingo24LangDetectorTest.class.getResourceAsStream("text-test.tsv"));
        for (String line : lines) {
            String[] data = line.split("\t");
            if (data.length != 2) continue;

            writer.reset();
            writer.append(data[1]);

            // Only check supported languages
            if (detector.hasModel(data[0])) {
                LanguageResult result = detector.detect();
                assertNotNull(result);
                assertEquals(data[0], result.getLanguage());
            }
        }
        writer.close();
    }
}
