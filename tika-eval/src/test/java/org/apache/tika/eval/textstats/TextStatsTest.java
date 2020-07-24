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
package org.apache.tika.eval.textstats;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tika.eval.langid.Language;
import org.apache.tika.eval.langid.LanguageIDWrapper;
import org.apache.tika.eval.tokens.CommonTokenResult;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TextStatsTest {

    @Test
    public void testBasic() throws Exception {
        String txt = "The quick brown fox &&^&%@! 8675309 jumped over tHe lazy wombat";
        String txtCleaned = "the quick brown fox 8675309 jumped over the lazy wombat";
        List<TextStatsCalculator> calcs = new ArrayList<>();
        calcs.add(new TextProfileSignature());
        calcs.add(new ContentLengthCalculator());
        calcs.add(new TokenEntropy());
        calcs.add(new CommonTokens());
        calcs.add(new TextSha256Signature());
        CompositeTextStatsCalculator calc = new CompositeTextStatsCalculator(calcs);

        Map<Class, Object> stats = calc.calculate(txt);


        CommonTokenResult ctr = (CommonTokenResult)stats.get(CommonTokens.class);
        assertEquals("eng", ctr.getLangCode());
        assertEquals( 9, ctr.getAlphabeticTokens());
        assertEquals( 8, ctr.getCommonTokens());
        assertEquals( 7, ctr.getUniqueCommonTokens());
        assertEquals( 8, ctr.getUniqueAlphabeticTokens());
        assertEquals( 0.11, ctr.getOOV(), 0.02);


        assertEquals(63, (int)stats.get(ContentLengthCalculator.class));

        assertEquals(3.12, (double)stats.get(TokenEntropy.class), 0.01);

        List<Language> probabilities = (List<Language>) stats.get(LanguageIDWrapper.class);
        assertEquals("eng", probabilities.get(0).getLanguage());
        assertEquals(0.01, probabilities.get(1).getConfidence(), 0.01);

        String textProfileSignature = (String)stats.get(TextProfileSignature.class);
        assertEquals("NCUFXDJOUJL45VIFW775OY47BQSYYBQOLJFXALMS3F3J7DFJQNPA====", textProfileSignature);

        assertEquals(new Base32().encodeAsString(
                DigestUtils.sha256(txtCleaned.getBytes(StandardCharsets.UTF_8))),
                stats.get(TextSha256Signature.class));
    }

    @Test
    public void testCJK() throws Exception {
        String txt = "普林斯顿大学";
        List<TextStatsCalculator> calcs = new ArrayList<>();
        calcs.add(new TextProfileSignature());
        CompositeTextStatsCalculator calc = new CompositeTextStatsCalculator(calcs);

        Map<Class, Object> stats = calc.calculate(txt);
        String textProfileSignature = (String)stats.get(TextProfileSignature.class);
        assertEquals("XKXLY6FNIGK2KGEF6HOSKSVGYDLLOFIAGO73RLMJ22PZVXBTXFFA====", textProfileSignature);

        //now test that if a user accidentally sets mintoken length > 2
        //the output will the be same as empty text
        calcs.clear();
        calcs.add(new TextProfileSignature());
        calc = new CompositeTextStatsCalculator(calcs);

        stats = calc.calculate("");
        String emptyStringSignature = (String)stats.get(TextProfileSignature.class);

        calcs.clear();
        TextProfileSignature tPs = new TextProfileSignature();
        tPs.setMinTokenLength(3);
        calcs.add(tPs);
        calc = new CompositeTextStatsCalculator(calcs);

        stats = calc.calculate(txt);
        assertEquals(emptyStringSignature, (String)stats.get(TextProfileSignature.class));

    }
}
