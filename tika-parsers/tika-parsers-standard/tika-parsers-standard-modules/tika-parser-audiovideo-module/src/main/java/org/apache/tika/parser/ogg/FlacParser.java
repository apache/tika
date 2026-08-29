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

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gagravarr.flac.FlacFile;
import org.gagravarr.flac.FlacInfo;
import org.gagravarr.flac.FlacOggFile;
import org.gagravarr.ogg.OggStreamIdentifier;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for FLAC audio files (both native FLAC and OGG-FLAC).
 */
@TikaComponent
public class FlacParser extends AbstractParser {
    private static final long serialVersionUID = -7546577301474546694L;

    protected static final MediaType NATIVE_FLAC =
            MediaType.parse(OggStreamIdentifier.NATIVE_FLAC.mimetype);
    protected static final MediaType OGG_FLAC =
            MediaType.parse(OggStreamIdentifier.OGG_FLAC.mimetype);

    private static List<MediaType> TYPES = Arrays.asList(NATIVE_FLAC, OGG_FLAC);

    /**
     * The metadata block type of a native FLAC PICTURE block
     */
    private static final int PICTURE_BLOCK_TYPE = 6;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return new HashSet<>(TYPES);
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {
        metadata.set(XMPDM.AUDIO_COMPRESSOR, "FLAC");

        // Spool to a file first: FlacFile.open consumes the stream, but
        //  native FLAC PICTURE blocks are read through a second stream
        //  over the file later on
        Path path = tis.getPath();

        // Open the FLAC file
        FlacFile flac = FlacFile.open(tis);

        // Start
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();

        // Extract the common FLAC info
        extractInfo(metadata, flac.getInfo());
        if (flac instanceof FlacOggFile) {
            FlacOggFile ogg = (FlacOggFile) flac;
            metadata.add(OggAudioParser.CODEC_VERSION,
                    "Flac " + ogg.getFirstPacket().getMajorVersion() +
                            "." + ogg.getFirstPacket().getMinorVersion());
            metadata.set(HttpHeaders.CONTENT_TYPE, OGG_FLAC.toString());
        } else {
            metadata.set(HttpHeaders.CONTENT_TYPE, NATIVE_FLAC.toString());
        }

        // Extract any Vorbis-style comments
        OggAudioParser.extractComments(metadata, xhtml, flac.getTags(), context);

        // Extract any embedded pictures, such as cover art, from native
        //  FLAC PICTURE metadata blocks (Ogg-contained FLAC carries its
        //  pictures in metadata_block_picture comments instead)
        if (!(flac instanceof FlacOggFile)) {
            extractNativePictures(path, xhtml, context);
        }

        // Extract duration if available from header
        FlacInfo info = flac.getInfo();
        if (info.getNumberOfSamples() > 0 && info.getSampleRate() > 0) {
            double duration = (double) info.getNumberOfSamples() / info.getSampleRate();
            OggAudioParser.extractDuration(metadata, xhtml, duration);
        }

        // Finish
        xhtml.endDocument();
        flac.close();
    }

    protected void extractInfo(Metadata metadata, FlacInfo info) throws TikaException {
        metadata.set(XMPDM.AUDIO_SAMPLE_RATE, (int) info.getSampleRate());
        OggAudioParser.extractChannelInfo(metadata, info.getNumChannels());
    }

    /**
     * Walks the metadata blocks of a native FLAC file and sends any
     * PICTURE blocks to the embedded document extractor. Their payload is
     * identical to the metadata_block_picture comments handled by
     * {@link OggAudioParser}. vorbis-java parses these blocks but keeps
     * them without a public accessor, so they are read through a second
     * stream over the spooled file, leaving the main parse untouched.
     * The walk stops at the block flagged as last, at the end of the
     * stream, or at a block that declares more data than is left.
     * TODO: remove this block walk once a vorbis-java release ships
     * FlacFile.getOtherMetadata(), present on their master but unreleased
     * as of 0.8, see https://github.com/Gagravarr/VorbisJava/issues/46
     */
    private static void extractNativePictures(Path path, XHTMLContentHandler xhtml,
            ParseContext context) throws IOException, SAXException {
        List<OggAudioParser.PictureBlock> pictures = new ArrayList<>();
        try (InputStream stream = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] magic = stream.readNBytes(4);
            if (magic.length != 4 || magic[0] != 'f' || magic[1] != 'L'
                    || magic[2] != 'a' || magic[3] != 'C') {
                return;
            }
            boolean lastBlock = false;
            while (!lastBlock) {
                // 1 byte of last-block flag and block type, then a
                //  24 bit BE block length
                byte[] header = stream.readNBytes(4);
                if (header.length != 4) {
                    break;
                }
                lastBlock = (header[0] & 0x80) != 0;
                int blockType = header[0] & 0x7F;
                int blockLength = ((header[1] & 0xFF) << 16) | ((header[2] & 0xFF) << 8)
                        | (header[3] & 0xFF);
                if (blockType == PICTURE_BLOCK_TYPE) {
                    byte[] block = stream.readNBytes(blockLength);
                    if (block.length != blockLength) {
                        break;
                    }
                    OggAudioParser.PictureBlock picture = OggAudioParser.PictureBlock.parse(block);
                    if (picture != null) {
                        pictures.add(picture);
                    }
                } else {
                    try {
                        stream.skipNBytes(blockLength);
                    } catch (EOFException e) {
                        //truncated block, stop the walk
                        break;
                    }
                }
            }
        }
        OggAudioParser.extractPictures(pictures, xhtml, context);
    }
}
