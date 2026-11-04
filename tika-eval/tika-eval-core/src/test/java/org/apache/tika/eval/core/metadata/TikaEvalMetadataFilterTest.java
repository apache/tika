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
package org.apache.tika.eval.core.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.DefaultMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;

public class TikaEvalMetadataFilterTest {

    @Test
    public void testBasic() throws Exception {
        for (MetadataFilter filter : new MetadataFilter[]{new TikaEvalMetadataFilter(),
                //make sure that the TikaEvalMetadataFilter is loaded automatically
                new DefaultMetadataFilter()}) {
            Metadata metadata = new Metadata();
            String content = "the quick brown fox, Zothro 1234 1235, jumped over the lazy dog. " +
                    "English is widely spoken in the United Kingdom, United States and Australia.";
            metadata.set(TikaCoreProperties.TIKA_CONTENT, content);

            List<Metadata> metadataList = new ArrayList<>();
            metadataList.add(metadata);
            filter.filter(metadataList);
            metadata = metadataList.get(0);
            assertEquals("eng", metadata.get(TikaEvalMetadataFilter.LANGUAGE));
            assertEquals(24, (int) metadata.getInt(TikaEvalMetadataFilter.NUM_TOKENS));
            assertEquals(21, (int) metadata.getInt(TikaEvalMetadataFilter.NUM_UNIQUE_TOKENS));
            assertEquals(22, (int) metadata.getInt(TikaEvalMetadataFilter.NUM_ALPHA_TOKENS));
            assertEquals(19, (int) metadata.getInt(TikaEvalMetadataFilter.NUM_UNIQUE_ALPHA_TOKENS));
            assertEquals(19, (int) metadata.getInt(TikaEvalMetadataFilter.NUM_COMMON_TOKENS));


            assertEquals(0.0999,
                    Double.parseDouble(metadata.get(TikaEvalMetadataFilter.OUT_OF_VOCABULARY)),
                    0.1);
            assertEquals("eng", metadata.get(TikaEvalMetadataFilter.LANGUAGE));

            // Bigram detector returns softmax probabilities; the top-1 score for
            // a short English sentence is close to 1.0.
            double langConf = Double.parseDouble(
                    metadata.get(TikaEvalMetadataFilter.LANGUAGE_CONFIDENCE));
            assertEquals(1.0, langConf, 0.1);

            double languageness = Double.parseDouble(metadata.get(TikaEvalMetadataFilter.LANGUAGENESS));
            assertTrue(languageness > 0, "clean English should read as clean text: " + languageness);
            assertEquals(languageness,
                    Double.parseDouble(metadata.get(TikaEvalMetadataFilter.LANGUAGENESS_MIN)), 1e-6,
                    "shorter than one window: the minimum is the whole-text score");
            assertEquals(1.0, Double.parseDouble(metadata.get(TikaEvalMetadataFilter.LANGUAGENESS_COVERAGE)), 1e-6);
        }
    }

    @Test
    public void languagenessSeparatesMojibakeAndReportsUnknown() {
        String fr = "Le minist\u00e8re a \u00e9t\u00e9 cr\u00e9\u00e9 \u00e0 Paris o\u00f9 il r\u00e9side depuis "
                + "des ann\u00e9es, apr\u00e8s une r\u00e9forme tr\u00e8s discut\u00e9e.";
        String moji = new String(fr.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.Charset.forName("windows-1252"));
        float clean = TikaEvalMetadataFilter.languageness(fr)[0];
        float junk = TikaEvalMetadataFilter.languageness(moji)[0];
        assertTrue(clean > 0 && junk < clean, "clean " + clean + " mojibake " + junk);

        float[] digits = TikaEvalMetadataFilter.languageness("2026-11-04 3.14159 42");
        assertEquals(TikaEvalMetadataFilter.UNKNOWN_LANGUAGENESS, digits[0]);
        assertEquals(TikaEvalMetadataFilter.UNKNOWN_LANGUAGENESS, digits[1]);
        assertEquals(-1f, digits[2]);

        float[] yi = TikaEvalMetadataFilter.languageness("\ua188\ua320\ua071\ua0b7 \ua009\ua0be\ua46d\ua1ec");
        assertEquals(TikaEvalMetadataFilter.UNKNOWN_LANGUAGENESS, yi[0]);
        assertEquals(0f, yi[2]);

        // a clean page with one damaged island: the minimum finds it, the page score barely moves
        StringBuilder page = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            page.append(fr).append(' ');
        }
        page.append(moji).append(' ').append(moji).append(' ');
        for (int i = 0; i < 8; i++) {
            page.append(fr).append(' ');
        }
        float[] p = TikaEvalMetadataFilter.languageness(page.toString());
        assertTrue(p[1] < p[0] && p[1] < 0, "page " + p[0] + " min " + p[1]);
    }
}
