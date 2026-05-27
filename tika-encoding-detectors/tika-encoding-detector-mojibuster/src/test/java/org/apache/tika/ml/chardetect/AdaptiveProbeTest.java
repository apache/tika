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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;

public class AdaptiveProbeTest {

    private static byte[] probe(byte[] raw, int contentTarget, int rawCap) throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(raw)) {
            return AdaptiveProbe.read(tis, contentTarget, rawCap);
        }
    }

    /** Plain text below the content target: read everything, stop at EOF. */
    @Test
    public void shortPlainTextReadsToEof() throws Exception {
        byte[] raw = "hello world, this is plain text".getBytes(StandardCharsets.UTF_8);
        byte[] p = probe(raw, 16384, 524288);
        assertEquals(raw.length, p.length);
    }

    /** No tags: content == raw, so the read stops at the content target. */
    @Test
    public void plainTextStopsAtContentTarget() throws Exception {
        byte[] raw = new byte[200_000];
        java.util.Arrays.fill(raw, (byte) 'a');
        byte[] p = probe(raw, 16384, 524288);
        // Stops within one chunk past the target (chunked by contentTarget).
        assertTrue(p.length >= 16384 && p.length <= 32768,
                "expected ~content target, got " + p.length);
    }

    /** Markup-heavy lead: must read past 16 KB raw to accumulate body text. */
    @Test
    public void markupHeavyReadsPastFixedWindow() throws Exception {
        StringBuilder sb = new StringBuilder();
        // ~40 KB of tags yielding almost no text, then real body content.
        for (int i = 0; i < 4000; i++) {
            sb.append("<div class=\"x\"></div>");
        }
        int markupBytes = sb.length();
        for (int i = 0; i < 20000; i++) {
            sb.append("content ");
        }
        byte[] raw = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] p = probe(raw, 16384, 524288);
        assertTrue(p.length > markupBytes,
                "should read past the markup block (" + markupBytes + "), got " + p.length);
    }

    /** All markup, no body: bounded by the raw cap. */
    @Test
    public void allMarkupBoundedByRawCap() throws Exception {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 300_000) {
            sb.append("<a href=\"#\"></a>");
        }
        byte[] raw = sb.toString().getBytes(StandardCharsets.UTF_8);
        int rawCap = 65536;
        byte[] p = probe(raw, 16384, rawCap);
        assertEquals(rawCap, p.length);
    }

    /** Multi-byte text with no ASCII tags stops at the content target (no over-read). */
    @Test
    public void utf16NoTagsStopsAtContentTarget() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            sb.append('中'); // CJK; no '<' so tagCount stays 0
        }
        byte[] raw = sb.toString().getBytes(StandardCharsets.UTF_16LE);
        byte[] p = probe(raw, 16384, 524288);
        assertTrue(p.length >= 16384 && p.length <= 32768,
                "no tags -> content==raw -> stop near target, got " + p.length);
    }

    /** mark/reset must leave the stream fully re-readable. */
    @Test
    public void streamIsResetAfterProbe() throws Exception {
        byte[] raw = new byte[100_000];
        java.util.Arrays.fill(raw, (byte) 'z');
        try (TikaInputStream tis = TikaInputStream.get(raw)) {
            AdaptiveProbe.read(tis, 16384, 524288);
            byte[] all = tis.readAllBytes();
            assertEquals(raw.length, all.length);
        }
    }

    /** Empty input returns an empty array, never null. */
    @Test
    public void emptyInputReturnsEmpty() throws Exception {
        byte[] p = probe(new byte[0], 16384, 524288);
        assertEquals(0, p.length);
    }
}
