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
package org.apache.tika.parser.video;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.commons.io.IOUtils;

import org.apache.tika.io.TikaInputStream;

/**
 * Text subtitle tracks of Matroska and WebM files: {@code S_TEXT/UTF8} (SubRip),
 * {@code S_TEXT/ASS}/{@code SSA} and WebVTT ({@code S_TEXT/WEBVTT}, WebM's
 * {@code D_WEBVTT/*}), uncompressed, zlib or header-stripped. One forward pass over the
 * EBML structure, which stops at the first Cluster if there is no text track; a file input
 * seeks past other tracks' blocks, a stream reads through them, so nothing is spooled. Laced
 * and encrypted subtitle blocks, and a Tracks element after the first Cluster, are not handled.
 */
final class MkvSubtitleExtractor {

    static final int MAX_BLOCK_BYTES = 1024 * 1024;
    // Info, Tracks: metadata, kilobytes in practice
    static final long MAX_HEADER_ELEMENT_BYTES = 16L * 1024 * 1024;
    // per-track strings held for the whole pass
    static final int MAX_NAME_BYTES = 4096;
    static final int MAX_COMP_SETTINGS_BYTES = 4096;

    private static final int SEGMENT = 0x18538067;
    private static final int INFO = 0x1549A966;
    private static final int TIMECODE_SCALE = 0x2AD7B1;
    private static final int TRACKS = 0x1654AE6B;
    private static final int TRACK_ENTRY = 0xAE;
    private static final int TRACK_NUMBER = 0xD7;
    private static final int CODEC_ID = 0x86;
    private static final int LANGUAGE = 0x22B59C;
    private static final int LANGUAGE_BCP47 = 0x22B59D;
    private static final int NAME = 0x536E;
    private static final int CONTENT_ENCODINGS = 0x6D80;
    private static final int CONTENT_ENCODING = 0x6240;
    private static final int CONTENT_ENCODING_SCOPE = 0x5032;
    private static final int CONTENT_ENCODING_TYPE = 0x5033;
    private static final int CONTENT_COMPRESSION = 0x5034;
    private static final int CONTENT_COMP_ALGO = 0x4254;
    private static final int CONTENT_COMP_SETTINGS = 0x4255;
    private static final int CLUSTER = 0x1F43B675;
    private static final int TIMECODE = 0xE7;
    private static final int SIMPLE_BLOCK = 0xA3;
    private static final int BLOCK_GROUP = 0xA0;
    private static final int BLOCK = 0xA1;
    private static final int BLOCK_DURATION = 0x9B;

    private static final Pattern SRT_TAG =
            Pattern.compile("</?(?:i|b|u|s|font)(?:\\s[^<>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SSA_OVERRIDE = Pattern.compile("\\{[^{}]*}");
    private static final Pattern VTT_TAG = Pattern.compile("<[^>]*>?");

    private enum Kind { SRT, ASS, VTT, WEBM_VTT }

    private MkvSubtitleExtractor() {
    }

    /** Damage is recorded on the container; what was read before it is kept. */
    static void extract(TikaInputStream tis, SubtitleTracks out) {
        try {
            // a cache that spilled part of a stream has a file but no measured length
            Source src = tis.hasFile() && tis.hasReliableLength()
                    ? new ChannelSource(tis.getSeekableByteChannel()) : new StreamSource(tis);
            try (src) {
                new Walk(src, out).run();
            }
        } catch (IOException e) {
            out.damaged(e);
        }
    }

    private static final class Track {
        long number;
        Kind kind;
        String language = "eng";
        String name;
        int compAlgo = -1;
        byte[] compSettings;
        // why the track cannot be read, or null
        String unsupported;
        SubtitleTrack out;
    }

    private static final class Walk {
        private final Source src;
        private final SubtitleTracks out;
        private final Map<Long, Track> tracks = new HashMap<>();
        private long timecodeScale = 1_000_000;
        private long clusterTime;

        Walk(Source src, SubtitleTracks out) {
            this.src = src;
            this.out = out;
        }

        // Segment and Cluster are entered rather than skipped, so their children come
        // through this one loop; that copes with the unknown sizes live muxers write
        void run() throws IOException {
            while (!out.isSpent()) {
                long id = src.readId();
                if (id < 0) {
                    return;
                }
                long size = src.readSize();
                if (id == CLUSTER) {
                    if (tracks.isEmpty()) {
                        return;
                    }
                    clusterTime = 0;
                    continue;
                }
                if (id == SEGMENT) {
                    continue;
                }
                if (size < 0) {
                    out.warn("Matroska element of unknown size; the rest of the file skipped");
                    return;
                }
                if (id == TIMECODE) {
                    clusterTime = uint(src.readBytes(size, 8));
                } else if (id == INFO) {
                    info(src.readBytes(size, MAX_HEADER_ELEMENT_BYTES));
                } else if (id == TRACKS) {
                    tracks(src.readBytes(size, MAX_HEADER_ELEMENT_BYTES));
                } else if (id == SIMPLE_BLOCK) {
                    Cue cue = readBlock(size);
                    if (cue != null) {
                        addCue(cue, -1);
                    }
                } else if (id == BLOCK_GROUP) {
                    blockGroup(size);
                } else {
                    src.skip(size);
                }
            }
        }

        private void info(byte[] p) {
            new Children(p, 0, p.length).forEach((id, off, len) -> {
                if (id == TIMECODE_SCALE) {
                    long v = uint(p, off, len);
                    timecodeScale = v > 0 ? v : 1_000_000;
                }
            });
        }

        private void tracks(byte[] p) {
            new Children(p, 0, p.length).forEach((id, off, len) -> {
                if (id != TRACK_ENTRY) {
                    return;
                }
                Track t = trackEntry(p, off, len);
                if (t == null || tracks.containsKey(t.number)) {
                    return;
                }
                if (t.unsupported != null) {
                    out.warn("track " + t.number + ": " + t.unsupported + "; skipped");
                    return;
                }
                t.out = out.add("track-" + t.number + ".vtt", t.language);
                if (t.out != null) {
                    t.out.setTitle(t.name);
                    tracks.put(t.number, t);
                }
            });
        }

        private Track trackEntry(byte[] p, int start, int length) {
            Track t = new Track();
            new Children(p, start, length).forEach((id, off, len) -> {
                switch (id) {
                    case TRACK_NUMBER:
                        t.number = uint(p, off, len);
                        break;
                    case CODEC_ID:
                        t.kind = kind(ascii(p, off, len));
                        break;
                    case LANGUAGE:
                        if (t.language.equals("eng")) {
                            t.language = ascii(p, off, len);
                        }
                        break;
                    case LANGUAGE_BCP47:
                        t.language = ascii(p, off, len);
                        break;
                    case NAME:
                        t.name = new String(p, off, Math.min(len, MAX_NAME_BYTES),
                                StandardCharsets.UTF_8);
                        break;
                    case CONTENT_ENCODINGS:
                        contentEncodings(t, p, off, len);
                        break;
                    default:
                        break;
                }
            });
            if (t.kind == null || t.number <= 0) {
                return null;
            }
            if ("und".equals(t.language) || t.language.isBlank()) {
                t.language = null;
            }
            return t;
        }

        private void contentEncodings(Track t, byte[] p, int start, int length) {
            int[] applying = {0};
            new Children(p, start, length).forEach((id, off, len) -> {
                if (id != CONTENT_ENCODING) {
                    return;
                }
                long[] scope = {1};
                long[] type = {0};
                boolean[] compressed = {false};
                int[] algo = {0};
                byte[][] settings = {null};
                new Children(p, off, len).forEach((eid, eoff, elen) -> {
                    if (eid == CONTENT_ENCODING_SCOPE) {
                        scope[0] = uint(p, eoff, elen);
                    } else if (eid == CONTENT_ENCODING_TYPE) {
                        type[0] = uint(p, eoff, elen);
                    } else if (eid == CONTENT_COMPRESSION) {
                        compressed[0] = true;
                        new Children(p, eoff, elen).forEach((cid, coff, clen) -> {
                            if (cid == CONTENT_COMP_ALGO) {
                                algo[0] = (int) Math.min(uint(p, coff, clen), 255);
                            } else if (cid == CONTENT_COMP_SETTINGS) {
                                byte[] s = new byte[Math.min(clen, MAX_COMP_SETTINGS_BYTES + 1)];
                                System.arraycopy(p, coff, s, 0, s.length);
                                settings[0] = s;
                            }
                        });
                    }
                });
                // scope bit 1 is the frames; the others cover codec private data
                if ((scope[0] & 1) == 0) {
                    return;
                }
                if (++applying[0] > 1) {
                    t.unsupported = "chained content encodings";
                } else if (type[0] != 0) {
                    t.unsupported = "encrypted";
                } else if (compressed[0]) {
                    t.compAlgo = algo[0];
                    t.compSettings = settings[0];
                    if (algo[0] != 0 && algo[0] != 3) {
                        t.unsupported = "compression algorithm " + algo[0] + " not supported";
                    } else if (settings[0] != null
                            && settings[0].length > MAX_COMP_SETTINGS_BYTES) {
                        t.unsupported = "stripped header over " + MAX_COMP_SETTINGS_BYTES
                                + " bytes";
                    }
                }
            });
        }

        private void blockGroup(long size) throws IOException {
            long end = src.position() + size;
            Cue cue = null;
            long duration = -1;
            while (src.position() < end) {
                long id = src.readId();
                long childSize = id < 0 ? -1 : src.readSize();
                if (childSize < 0 || src.position() + childSize > end) {
                    throw new EOFException("damaged Matroska BlockGroup");
                }
                if (id == BLOCK) {
                    cue = readBlock(childSize);
                } else if (id == BLOCK_DURATION) {
                    duration = uint(src.readBytes(childSize, 8));
                } else {
                    src.skip(childSize);
                }
            }
            if (cue != null) {
                addCue(cue, duration);
            }
        }

        private record Cue(Track track, long time, String text) {
        }

        /** The block's cue if it belongs to a subtitle track; the block is consumed. */
        private Cue readBlock(long size) throws IOException {
            long start = src.position();
            long trackNumber = src.readSize();
            long remaining = start + size - src.position();
            if (remaining < 0) {
                throw new IOException("Matroska block shorter than its header");
            }
            Track t = trackNumber < 0 ? null : tracks.get(trackNumber);
            long dataSize = remaining - 3;
            if (t == null || dataSize < 0) {
                src.skip(remaining);
                return null;
            }
            if (dataSize > MAX_BLOCK_BYTES || !out.chargeSample()) {
                if (dataSize > MAX_BLOCK_BYTES) {
                    out.warn("track " + t.number + ": block over " + MAX_BLOCK_BYTES
                            + " bytes skipped");
                }
                src.skip(remaining);
                return null;
            }
            byte[] h = src.readBytes(3, 3);
            int lacing = (h[2] >> 1) & 0x03;
            if (lacing != 0) {
                out.warn("track " + t.number + ": laced blocks not supported; skipped");
                src.skip(dataSize);
                return null;
            }
            byte[] data = src.readBytes(dataSize, MAX_BLOCK_BYTES);
            byte[] raw = raw(t, data, out);
            if (raw == null) {
                return null;
            }
            long relative = (short) (((h[0] & 0xFF) << 8) | (h[1] & 0xFF));
            return new Cue(t, clusterTime + relative, text(t, raw));
        }

        private void addCue(Cue cue, long duration) {
            long startMs = millis(cue.time);
            long endMs = duration < 0 ? startMs : millis(cue.time + duration);
            cue.track.out.addCue(startMs, endMs, cue.text);
        }

        private long millis(long ticks) {
            return (long) (ticks * (timecodeScale / 1_000_000.0));
        }
    }

    static Kind kind(String codecId) {
        switch (codecId) {
            case "S_TEXT/UTF8":
            case "S_TEXT/ASCII":
                return Kind.SRT;
            case "S_TEXT/ASS":
            case "S_TEXT/SSA":
            case "S_ASS":
            case "S_SSA":
                return Kind.ASS;
            case "S_TEXT/WEBVTT":
                return Kind.VTT;
            case "D_WEBVTT/SUBTITLES":
            case "D_WEBVTT/CAPTIONS":
            case "D_WEBVTT/DESCRIPTIONS":
                return Kind.WEBM_VTT;
            default:
                return null;
        }
    }

    /**
     * The block's bytes with compression undone, charged to the file's budget before any
     * filter can drop them; null if they cannot be decoded or the budget is spent.
     */
    private static byte[] raw(Track t, byte[] data, SubtitleTracks out) {
        if (t.compAlgo == 0) {
            return inflate(t, data, out);
        }
        if (t.compAlgo == 3 && t.compSettings != null) {
            if (!out.charge((long) t.compSettings.length + data.length)) {
                return null;
            }
            byte[] joined = new byte[t.compSettings.length + data.length];
            System.arraycopy(t.compSettings, 0, joined, 0, t.compSettings.length);
            System.arraycopy(data, 0, joined, t.compSettings.length, data.length);
            return joined;
        }
        return out.charge(data.length) ? data : null;
    }

    private static String text(Track t, byte[] raw) {
        String text = new String(raw, StandardCharsets.UTF_8);
        switch (t.kind) {
            case ASS:
                return assText(text);
            case WEBM_VTT:
                // WebM frames carry "identifier LF settings LF payload"
                int a = text.indexOf('\n');
                int b = a < 0 ? -1 : text.indexOf('\n', a + 1);
                return b < 0 ? "" : vttText(text.substring(b + 1));
            case VTT:
                return vttText(text);
            default:
                return SSA_OVERRIDE.matcher(SRT_TAG.matcher(text).replaceAll(""))
                        .replaceAll("");
        }
    }

    private static String vttText(String text) {
        return VTT_TAG.matcher(text).replaceAll("").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&");
    }

    // block layout: ReadOrder, Layer, Style, Name, MarginL, MarginR, MarginV, Effect, Text
    static String assText(String line) {
        int pos = 0;
        for (int i = 0; i < 8; i++) {
            pos = line.indexOf(',', pos);
            if (pos < 0) {
                return "";
            }
            pos++;
        }
        return SSA_OVERRIDE.matcher(line.substring(pos)).replaceAll("")
                .replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ");
    }

    /** Inflated bytes, charged whether or not they decode; null on damage or a spent budget. */
    private static byte[] inflate(Track t, byte[] data, SubtitleTracks out) {
        Inflater inflater = new Inflater();
        byte[] buf = new byte[Math.min(MAX_BLOCK_BYTES, Math.max(64, data.length * 4))];
        int n = 0;
        long produced;
        boolean damaged = false;
        try {
            inflater.setInput(data);
            while (!inflater.finished() && n < MAX_BLOCK_BYTES) {
                if (n == buf.length) {
                    byte[] bigger = new byte[Math.min(MAX_BLOCK_BYTES, buf.length * 2)];
                    System.arraycopy(buf, 0, bigger, 0, n);
                    buf = bigger;
                }
                int r = inflater.inflate(buf, n, buf.length - n);
                if (r == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                n += r;
            }
        } catch (DataFormatException e) {
            damaged = true;
        } finally {
            // includes what the call that failed had already inflated
            produced = inflater.getBytesWritten();
            inflater.end();
        }
        if (!out.charge(produced)) {
            return null;
        }
        if (damaged) {
            out.warn("track " + t.number + ": damaged zlib block skipped");
            return null;
        }
        if (n == MAX_BLOCK_BYTES) {
            out.warn("track " + t.number + ": block inflating past " + MAX_BLOCK_BYTES
                    + " bytes truncated");
        }
        byte[] result = new byte[n];
        System.arraycopy(buf, 0, result, 0, n);
        return result;
    }

    private static long uint(byte[] p) {
        return uint(p, 0, p.length);
    }

    private static long uint(byte[] p, int off, int len) {
        long v = 0;
        for (int i = 0; i < Math.min(len, 8); i++) {
            v = (v << 8) | (p[off + i] & 0xFF);
        }
        return v;
    }

    private static String ascii(byte[] p, int off, int len) {
        int end = off;
        int max = off + Math.min(len, 64);
        while (end < max && p[end] != 0) {
            end++;
        }
        return new String(p, off, end - off, StandardCharsets.US_ASCII).strip();
    }

    @FunctionalInterface
    private interface ChildVisitor {
        void visit(int id, int off, int len);
    }

    /** Child elements of an in-memory master element; stops at the first damaged header. */
    private record Children(byte[] p, int start, int length) {
        void forEach(ChildVisitor v) {
            int pos = start;
            int end = start + length;
            while (pos < end) {
                int idLen = vintLength(p[pos], 4);
                if (idLen == 0 || pos + idLen >= end) {
                    return;
                }
                int id = 0;
                for (int i = 0; i < idLen; i++) {
                    id = (id << 8) | (p[pos + i] & 0xFF);
                }
                pos += idLen;
                int sizeLen = vintLength(p[pos], 8);
                if (sizeLen == 0 || pos + sizeLen > end) {
                    return;
                }
                long size = p[pos] & (0xFF >> sizeLen);
                boolean allOnes = size == (0xFF >> sizeLen);
                for (int i = 1; i < sizeLen; i++) {
                    allOnes &= (p[pos + i] & 0xFF) == 0xFF;
                    size = (size << 8) | (p[pos + i] & 0xFF);
                }
                pos += sizeLen;
                // an unknown size cannot be stepped over in a sized parent
                if (allOnes || size > end - pos) {
                    return;
                }
                v.visit(id, pos, (int) size);
                pos += (int) size;
            }
        }
    }

    /** Length of an EBML variable-size integer from its first byte, or 0 if over max. */
    static int vintLength(byte first, int max) {
        int b = first & 0xFF;
        if (b == 0) {
            return 0;
        }
        int len = Integer.numberOfLeadingZeros(b) - 23;
        return len <= max ? len : 0;
    }

    private abstract static class Source implements AutoCloseable {
        abstract long position() throws IOException;

        /** -1 at end of input. */
        abstract int readByte() throws IOException;

        abstract void skip(long n) throws IOException;

        @Override
        public void close() throws IOException {
        }

        /** Element ID with its marker bits, or -1 at a clean end of input. */
        long readId() throws IOException {
            int b = readByte();
            if (b < 0) {
                return -1;
            }
            int len = vintLength((byte) b, 4);
            if (len == 0) {
                throw new IOException("invalid EBML element ID");
            }
            long id = b;
            for (int i = 1; i < len; i++) {
                id = (id << 8) | next();
            }
            return id;
        }

        /** Element size, or -1 for "unknown". */
        long readSize() throws IOException {
            int b = next();
            int len = vintLength((byte) b, 8);
            if (len == 0) {
                throw new IOException("invalid EBML size");
            }
            long v = b & (0xFF >> len);
            boolean allOnes = v == (0xFF >> len);
            for (int i = 1; i < len; i++) {
                int n = next();
                allOnes &= n == 0xFF;
                v = (v << 8) | n;
            }
            return allOnes ? -1 : v;
        }

        /** Allocates only what the input can back: the declared size is not trusted. */
        abstract byte[] readBytes(long size, long max) throws IOException;

        static void checkMax(long size, long max) throws IOException {
            if (size < 0 || size > max) {
                throw new IOException("EBML element of " + size + " bytes exceeds " + max);
            }
        }

        private int next() throws IOException {
            int b = readByte();
            if (b < 0) {
                throw new EOFException("Matroska element runs past the end of the input");
            }
            return b;
        }
    }

    private static final class StreamSource extends Source {
        private final InputStream in;
        private long pos;

        StreamSource(InputStream in) {
            this.in = in;
        }

        @Override
        long position() {
            return pos;
        }

        @Override
        int readByte() throws IOException {
            int b = in.read();
            if (b >= 0) {
                pos++;
            }
            return b;
        }

        @Override
        byte[] readBytes(long size, long max) throws IOException {
            checkMax(size, max);
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(size, 8192));
            long copied = IOUtils.copyLarge(in, out, 0, size);
            pos += copied;
            if (copied < size) {
                throw new EOFException("Matroska element runs past the end of the input");
            }
            return out.toByteArray();
        }

        @Override
        void skip(long n) throws IOException {
            if (n < 0) {
                throw new IOException("negative EBML skip");
            }
            // skipFully reads rather than seeking, so a size past the end throws here
            IOUtils.skipFully(in, n);
            pos += n;
        }
    }

    /** Seeks over what it skips; a small buffer serves the header bytes between seeks. */
    private static final class ChannelSource extends Source {
        private final SeekableByteChannel ch;
        private final long size;
        private final byte[] buf = new byte[256];
        private long bufStart;
        private int bufLen;
        private long pos;

        ChannelSource(SeekableByteChannel ch) throws IOException {
            this.ch = ch;
            this.size = ch.size();
        }

        @Override
        long position() {
            return pos;
        }

        @Override
        int readByte() throws IOException {
            if (pos < bufStart || pos >= bufStart + bufLen) {
                fill();
                if (bufLen == 0) {
                    return -1;
                }
            }
            return buf[(int) (pos++ - bufStart)] & 0xFF;
        }

        private void readFully(byte[] b) throws IOException {
            int done = 0;
            if (pos >= bufStart && pos < bufStart + bufLen) {
                done = (int) Math.min(b.length, bufStart + bufLen - pos);
                System.arraycopy(buf, (int) (pos - bufStart), b, 0, done);
                pos += done;
            }
            ByteBuffer out = ByteBuffer.wrap(b, done, b.length - done);
            ch.position(pos);
            while (out.hasRemaining()) {
                int r = ch.read(out);
                if (r < 0) {
                    throw new EOFException("Matroska element runs past the end of the file");
                }
                pos += r;
            }
        }

        @Override
        byte[] readBytes(long n, long max) throws IOException {
            checkMax(n, max);
            if (n > size - pos) {
                throw new EOFException("Matroska element runs past the end of the file");
            }
            byte[] b = new byte[(int) n];
            readFully(b);
            return b;
        }

        @Override
        void skip(long n) throws IOException {
            if (n < 0 || n > size - pos) {
                throw new EOFException("Matroska element runs past the end of the file");
            }
            pos += n;
        }

        private void fill() throws IOException {
            bufStart = pos;
            bufLen = 0;
            ch.position(pos);
            ByteBuffer in = ByteBuffer.wrap(buf);
            int r = ch.read(in);
            bufLen = Math.max(r, 0);
        }

        @Override
        public void close() throws IOException {
            ch.close();
        }
    }
}
