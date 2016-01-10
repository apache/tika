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

import org.apache.poi.util.IOUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.metadata.*;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Parser for the Adobe Photoshop PSD File Format.
 * <p/>
 * Documentation on the file format is available from
 * http://www.adobe.com/devnet-apps/photoshop/fileformatashtml/PhotoshopFileFormats.htm
 */
public class PSDParser extends AbstractParser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 883387734607994914L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                    MediaType.image("vnd.adobe.photoshop"))));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
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
        metadata.set(Photoshop.COLOR_MODE, Photoshop._COLOR_MODE_CHOICES_INDEXED[colorMode]);

        // Next is the Color Mode section
        // We don't care about this bit
        long colorModeSectionSize = EndianUtils.readIntBE(stream);
        stream.skip(colorModeSectionSize);

        // Next is the Image Resources section
        // Check for certain interesting keys here
        long imageResourcesSectionSize = EndianUtils.readIntBE(stream);
        long read = 0;
        while (read < imageResourcesSectionSize) {
            ResourceBlock rb = new ResourceBlock(stream);
            read += rb.totalLength;

            // Is it one we can do something useful with?
            if (rb.id == ResourceBlock.ID_CAPTION) {
                metadata.add(TikaCoreProperties.DESCRIPTION, rb.getDataAsString());
            } else if (rb.id == ResourceBlock.ID_EXIF_1) {
                // TODO Parse the EXIF info via ImageMetadataExtractor
            } else if (rb.id == ResourceBlock.ID_EXIF_3) {
                // TODO Parse the EXIF info via ImageMetadataExtractor
            } else if (rb.id == ResourceBlock.ID_XMP) {
                // TODO Parse the XMP info via ImageMetadataExtractor
            }
        }

        // Using http://www.adobe.com/devnet-apps/photoshop/fileformatashtml/#50577409_pgfId-1031423
        // for information about the Layer and Mask Section

        // This size is not needed so we skip it
        // long layersAndMaskSectionSize = EndianUtils.readIntBE(stream);
        stream.skip(4);

        // Layer info section contains layer names
        long layerInfoSectionSize = EndianUtils.readIntBE(stream);
        // Rounds up to nearest even number of bytes
        layerInfoSectionSize = (layerInfoSectionSize + 1) & ~0x01;
        read = 0;

        // From PSD spec:
        // If [layer count] is a negative number, its absolute
        // value is the number of layers and the first alpha channel contains
        // the transparency data for the merged result.
        long layerCount = Math.abs(EndianUtils.readShortBE(stream));
        read += 2;

        // Holds names of layers
        String[] names = new String[(int)layerCount];
        String[] textFields = new String[(int)layerCount];
        for (int i = 0; i < layerCount; i++) {
            textFields[i] = "";
        }
        int layers = 0;

        short channelCount;
        int tempNumber;
        int stringSize;
        int nameLength;
        int additionalLayerInfoSize;
        long readMarker;
        long extraDataFieldLength;
        long maskAdjustmentLength;
        long layerBlendingLength;
        long textSpan;

        byte[] anyBytes;
        byte[] fourBytes = new byte[4];
        byte[] additionalDataBytes;

        String keyString;

        while (read < layerInfoSectionSize && layers < layerCount) {
            // 16 bytes - object coordinates
            read += stream.skip(16);

            // 2 byes - number of channels in layer (retrieve)
            channelCount = EndianUtils.readShortBE(stream);
            read += 2;

            // 6 * number of channels - channel info
            read += stream.skip(6*channelCount);

            // ----------- skip 12 bytes for this block
            // 4 bytes - blend mode signature
            // 4 bytes - blend mode key
            // 1 byte - opacity
            // 1 byte - clipping
            // 1 byte - flags
            // 1 byte - filler
            // -----------
            read += stream.skip(12);

            // 4 bytes - length of extra data field (retrieve)
            extraDataFieldLength = EndianUtils.readIntBE(stream);
            read += 4;
            readMarker = read;

            // 4 bytes - length of mask/adjustment layer (retrieve and skip)
            maskAdjustmentLength = EndianUtils.readIntBE(stream);
            // 4 bytes - length of layer blending ranges (retrieve and skip)
            layerBlendingLength = EndianUtils.readIntBE(stream);
            read += 8;
            read += stream.skip(maskAdjustmentLength+layerBlendingLength);

            // Get the size of the name string
            stringSize = stream.read();
//            System.out.printf("\n\nstringSize: %d\n", stringSize);
            read += 1;
            // Read name string bytes into array
            anyBytes = new byte[stringSize];
            read += stream.read(anyBytes);
//            System.out.printf("nameBytes: %s\n", new String(nameBytes));
            // Convert the bytes into string
            String layerName = new String(anyBytes, "UTF-8");
//            System.out.printf("layerName: %s\n", layerName);

            // Consume the remainder of the name section
            nameLength = layerName.length() + 1;
            if (nameLength%4 != 0) {
                tempNumber = 4 - nameLength%4;
//                System.out.printf("skipSize: %d", skipSize);
                read += stream.skip(tempNumber);
            }

//            System.out.printf("extraDataFieldLength: %d, read: %d, readMarker: %d\n", extraDataFieldLength, read, readMarker);

            // The additional layer information begins with a 4 byte signature
            read += stream.read(fourBytes);
            keyString = new String(fourBytes, "UTF-8");
//            System.out.println("additionalLayerInfoSignature: "+additionalLayerInfoSignature);
            if (keyString.equals("8BIM")) {
                // Get the key for this info -- 4 bytes
                read += stream.read(fourBytes);
                keyString = new String(fourBytes, "UTF-8");
//                System.out.println("infoKey: "+infoKey);
                if (keyString.equals("TySh")) {
                    // 4 bytes give size of additional layer info
                    additionalLayerInfoSize = EndianUtils.readIntBE(stream);
                    additionalLayerInfoSize = (additionalLayerInfoSize + 1) & ~0x01;
                    read += 4;
//                    System.out.printf("additionalLayerInfoSize: %d\n", additionalLayerInfoSize);


                    // WORKS, kind of **************************
                    additionalDataBytes = new byte[additionalLayerInfoSize];
                    read += stream.read(additionalDataBytes);

                    // Get the index where the four bytes "TEXT" begin.
                    String additionalData = new String(additionalDataBytes, "UTF-8");
                    tempNumber = additionalData.indexOf("TEXT");

//                    byte[] textKey = Arrays.copyOfRange(additionalDataBytes, tempNumber, tempNumber+4);
//                    System.out.printf("textKey: %s\n", new String(textKey));

                    // Isolate the bytes which tell us the size of the unicode string
                    fourBytes = Arrays.copyOfRange(additionalDataBytes, tempNumber+4, tempNumber+8);
//                    System.out.printf("textSize: %d\n", EndianUtils.getIntBE(textSize));

                    // Get the number of bytes the text spans
                    textSpan = EndianUtils.getIntBE(fourBytes)*2;

                    if (textSpan > 0 && textSpan <= Integer.MAX_VALUE ) {

                        byte[] text = Arrays.copyOfRange(additionalDataBytes, tempNumber+8, tempNumber+8+(int)textSpan);
                        //                    System.out.printf("theRest: %s\n", new String(text));

                        textFields[layers] = new String(text, "UNICODE");

                    }
                    // *******************************************

                    // // // //
                    //
                    // Below is the proper code which does not work and
                    // it sits here hoping to be figured out some day
                    //
                    // // // //

//                    byte[] theRest = Arrays.copyOfRange(additionalDataBytes, tempNumber+8, additionalDataBytes.length-1);
//                    System.out.printf("theRest: %s\n", new String(theRest));
//
//                    // Enter-- Additional layer information "Data" section
//
//                    // 2 bytes version
//                    // 6 * 8 bytes transform stuff
//                    // 2 bytes text version ( == 50 for ps 6.0)
//                    // 4 bytes descriptor version ( == 16 for ps 6.0)
//                    read += stream.skip(56);
//
//                    // ****** Text Data ************
//
//
//                    // "Unicode string"
//                    // 4 bytes for number of chars, then 2 bytes per char ?'name from classID'?
//                    tempNumber = EndianUtils.readIntBE(stream);
////                    System.out.printf("lengthOfUnicodeClassID: %d\n", tempNumber);
//                    read += 4;
//                    byte[] tmpBytes = new byte[tempNumber*2];
//                    read += stream.read(tmpBytes);
////                    System.out.printf("nameFromClassID: %s\n", new String(tmpBytes));
////                    read += stream.skip(tempNumber*2);
//
//                    // 4 bytes for length of...
//                    // ^^ if zero, 4 byte classID
//                    // ^^ not zero, consume length
//                    tempNumber = EndianUtils.readIntBE(stream);
////                    System.out.printf("lengthOfClassID: %d\n", tempNumber);
//                    read += 4;
//                    if (tempNumber == 0) {
//                        read += stream.skip(4);
//                    } else {
//                        read += stream.skip(tempNumber);
//                    }
//
//                    // 4 bytes for number of items in descriptor
//                    int numberOfItems = EndianUtils.readIntBE(stream);
//                    read += 4;
////                    System.out.printf("numberOfItems: %d\n", numberOfItems);
//                    int items = 0;
//                    assert(numberOfItems<50);
//                    while (items < numberOfItems) {
//
//                        // 4 bytes length of item -- if not zero, is not key & consume
//                        // No length is given for key unless key is supported,
//                        // must skip all if first is not "TEXT"
//                        // TODO: Support all the keys even though only one is something we want
//                        tempNumber = EndianUtils.readIntBE(stream);
////                        System.out.printf("lengthOfItem: %d\n", tempNumber);
//                        read += 4;
//                        if (tempNumber != 0) {
//                            read += stream.skip(tempNumber);
//                            items++;
//                            continue;
//                        }
//
//                        // 4 bytes for item key
//                        byte[] itemKeyBytes = new byte[8];
//                        read += stream.read(itemKeyBytes);
//                        String itemKey = new String(itemKeyBytes, "UTF-8");
////                        System.out.println("itemKey: "+itemKey);
//                        // match key to "TEXT"
                          // Note: In practice it appears that the key is "Txt TEXT" not "TEXT"-- this is undocumented
//                        if (itemKey.equals("Txt TEXT")) {
//                            // 4 bytes for number of characters in the string
//                            tempNumber = EndianUtils.readIntBE(stream);
//                            read += 4;
//                            // unicode string, 2 bytes per char
//                            byte[] textBoxBytes = new byte[tempNumber*2];
//                            read += stream.read(textBoxBytes);
//                            textFields[layers] = new String(textBoxBytes, "UNICODE");
////                            System.out.printf("text field: %s\n", textFields[layers]);
//                        } else {
//                            // TODO: See the previous todo, the one about supporting keys
////                            break;
//                            break;
//                        }
//                        items++;
//                    }
//
//
//                    // ******************************
//
//                    // 2 bytes warp version
//                    // 4 bytes descriptor version
//                    // variable for descriptor -- 4 bytes to get size, then consume size
//                    // 4 * 8 bytes for something

                }
            }


            // Consume unread data so the next iteration wont be fucked
//            System.out.printf("extraDataFieldLength: %d, read: %d, readMarker: %d", extraDataFieldLength, read, readMarker);
            if (extraDataFieldLength - (read-readMarker) > 0) {
                anyBytes = new byte[(int) (extraDataFieldLength - (read - readMarker))];
                read += stream.read(anyBytes);
            }
//            System.out.printf("extraBytes:\nlength: %d\ncontent: %s\n", extraBytes.length, new String(extraBytes));

            // Consume the rest of the section
//            read += stream.skip(extraDataFieldLength - (read-readMarker));

            names[layers] = layerName;
            layers++;

        }
//        System.out.printf("read: %d, total: %d\n", read, layerInfoSectionSize);
//        System.out.println("Done with PSD Layers");
        String csvNames = "";
        for (String name : names) {
            csvNames += (name + ",");
        }
        metadata.set(Photoshop.LAYER_NAMES, csvNames);
        // Finally we have Image Data
        // We can't do anything with these parts

        // Add the text layer bodies as the body of text
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.startElement("p");
        for (String text : textFields) {
            if (text.length() > 0) {
                xhtml.characters(text.toCharArray(), 0, text.toCharArray().length);
                xhtml.newline();
            }
        }
        xhtml.endElement("p");
        xhtml.endDocument();
    }

    private static class ResourceBlock {
        private static final long SIGNATURE = 0x3842494d; // 8BIM
        private static final int ID_CAPTION = 0x03F0;
        private static final int ID_URL = 0x040B;
        private static final int ID_EXIF_1 = 0x0422;
        private static final int ID_EXIF_3 = 0x0423;
        private static final int ID_XMP = 0x0424;

        private int id;
        private String name;
        private byte[] data;
        private int totalLength;

        private ResourceBlock(InputStream stream) throws IOException, TikaException {
            // Verify the signature
            long sig = EndianUtils.readIntBE(stream);
            if (sig != SIGNATURE) {
                throw new TikaException("Invalid Image Resource Block Signature Found, got " +
                        sig + " 0x" + Long.toHexString(sig) + " but the spec defines " + SIGNATURE);
            }

            // Read the block
            id = EndianUtils.readUShortBE(stream);

            StringBuffer nameB = new StringBuffer();
            int nameLen = 0;
            while (true) {
                int v = stream.read();
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
            if (dataLen % 2 == 1) {
                // Data Length is even padded
                dataLen = dataLen + 1;
            }
            totalLength = 4 + 2 + nameLen + 4 + dataLen;

            data = new byte[dataLen];
            IOUtils.readFully(stream, data);
        }

        private String getDataAsString() {
            // Will be null padded
            return new String(data, 0, data.length - 1, US_ASCII);
        }
    }
}
