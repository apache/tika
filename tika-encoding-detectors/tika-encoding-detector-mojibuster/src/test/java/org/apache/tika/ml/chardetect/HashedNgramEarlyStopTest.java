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
package org.apache.tika.ml.chardetect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The early stop on long dense probes picks the same top class as scoring everything. */
public class HashedNgramEarlyStopTest {

    private static HashedNgramModel model;

    @BeforeAll
    static void load() throws Exception {
        model = HashedNgramEncodingDetector.loadDefaultModel();
    }

    @Test
    public void cyrillic() throws Exception {
        assertSameTop("Новая программа по профилактике насилия и жестокого обращения с детьми. ",
                "Ёлка № 5. ", "windows-1251");
    }

    @Test
    public void japanese() throws Exception {
        assertSameTop("日本語のテキストです。これは文字コード判定のテストで、長い文書を想定しています。",
                "", "Shift_JIS");
    }

    @Test
    public void arabic() throws Exception {
        assertSameTop("برنامج جديد للوقاية من العنف وإساءة معاملة الأطفال في المدارس. ", "", "windows-1256");
    }

    /** Ά, one of the few letters ISO-8859-7 and windows-1253 place differently, comes late. */
    @Test
    public void greekSiblingByteAfterCheckpoint() throws Exception {
        assertSameTop("Η πρόληψη της βίας και της κακοποίησης των παιδιών είναι σημαντική. ",
                "Άρθρα για την πρόληψη. ", "windows-1253");
    }

    private static void assertSameTop(String sentence, String tail, String charset) {
        Charset cs = Charset.forName(charset);
        byte[] probe = (sentence.repeat(65536 / sentence.length()) + tail).getBytes(cs);
        float[] early = new float[model.getNumClasses()];
        float[] full = new float[model.getNumClasses()];
        assertTrue(model.logits(probe, 0, probe.length, early));
        assertTrue(model.logits(probe, 0, probe.length, full, Integer.MAX_VALUE));
        assertEquals(model.getLabels()[top(full)], model.getLabels()[top(early)], charset);
    }

    private static int top(float[] logits) {
        int t = 0;
        for (int c = 1; c < logits.length; c++) {
            if (logits[c] > logits[t]) {
                t = c;
            }
        }
        return t;
    }
}
