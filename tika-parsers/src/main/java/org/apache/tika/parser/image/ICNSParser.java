/*
 * Copyright 2016 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import org.apache.poi.util.IOUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.AbstractParser;
import static org.apache.tika.parser.image.ICNSType.findIconType;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A basic parser class for Apple ICNS icon files
 */
public class ICNSParser extends AbstractParser {
    private static final long serialVersionUID = 922010233654248327L;

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.image("icns"));
    public static final String ICNS_MIME_TYPE = "image/icns";

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        byte[] header = new byte[4];
        IOUtils.readFully(stream, header, 0, 4); // Extract magic byte
        if (header[0] == (byte) 'i' && header[1] == (byte) 'c'
                && header[2] == (byte) 'n' && header[3] == (byte) 's') {
            // Good, signature found
        } else {
            throw new TikaException("ICNS magic signature invalid");
        }
        IOUtils.readFully(stream, header, 0, 4); //Extract image size/length of bytes in file
        int image_length = java.nio.ByteBuffer.wrap(header).getInt();
        byte[] full_file = new byte[image_length];
        IOUtils.readFully(stream, full_file);
        ArrayList<ICNSType> icons = new ArrayList<>();
        ArrayList<ICNSType> icon_masks = new ArrayList<>();
        byte[] tempByteArray = new byte[4];
        for (int offset = 0; offset < image_length - 8;) {
            //Read the ResType/OSTYpe identifier for sub-icon
            tempByteArray[0] = full_file[offset];
            tempByteArray[1] = full_file[offset + 1];
            tempByteArray[2] = full_file[offset + 2];
            tempByteArray[3] = full_file[offset + 3];
            ICNSType icnstype = findIconType(tempByteArray);

            if (icnstype == null) {
                //exit out of loop
                //No more icons left
                offset = image_length - 8;
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
            offset = offset + icon_length;
        }
        String icon_details = "", iconmask_details = "", bitsPerPixel,dimensions;
        for (ICNSType icon : icons) {
             bitsPerPixel = (icon.getBitsPerPixel()!=0)?icon.getBitsPerPixel() + " bpp":"JPEG 2000 or PNG format";
             dimensions = (!icon.hasRetinaDisplay())?(icon.getHeight() + "x" + icon.getWidth()):(icon.getHeight() + "x" + icon.getWidth() + "@2X");
             icon_details = icon_details + ", " + dimensions + " (" + bitsPerPixel + ")";
        }
        for (ICNSType icon : icon_masks) {
            iconmask_details = iconmask_details + ", " + icon.getHeight() + "x" + icon.getWidth() + " (" + icon.getBitsPerPixel() + " bpp" + ")";
        }

        metadata.set(Metadata.CONTENT_TYPE, ICNS_MIME_TYPE);
        if (!icon_details.equals("")) {
            metadata.set("Icon count", String.valueOf(icons.size()));
            icon_details = icon_details.substring(2);
            metadata.set("Icon details", icon_details);
        }
        if (!iconmask_details.equals("")) {
            metadata.set("Masked icon count", String.valueOf(icon_masks.size()));
            iconmask_details = iconmask_details.substring(2);
            metadata.set("Masked icon details", iconmask_details);
        }
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
    }
}
