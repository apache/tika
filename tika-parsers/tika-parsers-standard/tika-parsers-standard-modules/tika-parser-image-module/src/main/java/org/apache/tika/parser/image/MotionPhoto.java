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

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Google;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * The video of a motion photo, appended after the image and described by the
 * XMP the image parsers already extract (TIKA-4869):
 * <ul>
 *   <li>a Motion Photo lists its parts in {@code Container:Directory}: the
 *       primary image first, the video last with nothing after it, each with
 *       an {@code Item:Length};</li>
 *   <li>the older MicroVideo gives {@code Camera:MicroVideoOffset}, the
 *       number of bytes from the end of the file to the start of the video.</li>
 * </ul>
 * Either way the video ends at the end of the file, so its start follows from
 * its length. What is found there is typed by content: the declared
 * {@code Item:Mime} is not used as a detection hint, because a hint would
 * make a wrong length pass as a video, and nothing is emitted when detection
 * recognizes nothing.
 * <p>
 * The same holds for HEIC motion photos, whose video sits in a trailing
 * {@code mpvd} box; its 8 byte header is the primary item's padding, so the
 * video still ends at the end of the file.
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

    /**
     * The branch below an emitted trailer, which is not searched for a trailer
     * of its own: what is appended to an image may be an image again, and a
     * crafted file can nest that as deep as it likes.
     */
    private static final class Nested {
    }

    private static final Nested NESTED = new Nested();

    private MotionPhoto() {
    }

    /**
     * Emits the trailer as an embedded document, or nothing when the image
     * declares none, when the declared length does not fit the file, or when
     * the bytes there are not recognized. The last two are what sharing a
     * motion photo out of a gallery leaves behind, a common enough thing that
     * it is not worth an exception on a file that is otherwise fine; the XMP
     * that promised the video is in the metadata for a client to see.
     */
    static void extract(TikaInputStream tis, Metadata metadata, XHTMLContentHandler xhtml,
                        ParseContext context) throws IOException, SAXException {
        if (context.get(Nested.class) != null) {
            return;
        }
        Declaration declared = declaration(metadata);
        if (declared == null) {
            return;
        }
        //a length the file cannot hold is settled from what the stream already
        //knows, before an image gets spilled to disk on the strength of it
        if (tis.hasLength() && declared.length >= tis.getLength()) {
            return;
        }
        Trailer trailer = locate(tis, declared, context);
        if (trailer == null) {
            return;
        }
        Metadata trailerMetadata = Metadata.newInstance(context);
        //the name has to be set before the parse, and the declaration names the
        //format the file was written with, which detection cannot always tell
        //apart: an MP4 with the isom brand types as quicktime (TIKA-3646), and
        //the MicroVideo format declares nothing at all. Where the two disagree
        //about the kind of file it is, the bytes win.
        boolean fromDetection = declared.mime != null
                && !declared.mime.startsWith(trailer.type.getType() + "/");
        String extension = declared.mime == null ? "" : EmbeddedDocumentUtil
                .getExtensionForMediaType(
                        fromDetection ? trailer.type.toString() : declared.mime);
        trailerMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, NAME + extension);
        if (fromDetection && !extension.isEmpty()) {
            trailerMetadata.set(TikaCoreProperties.RESOURCE_NAME_EXTENSION_INFERRED, true);
        }
        trailerMetadata.set(HttpHeaders.CONTENT_TYPE, trailer.type.toString());
        //the declaration is exact: the trailer runs from there to the end of
        //the file, and a client should not have to read it to learn its size
        trailerMetadata.set(HttpHeaders.CONTENT_LENGTH, Long.toString(declared.length));
        trailerMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.name());
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        if (!extractor.shouldParseEmbedded(trailerMetadata, context)) {
            return;
        }
        context.set(Nested.class, NESTED);
        //an opener rather than a stream: the trailer is a region of a file, so
        //the parse can go back to the start of it without a copy, and it knows
        //its length from the metadata above rather than by spooling for it
        try (TemporaryResources tmp = new TemporaryResources();
                TikaInputStream embedded = TikaInputStream.get(
                        () -> region(trailer.file, trailer.start), tmp, trailerMetadata)) {
            extractor.parseEmbedded(embedded, new EmbeddedContentHandler(xhtml), trailerMetadata,
                    context, true);
        } finally {
            context.set(Nested.class, null);
        }
    }

    /**
     * What the declaration points at: where the bytes start and what they turn
     * out to be, or null when they are not there, are not recognized, or
     * cannot be read. An image that parsed is not failed over a trailer that
     * is out of reach.
     */
    private static Trailer locate(TikaInputStream tis, Declaration declared,
                                  ParseContext context) {
        try {
            Path file = tis.getPath();
            long start = Files.size(file) - declared.length;
            if (start <= 0) {
                return null;
            }
            MediaType type = detect(file, start, context);
            if (type == null || MediaType.OCTET_STREAM.equals(type)) {
                return null;
            }
            return new Trailer(file, start, type);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The bytes behind the image: where they start and what they are.
     */
    private static final class Trailer {
        final Path file;
        final long start;
        final MediaType type;

        Trailer(Path file, long start, MediaType type) {
            this.file = file;
            this.start = start;
            this.type = type;
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
        final String mime;

        Declaration(long length, String mime) {
            this.length = length;
            this.mime = mime;
        }
    }

    /**
     * The video item's own length, which is what separates it from the end of
     * the file: the format has it last and lets nothing follow it, in Ultra HDR
     * files as well, where the gain map comes before it.
     */
    private static Declaration directoryDeclaration(Metadata metadata) {
        for (int i = 1; i <= MAX_ITEMS; i++) {
            String item = DIRECTORY + i + ITEM;
            String semantic = metadata.get(item + "Item:Semantic");
            if (semantic == null) {
                return null;
            }
            if (MOTION_PHOTO.equals(semantic)) {
                long length = positiveLong(metadata.get(item + "Item:Length"));
                return length > 0
                        ? new Declaration(length, metadata.get(item + "Item:Mime"))
                        : null;
            }
        }
        return null;
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
}
