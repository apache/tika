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


public class CommonTokenResult {

    private final String langCode;
    private final int uniqueCommonTokens;//types
    private final int commonTokens;
    private final int uniqueAlphabeticTokens;
    private final int alphabeticTokens;

    public CommonTokenResult(String langCode, int uniqueCommonTokens, int commonTokens,
                             int uniqueAlphabeticTokens, int alphabeticTokens) {
        this.langCode = langCode;
        this.uniqueCommonTokens = uniqueCommonTokens;
        this.commonTokens = commonTokens;
        this.uniqueAlphabeticTokens = uniqueAlphabeticTokens;
        this.alphabeticTokens = alphabeticTokens;
    }

    /**
     *
     * @return the language used to select the common_tokens list
     */
    public String getLangCode() {
        return langCode;
    }

    /**
     *
     * @return total number of "common tokens"
     */
    public int getCommonTokens() {
        return commonTokens;
    }

    /**
     *
     * @return number of unique "common tokens" (types)
     */
    public int getUniqueCommonTokens() {
        return uniqueCommonTokens;
    }

    /**
     *
     * @return number of unique alphabetic tokens (types)
     */
    public int getUniqueAlphabeticTokens() {
        return uniqueAlphabeticTokens;
    }

    /**
     *
     * @return number of tokens that had at least one alphabetic/ideographic character
     * whether or not a common token
     */
    public int getAlphabeticTokens() {
        return alphabeticTokens;
    }

    public double getOOV() {
        return 1.0 - (double)commonTokens/(double)alphabeticTokens;
    }
}
