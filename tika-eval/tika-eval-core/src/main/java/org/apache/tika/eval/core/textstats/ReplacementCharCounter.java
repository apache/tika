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

/**
 * Counts U+FFFD (REPLACEMENT CHARACTER) occurrences in the extracted text.
 *
 * <p>A high replacement-char count signals a decode failure — the charset used
 * to decode the bytes couldn't map them, producing U+FFFD.  Unlike OOV, this is
 * a structural correctness signal that does not depend on the per-language
 * vocabulary, so it does not mis-rank CJK decodes (real CJK is OOV-heavy but
 * has zero U+FFFD; mojibake under the wrong charset has many).</p>
 */
public class ReplacementCharCounter implements StringStatsCalculator<Integer> {
    @Override
    public Integer calculate(String txt) {
        int n = 0;
        for (int i = 0; i < txt.length(); i++) {
            if (txt.charAt(i) == 0xFFFD) {
                n++;
            }
        }
        return n;
    }
}
