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

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * BOM'd UTF-16 Latin text with emoji: U+1F600's low surrogate (DE00) puts a NUL in the
 * "wrong" column, which leaves the UTF-16 specialist undecided unless it sees the BOM.
 */
public class Utf16BomDetectionTest {

    private static final int[] EMOJI = {0x1F604, 0x1F603, 0x1F600, 0x1F60A, 0x263A, 0x2639};

    private static String text() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            sb.append(',').appendCodePoint(EMOJI[i % EMOJI.length])
                    .append(", Smiling Face Number ").append(i).append(",0x09,*,*,0,0,\r\n");
        }
        return sb.toString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-16LE", "UTF-16BE"})
    public void bomUtf16WithEmoji(String charset) throws Exception {
        Charset cs = Charset.forName(charset);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write("\uFEFF".getBytes(cs));
        bos.write(text().getBytes(cs));
        List<EncodingResult> results;
        try (TikaInputStream tis = TikaInputStream.get(bos.toByteArray())) {
            results = new DefaultEncodingDetector().detect(tis, new Metadata(), new ParseContext());
        }
        assertFalse(results.isEmpty());
        assertEquals(cs.name(), results.get(0).getDecodeAs().name());
    }

    // TODO BOM-less UTF-16 with U+xx00 code units: UTF-16 specialist rework (see utf16-hashed)
    @Disabled
    @ParameterizedTest
    @ValueSource(strings = {"UTF-16LE", "UTF-16BE"})
    public void noBomUtf16WithEmoji(String charset) throws Exception {
        Charset cs = Charset.forName(charset);
        List<EncodingResult> results;
        try (TikaInputStream tis = TikaInputStream.get(text().getBytes(cs))) {
            results = new DefaultEncodingDetector().detect(tis, new Metadata(), new ParseContext());
        }
        assertFalse(results.isEmpty());
        assertEquals(cs.name(), results.get(0).getDecodeAs().name());
    }
}
