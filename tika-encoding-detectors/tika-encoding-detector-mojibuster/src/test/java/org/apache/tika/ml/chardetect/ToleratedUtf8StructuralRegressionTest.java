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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.EncodingResult;

/**
 * TIKA-4810: commit 360b3d354 (2026-06-10) dropped the {@code || utf8Tolerated}
 * branch that promoted a tolerated (near-clean) probe to STRUCTURAL UTF-8,
 * assuming NB's statistical layer always covers the fallback. It doesn't (a
 * real Bengali news page's NB pool came back empty) — but restoring the branch
 * unconditionally would re-open a false positive on short zip entry names
 * (9-30 bytes, routed through this detector by {@code ZipParser}), which is
 * why it was narrowed in the first place.
 */
public class ToleratedUtf8StructuralRegressionTest {

    private static final String BENGALI_SENTENCE =
            "সেমিতে ক্রোয়েশিয়া টাইব্রেকারে রাশিয়াকে হারিয়ে ফাইনালে উঠেছে। ";

    private static MojibusterEncodingDetector newDetector() {
        try {
            return new MojibusterEncodingDetector();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void longDocumentWithOneStrayByteIsStillUtf8() throws IOException {
        byte[] probe = buildProbe(30);
        List<EncodingResult> results = newDetector().detect(probe);
        assertTrue(hasStructuralUtf8(results),
                "A long, overwhelmingly UTF-8 document with a single tolerated "
                        + "error byte must still yield a STRUCTURAL UTF-8 candidate; "
                        + "results were: " + results);
    }

    /** Zip-entry-name-shaped probe: must not be promoted on tolerance alone. */
    @Test
    public void shortProbeWithOneStrayByteIsNotPromoted() throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.write(0xA9); // raw © byte: invalid as a UTF-8 lead
        bo.writeBytes("café-Köln.txt".getBytes(StandardCharsets.UTF_8));
        byte[] probe = bo.toByteArray();

        List<EncodingResult> results = newDetector().detect(probe);
        assertFalse(hasStructuralUtf8(results),
                "A short probe shaped like a zip entry name must not be promoted "
                        + "to STRUCTURAL UTF-8 on a single tolerated error alone; "
                        + "results were: " + results);
    }

    /** Real GBK filename from attachment_name_diffs.xlsx; must stay GB18030. */
    @Test
    public void chineseGbkFilenameIsNotPromotedToUtf8() {
        byte[] probe = "说明.txt".getBytes(Charset.forName("GBK"));
        List<EncodingResult> results = newDetector().detect(probe);
        assertFalse(hasStructuralUtf8(results),
                "A short GBK filename must not be promoted to STRUCTURAL UTF-8 "
                        + "on a single tolerated error alone; results were: " + results);
        assertTrue(results.stream().anyMatch(r -> r.getCharset().name().startsWith("GB")),
                "Expected a GB18030/GBK candidate; results were: " + results);
    }

    /** Real windows-1252 filename from attachment_name_diffs.xlsx. */
    @Test
    public void sauteFilenameIsNotPromotedToUtf8() {
        byte[] probe = "Sauté.txt".getBytes(Charset.forName("windows-1252"));
        List<EncodingResult> results = newDetector().detect(probe);
        assertFalse(hasStructuralUtf8(results),
                "A short windows-1252 filename must not be promoted to STRUCTURAL "
                        + "UTF-8 on a single tolerated error alone; results were: " + results);
    }

    private static boolean hasStructuralUtf8(List<EncodingResult> results) {
        return results.stream().anyMatch(r -> "UTF-8".equals(r.getCharset().name())
                && r.getResultType() == EncodingResult.ResultType.STRUCTURAL);
    }

    /** Declared-windows-1252 HTML page, genuinely UTF-8, one stray raw © byte. */
    private static byte[] buildProbe(int repeatCount) throws IOException {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < repeatCount; i++) {
            body.append(BENGALI_SENTENCE);
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.writeBytes(("<html><head><meta http-equiv=\"Content-Type\" "
                + "content=\"text/html; charset=windows-1252\">")
                .getBytes(StandardCharsets.US_ASCII));
        bo.writeBytes("<meta name=\"copyright\" content=\"".getBytes(StandardCharsets.US_ASCII));
        bo.write(0xA9); // raw © byte: invalid as a UTF-8 lead
        bo.writeBytes(" 2013\"></head><body><title>".getBytes(StandardCharsets.US_ASCII));
        bo.writeBytes(body.toString().getBytes(StandardCharsets.UTF_8));
        bo.writeBytes("</title></body></html>".getBytes(StandardCharsets.US_ASCII));
        return bo.toByteArray();
    }
}
