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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Photoshop;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * Parser for the Better Portable Graphics (BPG) File Format.
 * <p/>
 * Documentation on the file format is available from
 * http://bellard.org/bpg/bpg_spec.txt
 */
public class BPGParser extends AbstractImageParser {
    protected static final int EXTENSION_TAG_EXIF = 1;
    protected static final int EXTENSION_TAG_ICC_PROFILE = 2;
    protected static final int EXTENSION_TAG_XMP = 3;
    protected static final int EXTENSION_TAG_THUMBNAIL = 4;

    //50 MB -- throw TikaMemoryLimitException if xmp or exif is allegedly longer than this
    private static final int DEFAULT_MAX_RECORD_LENGTH = 50 * 1024 * 1024;

    private static final long serialVersionUID = -161736541253892772L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(
                    Arrays.asList(MediaType.image("x-bpg"), MediaType.image("bpg"))));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    private int maxRecordLength = DEFAULT_MAX_RECORD_LENGTH;

    @Override
    void extractMetadata(InputStream stream, ContentHandler contentHandler, Metadata metadata,
                         ParseContext parseContext)
            throws IOException, SAXException, TikaException {

        // Check for the magic header signature
        byte[] signature = new byte[4];
        IOUtils.readFully(stream, signature);
        if (signature[0] == (byte) 'B' && signature[1] == (byte) 'P' &&
                signature[2] == (byte) 'G' && signature[3] == (byte) 0xfb) {
            // Good, signature found
        } else {
            throw new TikaException("BPG magic signature invalid");
        }

        // Grab and decode the first byte
        int pdf = stream.read();

        // Pixel format: Greyscale / 4:2:0 / 4:2:2 / 4:4:4
        int pixelFormat = pdf & 0x7;
        // TODO Identify a suitable metadata key for this

        // Is there an alpha plane as well as a colour plane?
        boolean hasAlphaPlane1 = (pdf & 0x8) == 0x8;
        // TODO Identify a suitable metadata key for this+hasAlphaPlane2

        // Bit depth minus 8
        int bitDepth = (pdf >> 4) + 8;
        metadata.set(TIFF.BITS_PER_SAMPLE, Integer.toString(bitDepth));

        // Grab and decode the second byte
        int cer = stream.read();

        // Colour Space: YCbCr / RGB / YCgCo / YCbCrK / CMYK
        int colourSpace = cer & 0x15;
        switch (colourSpace) {
            case 0:
                metadata.set(Photoshop.COLOR_MODE, "YCbCr Colour");
                break;
            case 1:
                metadata.set(Photoshop.COLOR_MODE, "RGB Colour");
                break;
            case 2:
                metadata.set(Photoshop.COLOR_MODE, "YCgCo Colour");
                break;
            case 3:
                metadata.set(Photoshop.COLOR_MODE, "YCbCrK Colour");
                break;
            case 4:
                metadata.set(Photoshop.COLOR_MODE, "CMYK Colour");
                break;
        }

        // Are there extensions or not?
        boolean hasExtensions = (cer & 16) == 16;

        // Is the Alpha Plane 2 flag set?
        boolean hasAlphaPlane2 = (cer & 32) == 32;

        // cer then holds 2 more booleans - limited range, reserved

        // Width and height next
        int width = (int) EndianUtils.readUE7(stream);
        int height = (int) EndianUtils.readUE7(stream);
        metadata.set(TIFF.IMAGE_LENGTH, height);
        metadata.set(TIFF.IMAGE_WIDTH, width);

        // Picture Data length
        EndianUtils.readUE7(stream);

        // Extension Data Length, if extensions present
        long extensionDataLength = 0;
        if (hasExtensions) {
            extensionDataLength = EndianUtils.readUE7(stream);
        }

        // Alpha Data Length, if alpha used
        long alphaDataLength = 0;
        if (hasAlphaPlane1 || hasAlphaPlane2) {
            alphaDataLength = EndianUtils.readUE7(stream);
        }

        // Extension Data
        if (hasExtensions) {
            long extensionsDataSeen = 0;
            ImageMetadataExtractor metadataExtractor = new ImageMetadataExtractor(metadata);

            while (extensionsDataSeen < extensionDataLength) {
                int extensionType = (int) EndianUtils.readUE7(stream);
                int extensionLength = (int) EndianUtils.readUE7(stream);
                if (extensionLength > maxRecordLength) {
                    throw new TikaMemoryLimitException("extension length (" +
                            extensionLength + " bytes) is greater than 'maxRecordLength' (" +
                            maxRecordLength + " bytes).  If this file is not corrupt, " +
                            "consider bumping the maxRecordLength via tika-config.xml");
                }
                switch (extensionType) {
                    case EXTENSION_TAG_EXIF:
                        metadataExtractor.parseRawExif(stream, extensionLength, true);
                        break;
                    case EXTENSION_TAG_XMP:
                        handleXMP(stream, extensionLength, metadataExtractor);
                        break;
                    default:
                        IOUtils.skipFully(stream, extensionLength);
                }
                extensionsDataSeen += extensionLength;
            }
        }
        // HEVC Header + Data
        // Alpha HEVC Header + Data
        // We can't do anything with these parts
    }

    @Field
    public void setMaxRecordLength(int maxRecordLength) {
        this.maxRecordLength = maxRecordLength;
    }

    protected void handleXMP(InputStream stream, int xmpLength, ImageMetadataExtractor extractor)
            throws IOException, TikaException, SAXException {
        if (xmpLength < 0) {
            throw new TikaException("xmp length must be >= 0");
        }
        if (xmpLength > maxRecordLength) {
            throw new TikaMemoryLimitException("xmplength (" + xmpLength + " bytes) is larger than maxXMPLength (" +
                    maxRecordLength + "). Consider setting maxXMPLength to a greater value for " +
                    "this parser via" +
                    " tika-config.xml if this file is not corrupt.");
        }
        byte[] xmp = new byte[xmpLength];
        IOUtils.readFully(stream, xmp);
        extractor.parseRawXMP(xmp);
    }
}
