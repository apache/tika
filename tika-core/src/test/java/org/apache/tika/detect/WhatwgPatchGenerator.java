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
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Compares a WHATWG Encoding Standard decoder with a JDK charset, unit by unit, and writes the
 * difference as a {@link PatchedCharset} table. Not a test.
 *
 * <p>Download the index files from https://encoding.spec.whatwg.org/ (index-*.txt) into one
 * directory, then run with {@code tika-core/target/test-classes:tika-core/target/classes} on
 * the classpath:
 * <pre>
 * WhatwgPatchGenerator ENCODING JDK_BASE INDEX_DIR [PATCHED_NAME]
 * </pre>
 * ENCODING is a WHATWG name: {@code big5}, {@code shift_jis}, {@code euc-jp}, {@code euc-kr},
 * {@code gb18030}, or a single-byte encoding with an {@code index-ENCODING.txt}. With
 * PATCHED_NAME it prints the table resource; without, the differing units as TSV. A summary by
 * category goes to stderr either way.
 *
 * <p>Units are the byte sequences the WHATWG decoder reads as one code point (or two, for the
 * four big5 pointers that emit two). A pointer with no index entry decodes as U+FFFD, followed
 * by the trail when it is ASCII (the WHATWG decoder reads it again). The table leaves error
 * handling to the base: units whose WHATWG decode has a U+FFFD are not patched.
 */
public final class WhatwgPatchGenerator {

    private static final String FFFD = "�";

    private WhatwgPatchGenerator() {
    }

    public static void main(String[] args) throws IOException {
        String encoding = args[0];
        Charset base = Charset.forName(args[1]);
        Path dir = Paths.get(args[2]);
        Map<String, String[]> diff = diff(encoding, base, dir);
        Map<String, Integer> categories = new TreeMap<>();
        for (String[] d : diff.values()) {
            categories.merge(d[2], 1, Integer::sum);
        }
        System.err.println(encoding + " vs " + base.name() + ": " + diff.size()
                + " differing units " + categories);
        if (args.length > 3) {
            System.out.print(resource(encoding, base, dir, args[3], diff));
        } else {
            for (Map.Entry<String, String[]> e : diff.entrySet()) {
                System.out.println(e.getKey() + "\t" + cps(e.getValue()[0]) + "\t"
                        + cps(e.getValue()[1]) + "\t" + e.getValue()[2]);
            }
        }
    }

    /** Differing units: hex bytes to {JDK decode, WHATWG decode, category}. */
    static Map<String, String[]> diff(String encoding, Charset base, Path dir) throws IOException {
        Map<String, String[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : whatwgUnits(encoding, dir).entrySet()) {
            String jdk = decode(base, HexFormat.of().parseHex(e.getKey()));
            String web = e.getValue();
            if (!jdk.equals(web)) {
                out.put(e.getKey(), new String[]{jdk, web, category(jdk, web)});
            }
        }
        return out;
    }

    /** Every unit the WHATWG decoder reads for {@code encoding}, hex bytes to its decode. */
    static Map<String, String> whatwgUnits(String encoding, Path dir) throws IOException {
        Map<String, String> u = new LinkedHashMap<>();
        switch (encoding) {
            case "big5": {
                Map<Integer, String> index = index(dir, "big5");
                index.put(1133, "Ê̄");
                index.put(1135, "Ê̌");
                index.put(1164, "ê̄");
                index.put(1166, "ê̌");
                for (int l = 0x81; l <= 0xFE; l++) {
                    for (int t = 0x40; t <= 0xFE; t++) {
                        if (t <= 0x7E || t >= 0xA1) {
                            int p = (l - 0x81) * 157 + (t < 0x7F ? t - 0x40 : t - 0x62);
                            u.put(hex(l, t), index.getOrDefault(p, error(t)));
                        }
                    }
                }
                break;
            }
            case "shift_jis": {
                Map<Integer, String> index = index(dir, "jis0208");
                u.put(hex(0x80), "\u0080");
                for (int b = 0xA1; b <= 0xDF; b++) {
                    u.put(hex(b), cp(0xFF61 + b - 0xA1));
                }
                for (int l = 0x81; l <= 0xFC; l++) {
                    if (l >= 0xA0 && l <= 0xDF) {
                        continue;
                    }
                    for (int t = 0x40; t <= 0xFC; t++) {
                        if (t == 0x7F) {
                            continue;
                        }
                        int p = (l - (l < 0xA0 ? 0x81 : 0xC1)) * 188 + t - (t < 0x7F ? 0x40 : 0x41);
                        String v = p >= 8836 && p <= 10715 ? cp(0xE000 + p - 8836)
                                : index.getOrDefault(p, error(t));
                        u.put(hex(l, t), v);
                    }
                }
                break;
            }
            case "euc-jp": {
                Map<Integer, String> jis0208 = index(dir, "jis0208");
                Map<Integer, String> jis0212 = index(dir, "jis0212");
                for (int b = 0xA1; b <= 0xDF; b++) {
                    u.put(hex(0x8E, b), cp(0xFF61 + b - 0xA1));
                }
                for (int l = 0xA1; l <= 0xFE; l++) {
                    for (int t = 0xA1; t <= 0xFE; t++) {
                        int p = (l - 0xA1) * 94 + t - 0xA1;
                        u.put(hex(l, t), jis0208.getOrDefault(p, FFFD));
                        u.put(hex(0x8F, l, t), jis0212.getOrDefault(p, FFFD));
                    }
                }
                break;
            }
            case "euc-kr": {
                Map<Integer, String> index = index(dir, "euc-kr");
                for (int l = 0x81; l <= 0xFE; l++) {
                    for (int t = 0x41; t <= 0xFE; t++) {
                        u.put(hex(l, t), index.getOrDefault((l - 0x81) * 190 + t - 0x41, error(t)));
                    }
                }
                break;
            }
            case "gb18030": {
                Map<Integer, String> index = index(dir, "gb18030");
                TreeMap<Integer, Integer> ranges = new TreeMap<>();
                for (Map.Entry<Integer, String> e : index(dir, "gb18030-ranges").entrySet()) {
                    ranges.put(e.getKey(), e.getValue().codePointAt(0));
                }
                u.put(hex(0x80), "€");
                for (int l = 0x81; l <= 0xFE; l++) {
                    for (int t = 0x40; t <= 0xFE; t++) {
                        if (t != 0x7F) {
                            int p = (l - 0x81) * 190 + t - (t < 0x7F ? 0x40 : 0x41);
                            u.put(hex(l, t), index.getOrDefault(p, error(t)));
                        }
                    }
                }
                for (int b1 = 0x81; b1 <= 0xFE; b1++) {
                    for (int b2 = 0x30; b2 <= 0x39; b2++) {
                        for (int b3 = 0x81; b3 <= 0xFE; b3++) {
                            for (int b4 = 0x30; b4 <= 0x39; b4++) {
                                int p = (b1 - 0x81) * 12600 + (b2 - 0x30) * 1260
                                        + (b3 - 0x81) * 10 + b4 - 0x30;
                                String v;
                                if ((p > 39419 && p < 189000) || p > 1237575) {
                                    v = FFFD;
                                } else if (p == 7457) {
                                    v = "";
                                } else {
                                    Map.Entry<Integer, Integer> r = ranges.floorEntry(p);
                                    v = cp(r.getValue() + p - r.getKey());
                                }
                                u.put(hex(b1, b2, b3, b4), v);
                            }
                        }
                    }
                }
                break;
            }
            default: {
                Map<Integer, String> index = index(dir, encoding);
                for (int b = 0x80; b <= 0xFF; b++) {
                    u.put(hex(b), index.getOrDefault(b - 0x80, FFFD));
                }
            }
        }
        return u;
    }

    /** WHATWG decode of a lead and unmapped trail: an ASCII trail is read again as itself. */
    private static String error(int trail) {
        return trail < 0x80 ? FFFD + (char) trail : FFFD;
    }

    /** Bytes of the WHATWG unit starting at {@code i} (1 if not a multibyte lead). */
    static int unitLength(String encoding, byte[] b, int i) {
        int x = b[i] & 0xFF;
        int next = i + 1 < b.length ? b[i + 1] & 0xFF : -1;
        switch (encoding) {
            case "big5":
            case "euc-kr":
                return x >= 0x81 && x <= 0xFE ? 2 : 1;
            case "shift_jis":
                return (x >= 0x81 && x <= 0x9F) || (x >= 0xE0 && x <= 0xFC) ? 2 : 1;
            case "euc-jp":
                return x == 0x8F ? 3 : x == 0x8E || (x >= 0xA1 && x <= 0xFE) ? 2 : 1;
            case "gb18030":
                return x >= 0x81 && x <= 0xFE ? (next >= 0x30 && next <= 0x39 ? 4 : 2) : 1;
            default:
                return 1;
        }
    }

    /**
     * Bytes in the unit {@code cs} reads starting with each byte (1, 2 or 3, whatever follows),
     * or {@code null} if some byte has no such fixed length.
     */
    static int[] unitLengths(Charset cs) {
        int[] len = new int[256];
        CharsetDecoder d = cs.newDecoder();
        for (int b = 0; b < 256; b++) {
            if (consumed(d, (byte) b) == 1) {
                len[b] = 1;
                continue;
            }
            int n = consumed(d, (byte) b, (byte) 0) == 2 ? 2 : 3;
            for (int x = 0; x < 256; x++) {
                if (n == 2 ? consumed(d, (byte) b, (byte) x) != 2
                        : !allConsume3(d, (byte) b, (byte) x)) {
                    return null;
                }
            }
            len[b] = n;
        }
        return len;
    }

    private static boolean allConsume3(CharsetDecoder d, byte b, byte x) {
        for (int y = 0; y < 256; y++) {
            if (consumed(d, b, x, (byte) y) != 3) {
                return false;
            }
        }
        return true;
    }

    /** Bytes the first unit of {@code b} takes (0 if it needs more input). */
    private static int consumed(CharsetDecoder d, byte... b) {
        for (int k = 1; k <= b.length; k++) {
            d.reset();
            ByteBuffer in = ByteBuffer.wrap(b, 0, k);
            CoderResult cr = d.decode(in, CharBuffer.allocate(8), false);
            if (cr.isError()) {
                return in.position() + cr.length();
            }
            if (in.position() > 0) {
                return in.position();
            }
        }
        return 0;
    }

    private static String resource(String encoding, Charset base, Path dir, String name,
                                   Map<String, String[]> diff) throws IOException {
        int[] len = unitLengths(base);
        if (len == null) {
            throw new IllegalStateException(base.name() + " has units PatchedCharset cannot walk");
        }
        StringBuilder sb = new StringBuilder();
        String file = encoding.equals("shift_jis") || encoding.equals("euc-jp") ? "jis0208"
                : encoding;
        for (String line : Files.readAllLines(dir.resolve("index-" + file + ".txt"))) {
            if (line.startsWith("# Identifier") || line.startsWith("# Date")) {
                sb.append(line).append('\n');
            }
        }
        sb.insert(0, String.format(Locale.ROOT, "# %s decoded as the WHATWG Encoding Standard"
                + " %s decoder, where they differ.%n# Generated by WhatwgPatchGenerator from"
                + " https://encoding.spec.whatwg.org/index-%s.txt%n", base.name(), encoding, file));
        sb.append(BSD_NOTICE);
        sb.append("name\t").append(name).append('\n');
        sb.append("base\t").append(base.name()).append('\n');
        for (int n = 2; n <= 3; n++) {
            String ranges = ranges(len, n);
            if (!ranges.isEmpty()) {
                sb.append(n == 2 ? "lead" : "lead3").append('\t').append(ranges).append('\n');
            }
        }
        for (Map.Entry<String, String[]> e : diff.entrySet()) {
            int first = Integer.parseInt(e.getKey().substring(0, 2), 16);
            if (e.getKey().length() != 2 * len[first]) {
                throw new IllegalStateException(e.getKey() + " is not one " + base.name() + " unit");
            }
            if (e.getValue()[1].indexOf('\uFFFD') >= 0) {
                // error handling stays the base's
                continue;
            }
            if (len[first] > 2) {
                throw new IllegalStateException(e.getKey() + ": PatchedCharset patches 1- and 2-byte units");
            }
            sb.append(e.getKey()).append('\t').append(cps(e.getValue()[1])).append('\n');
        }
        return sb.toString();
    }

    private static String ranges(int[] len, int n) {
        StringBuilder sb = new StringBuilder();
        for (int b = 0; b < 256; b++) {
            if (len[b] == n && (b == 0 || len[b - 1] != n)) {
                int e = b;
                while (e + 1 < 256 && len[e + 1] == n) {
                    e++;
                }
                sb.append(sb.length() > 0 ? "," : "")
                        .append(String.format(Locale.ROOT, "%02X-%02X", b, e));
            }
        }
        return sb.toString();
    }

    private static String category(String jdk, String web) {
        if (jdk.indexOf('\uFFFD') >= 0 && web.indexOf('\uFFFD') >= 0) {
            // both report an error; they differ only in how many bytes it takes
            return "error handling";
        }
        if (jdk.chars().allMatch(c -> c == 0xFFFD)) {
            return isC1(web) ? "FFFD->C1" : "FFFD->char";
        }
        if (web.equals(FFFD)) {
            return isC1(jdk) ? "C1->FFFD" : "char->FFFD";
        }
        if (isC1(jdk)) {
            return "C1->char";
        }
        if (isPua(jdk) || isPua(web)) {
            return "PUA";
        }
        return "char->different char";
    }

    private static boolean isC1(String s) {
        return s.length() == 1 && s.charAt(0) >= 0x80 && s.charAt(0) <= 0x9F;
    }

    private static boolean isPua(String s) {
        return s.codePoints().anyMatch(c -> Character.getType(c) == Character.PRIVATE_USE);
    }

    private static Map<Integer, String> index(Path dir, String name) throws IOException {
        Map<Integer, String> m = new HashMap<>();
        for (String line : Files.readAllLines(dir.resolve("index-" + name + ".txt"),
                StandardCharsets.UTF_8)) {
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }
            String[] f = line.trim().split("\\s+");
            m.put(Integer.parseInt(f[0]), cp(Integer.decode(f[1])));
        }
        return m;
    }

    private static String decode(Charset cs, byte[] b) {
        try {
            return cs.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .decode(ByteBuffer.wrap(b)).toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(int... bytes) {
        StringBuilder sb = new StringBuilder();
        for (int b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02X", b));
        }
        return sb.toString();
    }

    private static String cp(int c) {
        return new String(Character.toChars(c));
    }

    static String cps(String s) {
        List<String> l = new ArrayList<>();
        s.codePoints().forEach(c -> l.add(String.format(Locale.ROOT, "U+%04X", c)));
        return String.join(" ", l);
    }

    private static final String BSD_NOTICE = "#\n"
            + "# Copyright (c) WHATWG (Apple, Google, Mozilla, Microsoft).\n"
            + "#\n"
            + "# Redistribution and use in source and binary forms, with or without\n"
            + "# modification, are permitted provided that the following conditions are met:\n"
            + "# 1. Redistributions of source code must retain the above copyright notice,\n"
            + "#    this list of conditions and the following disclaimer.\n"
            + "# 2. Redistributions in binary form must reproduce the above copyright notice,\n"
            + "#    this list of conditions and the following disclaimer in the documentation\n"
            + "#    and/or other materials provided with the distribution.\n"
            + "# 3. Neither the name of the copyright holder nor the names of its\n"
            + "#    contributors may be used to endorse or promote products derived from\n"
            + "#    this software without specific prior written permission.\n"
            + "#\n"
            + "# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\"\n"
            + "# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE\n"
            + "# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE\n"
            + "# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE\n"
            + "# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR\n"
            + "# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF\n"
            + "# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS\n"
            + "# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN\n"
            + "# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)\n"
            + "# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE\n"
            + "# POSSIBILITY OF SUCH DAMAGE.\n"
            + "#\n";
}
