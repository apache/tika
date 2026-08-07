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
 * Regression test for a real-world failure: a genuinely UTF-8 HTML page whose
 * only single-byte "legacy" artifact (a stray {@code &copy;} written as raw
 * {@code 0xA9} rather than an entity) sits before the bulk of the document's
 * real multi-byte content.  {@link StructuralEncodingRules#checkUtf8} correctly
 * reports {@code NOT_UTF8} for the whole probe (one malformed lead byte), and
 * the tolerance mechanism in {@link MojibusterEncodingDetector} is supposed to
 * recognize this as "essentially UTF-8" when there's abundant genuine
 * multi-byte evidence.
 *
 * <p>Commit 360b3d354 ("merge conflict and flaky test", 2026-06-10) dropped the
 * {@code || utf8Tolerated} branch that used to promote this case to a
 * STRUCTURAL UTF-8 candidate, on the assumption that the NB statistical layer
 * would independently propose UTF-8 as a fallback.  That assumption doesn't
 * hold for every script/corpus (verified against a real Bengali-language news
 * page): NB's own candidate pool can come back completely empty, leaving
 * Mojibuster with nothing but the {@code windows-1252} "give up" default —
 * silent, complete mojibake on an otherwise-clean UTF-8 document.</p>
 *
 * <p>The companion {@link #shortProbeWithOneStrayByteIsNotPromoted()} test
 * guards the reason that branch was narrowed in the first place: zip entry
 * names are typically 9-30 bytes, and {@link
 * org.apache.tika.parser.pkg.ZipParser} runs them through this same detector
 * (see {@code ZipParser#isDetectCharsetsInEntryNames}). A single coincidental
 * error byte in a short, genuinely-legacy-encoded filename must NOT be enough
 * to promote it to STRUCTURAL UTF-8 — that would re-open the false-positive
 * this detector is relied on to avoid for filenames.</p>
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

    /**
     * Long document, abundant genuine multi-byte UTF-8 evidence, exactly one
     * tolerated error byte before it.  Must still be recognized as UTF-8.
     */
    @Test
    public void longDocumentWithOneStrayByteIsStillUtf8() throws IOException {
        byte[] probe = buildProbe(30);
        List<EncodingResult> results = newDetector().detect(probe);
        boolean hasStructuralUtf8 = results.stream().anyMatch(r ->
                "UTF-8".equals(r.getCharset().name())
                        && r.getResultType() == EncodingResult.ResultType.STRUCTURAL);
        assertTrue(hasStructuralUtf8,
                "A long, overwhelmingly UTF-8 document with a single tolerated "
                        + "error byte must still yield a STRUCTURAL UTF-8 candidate; "
                        + "results were: " + results);
    }

    /**
     * Short probe (the zip-entry-name shape), exactly one error byte, only a
     * handful of genuine multi-byte sequences.  Must NOT be promoted to
     * STRUCTURAL UTF-8 on the strength of tolerance alone — that's the
     * false-positive TIKA-4752-era filename detection depends on avoiding.
     */
    @Test
    public void shortProbeWithOneStrayByteIsNotPromoted() throws IOException {
        // ~20 bytes: one legacy high byte + a couple of genuine multi-byte
        // UTF-8 chars — the shape of a real (short) zip entry name, not a
        // full document.
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.write(0xA9); // stray legacy byte, invalid as a UTF-8 lead
        bo.writeBytes("café-Köln.txt".getBytes(StandardCharsets.UTF_8));
        byte[] probe = bo.toByteArray();

        List<EncodingResult> results = newDetector().detect(probe);
        boolean hasStructuralUtf8 = results.stream().anyMatch(r ->
                "UTF-8".equals(r.getCharset().name())
                        && r.getResultType() == EncodingResult.ResultType.STRUCTURAL);
        assertFalse(hasStructuralUtf8,
                "A short probe shaped like a zip entry name must not be promoted "
                        + "to STRUCTURAL UTF-8 on a single tolerated error alone; "
                        + "results were: " + results);
    }

    /**
     * Real embedded-file-name regression from {@code attachment_name_diffs.xlsx}
     * (commoncrawl3/5D/5DXWH7R4A5Q6VAWBAMBSUZM5PNEVAE63): a GBK zip entry name
     * ({@code 说明.txt}) must stay GB18030, not get pulled toward STRUCTURAL
     * UTF-8 by tolerance — the same false-positive risk as the Latin case,
     * CJK-flavored.
     */
    @Test
    public void chineseGbkFilenameIsNotPromotedToUtf8() {
        byte[] probe = "说明.txt".getBytes(Charset.forName("GBK"));
        List<EncodingResult> results = newDetector().detect(probe);
        boolean hasStructuralUtf8 = results.stream().anyMatch(r ->
                "UTF-8".equals(r.getCharset().name())
                        && r.getResultType() == EncodingResult.ResultType.STRUCTURAL);
        assertFalse(hasStructuralUtf8,
                "A short GBK filename must not be promoted to STRUCTURAL UTF-8 "
                        + "on a single tolerated error alone; results were: " + results);
        assertTrue(results.stream().anyMatch(r -> r.getCharset().name().startsWith("GB")),
                "Expected a GB18030/GBK candidate; results were: " + results);
    }

    /**
     * Real embedded-file-name regression from {@code attachment_name_diffs.xlsx}
     * (bug_trackers/MOZILLA/240463-316268/MOZILLA-296795-4.zip): a windows-1252
     * zip entry name ({@code Sauté.txt}) must stay legacy SBCS, not get promoted
     * to STRUCTURAL UTF-8 by tolerance.
     */
    @Test
    public void sauteFilenameIsNotPromotedToUtf8() {
        byte[] probe = "Sauté.txt".getBytes(Charset.forName("windows-1252"));
        List<EncodingResult> results = newDetector().detect(probe);
        boolean hasStructuralUtf8 = results.stream().anyMatch(r ->
                "UTF-8".equals(r.getCharset().name())
                        && r.getResultType() == EncodingResult.ResultType.STRUCTURAL);
        assertFalse(hasStructuralUtf8,
                "A short windows-1252 filename must not be promoted to STRUCTURAL "
                        + "UTF-8 on a single tolerated error alone; results were: " + results);
    }

    /** HTML wrapper + {@code repeatCount} copies of a real Bengali sentence,
     *  with a single raw {@code 0xA9} (not a UTF-8 encoded {@code ©}) planted
     *  in a meta tag before the real content — matches the real-world
     *  failure exactly (declared windows-1252, genuinely UTF-8 body). */
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
        bo.write(0xA9); // stray legacy byte, invalid as a UTF-8 lead
        bo.writeBytes(" 2013\"></head><body><title>".getBytes(StandardCharsets.US_ASCII));
        bo.writeBytes(body.toString().getBytes(StandardCharsets.UTF_8));
        bo.writeBytes("</title></body></html>".getBytes(StandardCharsets.US_ASCII));
        return bo.toByteArray();
    }
}
