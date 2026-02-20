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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Randomized stress test for {@link TikaEvalTokenizer}.
 * <p>
 * Throws arbitrary Unicode strings at the tokenizer and checks that:
 * <ul>
 *   <li>No exceptions are thrown.</li>
 *   <li>No emitted token exceeds {@link TikaEvalTokenizer#MAX_TOKEN_LENGTH}.</li>
 *   <li>In {@link TikaEvalTokenizer.Mode#COMMON_TOKENS} mode, no token is shorter
 *       than {@link TikaEvalTokenizer#MIN_ALPHA_TOKEN_LENGTH}.</li>
 *   <li>Passing a {@code maxTokens} limit never produces more tokens than that limit.</li>
 * </ul>
 * <p>
 * The seed is chosen randomly each run. If the test fails, the seed is printed
 * in the failure message so the exact run can be reproduced by passing it to
 * {@link Random#Random(long)}.
 */
public class TikaEvalTokenizerFuzzTest {

    private static final int TRIALS = 2000;
    private static final int MAX_STRING_LENGTH = 2000;

    @Test
    public void testRandomStrings() {
        long seed = new Random().nextLong();
        Random rng = new Random(seed);

        for (int trial = 0; trial < TRIALS; trial++) {
            String input = randomString(rng, rng.nextInt(MAX_STRING_LENGTH + 1));
            int maxTokens = rng.nextBoolean() ? Integer.MAX_VALUE : rng.nextInt(50) + 1;

            checkInvariants(input, TikaEvalTokenizer.Mode.STANDARD, maxTokens, seed, trial);
            checkInvariants(input, TikaEvalTokenizer.Mode.COMMON_TOKENS, maxTokens, seed, trial);
        }
    }

    private static void checkInvariants(String input, TikaEvalTokenizer.Mode mode,
                                        int maxTokens, long seed, int trial) {
        List<String> tokens = new ArrayList<>();
        String ctx = context(seed, trial, input, mode, maxTokens);
        try {
            TikaEvalTokenizer.tokenize(input, mode, maxTokens, tokens::add);
        } catch (Exception e) {
            fail("Unexpected exception — " + ctx + ": " + e);
        }

        if (maxTokens != Integer.MAX_VALUE) {
            assertTrue(tokens.size() <= maxTokens,
                    "Token count " + tokens.size() + " exceeds maxTokens " + maxTokens
                            + " — " + ctx);
        }

        for (String token : tokens) {
            assertTrue(token.length() <= TikaEvalTokenizer.MAX_TOKEN_LENGTH,
                    "Token length " + token.length() + " exceeds MAX_TOKEN_LENGTH — " + ctx);

            if (mode == TikaEvalTokenizer.Mode.COMMON_TOKENS && !isCJKBigram(token)) {
                assertTrue(token.length() >= TikaEvalTokenizer.MIN_ALPHA_TOKEN_LENGTH,
                        "Non-CJK token length " + token.length()
                                + " is below MIN_ALPHA_TOKEN_LENGTH — " + ctx);
            }
        }
    }

    /**
     * Generate a random string of up to {@code maxLen} chars drawn from a wide
     * Unicode range, including ASCII, Latin extended, CJK, Arabic, combining marks,
     * and raw surrogates to stress-test boundary handling.
     */
    private static String randomString(Random rng, int maxLen) {
        StringBuilder sb = new StringBuilder(maxLen);
        int remaining = maxLen;
        while (remaining > 0) {
            int kind = rng.nextInt(8);
            switch (kind) {
                case 0: // ASCII printable
                    sb.append((char) (rng.nextInt(95) + 32));
                    remaining--;
                    break;
                case 1: // Basic Latin letter
                    sb.append((char) ('a' + rng.nextInt(26)));
                    remaining--;
                    break;
                case 2: // CJK Unified Ideographs (U+4E00–U+9FFF)
                    sb.append((char) (0x4E00 + rng.nextInt(0x9FFF - 0x4E00 + 1)));
                    remaining--;
                    break;
                case 3: // Arabic (U+0600–U+06FF)
                    sb.append((char) (0x0600 + rng.nextInt(0x100)));
                    remaining--;
                    break;
                case 4: // Combining marks (U+0300–U+036F)
                    sb.append((char) (0x0300 + rng.nextInt(0x70)));
                    remaining--;
                    break;
                case 5: // Random BMP codepoint
                    sb.append((char) rng.nextInt(0xFFFE));
                    remaining--;
                    break;
                case 6: // Supplementary character via surrogate pair
                    if (remaining >= 2) {
                        int cp = 0x10000 + rng.nextInt(0xFFFF);
                        char[] chars = Character.toChars(cp);
                        sb.append(chars);
                        remaining -= chars.length;
                    }
                    break;
                case 7: // Short repeated run (stress-tests buffer accumulation)
                    int runLen = Math.min(remaining, rng.nextInt(20) + 1);
                    char c = (char) ('a' + rng.nextInt(26));
                    for (int j = 0; j < runLen; j++) {
                        sb.append(c);
                    }
                    remaining -= runLen;
                    break;
                default:
                    break;
            }
        }
        return sb.toString();
    }

    /** CJK bigrams are exactly 2 ideographic codepoints — exempt from MIN_ALPHA_TOKEN_LENGTH. */
    private static boolean isCJKBigram(String token) {
        return token.codePointCount(0, token.length()) == 2
                && Character.isIdeographic(token.codePointAt(0));
    }

    private static String context(long seed, int trial, String input,
                                  TikaEvalTokenizer.Mode mode, int maxTokens) {
        String preview = input.length() > 30
                ? input.substring(0, 30).replace("\n", "\\n") + "..."
                : input.replace("\n", "\\n");
        return "seed=" + seed + " trial=" + trial
                + " mode=" + mode + " maxTokens=" + maxTokens
                + " input[" + input.length() + "]='" + preview + "'";
    }
}
