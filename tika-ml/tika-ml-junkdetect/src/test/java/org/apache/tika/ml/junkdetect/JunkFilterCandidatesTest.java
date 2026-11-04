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
import static org.junit.jupiter.api.Assertions.assertNull;
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

/** {@link JunkFilterEncodingDetector#candidates} reports what {@link JunkFilterEncodingDetector#detect} acts on. */
public class JunkFilterCandidatesTest {

    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final Charset W1252 = Charset.forName("windows-1252");
    private static final Charset L1 = StandardCharsets.ISO_8859_1;

    private final JunkFilterEncodingDetector filter =
            new JunkFilterEncodingDetector((HashedJunkModel) null);

    private static EncodingDetectorContext context(EncodingResult... results) {
        EncodingDetectorContext ctx = new EncodingDetectorContext();
        ctx.addResult(List.of(results), "stub");
        return ctx;
    }

    private static EncodingResult r(Charset cs, EncodingResult.ResultType t) {
        return new EncodingResult(cs, 0.9f, cs.name(), t);
    }

    private JunkFilterEncodingDetector.Candidates candidates(byte[] bytes, EncodingDetectorContext ctx)
            throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            return filter.candidates(tis, ctx);
        }
    }

    @Test
    public void modelArbitratesDifferingDecodes() throws Exception {
        byte[] b = "café résumé".getBytes(UTF8);
        JunkFilterEncodingDetector.Candidates c = candidates(b, context(
                r(UTF8, EncodingResult.ResultType.STRUCTURAL), r(W1252, EncodingResult.ResultType.DECLARATIVE)));
        assertEquals(JunkFilterEncodingDetector.Candidates.Rule.MODEL, c.getRule());
        assertEquals(List.of(UTF8, W1252), c.getBandCharsets());
        assertEquals("café résumé", c.getTexts().get(UTF8));
        assertEquals("cafÃ© rÃ©sumÃ©", c.getTexts().get(W1252));
        assertEquals(UTF8, c.getStructural());
        assertEquals(W1252, c.getDeclared());
        assertNull(c.getRuleCharset());
    }

    @Test
    public void declarationWithAnEquivalentDecodeIsTheRule() throws Exception {
        byte[] b = "café".getBytes(W1252);
        EncodingDetectorContext ctx = context(
                r(W1252, EncodingResult.ResultType.DECLARATIVE), r(L1, EncodingResult.ResultType.STATISTICAL));
        JunkFilterEncodingDetector.Candidates c = candidates(b, ctx);
        assertEquals(JunkFilterEncodingDetector.Candidates.Rule.PREFER_DECLARATIVE, c.getRule());
        assertEquals(W1252, c.getRuleCharset());

        ParseContext pc = new ParseContext();
        pc.set(EncodingDetectorContext.class, ctx);
        JunkFilterEncodingDetector withModel = new JunkFilterEncodingDetector(JunkDetector.loadFromClasspath().getModel());
        try (TikaInputStream tis = TikaInputStream.get(b)) {
            List<EncodingResult> out = withModel.detect(tis, new Metadata(), pc);
            assertEquals(W1252, out.get(0).getCharset());
        }
        assertEquals("junk-filter-prefer-declarative", ctx.getArbitrationInfo());
    }

    @Test
    public void identicalAndSingleDecodesAreRules() throws Exception {
        byte[] ascii = "plain ascii".getBytes(StandardCharsets.US_ASCII);
        assertEquals(JunkFilterEncodingDetector.Candidates.Rule.IDENTICAL_DECODINGS, candidates(ascii, context(
                r(UTF8, EncodingResult.ResultType.STATISTICAL), r(W1252, EncodingResult.ResultType.STATISTICAL)))
                .getRule());
        assertEquals(JunkFilterEncodingDetector.Candidates.Rule.TOO_FEW_TEXTS,
                candidates(ascii, context(r(UTF8, EncodingResult.ResultType.STATISTICAL))).getRule());
    }

    @Test
    public void probeLosesItsByteOrderMark() throws Exception {
        byte[] b = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i', (byte) 0xC3, (byte) 0xA9};
        JunkFilterEncodingDetector.Candidates c = candidates(b, context(
                r(UTF8, EncodingResult.ResultType.DECLARATIVE), r(W1252, EncodingResult.ResultType.STATISTICAL)));
        assertEquals(4, c.getProbe().length);
        assertEquals("hié", c.getTexts().get(UTF8));
    }

    @Test
    public void nothingToReportWithoutStreamContextOrBytes() throws Exception {
        assertNull(filter.candidates(null, context(r(UTF8, EncodingResult.ResultType.STATISTICAL))));
        try (TikaInputStream tis = TikaInputStream.get("x".getBytes(UTF8))) {
            assertNull(filter.candidates(tis, null));
        }
        assertNull(candidates(new byte[0], context(r(UTF8, EncodingResult.ResultType.STATISTICAL))));
    }

    @Test
    public void lowConfidenceStatisticalTailIsOutsideTheBand() throws Exception {
        byte[] b = "café".getBytes(UTF8);
        EncodingDetectorContext ctx = new EncodingDetectorContext();
        ctx.addResult(List.of(new EncodingResult(UTF8, 0.9f), new EncodingResult(W1252, 0.8f),
                new EncodingResult(Charset.forName("windows-1250"), 0.2f)), "stub");
        JunkFilterEncodingDetector.Candidates c = candidates(b, ctx);
        assertEquals(List.of(UTF8, W1252), c.getBandCharsets());
        assertTrue(c.getTexts().keySet().containsAll(c.getBandCharsets()));
    }
}
