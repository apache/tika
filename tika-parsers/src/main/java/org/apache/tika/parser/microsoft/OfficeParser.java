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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import org.apache.poi.hdgf.extractor.VisioTextExtractor;
import org.apache.poi.hpsf.CustomProperties;
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
import org.apache.tika.parser.ParseContext;
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
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        POIFSFileSystem filesystem = new POIFSFileSystem(stream);

        // Parse summary entries first, to make metadata available early
        parseSummaryEntryIfExists(
                filesystem, SUMMARY_INFORMATION, metadata);
        parseSummaryEntryIfExists(
                filesystem, DOCUMENT_SUMMARY_INFORMATION, metadata);

        // Parse remaining document entries
        boolean outlookExtracted = false;
        Iterator<?> entries = filesystem.getRoot().getEntries();
        while (entries.hasNext()) {
            Entry entry = (Entry) entries.next();
            String name = entry.getName();
            if (!(entry instanceof DocumentEntry)) {
                // Skip directory entries
            } else if ("WordDocument".equals(name)) {
                setType(metadata, "application/msword");
                WordExtractor extractor = new WordExtractor(filesystem);

                addTextIfAny(xhtml, "header", extractor.getHeaderText());

                for (String paragraph : extractor.getParagraphText()) {
                    xhtml.element("p", paragraph);
                }

                for (String paragraph : extractor.getFootnoteText()) {
                    xhtml.element("p", paragraph);
                }

                for (String paragraph : extractor.getCommentsText()) {
                    xhtml.element("p", paragraph);
                }

                for (String paragraph : extractor.getEndnoteText()) {
                    xhtml.element("p", paragraph);
                }

                addTextIfAny(xhtml, "footer", extractor.getFooterText());
            } else if ("PowerPoint Document".equals(name)) {
                setType(metadata, "application/vnd.ms-powerpoint");
                PowerPointExtractor extractor =
                    new PowerPointExtractor(filesystem);
                xhtml.element("p", extractor.getText(true, true));
            } else if ("Workbook".equals(name)) {
                setType(metadata, "application/vnd.ms-excel");
                Locale locale = context.get(Locale.class, Locale.getDefault());
                new ExcelExtractor().parse(filesystem, xhtml, locale);
            } else if ("VisioDocument".equals(name)) {
                setType(metadata, "application/vnd.visio");
                VisioTextExtractor extractor =
                    new VisioTextExtractor(filesystem);
                for (String text : extractor.getAllText()) {
                    xhtml.element("p", text);
                }
            } else if (!outlookExtracted && name.startsWith("__substg1.0_")) {
                // TODO: Cleaner mechanism for detecting Outlook
                outlookExtracted = true;
                setType(metadata, "application/vnd.ms-outlook");
                new OutlookExtractor(filesystem).parse(xhtml, metadata);
            }
        }

        xhtml.endDocument();
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

    private void parseSummaryEntryIfExists(
            POIFSFileSystem filesystem, String entryName, Metadata metadata)
            throws IOException, TikaException {
        try {
            DocumentEntry entry =
                (DocumentEntry) filesystem.getRoot().getEntry(entryName);
            PropertySet properties =
                new PropertySet(new DocumentInputStream(entry));
            if (properties.isSummaryInformation()) {
                parse(new SummaryInformation(properties), metadata);
            }
            if (properties.isDocumentSummaryInformation()) {
                parse(new DocumentSummaryInformation(properties), metadata);
            }
        } catch (FileNotFoundException e) {
            // entry does not exist, just skip it
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
        set(metadata, Metadata.CREATION_DATE, summary.getCreateDateTime());
        set(metadata, Metadata.CHARACTER_COUNT, summary.getCharCount());
        set(metadata, Metadata.EDIT_TIME, summary.getEditTime());
        set(metadata, Metadata.LAST_SAVED, summary.getLastSaveDateTime());
        set(metadata, Metadata.PAGE_COUNT, summary.getPageCount());
        set(metadata, Metadata.SECURITY, summary.getSecurity());
        set(metadata, Metadata.WORD_COUNT, summary.getWordCount());
        set(metadata, Metadata.LAST_PRINTED, summary.getLastPrinted());
    }

    private void parse(DocumentSummaryInformation summary, Metadata metadata) {
        set(metadata, Metadata.COMPANY, summary.getCompany());
        set(metadata, Metadata.MANAGER, summary.getManager());
        set(metadata, Metadata.LANGUAGE, getLanguage(summary));
        set(metadata, Metadata.CATEGORY, summary.getCategory());
    }

    private String getLanguage(DocumentSummaryInformation summary) {
        CustomProperties customProperties = summary.getCustomProperties();
        if (customProperties != null) {
            Object value = customProperties.get("Language");
            if (value instanceof String) {
                return (String) value;
            }
        }
        return null;
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

    /**
     * Outputs a section of text if the given text is non-empty.
     *
     * @param xhtml XHTML content handler
     * @param section the class of the &lt;div/&gt; section emitted
     * @param text text to be emitted, if any
     * @throws SAXException if an error occurs
     */
    private void addTextIfAny(
            XHTMLContentHandler xhtml, String section, String text)
            throws SAXException {
        if (text != null && text.length() > 0) {
            xhtml.startElement("div", "class", section);
            xhtml.element("p", text);
            xhtml.endElement("div");
        }
    }

}
