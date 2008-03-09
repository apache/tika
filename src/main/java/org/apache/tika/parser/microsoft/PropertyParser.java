/**
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.apache.poi.hpsf.DocumentSummaryInformation;
import org.apache.poi.hpsf.NoPropertySetStreamException;
import org.apache.poi.hpsf.PropertySet;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.hpsf.UnexpectedPropertySetTypeException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser for HPSF property streams within Microsoft Office files.
 */
public class PropertyParser implements Parser {

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        try {
            PropertySet properties =
                new PropertySet(IOUtils.toByteArray(stream));
            if (properties.isSummaryInformation()) {
                SummaryInformation information = new SummaryInformation(properties);
                set(metadata, Metadata.TITLE, information.getTitle());
                set(metadata, Metadata.AUTHOR, information.getAuthor());
                set(metadata, Metadata.KEYWORDS, information.getKeywords());
                set(metadata, Metadata.SUBJECT, information.getSubject());
                set(metadata, Metadata.LAST_AUTHOR, information.getLastAuthor());
                set(metadata, Metadata.COMMENTS, information.getComments());
                set(metadata, Metadata.TEMPLATE, information.getTemplate());
                set(metadata, Metadata.APPLICATION_NAME, information.getApplicationName());
                set(metadata, Metadata.REVISION_NUMBER, information.getRevNumber());
                set(metadata, "creationdate", information.getCreateDateTime());
                set(metadata, Metadata.CHARACTER_COUNT, information.getCharCount());
                set(metadata, "edittime", information.getEditTime());
                set(metadata, Metadata.LAST_SAVED, information.getLastSaveDateTime());
                set(metadata, Metadata.PAGE_COUNT, information.getPageCount());
                set(metadata, "security", information.getSecurity());
                set(metadata, Metadata.WORD_COUNT, information.getWordCount());
                set(metadata, Metadata.LAST_PRINTED, information.getLastPrinted());
            }
            if (properties.isDocumentSummaryInformation()) {
                DocumentSummaryInformation information = new DocumentSummaryInformation(properties);
                set(metadata, "company", information.getCompany());
                set(metadata, "manager", information.getManager());
            }

            // No content, just metadata
            XHTMLContentHandler xhtml =
                new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            xhtml.endDocument();
        } catch (NoPropertySetStreamException e) {
            throw new TikaException("Not a HPSF document", e);
        } catch (UnexpectedPropertySetTypeException e) {
            throw new TikaException("Unexpected HPSF document", e);
        }
    }

    private static void set(Metadata metadata, String name, String value) {
        if (value != null) {
            metadata.set(name, value);
        }
    }

    private static void set(Metadata metadata, String name, Date value) {
        if (value != null) {
            metadata.set(name, value.toString());
        }
    }

    private static void set(Metadata metadata, String name, long value) {
        if (value > 0) {
            metadata.set(name, Long.toString(value));
        }
    }

}
