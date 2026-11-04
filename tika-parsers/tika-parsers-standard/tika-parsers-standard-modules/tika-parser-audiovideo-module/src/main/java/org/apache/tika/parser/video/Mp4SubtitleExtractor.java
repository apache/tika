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

import static org.apache.tika.io.EndianUtils.getUIntBE;
import static org.apache.tika.io.EndianUtils.getUShortBE;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.io.TikaInputStream;

/**
 * Text subtitle tracks of MP4 and QuickTime files: {@code tx3g} (3GPP timed text,
 * {@code mov_text}) and QuickTime {@code text} sample entries in a {@code text}, {@code sbtl}
 * or {@code subt} track. Samples are located through the track's sample tables, and through
 * {@code moof/traf/trun} in fragmented files, and read by seeking. Chapter tracks
 * ({@code tref/chap}) are skipped; edit lists are not applied.
 */
final class Mp4SubtitleExtractor {

    // a sample table above this is not loaded; a text track's tables are kilobytes
    static final int MAX_TABLE_BYTES = 16 * 1024 * 1024;
    // samples walked per track, read or not; reads are charged per file
    private static final int MAX_TRACK_SAMPLES = 1_000_000;
    private static final int MAX_CHILDREN = 100_000;
    // uint16 length prefix plus the longest text it can declare
    private static final int MAX_SAMPLE_READ = 2 + 0xFFFF;

    private Mp4SubtitleExtractor() {
    }

    /** Damage is recorded on the container; what was read before it is kept. */
    static void extract(TikaInputStream tis, SubtitleTracks out) {
        try (SeekableByteChannel ch = tis.getSeekableByteChannel()) {
            extract(ch, out);
        } catch (IOException e) {
            out.damaged(e);
        }
    }

    private static void extract(SeekableByteChannel ch, SubtitleTracks out) throws IOException {
        Box moov = null;
        List<Box> moofs = new ArrayList<>();
        for (Box b : children(ch, 0, ch.size(), out)) {
            if (b.is("moov") && moov == null) {
                moov = b;
            } else if (b.is("moof")) {
                moofs.add(b);
            }
        }
        if (moov == null) {
            return;
        }
        List<Box> traks = new ArrayList<>();
        Map<Long, long[]> trex = new HashMap<>();
        for (Box b : children(ch, moov.payload, moov.end, out)) {
            if (b.is("trak")) {
                traks.add(b);
            } else if (b.is("mvex")) {
                readTrex(ch, b, trex, out);
            }
        }
        Set<Long> chapterTracks = chapterTracks(ch, traks, out);
        Map<Long, Track> tracks = new LinkedHashMap<>();
        for (Box trak : traks) {
            Track t = Track.read(ch, trak, out);
            if (t == null || chapterTracks.contains(t.id) || tracks.containsKey(t.id)) {
                continue;
            }
            t.out = out.add("track-" + t.id + ".vtt", t.language);
            if (t.out == null) {
                break;
            }
            tracks.put(t.id, t);
        }
        for (Track t : tracks.values()) {
            t.readCues(ch, out);
            if (out.isSpent()) {
                return;
            }
        }
        if (!tracks.isEmpty()) {
            readFragments(ch, moofs, trex, tracks, out);
        }
    }

    /** trex defaults by track ID: {duration, size}. */
    private static void readTrex(SeekableByteChannel ch, Box mvex, Map<Long, long[]> trex,
                                 SubtitleTracks out) throws IOException {
        for (Box b : children(ch, mvex.payload, mvex.end, out)) {
            if (b.is("trex")) {
                byte[] p = payload(ch, b, 24);
                if (p.length >= 24) {
                    trex.put(getUIntBE(p, 4), new long[]{getUIntBE(p, 12), getUIntBE(p, 16)});
                }
            }
        }
    }

    // QuickTime chapter titles are text tracks too, named by another track's tref/chap
    private static Set<Long> chapterTracks(SeekableByteChannel ch, List<Box> traks,
                                           SubtitleTracks out) throws IOException {
        Set<Long> ids = new HashSet<>();
        for (Box trak : traks) {
            for (Box tref : children(ch, trak.payload, trak.end, out)) {
                if (!tref.is("tref")) {
                    continue;
                }
                for (Box chap : children(ch, tref.payload, tref.end, out)) {
                    if (chap.is("chap")) {
                        byte[] p = payload(ch, chap, 4 * SubtitleTracks.MAX_TRACKS);
                        for (int i = 0; i + 4 <= p.length; i += 4) {
                            ids.add(getUIntBE(p, i));
                        }
                    }
                }
            }
        }
        return ids;
    }

    /**
     * One pass over the fragments, each traf going to its track. Without an explicit base, the
     * first traf's data starts at the moof and each later one where the previous ended
     * (ISO 14496-12 8.8.7.1), so every traf is walked, subtitle track or not.
     */
    private static void readFragments(SeekableByteChannel ch, List<Box> moofs,
                                      Map<Long, long[]> trex, Map<Long, Track> tracks,
                                      SubtitleTracks out) throws IOException {
        for (Box moof : moofs) {
            if (out.isSpent() || tracks.values().stream().allMatch(t -> t.done)) {
                return;
            }
            long dataEnd = moof.start;
            boolean endKnown = true;
            for (Box traf : children(ch, moof.payload, moof.end, out)) {
                if (!traf.is("traf")) {
                    continue;
                }
                try {
                    dataEnd = readTraf(ch, traf, moof.start, dataEnd, endKnown, trex, tracks, out);
                    endKnown = true;
                } catch (Unlocated e) {
                    endKnown = false;
                    if (e.getMessage() != null) {
                        out.warn(e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Emits the samples of {@code traf} if it belongs to an open subtitle track; returns where
     * its data ends. Damage ends that track's fragments; in any traf it loses where the traf's
     * data ends ({@link Unlocated}).
     */
    private static long readTraf(SeekableByteChannel ch, Box traf, long moofStart, long prevEnd,
                                 boolean prevKnown, Map<Long, long[]> trex,
                                 Map<Long, Track> tracks, SubtitleTracks out)
            throws IOException {
        List<Box> boxes = children(ch, traf.payload, traf.end, out);
        Box tfhd = null;
        for (Box b : boxes) {
            if (b.is("tfhd")) {
                tfhd = b;
                break;
            }
        }
        byte[] p = tfhd == null ? new byte[0] : payload(ch, tfhd, 40);
        if (p.length < 8) {
            throw new Unlocated("MP4 traf without a usable tfhd");
        }
        int flags = flags(p);
        long trackId = getUIntBE(p, 4);
        Track t = tracks.get(trackId);
        if (t != null && t.done) {
            t = null;
        }
        int need = 8 + ((flags & 0x01) != 0 ? 8 : 0) + ((flags & 0x02) != 0 ? 4 : 0)
                + ((flags & 0x08) != 0 ? 4 : 0) + ((flags & 0x10) != 0 ? 4 : 0)
                + ((flags & 0x20) != 0 ? 4 : 0);
        if (p.length < need) {
            throw lost(t, "MP4 tfhd shorter than its flags declare");
        }
        long base;
        int i = 8;
        if ((flags & 0x01) != 0) {
            base = s64(p, i);
            i += 8;
        } else if ((flags & 0x20000) != 0) {
            base = moofStart;
        } else if (prevKnown) {
            base = prevEnd;
        } else {
            throw new Unlocated(t == null ? null : "MP4 fragment data of track " + t.id
                    + " follows a damaged or oversized traf; skipped");
        }
        long[] defaults = trex.get(trackId);
        long defDuration = defaults == null ? 0 : defaults[0];
        long defSize = defaults == null ? 0 : defaults[1];
        if ((flags & 0x02) != 0) {
            i += 4;
        }
        if ((flags & 0x08) != 0) {
            defDuration = getUIntBE(p, i);
            i += 4;
        }
        if ((flags & 0x10) != 0) {
            defSize = getUIntBE(p, i);
        }
        if (t != null) {
            for (Box b : boxes) {
                if (b.is("tfdt")) {
                    byte[] d = payload(ch, b, 12);
                    if (d.length < (d.length > 0 && d[0] == 1 ? 12 : 8)) {
                        throw lost(t, "MP4 tfdt shorter than its version declares");
                    }
                    t.time = d[0] == 1 ? s64(d, 4) : getUIntBE(d, 4);
                }
            }
        }
        long next = base;
        for (Box b : boxes) {
            if (b.is("trun")) {
                next = readTrun(ch, b, base, next, defDuration, defSize, t, out);
            }
        }
        return next;
    }

    /** Ends {@code t}'s fragments (if it is a subtitle track) and loses the traf's end. */
    private static Unlocated lost(Track t, String message) {
        if (t != null) {
            t.done = true;
        }
        return new Unlocated(message);
    }

    /** Emits the run's samples to {@code t} (null: not a subtitle track); returns its data end. */
    private static long readTrun(SeekableByteChannel ch, Box trun, long base, long next,
                                 long defDuration, long defSize, Track t, SubtitleTracks out)
            throws IOException {
        long boxBytes = trun.end - trun.payload;
        if (boxBytes > MAX_TABLE_BYTES) {
            // valid in a long video fragment, where only its end is lost
            throw t == null ? new Unlocated(null) : lost(t, "MP4 trun of " + boxBytes
                    + " bytes exceeds the subtitle table limit of " + MAX_TABLE_BYTES);
        }
        byte[] p = payload(ch, trun, MAX_TABLE_BYTES);
        int flags = p.length >= 4 ? flags(p) : 0;
        int i = 8 + ((flags & 0x01) != 0 ? 4 : 0) + ((flags & 0x04) != 0 ? 4 : 0);
        int width = 4 * Integer.bitCount(flags & 0xF00);
        long count = p.length >= 8 ? getUIntBE(p, 4) : 0;
        if (p.length < i || (long) (p.length - i) < count * width) {
            throw lost(t, "MP4 trun shorter than its sample count declares");
        }
        long offset = (flags & 0x01) != 0 ? base + (int) getUIntBE(p, 8) : next;
        if (width == 0 && t == null) {
            // every sample has the default size; nothing to walk
            return defSize > 0 && count > (Long.MAX_VALUE - Math.max(offset, 0)) / defSize
                    ? Long.MAX_VALUE : offset + count * defSize;
        }
        long fileSize = ch.size();
        for (long s = 0; s < count; s++) {
            long duration = defDuration;
            long size = defSize;
            if ((flags & 0x100) != 0) {
                duration = getUIntBE(p, i);
                i += 4;
            }
            if ((flags & 0x200) != 0) {
                size = getUIntBE(p, i);
                i += 4;
            }
            i += ((flags & 0x400) != 0 ? 4 : 0) + ((flags & 0x800) != 0 ? 4 : 0);
            if (t != null && (++t.walked > MAX_TRACK_SAMPLES
                    || !t.cue(ch, offset, size, duration, fileSize, out))) {
                t.done = true;
                return offset;
            }
            offset += size;
        }
        return offset;
    }

    private static final class Track {
        long id;
        long timescale;
        String language;
        byte[] stts;
        byte[] stsc;
        byte[] stsz;
        boolean stz2;
        byte[] chunkOffsets;
        boolean co64;
        SubtitleTrack out;
        // set once the track takes no more cues
        boolean done;
        // running decode time and samples walked, carried from stbl into the fragments
        long time;
        int walked;

        /** The track's tables if it is a text subtitle track, else null. */
        static Track read(SeekableByteChannel ch, Box trak, SubtitleTracks out)
                throws IOException {
            Track t = new Track();
            Box mdia = null;
            for (Box b : children(ch, trak.payload, trak.end, out)) {
                if (b.is("tkhd")) {
                    byte[] p = payload(ch, b, 32);
                    t.id = p.length >= 24 ? getUIntBE(p, p[0] == 1 ? 20 : 12) : 0;
                } else if (b.is("mdia")) {
                    mdia = b;
                }
            }
            if (mdia == null) {
                return null;
            }
            Box minf = null;
            boolean text = false;
            for (Box b : children(ch, mdia.payload, mdia.end, out)) {
                if (b.is("hdlr")) {
                    byte[] p = payload(ch, b, 12);
                    String type = p.length >= 12 ? fourCc(p, 8) : "";
                    text = type.equals("text") || type.equals("sbtl") || type.equals("subt");
                } else if (b.is("mdhd")) {
                    byte[] p = payload(ch, b, 34);
                    boolean v1 = p.length > 0 && p[0] == 1;
                    int ts = v1 ? 20 : 12;
                    int lang = v1 ? 32 : 20;
                    if (p.length >= lang + 2) {
                        t.timescale = getUIntBE(p, ts);
                        t.language = language(getUShortBE(p, lang));
                    }
                } else if (b.is("minf")) {
                    minf = b;
                }
            }
            if (!text || minf == null || t.timescale == 0) {
                return null;
            }
            Box stbl = null;
            for (Box b : children(ch, minf.payload, minf.end, out)) {
                if (b.is("stbl")) {
                    stbl = b;
                }
            }
            if (stbl == null) {
                return null;
            }
            String format = null;
            for (Box b : children(ch, stbl.payload, stbl.end, out)) {
                switch (b.type) {
                    case "stsd":
                        byte[] p = payload(ch, b, 16);
                        format = p.length >= 16 ? fourCc(p, 12) : "";
                        break;
                    case "stts":
                        t.stts = table(ch, b, out);
                        break;
                    case "stsc":
                        t.stsc = table(ch, b, out);
                        break;
                    case "stsz":
                    case "stz2":
                        t.stsz = table(ch, b, out);
                        t.stz2 = b.is("stz2");
                        break;
                    case "stco":
                    case "co64":
                        t.chunkOffsets = table(ch, b, out);
                        t.co64 = b.is("co64");
                        break;
                    default:
                        break;
                }
            }
            if (format == null || !(format.equals("tx3g") || format.equals("text"))) {
                if (format != null && !format.isEmpty()) {
                    out.warn("track " + t.id + ": sample entry '" + printable(format)
                            + "' not supported");
                }
                return null;
            }
            // a fragmented file may leave the stbl sample tables out
            return t;
        }

        void readCues(SeekableByteChannel ch, SubtitleTracks budget) throws IOException {
            if (stsz == null || stsc == null || chunkOffsets == null) {
                return;
            }
            SampleSizes sizes = new SampleSizes(stsz, stz2);
            int samples = Math.min(sizes.count, MAX_TRACK_SAMPLES);
            int chunkWidth = co64 ? 8 : 4;
            long chunks = Math.min(getUIntBE(chunkOffsets, 4),
                    (chunkOffsets.length - 8) / chunkWidth);
            long stscEntries = Math.min(getUIntBE(stsc, 4), (stsc.length - 8) / 12);
            long sttsEntries = stts == null ? 0
                    : Math.min(getUIntBE(stts, 4), (stts.length - 8) / 8);
            long fileSize = ch.size();
            // chunks run in order; a table that steps back must not re-walk them
            long nextChunk = 1;
            int sttsIndex = 0;
            long sttsLeft = 0;
            long delta = 0;
            for (long e = 0; e < stscEntries && walked < samples; e++) {
                long firstChunk = getUIntBE(stsc, 8 + (int) e * 12);
                long perChunk = getUIntBE(stsc, 12 + (int) e * 12);
                long nextFirst = e + 1 < stscEntries ? getUIntBE(stsc, 8 + (int) (e + 1) * 12)
                        : chunks + 1;
                for (long c = Math.max(firstChunk, nextChunk);
                        c < nextFirst && c <= chunks && walked < samples; c++) {
                    nextChunk = c + 1;
                    long offset = co64 ? s64(chunkOffsets, 8 + (int) (c - 1) * 8)
                            : getUIntBE(chunkOffsets, 8 + (int) (c - 1) * 4);
                    for (long s = 0; s < perChunk && walked < samples; s++) {
                        while (sttsLeft == 0 && sttsIndex < sttsEntries) {
                            sttsLeft = getUIntBE(stts, 8 + sttsIndex * 8);
                            delta = getUIntBE(stts, 12 + sttsIndex * 8);
                            sttsIndex++;
                        }
                        long duration = sttsLeft > 0 ? delta : 0;
                        if (sttsLeft > 0) {
                            sttsLeft--;
                        }
                        long size = sizes.get(walked);
                        walked++;
                        if (!cue(ch, offset, size, duration, fileSize, budget)) {
                            done = true;
                            return;
                        }
                        offset += size;
                    }
                }
            }
        }

        /** Emits one sample and advances {@link #time}; false once the file's budget is spent. */
        boolean cue(SeekableByteChannel ch, long offset, long size, long duration,
                    long fileSize, SubtitleTracks budget) throws IOException {
            if (offset >= 0 && size >= 2 && offset <= fileSize - 2) {
                if (!budget.chargeSample()) {
                    return false;
                }
                String text = sampleText(ch, offset, Math.min(size, MAX_SAMPLE_READ), budget);
                if (text == null || !out.addCue(millis(time), millis(time + duration), text)) {
                    return false;
                }
            }
            time += duration;
            return true;
        }

        private long millis(long units) {
            return units / timescale * 1000 + (units % timescale) * 1000 / timescale;
        }
    }

    /** A traf whose data end is unknown; a null message is not worth a warning. */
    private static final class Unlocated extends IOException {
        private static final long serialVersionUID = 1L;

        Unlocated(String message) {
            super(message);
        }
    }

    private static int flags(byte[] p) {
        return ((p[1] & 0xFF) << 16) | ((p[2] & 0xFF) << 8) | (p[3] & 0xFF);
    }

    /** Sizes from {@code stsz} (fixed or 32-bit) or {@code stz2} (4, 8 or 16-bit). */
    private static final class SampleSizes {
        final int count;
        private final byte[] table;
        private final long fixed;
        private final int bits;

        SampleSizes(byte[] table, boolean compact) {
            this.table = table;
            long declared = table.length >= 12 ? getUIntBE(table, 8) : 0;
            if (compact) {
                fixed = 0;
                int b = table.length >= 12 ? table[7] & 0xFF : 0;
                bits = b == 4 || b == 8 || b == 16 ? b : 0;
            } else {
                fixed = table.length >= 12 ? getUIntBE(table, 4) : 0;
                bits = fixed != 0 ? 0 : 32;
            }
            long available = fixed != 0 ? declared
                    : bits == 0 ? 0 : (table.length - 12L) * 8 / bits;
            count = (int) Math.min(Math.min(declared, available), Integer.MAX_VALUE);
        }

        long get(int i) {
            if (fixed != 0) {
                return fixed;
            }
            switch (bits) {
                case 32:
                    return getUIntBE(table, 12 + i * 4);
                case 16:
                    return getUShortBE(table, 12 + i * 2);
                case 8:
                    return table[12 + i] & 0xFF;
                default:
                    int v = table[12 + i / 2] & 0xFF;
                    return i % 2 == 0 ? v >>> 4 : v & 0x0F;
            }
        }
    }

    /**
     * uint16 length, then UTF-8 text or UTF-16 with a BOM; style boxes follow and are ignored.
     * Only the declared text is read and charged; null once the budget is spent.
     */
    static String sampleText(SeekableByteChannel ch, long offset, long size,
                             SubtitleTracks budget) throws IOException {
        byte[] h = read(ch, offset, 2);
        long available = Math.min(size, ch.size() - offset) - 2;
        int len = (int) Math.min(getUShortBE(h, 0), available);
        if (len <= 0) {
            return "";
        }
        // charged before decoding: whitespace that cueText drops still costs its bytes
        if (!budget.charge(len)) {
            return null;
        }
        byte[] b = read(ch, offset + 2, len);
        if (len >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return new String(b, 2, len - 2, StandardCharsets.UTF_16BE);
        }
        if (len >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return new String(b, 2, len - 2, StandardCharsets.UTF_16LE);
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    // QuickTime Macintosh language codes 0-94 and 128-150 (Script Manager langXXX), as ISO 639-2/T
    private static final String[] MAC_LANGUAGES = {
        "eng", "fra", "deu", "ita", "nld", "swe", "spa", "dan", "por", "nor",
        "heb", "jpn", "ara", "fin", "ell", "isl", "mlt", "tur", "hrv", "zho",
        "urd", "hin", "tha", "kor", "lit", "pol", "hun", "est", "lav", "smi",
        "fao", "fas", "rus", "zho", "nld", "gle", "sqi", "ron", "ces", "slk",
        "slv", "yid", "srp", "mkd", "bul", "ukr", "bel", "uzb", "kaz", "aze",
        "aze", "hye", "kat", "ron", "kir", "tgk", "tuk", "mon", "mon", "pus",
        "kur", "kas", "snd", "bod", "nep", "san", "mar", "ben", "asm", "guj",
        "pan", "ori", "mal", "kan", "tam", "tel", "sin", "mya", "khm", "lao",
        "vie", "ind", "tgl", "msa", "msa", "amh", "tir", "orm", "som", "swa",
        "kin", "run", "nya", "mlg", "epo", null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, "cym", "eus",
        "cat", "lat", "que", "grn", "aym", "tat", "uig", "dzo", "jav", "sun",
        "glg", "afr", "bre", "iku", "gla", "glv", "gle", "ton", "grc", "kal",
        "aze"
    };

    // ISO 639-2/T packed as three 5-bit letters; below 0x400 is a QuickTime Mac code
    static String language(int packed) {
        if (packed < 0x400) {
            return packed < MAC_LANGUAGES.length ? MAC_LANGUAGES[packed] : null;
        }
        if (packed == 0x7FFF) {
            return null;
        }
        char[] c = new char[3];
        for (int i = 0; i < 3; i++) {
            int v = (packed >> (10 - 5 * i)) & 0x1F;
            if (v < 1 || v > 26) {
                return null;
            }
            c[i] = (char) (v + 0x60);
        }
        String lang = new String(c);
        return lang.equals("und") ? null : lang;
    }

    private record Box(String type, long start, long payload, long end) {
        boolean is(String t) {
            return type.equals(t);
        }
    }

    /** Child boxes of [start, end); a box running past {@code end} is clipped to it. */
    private static List<Box> children(SeekableByteChannel ch, long start, long end,
                                      SubtitleTracks out) throws IOException {
        List<Box> boxes = new ArrayList<>();
        long pos = start;
        while (pos + 8 <= end) {
            if (boxes.size() >= MAX_CHILDREN) {
                out.warn("more than " + MAX_CHILDREN + " MP4 boxes in one box; the rest skipped");
                break;
            }
            byte[] h = read(ch, pos, 8);
            long size = getUIntBE(h, 0);
            String type = fourCc(h, 4);
            long header = 8;
            if (size == 1) {
                if (pos + 16 > end) {
                    break;
                }
                size = s64(read(ch, pos + 8, 8), 0);
                header = 16;
            } else if (size == 0) {
                size = end - pos;
            }
            if (size < header) {
                break;
            }
            long boxEnd = size > end - pos ? end : pos + size;
            boxes.add(new Box(type, pos, pos + header, boxEnd));
            pos = boxEnd;
        }
        return boxes;
    }

    private static byte[] payload(SeekableByteChannel ch, Box b, int max) throws IOException {
        return read(ch, b.payload, (int) Math.min(max, b.end - b.payload));
    }

    /** The box's payload, or null if it is too short or over {@link #MAX_TABLE_BYTES}. */
    private static byte[] table(SeekableByteChannel ch, Box b, SubtitleTracks out)
            throws IOException {
        long len = b.end - b.payload;
        if (len > MAX_TABLE_BYTES) {
            out.warn("MP4 '" + b.type + "' box of " + len
                    + " bytes exceeds the subtitle table limit of " + MAX_TABLE_BYTES);
            return null;
        }
        byte[] p = read(ch, b.payload, (int) len);
        return p.length >= 8 ? p : null;
    }

    private static byte[] read(SeekableByteChannel ch, long pos, int len) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(Math.max(0, len));
        ch.position(pos);
        while (buf.hasRemaining()) {
            if (ch.read(buf) < 0) {
                throw new EOFException("MP4 box runs past the end of the file");
            }
        }
        return buf.array();
    }

    private static String fourCc(byte[] b, int off) {
        return new String(b, off, 4, StandardCharsets.ISO_8859_1);
    }

    private static String printable(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c >= 0x20 && c < 0x7F ? c : '?');
        }
        return sb.toString();
    }

    private static long s64(byte[] b, int off) {
        return (getUIntBE(b, off) << 32) | getUIntBE(b, off + 4);
    }
}
