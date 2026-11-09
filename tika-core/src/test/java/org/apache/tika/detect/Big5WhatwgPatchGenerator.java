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
package org.apache.tika.detect;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Regenerates the patch table in {@link Big5HkscsWhatwgCharset}: every valid Big5 pair whose
 * WHATWG {@code index-big5.txt} decode differs from the JDK's Big5-HKSCS. Not a test.
 *
 * <p>Usage: download https://encoding.spec.whatwg.org/index-big5.txt, then
 * {@code java -cp tika-core/target/test-classes:tika-core/target/classes
 * org.apache.tika.detect.Big5WhatwgPatchGenerator index-big5.txt}. Prints the
 * {@code PAIRS}/{@code VALUES} arrays to stdout and a per-category summary to stderr.
 */
public final class Big5WhatwgPatchGenerator {

    private Big5WhatwgPatchGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Map<Integer, String> whatwg = readIndex(args[0]);
        Charset hkscs = Charset.forName("Big5-HKSCS");
        StringBuilder pairs = new StringBuilder();
        StringBuilder values = new StringBuilder();
        Map<String, Integer> categories = new HashMap<>();
        int n = 0;
        for (int lead = 0x81; lead <= 0xFE; lead++) {
            for (int trail = 0x40; trail <= 0xFE; trail++) {
                if (trail > 0x7E && trail < 0xA1) {
                    continue;
                }
                String jdk = decode(hkscs, lead, trail);
                String web = whatwg.getOrDefault(pointer(lead, trail), "�");
                if (jdk.equals(web)) {
                    continue;
                }
                if (web.equals("�")) {
                    throw new IllegalStateException(String.format(Locale.ROOT,
                            "WHATWG unmaps %02X%02X; the patch cannot express that", lead, trail));
                }
                categories.merge(category(jdk, web), 1, Integer::sum);
                pairs.append(String.format(Locale.ROOT, "0x%02X%02X,%s", lead, trail,
                        ++n % 8 == 0 ? "\n" : " "));
                values.append('"').append(escape(web)).append("\",")
                        .append(n % 4 == 0 ? "\n" : " ");
            }
        }
        System.out.println("PAIRS = {\n" + pairs.toString().trim() + "\n};");
        System.out.println("VALUES = {\n" + values.toString().trim() + "\n};");
        System.err.println("patched pairs: " + n + " " + categories);
    }

    /** WHATWG pointer of a valid Big5 pair. */
    static int pointer(int lead, int trail) {
        return (lead - 0x81) * 157 + (trail < 0x7F ? trail - 0x40 : trail - 0x62);
    }

    static Map<Integer, String> readIndex(String path) throws IOException {
        Map<Integer, String> m = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get(path))) {
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }
            String[] f = line.trim().split("\\s+");
            m.put(Integer.parseInt(f[0]), new String(Character.toChars(Integer.decode(f[1]))));
        }
        // Encoding Standard, big5 decoder: these pointers emit two code points.
        m.put(1133, "Ê̄");
        m.put(1135, "Ê̌");
        m.put(1164, "ê̄");
        m.put(1166, "ê̌");
        return m;
    }

    private static String decode(Charset cs, int lead, int trail) {
        try {
            return cs.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .decode(ByteBuffer.wrap(new byte[]{(byte) lead, (byte) trail})).toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String category(String jdk, String web) {
        if (jdk.equals("�")) {
            return "FFFD->char";
        }
        if (jdk.codePoints().anyMatch(c -> Character.getType(c) == Character.PRIVATE_USE)) {
            return "PUA->char";
        }
        if (Character.isLetter(jdk.codePointAt(0)) && Character.isLetter(web.codePointAt(0))) {
            return "char->different char";
        }
        return "punctuation/symbol variant";
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            b.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
        }
        return b.toString();
    }
}
