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

// JDK imports
import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.hpsf.DocumentSummaryInformation;
import org.apache.poi.hpsf.HPSFException;
import org.apache.poi.hpsf.PropertySet;
import org.apache.poi.hpsf.PropertySetFactory;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.AppendableAdaptor;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Defines a Microsoft document content extractor.
 */
public abstract class OfficeParser implements Parser {

    /**
     * Extracts properties and text from an MS Document input stream
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        POIFSFileSystem filesystem = new POIFSFileSystem(stream);

        metadata.set(Metadata.CONTENT_TYPE, getContentType());
        getMetadata(
                filesystem, SummaryInformation.DEFAULT_STREAM_NAME, metadata);
        getMetadata(
                filesystem, DocumentSummaryInformation.DEFAULT_STREAM_NAME,
                metadata);

        XHTMLContentHandler xhtml =
            new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.startElement("p");
        extractText(filesystem, new AppendableAdaptor(xhtml));
        xhtml.endElement("p");
        xhtml.endDocument();
    }

    /**
     * The content type of the document being parsed.
     *
     * @return MIME content type
     */
    protected abstract String getContentType();

    /**
     * Extracts the text content from a Microsoft document input stream.
     */
    protected abstract void extractText(POIFSFileSystem filesystem, Appendable appendable)
        throws IOException, TikaException;

    private void getMetadata(
            POIFSFileSystem filesystem, String name, Metadata metadata) {
        try {
            InputStream stream = filesystem.createDocumentInputStream(name);
            try {
                getMetadata(stream, metadata);
            } finally {
                stream.close();
            }
        } catch (Exception e) {
            // summary information not available, ignore
        }
    }

    private void getMetadata(InputStream stream, Metadata metadata)
            throws HPSFException, IOException {
        PropertySet set = PropertySetFactory.create(stream);
        if (set instanceof SummaryInformation) {
            getMetadata((SummaryInformation) set, metadata);
        } else if (set instanceof DocumentSummaryInformation) {
            getMetadata((DocumentSummaryInformation) set, metadata);
        }
    }

    private void getMetadata(
            SummaryInformation information, Metadata metadata) {
        if (information.getTitle() != null) {
            metadata.set(Metadata.TITLE, information.getTitle());
        }
        if (information.getAuthor() != null) {
            metadata.set(Metadata.AUTHOR, information.getAuthor());
        }
        if (information.getKeywords() != null) {
            metadata.set(Metadata.KEYWORDS, information.getKeywords());
        }
        if (information.getSubject() != null) {
            metadata.set(Metadata.SUBJECT, information.getSubject());
        }
        if (information.getLastAuthor() != null) {
            metadata.set(Metadata.LAST_AUTHOR, information.getLastAuthor());
        }
        if (information.getComments() != null) {
            metadata.set(Metadata.COMMENTS, information.getComments());
        }
        if (information.getTemplate() != null) {
            metadata.set(Metadata.TEMPLATE, information.getTemplate());
        }
        if (information.getApplicationName() != null) {
            metadata.set(
                    Metadata.APPLICATION_NAME,
                    information.getApplicationName());
        }
        if (information.getRevNumber() != null) {
            metadata.set(Metadata.REVISION_NUMBER, information.getRevNumber());
        }
        if (information.getCreateDateTime() != null) {
            metadata.set(
                    "creationdate",
                    information.getCreateDateTime().toString());
        }
        if (information.getCharCount() > 0) {
            metadata.set(
                    Metadata.CHARACTER_COUNT,
                    Integer.toString(information.getCharCount()));
        }
        if (information.getEditTime() > 0) {
            metadata.set("edittime", Long.toString(information.getEditTime()));
        }
        if (information.getLastSaveDateTime() != null) {
            metadata.set(
                    Metadata.LAST_SAVED,
                    information.getLastSaveDateTime().toString());
        }
        if (information.getPageCount() > 0) {
            metadata.set(
                    Metadata.PAGE_COUNT,
                    Integer.toString(information.getPageCount()));
        }
        if (information.getSecurity() > 0) {
            metadata.set(
                    "security", Integer.toString(information.getSecurity()));
        }
        if (information.getWordCount() > 0) {
            metadata.set(
                    Metadata.WORD_COUNT,
                    Integer.toString(information.getWordCount()));
        }
        if (information.getLastPrinted() != null) {
            metadata.set(
                    Metadata.LAST_PRINTED,
                    information.getLastPrinted().toString());
        }
    }

    private void getMetadata(
            DocumentSummaryInformation information, Metadata metadata) {
        if (information.getCompany() != null) {
            metadata.set("company", information.getCompany());
        }
        if (information.getManager() != null) {
            metadata.set("manager", information.getManager());
        }
    }

}
