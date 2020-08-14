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
package org.apache.tika.parser.font;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.fontbox.ttf.NameRecord;
import org.apache.fontbox.ttf.NamingTable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser for TrueType font files (TTF).
 */
public class TrueTypeParser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = 44788554612243032L;

    private static final MediaType TYPE =
        MediaType.application("x-font-ttf");

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.singleton(TYPE);

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        TikaInputStream tis = TikaInputStream.cast(stream);

        // Ask FontBox to parse the file for us
        TrueTypeFont font = null;
        try {
            TTFParser parser = new TTFParser();
            if (tis != null && tis.hasFile()) {
                font = parser.parse(tis.getFile());
            } else {
                font = parser.parse(stream);
            }

            // Report the details of the font
            metadata.set(Metadata.CONTENT_TYPE, TYPE.toString());
            metadata.set(TikaCoreProperties.CREATED,
                    font.getHeader().getCreated());
            metadata.set(TikaCoreProperties.MODIFIED,
                    font.getHeader().getModified());
            metadata.set(AdobeFontMetricParser.MET_DOC_VERSION,
                    Float.toString(font.getHeader().getVersion()));

            // Pull out the naming info
            NamingTable fontNaming = font.getNaming();
            for (NameRecord nr : fontNaming.getNameRecords()) {
                if (nr.getNameId() == NameRecord.NAME_FONT_FAMILY_NAME) {
                    metadata.set(AdobeFontMetricParser.MET_FONT_FAMILY_NAME, nr.getString());
                }
                if (nr.getNameId() == NameRecord.NAME_FONT_SUB_FAMILY_NAME) {
                    metadata.set(AdobeFontMetricParser.MET_FONT_SUB_FAMILY_NAME, nr.getString());
                }
                if (nr.getNameId() == NameRecord.NAME_FULL_FONT_NAME) {
                    metadata.set(AdobeFontMetricParser.MET_FONT_NAME, nr.getString());
                    metadata.set(TikaCoreProperties.TITLE, nr.getString());
                }
                if (nr.getNameId() == NameRecord.NAME_POSTSCRIPT_NAME) {
                    metadata.set(AdobeFontMetricParser.MET_PS_NAME, nr.getString());
                }
                if (nr.getNameId() == NameRecord.NAME_COPYRIGHT) {
                    metadata.set("Copyright", nr.getString());
                }
                if (nr.getNameId() == NameRecord.NAME_TRADEMARK) {
                    metadata.set("Trademark", nr.getString());
                }
            }
        } finally {
            if (font != null) {
                font.close();
            }
        }

        // For now, we only output metadata, no textual contents
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
    }

}
