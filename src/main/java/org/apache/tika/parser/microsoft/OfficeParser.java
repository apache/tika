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
import java.util.Iterator;

import org.apache.poi.hdgf.extractor.VisioTextExtractor;
import org.apache.poi.hpsf.DocumentSummaryInformation;
import org.apache.poi.hpsf.MarkUnsupportedException;
import org.apache.poi.hpsf.NoPropertySetStreamException;
import org.apache.poi.hpsf.PropertySet;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.hpsf.UnexpectedPropertySetTypeException;
import org.apache.poi.hslf.extractor.PowerPointExtractor;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Defines a Microsoft document content extractor.
 */
public class OfficeParser implements Parser {

    private static final String SUMMARY_INFORMATION =
        SummaryInformation.DEFAULT_STREAM_NAME;

    private static final String DOCUMENT_SUMMARY_INFORMATION =
        DocumentSummaryInformation.DEFAULT_STREAM_NAME;

    /**
     * Extracts properties and text from an MS Document input stream
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        POIFSFileSystem filesystem = new POIFSFileSystem(stream);
        Iterator<?> entries = filesystem.getRoot().getEntries();
        while (entries.hasNext()) {
            Entry entry = (Entry) entries.next();
            String name = entry.getName();
            if (!(entry instanceof DocumentEntry)) {
                // Skip directory entries
            } else if (SUMMARY_INFORMATION.equals(name)
                    || DOCUMENT_SUMMARY_INFORMATION.equals(name)) {
                parse((DocumentEntry) entry, metadata);
            } else if ("WordDocument".equals(name)) {
                setType(metadata, "application/msword");
                WordExtractor extractor = new WordExtractor(filesystem);
                for (String paragraph : extractor.getParagraphText()) {
                    xhtml.element("p", paragraph);
                }
            } else if ("PowerPoint Document".equals(name)) {
                setType(metadata, "application/vnd.ms-powerpoint");
                PowerPointExtractor extractor =
                    new PowerPointExtractor(filesystem);
                xhtml.element("p", extractor.getText(true, true));
            } else if ("Workbook".equals(name)) {
                setType(metadata, "application/vnd.ms-excel");
                new ExcelExtractor().parse(filesystem, xhtml);
            } else if ("VisioDocument".equals(name)) {
                setType(metadata, "application/vnd.visio");
                VisioTextExtractor extractor =
                    new VisioTextExtractor(filesystem);
                for (String text : extractor.getAllText()) {
                    xhtml.element("p", text);
                }
            } else if (name.startsWith("__substg1.0_")) {
                setType(metadata, "application/vnd.ms-outlook");
                new OutlookExtractor(filesystem).parse(xhtml, metadata);
            }
        }

        xhtml.endDocument();
    }

    public void parse(DocumentEntry entry, Metadata metadata)
            throws IOException, TikaException {
        try {
            PropertySet properties =
                new PropertySet(new DocumentInputStream(entry));
            if (properties.isSummaryInformation()) {
                parse(new SummaryInformation(properties), metadata);
            }
            if (properties.isDocumentSummaryInformation()) {
                parse(new DocumentSummaryInformation(properties), metadata);
            }
        } catch (NoPropertySetStreamException e) {
            throw new TikaException("Not a HPSF document", e);
        } catch (UnexpectedPropertySetTypeException e) {
            throw new TikaException("Unexpected HPSF document", e);
        } catch (MarkUnsupportedException e) {
            throw new TikaException("Invalid DocumentInputStream", e);
        }
    }

    private void parse(SummaryInformation summary, Metadata metadata) {
        set(metadata, Metadata.TITLE, summary.getTitle());
        set(metadata, Metadata.AUTHOR, summary.getAuthor());
        set(metadata, Metadata.KEYWORDS, summary.getKeywords());
        set(metadata, Metadata.SUBJECT, summary.getSubject());
        set(metadata, Metadata.LAST_AUTHOR, summary.getLastAuthor());
        set(metadata, Metadata.COMMENTS, summary.getComments());
        set(metadata, Metadata.TEMPLATE, summary.getTemplate());
        set(metadata, Metadata.APPLICATION_NAME, summary.getApplicationName());
        set(metadata, Metadata.REVISION_NUMBER, summary.getRevNumber());
        set(metadata, "creationdate", summary.getCreateDateTime());
        set(metadata, Metadata.CHARACTER_COUNT, summary.getCharCount());
        set(metadata, "edittime", summary.getEditTime());
        set(metadata, Metadata.LAST_SAVED, summary.getLastSaveDateTime());
        set(metadata, Metadata.PAGE_COUNT, summary.getPageCount());
        set(metadata, "security", summary.getSecurity());
        set(metadata, Metadata.WORD_COUNT, summary.getWordCount());
        set(metadata, Metadata.LAST_PRINTED, summary.getLastPrinted());
    }

    private void parse(DocumentSummaryInformation summary, Metadata metadata) {
        set(metadata, "company", summary.getCompany());
        set(metadata, "manager", summary.getManager());
    }

    private void setType(Metadata metadata, String type) {
        metadata.set(Metadata.CONTENT_TYPE, type);
    }

    private void set(Metadata metadata, String name, String value) {
        if (value != null) {
            metadata.set(name, value);
        }
    }

    private void set(Metadata metadata, String name, Date value) {
        if (value != null) {
            metadata.set(name, value.toString());
        }
    }

    private void set(Metadata metadata, String name, long value) {
        if (value > 0) {
            metadata.set(name, Long.toString(value));
        }
    }

}
