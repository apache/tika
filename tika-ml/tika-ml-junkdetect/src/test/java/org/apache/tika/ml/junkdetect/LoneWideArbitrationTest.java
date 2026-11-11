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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.chardetect.MojibusterEncodingDetector;
import org.apache.tika.parser.ParseContext;

/**
 * A statistical UTF-16/32 pick is never Mojibuster's only candidate: windows-1252 is offered as the
 * alternative decode and JunkFilter keeps whichever reads as text.  The assertions hold whatever the
 * model's own pick is, so a retrain cannot break them by chance.
 */
public class LoneWideArbitrationTest {

    private static final Charset WIN1252 = Charset.forName("windows-1252");
    private static MojibusterEncodingDetector mojibuster;
    private static JunkFilterEncodingDetector junkFilter;

    @BeforeAll
    static void load() throws Exception {
        mojibuster = new MojibusterEncodingDetector();
        junkFilter = new JunkFilterEncodingDetector(JunkDetector.loadFromClasspath().getModel());
    }

    /** Mojibuster then JunkFilter, as DefaultEncodingDetector runs them. */
    private static Charset chain(byte[] bytes) throws Exception {
        EncodingDetectorContext ctx = new EncodingDetectorContext();
        ParseContext pc = new ParseContext();
        pc.set(EncodingDetectorContext.class, ctx);
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> moji = mojibuster.detect(tis, new Metadata(), pc);
            assertFalse(moji.isEmpty());
            if (moji.get(0).getResultType() == EncodingResult.ResultType.STATISTICAL
                    && isWide(moji.get(0).getCharset())) {
                assertTrue(moji.stream().anyMatch(r -> !isWide(r.getCharset())),
                        "statistical wide pick returned alone: " + moji);
            }
            ctx.addResult(moji, "MojibusterEncodingDetector");
            List<EncodingResult> out = junkFilter.detect(tis, new Metadata(), pc);
            return (out.isEmpty() ? moji : out).get(0).getCharset();
        }
    }

    private static boolean isWide(Charset cs) {
        return cs.name().startsWith("UTF-16") || cs.name().startsWith("UTF-32");
    }

    private static String ircLog() {
        StringBuilder sb = new StringBuilder();
        String[] nicks = {"<alice>", "<bob>", "<carol>"};
        for (int i = 0; i < 40; i++) {
            sb.append(nicks[i % 3]).append(" the build on \u0003").append(4 + i % 9).append("branch-")
              .append(i).append("\u0003 is \u0002green\u000f again, see ticket ").append(1000 + i)
              .append(" for the \u001funderlined\u000f notes\r\n");
        }
        return sb.toString();
    }

    private static String dosBanner() {
        StringBuilder sb = new StringBuilder("\u0007\u0007 WELCOME TO THE BOARD \u0007\u0007\r\n");
        for (int i = 0; i < 30; i++) {
            sb.append("\u0001 item ").append(i).append(": press any key to continue the download \u0014\r\n");
        }
        sb.append("\u001a");
        return sb.toString();
    }

    @Test
    public void asciiWithControlsIsNotUtf16() throws Exception {
        for (String text : new String[] {ircLog(), dosBanner()}) {
            byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
            Charset pick = chain(bytes);
            assertFalse(isWide(pick), pick.name());
            // 7-bit bytes decode identically under every ASCII superset; the label does not matter
            assertEquals(new String(bytes, WIN1252), new String(bytes, pick));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-16LE", "UTF-16BE"})
    public void bomlessUtf16TextStaysUtf16(String charset) throws Exception {
        String[] texts = {
            "The committee met on Tuesday to review the budget for the coming year. Members agreed that "
            + "the library extension should proceed, and that the café on the ground floor would reopen.",
            "北京是中华人民共和国的首都，也是全国的政治和文化中心。这座城市有三千多年的建城历史。",
            "وقال الوزير إن الحكومة ستعمل على تحسين الخدمات الصحية في جميع المناطق خلال العام المقبل."
        };
        Charset cs = Charset.forName(charset);
        for (String text : texts) {
            assertEquals(cs, chain(text.getBytes(cs)), text.substring(0, 12));
        }
    }
}
