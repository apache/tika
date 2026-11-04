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
import java.util.Arrays;
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
import org.apache.tika.parser.video.CaptionData;
import org.apache.tika.parser.video.Cea608Decoder;
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
 * <p>
 * CEA-608 closed captions come from QuickTime {@code c608} tracks and from the SEI messages of
 * H.264 and HEVC video tracks. For video only the NAL headers before a sample's first slice
 * are read, plus SEI bodies. A probe pass reads the first {@value #PROBE_HEAD} samples, then
 * {@value #PROBE_WINDOW} samples every {@value #PROBE_INTERVAL_SECONDS} seconds of the track;
 * the whole track is read only if the probe finds caption data. Pairs are reordered by
 * presentation time before decoding.
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
    static final int PROBE_HEAD = 300;
    static final int PROBE_INTERVAL_SECONDS = 5;
    static final int PROBE_WINDOW = 8;
    // NAL units looked at before a sample's first slice; SEI precedes it, after parameter
    // sets and other SEI (HDR keyframes carry several)
    private static final int MAX_NALS_PER_SAMPLE = 32;
    // NAL headers (and small SEI) are parsed from windows of this many bytes, not read one by
    // one; each read is charged at least MIN_READ_CHARGE so tiny reads cannot multiply
    private static final int NAL_WINDOW = 128;
    private static final int MIN_READ_CHARGE = 64;
    // video and c608 tracks read for captions, separate from MAX_TRACKS text tracks
    static final int MAX_CAPTION_TRACKS = 16;
    // caption cue text per file, across tracks and channels: re-displays cannot multiply it
    static final long MAX_CAPTION_CHARS = 16L * 1024 * 1024;
    private static final int MAX_SEI_BYTES = 8 * 1024;
    // caption byte pairs held per track for reordering, padding included
    static final int MAX_CAPTION_PAIRS = 1 << 21;

    private final boolean subtitles;
    private final boolean captions;
    private final boolean fullCaptionScan;

    public Mp4SubtitleExtractor() {
        this(true, true, false);
    }

    /**
     * @param subtitles       whether text subtitle tracks are extracted
     * @param captions        whether CEA-608 captions are extracted
     * @param fullCaptionScan whether every video sample is read without a probe pass first
     */
    public Mp4SubtitleExtractor(boolean subtitles, boolean captions, boolean fullCaptionScan) {
        this.subtitles = subtitles;
        this.captions = captions;
        this.fullCaptionScan = fullCaptionScan;
    }

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
        long[] captionBudget = {MAX_CAPTION_CHARS};
        Set<String> fragmentWarnings = new LinkedHashSet<>();
        try {
            extractTracks(ch, traks, chapterTracks, moofs, trex, budget, captionBudget,
                    fragmentWarnings, containerMetadata, xhtml, context);
        } finally {
            for (String w : fragmentWarnings) {
                EmbeddedDocumentUtil.recordException(new TikaException(w), containerMetadata,
                        context);
            }
        }
    }

    private void extractTracks(SeekableByteChannel ch, List<Box> traks, Set<Long> chapterTracks,
                               List<Box> moofs, Map<Long, long[]> trex, long[] budget,
                               long[] captionBudget, Set<String> fragmentWarnings,
                               Metadata containerMetadata, XHTMLContentHandler xhtml,
                               ParseContext context)
            throws IOException, SAXException {
        int found = 0;
        int captionTracks = 0;
        for (Box trak : traks) {
            Track track;
            try {
                track = Track.read(ch, trak, subtitles, captions);
            } catch (IOException | TikaException e) {
                EmbeddedDocumentUtil.recordException(e, containerMetadata, context);
                continue;
            }
            if (track == null || chapterTracks.contains(track.id)) {
                continue;
            }
            if (track.kind == Kind.TEXT ? ++found > MAX_TRACKS
                    : ++captionTracks > MAX_CAPTION_TRACKS) {
                // one warning per kind; later tracks of the other kind still run
                if (track.kind == Kind.TEXT ? found == MAX_TRACKS + 1
                        : captionTracks == MAX_CAPTION_TRACKS + 1) {
                    EmbeddedDocumentUtil.recordException(new TikaException(track.kind == Kind.TEXT
                            ? "more than " + MAX_TRACKS + " subtitle tracks; skipping the rest"
                            : "more than " + MAX_CAPTION_TRACKS
                                    + " video tracks with captions; skipping the rest"),
                            containerMetadata, context);
                }
                continue;
            }
            SubtitleTrack out = new SubtitleTrack("track-" + track.id + ".vtt", track.language);
            track.budget = budget;
            track.fragmentWarnings = fragmentWarnings;
            List<SubtitleTrack> results;
            if (track.kind == Kind.TEXT) {
                walk(ch, track, moofs, trex, out, containerMetadata, context);
                results = List.of(out);
            } else {
                results = captionTracks(ch, track, moofs, trex, out, captionBudget,
                        containerMetadata, context);
            }
            for (SubtitleTrack result : results) {
                if (result.isTruncated()) {
                    EmbeddedDocumentUtil.recordException(new TikaException(
                            result.getName() + " truncated at " + SubtitleTrack.MAX_CHARS
                                    + " chars"), containerMetadata, context);
                }
                result.emit(xhtml, context);
            }
            if (budget[0] < 0) {
                EmbeddedDocumentUtil.recordException(new TikaException("subtitle text exceeds "
                        + MAX_DECODED_BYTES + " bytes; the rest skipped"), containerMetadata,
                        context);
                return;
            }
            if (captionBudget[0] == -1) {
                captionBudget[0] = -2;
                EmbeddedDocumentUtil.recordException(new TikaException("caption text exceeds "
                        + MAX_CAPTION_CHARS + " chars; the rest skipped"), containerMetadata,
                        context);
            }
        }
    }

    private static void walk(SeekableByteChannel ch, Track track, List<Box> moofs,
                             Map<Long, long[]> trex, SubtitleTrack out,
                             Metadata containerMetadata, ParseContext context) {
        try {
            track.readCues(ch, out);
            if (!moofs.isEmpty() && !track.full) {
                track.readFragments(ch, moofs, trex.get(track.id), out);
            }
        } catch (IOException e) {
            EmbeddedDocumentUtil.recordException(e, containerMetadata, context);
        }
    }

    /** Captions of a video or c608 track: a probe pass, then the full pass if it found any. */
    private List<SubtitleTrack> captionTracks(SeekableByteChannel ch, Track track,
                                              List<Box> moofs, Map<Long, long[]> trex,
                                              SubtitleTrack out, long[] captionBudget,
                                              Metadata containerMetadata,
                                              ParseContext context) {
        if (captionBudget[0] < 0) {
            return List.of();
        }
        track.pairs = new CaptionPairs();
        if (track.kind != Kind.C608 && !fullCaptionScan) {
            track.probing = true;
            walk(ch, track, moofs, trex, out, containerMetadata, context);
            if (!track.pairs.present) {
                return List.of();
            }
            track.reset();
        }
        walk(ch, track, moofs, trex, out, containerMetadata, context);
        if (track.pairs.overflow) {
            EmbeddedDocumentUtil.recordException(new TikaException("caption data of track "
                    + track.id + " exceeds " + MAX_CAPTION_PAIRS + " pairs; the rest skipped"),
                    containerMetadata, context);
        } else if (track.samplesRead >= MAX_SAMPLES) {
            EmbeddedDocumentUtil.recordException(new TikaException("captions of track "
                    + track.id + " read for the first " + MAX_SAMPLES + " samples only"),
                    containerMetadata, context);
        }
        return track.pairs.decode("track-" + track.id + "-", captionBudget);
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

    private enum Kind { TEXT, AVC, HEVC, C608 }

    private static final class Track {
        long id;
        Kind kind;
        // NAL unit length prefix of AVC/HEVC samples, from avcC/hvcC
        int nalLength;
        byte[] ctts;
        CaptionPairs pairs;
        boolean probing;
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
        // caption samples seen in this pass, read or skipped by the probe
        long sampleIndex;
        // probe: samples left in the current window, and the decode time of the next one
        int probeLeft;
        long nextProbe;

        /** The track's tables if it is a text track, or a caption source when asked; else null. */
        static Track read(SeekableByteChannel ch, Box trak, boolean subtitles, boolean captions)
                throws IOException, TikaException {
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
            String handler = "";
            for (Box b : children(ch, mdia.payload, mdia.end)) {
                if (b.is("hdlr")) {
                    byte[] p = payload(ch, b, 12);
                    handler = p.length >= 12 ? fourCc(p, 8) : "";
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
            boolean text = subtitles && (handler.equals("text") || handler.equals("sbtl")
                    || handler.equals("subt"));
            boolean video = captions && (handler.equals("vide") || handler.equals("clcp"));
            if (!(text || video) || minf == null || t.timescale == 0) {
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
            for (Box b : children(ch, stbl.payload, stbl.end)) {
                switch (b.type) {
                    case "stsd":
                        t.kind = t.sampleKind(ch, b, text);
                        break;
                    case "ctts":
                        // only the first MAX_SAMPLES are walked; a longer table is valid
                        t.ctts = Mp4SubtitleExtractor.read(ch, b.payload, (int) Math.min(b.end - b.payload,
                                8L + 8L * MAX_SAMPLES));
                        if (t.ctts.length < 8) {
                            t.ctts = null;
                        }
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
            return t.kind != null ? t : null;
        }

        // the first sample entry: tx3g/text, c608, or avc1/avc3/hvc1/hev1 with its NAL length
        private Kind sampleKind(SeekableByteChannel ch, Box stsd, boolean text)
                throws IOException {
            byte[] p = payload(ch, stsd, 4096);
            String format = p.length >= 16 ? fourCc(p, 12) : "";
            if (text) {
                return format.equals("tx3g") || format.equals("text") ? Kind.TEXT : null;
            }
            if (format.equals("c608")) {
                return Kind.C608;
            }
            boolean avc = format.equals("avc1") || format.equals("avc3");
            boolean hevc = format.equals("hvc1") || format.equals("hev1");
            if (!avc && !hevc) {
                return null;
            }
            // VisualSampleEntry: 8-byte box header, 78 bytes of fields, then child boxes
            int end = (int) Math.min(p.length, 8 + u32(p, 8));
            int pos = 8 + 86;
            while (pos + 8 <= end) {
                long size = u32(p, pos);
                String type = fourCc(p, pos + 4);
                int lengthAt = avc ? 4 : 21;
                if (size < 8 || pos + size > end) {
                    return null;
                }
                if (type.equals(avc ? "avcC" : "hvcC") && size >= 8 + lengthAt + 1) {
                    nalLength = (p[pos + 8 + lengthAt] & 0x03) + 1;
                    // 3 is not a legal length size
                    return nalLength == 3 ? null : avc ? Kind.AVC : Kind.HEVC;
                }
                pos += (int) size;
            }
            return null;
        }

        void reset() {
            time = 0;
            samplesRead = 0;
            sampleIndex = 0;
            probeLeft = 0;
            nextProbe = 0;
            full = false;
            probing = false;
            pairs = new CaptionPairs();
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
            long cttsEntries = ctts == null ? 0 : Math.min(u32(ctts, 4), (ctts.length - 8) / 8);
            long fileSize = ch.size();

            int sample = 0;
            try {
            // chunks run in order; a table that steps back must not re-walk them
            long nextChunk = 1;
            int sttsIndex = 0;
            long sttsLeft = 0;
            long delta = 0;
            int cttsIndex = 0;
            long cttsLeft = 0;
            long offsetDelta = 0;
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
                        while (cttsLeft == 0 && cttsIndex < cttsEntries) {
                            cttsLeft = u32(ctts, 8 + cttsIndex * 8);
                            // signed in version 1, and written signed into version 0 too
                            offsetDelta = (int) u32(ctts, 12 + cttsIndex * 8);
                            cttsIndex++;
                        }
                        long cto = cttsLeft > 0 ? offsetDelta : 0;
                        if (cttsLeft > 0) {
                            cttsLeft--;
                        }
                        long size = sizes.get(sample);
                        if (!cue(ch, offset, size, duration, cto, fileSize, out)) {
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
                            long cto, long fileSize, SubtitleTrack out) throws IOException {
            if (kind != Kind.TEXT) {
                boolean more = captionSample(ch, offset, size, time + cto, fileSize);
                time += duration;
                return more;
            }
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
                if ((flags & 0x400) != 0) {
                    i += 4;
                }
                long cto = 0;
                if ((flags & 0x800) != 0) {
                    cto = (int) u32(p, i);
                    i += 4;
                }
                if (out != null) {
                    if (samplesRead >= MAX_SAMPLES
                            || !cue(ch, offset, size, duration, cto, fileSize, out)) {
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

        /** Collects one sample's caption pairs; false to stop the walk. */
        private boolean captionSample(SeekableByteChannel ch, long offset, long size, long pts,
                                      long fileSize) throws IOException {
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
            long ms = Math.max(0, millis(pts));
            boolean more = kind == Kind.C608 ? c608(ch, offset, end, ms)
                    : nals(ch, offset, end, ms);
            // the probe only has to find caption data, padding or not
            return more && !(probing && pairs.present);
        }

        // length-prefixed NAL units up to the first slice; SEI bodies go to CaptionData
        private boolean nals(SeekableByteChannel ch, long offset, long end, long ms)
                throws IOException {
            int header = kind == Kind.AVC ? 1 : 2;
            int need = nalLength + header;
            byte[] window = new byte[0];
            long windowAt = offset;
            long pos = offset;
            for (int n = 0; n < MAX_NALS_PER_SAMPLE && pos + need <= end; n++) {
                if (pos < windowAt || pos + need > windowAt + window.length) {
                    int len = (int) Math.min(NAL_WINDOW, end - pos);
                    if (!charge(Math.max(len, MIN_READ_CHARGE))) {
                        return false;
                    }
                    window = Mp4SubtitleExtractor.read(ch, pos, len);
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
                boolean slice = kind == Kind.AVC ? type >= 1 && type <= 5 : type <= 31;
                if (slice) {
                    return true;
                }
                boolean sei = kind == Kind.AVC ? type == 6 : type == 39 || type == 40;
                if (sei) {
                    int take = (int) Math.min(len, MAX_SEI_BYTES);
                    byte[] nal;
                    if (start + take <= windowAt + window.length) {
                        int from = (int) (start - windowAt);
                        nal = Arrays.copyOfRange(window, from, from + take);
                    } else {
                        if (!charge(Math.max(take, MIN_READ_CHARGE))) {
                            return false;
                        }
                        nal = Mp4SubtitleExtractor.read(ch, start, take);
                    }
                    if (CaptionData.fromSei(nal, header,
                            (field, b1, b2) -> pairs.add(ms, field, b1, b2)) > 0) {
                        pairs.present = true;
                    }
                    if (pairs.overflow) {
                        return false;
                    }
                }
                pos = start + len;
            }
            return true;
        }

        // QuickTime c608 sample: cdat (field 1) and cdt2 (field 2) atoms of byte pairs
        private boolean c608(SeekableByteChannel ch, long offset, long end, long ms)
                throws IOException {
            int take = (int) Math.min(end - offset, MAX_SEI_BYTES);
            if (!charge(Math.max(take, MIN_READ_CHARGE))) {
                return false;
            }
            byte[] b = Mp4SubtitleExtractor.read(ch, offset, take);
            int pos = 0;
            while (pos + 8 <= b.length) {
                long size = u32(b, pos);
                if (size < 8 || size > b.length - pos) {
                    break;
                }
                String type = fourCc(b, pos + 4);
                int field = type.equals("cdat") ? 0 : type.equals("cdt2") ? 1 : -1;
                pairs.present |= field >= 0;
                for (int i = pos + 8; field >= 0 && i + 2 <= pos + size; i += 2) {
                    pairs.add(ms, field, b[i] & 0xFF, b[i + 1] & 0xFF);
                }
                pos += (int) size;
            }
            return !pairs.overflow;
        }

        private boolean charge(long bytes) {
            if (budget[0] < bytes) {
                budget[0] = -1;
                return false;
            }
            budget[0] -= bytes;
            return true;
        }
    }

    /** Caption pairs with presentation times, decoded in time order (stable within a frame). */
    private static final class CaptionPairs {
        private static final int INDEX_BITS = 21;
        private long[] keys = new long[256];
        private int[] data = new int[256];
        int count;
        boolean overflow;
        // cc_data was seen, even if every pair was padding
        boolean present;
        // latest time of any pair: when the last caption leaves the screen
        long lastMillis;

        // padding is kept: it separates a repeated control code from a new one, and that must
        // be judged in presentation order
        void add(long ms, int field, int b1, int b2) {
            lastMillis = Math.max(lastMillis, ms);
            if (count >= MAX_CAPTION_PAIRS) {
                overflow = true;
                return;
            }
            if (count == keys.length) {
                keys = Arrays.copyOf(keys, count * 2);
                data = Arrays.copyOf(data, count * 2);
            }
            keys[count] = (Math.min(ms, (1L << 42) - 1) << INDEX_BITS) | count;
            data[count] = (field << 16) | (b1 << 8) | b2;
            count++;
        }

        List<SubtitleTrack> decode(String prefix, long[] budget) {
            long[] sorted = Arrays.copyOf(keys, count);
            Arrays.sort(sorted);
            Cea608Decoder decoder = new Cea608Decoder(prefix, budget);
            for (long key : sorted) {
                int v = data[(int) (key & ((1 << INDEX_BITS) - 1))];
                decoder.pair(key >>> INDEX_BITS, v >> 16, (v >> 8) & 0xFF, v & 0xFF);
            }
            return decoder.finish(lastMillis);
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
