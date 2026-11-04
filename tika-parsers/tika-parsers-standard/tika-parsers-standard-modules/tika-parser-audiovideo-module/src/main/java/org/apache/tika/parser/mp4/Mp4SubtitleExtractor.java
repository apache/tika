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
package org.apache.tika.parser.mp4;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.video.SubtitleExtractor;
import org.apache.tika.parser.video.SubtitleTrack;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Text subtitle tracks of MP4 and QuickTime files: {@code tx3g} (3GPP timed text,
 * {@code mov_text}) and QuickTime {@code text} sample entries in a {@code text}, {@code sbtl}
 * or {@code subt} track. Samples are located through the track's sample tables and read by
 * seeking, so the media data is never read. Fragmented files add samples through
 * {@code moof/traf/trun}. Chapter tracks ({@code tref/chap}) are skipped; edit lists are not
 * applied.
 */
public class Mp4SubtitleExtractor implements SubtitleExtractor {

    static final int MAX_TRACKS = 64;
    static final int MAX_SAMPLES = 1_000_000;
    // a sample table above this is not loaded; a text track's tables are kilobytes
    static final int MAX_TABLE_BYTES = 16 * 1024 * 1024;
    // sample text decoded per file, blank or not: bounds the work; real tracks are megabytes
    static final long MAX_DECODED_BYTES = 256L * 1024 * 1024;
    private static final int MAX_CHILDREN = 100_000;
    // uint16 length prefix plus the longest text it can declare
    private static final int MAX_SAMPLE_READ = 2 + 0xFFFF;

    @Override
    public void extract(TikaInputStream tis, Metadata containerMetadata,
                        XHTMLContentHandler xhtml, ParseContext context)
            throws IOException, SAXException, TikaException {
        try (SeekableByteChannel ch = tis.getSeekableByteChannel()) {
            extract(ch, containerMetadata, xhtml, context);
        }
    }

    private void extract(SeekableByteChannel ch, Metadata containerMetadata,
                         XHTMLContentHandler xhtml, ParseContext context)
            throws IOException, SAXException {
        Box moov = null;
        List<Box> moofs = new ArrayList<>();
        for (Box b : children(ch, 0, ch.size())) {
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
        for (Box b : children(ch, moov.payload, moov.end)) {
            if (b.is("trak")) {
                traks.add(b);
            } else if (b.is("mvex")) {
                readTrex(ch, b, trex);
            }
        }
        Set<Long> chapterTracks = chapterTracks(ch, traks);
        long[] budget = {MAX_DECODED_BYTES};
        Set<String> fragmentWarnings = new LinkedHashSet<>();
        try {
            extractTracks(ch, traks, chapterTracks, moofs, trex, budget, fragmentWarnings,
                    containerMetadata, xhtml, context);
        } finally {
            for (String w : fragmentWarnings) {
                EmbeddedDocumentUtil.recordException(new TikaException(w), containerMetadata,
                        context);
            }
        }
    }

    private void extractTracks(SeekableByteChannel ch, List<Box> traks, Set<Long> chapterTracks,
                               List<Box> moofs, Map<Long, long[]> trex, long[] budget,
                               Set<String> fragmentWarnings, Metadata containerMetadata,
                               XHTMLContentHandler xhtml, ParseContext context)
            throws IOException, SAXException {
        int found = 0;
        for (Box trak : traks) {
            Track track;
            try {
                track = Track.read(ch, trak);
            } catch (IOException | TikaException e) {
                EmbeddedDocumentUtil.recordException(e, containerMetadata, context);
                continue;
            }
            if (track == null || chapterTracks.contains(track.id)) {
                continue;
            }
            if (++found > MAX_TRACKS) {
                EmbeddedDocumentUtil.recordException(new TikaException(
                        "more than " + MAX_TRACKS + " subtitle tracks; skipping the rest"),
                        containerMetadata, context);
                return;
            }
            SubtitleTrack out = new SubtitleTrack("track-" + track.id + ".vtt", track.language);
            track.budget = budget;
            track.fragmentWarnings = fragmentWarnings;
            try {
                track.readCues(ch, out);
                if (!moofs.isEmpty()) {
                    track.readFragments(ch, moofs, trex.get(track.id), out);
                }
            } catch (IOException e) {
                EmbeddedDocumentUtil.recordException(e, containerMetadata, context);
            }
            if (out.isTruncated()) {
                EmbeddedDocumentUtil.recordException(new TikaException("subtitle track "
                        + track.id + " truncated at " + SubtitleTrack.MAX_CHARS + " chars"),
                        containerMetadata, context);
            }
            out.emit(xhtml, context);
            if (budget[0] < 0) {
                EmbeddedDocumentUtil.recordException(new TikaException("subtitle text exceeds "
                        + MAX_DECODED_BYTES + " bytes; the rest skipped"), containerMetadata,
                        context);
                return;
            }
        }
    }

    /** trex defaults by track ID: {duration, size}. */
    private static void readTrex(SeekableByteChannel ch, Box mvex, Map<Long, long[]> trex)
            throws IOException {
        for (Box b : children(ch, mvex.payload, mvex.end)) {
            if (b.is("trex") && trex.size() < MAX_CHILDREN) {
                byte[] p = payload(ch, b, 24);
                if (p.length >= 24) {
                    trex.put(u32(p, 4), new long[]{u32(p, 12), u32(p, 16)});
                }
            }
        }
    }

    // QuickTime chapter titles are text tracks too, named by another track's tref/chap
    private static Set<Long> chapterTracks(SeekableByteChannel ch, List<Box> traks)
            throws IOException {
        Set<Long> ids = new HashSet<>();
        for (Box trak : traks) {
            for (Box tref : children(ch, trak.payload, trak.end)) {
                if (!tref.is("tref")) {
                    continue;
                }
                for (Box chap : children(ch, tref.payload, tref.end)) {
                    if (chap.is("chap")) {
                        byte[] p = payload(ch, chap, 4 * MAX_TRACKS);
                        for (int i = 0; i + 4 <= p.length; i += 4) {
                            ids.add(u32(p, i));
                        }
                    }
                }
            }
        }
        return ids;
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
        // set once the track takes no more cues
        boolean full;
        // text bytes this file may still decode, shared by its tracks; negative once spent
        long[] budget;
        // why fragment data was skipped, shared by the file's tracks so each is said once
        Set<String> fragmentWarnings;
        // running decode time and sample count, carried from stbl into the fragments
        long time;
        int samplesRead;

        /** The track's tables if it is a text subtitle track, else null. */
        static Track read(SeekableByteChannel ch, Box trak) throws IOException, TikaException {
            Track t = new Track();
            Box mdia = null;
            for (Box b : children(ch, trak.payload, trak.end)) {
                if (b.is("tkhd")) {
                    byte[] p = payload(ch, b, 32);
                    t.id = p.length >= 24 ? u32(p, p[0] == 1 ? 20 : 12) : 0;
                } else if (b.is("mdia")) {
                    mdia = b;
                }
            }
            if (mdia == null) {
                return null;
            }
            Box minf = null;
            boolean text = false;
            for (Box b : children(ch, mdia.payload, mdia.end)) {
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
                        t.timescale = u32(p, ts);
                        t.language = language(((p[lang] & 0xFF) << 8) | (p[lang + 1] & 0xFF));
                    }
                } else if (b.is("minf")) {
                    minf = b;
                }
            }
            if (!text || minf == null || t.timescale == 0) {
                return null;
            }
            Box stbl = null;
            for (Box b : children(ch, minf.payload, minf.end)) {
                if (b.is("stbl")) {
                    stbl = b;
                }
            }
            if (stbl == null) {
                return null;
            }
            boolean textEntry = false;
            for (Box b : children(ch, stbl.payload, stbl.end)) {
                switch (b.type) {
                    case "stsd":
                        byte[] p = payload(ch, b, 16);
                        String format = p.length >= 16 ? fourCc(p, 12) : "";
                        textEntry = format.equals("tx3g") || format.equals("text");
                        break;
                    case "stts":
                        t.stts = table(ch, b);
                        break;
                    case "stsc":
                        t.stsc = table(ch, b);
                        break;
                    case "stsz":
                    case "stz2":
                        t.stsz = table(ch, b);
                        t.stz2 = b.is("stz2");
                        break;
                    case "stco":
                    case "co64":
                        t.chunkOffsets = table(ch, b);
                        t.co64 = b.is("co64");
                        break;
                    default:
                        break;
                }
            }
            // a fragmented file may leave the stbl sample tables out
            return textEntry ? t : null;
        }

        void readCues(SeekableByteChannel ch, SubtitleTrack out) throws IOException {
            if (stsz == null || stsc == null || chunkOffsets == null) {
                return;
            }
            SampleSizes sizes = new SampleSizes(stsz, stz2);
            int samples = Math.min(sizes.count, MAX_SAMPLES);
            int chunkWidth = co64 ? 8 : 4;
            long chunks = Math.min(u32(chunkOffsets, 4), (chunkOffsets.length - 8) / chunkWidth);
            long stscEntries = Math.min(u32(stsc, 4), (stsc.length - 8) / 12);
            long sttsEntries = stts == null ? 0 : Math.min(u32(stts, 4), (stts.length - 8) / 8);
            long fileSize = ch.size();

            int sample = 0;
            try {
            // chunks run in order; a table that steps back must not re-walk them
            long nextChunk = 1;
            int sttsIndex = 0;
            long sttsLeft = 0;
            long delta = 0;
            for (long e = 0; e < stscEntries && sample < samples; e++) {
                long firstChunk = u32(stsc, 8 + (int) e * 12);
                long perChunk = u32(stsc, 12 + (int) e * 12);
                long nextFirst = e + 1 < stscEntries ? u32(stsc, 8 + (int) (e + 1) * 12)
                        : chunks + 1;
                for (long c = Math.max(firstChunk, nextChunk);
                        c < nextFirst && c <= chunks && sample < samples; c++) {
                    nextChunk = c + 1;
                    long offset = co64 ? s64(chunkOffsets, 8 + (int) (c - 1) * 8)
                            : u32(chunkOffsets, 8 + (int) (c - 1) * 4);
                    for (long s = 0; s < perChunk && sample < samples; s++) {
                        while (sttsLeft == 0 && sttsIndex < sttsEntries) {
                            sttsLeft = u32(stts, 8 + sttsIndex * 8);
                            delta = u32(stts, 12 + sttsIndex * 8);
                            sttsIndex++;
                        }
                        long duration = sttsLeft > 0 ? delta : 0;
                        if (sttsLeft > 0) {
                            sttsLeft--;
                        }
                        long size = sizes.get(sample);
                        if (!cue(ch, offset, size, duration, fileSize, out)) {
                            full = true;
                            return;
                        }
                        offset += size;
                        sample++;
                    }
                }
            }
            } finally {
                samplesRead = sample;
            }
        }

        /**
         * Emits one sample and advances {@link #time}; false once the track is full or the
         * file's decode budget is spent.
         */
        private boolean cue(SeekableByteChannel ch, long offset, long size, long duration,
                            long fileSize, SubtitleTrack out) throws IOException {
            if (offset >= 0 && size >= 2 && offset <= fileSize - 2) {
                String text = sampleText(ch, offset, Math.min(size, MAX_SAMPLE_READ), budget);
                if (text == null || !out.addCue(millis(time), millis(time + duration), text)) {
                    return false;
                }
            }
            time += duration;
            return true;
        }

        void readFragments(SeekableByteChannel ch, List<Box> moofs, long[] trex,
                           SubtitleTrack out) throws IOException {
            for (Box moof : moofs) {
                // without an explicit base, the first traf's data starts at the moof and
                // each later one where the previous ended (ISO 14496-12 8.8.7.1)
                long dataEnd = moof.start();
                boolean endKnown = true;
                for (Box traf : children(ch, moof.payload(), moof.end())) {
                    if (!traf.is("traf")) {
                        continue;
                    }
                    try {
                        dataEnd = readTraf(ch, traf, moof.start(), dataEnd, endKnown, trex, out);
                        endKnown = true;
                    } catch (Unlocated e) {
                        endKnown = false;
                        if (e.getMessage() != null && fragmentWarnings.size() < MAX_TRACKS) {
                            fragmentWarnings.add(e.getMessage());
                        }
                    }
                    if (full) {
                        return;
                    }
                }
            }
        }

        /**
         * Emits the samples of {@code traf} if it belongs to this track; returns where its
         * data ends. Damage in this track's boxes is an IOException that ends its fragments;
         * damage in another track's only loses where that traf's data ends ({@link Unlocated}).
         */
        private long readTraf(SeekableByteChannel ch, Box traf, long moofStart, long prevEnd,
                              boolean prevKnown, long[] trex, SubtitleTrack out)
                throws IOException {
            List<Box> boxes = children(ch, traf.payload(), traf.end());
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
            long trackId = u32(p, 4);
            boolean mine = trackId == id;
            int need = 8 + ((flags & 0x01) != 0 ? 8 : 0) + ((flags & 0x02) != 0 ? 4 : 0)
                    + ((flags & 0x08) != 0 ? 4 : 0) + ((flags & 0x10) != 0 ? 4 : 0)
                    + ((flags & 0x20) != 0 ? 4 : 0);
            if (p.length < need) {
                throw damage(mine, "MP4 tfhd shorter than its flags declare");
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
                throw new Unlocated(mine ? "MP4 fragment data of track " + id
                        + " follows a damaged or oversized traf; skipped" : null);
            }
            long defDuration = trex == null ? 0 : trex[0];
            long defSize = trex == null ? 0 : trex[1];
            if ((flags & 0x02) != 0) {
                i += 4;
            }
            if ((flags & 0x08) != 0) {
                defDuration = u32(p, i);
                i += 4;
            }
            if ((flags & 0x10) != 0) {
                defSize = u32(p, i);
            }
            if (mine) {
                for (Box b : boxes) {
                    if (b.is("tfdt")) {
                        byte[] t = payload(ch, b, 12);
                        if (t.length < (t.length > 0 && t[0] == 1 ? 12 : 8)) {
                            throw new IOException("MP4 tfdt shorter than its version declares");
                        }
                        time = t[0] == 1 ? s64(t, 4) : u32(t, 4);
                    }
                }
            }
            long next = base;
            for (Box b : boxes) {
                if (b.is("trun")) {
                    next = readTrun(ch, b, base, next, defDuration, defSize, mine ? out : null);
                    if (full) {
                        return next;
                    }
                }
            }
            return next;
        }

        private static IOException damage(boolean mine, String message) {
            return mine ? new IOException(message) : new Unlocated(message);
        }

        /**
         * Emits the run's samples to {@code out} (null: another track's run); returns the end
         * of its data.
         */
        private long readTrun(SeekableByteChannel ch, Box trun, long base, long next,
                              long defDuration, long defSize, SubtitleTrack out)
                throws IOException {
            long boxBytes = trun.end() - trun.payload();
            if (boxBytes > MAX_TABLE_BYTES) {
                // valid in a long video fragment; for another track only its end is lost
                String message = "MP4 trun of " + boxBytes
                        + " bytes exceeds the subtitle table limit of " + MAX_TABLE_BYTES;
                throw out == null ? new Unlocated(null) : new IOException(message);
            }
            byte[] p = payload(ch, trun, MAX_TABLE_BYTES);
            int flags = p.length >= 4 ? flags(p) : 0;
            int i = 8 + ((flags & 0x01) != 0 ? 4 : 0) + ((flags & 0x04) != 0 ? 4 : 0);
            int width = 4 * Integer.bitCount(flags & 0xF00);
            long count = p.length >= 8 ? u32(p, 4) : 0;
            if (p.length < i || (long) (p.length - i) < count * width) {
                throw damage(out != null, "MP4 trun shorter than its sample count declares");
            }
            long offset = (flags & 0x01) != 0 ? base + (int) u32(p, 8) : next;
            if (width == 0 && out == null) {
                // every sample has the default size; nothing to walk
                return defSize > 0 && count > (Long.MAX_VALUE - Math.max(offset, 0)) / defSize
                        ? Long.MAX_VALUE : offset + count * defSize;
            }
            long fileSize = ch.size();
            for (long s = 0; s < count; s++) {
                long duration = defDuration;
                long size = defSize;
                if ((flags & 0x100) != 0) {
                    duration = u32(p, i);
                    i += 4;
                }
                if ((flags & 0x200) != 0) {
                    size = u32(p, i);
                    i += 4;
                }
                i += ((flags & 0x400) != 0 ? 4 : 0) + ((flags & 0x800) != 0 ? 4 : 0);
                if (out != null) {
                    if (samplesRead >= MAX_SAMPLES || !cue(ch, offset, size, duration, fileSize,
                            out)) {
                        full = true;
                        return offset;
                    }
                    samplesRead++;
                }
                offset += size;
            }
            return offset;
        }

        private long millis(long units) {
            return units / timescale * 1000 + (units % timescale) * 1000 / timescale;
        }
    }

    /** Another track's traf whose data end is unknown; a null message is not worth a warning. */
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
            long declared = table.length >= 12 ? u32(table, 8) : 0;
            if (compact) {
                fixed = 0;
                int b = table.length >= 12 ? table[7] & 0xFF : 0;
                bits = b == 4 || b == 8 || b == 16 ? b : 0;
            } else {
                fixed = table.length >= 12 ? u32(table, 4) : 0;
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
                    return u32(table, 12 + i * 4);
                case 16:
                    return ((table[12 + i * 2] & 0xFF) << 8) | (table[13 + i * 2] & 0xFF);
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
     * Only the declared text is read, so an empty sample costs two bytes whatever its size.
     */
    static String sampleText(SeekableByteChannel ch, long offset, long size) throws IOException {
        return sampleText(ch, offset, size, null);
    }

    /** As {@link #sampleText(SeekableByteChannel, long, long)}; null once the budget is spent. */
    static String sampleText(SeekableByteChannel ch, long offset, long size, long[] budget)
            throws IOException {
        byte[] h = read(ch, offset, 2);
        long available = Math.min(size, ch.size() - offset) - 2;
        int len = (int) Math.min(((h[0] & 0xFF) << 8) | (h[1] & 0xFF), available);
        if (len <= 0) {
            return "";
        }
        if (budget != null) {
            // charged before decoding: whitespace that cueText drops still costs its bytes
            if (budget[0] < len) {
                budget[0] = -1;
                return null;
            }
            budget[0] -= len;
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
    static List<Box> children(SeekableByteChannel ch, long start, long end) throws IOException {
        List<Box> boxes = new ArrayList<>();
        long pos = start;
        while (pos + 8 <= end && boxes.size() < MAX_CHILDREN) {
            byte[] h = read(ch, pos, 8);
            long size = u32(h, 0);
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

    private static byte[] table(SeekableByteChannel ch, Box b) throws IOException, TikaException {
        long len = b.end - b.payload;
        if (len > MAX_TABLE_BYTES) {
            throw new TikaException("MP4 '" + b.type + "' box of " + len
                    + " bytes exceeds the subtitle table limit of " + MAX_TABLE_BYTES);
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

    private static long u32(byte[] b, int off) {
        return ((b[off] & 0xFFL) << 24) | ((b[off + 1] & 0xFFL) << 16)
                | ((b[off + 2] & 0xFFL) << 8) | (b[off + 3] & 0xFFL);
    }

    private static long s64(byte[] b, int off) {
        return (u32(b, off) << 32) | u32(b, off + 4);
    }
}
