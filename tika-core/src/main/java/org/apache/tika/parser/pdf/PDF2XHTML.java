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

import org.apache.commons.io.IOExceptionWithCause;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.XHTMLContentHandler;
import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.pdmodel.PDPage;
import org.pdfbox.util.PDFTextStripper;
import org.pdfbox.util.TextPosition;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Utility class that overrides the {@link PDFTextStripper} functionality
 * to produce a semi-structured XHTML SAX events instead of a plain text
 * stream.
 */
class PDF2XHTML extends PDFTextStripper {

    /**
     * Converts the given PDF document (and related metadata) to a stream
     * of XHTML SAX events sent to the given content handler.
     *
     * @param document PDF document
     * @param handler SAX content handler
     * @param metadata PDF metadata
     * @throws SAXException if the content handler fails to process SAX events
     * @throws TikaException if the PDF document can not be processed
     */
    public static void process(
            PDDocument document, ContentHandler handler, Metadata metadata)
            throws SAXException, TikaException {
        try {
            new PDF2XHTML(handler, metadata).getText(document);
        } catch (IOException e) {
            if (e.getCause() instanceof SAXException) {
                throw (SAXException) e.getCause();
            } else {
                throw new TikaException("Unable to extract PDF content", e);
            }
        }
    }

    private final XHTMLContentHandler handler;

    private PDF2XHTML(ContentHandler handler, Metadata metadata)
            throws IOException {
        this.handler = new XHTMLContentHandler(handler, metadata);
    }

    protected void startDocument(PDDocument pdf) throws IOException {
        try {
            handler.startDocument();
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to start a document", e);
        }
    }

    protected void endDocument(PDDocument pdf) throws IOException {
        try {
            handler.endDocument();
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to end a document", e);
        }
    }

    protected void startPage(PDPage page) throws IOException {
        try {
            handler.startElement("div");
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to start a page", e);
        }
    }

    protected void endPage(PDPage page) throws IOException {
        try {
            handler.endElement("div");
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to end a page", e);
        }
    }

    protected void startParagraph() throws IOException {
        try {
            handler.startElement("p");
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to start a paragraph", e);
        }
    }

    protected void endParagraph() throws IOException {
        try {
            handler.endElement("p");
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to end a paragraph", e);
        }
    }

    protected void writeCharacters(TextPosition text) throws IOException {
        try {
            handler.characters(text.getCharacter());
        } catch (SAXException e) {
            throw new IOExceptionWithCause(
                    "Unable to write a character: " + text.getCharacter(), e);
        }
    }

    // Two methods added to work around lack of support for processWordSeparator
    // and processLineSeparator in PDFBox-0.7.3. This is fixed in CVS Head (PDFBox-0.7.4)
    public String getWordSeparator()
    {
        try
        {
            handler.characters(" ");
        } catch(SAXException e) {

        }
        return super.getWordSeparator();    //To change body of overridden methods use File | Settings | File Templates.
    }

    public String getLineSeparator()
    {
        try
        {
            handler.characters("\n");
        } catch(SAXException e) {

        }
        return super.getLineSeparator();
    }

//    protected void processLineSeparator(TextPosition p) throws IOException {
//        try {
//            handler.characters("\n");
//        } catch (SAXException e) {
//            throw new IOExceptionWithCause("Unable to write a newline", e);
//        }
//    }
//
//    protected void processWordSeparator(TextPosition a, TextPosition b)
//            throws IOException {
//        try {
//            handler.characters(" ");
//        } catch (SAXException e) {
//            throw new IOExceptionWithCause("Unable to write a space", e);
//        }
//    }

}
