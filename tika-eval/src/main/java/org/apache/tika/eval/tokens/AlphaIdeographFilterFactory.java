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
package org.apache.tika.eval.tokens;

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.FilteringTokenFilter;
import org.apache.lucene.analysis.util.TokenFilterFactory;

/**
 * Factory for filter that only allows tokens with characters that "isAlphabetic"  or "isIdeographic" through.
 */
public class AlphaIdeographFilterFactory extends TokenFilterFactory {



    public AlphaIdeographFilterFactory(Map<String, String> args) {
        super(args);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new AlphaFilter(tokenStream);
    }

    /**
     * Remove tokens tokens that do not contain an "
     */
    private class AlphaFilter extends FilteringTokenFilter {

        private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

        public AlphaFilter(TokenStream in) {
            super(in);
        }

        @Override
        protected boolean accept() throws IOException {
            return isAlphabetic(termAtt.buffer());
        }
    }

    public static boolean isAlphabetic(char[] token) {
        for (int i = 0; i < token.length; i++) {
            int cp = token[i];
            if (Character.isHighSurrogate(token[i])) {
                if (i < token.length-1) {
                    cp = Character.toCodePoint(token[i], token[i + 1]);
                    i++;
                }
            }

            if (Character.isAlphabetic(cp) ||
                    Character.isIdeographic(cp)) {
                return true;
            }
        }
        return false;
    }

}
