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

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

public class CjkDecodeValidatorTest {

    @Test
    public void realJapaneseFarBelowVetoThreshold() throws Exception {
        byte[] b = ("日本語のテスト文章をたくさん書いて高バイトを十分に確保します"
                + "これは本物の日本語です").getBytes("Shift_JIS");
        double rate = CjkDecodeValidator.strippedFailureRate(b, Charset.forName("Shift_JIS"));
        assertTrue(rate >= 0.0 && rate < 0.025, "real JP should be near-zero failure, got " + rate);
    }

    @Test
    public void realKoreanFarBelowVetoThreshold() throws Exception {
        byte[] b = ("안녕하세요 이것은 진짜 한국어 문장입니다 고바이트를 충분히 확보하기 위해 "
                + "여러 글자를 적습니다").getBytes("EUC-KR");
        double rate = CjkDecodeValidator.strippedFailureRate(b, Charset.forName("EUC-KR"));
        assertTrue(rate >= 0.0 && rate < 0.025, "real KR should be near-zero failure, got " + rate);
    }

    /** Mixed-encoding: legacy CJK body + an embedded UTF-8 run. Stripping the UTF-8
     *  run de-confounds, so the rate stays low (the WS2 breakthrough). */
    @Test
    public void mixedLegacyPlusUtf8NotVetoed() throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.writeBytes("日本語の本文をしっかり書いて高バイトを確保する本物のテキスト".getBytes("Shift_JIS"));
        bo.writeBytes("これはUTF-8の埋め込みウィジェット".getBytes("UTF-8")); // embedded UTF-8
        double rate = CjkDecodeValidator.strippedFailureRate(bo.toByteArray(),
                Charset.forName("Shift_JIS"));
        assertTrue(rate >= 0.0 && rate < 0.025, "mixed real CJK should stay low post-strip, got " + rate);
    }

    @Test
    public void garbageHighBytesVetoed() {
        byte[] b = new byte[60];
        Arrays.fill(b, (byte) 0xFF); // 0xFF is not a valid GB18030 lead → all malformed
        double rate = CjkDecodeValidator.strippedFailureRate(b, Charset.forName("GB18030"));
        assertTrue(rate >= 0.025, "garbage high bytes should be vetoed, got " + rate);
    }

    @Test
    public void insufficientHighBytesReturnsMinusOne() {
        byte[] b = "mostly ascii with a couple high bytes".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        assertEquals(-1.0, CjkDecodeValidator.strippedFailureRate(b, Charset.forName("GB18030")));
    }

    @Test
    public void appliesToLegacyCjkButNotIso2022OrLatin() {
        assertTrue(CjkDecodeValidator.appliesTo("GB18030"));
        assertTrue(CjkDecodeValidator.appliesTo("Shift_JIS"));
        assertTrue(CjkDecodeValidator.appliesTo("Big5-HKSCS"));
        assertTrue(CjkDecodeValidator.appliesTo("x-windows-949"));
        assertEquals(false, CjkDecodeValidator.appliesTo("ISO-2022-JP"));
        assertEquals(false, CjkDecodeValidator.appliesTo("windows-1252"));
    }
}
