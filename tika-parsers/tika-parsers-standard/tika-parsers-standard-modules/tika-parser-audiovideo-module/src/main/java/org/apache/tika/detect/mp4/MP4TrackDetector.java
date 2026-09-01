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
package org.apache.tika.detect.mp4;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp4.Mp4Boxes;

/**
 * Types a plain MP4 file by what it contains, as RFC 4337 asks: video/mp4
 * when it has a video track, audio/mp4 when it has only audio, and
 * application/mp4 when it has neither (TIKA-2935, TIKA-3646).
 * <p>
 * The mime magic cannot do this. It only knows the brand in the
 * {@code ftyp} box, and the brand of the great majority of MP4 files,
 * {@code isom}, says nothing about their content: Tika's own
 * {@code testMP4Video.mp4} and {@code testMP4AudioOnly.mp4} both carry it.
 * Brands that name a format of their own (M4A, 3GP, HEIC, AVIF, CR3, ...)
 * keep their magic and are left alone here.
 * <p>
 * The detector walks the top level boxes by their size fields, which skips
 * the media data rather than reading it, and reads the handler type of each
 * track in the movie box. A file whose movie box is unreachable (missing,
 * beyond the limits below, or not backed by a file) is left to the magic.
 */
@TikaComponent
public class MP4TrackDetector implements Detector {

    private static final long serialVersionUID = 1L;

    static final MediaType MP4_VIDEO = MediaType.video("mp4");
    static final MediaType MP4_AUDIO = MediaType.audio("mp4");
    static final MediaType MP4_APPLICATION = MediaType.application("mp4");

    /**
     * The brands of a plain MP4. A brand naming a specific format is not
     * here: those files are typed by their own magic.
     */
    private static final Set<String> BRANDS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1",
                    "mmp4")));

    /**
     * Bounds the walk over a crafted file: a real one has a handful of top
     * level boxes and a handful of tracks.
     */
    private static final int MAX_BOXES = 256;

    private static final int MAX_TRACKS = 64;

    /**
     * How much of a movie box is read for its track handlers.
     */
    private static final int MAX_MOOV_BYTES = 8 * 1024 * 1024;

    /**
     * Without a file to seek in, only a movie box within this many bytes of
     * the start is found.
     */
    private static final int MAX_PREFIX_BYTES = 256 * 1024;

    @Override
    public MediaType detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext)
            throws IOException {
        if (tis == null) {
            return MediaType.OCTET_STREAM;
        }
        tis.mark(16);
        byte[] header = new byte[16];
        int read;
        try {
            read = IOUtils.read(tis, header, 0, 16);
        } finally {
            tis.reset();
        }
        if (read < 12 || !isFtyp(header) || !BRANDS.contains(brand(header))) {
            return MediaType.OCTET_STREAM;
        }
        byte[] moov = movieBox(tis);
        if (moov == null) {
            return MediaType.OCTET_STREAM;
        }
        return byTracks(moov);
    }

    private static boolean isFtyp(byte[] header) {
        return header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
    }

    private static String brand(byte[] header) {
        return new String(header, 8, 4, StandardCharsets.US_ASCII);
    }

    /**
     * The movie box, found by walking the top level boxes; null when there
     * is none within the limits.
     */
    private static byte[] movieBox(TikaInputStream tis) throws IOException {
        if (tis.hasFile()) {
            try (SeekableByteChannel channel = Files.newByteChannel(tis.getPath())) {
                return movieBox(new ChannelBoxes(channel));
            }
        }
        tis.mark(MAX_PREFIX_BYTES);
        try {
            byte[] prefix = new byte[MAX_PREFIX_BYTES];
            int length = IOUtils.read(tis, prefix, 0, MAX_PREFIX_BYTES);
            return movieBox(new ArrayBoxes(prefix, Math.max(length, 0)));
        } finally {
            tis.reset();
        }
    }

    private static byte[] movieBox(Boxes boxes) throws IOException {
        long position = 0;
        for (int i = 0; i < MAX_BOXES; i++) {
            byte[] header = boxes.read(position, 16);
            if (header == null || header.length < 8) {
                return null;
            }
            long size = uint32(header, 0);
            int headerSize = 8;
            if (size == 1) {
                if (header.length < 16) {
                    return null;
                }
                size = uint64(header, 8);
                headerSize = 16;
            } else if (size == 0) {
                size = boxes.length() - position;
            }
            if (size < headerSize || position > boxes.length() - size) {
                return null;
            }
            if ("moov".equals(Mp4Boxes.fourCC(header, 4))) {
                long payload = size - headerSize;
                if (payload <= 0 || payload > MAX_MOOV_BYTES) {
                    return null;
                }
                return boxes.read(position + headerSize, (int) payload);
            }
            position += size;
        }
        return null;
    }

    /**
     * The type of the movie: video where a track says so, else audio, else
     * neither. The handlers are read where a track keeps them,
     * {@code moov/trak/mdia/hdlr}, so the handler of the metadata in
     * {@code moov/udta/meta} is not mistaken for a track.
     */
    private static MediaType byTracks(byte[] moov) {
        boolean audio = false;
        int trak = Mp4Boxes.findBox(moov, 0, moov.length, "trak", MAX_BOXES);
        for (int track = 0; trak >= 0 && track < MAX_TRACKS; track++) {
            int trakEnd = Mp4Boxes.boxEnd(moov, trak, moov.length);
            if (trakEnd < 0) {
                break;
            }
            String handler = handler(moov, trak, trakEnd);
            if ("vide".equals(handler)) {
                return MP4_VIDEO;
            }
            audio |= "soun".equals(handler);
            trak = Mp4Boxes.findBox(moov, trakEnd, moov.length, "trak", MAX_BOXES);
        }
        return audio ? MP4_AUDIO : MP4_APPLICATION;
    }

    /**
     * The handler type of a track, from {@code mdia/hdlr}, or null.
     */
    private static String handler(byte[] moov, int trak, int trakEnd) {
        int payload = Mp4Boxes.payloadStart(moov, trak, trakEnd);
        int mdia = payload < 0 ? -1
                : Mp4Boxes.findBox(moov, payload, trakEnd, "mdia", MAX_BOXES);
        if (mdia < 0) {
            return null;
        }
        int mdiaEnd = Mp4Boxes.boxEnd(moov, mdia, trakEnd);
        int mdiaPayload = mdiaEnd < 0 ? -1 : Mp4Boxes.payloadStart(moov, mdia, mdiaEnd);
        int hdlr = mdiaPayload < 0 ? -1
                : Mp4Boxes.findBox(moov, mdiaPayload, mdiaEnd, "hdlr", MAX_BOXES);
        if (hdlr < 0) {
            return null;
        }
        //hdlr: version and flags, pre_defined, then the handler type
        int handler = hdlr + Mp4Boxes.HEADER + 8;
        return handler + 4 <= Mp4Boxes.boxEnd(moov, hdlr, mdiaEnd)
                ? Mp4Boxes.fourCC(moov, handler) : null;
    }

    private static long uint32(byte[] b, int off) {
        return ByteBuffer.wrap(b, off, 4).getInt() & 0xFFFFFFFFL;
    }

    private static long uint64(byte[] b, int off) {
        return ByteBuffer.wrap(b, off, 8).getLong();
    }

    /**
     * The bytes the walk reads from, so the same walk serves a file and a
     * prefix held in memory.
     */
    private interface Boxes {
        /**
         * @return the bytes at the offset, or null if they are not there
         */
        byte[] read(long offset, int length) throws IOException;

        long length() throws IOException;
    }

    private static final class ArrayBoxes implements Boxes {
        private final byte[] bytes;
        private final int length;

        ArrayBoxes(byte[] bytes, int length) {
            this.bytes = bytes;
            this.length = length;
        }

        @Override
        public byte[] read(long offset, int size) {
            if (offset < 0 || offset > length - Math.min(size, 8)) {
                return null;
            }
            int available = (int) Math.min(size, length - offset);
            return Arrays.copyOfRange(bytes, (int) offset, (int) offset + available);
        }

        @Override
        public long length() {
            return length;
        }
    }

    private static final class ChannelBoxes implements Boxes {
        private final SeekableByteChannel channel;

        ChannelBoxes(SeekableByteChannel channel) {
            this.channel = channel;
        }

        @Override
        public byte[] read(long offset, int size) throws IOException {
            if (offset < 0 || offset >= channel.size()) {
                return null;
            }
            int available = (int) Math.min(size, channel.size() - offset);
            ByteBuffer buffer = ByteBuffer.allocate(available);
            channel.position(offset);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                //read to the end of the buffer
            }
            //only what was actually read: a short read must not look like data
            return buffer.position() == available ? buffer.array()
                    : Arrays.copyOf(buffer.array(), buffer.position());
        }

        @Override
        public long length() throws IOException {
            return channel.size();
        }
    }
}
