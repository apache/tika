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
package org.apache.tika.eval.core.tokens;

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.UAX29URLEmailTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.analysis.util.TokenFilterFactory;

/**
 * Factory for filter that normalizes urls and emails to __url__ and __email__
 * respectively.  <b>WARNING:</b>This will not work correctly unless the
 * {@link UAX29URLEmailTokenizer} is used!  This must be run _before_ the
 * {@link AlphaIdeographFilterFactory}, or else the emails/urls will already
 * be removed!
 */
public class URLEmailNormalizingFilterFactory extends TokenFilterFactory {

    public static final String URL = "___url___";
    public static final String EMAIL = "___email___";
    private static final char[] URL_CHARS = URL.toCharArray();
    private static final char[] EMAIL_CHARS = EMAIL.toCharArray();

    public URLEmailNormalizingFilterFactory(Map<String, String> args) {
        super(args);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new URLEmailFilter(tokenStream);
    }

    /**
     * Normalize urls and emails
     */
    private static class URLEmailFilter extends TokenFilter {

        private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
        private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);


        protected URLEmailFilter(TokenStream input) {
            super(input);
        }


        @Override
        public boolean incrementToken() throws IOException {
            if (!input.incrementToken()) {
                return false;
            }
            //== is actually substantially faster than .equals(String)
            if (typeAtt.type().equals(UAX29URLEmailTokenizer.TOKEN_TYPES[UAX29URLEmailTokenizer.URL])) {
                termAtt.copyBuffer(URL_CHARS, 0, URL_CHARS.length);
                termAtt.setLength(URL_CHARS.length);
            } else if (typeAtt.type().equals(
                    UAX29URLEmailTokenizer.TOKEN_TYPES[UAX29URLEmailTokenizer.EMAIL])) {
                termAtt.copyBuffer(EMAIL_CHARS, 0, EMAIL_CHARS.length);
                termAtt.setLength(EMAIL_CHARS.length);
            }
            return true;
        }
    }
}
