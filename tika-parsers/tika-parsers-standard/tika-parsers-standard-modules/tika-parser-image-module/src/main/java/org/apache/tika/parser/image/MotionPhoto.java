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
package org.apache.tika.parser.image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Google;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * The video of a motion photo, appended after the image and described by the
 * XMP the image parsers already extract (TIKA-4869):
 * <ul>
 *   <li>a Motion Photo lists its parts in {@code Container:Directory}: the
 *       primary image first, the rest tightly packed after it, each with an
 *       {@code Item:Length} and an optional {@code Item:Padding};</li>
 *   <li>the older MicroVideo gives {@code Camera:MicroVideoOffset}, the
 *       number of bytes from the end of the file to the start of the video.</li>
 * </ul>
 * Either way the video ends at the end of the file, so its start follows from
 * its length. What is found there is typed by content: the declared
 * {@code Item:Mime} is not used as a detection hint, because a hint would
 * make a wrong length pass as a video, and nothing is emitted when detection
 * recognizes nothing.
 * <p>
 * The same holds for HEIC and AVIF motion photos, whose video sits in a
 * trailing {@code mpvd} box; its 8 byte header is the primary item's padding,
 * so the video still ends at the end of the file.
 */
final class MotionPhoto {

    /**
     * The name the video is emitted under, with the extension of whatever it
     * turns out to be.
     */
    private static final String NAME = "motion-photo";

    private static final String ITEM = "]/Container:Item/";
    private static final String DIRECTORY = "xmp-raw:Container:Directory[";

    /**
     * The {@code Item:Semantic} of the video.
     */
    private static final String MOTION_PHOTO = "MotionPhoto";

    /**
     * A directory holds a handful of items; this only bounds the walk.
     */
    private static final int MAX_ITEMS = 64;

    /**
     * Enough of the video for the detectors to recognize it.
     */
    private static final int DETECTION_PREFIX = 8 * 1024;

    private MotionPhoto() {
    }

    /**
     * Emits the video as an embedded document, or nothing when the image
     * declares none, when the declared length does not fit the file, or when
     * the bytes there are not recognized.
     */
    static void extract(TikaInputStream tis, Metadata metadata, XHTMLContentHandler xhtml,
                        ParseContext context) throws IOException, SAXException {
        Declaration declared = declaration(metadata);
        if (declared == null) {
            return;
        }
        long length = declared.length;
        Path file = tis.getPath();
        long start = Files.size(file) - length;
        if (start <= 0) {
            EmbeddedDocumentUtil.recordException(new TikaException(
                    "motion photo video of " + length + " bytes does not fit the file"),
                    metadata, context);
            return;
        }
        MediaType type = detect(file, start, context);
        if (type == null || MediaType.OCTET_STREAM.equals(type)) {
            EmbeddedDocumentUtil.recordException(new TikaException(
                    "no motion photo video at the declared offset"), metadata, context);
            return;
        }
        Metadata videoMetadata = Metadata.newInstance(context);
        //the name has to be set before the parse, which is the only thing here
        //that knows the format for sure, so it follows the file's own
        //declaration and carries no extension where there is none to follow
        videoMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, NAME + extension(declared.mime));
        videoMetadata.set(HttpHeaders.CONTENT_TYPE, type.toString());
        videoMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.name());
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        if (extractor.shouldParseEmbedded(videoMetadata, context)) {
            try (TikaInputStream video = TikaInputStream.get(region(file, start))) {
                extractor.parseEmbedded(video, new EmbeddedContentHandler(xhtml), videoMetadata,
                        context, false);
            }
        }
    }

    /**
     * What the image says about its video: how many bytes it occupies at the
     * end of the file and, where the format has it, the type it claims.
     */
    static Declaration declaration(Metadata metadata) {
        Declaration fromDirectory = directoryDeclaration(metadata);
        if (fromDirectory != null) {
            return fromDirectory;
        }
        long microVideo = positiveLong(metadata.get(Google.MICRO_VIDEO_OFFSET));
        //the MicroVideo format names no type
        return microVideo > 0 ? new Declaration(microVideo, null) : null;
    }

    /**
     * A declared video: its length to the end of the file and its claimed
     * type, which may be null.
     */
    static final class Declaration {
        final long length;
        final MediaType mime;

        Declaration(long length, MediaType mime) {
            this.length = length;
            this.mime = mime;
        }
    }

    /**
     * The bytes from the start of the video to the end of the file: its own
     * length and that of anything the directory lists after it. In practice
     * the video is the last item, in Ultra HDR files as well, where the gain
     * map comes before it.
     */
    private static Declaration directoryDeclaration(Metadata metadata) {
        int video = -1;
        int items = 0;
        for (int i = 1; i <= MAX_ITEMS; i++) {
            String semantic = metadata.get(DIRECTORY + i + ITEM + "Item:Semantic");
            if (semantic == null) {
                break;
            }
            items = i;
            if (video < 0 && MOTION_PHOTO.equals(semantic)) {
                video = i;
            }
        }
        if (video < 0) {
            return null;
        }
        long length = 0;
        for (int i = video; i <= items; i++) {
            long itemLength = positiveLong(metadata.get(DIRECTORY + i + ITEM + "Item:Length"));
            if (itemLength <= 0) {
                return null;
            }
            length += itemLength;
            long padding = positiveLong(metadata.get(DIRECTORY + i + ITEM + "Item:Padding"));
            if (padding > 0) {
                length += padding;
            }
        }
        return new Declaration(length,
                MediaType.parse(metadata.get(DIRECTORY + video + ITEM + "Item:Mime")));
    }

    private static long positiveLong(String value) {
        if (value == null) {
            return -1;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Types the data at the offset by content alone.
     */
    private static MediaType detect(Path file, long start, ParseContext context)
            throws IOException {
        byte[] prefix;
        try (InputStream is = region(file, start)) {
            prefix = is.readNBytes(DETECTION_PREFIX);
        }
        try (TikaInputStream tis = TikaInputStream.get(prefix)) {
            return EmbeddedDocumentUtil.getDetector(context).detect(tis, new Metadata(), context);
        }
    }

    /**
     * The file from the offset to its end.
     */
    private static InputStream region(Path file, long start) throws IOException {
        InputStream is = Files.newInputStream(file);
        try {
            is.skipNBytes(start);
        } catch (IOException e) {
            is.close();
            throw e;
        }
        return is;
    }

    /**
     * The usual extension of a type, {@code .mp4} for video/mp4.
     */
    private static String extension(MediaType type) {
        if (type == null) {
            return "";
        }
        try {
            String extension = MimeTypes.getDefaultMimeTypes().forName(type.toString())
                    .getExtension();
            if (!extension.isEmpty()) {
                return extension;
            }
        } catch (MimeTypeException e) {
            //fall through to the subtype
        }
        return "." + type.getSubtype();
    }
}
