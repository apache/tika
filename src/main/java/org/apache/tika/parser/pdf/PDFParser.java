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
package org.apache.tika.parser.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Calendar;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;

import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.pdmodel.PDDocumentInformation;
import org.pdfbox.util.PDFTextStripper;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * PDF parser
 */
public class PDFParser implements Parser {

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        try {
            PDDocument pdfDocument = PDDocument.load(stream);
            try {
                if (pdfDocument.isEncrypted()) {
                    pdfDocument.decrypt("");
                }

                PDDocumentInformation info =
                    pdfDocument.getDocumentInformation();
                if (info.getTitle() != null) {
                    metadata.set(Metadata.TITLE, info.getTitle());
                }
                if (info.getAuthor() != null) {
                    metadata.set(Metadata.AUTHOR, info.getAuthor());
                }
                if (info.getCreator() != null) {
                    metadata.set(Metadata.CREATOR, info.getCreator());
                }
                if (info.getKeywords() != null) {
                    metadata.set(Metadata.KEYWORDS, info.getKeywords());
                }
                if (info.getProducer() != null) {
                    // TODO: Need a Metadata key for producer
                    metadata.set("producer", info.getProducer());
                }
                if (info.getSubject() != null) {
                    metadata.set(Metadata.SUBJECT, info.getSubject());
                }
                if (info.getTrapped() != null) {
                    // TODO: Need a Metadata key for producer
                    metadata.set("trapped", info.getTrapped());
                }
                Calendar created = info.getCreationDate();
                if (created != null) {
                    metadata.set("created", created.getTime().toString());
                }
                Calendar modified = info.getModificationDate();
                if (modified != null) {
                    metadata.set(
                            Metadata.LAST_MODIFIED,
                            modified.getTime().toString());
                }

                StringWriter writer = new StringWriter();
                new PDFTextStripper().writeText(pdfDocument, writer);

                XHTMLContentHandler xhtml =
                    new XHTMLContentHandler(handler, metadata);
                xhtml.startDocument();
                xhtml.element("p", writer.getBuffer().toString());
                xhtml.endDocument();
            } finally {
                pdfDocument.close();
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new TikaException("Error parsing a PDF document", e);
        }
    }

}
