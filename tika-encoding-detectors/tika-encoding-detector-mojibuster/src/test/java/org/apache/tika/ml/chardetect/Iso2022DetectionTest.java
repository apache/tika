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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.EncodingResult;

/**
 * WS3: ISO-2022-JP/KR/CN are 7-bit escape-based encodings invisible to the NB
 * bigram model; the detector recognizes them structurally inside the pure-ASCII
 * branch.  Binary FPs (high-byte) never reach that branch, and a stray {@code
 * ESC $} in real ASCII is rejected by the decode-verify.
 */
public class Iso2022DetectionTest {

    private final MojibusterEncodingDetector det = newDetector();

    private static MojibusterEncodingDetector newDetector() {
        try {
            return new MojibusterEncodingDetector();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void detectsRealIso2022Jp() throws Exception {
        byte[] b = ("日本語のテスト文章です。これは ISO-2022-JP でエンコードされた"
                + "純粋に7ビットの文書です。").getBytes("ISO-2022-JP");
        EncodingResult top = det.detect(b).get(0);
        assertEquals("ISO-2022-JP", top.getCharset().name());
        assertEquals(EncodingResult.ResultType.STRUCTURAL, top.getResultType());
    }

    @Test
    public void detectsRealIso2022Kr() throws Exception {
        byte[] b = ("안녕하세요 이것은 ISO-2022-KR 로 인코딩된 한국어 문서입니다 "
                + "순수한 7비트 텍스트입니다").getBytes("ISO-2022-KR");
        assertEquals("ISO-2022-KR", det.detect(b).get(0).getCharset().name());
    }

    @Test
    public void plainAsciiIsNotIso2022() {
        byte[] b = "Hello world, this is ordinary 7-bit ASCII prose with no escapes."
                .getBytes(StandardCharsets.US_ASCII);
        Charset top = det.detect(b).get(0).getCharset();
        assertNotEquals("ISO-2022-JP", top.name());
        assertNotEquals("ISO-2022-KR", top.name());
    }

    /** A real {@code ESC(0x1B) $ B} with an empty JIS section embedded in ASCII
     *  yields zero CJK, so the decode-verify must reject it (not crown ISO-2022-JP). */
    @Test
    public void strayEscapeInAsciiIsNotIso2022() {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.writeBytes("terminal log dump: ".getBytes(StandardCharsets.US_ASCII));
        bo.writeBytes(new byte[] {0x1b, '$', 'B', 0x1b, '(', 'B'}); // enter then exit JIS
        bo.writeBytes("back to ascii output".getBytes(StandardCharsets.US_ASCII));
        assertNotEquals("ISO-2022-JP", det.detect(bo.toByteArray()).get(0).getCharset().name());
    }
}
