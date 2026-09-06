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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Chunked stripTags with a carried Cursor must be byte-identical to the single-shot
 * strip of the final buffer, for every chunking -- including boundaries inside tags,
 * comments (the {@code <!} lookahead hold-back), raw script/style bodies, quoted
 * attribute values, and multibyte UTF-8 sequences.
 */
public class HtmlByteStripperResumeTest {

    private static final String[] SAMPLES = {
            "plain text, no markup at all — even multibyte: čšž 中文 🚀",
            "<html><head><title>t</title></head><body>Hello <b>world</b></body></html>",
            "before<!-- a comment with <tags> inside -->after",
            "x<!-- unterminated comment...",
            "a<!DOCTYPE html>b<?xml version=\"1.0\"?>c",
            "<script>var a = '<div>'; // not a tag </script>text<style>.x{}</style>tail",
            "<img alt=\"seen text\" src=\"nope.png\" title='also seen'>body",
            "AT&T &amp; friends &#65; &#x42; &notanentity; &unterminated",
            "stray < less-than and << double and <3 hearts",
            "<a href=\"x\">link</a><ul><li>item</li></ul>",
            "<div class='q' aria-label=\"read me\">deep</div>",
            "čšž<em>中文</em>🚀<!--中-->done",
            "text</scripted>more<script>raw</script>end",
            "<!",
            "<!-",
            "<!--",
            "<!-->",
            "<!---->tail",
    };

    @Test
    public void testEveryBoundaryEqualsSingleShot() {
        for (String sample : SAMPLES) {
            byte[] src = sample.getBytes(StandardCharsets.UTF_8);
            Expected want = singleShot(src);
            // every single split point, and every pair of split points
            for (int cut = 0; cut <= src.length; cut++) {
                check(src, new int[]{cut}, want, sample);
            }
            for (int c1 = 0; c1 <= src.length; c1 += 3) {
                for (int c2 = c1; c2 <= src.length; c2 += 3) {
                    check(src, new int[]{c1, c2}, want, sample);
                }
            }
        }
    }

    @Test
    public void testRandomChunkingsOnConcatenatedSamples() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append(SAMPLES[i % SAMPLES.length]);
        }
        byte[] src = sb.toString().getBytes(StandardCharsets.UTF_8);
        Expected want = singleShot(src);
        Random random = new Random(42);
        for (int trial = 0; trial < 200; trial++) {
            int nCuts = 1 + random.nextInt(8);
            int[] cuts = new int[nCuts];
            for (int i = 0; i < nCuts; i++) {
                cuts[i] = random.nextInt(src.length + 1);
            }
            Arrays.sort(cuts);
            check(src, cuts, want, "concat trial " + trial);
        }
    }

    private record Expected(byte[] out, int tagCount) {
    }

    private static Expected singleShot(byte[] src) {
        byte[] dst = new byte[src.length + 16];
        HtmlByteStripper.Result r = HtmlByteStripper.stripTags(src, 0, src.length, dst, 0);
        return new Expected(Arrays.copyOf(dst, r.length), r.tagCount);
    }

    private static void check(byte[] src, int[] cuts, Expected want, String label) {
        byte[] dst = new byte[src.length + 16];
        HtmlByteStripper.Cursor cursor = new HtmlByteStripper.Cursor();
        int prev = 0;
        for (int cut : cuts) {
            int end = Math.max(prev, cut);
            HtmlByteStripper.stripTags(src, end, dst, cursor, false);
            prev = end;
        }
        HtmlByteStripper.stripTags(src, src.length, dst, cursor, true);
        assertEquals(want.tagCount(), cursor.tagCount(), label + " cuts=" + Arrays.toString(cuts));
        assertArrayEquals(want.out(), Arrays.copyOf(dst, cursor.contentLength()),
                label + " cuts=" + Arrays.toString(cuts));
    }
}
