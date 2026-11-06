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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.xml.sax.SAXException;

import org.apache.tika.exception.EmbeddedLimitReachedException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * The text subtitle tracks and closed captions of one container file, read up front (random
 * access needs the stream unread) and held, under limits they share, until {@link #emit}, so
 * a container parser can emit them after its own metadata pass. Used by Tika's MP4 and
 * Matroska parsers; not a stable API.
 */
public final class SubtitleTracks {

    static final int MAX_TRACKS = 64;
    // cue text held across all tracks until emit
    static final int MAX_TOTAL_CHARS = 16 * 1024 * 1024;
    // bytes decoded per file, markup and blanks included: bounds the work, not the output
    static final long MAX_DECODED_BYTES = 256L * 1024 * 1024;
    // samples or blocks walked per file, empty ones included
    static final int MAX_SAMPLES = 1_000_000;
    // captions have their own budgets, so a long video cannot starve the text tracks;
    // samples are counted as walked, skipped ones included
    static final int MAX_CAPTION_SAMPLES = 1_000_000;
    static final long MAX_CAPTION_BYTES = 128L * 1024 * 1024;
    // caption byte pairs held for reordering, across tracks, padding included
    static final int CAPTION_PAIR_BITS = 21;
    static final int MAX_CAPTION_PAIRS = 1 << CAPTION_PAIR_BITS;
    private static final int MAX_WARNINGS = 32;

    private final Metadata containerMetadata;
    private final ParseContext context;
    private final List<SubtitleTrack> tracks = new ArrayList<>();
    private final Set<String> warned = new HashSet<>();
    private long decodedBytes;
    private int samples;
    private int chars;
    private boolean spent;
    private int captionSamples;
    private long captionBytes;
    private int captionPairs;
    private boolean captionsSpent;
    private EmbeddedLimitReachedException limit;

    SubtitleTracks(Metadata containerMetadata, ParseContext context) {
        this.containerMetadata = containerMetadata;
        this.context = context;
    }

    /**
     * The tx3g and QuickTime text tracks of an MP4 or QuickTime file and, with
     * {@code closedCaptions}, its CEA-608 captions: from QuickTime {@code c608} tracks, and
     * from the SEI messages of H.264 and HEVC video as {@code videoScan} says. Read through
     * {@link TikaInputStream#getSeekableByteChannel()}; the caller decides whether that is
     * cheap. Leaves the stream's read position alone.
     */
    public static SubtitleTracks readMp4(TikaInputStream tis, Metadata containerMetadata,
                                         ParseContext context, boolean closedCaptions,
                                         VideoCaptionScan videoScan) {
        SubtitleTracks out = new SubtitleTracks(containerMetadata, context);
        Mp4SubtitleExtractor.extract(tis, out, closedCaptions
                ? new Mp4SubtitleExtractor.Captions(true, videoScan)
                : new Mp4SubtitleExtractor.Captions(false, VideoCaptionScan.NONE));
        return out;
    }

    /**
     * The text tracks of a Matroska or WebM file. A stream input is read up to the first
     * Cluster if no text track is taken, else to its end or until a budget is spent.
     */
    public static SubtitleTracks readMatroska(TikaInputStream tis, Metadata containerMetadata,
                                              ParseContext context) {
        SubtitleTracks out = new SubtitleTracks(containerMetadata, context);
        MkvSubtitleExtractor.extract(tis, out);
        return out;
    }

    /**
     * A new track, or null if the embedded document extractor refuses it (asked before any
     * of it is decoded), past {@link #MAX_TRACKS}, or once a budget is spent.
     */
    SubtitleTrack add(String name, String language, String title) {
        if (spent) {
            return null;
        }
        if (tracks.size() >= MAX_TRACKS) {
            warn("more than " + MAX_TRACKS + " subtitle tracks; the rest skipped");
            return null;
        }
        try {
            if (!EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context).shouldParseEmbedded(
                    SubtitleTrack.metadata(name, language, title, context), context)) {
                return null;
            }
        } catch (EmbeddedLimitReachedException e) {
            // thrown from emit, after the container parser's own metadata pass
            limit = e;
            spent = true;
            return null;
        }
        SubtitleTrack t = new SubtitleTrack(this, name, language, title);
        tracks.add(t);
        return t;
    }

    /** Charges decoded bytes; false once the file's budget is spent. */
    boolean charge(long bytes) {
        if (spent) {
            return false;
        }
        if (bytes > MAX_DECODED_BYTES - decodedBytes) {
            spend("subtitle text exceeds " + MAX_DECODED_BYTES + " decoded bytes");
            return false;
        }
        decodedBytes += bytes;
        return true;
    }

    /** Charges one sample or block read; false once the file's budget is spent. */
    boolean chargeSample() {
        if (spent) {
            return false;
        }
        if (++samples > MAX_SAMPLES) {
            spend("more than " + MAX_SAMPLES + " subtitle samples");
            return false;
        }
        return true;
    }

    boolean chargeChars(int n) {
        if (spent) {
            return false;
        }
        if (n > MAX_TOTAL_CHARS - chars) {
            spend("subtitle text exceeds " + MAX_TOTAL_CHARS + " chars");
            return false;
        }
        chars += n;
        return true;
    }

    /** Charges one sample walked for captions; false once the captions' budget is spent. */
    boolean chargeCaptionSample() {
        if (captionsSpent) {
            return false;
        }
        if (++captionSamples > MAX_CAPTION_SAMPLES) {
            spendCaptions("more than " + MAX_CAPTION_SAMPLES + " caption samples");
            return false;
        }
        return true;
    }

    /** Charges bytes read for captions; false once the captions' budget is spent. */
    boolean chargeCaptionBytes(long bytes) {
        if (captionsSpent) {
            return false;
        }
        if (bytes > MAX_CAPTION_BYTES - captionBytes) {
            spendCaptions("more than " + MAX_CAPTION_BYTES + " bytes read for captions");
            return false;
        }
        captionBytes += bytes;
        return true;
    }

    /** Charges one caption byte pair held; false once the captions' budget is spent. */
    boolean chargeCaptionPair() {
        if (captionsSpent) {
            return false;
        }
        if (++captionPairs > MAX_CAPTION_PAIRS) {
            spendCaptions("more than " + MAX_CAPTION_PAIRS + " caption byte pairs");
            return false;
        }
        return true;
    }

    /** Uncounts pairs that a track read again will collect anew. */
    void refundCaptionPairs(int n) {
        captionPairs -= Math.min(n, captionPairs);
    }

    /** True once a caption budget is spent: no more caption data is read. */
    boolean captionsSpent() {
        return captionsSpent;
    }

    private void spendCaptions(String why) {
        captionsSpent = true;
        warn(why + "; the rest skipped");
    }

    /** True once a budget is spent: no more cues are taken. */
    boolean isSpent() {
        return spent;
    }

    /** Records a warning on the container, once per message. */
    void warn(String message) {
        if (warned.size() < MAX_WARNINGS && warned.add(message)) {
            EmbeddedDocumentUtil.recordException(new TikaException("subtitles: " + message),
                    containerMetadata, context);
        }
    }

    /** Records damage that ended the walk; what was read before it is kept. */
    void damaged(IOException e) {
        warn(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
    }

    private void spend(String why) {
        spent = true;
        warn(why + "; the rest skipped");
    }

    /**
     * Emits every track with cues as an embedded {@code text/vtt} document, then throws the
     * {@link EmbeddedLimitReachedException} a track met when it was found, if any.
     */
    public void emit(XHTMLContentHandler xhtml) throws IOException, SAXException {
        for (SubtitleTrack t : tracks) {
            t.emit(xhtml, context);
        }
        if (limit != null) {
            throw limit;
        }
    }
}
