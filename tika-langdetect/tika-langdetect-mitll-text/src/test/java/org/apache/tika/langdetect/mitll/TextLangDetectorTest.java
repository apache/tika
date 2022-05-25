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
package org.apache.tika.langdetect.mitll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.langdetect.LanguageDetectorTest;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.language.detect.LanguageWriter;

/**
 * Created by trevorlewis on 3/7/16.
 */
public class TextLangDetectorTest {

    @Test
    public void test() throws Exception {
        List<String> lines =
                IOUtils.readLines(LanguageDetectorTest.class.getResourceAsStream("text-test.tsv"),
                        StandardCharsets.UTF_8);

        assertEquals(18, lines.size());

        assumeTrue(TextLangDetector.canRun());

        LanguageDetector detector = new TextLangDetector();
        LanguageWriter writer = new LanguageWriter(detector);

        for (String line : lines) {
            String[] data = line.split("\t");
            if (data.length != 2) {
                continue;
            }

            writer.reset();
            writer.append(data[1]);

            LanguageResult result = detector.detect();
            assertNotNull(result);

            assertEquals(data[0], result.getLanguage());
        }

        writer.close();
    }
}
