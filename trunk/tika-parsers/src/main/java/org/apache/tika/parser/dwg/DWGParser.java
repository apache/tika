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
    private static String[] HEADER_PROPERTIES_ENTRIES = {
        Metadata.TITLE, 
        Metadata.SUBJECT,
        Metadata.AUTHOR,
        Metadata.KEYWORDS,
        Metadata.COMMENTS,
        Metadata.LAST_AUTHOR,
        null, // Unknown?
        Metadata.RELATION, // Hyperlink
    };

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

        if (version.equals("AC1018")) {
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
        for (int i = 0; i < HEADER_PROPERTIES_ENTRIES.length; i++) {
            int stringLen = LittleEndian.readUShort(stream);

            byte[] stringData = new byte[stringLen];
            IOUtils.readFully(stream, stringData);

            // Often but not always null terminated
            if (stringData[stringLen-1] == 0) {
                stringLen--;
            }
            String headerValue =
                StringUtil.getFromCompressedUnicode(stringData, 0, stringLen);

            handleHeader(i, headerValue, metadata, xhtml);
        }
    }

    /**
     * Stored as UCS2, so 16 bit "unicode"
     */
    private void get2007and2010Props(
            InputStream stream, Metadata metadata, XHTMLContentHandler xhtml)
            throws IOException, SAXException {
        for (int i = 0; i < HEADER_PROPERTIES_ENTRIES.length; i++) {
            int stringLen = LittleEndian.readUShort(stream);

            byte[] stringData = new byte[stringLen * 2];
            IOUtils.readFully(stream, stringData);
            String headerValue = StringUtil.getFromUnicodeLE(stringData);

            handleHeader(i, headerValue, metadata, xhtml);
        }
    }

    private void handleHeader(
            int headerNumber, String value, Metadata metadata,
            XHTMLContentHandler xhtml) throws SAXException {
        if(value == null || value.length() == 0) {
            return;
        }

        // Some strings are null terminated
        if(value.charAt(value.length()-1) == 0) {
            value = value.substring(0, value.length()-1);
        }

        String headerProp = HEADER_PROPERTIES_ENTRIES[headerNumber];
        if(headerProp != null) {
            metadata.set(headerProp, value);
        }

        xhtml.element("p", value);
    }

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

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

}
