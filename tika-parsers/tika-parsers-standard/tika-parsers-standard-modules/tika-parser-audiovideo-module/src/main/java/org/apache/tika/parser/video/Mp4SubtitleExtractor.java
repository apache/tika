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
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
 * <p>
 * CEA-608 closed captions come from QuickTime {@code c608} tracks and, when asked, from the
 * SEI messages of H.264 and HEVC video tracks, of which only the NAL units before a sample's
 * first slice are read. A probed video track is read for its first {@value #PROBE_HEAD}
 * samples, then {@value #PROBE_WINDOW} samples every {@value #PROBE_INTERVAL_SECONDS} seconds,
 * and in full only once a caption character turns up. Caption pairs are reordered by
 * presentation time before decoding.
 */
final class Mp4SubtitleExtractor {

    /** Which captions to read: {@code c608} tracks, and H.264/HEVC SEI as {@code scan} says. */
    record Captions(boolean c608, VideoCaptionScan scan) {
        Captions {
            scan = scan == null ? VideoCaptionScan.NONE : scan;
        }

        boolean video() {
            return scan != VideoCaptionScan.NONE;
        }

        boolean probe() {
            return scan == VideoCaptionScan.PROBE;
        }
    }

    // absent from runtimes built without jdk.charsets
    private static final Charset MAC_ROMAN =
            Charset.isSupported("x-MacRoman") ? Charset.forName("x-MacRoman") : null;

    // a sample table above this is not loaded; a text track's tables are kilobytes
    static final int MAX_TABLE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_CHILDREN = 100_000;
    // uint16 length prefix plus the longest text it can declare
    private static final int MAX_SAMPLE_READ = 2 + 0xFFFF;
    static final int PROBE_HEAD = 300;
    static final int PROBE_INTERVAL_SECONDS = 5;
    static final int PROBE_WINDOW = 8;
    // c608 and video tracks read for captions; each channel they yield still takes a track
    static final int MAX_CAPTION_SOURCES = 16;
    // NAL units looked at before a sample's first slice; SEI follows parameter sets and other
    // SEI (HDR keyframes carry several)
    private static final int MAX_NALS_PER_SAMPLE = 32;
    // NAL headers (and small SEI) are parsed from windows of this many bytes, not read one by
    // one; each read is charged at least MIN_READ_CHARGE so tiny reads cannot multiply
    private static final int NAL_WINDOW = 128;
    private static final int MIN_READ_CHARGE = 64;
    private static final int MAX_SEI_BYTES = 8 * 1024;

    private Mp4SubtitleExtractor() {
    }

    /** Damage is recorded on the container; what was read before it is kept. */
    static void extract(TikaInputStream tis, SubtitleTracks out, Captions captions) {
        try (SeekableByteChannel ch = tis.getSeekableByteChannel()) {
            extract(ch, out, captions);
        } catch (IOException e) {
            out.damaged(e);
        }
    }

    static void extract(SeekableByteChannel ch, SubtitleTracks out, Captions captions)
            throws IOException {
        // moov alone first: most files have no text track, and a long fragmented one has
        // a moof every few seconds
        Box moov = null;
        long pos = 0;
        long size = ch.size();
        for (int n = 0; moov == null; n++) {
            Box b = next(ch, pos, size);
            if (b == null) {
                return;
            }
            if (n >= MAX_CHILDREN) {
                out.warn("more than " + MAX_CHILDREN + " MP4 boxes before moov; skipped");
                return;
            }
            if (b.is("moov")) {
                moov = b;
            }
            pos = b.end;
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
        try {
            readTracks(ch, size, traks, trex, chapterTracks, tracks, captions, out);
        } finally {
            // caption pairs are held until here: damage later in the file must not lose them
            for (Track t : tracks.values()) {
                if (t.pairs != null && t.pairs.found) {
                    t.pairs.decode(out, "track-" + t.id + "-",
                            t.kind == Kind.C608 ? t.language : null);
                }
            }
        }
    }

    private static void readTracks(SeekableByteChannel ch, long size, List<Box> traks,
                                   Map<Long, long[]> trex, Set<Long> chapterTracks,
                                   Map<Long, Track> tracks, Captions captions,
                                   SubtitleTracks out) throws IOException {
        int captionSources = 0;
        for (Box trak : traks) {
            boolean room = captionSources < MAX_CAPTION_SOURCES;
            Track t = Track.read(ch, trak, chapterTracks, tracks.keySet(), captions, room, out);
            if (t == null) {
                continue;
            }
            if (t.kind == Kind.TEXT) {
                t.out = out.add("track-" + t.id + ".vtt", t.language, null);
                if (t.out == null) {
                    continue;
                }
            } else if (!room) {
                out.warn("more than " + MAX_CAPTION_SOURCES + " caption sources; the rest skipped");
                continue;
            } else {
                captionSources++;
                t.startCaptions(trak, captions.probe(), out);
            }
            tracks.put(t.id, t);
            t.readCues(ch, out);
            if (out.isSpent()) {
                return;
            }
        }
        if (tracks.isEmpty()) {
            return;
        }
        List<Box> moofs = new ArrayList<>();
        for (Box b : children(ch, 0, size, out)) {
            if (b.is("moof")) {
                moofs.add(b);
            }
        }
        readFragments(ch, moofs, trex, tracks, out);
        rescan(ch, moofs, trex, chapterTracks, tracks, captions, out);
    }

    /**
     * Reads in full, in one more pass over the fragments, the video tracks whose probe found
     * captions after samples it had skipped.
     */
    private static void rescan(SeekableByteChannel ch, List<Box> moofs, Map<Long, long[]> trex,
                               Set<Long> chapterTracks, Map<Long, Track> tracks,
                               Captions captions, SubtitleTracks out) throws IOException {
        Map<Long, Track> again = new LinkedHashMap<>();
        for (Track t : tracks.values()) {
            if (!t.rescan || out.captionsSpent()) {
                continue;
            }
            Track r = Track.read(ch, t.trak, chapterTracks, Set.of(), captions, true, out);
            if (r == null) {
                continue;
            }
            // the reread collects these pairs again; the probe's stay held in case it fails
            out.refundCaptionPairs(t.pairs.count);
            r.startCaptions(t.trak, false, out);
            again.put(r.id, r);
            r.readCues(ch, out);
        }
        if (!again.isEmpty()) {
            readFragments(ch, moofs, trex, again, out);
            for (Track r : again.values()) {
                if (r.pairs.found) {
                    tracks.put(r.id, r);
                }
            }
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
     * Emits the samples of {@code traf} if it belongs to an open track; returns where
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

    /** Ends {@code t}'s fragments (if not null) and loses the traf's end. */
    private static Unlocated lost(Track t, String message) {
        if (t != null) {
            t.done = true;
        }
        return new Unlocated(message);
    }

    /** Emits the run's samples to {@code t} (null: not read); returns its data end. */
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
        long fileSize = ch.size();
        for (long s = 0; s < count; s++) {
            if (width == 0 && t == null) {
                // every sample left has the default size; nothing to walk
                return runEnd(offset, count - s, defSize);
            }
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
            if ((flags & 0x400) != 0) {
                i += 4;
            }
            long cto = 0;
            if ((flags & 0x800) != 0) {
                // signed in version 1, and written signed into version 0 too
                cto = (int) getUIntBE(p, i);
                i += 4;
            }
            if (t != null && !t.cue(ch, offset, size, duration, cto, fileSize, out)) {
                // the run's end still places the next traf
                t.done = true;
                t = null;
            }
            offset += size;
        }
        return offset;
    }

    private static long runEnd(long offset, long count, long defSize) {
        return defSize > 0 && count > (Long.MAX_VALUE - Math.max(offset, 0)) / defSize
                ? Long.MAX_VALUE : offset + count * defSize;
    }

    private enum Kind { TEXT, AVC, HEVC, C608 }

    private static final class Track {
        long id;
        Kind kind;
        // caption sources: NAL unit length prefix (from avcC/hvcC), and composition offsets
        int nalLength;
        byte[] ctts;
        CaptionPairs pairs;
        Box trak;
        boolean probing;
        // the probe found captions after skipping samples: the track is read again in full
        boolean rescan;
        // caption samples walked, read or skipped by the probe
        long sampleIndex;
        // probe: samples left in the current window, and the decode time of the next one
        int probeLeft;
        long nextProbe;
        long timescale;
        String language;
        byte[] stts;
        byte[] stsc;
        byte[] stsz;
        boolean stz2;
        byte[] chunkOffsets;
        boolean co64;
        SubtitleTrack out;
        // QuickTime text: BOM-less samples may be in a Mac script, not UTF-8
        boolean macText;
        // set once the track takes no more cues
        boolean done;
        // running decode time, carried from stbl into the fragments
        long time;

        /**
         * The track's tables if it is a text subtitle track or an asked-for caption source not
         * among {@code skip} or {@code seen}, else null. A caption source without
         * {@code room} comes back without its tables.
         */
        static Track read(SeekableByteChannel ch, Box trak, Set<Long> skip, Set<Long> seen,
                          Captions captions, boolean room, SubtitleTracks out)
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
            if (mdia == null || skip.contains(t.id) || seen.contains(t.id)) {
                return null;
            }
            Box minf = null;
            String handler = "";
            for (Box b : children(ch, mdia.payload, mdia.end, out)) {
                if (b.is("hdlr")) {
                    byte[] p = payload(ch, b, 12);
                    handler = p.length >= 12 ? fourCc(p, 8) : "";
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
            boolean text = handler.equals("text") || handler.equals("sbtl")
                    || handler.equals("subt");
            boolean c608 = captions.c608() && handler.equals("clcp");
            boolean video = captions.video() && handler.equals("vide");
            if (!(text || c608 || video) || minf == null || t.timescale == 0) {
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
            List<Box> tables = children(ch, stbl.payload, stbl.end, out);
            for (Box b : tables) {
                if (b.is("stsd")) {
                    t.kind = text ? textKind(ch, b, t) : sampleKind(ch, b, c608, t, out);
                }
            }
            // other formats (wvtt, stpp, av01, ...) are valid tracks Tika does not decode
            if (t.kind == null) {
                return null;
            }
            if (t.kind != Kind.TEXT && !room) {
                return t;
            }
            // a video's tables can be large and valid: its first samples are still read
            boolean prefix = t.kind != Kind.TEXT;
            for (Box b : tables) {
                switch (b.type) {
                    case "stts":
                        t.stts = table(ch, b, prefix, out);
                        break;
                    case "stsc":
                        t.stsc = table(ch, b, prefix, out);
                        break;
                    case "stsz":
                    case "stz2":
                        t.stsz = table(ch, b, prefix, out);
                        t.stz2 = b.is("stz2");
                        break;
                    case "stco":
                    case "co64":
                        t.chunkOffsets = table(ch, b, prefix, out);
                        t.co64 = b.is("co64");
                        break;
                    case "ctts":
                        t.ctts = prefix ? table(ch, b, true, out) : null;
                        break;
                    default:
                        break;
                }
            }
            // a fragmented file may leave the stbl sample tables out
            return t;
        }

        private static Kind textKind(SeekableByteChannel ch, Box stsd, Track t)
                throws IOException {
            byte[] p = payload(ch, stsd, 16);
            String format = p.length >= 16 ? fourCc(p, 12) : "";
            t.macText = format.equals("text");
            return format.equals("tx3g") || format.equals("text") ? Kind.TEXT : null;
        }

        // the first sample entry: c608, or H.264/HEVC (Dolby Vision too) with its NAL length
        private static Kind sampleKind(SeekableByteChannel ch, Box stsd, boolean c608, Track t,
                                       SubtitleTracks out) throws IOException {
            Box entry = next(ch, stsd.payload + 8, stsd.end);
            if (entry == null) {
                return null;
            }
            if (c608) {
                return entry.is("c608") ? Kind.C608 : null;
            }
            boolean avc = entry.is("avc1") || entry.is("avc3") || entry.is("dva1")
                    || entry.is("dvav");
            boolean hevc = entry.is("hvc1") || entry.is("hev1") || entry.is("dvh1")
                    || entry.is("dvhe");
            if (!avc && !hevc) {
                return null;
            }
            // VisualSampleEntry: 8 bytes of SampleEntry fields and 70 of visual ones, then boxes
            for (Box b : children(ch, entry.payload + 78, entry.end, out)) {
                if (b.is(avc ? "avcC" : "hvcC")) {
                    int at = avc ? 4 : 21;
                    byte[] p = payload(ch, b, at + 1);
                    if (p.length <= at) {
                        return null;
                    }
                    t.nalLength = (p[at] & 0x03) + 1;
                    // 3 is not a legal length size
                    return t.nalLength == 3 ? null : avc ? Kind.AVC : Kind.HEVC;
                }
            }
            return null;
        }

        void startCaptions(Box trak, boolean probe, SubtitleTracks budget) {
            this.trak = trak;
            pairs = new CaptionPairs(budget);
            probing = probe && kind != Kind.C608;
        }

        /** The stbl samples; the tables are dropped after, as the fragments need only time. */
        void readCues(SeekableByteChannel ch, SubtitleTracks budget) throws IOException {
            try {
                readTables(ch, budget);
            } finally {
                stts = null;
                stsc = null;
                stsz = null;
                chunkOffsets = null;
                ctts = null;
            }
        }

        private void readTables(SeekableByteChannel ch, SubtitleTracks budget)
                throws IOException {
            if (stsz == null || stsc == null || chunkOffsets == null) {
                return;
            }
            SampleSizes sizes = new SampleSizes(stsz, stz2);
            int samples = sizes.count;
            int walked = 0;
            int chunkWidth = co64 ? 8 : 4;
            long chunks = Math.min(getUIntBE(chunkOffsets, 4),
                    (chunkOffsets.length - 8) / chunkWidth);
            long stscEntries = Math.min(getUIntBE(stsc, 4), (stsc.length - 8) / 12);
            Runs durations = new Runs(stts);
            Runs offsets = new Runs(ctts);
            long fileSize = ch.size();
            // chunks run in order; a table that steps back must not re-walk them
            long nextChunk = 1;
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
                        long duration = durations.next();
                        // signed in version 1, and written signed into version 0 too
                        long cto = (int) offsets.next();
                        long size = sizes.get(walked);
                        walked++;
                        if (!cue(ch, offset, size, duration, cto, fileSize, budget)) {
                            done = true;
                            return;
                        }
                        offset += size;
                    }
                }
            }
        }

        /**
         * Emits one sample, or collects its captions at {@code time + cto}, and advances
         * {@link #time}; false once the track takes no more.
         */
        boolean cue(SeekableByteChannel ch, long offset, long size, long duration, long cto,
                    long fileSize, SubtitleTracks budget) throws IOException {
            if (kind != Kind.TEXT) {
                boolean more = captionSample(ch, offset, size, time + cto, duration, fileSize,
                        budget);
                time += duration;
                return more;
            }
            // empty samples are charged too: they cost a walk, not a read
            if (!budget.chargeSample()) {
                return false;
            }
            if (offset >= 0 && size >= 2 && offset <= fileSize - 2) {
                String text = sampleText(ch, offset, Math.min(size, MAX_SAMPLE_READ), budget,
                        macText);
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

        /** Collects one sample's caption pairs; false to stop the walk. */
        private boolean captionSample(SeekableByteChannel ch, long offset, long size, long pts,
                                      long duration, long fileSize, SubtitleTracks budget)
                throws IOException {
            // charged before any skip: the walk is bounded, not only the reads
            if (!budget.chargeCaptionSample()) {
                return false;
            }
            long index = sampleIndex++;
            if (probing && index >= PROBE_HEAD) {
                if (probeLeft > 0) {
                    probeLeft--;
                } else if (time >= nextProbe) {
                    probeLeft = PROBE_WINDOW - 1;
                    nextProbe = time + PROBE_INTERVAL_SECONDS * timescale;
                } else {
                    return true;
                }
            }
            if (offset < 0 || size <= 0 || offset >= fileSize) {
                return true;
            }
            long end = Math.min(offset + size, fileSize);
            // may be negative: kept so that such frames still sort by presentation time
            long ms = millis(pts);
            pairs.shownUntil(millis(pts + duration));
            boolean more = kind == Kind.C608 ? c608(ch, offset, end, ms, budget)
                    : nals(ch, offset, end, ms, budget);
            if (probing && pairs.found) {
                probing = false;
                if (index >= PROBE_HEAD) {
                    // samples before this one were skipped
                    rescan = true;
                    return false;
                }
            }
            return more;
        }

        // length-prefixed NAL units up to the first slice; SEI bodies go to CaptionData
        private boolean nals(SeekableByteChannel ch, long offset, long end, long ms,
                             SubtitleTracks budget) throws IOException {
            int header = kind == Kind.AVC ? 1 : 2;
            int need = nalLength + header;
            byte[] window = new byte[0];
            long windowAt = offset;
            long pos = offset;
            for (int n = 0; n < MAX_NALS_PER_SAMPLE && pos + need <= end; n++) {
                if (pos + need > windowAt + window.length) {
                    window = readCharged(ch, pos, (int) Math.min(NAL_WINDOW, end - pos), budget);
                    if (window == null) {
                        return false;
                    }
                    windowAt = pos;
                }
                int o = (int) (pos - windowAt);
                long len = 0;
                for (int i = 0; i < nalLength; i++) {
                    len = (len << 8) | (window[o + i] & 0xFF);
                }
                long start = pos + nalLength;
                if (len < header || len > end - start) {
                    return true;
                }
                int h = window[o + nalLength] & 0xFF;
                int type = kind == Kind.AVC ? h & 0x1F : (h >> 1) & 0x3F;
                // A/53 captions ride in prefix SEI, before the first slice
                boolean slice = kind == Kind.AVC ? type >= 1 && type <= 5 : type <= 31;
                if (slice) {
                    return true;
                }
                if (kind == Kind.AVC ? type == 6 : type == 39) {
                    int take = (int) Math.min(len, MAX_SEI_BYTES);
                    int from = (int) (start - windowAt);
                    byte[] nal = start + take <= windowAt + window.length
                            ? Arrays.copyOfRange(window, from, from + take)
                            : readCharged(ch, start, take, budget);
                    if (nal == null) {
                        return false;
                    }
                    CaptionData.fromSei(nal, header, (field, b1, b2) -> pairs.add(ms, field, b1, b2));
                    if (pairs.overflow) {
                        return false;
                    }
                }
                pos = start + len;
            }
            return true;
        }

        // QuickTime c608 sample: cdat (field 1) and cdt2 (field 2) atoms of byte pairs
        private boolean c608(SeekableByteChannel ch, long offset, long end, long ms,
                             SubtitleTracks budget) throws IOException {
            byte[] b = readCharged(ch, offset, (int) Math.min(end - offset, MAX_SEI_BYTES),
                    budget);
            if (b == null) {
                return false;
            }
            int pos = 0;
            while (pos + 8 <= b.length) {
                long size = getUIntBE(b, pos);
                if (size < 8 || size > b.length - pos) {
                    break;
                }
                String type = fourCc(b, pos + 4);
                int field = type.equals("cdat") ? 0 : type.equals("cdt2") ? 1 : -1;
                for (int i = pos + 8; field >= 0 && i + 2 <= pos + size; i += 2) {
                    pairs.add(ms, field, b[i] & 0xFF, b[i + 1] & 0xFF);
                }
                pos += (int) size;
            }
            return !pairs.overflow;
        }
    }

    /** A run-length table ({@code stts}, {@code ctts}): sample count, then value, per entry. */
    private static final class Runs {
        private final byte[] table;
        private final long entries;
        private int index;
        private long left;
        private long value;

        Runs(byte[] table) {
            this.table = table;
            entries = table == null ? 0 : Math.min(getUIntBE(table, 4), (table.length - 8) / 8);
        }

        /** The next sample's value as uint32; 0 past the table's end. */
        long next() {
            while (left == 0 && index < entries) {
                left = getUIntBE(table, 8 + index * 8);
                value = getUIntBE(table, 12 + index * 8);
                index++;
            }
            if (left == 0) {
                return 0;
            }
            left--;
            return value;
        }
    }

    /** Caption pairs with presentation times, decoded in time order (stable within a frame). */
    private static final class CaptionPairs {
        private static final int INDEX_BITS = SubtitleTracks.CAPTION_PAIR_BITS;
        // keys hold the time in the remaining bits, offset so that negative times sort first
        private static final long TIME_BIAS = 1L << (62 - INDEX_BITS);
        private final SubtitleTracks budget;
        private long[] keys = new long[256];
        private int[] data = new int[256];
        int count;
        boolean overflow;
        // a caption character was seen
        boolean found;
        private final Cea608Decoder.Filter filter = new Cea608Decoder.Filter();
        // when the last frame read leaves the screen
        long lastMillis;

        CaptionPairs(SubtitleTracks budget) {
            this.budget = budget;
        }

        void shownUntil(long ms) {
            lastMillis = Math.max(lastMillis, ms);
        }

        // padding is kept: it separates a repeated control code from a new one, and that must
        // be judged in presentation order
        void add(long ms, int field, int b1, int b2) {
            shownUntil(ms);
            if (overflow || count >= 1 << INDEX_BITS || !budget.chargeCaptionPair()) {
                overflow = true;
                return;
            }
            if (count == keys.length) {
                keys = Arrays.copyOf(keys, count * 2);
                data = Arrays.copyOf(data, count * 2);
            }
            long t = Math.max(-TIME_BIAS, Math.min(ms, TIME_BIAS - 1)) + TIME_BIAS;
            keys[count] = (t << INDEX_BITS) | count;
            data[count] = (field << 16) | (b1 << 8) | b2;
            count++;
            found |= Cea608Decoder.Filter.isCharacter(filter.filter(field, b1, b2));
        }

        void decode(SubtitleTracks owner, String prefix, String language) {
            Arrays.sort(keys, 0, count);
            Cea608Decoder decoder = new Cea608Decoder(owner, prefix, language);
            for (int i = 0; i < count; i++) {
                long key = keys[i];
                int v = data[(int) (key & ((1 << INDEX_BITS) - 1))];
                long ms = Math.max(0, (key >>> INDEX_BITS) - TIME_BIAS);
                decoder.pair(ms, v >> 16, (v >> 8) & 0xFF, v & 0xFF);
            }
            decoder.finish(lastMillis);
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
     * QuickTime text ({@code macText}) that is not valid UTF-8 is read as MacRoman, the Mac
     * script of Western languages. Only the declared text is read and charged; null once the
     * budget is spent.
     */
    static String sampleText(SeekableByteChannel ch, long offset, long size,
                             SubtitleTracks budget, boolean macText) throws IOException {
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
        if (macText && MAC_ROMAN != null) {
            try {
                return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(b)).toString();
            } catch (CharacterCodingException e) {
                return new String(b, MAC_ROMAN);
            }
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
        for (Box b = next(ch, pos, end); b != null; b = next(ch, pos, end)) {
            if (boxes.size() >= MAX_CHILDREN) {
                out.warn("more than " + MAX_CHILDREN + " MP4 boxes in one box; the rest skipped");
                break;
            }
            boxes.add(b);
            pos = b.end;
        }
        return boxes;
    }

    /** The box at {@code pos}, clipped to {@code end}; null at the end or on a bad header. */
    private static Box next(SeekableByteChannel ch, long pos, long end) throws IOException {
        if (pos + 8 > end) {
            return null;
        }
        byte[] h = read(ch, pos, 8);
        long size = getUIntBE(h, 0);
        String type = fourCc(h, 4);
        long header = 8;
        if (size == 1) {
            if (pos + 16 > end) {
                return null;
            }
            size = s64(read(ch, pos + 8, 8), 0);
            header = 16;
        } else if (size == 0) {
            size = end - pos;
        }
        if (size < header) {
            return null;
        }
        return new Box(type, pos, pos + header, size > end - pos ? end : pos + size);
    }

    private static byte[] payload(SeekableByteChannel ch, Box b, int max) throws IOException {
        return read(ch, b.payload, (int) Math.min(max, b.end - b.payload));
    }

    /**
     * The box's payload, or null if it is too short or over {@link #MAX_TABLE_BYTES}; with
     * {@code prefix}, its first {@link #MAX_TABLE_BYTES} instead.
     */
    private static byte[] table(SeekableByteChannel ch, Box b, boolean prefix,
                                SubtitleTracks out) throws IOException {
        long len = b.end - b.payload;
        if (prefix && len > MAX_TABLE_BYTES) {
            out.warn("MP4 '" + b.type + "' box of " + len + " bytes; captions read from its first "
                    + MAX_TABLE_BYTES);
            len = MAX_TABLE_BYTES;
        } else if (len > MAX_TABLE_BYTES) {
            out.warn("MP4 '" + b.type + "' box of " + len
                    + " bytes exceeds the subtitle table limit of " + MAX_TABLE_BYTES);
            return null;
        }
        byte[] p = read(ch, b.payload, (int) len);
        return p.length >= 8 ? p : null;
    }

    /** Reads for captions, charging at least {@link #MIN_READ_CHARGE}; null once spent. */
    private static byte[] readCharged(SeekableByteChannel ch, long pos, int len,
                                      SubtitleTracks budget) throws IOException {
        return budget.chargeCaptionBytes(Math.max(len, MIN_READ_CHARGE)) ? read(ch, pos, len)
                : null;
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

    private static long s64(byte[] b, int off) {
        return (getUIntBE(b, off) << 32) | getUIntBE(b, off + 4);
    }
}
