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
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.util.PDFOperator;
import org.apache.pdfbox.util.PDFTextStripper;
import org.apache.pdfbox.util.TextPosition;
import org.apache.pdfbox.util.operator.OperatorProcessor;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.XHTMLContentHandler;
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

        // TIKA-292: Ignore unneeded PDF operators
        // TODO: Remove this once PDFBox is no longer so verbose
        OperatorProcessor ignore = new OperatorProcessor() {
            @Override @SuppressWarnings("unchecked")
            public void process(PDFOperator operator, List arguments) {
            }
        };
        registerOperatorProcessor("b", ignore);
        registerOperatorProcessor("B", ignore);
        registerOperatorProcessor("b*", ignore);
        registerOperatorProcessor("B*", ignore);
        registerOperatorProcessor("BDC", ignore);
        registerOperatorProcessor("BI", ignore);
        registerOperatorProcessor("BMC", ignore);
        registerOperatorProcessor("b", ignore);
        registerOperatorProcessor("BX", ignore);
        registerOperatorProcessor("c", ignore);
        registerOperatorProcessor("CS", ignore);
        registerOperatorProcessor("cs", ignore);
        registerOperatorProcessor("d", ignore);
        registerOperatorProcessor("d0", ignore);
        registerOperatorProcessor("d1", ignore);
        registerOperatorProcessor("DP", ignore);
        registerOperatorProcessor("El", ignore);
        registerOperatorProcessor("EMC", ignore);
        registerOperatorProcessor("EX", ignore);
        registerOperatorProcessor("f", ignore);
        registerOperatorProcessor("F", ignore);
        registerOperatorProcessor("f*", ignore);
        registerOperatorProcessor("G", ignore);
        registerOperatorProcessor("g", ignore);
        registerOperatorProcessor("h", ignore);
        registerOperatorProcessor("i", ignore);
        registerOperatorProcessor("ID", ignore);
        registerOperatorProcessor("j", ignore);
        registerOperatorProcessor("J", ignore);
        registerOperatorProcessor("K", ignore);
        registerOperatorProcessor("k", ignore);
        registerOperatorProcessor("l", ignore);
        registerOperatorProcessor("m", ignore);
        registerOperatorProcessor("M", ignore);
        registerOperatorProcessor("MP", ignore);
        registerOperatorProcessor("n", ignore);
        registerOperatorProcessor("re", ignore);
        registerOperatorProcessor("RG", ignore);
        registerOperatorProcessor("rg", ignore);
        registerOperatorProcessor("ri", ignore);
        registerOperatorProcessor("s", ignore);
        registerOperatorProcessor("S", ignore);
        registerOperatorProcessor("SC", ignore);
        registerOperatorProcessor("sc", ignore);
        registerOperatorProcessor("SCN", ignore);
        registerOperatorProcessor("scn", ignore);
        registerOperatorProcessor("sh", ignore);
        registerOperatorProcessor("v", ignore);
        registerOperatorProcessor("W", ignore);
        registerOperatorProcessor("W*", ignore);
        registerOperatorProcessor("y", ignore);
    }

    @Override
    protected void startDocument(PDDocument pdf) throws IOException {
        try {
            handler.startDocument();
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to start a document", e);
        }
    }

    @Override
    protected void endDocument(PDDocument pdf) throws IOException {
        try {
            handler.endDocument();
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to end a document", e);
        }
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        try {
            handler.startElement("div", "class", "page");
            handler.startElement("p");
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to start a page", e);
        }
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
        try {
            handler.endElement("p");
            handler.endElement("div");
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to end a page", e);
        }
    }

    @Override
    protected void writeString(String text) throws IOException {
        try {
            handler.characters(text);
        } catch (SAXException e) {
            throw new IOExceptionWithCause(
                    "Unable to write a string: " + text, e);
        }
    }

    @Override
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
    @Override
    public String getWordSeparator()
    {
        try
        {
            handler.characters(" ");
        } catch(SAXException e) {

        }
        return super.getWordSeparator();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
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
