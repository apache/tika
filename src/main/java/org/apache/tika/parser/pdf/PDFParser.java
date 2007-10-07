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

import org.apache.tika.config.Content;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;

import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.pdmodel.PDDocumentInformation;
import org.pdfbox.util.PDFTextStripper;

/**
 * PDF parser
 */
public class PDFParser implements Parser {

    public String parse(
            InputStream stream, Iterable<Content> contents, Metadata metadata)
            throws IOException, TikaException {
        try {
            PDDocument pdfDocument = PDDocument.load(stream);
            try {
                if (pdfDocument.isEncrypted()) {
                    pdfDocument.decrypt("");
                }

                PDDocumentInformation metaData =
                    pdfDocument.getDocumentInformation();
                for (Content content : contents) {
                    String text = content.getTextSelect();
                    if ("title".equalsIgnoreCase(text)) {
                        metadata.set(content.getName(), metaData.getTitle());
                    } else if ("author".equalsIgnoreCase(text)) {
                        metadata.set(content.getName(), metaData.getAuthor());
                    } else if ("creator".equalsIgnoreCase(text)) {
                        metadata.set(content.getName(), metaData.getCreator());
                    } else if ("keywords".equalsIgnoreCase(text)) {
                        metadata.set(content.getName(), metaData.getKeywords());
                    } else if ("producer".equalsIgnoreCase(text)) {
                        metadata.set(content.getName(), metaData.getProducer());
                    } else if ("subject".equalsIgnoreCase(text)) {
                        metadata.set(content.getName(), metaData.getSubject());
                    } else if ("trapped".equalsIgnoreCase(text)) {
                        metadata.set(content.getName(), metaData.getTrapped());
                    } else if ("creationDate".equalsIgnoreCase(text)) {
                        Calendar calendar = metaData.getCreationDate();
                        if (calendar != null) {
                            metadata.set(content.getName(),
                                    calendar.getTime().toString());
                        }
                    } else if ("modificationDate".equalsIgnoreCase(text)) {
                        Calendar calendar = metaData.getModificationDate();
                        if (calendar != null) {
                            metadata.set(content.getName(),
                                    calendar.getTime().toString());
                        }
                    }
                }

                StringWriter writer = new StringWriter();
                new PDFTextStripper().writeText(pdfDocument, writer);
                return writer.getBuffer().toString();
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
