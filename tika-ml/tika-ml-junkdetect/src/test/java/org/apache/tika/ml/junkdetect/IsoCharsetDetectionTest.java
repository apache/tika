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
package org.apache.tika.ml.junkdetect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.Charset;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * TIKA-5019: undeclared ISO-8859-5/6/7 text must not come back as a windows sibling or CJK. The
 * Arabic line is the reporter's, tested as given and pasted 20 times (was x-EUC-TW, windows-1252).
 */
public class IsoCharsetDetectionTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ISO-8859-6|جديد للوقاية من العنف وإساءة معاملة الأطفال.",
            "ISO-8859-5|Новая программа по профилактике насилия и жестокого обращения с детьми.",
            "ISO-8859-7|Άρθρα για την πρόληψη της βίας και της κακοποίησης των παιδιών.",
            "windows-1253|Άρθρα για την πρόληψη της βίας και της κακοποίησης των παιδιών.",
    })
    public void detectsUndeclared(String charset, String text) throws Exception {
        Charset cs = Charset.forName(charset);
        for (int repeat : new int[]{1, 20}) {
            byte[] bytes = text.repeat(repeat).getBytes(cs);
            List<EncodingResult> results;
            try (TikaInputStream tis = TikaInputStream.get(bytes)) {
                results = new DefaultEncodingDetector().detect(tis, new Metadata(), new ParseContext());
            }
            assertFalse(results.isEmpty(), charset + " x" + repeat);
            assertEquals(cs.name(), results.get(0).getDecodeAs().name(), charset + " x" + repeat);
        }
    }
}
