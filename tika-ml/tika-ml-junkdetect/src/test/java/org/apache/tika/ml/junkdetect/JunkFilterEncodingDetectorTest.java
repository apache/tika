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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.quality.TextQualityComparison;
import org.apache.tika.quality.TextQualityDetector;
import org.apache.tika.quality.TextQualityScore;

/**
 * Unit tests for {@link JunkFilterEncodingDetector}.
 *
 * <p>Uses a stub {@link TextQualityDetector} rather than the real
 * {@link JunkDetector} — we are testing arbitration control flow, not
 * the quality of the junk detector's decisions.
 */
public class JunkFilterEncodingDetectorTest {

    /** Stub quality detector: always picks the label matching {@link #preferred}. */
    private static final class PreferenceStub implements TextQualityDetector {
        private final String preferred;

        PreferenceStub(String preferred) {
            this.preferred = preferred;
        }

        @Override
        public TextQualityScore score(String text) {
            return new TextQualityScore(Float.NaN, Float.NaN, Float.NaN,
                    Float.NaN, "UNKNOWN");
        }

        @Override
        public TextQualityComparison compare(String labelA, String candidateA,
                                             String labelB, String candidateB) {
            String winner = preferred.equals(labelA) ? "A"
                    : preferred.equals(labelB) ? "B" : "A";
            return new TextQualityComparison(winner, 0.0f,
                    score(candidateA), score(candidateB), labelA, labelB);
        }
    }

    private static ParseContext contextWith(EncodingResult... results) {
        EncodingDetectorContext ctx = new EncodingDetectorContext();
        ctx.addResult(List.of(results), "stub");
        ParseContext p = new ParseContext();
        p.set(EncodingDetectorContext.class, ctx);
        return p;
    }

    @Test
    public void picksPreferredCharsetFromTwoCandidates() throws Exception {
        Charset utf8 = StandardCharsets.UTF_8;
        Charset win1252 = Charset.forName("windows-1252");
        // Non-ASCII bytes so UTF-8 and windows-1252 decode to different strings
        // (otherwise arbiter sees identical decodings and abstains).
        byte[] bytes = "café résumé naïve".getBytes(StandardCharsets.UTF_8);

        ParseContext pc = contextWith(
                new EncodingResult(utf8, 0.5f, "UTF-8",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(win1252, 0.5f, "windows-1252",
                        EncodingResult.ResultType.STATISTICAL));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(new PreferenceStub("UTF-8"));
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertEquals(1, out.size(), "Expected exactly one result");
            assertEquals(utf8, out.get(0).getCharset());
        }
    }

    @Test
    public void noopWhenNoQualityDetector() throws Exception {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        ParseContext pc = contextWith(
                new EncodingResult(StandardCharsets.UTF_8, 0.5f, "UTF-8",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(Charset.forName("windows-1252"), 0.5f,
                        "windows-1252", EncodingResult.ResultType.STATISTICAL));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector((TextQualityDetector) null);
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertTrue(out.isEmpty(),
                    "No TextQualityDetector → detector must be a no-op");
        }
    }

    @Test
    public void noopWhenOnlyOneCandidate() throws Exception {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        ParseContext pc = contextWith(
                new EncodingResult(StandardCharsets.UTF_8, 0.9f, "UTF-8",
                        EncodingResult.ResultType.DECLARATIVE));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(new PreferenceStub("UTF-8"));
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertTrue(out.isEmpty(),
                    "Single candidate → no arbitration needed, no-op");
        }
    }

    @Test
    public void noopWhenAllDecodingsIdentical() throws Exception {
        // Pure-ASCII bytes decode identically under UTF-8 and windows-1252.
        byte[] bytes = "plain ascii content".getBytes(StandardCharsets.US_ASCII);

        ParseContext pc = contextWith(
                new EncodingResult(StandardCharsets.UTF_8, 0.5f, "UTF-8",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(Charset.forName("windows-1252"), 0.5f,
                        "windows-1252", EncodingResult.ResultType.STATISTICAL));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(new PreferenceStub("UTF-8"));
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertTrue(out.isEmpty(),
                    "Byte-identical decodings → arbiter abstains");
            assertEquals("junk-filter-identical-decodings",
                    pc.get(EncodingDetectorContext.class).getArbitrationInfo());
        }
    }

    // NOTE: a full default-constructor integration test (which would load
    // the bundled JunkDetector via ServiceLoader) is not included here
    // because JunkDetector currently exposes only static factory methods
    // (loadFromClasspath / loadFromPath / load) and has no public no-arg
    // constructor — ServiceLoader cannot instantiate it. Wiring JunkDetector
    // up as a proper SPI provider is tracked as follow-up work for TIKA-4720;
    // at that point this test can be added to exercise the real SPI path.
}
