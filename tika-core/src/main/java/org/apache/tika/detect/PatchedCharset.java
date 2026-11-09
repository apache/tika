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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A JDK charset with some byte sequences decoded differently, e.g. as the WHATWG Encoding
 * Standard (browsers) decodes them. Everything not in the patch table, including invalid and
 * unmappable input, is the base decoder's to decode or report; encoding is the base's.
 *
 * <p>The table is a resource written by {@code WhatwgPatchGenerator} (tika-core tests):
 * {@code name}, {@code base} and optional {@code lead} header lines, then one
 * {@code HEXBYTES<TAB>U+XXXX[ U+XXXX...]} line per patched sequence. {@code lead} lists the
 * bytes after which the base always consumes one more byte as a unit (e.g. {@code 80-FF});
 * every other byte is a unit by itself. Patched sequences are units: one byte, or a lead and
 * the byte after it.
 *
 * <p>Not registered with {@link Charset#forName}; obtain instances from
 * {@link CharsetSupersets}.
 */
final class PatchedCharset extends Charset {

    private final Charset base;

    private final boolean[] lead = new boolean[256];

    private final String[] single = new String[256];

    /** 1 + index into {@link #pairValues}, by {@code lead << 8 | trail}; null if no pairs. */
    private final short[] pairSlot;

    private final String[] pairValues;

    private final int[] patched;

    private PatchedCharset(String name, Charset base, boolean[] lead, List<int[]> keys,
                           List<String> values) {
        super(name, null);
        this.base = base;
        System.arraycopy(lead, 0, this.lead, 0, 256);
        List<String> pv = new ArrayList<>();
        short[] slot = null;
        patched = new int[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            int[] k = keys.get(i);
            if (k.length == 1) {
                patched[i] = k[0];
                single[k[0]] = values.get(i);
            } else {
                patched[i] = k[0] << 8 | k[1];
                if (slot == null) {
                    slot = new short[0x10000];
                }
                pv.add(values.get(i));
                slot[patched[i]] = (short) pv.size();
            }
        }
        pairSlot = slot;
        pairValues = pv.toArray(new String[0]);
    }

    /** Loads a patch table resource next to this class. */
    static PatchedCharset load(String resource) {
        try (InputStream in = PatchedCharset.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing charset patch " + resource);
            }
            return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)),
                    resource);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static PatchedCharset parse(BufferedReader reader, String resource)
            throws IOException {
        String name = null;
        Charset base = null;
        boolean[] lead = new boolean[256];
        List<int[]> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t");
            if (f.length != 2) {
                throw new IllegalStateException(resource + ": bad line " + line);
            }
            switch (f[0]) {
                case "name":
                    name = f[1];
                    break;
                case "base":
                    base = Charset.forName(f[1]);
                    break;
                case "lead":
                    for (String range : f[1].split(",")) {
                        String[] lh = range.split("-");
                        int lo = Integer.parseInt(lh[0], 16);
                        int hi = Integer.parseInt(lh[lh.length - 1], 16);
                        for (int b = lo; b <= hi; b++) {
                            lead[b] = true;
                        }
                    }
                    break;
                default:
                    keys.add(key(f[0], lead, resource));
                    values.add(value(f[1]));
            }
        }
        if (name == null || base == null) {
            throw new IllegalStateException(resource + ": needs name and base");
        }
        return new PatchedCharset(name, base, lead, keys, values);
    }

    private static int[] key(String hex, boolean[] lead, String resource) {
        int n = hex.length() / 2;
        int[] k = new int[n];
        for (int i = 0; i < n; i++) {
            k[i] = Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        if (hex.length() % 2 != 0 || n != (lead[k[0]] ? 2 : 1)) {
            throw new IllegalStateException(resource + ": " + hex + " is not one unit");
        }
        return k;
    }

    private static String value(String codePoints) {
        StringBuilder sb = new StringBuilder();
        for (String cp : codePoints.split(" ")) {
            sb.appendCodePoint(Integer.parseInt(cp.substring(2), 16));
        }
        return sb.toString();
    }

    /** The JDK charset this patches. */
    Charset base() {
        return base;
    }

    /** True if the base always takes the byte after {@code b} with it. */
    boolean isLead(int b) {
        return lead[b & 0xFF];
    }

    /** The patched sequences: a byte, or {@code lead << 8 | trail}. */
    int[] patchedUnits() {
        return patched.clone();
    }

    /** The patched decode of a one-byte unit, or {@code null}. */
    String patch(int b) {
        return single[b & 0xFF];
    }

    /** The patched decode of a two-byte unit, or {@code null}. */
    String patch(int lead, int trail) {
        if (pairSlot == null) {
            return null;
        }
        int s = pairSlot[(lead & 0xFF) << 8 | (trail & 0xFF)];
        return s == 0 ? null : pairValues[s - 1];
    }

    @Override
    public boolean contains(Charset cs) {
        return cs == this || base.contains(cs);
    }

    @Override
    public CharsetDecoder newDecoder() {
        return new Decoder(this);
    }

    @Override
    public CharsetEncoder newEncoder() {
        return base.newEncoder();
    }

    private static final class Decoder extends CharsetDecoder {

        private final PatchedCharset cs;

        private final CharsetDecoder base;

        private Decoder(PatchedCharset cs) {
            super(cs, cs.base.newDecoder().averageCharsPerByte(),
                    cs.base.newDecoder().maxCharsPerByte());
            this.cs = cs;
            this.base = cs.base.newDecoder();
        }

        @Override
        protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
            while (true) {
                int start = in.position();
                int limit = in.limit();
                // Walk the base's units so a patched sequence is only matched at a unit boundary.
                int i = start;
                int unit = 0;
                String value = null;
                while (i < limit) {
                    int b = in.get(i) & 0xFF;
                    if (!cs.lead[b]) {
                        value = cs.single[b];
                        if (value != null) {
                            unit = 1;
                            break;
                        }
                        i++;
                        continue;
                    }
                    if (i + 1 == limit) {
                        break;
                    }
                    value = cs.patch(b, in.get(i + 1));
                    if (value != null) {
                        unit = 2;
                        break;
                    }
                    i += 2;
                }
                if (i > start) {
                    ByteBuffer run = in.duplicate();
                    run.limit(i);
                    CoderResult cr = base.decode(run, out, false);
                    in.position(run.position());
                    if (cr.isError() || cr.isOverflow() || run.hasRemaining()) {
                        return cr;
                    }
                }
                if (value == null) {
                    return CoderResult.UNDERFLOW;
                }
                if (out.remaining() < value.length()) {
                    return CoderResult.OVERFLOW;
                }
                out.put(value);
                in.position(i + unit);
            }
        }

        @Override
        protected void implReset() {
            base.reset();
        }
    }
}
