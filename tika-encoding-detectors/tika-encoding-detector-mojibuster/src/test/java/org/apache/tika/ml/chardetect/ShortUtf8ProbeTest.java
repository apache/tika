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
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.EncodingResult;

/**
 * Short clean UTF-8 is UTF-8 even when the model calls it binary (it does for the Greek probe):
 * the grammar check's STRUCTURAL candidate does not depend on the model.
 */
public class ShortUtf8ProbeTest {

    @Test
    public void greek32Bytes() throws Exception {
        assertUtf8("Η Ελλάδα είναι χώρα της νοτιοανατολικής Ευρώπης.");
    }

    @Test
    public void korean32Bytes() throws Exception {
        assertUtf8("대한민국은 동아시아의 한반도 남부에 위치한 민주공화국이다.");
    }

    private static void assertUtf8(String text) throws Exception {
        byte[] probe = Arrays.copyOf(text.getBytes(StandardCharsets.UTF_8), 32);
        List<EncodingResult> results = new MojibusterEncodingDetector().detect(probe);
        assertFalse(results.isEmpty());
        assertEquals(StandardCharsets.UTF_8, results.get(0).getCharset(), results.toString());
    }
}
