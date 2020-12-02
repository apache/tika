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
package org.apache.tika.parser.pdf;

import java.io.IOException;
import java.io.Writer;

import org.apache.commons.io.IOExceptionWithCause;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;


/**
 * Utility class that overrides the {@link PDFTextStripper} functionality
 * to integrate text extraction via OCR only.
 *
 */
class OCR2XHTML extends AbstractPDF2XHTML {

    private OCR2XHTML(PDDocument document, ContentHandler handler, ParseContext context, Metadata metadata,
                      PDFParserConfig config)
            throws IOException {
        super(document, handler, context, metadata, config);
    }

    /**
     * Converts the given PDF document (and related metadata) to a stream
     * of XHTML SAX events sent to the given content handler.
     *
     * @param document PDF document
     * @param handler  SAX content handler
     * @param metadata PDF metadata
     * @throws SAXException  if the content handler fails to process SAX events
     * @throws TikaException if there was an exception outside of per page processing
     */
    public static void process(
            PDDocument document, ContentHandler handler, ParseContext context, Metadata metadata,
            PDFParserConfig config)
            throws SAXException, TikaException {
        OCR2XHTML ocr2XHTML = null;
        try {
            ocr2XHTML = new OCR2XHTML(document, handler, context, metadata, config);
            ocr2XHTML.writeText(document, new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) {
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        } catch (IOException e) {
            if (e.getCause() instanceof SAXException) {
                throw (SAXException) e.getCause();
            } else {
                throw new TikaException("Unable to extract PDF content", e);
            }
        }
        if (ocr2XHTML.exceptions.size() > 0) {
            //throw the first
            throw new TikaException("Unable to extract all PDF content",
                    ocr2XHTML.exceptions.get(0));
        }
    }

    @Override
    public void processPage(PDPage pdPage) throws IOException {
        try {
            startPage(pdPage);
            doOCROnCurrentPage();
            endPage(pdPage);
        } catch (TikaException|SAXException e) {
            throw new IOExceptionWithCause(e);
        } catch (IOException e) {
            handleCatchableIOE(e);
        }
    }

    @Override
    protected void writeString(String text) throws IOException {
        //no-op
    }

    @Override
    protected void writeCharacters(TextPosition text) throws IOException {
        //no-op
    }

    @Override
    protected void writeWordSeparator() throws IOException {
        //no-op
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        //no-op
    }

}

