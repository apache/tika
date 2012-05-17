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
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.util.IOUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser for the Adobe Photoshop PSD File Format.
 * 
 * Documentation on the file format is available from
 * http://www.adobe.com/devnet-apps/photoshop/fileformatashtml/PhotoshopFileFormats.htm
 */
public class PSDParser extends AbstractParser {

    /** Serial version UID */
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
        if(signature[0] == (byte)'8' && signature[1] == (byte)'B' &&
           signature[2] == (byte)'P' && signature[3] == (byte)'S') {
           // Good, signature found
        } else {
           throw new TikaException("PSD/PSB magic signature invalid");
        }
        
        // Check the version
        int version = EndianUtils.readUShortBE(stream);
        if(version == 1 || version == 2) {
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
        
        // Colour mode
        // Bitmap = 0; Grayscale = 1; Indexed = 2; RGB = 3; CMYK = 4; Multichannel = 7; Duotone = 8; Lab = 9.
        int colorMode = EndianUtils.readUShortBE(stream);
        // TODO Identify a suitable metadata key for this
        
        // Next is the Color Mode section
        // We don't care about this bit
        long colorModeSectionSize = EndianUtils.readIntBE(stream);
        stream.skip(colorModeSectionSize);

        // Next is the Image Resources section
        // Check for certain interesting keys here
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
        
        // Next is the Layer and Mask Info
        // Finally we have Image Data
        // We can't do anything with these parts
        
        // We don't have any helpful text, sorry...
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
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
          if(sig != SIGNATURE) {
             throw new TikaException("Invalid Image Resource Block Signature Found, got " +
                   sig + " 0x" + Long.toHexString(sig) + " but the spec defines " + SIGNATURE);
          }
          
          // Read the block
          id = EndianUtils.readUShortBE(stream);
          
          StringBuffer nameB = new StringBuffer();
          int nameLen = 0;
          while(true) {
             int v = stream.read();
             nameLen++;
             
             if(v == 0) {
                // Even size, may be padded
                if(nameLen % 2 == 1) {
                   stream.read();
                   nameLen++;
                }
                break;
             } else {
                nameB.append((char)v);
             }
             name = nameB.toString();
          }
          
          int dataLen = EndianUtils.readIntBE(stream);
          totalLength = 4 + 2 + nameLen + 4 + dataLen;
          
          data = new byte[dataLen];
          IOUtils.readFully(stream, data);
       }
       
       private String getDataAsString() {
          // Will be null padded
          try {
             return new String(data, 0, data.length-1, "ASCII");
          } catch(UnsupportedEncodingException e) {
             throw new RuntimeException("Something is very broken in your JVM!");
          }
       }
    }
}
