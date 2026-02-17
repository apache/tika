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
package org.apache.tika.eval.core.textstats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.eval.core.langid.LanguageIDWrapper;
import org.apache.tika.eval.core.tokens.CommonTokenResult;
import org.apache.tika.language.detect.LanguageResult;

public class TextStatsTest {

    @Test
    public void testBasic() throws Exception {
        String txt =
                "The quick brown fox &&^&%@! ; ; ; ;;; ;;; 8675309 jumped over tHe lazy wombat";
        String txtCleaned = "the quick brown fox 8675309 jumped over the lazy wombat";
        List<TextStatsCalculator> calcs = new ArrayList<>();
        calcs.add(new TextProfileSignature());
        calcs.add(new ContentLengthCalculator());
        calcs.add(new TokenEntropy());
        calcs.add(new CommonTokens());
        calcs.add(new TextSha256Signature());
        CompositeTextStatsCalculator calc = new CompositeTextStatsCalculator(calcs);

        Map<Class, Object> stats = calc.calculate(txt);


        CommonTokenResult ctr = (CommonTokenResult) stats.get(CommonTokens.class);
        assertEquals("eng", ctr.getLangCode());
        assertEquals(9, ctr.getAlphabeticTokens());
        assertEquals(8, ctr.getCommonTokens());
        assertEquals(7, ctr.getUniqueCommonTokens());
        assertEquals(8, ctr.getUniqueAlphabeticTokens());
        assertEquals(0.11, ctr.getOOV(), 0.02);


        assertEquals(77, (int) stats.get(ContentLengthCalculator.class));

        assertEquals(3.12, (double) stats.get(TokenEntropy.class), 0.01);

        List<LanguageResult> probabilities =
                (List<LanguageResult>) stats.get(LanguageIDWrapper.class);
        assertEquals("eng", probabilities.get(0).getLanguage());
        // Bigram detector: second-place score is near zero for a clear English sentence
        assertEquals(0.0, probabilities.get(1).getRawScore(), 0.01);

        String textProfileSignature = (String) stats.get(TextProfileSignature.class);
        assertEquals("XF3W27O7IWOJVVNQ4HLKYYPCPPX3L2M72YSEMZ3WADL4VTXVITIA====",
                textProfileSignature);

        assertEquals(new Base32()
                        .encodeAsString(DigestUtils.sha256(txtCleaned.getBytes(StandardCharsets.UTF_8))),
                stats.get(TextSha256Signature.class));
    }

    @Test
    public void testCJK() throws Exception {
        String txt = "普林斯顿大学";
        List<TextStatsCalculator> calcs = new ArrayList<>();
        calcs.add(new TextProfileSignature());
        calcs.add(new CommonTokens());
        CompositeTextStatsCalculator calc = new CompositeTextStatsCalculator(calcs);

        Map<Class, Object> stats = calc.calculate(txt);

        List<LanguageResult> probabilities =
                (List<LanguageResult>) stats.get(LanguageIDWrapper.class);
        // Short Chinese text may be classified as any Chinese variant (cmn, wuu, zho, yue)
        // since they share the same confusable group.
        Set<String> chineseVariants = new HashSet<>(Arrays.asList("cmn", "wuu", "zho", "yue"));
        assertTrue(chineseVariants.contains(probabilities.get(0).getLanguage()),
                "Expected a Chinese variant but got: " + probabilities.get(0).getLanguage());
        // For very short CJK text, the detector may spread probability across
        // Chinese variants; the second-place score can be non-trivial.
        assertTrue(probabilities.get(1).getRawScore() < 0.3,
                "Second-place score unexpectedly high: " + probabilities.get(1).getRawScore());


        String textProfileSignature = (String) stats.get(TextProfileSignature.class);
        assertEquals("XKXLY6FNIGK2KGEF6HOSKSVGYDLLOFIAGO73RLMJ22PZVXBTXFFA====",
                textProfileSignature);

        //now test that if a user accidentally sets mintoken length > 2
        //the output will the be same as empty text
        calcs.clear();
        calcs.add(new TextProfileSignature());
        calc = new CompositeTextStatsCalculator(calcs);

        stats = calc.calculate("");
        String emptyStringSignature = (String) stats.get(TextProfileSignature.class);

        calcs.clear();
        TextProfileSignature tPs = new TextProfileSignature();
        tPs.setMinTokenLength(3);
        calcs.add(tPs);
        calc = new CompositeTextStatsCalculator(calcs);

        stats = calc.calculate(txt);
        assertEquals(emptyStringSignature, (String) stats.get(TextProfileSignature.class));

    }
}
