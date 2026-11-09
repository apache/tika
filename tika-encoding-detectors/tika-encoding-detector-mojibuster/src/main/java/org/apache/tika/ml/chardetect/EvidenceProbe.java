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
package org.apache.tika.ml.chardetect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;

import org.apache.tika.detect.BOMDetector;
import org.apache.tika.io.TikaInputStream;

/**
 * Encoding-detection probe that adds evidence from past the {@link AdaptiveProbe} prefix.
 *
 * <p>A document whose first non-ASCII byte sits past the prefix (an email with a long ASCII
 * header block, a log, a CSV) gives the detectors nothing to work with.  When the prefix is
 * not already decisive, this scans on, takes windows around each byte &ge; 0x80 and around
 * the 7-bit signatures of ISO-2022 ({@code ESC $}, SO/SI), HZ (<code>~{</code>) and UTF-7
 * ({@code +...-}), and appends them to the prefix, each after a {@code '\n'}.  A prefix
 * with a BOM, a NUL or an ISO-2022 escape is evidence enough and is returned as is.
 *
 * <ul>
 *   <li>The scan stops at {@value #STOP_HIGH} high bytes or {@value #STOP_UTF8} clean UTF-8
 *   sequences, at EOF, or at the cap: {@value #FILE_SCAN_CAP} bytes when the stream's length
 *   is measured (file, byte array), {@value #STREAM_SCAN_CAP} otherwise, since a passthrough
 *   stream buffers what the mark covers.</li>
 *   <li>Evidence is used only if it reached a stop, is clean UTF-8, holds at least
 *   {@value #ACCEPT_HIGH} high bytes, or is signature-only with no high byte at all;
 *   otherwise the prefix is returned unchanged.</li>
 *   <li>Signature windows stop being taken after {@value #SIG_BUDGET} bytes of them.</li>
 *   <li>The prefix is always kept whole: 7-bit evidence is ASCII-range, and EBCDIC
 *   Hebrew/Arabic letters are all below 0x80.</li>
 * </ul>
 *
 * <p>Windows start after two ASCII bytes and end at two ASCII bytes, so no multibyte
 * character is cut (DBCS trail bytes can be ASCII-range); touching windows merge; a
 * signature window runs to its closing marker, at most {@value #SIG_MAX} bytes.
 */
public final class EvidenceProbe {

    /** {@link org.apache.tika.detect.EncodingProbeCache} kind for this probe. */
    public static final String KIND = "evidence";

    static final long FILE_SCAN_CAP = 16L << 20;
    static final long STREAM_SCAN_CAP = 1L << 20;
    static final int STOP_HIGH = 50;
    static final int STOP_UTF8 = 16;
    static final int ACCEPT_HIGH = 8;
    static final int WINDOW = 32;
    static final int MAX_WINDOW = 256;
    static final int ALIGN_BACK = 256;
    static final int SIG_MAX = 8192;
    // base64 and binary are full of false 7-bit signatures; high-byte windows are bounded by STOP_HIGH
    static final int SIG_BUDGET = 32 * 1024;

    private static final int CHUNK = 64 * 1024;
    // covers the longest reach of one window past its trigger byte (a signature run + WINDOW)
    private static final int LOOKAHEAD = 12 * 1024;
    private static final int LOOKBACK = ALIGN_BACK + WINDOW + 16;

    private static final VarHandle LONGS =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final long ONES = 0x0101010101010101L;
    private static final long HIGHS = 0x8080808080808080L;
    /** First bytes {@link #sigEnd} can match. */
    private static final boolean[] SIG_START = new boolean[256];

    private static final boolean[] BASE64 = new boolean[256];

    static {
        for (int c = 0; c < 256; c++) {
            BASE64[c] = isBase64((byte) c);
        }
        SIG_START[0x1B] = true;
        SIG_START[0x0E] = true;
        SIG_START['~'] = true;
        SIG_START['+'] = true;
    }

    private EvidenceProbe() {
    }

    /**
     * Reads the probe from {@code tis}; the stream position is unchanged on return.
     * {@code contentTarget} and {@code rawCap} size the prefix as in {@link AdaptiveProbe}.
     */
    public static byte[] read(TikaInputStream tis, int contentTarget, int rawCap)
            throws IOException {
        return scan(tis, contentTarget, rawCap,
                tis.hasReliableLength() ? FILE_SCAN_CAP : STREAM_SCAN_CAP).probe;
    }

    static Result scan(TikaInputStream tis, int contentTarget, int rawCap, long scanCap)
            throws IOException {
        byte[] prefix = AdaptiveProbe.read(tis, contentTarget, rawCap);
        int pl = prefix.length;
        Result r = new Result(prefix, pl);
        if (pl == 0 || hasBomOrNul(prefix)) {
            return r;
        }
        Tally st = new Tally();
        st.add(prefix, 0, pl);
        if (st.decisive() || hasIso2022Escape(prefix)) {
            return r;
        }
        if (pl >= scanCap || (tis.hasReliableLength() && tis.getLength() <= pl)) {
            return r;
        }
        st.sigs = countSigs(prefix, pl);

        Scanner sc = new Scanner(tis, prefix, (int) scanCap);
        tis.mark((int) scanCap);
        try {
            IOUtils.skip(tis, pl);
            boolean stopped = sc.extract(st);
            r.read = sc.pos;
            if (sc.windows == 0) {
                return r;
            }
            boolean utf8Clean = st.utf8 > 0 && st.utf8Bad == 0;
            if (!stopped && st.high < ACCEPT_HIGH && !utf8Clean
                    && !(st.high == 0 && st.sigs > 0)) {
                return r;
            }
            byte[] ev = sc.out.toByteArray();
            byte[] probe = Arrays.copyOf(prefix, pl + ev.length);
            System.arraycopy(ev, 0, probe, pl, ev.length);
            r.probe = probe;
            return r;
        } finally {
            tis.reset();
        }
    }

    static final class Result {
        byte[] probe;
        /** End offset of the scan (bytes from the mark). */
        long read;

        Result(byte[] probe, long read) {
            this.probe = probe;
            this.read = read;
        }
    }

    private static boolean hasBomOrNul(byte[] p) {
        if (BOMDetector.bomLength(p) > 0) {
            return true;
        }
        for (byte b : p) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasIso2022Escape(byte[] p) {
        for (int i = 0; i + 1 < p.length; i++) {
            if (p[i] == 0x1B && (p[i + 1] == '$' || p[i + 1] == '(')) {
                return true;
            }
        }
        return false;
    }

    private static int countSigs(byte[] p, int hi) {
        int n = 0;
        for (int i = 0; i < hi; i++) {
            if (!SIG_START[p[i] & 0xFF]) {
                continue;
            }
            int e = sigEnd(p, 0, i, hi);
            if (e > 0) {
                n++;
                i = e - 1;
            } else if (e < -1) {
                i = -e - 3;
            }
        }
        return n;
    }

    /**
     * End (exclusive, absolute) of a 7-bit signature starting at absolute offset {@code i}
     * of {@code b} (whose index 0 is offset {@code base}), bounded by {@code hi}; -1 if none,
     * or {@code -(j + 2)} for a failed UTF-7 candidate whose base64 run ends at {@code j}, so
     * the caller can skip the run instead of rescanning it from each inner '+'.
     */
    static int sigEnd(byte[] b, int base, int i, int hi) {
        int c = b[i - base] & 0xFF;
        if (c == 0x1B && i + 1 < hi && "$()*+.NO".indexOf(b[i + 1 - base]) >= 0) {
            for (int j = i + 2; j + 1 < hi && j < i + SIG_MAX; j++) {
                if ((b[j - base] == 0x1B && b[j + 1 - base] == '(') || b[j - base] == 0x0F) {
                    return Math.min(hi, j + 3);
                }
            }
            return Math.min(hi, i + SIG_MAX);
        }
        if (c == 0x0E) {
            for (int j = i + 1; j < hi && j < i + SIG_MAX; j++) {
                if (b[j - base] == 0x0F) {
                    return j + 1;
                }
            }
            return Math.min(hi, i + SIG_MAX);
        }
        if (c == '~' && i + 1 < hi && b[i + 1 - base] == '{') {
            for (int j = i + 2; j + 1 < hi && j < i + SIG_MAX; j++) {
                if (b[j - base] == '~' && b[j + 1 - base] == '}') {
                    return j + 2;
                }
            }
            return Math.min(hi, i + SIG_MAX);
        }
        if (c == '+') {
            int j = i + 1;
            while (j < hi && j < i + MAX_WINDOW && isBase64(b[j - base])) {
                j++;
            }
            if (j - i - 1 >= 2 && j < hi && b[j - base] == '-') {
                return j + 1;
            }
            return j > i + 1 ? -(j + 2) : -1;
        }
        return -1;
    }

    private static boolean isBase64(byte x) {
        return (x >= 'A' && x <= 'Z') || (x >= 'a' && x <= 'z') || (x >= '0' && x <= '9')
                || x == '+' || x == '/';
    }

    private static boolean isHigh(byte x) {
        return (x & 0xFF) >= 0x80;
    }

    /** High-byte and UTF-8 sequence counts. */
    static final class Tally {
        int high;
        int utf8;
        int utf8Bad;
        int sigs;

        boolean decisive() {
            return (utf8 >= STOP_UTF8 && utf8Bad == 0) || high >= STOP_HIGH;
        }

        void add(byte[] b, int s, int e) {
            int i = s;
            while (i < e) {
                int x = b[i] & 0xFF;
                if (x < 0x80) {
                    i++;
                    continue;
                }
                int len = x >= 0xC2 && x <= 0xDF ? 2 : x >= 0xE0 && x <= 0xEF ? 3
                        : x >= 0xF0 && x <= 0xF4 ? 4 : 0;
                boolean ok = len > 0 && i + len <= e;
                for (int j = 1; ok && j < len; j++) {
                    ok = (b[i + j] & 0xC0) == 0x80;
                }
                if (ok && len == 3) {
                    int y = b[i + 1] & 0xFF;
                    ok = !((x == 0xE0 && y < 0xA0) || (x == 0xED && y > 0x9F));
                }
                if (ok && len == 4) {
                    int y = b[i + 1] & 0xFF;
                    ok = !((x == 0xF0 && y < 0x90) || (x == 0xF4 && y > 0x8F));
                }
                if (ok) {
                    utf8++;
                    high += len;
                    i += len;
                } else {
                    utf8Bad++;
                    high++;
                    i++;
                }
            }
        }
    }

    /** Sliding buffer over the stream past the prefix; offsets are absolute from the mark. */
    private static final class Scanner {
        private final TikaInputStream tis;
        private final int cap;
        private final int lo;
        private byte[] buf;
        /** Absolute offset of buf[0]. */
        private int base;
        /** Absolute end of the valid bytes in buf. */
        private int end;
        private boolean eof;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int windows;
        int pos;
        private int sigBytes;

        Scanner(TikaInputStream tis, byte[] prefix, int cap) {
            this.tis = tis;
            this.cap = cap;
            this.lo = prefix.length;
            // the first window may reach back across the prefix end, which can split a character
            int keep = Math.min(prefix.length, LOOKBACK);
            this.buf = new byte[Math.max(CHUNK + LOOKAHEAD + LOOKBACK, keep)];
            System.arraycopy(prefix, prefix.length - keep, buf, 0, keep);
            this.base = prefix.length - keep;
            this.end = prefix.length;
        }

        private byte at(int i) {
            return buf[i - base];
        }

        /** Ensures bytes up to {@code upTo} are buffered unless EOF or the cap comes first. */
        private void fill(int upTo, int keepFrom) throws IOException {
            if (eof || end >= upTo) {
                return;
            }
            int shift = keepFrom - base;
            if (shift > 0) {
                System.arraycopy(buf, shift, buf, 0, end - keepFrom);
                base = keepFrom;
            }
            while (!eof && end - base < buf.length) {
                int want = Math.min(buf.length - (end - base), cap - end);
                if (want <= 0) {
                    eof = true;
                    break;
                }
                int n = IOUtils.read(tis, buf, end - base, want);
                end += n;
                if (n < want) {
                    eof = true;
                }
            }
        }

        /** Windows over [lo, cap); true when the tally became decisive. */
        boolean extract(Tally st) throws IOException {
            int prevEnd = lo;
            int i = lo;
            int lastEnd = -1;
            while (true) {
                int sigEnd = -1;
                while (true) {
                    if (!eof && i + LOOKAHEAD >= end) {
                        fill(i + LOOKAHEAD + 1, Math.max(base, i - LOOKBACK));
                    }
                    if (i >= end) {
                        pos = end;
                        return false;
                    }
                    int lim = eof ? end : end - LOOKAHEAD;
                    i = skipPlain(i, lim, sigBytes < SIG_BUDGET);
                    if (i >= lim) {
                        continue;
                    }
                    if (isHigh(at(i))) {
                        break;
                    }
                    if (sigBytes >= SIG_BUDGET) {
                        i++;
                        continue;
                    }
                    sigEnd = sigEnd(buf, base, i, end);
                    if (sigEnd > 0) {
                        break;
                    }
                    i = sigEnd < -1 ? -sigEnd - 2 : i + 1;
                }
                int start = Math.max(prevEnd, i - WINDOW);
                while (start > prevEnd && isHigh(at(start - 1))) {
                    start--;
                }
                int floorBase = windows == 0 ? Math.max(Math.max(0, lo - ALIGN_BACK), base)
                        : prevEnd;
                int s = start;
                int floor = Math.max(floorBase, start - ALIGN_BACK);
                while (s > floor && !afterTwoAscii(s)) {
                    s--;
                }
                if ((s == prevEnd && windows > 0) || afterTwoAscii(s)) {
                    start = s;
                }
                int wEnd;
                if (sigEnd > 0) {
                    wEnd = Math.min(end, sigEnd + WINDOW);
                    st.sigs++;
                } else {
                    int last = i;
                    int k = i + 1;
                    wEnd = last + WINDOW + 1;
                    while (k < end && k < wEnd && k - start < MAX_WINDOW) {
                        if (isHigh(at(k))) {
                            last = k;
                            wEnd = last + WINDOW + 1;
                        }
                        k++;
                    }
                    wEnd = Math.min(wEnd, end);
                }
                while (wEnd < end && wEnd - start < MAX_WINDOW + 1024
                        && (isHigh(at(wEnd - 1)) || isHigh(at(wEnd - 2)))) {
                    wEnd++;
                }
                // a separator inside a 7-bit run, or between a lead byte and its trail, corrupts decoding
                if (windows > 0 && start <= lastEnd) {
                    out.write(buf, lastEnd - base, wEnd - lastEnd);
                } else {
                    out.write('\n');
                    out.write(buf, start - base, wEnd - start);
                    windows++;
                }
                st.add(buf, start - base, wEnd - base);
                if (sigEnd > 0) {
                    sigBytes += wEnd - start;
                }
                lastEnd = wEnd;
                prevEnd = wEnd;
                i = wEnd;
                if (st.decisive()) {
                    pos = wEnd;
                    return true;
                }
            }
        }

        /**
         * First offset in [i, lim) holding a high byte or, if {@code sigs}, a signature start;
         * {@code lim} if none.  Eight bytes at a time: the zero-byte test's lowest flag is
         * always a true match (borrows only create flags above one).
         */
        private int skipPlain(int i, int lim, boolean sigs) {
            byte[] b = buf;
            int k = i - base;
            int kl = lim - base;
            int ke = end - base;
            while (true) {
                int hit = -1;
                while (k + 8 <= kl) {
                    long v = (long) LONGS.get(b, k);
                    long m = v & HIGHS;
                    if (sigs) {
                        m |= zeroByte(v ^ (0x1BL * ONES)) | zeroByte(v ^ (0x0EL * ONES))
                                | zeroByte(v ^ ('~' * ONES)) | zeroByte(v ^ ('+' * ONES));
                    }
                    if (m != 0) {
                        // little-endian: the lowest flagged byte is the first match
                        hit = k + (Long.numberOfTrailingZeros(m) >>> 3);
                        break;
                    }
                    k += 8;
                }
                if (hit < 0) {
                    while (k < kl && b[k] >= 0 && !(sigs && SIG_START[b[k]])) {
                        k++;
                    }
                    hit = k;
                }
                k = hit;
                if (k >= kl || b[k] < 0) {
                    return k + base;
                }
                int next = nextAfterNonSignature(b, k, ke);
                if (next < 0) {
                    return k + base;
                }
                k = next;
            }
        }

        /**
         * Where {@link #sigEnd} would send the scan from {@code k} when no signature starts
         * there (k + 1, or past a failed UTF-7 run); -1 if one may start there.
         */
        private static int nextAfterNonSignature(byte[] b, int k, int ke) {
            int c = b[k];
            if (c == 0x0E) {
                return -1;
            }
            if (k + 1 >= ke) {
                return k + 1;
            }
            byte n = b[k + 1];
            if (c == '~') {
                return n == '{' ? -1 : k + 1;
            }
            if (c == '+') {
                int j = k + 1;
                while (j < ke && j < k + MAX_WINDOW && BASE64[b[j] & 0xFF]) {
                    j++;
                }
                if (j - k - 1 >= 2 && j < ke && b[j] == '-') {
                    return -1;
                }
                return j > k + 1 ? j : k + 1;
            }
            return "$()*+.NO".indexOf(n) < 0 ? k + 1 : -1;
        }

        private static long zeroByte(long x) {
            return (x - ONES) & ~x & HIGHS;
        }

        private boolean afterTwoAscii(int s) {
            return s == 0 || (!isHigh(at(s - 1)) && (s - 2 < 0 || !isHigh(at(s - 2))));
        }
    }
}
