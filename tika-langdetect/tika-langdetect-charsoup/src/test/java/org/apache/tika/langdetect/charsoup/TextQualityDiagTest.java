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
package org.apache.tika.langdetect.charsoup;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Diagnostic test to explore text quality scores for encoding arbitration.
 * Not a regression test — just prints scores for analysis.
 */
public class TextQualityDiagTest {

    @Test
    public void dumpScores() {
        // Arabic text in windows-1256
        Charset windows1256 = Charset.forName("windows-1256");
        String arabicText =
                "\u0641\u064a \u0642\u0631\u064a\u0629 \u0645\u0646 " +
                "\u0627\u0644\u0642\u0631\u0649 \u0643\u0627\u0646 " +
                "\u0647\u0646\u0627\u0643 \u0631\u062c\u0644 " +
                "\u062d\u0643\u064a\u0645 \u064a\u0639\u0631\u0641 " +
                "\u0643\u0644 \u0634\u064a\u0621 \u0639\u0646 " +
                "\u0627\u0644\u062d\u064a\u0627\u0629 \u0648\u0643\u0627\u0646 " +
                "\u064a\u0639\u0644\u0645 \u0627\u0644\u0646\u0627\u0633 " +
                "\u0643\u064a\u0641 \u064a\u0639\u064a\u0634\u0648\u0646 " +
                "\u0628\u0633\u0644\u0627\u0645 \u0648\u0627\u0646\u0633\u062c\u0627\u0645.";
        byte[] arabicBytes = arabicText.getBytes(windows1256);

        // "hello world\r\n" as windows-1252
        byte[] helloBytes = "hello world\r\n".getBytes(StandardCharsets.US_ASCII);

        System.out.println("=== Arabic bytes decoded with different charsets ===");
        for (String csName : new String[]{"windows-1256", "x-MacCyrillic", "UTF-8"}) {
            Charset cs = Charset.forName(csName);
            String decoded = CharSoupEncodingDetector.decode(arabicBytes, cs);
            printScores(csName, decoded);
        }

        System.out.println("\n=== 'hello world\\r\\n' decoded with different charsets ===");
        for (String csName : new String[]{"windows-1252", "IBM500"}) {
            Charset cs = Charset.forName(csName);
            String decoded = CharSoupEncodingDetector.decode(helloBytes, cs);
            printScores(csName, decoded);
        }

        // Also try some real-world short text
        System.out.println("\n=== Short real text ===");
        printScores("English sentence", "The quick brown fox jumps over the lazy dog.");
        printScores("French sentence", "Le renard brun rapide saute par-dessus le chien paresseux.");
        printScores("German sentence", "Der schnelle braune Fuchs springt \u00fcber den faulen Hund.");
    }

    private void printScores(String label, String text) {
        int totalChars = text.length();
        int letterCount = 0;
        int replacementCount = 0;
        int controlCount = 0;
        int spaceCount = 0;
        int digitCount = 0;
        int punctCount = 0;
        int otherCount = 0;

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            if (cp == 0xFFFD) {
                replacementCount++;
            } else if (Character.isISOControl(cp) || (cp >= 0x80 && cp <= 0x9F)) {
                controlCount++;
            } else if (Character.isLetter(cp)) {
                letterCount++;
            } else if (Character.isWhitespace(cp)) {
                spaceCount++;
            } else if (Character.isDigit(cp)) {
                digitCount++;
            } else if (isPunctuation(cp)) {
                punctCount++;
            } else {
                otherCount++;
            }
        }

        float letterRatio = totalChars > 0 ? (float) letterCount / totalChars : 0;
        float junkRatio = totalChars > 0 ?
                (float) (replacementCount + controlCount) / totalChars : 0;
        float nonLetterNonSpaceRatio = totalChars > 0 ?
                (float) (totalChars - letterCount - spaceCount) / totalChars : 0;

        System.out.printf(Locale.ROOT,
                "  %-20s len=%3d  letters=%.2f  junk(repl+ctrl)=%.2f  " +
                        "nonLetterNonSpace=%.2f  [L=%d S=%d P=%d D=%d R=%d C=%d O=%d]%n",
                label, totalChars, letterRatio, junkRatio, nonLetterNonSpaceRatio,
                letterCount, spaceCount, punctCount, digitCount,
                replacementCount, controlCount, otherCount);

        // Show first 60 chars with hex for non-printable
        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < Math.min(text.length(), 60); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0x20 && cp < 0x7F) {
                preview.appendCodePoint(cp);
            } else if (Character.isLetter(cp)) {
                preview.appendCodePoint(cp);
            } else {
                preview.append(String.format(Locale.ROOT, "\\u%04X", cp));
            }
        }
        System.out.printf(Locale.ROOT, "  %-20s text: %s%n", "", preview);
    }

    private boolean isPunctuation(int cp) {
        int type = Character.getType(cp);
        return type == Character.CONNECTOR_PUNCTUATION ||
                type == Character.DASH_PUNCTUATION ||
                type == Character.END_PUNCTUATION ||
                type == Character.FINAL_QUOTE_PUNCTUATION ||
                type == Character.INITIAL_QUOTE_PUNCTUATION ||
                type == Character.OTHER_PUNCTUATION ||
                type == Character.START_PUNCTUATION;
    }
}
