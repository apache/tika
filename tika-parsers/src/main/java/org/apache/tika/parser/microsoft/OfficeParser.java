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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.apache.poi.hdgf.extractor.VisioTextExtractor;
import org.apache.poi.hpbf.extractor.PublisherTextExtractor;
import org.apache.poi.hslf.extractor.PowerPointExtractor;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Defines a Microsoft document content extractor.
 */
public class OfficeParser implements Parser {

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                MediaType.application("x-tika-msoffice"),
                MediaType.application("vnd.visio"),
                MediaType.application("vnd.ms-powerpoint"),
                MediaType.application("vnd.ms-excel"),
                MediaType.application("vnd.ms-excel.sheet.binary.macroenabled.12"),
                MediaType.application("msword"),
                MediaType.application("vnd.ms-outlook"))));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

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
        new SummaryExtractor(metadata).parseSummaries(filesystem);

        // Parse remaining document entries
        boolean outlookExtracted = false;
        Iterator<?> entries = filesystem.getRoot().getEntries();
        while (entries.hasNext()) {
            Entry entry = (Entry) entries.next();
            String name = entry.getName();
            if (entry instanceof DirectoryEntry) {
               if ("Quill".equals(name)) {
                  setType(metadata, "application/x-mspublisher");
                  PublisherTextExtractor extractor =
                      new PublisherTextExtractor(filesystem);
                  xhtml.element("p", extractor.getText());
               }
            } else if (entry instanceof DocumentEntry) {
               if ("WordDocument".equals(name)) {
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

    private void setType(Metadata metadata, String type) {
        metadata.set(Metadata.CONTENT_TYPE, type);
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
