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
 * Counts non-ASCII characters (code units &ge; U+0080) in the extracted text.
 *
 * <p>Used as the denominator for the U+FFFD rate (see {@link ReplacementCharCounter}):
 * decode failures only arise from high bytes, so FFFD as a fraction of non-ASCII
 * chars is the un-diluted signal, whereas FFFD over total length collapses to ~0
 * on COMMON / ASCII-dominated documents.  U+FFFD itself is &ge; 0x80, so it is
 * included in this count, keeping the rate in [0, 100].</p>
 */
public class NonAsciiCharCounter implements StringStatsCalculator<Integer> {
    @Override
    public Integer calculate(String txt) {
        int n = 0;
        for (int i = 0; i < txt.length(); i++) {
            if (txt.charAt(i) >= 0x80) {
                n++;
            }
        }
        return n;
    }
}
