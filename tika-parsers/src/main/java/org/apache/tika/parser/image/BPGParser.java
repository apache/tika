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
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser for the Better Portable Graphics )BPG) File Format.
 * 
 * Documentation on the file format is available from
 * http://bellard.org/bpg/bpg_spec.txt
 */
public class BPGParser extends AbstractParser {
    private static final long serialVersionUID = -161736541253892772L;
    
    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                MediaType.image("x-bpg"), MediaType.image("bpg"))));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }
    
    protected int EXTENSION_TAG_EXIF = 1;
    protected int EXTENSION_TAG_ICC_PROFILE = 2;
    protected int EXTENSION_TAG_XMP = 3;
    protected int EXTENSION_TAG_THUMBNAIL = 4;

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // Check for the magic header signature
        byte[] signature = new byte[4];
        IOUtils.readFully(stream, signature);
        if(signature[0] == (byte)'B' && signature[1] == (byte)'P' &&
           signature[2] == (byte)'G' && signature[3] == (byte)0xfb) {
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
        boolean hasAlphaPlane = (pdf & 0x8) == 0x8;
        // TODO Identify a suitable metadata key for this
        
        // Bit depth minus 8
        int bitDepth = (pdf >> 4) + 8;
        metadata.set(TIFF.BITS_PER_SAMPLE, Integer.toString(bitDepth));
        
        // Grab and decode the second byte
        int cer = stream.read();
        
        // Colour Space: YCbCr / RGB / YCgCo / YCbCrK / CMYK
        int colourSpace = cer & 0x7;
        // TODO Identify a suitable metadata key for this
        
        // Are there extensions or not?
        boolean hasExtensions = (cer & 0x8) == 0x8;
        
        // Width and height next
        int width  = (int)EndianUtils.readUE7(stream);
        int height = (int)EndianUtils.readUE7(stream);
        metadata.set(TIFF.IMAGE_LENGTH, height);
        metadata.set(TIFF.IMAGE_WIDTH, width);
        
        // Picture Data length
        EndianUtils.readUE7(stream);
        
        // Extension Data Length, if extensions present
        long extensionDataLength = 0;
        if (hasExtensions)
            extensionDataLength = EndianUtils.readUE7(stream);
        
        // Alpha Data Length, if alpha used
        long alphaDataLength = 0;
        if (hasAlphaPlane)
            alphaDataLength = EndianUtils.readUE7(stream);
        
        // Extension Data
        
        // HEVC Header + Data
        // Alpha HEVC Header + Data
        // We can't do anything with these parts

        // TODO Update from here on
        
        // Next is the Image Resources section
        // Check for certain interesting keys here
/*
        long imageResourcesSectionSize = EndianUtils.readIntBE(stream);
        long read = 0;
        while(read < imageResourcesSectionSize) {
           ResourceBlock rb = new ResourceBlock(stream);
           read += rb.totalLength;
           
           // Is it one we can do something useful with?
           if(rb.id == ResourceBlock.ID_CAPTION) {
              metadata.add(TikaCoreProperties.DESCRIPTION, rb.getDataAsString()); 
           } else if(rb.id == ResourceBlock.ID_EXIF_1) {
              // TODO Parse the EXIF info
           } else if(rb.id == ResourceBlock.ID_EXIF_3) {
              // TODO Parse the EXIF info
           } else if(rb.id == ResourceBlock.ID_XMP) {
              // TODO Parse the XMP info
           }
        }
*/
        
        
        // We don't have any helpful text, sorry...
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
    }
}
