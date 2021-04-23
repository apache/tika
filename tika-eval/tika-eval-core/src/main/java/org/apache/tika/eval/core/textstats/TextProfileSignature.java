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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.mutable.MutableInt;

import org.apache.tika.eval.core.tokens.TokenCounts;

/**
 * Copied nearly directly from Apache Nutch:
 * https://github.com/apache/nutch/blob/master/src/java/org/apache/nutch/crawl/TextProfileSignature.java
 * <p>
 * See documentation: https://nutch.apache.org/apidocs/apidocs-2.0/org/apache/nutch/crawl/TextProfileSignature.html
 * <p>
 * This returns the base32 encoded sha256
 */
public class TextProfileSignature implements TokenCountStatsCalculator<String> {

    int minTokenLength = 2;
    float quantRate = 0.01f;
    boolean secondaryLexicographicSorting = true;

    Base32 base32 = new Base32();

    @Override
    public String calculate(TokenCounts tokenCounts) {
        int maxFreq = -1;
        for (Map.Entry<String, MutableInt> e : tokenCounts.getTokens().entrySet()) {
            if (e.getKey().length() >= minTokenLength) {
                if (e.getValue().intValue() > maxFreq) {
                    maxFreq = e.getValue().intValue();
                }
            }
        }

        int quant = Math.round(maxFreq * quantRate);
        if (quant < 2) {
            if (maxFreq > 1) {
                quant = 2;
            } else {
                quant = 1;
            }
        }

        List<Token> profile = new ArrayList<>();
        for (Map.Entry<String, MutableInt> e : tokenCounts.getTokens().entrySet()) {
            String token = e.getKey();
            if (token.length() >= minTokenLength) {
                int quantCnt = (e.getValue().intValue() / quant) * quant;
                if (quantCnt < quant) {
                    continue;
                }
                profile.add(new Token((e.getValue().intValue() / quant) * quant, e.getKey()));
            }
        }
        profile.sort(new TokenComparator());
        StringBuffer newText = new StringBuffer();
        int i = 0;
        for (Token t : profile) {
            if (i++ > 0) {
                newText.append("\n");
            }
            newText.append(t.val);
        }
        return base32.encodeAsString(DigestUtils.sha256(newText.toString()));
    }

    /**
     * Be careful -- for CJK languages, the default analyzer uses character
     * bigrams.  You will "ignore" all cjk language tokens if you set
     * minTokenLength > 2!
     *
     * @param minTokenLength -- include tokens of this length or greater.
     */
    public void setMinTokenLength(int minTokenLength) {
        this.minTokenLength = minTokenLength;
    }

    public void setQuantRate(float quantRate) {
        this.quantRate = quantRate;
    }

    private static class Token {
        public int cnt;
        public String val;

        public Token(int cnt, String val) {
            this.cnt = cnt;
            this.val = val;
        }

        public String toString() {
            return val + " " + cnt;
        }
    }

    private class TokenComparator implements Comparator<Token> {
        /**
         * Sort tokens first by decreasing frequency and second in lexicographic
         * (Unicode) order
         */
        public int compare(Token t1, Token t2) {
            int diffCnt = t2.cnt - t1.cnt;
            if (diffCnt == 0 && secondaryLexicographicSorting) {
                return t1.val.compareTo(t2.val);
            }
            return diffCnt;
        }
    }
}
