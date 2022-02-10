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

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
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
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.xmp.JempboxExtractor;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for the Adobe Photoshop PSD File Format.
 * <p/>
 * Documentation on the file format is available from
 * http://www.adobe.com/devnet-apps/photoshop/fileformatashtml/PhotoshopFileFormats.htm
 * <p>
 * An MIT-licensed python parser with test files is:
 * https://github.com/psd-tools/psd-tools
 */
public class PSDParser extends AbstractParser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 883387734607994914L;

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Collections.singletonList(MediaType.image("vnd.adobe.photoshop"))));

    private static final int MAX_DATA_LENGTH_BYTES = 10_000_000;
    private static final int MAX_BLOCKS = 10000;

    private int maxDataLengthBytes = MAX_DATA_LENGTH_BYTES;

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        // Check for the magic header signature
        byte[] signature = new byte[4];
        IOUtils.readFully(stream, signature);
        if (signature[0] == (byte) '8' && signature[1] == (byte) 'B' &&
                signature[2] == (byte) 'P' && signature[3] == (byte) 'S') {
            // Good, signature found
        } else {
            throw new TikaException("PSD/PSB magic signature invalid");
        }

        // Check the version
        int version = EndianUtils.readUShortBE(stream);
        if (version == 1 || version == 2) {
            // Good, we support these two
        } else {
            throw new TikaException("Invalid PSD/PSB version " + version);
        }

        // Skip the reserved block
        IOUtils.readFully(stream, new byte[6]);

        // Number of channels in the image
        int numChannels = EndianUtils.readUShortBE(stream);
        // TODO Identify a suitable metadata key for this

        // Width and Height
        int height = EndianUtils.readIntBE(stream);
        int width = EndianUtils.readIntBE(stream);
        metadata.set(TIFF.IMAGE_LENGTH, height);
        metadata.set(TIFF.IMAGE_WIDTH, width);

        // Depth (bits per channel)
        int depth = EndianUtils.readUShortBE(stream);
        metadata.set(TIFF.BITS_PER_SAMPLE, Integer.toString(depth));

        // Colour mode, eg Bitmap or RGB
        int colorMode = EndianUtils.readUShortBE(stream);
        if (colorMode < Photoshop._COLOR_MODE_CHOICES_INDEXED.length) {
            metadata.set(Photoshop.COLOR_MODE, Photoshop._COLOR_MODE_CHOICES_INDEXED[colorMode]);
        }

        // Next is the Color Mode section
        // We don't care about this bit
        long colorModeSectionSize = EndianUtils.readIntBE(stream);
        IOUtils.skipFully(stream, colorModeSectionSize);

        // Next is the Image Resources section
        // Check for certain interesting keys here
        long imageResourcesSectionSize = EndianUtils.readIntBE(stream);
        long read = 0;
        //if something is corrupt about this number, prevent an
        //infinite loop by only reading 10000 blocks
        int blocks = 0;
        while (read < imageResourcesSectionSize && blocks < MAX_BLOCKS) {
            ResourceBlock rb = new ResourceBlock(stream, maxDataLengthBytes);
            if (rb.totalLength <= 0) {
                //break;
            }
            read += rb.totalLength;

            // Is it one we can do something useful with?
            if (rb.id == ResourceBlock.ID_CAPTION) {
                metadata.add(TikaCoreProperties.DESCRIPTION, rb.getDataAsString());
            } else if (rb.id == ResourceBlock.ID_EXIF_1) {
                // TODO Parse the EXIF info via ImageMetadataExtractor
            } else if (rb.id == ResourceBlock.ID_EXIF_3) {
                // TODO Parse the EXIF info via ImageMetadataExtractor
            } else if (rb.id == ResourceBlock.ID_XMP) {
                //if there are multiple xmps in a file, this will
                //overwrite the data from the earlier xmp
                JempboxExtractor ex = new JempboxExtractor(metadata);
                ex.parse(new ByteArrayInputStream(rb.data));
            }
            blocks++;
        }

        // Next is the Layer and Mask Info
        // Finally we have Image Data
        // We can't do anything with these parts

        // We don't have any helpful text, sorry...
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
    }

    @Field
    public void setMaxDataLengthBytes(int maxDataLengthBytes) {
        this.maxDataLengthBytes = maxDataLengthBytes;
    }

    private static class ResourceBlock {
        private static final long SIGNATURE = 0x3842494d; // 8BIM
        private static final int ID_CAPTION = 0x03F0;
        private static final int ID_EXIF_1 = 0x0422;
        private static final int ID_EXIF_3 = 0x0423;
        private static final int ID_XMP = 0x0424;
        //TODO
        private static final int ID_URL = 0x040B;
        private static final int ID_AUTO_SAVE_FILE_PATH = 0x043E;
        private static final int ID_THUMBNAIL_RESOURCE = 0x040C;
        static int counter = 0;
        private final int maxDataLengthBytes;
        private int id;
        private String name;
        private byte[] data;
        private int totalLength;

        private ResourceBlock(InputStream stream, int maxDataLengthBytes)
                throws IOException, TikaException {
            this.maxDataLengthBytes = maxDataLengthBytes;
            counter++;
            // Verify the signature
            long sig = EndianUtils.readIntBE(stream);
            if (sig != SIGNATURE) {
                throw new TikaException(
                        "Invalid Image Resource Block Signature Found, got " + sig + " 0x" +
                                Long.toHexString(sig) + " but the spec defines " + SIGNATURE);
            }

            // Read the block
            id = EndianUtils.readUShortBE(stream);

            StringBuffer nameB = new StringBuffer();
            int nameLen = 0;
            while (true) {
                int v = stream.read();
                if (v < 0) {
                    throw new EOFException();
                }
                nameLen++;

                if (v == 0) {
                    // The name length is padded to be even
                    if (nameLen % 2 == 1) {
                        stream.read();
                        nameLen++;
                    }
                    break;
                } else {
                    nameB.append((char) v);
                }
                name = nameB.toString();
            }

            int dataLen = EndianUtils.readIntBE(stream);
            if (dataLen < 0) {
                throw new TikaException("data length must be >= 0: " + dataLen);
            }
            if (dataLen % 2 == 1) {
                // Data Length is even padded
                dataLen = dataLen + 1;
            }
            //protect against overflow
            if (Integer.MAX_VALUE - dataLen < nameLen + 10) {
                throw new TikaException("data length is too long:" + dataLen);
            }
            totalLength = 4 + 2 + nameLen + 4 + dataLen;
            // Do we have use for the data segment?
            if (captureData(id)) {
                if (dataLen > maxDataLengthBytes) {
                    throw new TikaMemoryLimitException(
                            "data length must be < " + maxDataLengthBytes + ": " + dataLen);
                }
                data = new byte[dataLen];
                IOUtils.readFully(stream, data);
            } else {
                data = new byte[0];
                IOUtils.skipFully(stream, dataLen);
            }
        }

        /**
         * To save memory, only capture the data
         * section of resource blocks we process
         */
        private static boolean captureData(int id) {
            switch (id) {
                case ID_CAPTION:
                case ID_EXIF_1:
                case ID_EXIF_3:
                case ID_XMP:
                    return true;
            }
            return false;
        }

        private String getDataAsString() {
            // Will be null padded
            return new String(data, 0, data.length - 1, US_ASCII);
        }
    }
}
