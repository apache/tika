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
import java.util.Calendar;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;

import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.pdmodel.PDDocumentInformation;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * PDF parser.
 * <p>
 * This parser can process also encrypted PDF documents if the required
 * password is given as a part of the input metadata associated with a
 * document. If no password is given, then this parser will try decrypting
 * the document using the empty password that's often used with PDFs.
 */
public class PDFParser implements Parser {

    /**
     * Metadata key for giving the document password to the parser.
     *
     * @since Apache Tika 0.5
     */
    public static final String PASSWORD = "org.apache.tika.parser.pdf.password";

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        PDDocument pdfDocument = PDDocument.load(stream);
        try {
            if (pdfDocument.isEncrypted()
                    && !pdfDocument.getCurrentAccessPermission().canExtractContent()) {
                try {
                    String password = metadata.get(PASSWORD);
                    if (password == null) {
                        password = "";
                    }
                    pdfDocument.decrypt(password);
                } catch (Exception e) {
                    // Ignore
                }
            }
            metadata.set(Metadata.CONTENT_TYPE, "application/pdf");
            extractMetadata(pdfDocument, metadata);
            PDF2XHTML.process(pdfDocument, handler, metadata);
        } finally {
            pdfDocument.close();
        }
    }

    private void extractMetadata(PDDocument document, Metadata metadata)
            throws TikaException {
        PDDocumentInformation info = document.getDocumentInformation();
        addMetadata(metadata, Metadata.TITLE, info.getTitle());
        addMetadata(metadata, Metadata.AUTHOR, info.getAuthor());
        addMetadata(metadata, Metadata.CREATOR, info.getCreator());
        addMetadata(metadata, Metadata.KEYWORDS, info.getKeywords());
        addMetadata(metadata, "producer", info.getProducer());
        addMetadata(metadata, Metadata.SUBJECT, info.getSubject());
        addMetadata(metadata, "trapped", info.getTrapped());
        try {
            addMetadata(metadata, "created", info.getCreationDate());
        } catch (IOException e) {
            // Invalid date format, just ignore
        }
        try {
            Calendar modified = info.getModificationDate(); 
            addMetadata(metadata, Metadata.LAST_MODIFIED, modified);
        } catch (IOException e) {
            // Invalid date format, just ignore
        }
    }

    private void addMetadata(Metadata metadata, String name, String value) {
        if (value != null) {
            metadata.add(name, value);
        }
    }

    private void addMetadata(Metadata metadata, String name, Calendar value) {
        if (value != null) {
            metadata.set(name, value.getTime().toString());
        }
    }

}
