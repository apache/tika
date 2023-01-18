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

import static org.apache.tika.parser.image.ICNSType.findIconType;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * A basic parser class for Apple ICNS icon files
 */
public class ICNSParser extends AbstractParser {
    public static final String ICNS_MIME_TYPE = "image/icns";
    private static final long serialVersionUID = 922010233654248327L;
    private static final long MAX_IMAGE_LENGTH_BYTES = 10485760;// 10MB
    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.image("icns"));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        byte[] header = new byte[4];
        IOUtils.read(stream, header, 0, 4); // Extract magic byte
        if (header[0] == (byte) 'i' && header[1] == (byte) 'c' && header[2] == (byte) 'n' &&
                header[3] == (byte) 's') {
            // Good, signature found
        } else {
            throw new TikaException("ICNS magic signature invalid");
        }
        IOUtils.read(stream, header, 0, 4); //Extract image size/length of bytes in file
        int image_length = java.nio.ByteBuffer.wrap(header).getInt();
        image_length -= 8;//for the bytes read so far
        if (image_length > MAX_IMAGE_LENGTH_BYTES) {
            throw new TikaMemoryLimitException(image_length, MAX_IMAGE_LENGTH_BYTES);
        } else if (image_length < 0) {
            throw new TikaException("image length must be >= 0");
        }

        byte[] full_file = new byte[image_length];
        IOUtils.readFully(stream, full_file);
        ArrayList<ICNSType> icons = new ArrayList<>();
        ArrayList<ICNSType> icon_masks = new ArrayList<>();
        byte[] tempByteArray = new byte[4];
        for (int offset = 0; offset < image_length - 8; ) {
            //Read the ResType/OSTYpe identifier for sub-icon
            tempByteArray[0] = full_file[offset];
            tempByteArray[1] = full_file[offset + 1];
            tempByteArray[2] = full_file[offset + 2];
            tempByteArray[3] = full_file[offset + 3];
            ICNSType icnstype = findIconType(tempByteArray);

            if (icnstype == null) {
                //exit out of loop
                //No more icons left
                break;
            } else if (icnstype.hasMask() == true) {
                icon_masks.add(findIconType(tempByteArray));
            } else {
                icons.add(findIconType(tempByteArray));

            }
            //Read the sub-icon length
            tempByteArray[0] = full_file[offset + 4];
            tempByteArray[1] = full_file[offset + 5];
            tempByteArray[2] = full_file[offset + 6];
            tempByteArray[3] = full_file[offset + 7];
            int icon_length = java.nio.ByteBuffer.wrap(tempByteArray).getInt();
            if (icon_length <= 0) {
                break;
            }
            offset = offset + icon_length;
        }
        StringBuilder icon_details = new StringBuilder();
        StringBuilder iconmask_details = new StringBuilder();
        String bitsPerPixel;
        String dimensions;
        for (ICNSType icon : icons) {
            bitsPerPixel = (icon.getBitsPerPixel() != 0) ? icon.getBitsPerPixel() + " bpp" :
                    "JPEG 2000 or PNG format";
            dimensions = (!icon.hasRetinaDisplay()) ? (icon.getHeight() + "x" + icon.getWidth()) :
                    (icon.getHeight() + "x" + icon.getWidth() + "@2X");
            icon_details.append(", ").append(dimensions).append(" (").append(bitsPerPixel).append(")");
        }
        for (ICNSType icon : icon_masks) {
            iconmask_details
                    .append(", ")
                    .append(icon.getHeight())
                    .append("x")
                    .append(icon.getWidth())
                    .append(" (")
                    .append(icon.getBitsPerPixel())
                    .append(" bpp")
                    .append(")");
        }

        metadata.set(Metadata.CONTENT_TYPE, ICNS_MIME_TYPE);
        if (!icon_details.toString().equals("")) {
            metadata.set("Icon count", String.valueOf(icons.size()));
            icon_details = new StringBuilder(icon_details.substring(2));
            metadata.set("Icon details", icon_details.toString());
        }
        if (!iconmask_details.toString().equals("")) {
            metadata.set("Masked icon count", String.valueOf(icon_masks.size()));
            iconmask_details = new StringBuilder(iconmask_details.substring(2));
            metadata.set("Masked icon details", iconmask_details.toString());
        }
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
    }
}
