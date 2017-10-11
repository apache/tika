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
package org.apache.tika.parser.microsoft;

import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

/**
 * Parser for temporary MSOFfice files.
 * This currently only extracts the owner's name.
 */
public class MSOwnerFileParser extends AbstractParser {

    private static final int ASCII_CHUNK_LENGTH = 54;
    private static final MediaType MEDIA_TYPE = MediaType.application("x-ms-owner");
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -752276948656079347L;
    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MEDIA_TYPE);

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /**
     * Extracts owner from MS temp file
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        byte[] asciiNameBytes = new byte[ASCII_CHUNK_LENGTH];
        IOUtils.readFully(stream, asciiNameBytes);
        int asciiNameLength = (int)asciiNameBytes[0];//don't need to convert to unsigned int because it can't be that long
        //sanity check name length
        if (asciiNameLength < 0) {
            throw new TikaException("ascii name length must be >= 0");
        } else if (asciiNameLength > ASCII_CHUNK_LENGTH) {
            throw new TikaException("ascii name length must be < 55");
        }

        String asciiName = new String(asciiNameBytes, 1, asciiNameLength, StandardCharsets.US_ASCII);
        metadata.set(TikaCoreProperties.MODIFIER, asciiName);

        int unicodeCharLength = stream.read();
        if (asciiNameLength == unicodeCharLength) {
            stream.read();//zero after the char length
            byte[] unicodeBytes = new byte[unicodeCharLength * 2];
            IOUtils.readFully(stream, unicodeBytes);
            String unicodeName = new String(unicodeBytes, StandardCharsets.UTF_16LE);
            metadata.set(TikaCoreProperties.MODIFIER, unicodeName);
        } else {
            throw new TikaException("Ascii name length should be the same as the unicode length");
        }
        xhtml.endDocument();
    }
}