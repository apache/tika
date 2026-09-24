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
package org.apache.tika.parser.ogg;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.gagravarr.ogg.audio.OggAudioHeaders;
import org.gagravarr.ogg.audio.OggAudioInfoHeader;
import org.gagravarr.ogg.audio.OggAudioStatistics;
import org.gagravarr.ogg.audio.OggAudioStream;
import org.gagravarr.vorbis.VorbisComments;
import org.gagravarr.vorbis.VorbisStyleComments;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Audio;
import org.apache.tika.metadata.KeyPrefix;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMP;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.audio.CoverArt;
import org.apache.tika.parser.audio.NumberAndTotal;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parent parser for the various Ogg Audio formats, such as
 * Vorbis and Opus.
 */
public abstract class OggAudioParser extends AbstractParser {
    private static final long serialVersionUID = 5168743829615945633L;

    private static final KeyPrefix VORBIS =
            KeyPrefix.file("vorbis:", "Vorbis comment field names");

    /**
     * The Vorbis comment header vendor string (encoder library identification), also
     * captured under {@link org.apache.tika.metadata.XMP#CREATOR_TOOL}; kept under its
     * own name too since some consumers look for the raw vorbis: field.
     */
    private static final Property VORBIS_VENDOR = Property.internalText("vorbis:vendor");

    // Codec bitstream/library version string (e.g. "Theora 3.2.1"); distinct from the vendor/encoder tool under XMP#CREATOR_TOOL.
    protected static final Property CODEC_VERSION = Property.internalText("ogg:codec-version");

    /**
     * Comment holding an embedded picture (e.g. cover art) as a base64
     * encoded FLAC picture block
     */
    private static final String METADATA_BLOCK_PICTURE = "metadata_block_picture";


    /**
     * Returns the first positive integer found under the given comment keys,
     * or null if there is none.
     */
    private static Integer firstPositiveInteger(Map<String, List<String>> fields, String... keys) {
        for (String key : keys) {
            for (String value : all(fields, key)) {
                try {
                    int parsed = Integer.parseInt(value.trim());
                    if (parsed > 0) {
                        return parsed;
                    }
                } catch (NumberFormatException e) {
                    //skip unparseable values
                }
            }
        }
        return null;
    }

    protected static void extractChannelInfo(Metadata metadata, OggAudioInfoHeader info) {
        extractChannelInfo(metadata, info.getNumChannels());
    }

    protected static void extractChannelInfo(Metadata metadata, int channelCount) {
        if (channelCount == 1) {
            metadata.set(XMPDM.AUDIO_CHANNEL_TYPE, "Mono");
        } else if (channelCount == 2) {
            metadata.set(XMPDM.AUDIO_CHANNEL_TYPE, "Stereo");
        } else if (channelCount == 5) {
            metadata.set(XMPDM.AUDIO_CHANNEL_TYPE, "5.1");
        } else if (channelCount == 7) {
            metadata.set(XMPDM.AUDIO_CHANNEL_TYPE, "7.1");
        }
    }

    /**
     * @return the pictures carried in the comments; the caller emits them
     * through {@link #extractPictures(List, XHTMLContentHandler, ParseContext)},
     * {@link FlacParser} first merges them with the native PICTURE blocks so
     * one file yields one thumbnail
     */
    protected static List<PictureBlock> extractComments(Metadata metadata,
            XHTMLContentHandler xhtml, VorbisStyleComments comments)
            throws IOException, TikaException, SAXException {
        Map<String, List<String>> fields = fields(comments);
        String title = first(fields, VorbisComments.KEY_TITLE);
        String artist = first(fields, VorbisComments.KEY_ARTIST);
        String album = first(fields, VorbisComments.KEY_ALBUM);
        String trackNumber = first(fields, VorbisComments.KEY_TRACKNUMBER);
        // Get the specific known comments
        metadata.set(TikaCoreProperties.TITLE, title);
        metadata.set(TikaCoreProperties.CREATOR, artist);
        metadata.set(XMPDM.ARTIST, artist);
        metadata.set(XMPDM.ALBUM, album);
        metadata.set(XMPDM.GENRE, first(fields, VorbisComments.KEY_GENRE));
        metadata.set(XMPDM.RELEASE_DATE, first(fields, VorbisComments.KEY_DATE));
        metadata.add(XMP.CREATOR_TOOL, comments.getVendor());
        metadata.add(VORBIS_VENDOR, comments.getVendor());

        //xmpDM:copyright is single-valued, so map the first comment; like
        //vendor, the raw comments also stay available under the vorbis: name
        String copyright = first(fields, "copyright");
        if (copyright != null) {
            metadata.set(XMPDM.COPYRIGHT, copyright);
        }

        for (String comment : all(fields, "comment")) {
            metadata.add(XMPDM.LOG_COMMENT, comment);
        }

        // Grab the rest just in case; the pictures become embedded
        //  documents instead, their raw base64 blocks help nobody
        Set<String> done = new HashSet<>();
        for (String key : Arrays.asList(
                VorbisComments.KEY_TITLE, VorbisComments.KEY_ARTIST,
                VorbisComments.KEY_ALBUM, VorbisComments.KEY_GENRE,
                VorbisComments.KEY_DATE, VorbisComments.KEY_TRACKNUMBER,
                "vendor", "comment", METADATA_BLOCK_PICTURE)) {
            done.add(fieldName(key));
        }
        // BAG: a Vorbis comment field can legitimately repeat.
        for (Map.Entry<String, List<String>> field : fields.entrySet()) {
            if (!done.contains(field.getKey())) {
                for (String value : field.getValue()) {
                    metadata.add(VORBIS, field.getKey(), value);
                }
            }
        }

        // Output as text too
        xhtml.element("h1", title);
        xhtml.element("p", artist);

        // Album and Track number
        if (trackNumber != null) {
            xhtml.element("p", album + ", track " + trackNumber);
            metadata.set(Audio.RAW_TRACK_NUMBER, trackNumber);
            NumberAndTotal trackNumberAndTotal = NumberAndTotal.parse(trackNumber);
            if (trackNumberAndTotal != null) {
                if (trackNumberAndTotal.number != null) {
                    metadata.set(XMPDM.TRACK_NUMBER, trackNumberAndTotal.number);
                }
                if (trackNumberAndTotal.total != null) {
                    metadata.set(Audio.TRACK_COUNT, trackNumberAndTotal.total);
                }
            }
        } else {
            xhtml.element("p", album);
        }
        for (String discValue : all(fields, "discnumber")) {
            metadata.set(Audio.RAW_DISC_NUMBER, discValue);
            NumberAndTotal discNumberAndTotal = NumberAndTotal.parse(discValue);
            if (discNumberAndTotal != null) {
                if (discNumberAndTotal.number != null) {
                    metadata.set(XMPDM.DISC_NUMBER, discNumberAndTotal.number);
                }
                if (discNumberAndTotal.total != null) {
                    metadata.set(Audio.DISC_COUNT, discNumberAndTotal.total);
                }
            }
        }
        //explicit totals win over the combined "n/total" form
        Integer trackTotal = firstPositiveInteger(fields, "tracktotal", "totaltracks");
        if (trackTotal != null) {
            metadata.set(Audio.TRACK_COUNT, trackTotal);
        }
        Integer discTotal = firstPositiveInteger(fields, "disctotal", "totaldiscs");
        if (discTotal != null) {
            metadata.set(Audio.DISC_COUNT, discTotal);
        }

        // A few other bits
        xhtml.element("p", first(fields, VorbisComments.KEY_DATE));
        for (String comment : all(fields, "comment")) {
            xhtml.element("p", comment);
        }
        xhtml.element("p", first(fields, VorbisComments.KEY_GENRE));

        // The pictures are the caller's to emit
        return parsePictures(fields);
    }

    /**
     * The comment fields keyed by lower-case name, re-read from the raw comment header:
     * vorbis-java folds names in the default locale and then strips non-ASCII, so a
     * Turkic JVM turns TITLE into "ttle" (TIKA-4921). Falls back to the library's map
     * when there is no raw header, as for comments built in code.
     */
    static Map<String, List<String>> fields(VorbisStyleComments comments) {
        Map<String, List<String>> fields = rawFields(comments);
        if (fields != null) {
            return fields;
        }
        fields = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> field : comments.getAllComments().entrySet()) {
            fields.computeIfAbsent(fieldName(field.getKey()), k -> new ArrayList<>())
                    .addAll(field.getValue());
        }
        return fields;
    }

    private static Map<String, List<String>> rawFields(VorbisStyleComments comments) {
        byte[] data;
        try {
            data = comments.getData();
        } catch (NullPointerException e) {
            // no packet behind comments built in code
            return null;
        }
        if (data == null) {
            return null;
        }
        // the format-specific header size is protected; find the vendor string instead
        String vendor = comments.getVendor() == null ? "" : comments.getVendor();
        byte[] vendorBytes = vendor.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int offset = -1;
        for (int headerSize : new int[]{0, 4, 7, 8}) {
            int end = headerSize + 4 + vendorBytes.length;
            if (end <= data.length && buffer.getInt(headerSize) == vendorBytes.length &&
                    Arrays.equals(data, headerSize + 4, end, vendorBytes, 0, vendorBytes.length)) {
                offset = end;
                break;
            }
        }
        if (offset < 0) {
            return null;
        }
        try {
            buffer.position(offset);
            int count = buffer.getInt();
            if (count < 0) {
                return null;
            }
            Map<String, List<String>> fields = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                int length = buffer.getInt();
                if (length < 0 || length > buffer.remaining()) {
                    return null;
                }
                byte[] bytes = new byte[length];
                buffer.get(bytes);
                String comment = new String(bytes, StandardCharsets.UTF_8);
                int eq = comment.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                fields.computeIfAbsent(fieldName(comment.substring(0, eq)), k -> new ArrayList<>())
                        .add(comment.substring(eq + 1));
            }
            return fields;
        } catch (BufferUnderflowException e) {
            return null;
        }
    }

    private static String fieldName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String first(Map<String, List<String>> fields, String name) {
        List<String> values = fields.get(fieldName(name));
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static List<String> all(Map<String, List<String>> fields, String name) {
        return fields.getOrDefault(fieldName(name), Collections.emptyList());
    }

    /**
     * Parses the embedded pictures, such as cover art, out of the comments.
     * The pictures are carried as base64 encoded FLAC picture blocks;
     * malformed blocks are skipped silently.
     */
    private static List<PictureBlock> parsePictures(Map<String, List<String>> fields) {
        List<PictureBlock> pictures = new ArrayList<>();
        for (String block : all(fields, METADATA_BLOCK_PICTURE)) {
            byte[] decoded;
            try {
                decoded = Base64.getMimeDecoder().decode(block);
            } catch (IllegalArgumentException e) {
                //not valid base64, skip
                continue;
            }
            PictureBlock picture = PictureBlock.parse(decoded);
            if (picture != null) {
                pictures.add(picture);
            }
        }
        return pictures;
    }

    /**
     * Sends parsed picture blocks to the embedded document extractor;
     * {@link CoverArt#thumbnailIndex(List)} decides which of them is the
     * file's thumbnail. Native FLAC PICTURE metadata blocks use the very
     * same structure, so {@link FlacParser} shares this method.
     */
    static void extractPictures(List<PictureBlock> pictures, XHTMLContentHandler xhtml,
            ParseContext context) throws IOException, SAXException {
        List<CoverArt.Picture> mapped = new ArrayList<>();
        for (PictureBlock picture : pictures) {
            mapped.add(new CoverArt.Picture(picture.pictureType, picture.mimeType,
                    picture.description, picture.data));
        }
        CoverArt.extractPictures(mapped, xhtml, context);
    }

    /**
     * A FLAC picture block: a 32 bit BE picture type, the mime type, the
     * description, the image geometry and the picture data, with mime type,
     * description and data length prefixed.
     */
    static final class PictureBlock {
        final int pictureType;
        final String mimeType;
        final String description;
        final byte[] data;

        private PictureBlock(int pictureType, String mimeType, String description, byte[] data) {
            this.pictureType = pictureType;
            this.mimeType = mimeType;
            this.description = description;
            this.data = data;
        }

        /**
         * Parses a picture block, or returns null for a malformed or
         * truncated one, or one that links to a picture instead of
         * embedding it.
         */
        static PictureBlock parse(byte[] block) {
            try {
                ByteBuffer buffer = ByteBuffer.wrap(block);
                int pictureType = buffer.getInt();
                String mimeType = getPrefixedString(buffer, StandardCharsets.ISO_8859_1);
                if (mimeType == null || "-->".equals(mimeType)) {
                    return null;
                }
                String description = getPrefixedString(buffer, StandardCharsets.UTF_8);
                if (description == null) {
                    return null;
                }
                // Width, height, color depth and number of colors
                buffer.position(buffer.position() + 16);
                int dataLength = buffer.getInt();
                if (dataLength <= 0 || dataLength > buffer.remaining()) {
                    return null;
                }
                byte[] data = new byte[dataLength];
                buffer.get(data);
                return new PictureBlock(pictureType, mimeType, description, data);
            } catch (BufferUnderflowException | IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static String getPrefixedString(ByteBuffer buffer, Charset charset) {
        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) {
            return null;
        }
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, charset);
    }

    protected static void extractDuration(Metadata metadata, XHTMLContentHandler xhtml,
            OggAudioHeaders headers, OggAudioStream audio) throws IOException, SAXException {
        // Have the statistics calculated
        OggAudioStatistics stats = new OggAudioStatistics(headers, audio);
        stats.calculate();

        // Record the duration, if available
        extractDuration(metadata, xhtml, stats.getDurationSeconds());
    }

    protected static void extractDuration(Metadata metadata, XHTMLContentHandler xhtml,
            double duration) throws SAXException {
        // Record the duration, if available
        if (duration > 0) {
            // Save as metadata to the nearest .01 seconds.
            // DecimalFormat is not thread-safe and these parsers are shared across
            // threads, so create a new one per call (see MP4Parser).
            DecimalFormat durationFormat =
                    (DecimalFormat) NumberFormat.getNumberInstance(Locale.ROOT);
            durationFormat.applyPattern("0.0#");
            metadata.add(XMPDM.DURATION, durationFormat.format(duration));

            // Output as Hours / Minutes / Seconds / Parts
            String durationStr = formatDuration(duration);
            xhtml.element("p", durationStr);
        }
    }

    private static String formatDuration(double durationSeconds) {
        long totalSeconds = (long) durationSeconds;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        double fraction = durationSeconds - totalSeconds;

        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
        }
    }

}
