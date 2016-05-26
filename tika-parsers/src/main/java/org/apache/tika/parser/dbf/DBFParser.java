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
package org.apache.tika.parser.dbf;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.txt.Icu4jEncodingDetector;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * This is a Tika wrapper around the DBFReader.
 * <p>
 * This reads many dbase3 file variants (not DBASE 7, yet!).
 * <p>
 * It caches the first 10 rows and then runs encoding dectection
 * on the "character" cells.
 */
public class DBFParser extends AbstractParser {

    private static final int ROWS_TO_BUFFER_FOR_CHARSET_DETECTION = 10;
    private static final int MAX_CHARS_FOR_CHARSET_DETECTION = 20000;
    private static final Charset DEFAULT_CHARSET = StandardCharsets.ISO_8859_1;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("x-dbf"));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        DBFReader reader = DBFReader.open(stream);
        DBFFileHeader header = reader.getHeader();
        metadata.set(Metadata.CONTENT_TYPE, header.getVersion().getFullMimeString());

        //insert metadata here
        Calendar lastModified = header.getLastModified();
        if (lastModified != null) {
            metadata.set(TikaCoreProperties.MODIFIED, lastModified);
        }

        //buffer first X rows for charset detection
        List<DBFRow> firstRows = new LinkedList<>();
        DBFRow row = reader.next();
        int i = 0;
        while (row != null && i++ < ROWS_TO_BUFFER_FOR_CHARSET_DETECTION) {
            firstRows.add(row.deepCopy());
            row = reader.next();
        }

        Charset charset = getCharset(firstRows, header);
        metadata.set(Metadata.CONTENT_ENCODING, charset.toString());

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.startElement("table");
        xhtml.startElement("thead");
        for (DBFColumnHeader col : header.getCols()) {
            xhtml.startElement("th");
            xhtml.characters(col.getName(charset));
            xhtml.endElement("th");
        }
        xhtml.endElement("thead");

        xhtml.startElement("tbody");

        //now write cached rows
        while (firstRows.size() > 0) {
            DBFRow cachedRow = firstRows.remove(0);
            writeRow(cachedRow, charset, xhtml);
        }

        //now continue with rest
        while (row != null) {
            writeRow(row, charset, xhtml);
            row = reader.next();
        }
        xhtml.endElement("tbody");
        xhtml.endElement("table");
        xhtml.endDocument();
    }

    private Charset getCharset(List<DBFRow> firstRows, DBFFileHeader header) throws IOException,
            TikaException {
        //TODO: potentially use codepage info in the header
        Charset charset = DEFAULT_CHARSET;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (DBFRow row : firstRows) {
            for (DBFCell cell : row.cells) {
                if (cell.getColType().equals(DBFColumnHeader.ColType.C)) {
                    byte[] bytes = cell.getBytes();
                    bos.write(bytes);
                    if (bos.size() > MAX_CHARS_FOR_CHARSET_DETECTION) {
                        break;
                    }
                }
            }
        }
        byte[] bytes = bos.toByteArray();
        if (bytes.length > 20) {
            EncodingDetector detector = new Icu4jEncodingDetector();
            detector.detect(TikaInputStream.get(bytes), new Metadata());
            charset = detector.detect(new ByteArrayInputStream(bytes), new Metadata());
        }
        return charset;
    }

    private void writeRow(DBFRow row, Charset charset, XHTMLContentHandler xhtml) throws SAXException {
        xhtml.startElement("tr");
        for (DBFCell cell : row.cells) {
            xhtml.startElement("td");
            xhtml.characters(cell.getString(charset));
            xhtml.endElement("td");
        }
        xhtml.endElement("tr");

    }
}
