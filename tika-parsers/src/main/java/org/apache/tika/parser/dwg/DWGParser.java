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
package org.apache.tika.parser.dwg;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.poi.util.IOUtils;
import org.apache.poi.util.LittleEndian;
import org.apache.poi.util.StringUtil;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * DWG (CAD Drawing) parser. This is a very basic parser, which just
 *  looks for bits of the headers.
 * Note that we use Apache POI for various parts of the processing, as
 *  lots of the low level string/int/short concepts are the same.
 */
public class DWGParser implements Parser {

    private static MediaType TYPE = MediaType.image("vnd.dwg");

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return Collections.singleton(TYPE);
    }

    /** The order of the fields in the header */
    private static final String[] HEADER_PROPERTIES_ENTRIES = {
        Metadata.TITLE, 
        Metadata.SUBJECT,
        Metadata.AUTHOR,
        Metadata.KEYWORDS,
        Metadata.COMMENTS,
        Metadata.LAST_AUTHOR,
        null, // Unknown?
        Metadata.RELATION, // Hyperlink
    };
    /** For the 2000 file, they're indexed */
    private static final String[] HEADER_2000_PROPERTIES_ENTRIES = {
       null, 
       Metadata.RELATION, // 0x01
       Metadata.TITLE,    // 0x02
       Metadata.SUBJECT,  // 0x03
       Metadata.AUTHOR,   // 0x04
       null,
       Metadata.COMMENTS, // 0x06 
       Metadata.KEYWORDS, // 0x07
       Metadata.LAST_AUTHOR, // 0x08
   };
   private static final String HEADER_2000_PROPERTIES_MARKER_STR =
      "DWGPROPS COOKIE";
   private static final byte[] HEADER_2000_PROPERTIES_MARKER =
      new byte[HEADER_2000_PROPERTIES_MARKER_STR.length()];
   static {
      StringUtil.putCompressedUnicode(
            HEADER_2000_PROPERTIES_MARKER_STR,
            HEADER_2000_PROPERTIES_MARKER, 0
      );
   }
    
    /** 
     * How far to skip after the last standard property, before
     *  we find any custom properties that might be there.
     */
    private static final int CUSTOM_PROPERTIES_SKIP = 20; 

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {
        // First up, which version of the format are we handling?
        byte[] header = new byte[128];
        IOUtils.readFully(stream, header);
        String version = new String(header, 0, 6, "US-ASCII");

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        if (version.equals("AC1015")) {
            metadata.set(Metadata.CONTENT_TYPE, TYPE.toString());
            if(skipTo2000PropertyInfoSection(stream, header)){
                get2000Props(stream,metadata,xhtml);
            }
        } else if (version.equals("AC1018")) {
            metadata.set(Metadata.CONTENT_TYPE, TYPE.toString());
            if(skipToPropertyInfoSection(stream, header)){
                get2004Props(stream,metadata,xhtml);
            }
        } else if (version.equals("AC1021") || version.equals("AC1024")) {
            metadata.set(Metadata.CONTENT_TYPE, TYPE.toString());
            skipToPropertyInfoSection(stream, header);
            get2007and2010Props(stream,metadata,xhtml);
        } else {
            throw new TikaException(
                    "Unsupported AutoCAD drawing version: " + version);
        }

        xhtml.endDocument();
    }

    /**
     * Stored as US-ASCII
     */
    private void get2004Props(
            InputStream stream, Metadata metadata, XHTMLContentHandler xhtml)
            throws IOException, SAXException {
       // Standard properties
        for (int i = 0; i < HEADER_PROPERTIES_ENTRIES.length; i++) {
            String headerValue = read2004String(stream);
            handleHeader(i, headerValue, metadata, xhtml);
        }
        
        // Custom properties
        int customCount = skipToCustomProperties(stream);
        for (int i = 0; i < customCount; i++) {
           String propName = read2004String(stream);
           String propValue = read2004String(stream);
           if(propName.length() > 0 && propValue.length() > 0) {
              metadata.add(propName, propValue);
           }
        }
    }
    private String read2004String(InputStream stream) throws IOException {
       int stringLen = LittleEndian.readUShort(stream);

       byte[] stringData = new byte[stringLen];
       IOUtils.readFully(stream, stringData);

       // Often but not always null terminated
       if (stringData[stringLen-1] == 0) {
           stringLen--;
       }
       String value = StringUtil.getFromCompressedUnicode(stringData, 0, stringLen);
       return value;
    }

    /**
     * Stored as UCS2, so 16 bit "unicode"
     */
    private void get2007and2010Props(
            InputStream stream, Metadata metadata, XHTMLContentHandler xhtml)
            throws IOException, SAXException {
        // Standard properties
        for (int i = 0; i < HEADER_PROPERTIES_ENTRIES.length; i++) {
            String headerValue = read2007and2010String(stream);
            handleHeader(i, headerValue, metadata, xhtml);
        }
        
        // Custom properties
        int customCount = skipToCustomProperties(stream);
        for (int i = 0; i < customCount; i++) {
           String propName = read2007and2010String(stream);
           String propValue = read2007and2010String(stream);
           if(propName.length() > 0 && propValue.length() > 0) {
              metadata.add(propName, propValue);
           }
        }
    }
    private String read2007and2010String(InputStream stream) throws IOException {
       int stringLen = LittleEndian.readUShort(stream);

       byte[] stringData = new byte[stringLen * 2];
       IOUtils.readFully(stream, stringData);
       String value = StringUtil.getFromUnicodeLE(stringData);
       
       // Some strings are null terminated
       if(value.charAt(value.length()-1) == 0) {
           value = value.substring(0, value.length()-1);
       }

       return value;
    }

    private void get2000Props(
            InputStream stream, Metadata metadata, XHTMLContentHandler xhtml)
            throws IOException, SAXException {
        int propCount = 0;
        while(propCount < 30) {
            int propIdx = LittleEndian.readUShort(stream);
            int length = LittleEndian.readUShort(stream);
            int valueType = stream.read();
            
            if(propIdx == 0x28) {
               // This one seems not to follow the pattern
               length = 0x19;
            } else if(propIdx == 90) {
               // We think this means the end of properties
               break;
            }
            
            byte[] value = new byte[length];
            IOUtils.readFully(stream, value);
            if(valueType == 0x1e) {
                // Normal string, good
                String val = StringUtil.getFromCompressedUnicode(value, 0, length);
                
                // Is it one we can look up by index?
                if(propIdx < HEADER_2000_PROPERTIES_ENTRIES.length) {
                   metadata.add(HEADER_2000_PROPERTIES_ENTRIES[propIdx], val);
                   xhtml.element("p", val);
                } else if(propIdx == 0x012c) {
                   int splitAt = val.indexOf('='); 
                   if(splitAt > -1) {
                      String propName = val.substring(0, splitAt);
                      String propVal = val.substring(splitAt+1);
                      metadata.add(propName, propVal);
                   }
                }
            } else {
                // No idea...
            }
            
            propCount++;
        }
    }
    
    private void handleHeader(
            int headerNumber, String value, Metadata metadata,
            XHTMLContentHandler xhtml) throws SAXException {
        if(value == null || value.length() == 0) {
            return;
        }

        String headerProp = HEADER_PROPERTIES_ENTRIES[headerNumber];
        if(headerProp != null) {
            metadata.set(headerProp, value);
        }

        xhtml.element("p", value);
    }

    /**
     * Grab the offset, then skip there
     */
    private boolean skipToPropertyInfoSection(InputStream stream, byte[] header)
            throws IOException {
        // The offset is stored in the header from 0x20 onwards
        long offsetToSection = LittleEndian.getLong(header, 0x20);
        long toSkip = offsetToSection - header.length;
        if(offsetToSection == 0){
            return false;
        }        
        while (toSkip > 0) {
            byte[] skip = new byte[Math.min((int) toSkip, 0x4000)];
            IOUtils.readFully(stream, skip);
            toSkip -= skip.length;
        }
        return true;
    }
    /**
     * We think it can be anywhere...
     */
    private boolean skipTo2000PropertyInfoSection(InputStream stream, byte[] header)
            throws IOException {
       int val = 0;
       while(val != -1) {
          val = stream.read();
          if(val == HEADER_2000_PROPERTIES_MARKER[0]) {
             boolean going = true;
             for(int i=1; i<HEADER_2000_PROPERTIES_MARKER.length && going; i++) {
                val = stream.read();
                if(val != HEADER_2000_PROPERTIES_MARKER[i]) going = false;
             }
             if(going) {
                // Bingo, found it
                return true;
             }
          }
       }
       return false;
    }
    private int skipToCustomProperties(InputStream stream) 
            throws IOException {
       // There should be 4 zero bytes next
       byte[] padding = new byte[4];
       IOUtils.readFully(stream, padding);
       if(padding[0] == 0 && padding[1] == 0 &&
             padding[2] == 0 && padding[3] == 0) {
          // Looks hopeful, skip on
          padding = new byte[CUSTOM_PROPERTIES_SKIP];
          IOUtils.readFully(stream, padding);
          
          // We should now have the count
          int count = LittleEndian.readUShort(stream);
          
          // Sanity check it
          if(count > 0 && count < 0x7f) {
             // Looks plausible
             return count;
          } else {
             // No properties / count is too high to trust
             return 0;
          }
       } else {
          // No padding. That probably means no custom props
          return 0;
       }
    }

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

}
