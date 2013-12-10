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
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.interactive.action.type.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.type.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.util.PDFTextStripper;
import org.apache.pdfbox.util.TextPosition;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Utility class that overrides the {@link PDFTextStripper} functionality
 * to produce a semi-structured XHTML SAX events instead of a plain text
 * stream.
 */
class PDF2XHTML extends PDFTextStripper {
    
    /**
     * Maximum recursive depth during AcroForm processing.
     * Prevents theoretical AcroForm recursion bomb. 
     */
    private final static int MAX_ACROFORM_RECURSIONS = 10;


    // TODO: remove once PDFBOX-1130 is fixed:
    private boolean inParagraph = false;

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
            PDDocument document, ContentHandler handler, ParseContext context, Metadata metadata,
            PDFParserConfig config)
            throws SAXException, TikaException {
        try {
            // Extract text using a dummy Writer as we override the
            // key methods to output to the given content
            // handler.
            PDF2XHTML pdf2XHTML = new PDF2XHTML(handler, context, metadata, config);

            pdf2XHTML.writeText(document, new Writer() {
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
    }
    
    private final ContentHandler originalHandler;
    private final ParseContext context;
    private final XHTMLContentHandler handler;
    private final PDFParserConfig config;
    
    private PDF2XHTML(ContentHandler handler, ParseContext context, Metadata metadata, 
            PDFParserConfig config)
            throws IOException {
        //source of config (derives from context or PDFParser?) is
        //already determined in PDFParser.  No need to check context here.
        this.config = config;
        this.originalHandler = handler;
        this.context = context;
        this.handler = new XHTMLContentHandler(handler, metadata);
        setForceParsing(true);
        setSortByPosition(config.getSortByPosition());
        if (config.getEnableAutoSpace()) {
            setWordSeparator(" ");
        } else {
            setWordSeparator("");
        }
        // TODO: maybe expose setting these too:
        //setAverageCharTolerance(1.0f);
        //setSpacingTolerance(1.0f);
        setSuppressDuplicateOverlappingText(config.getSuppressDuplicateOverlappingText());
    }

    void extractBookmarkText() throws SAXException {
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline != null) {
            extractBookmarkText(outline);
        }
    }

    void extractBookmarkText(PDOutlineNode bookmark) throws SAXException {
        PDOutlineItem current = bookmark.getFirstChild();
        if (current != null) {
            handler.startElement("ul");
            while (current != null) {
                handler.startElement("li");
                handler.characters(current.getTitle());
                handler.endElement("li");
                // Recurse:
                extractBookmarkText(current);
                current = current.getNextSibling();
            }
            handler.endElement("ul");
        }
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
            // Extract text for any bookmarks:
            extractBookmarkText();
            extractEmbeddedDocuments(pdf, originalHandler);
            
            //extract acroform data at end of doc
            if (config.getExtractAcroFormContent() == true){
                extractAcroForm(pdf, handler);
             }
            handler.endDocument();
        } catch (TikaException e){
           throw new IOExceptionWithCause("Unable to end a document", e);
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to end a document", e);
        }
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        try {
            handler.startElement("div", "class", "page");
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to start a page", e);
        }
        writeParagraphStart();
    }

    @Override
    protected void endPage(PDPage page) throws IOException {

        try {
            writeParagraphEnd();
            // TODO: remove once PDFBOX-1143 is fixed:
            if (config.getExtractAnnotationText()) {
                for(Object o : page.getAnnotations()) {
                    if( o instanceof PDAnnotationLink ) {
                        PDAnnotationLink annotationlink = (PDAnnotationLink) o;
                        if (annotationlink.getAction()  != null) {
                            PDAction action = annotationlink.getAction();
                            if( action instanceof PDActionURI ) {
                                PDActionURI uri = (PDActionURI) action;
                                String link = uri.getURI();
                                if (link != null) {
                                    handler.startElement("div", "class", "annotation");
                                    handler.startElement("a", "href", link);
                                    handler.endElement("a");
                                    handler.endElement("div");
                                }
                             }
                        }
                    }

                    if (o instanceof PDAnnotationMarkup) {
                        PDAnnotationMarkup annot = (PDAnnotationMarkup) o;
                        String title = annot.getTitlePopup();
                        String subject = annot.getSubject();
                        String contents = annot.getContents();
                        // TODO: maybe also annot.getRichContents()?
                        if (title != null || subject != null || contents != null) {
                            handler.startElement("div", "class", "annotation");

                            if (title != null) {
                                handler.startElement("div", "class", "annotationTitle");
                                handler.characters(title);
                                handler.endElement("div");
                            }

                            if (subject != null) {
                                handler.startElement("div", "class", "annotationSubject");
                                handler.characters(subject);
                                handler.endElement("div");
                            }

                            if (contents != null) {
                                handler.startElement("div", "class", "annotationContents");
                                handler.characters(contents);
                                handler.endElement("div");
                            }

                            handler.endElement("div");
                        }
                    }
                }
            }
            handler.endElement("div");
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to end a page", e);
        }
    }

    @Override
    protected void writeParagraphStart() throws IOException {
        // TODO: remove once PDFBOX-1130 is fixed
        if (inParagraph) {
            // Close last paragraph
            writeParagraphEnd();
        }
        assert !inParagraph;
        inParagraph = true;
        try {
            handler.startElement("p");
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to start a paragraph", e);
        }
    }

    @Override
    protected void writeParagraphEnd() throws IOException {
        // TODO: remove once PDFBOX-1130 is fixed
        if (!inParagraph) {
            writeParagraphStart();
        }
        assert inParagraph;
        inParagraph = false;
        try {
            handler.endElement("p");
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to end a paragraph", e);
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

    @Override
    protected void writeWordSeparator() throws IOException {
        try {
            handler.characters(getWordSeparator());
        } catch (SAXException e) {
            throw new IOExceptionWithCause(
                    "Unable to write a space character", e);
        }
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        try {
            handler.newline();
        } catch (SAXException e) {
            throw new IOExceptionWithCause(
                    "Unable to write a newline character", e);
        }
    }
    
    private void extractEmbeddedDocuments(PDDocument document, ContentHandler handler)
          throws IOException, SAXException, TikaException {
      PDDocumentCatalog catalog = document.getDocumentCatalog();
      PDDocumentNameDictionary names = catalog.getNames();
      if (names != null) {

          PDEmbeddedFilesNameTreeNode embeddedFiles = names.getEmbeddedFiles();
          if (embeddedFiles != null) {

              EmbeddedDocumentExtractor embeddedExtractor = context.get(EmbeddedDocumentExtractor.class);
              if (embeddedExtractor == null) {
                  embeddedExtractor = new ParsingEmbeddedDocumentExtractor(context);
              }

              Map<String, COSObjectable> embeddedFileNames = embeddedFiles.getNames();

              if (embeddedFileNames != null) {
                  for (Map.Entry<String,COSObjectable> ent : embeddedFileNames.entrySet()) {
                      PDComplexFileSpecification spec = (PDComplexFileSpecification) ent.getValue();
                      PDEmbeddedFile file = spec.getEmbeddedFile();

                      Metadata metadata = new Metadata();
                      // TODO: other metadata?
                      metadata.set(Metadata.RESOURCE_NAME_KEY, ent.getKey());
                      metadata.set(Metadata.CONTENT_TYPE, file.getSubtype());
                      metadata.set(Metadata.CONTENT_LENGTH, Long.toString(file.getSize()));

                      if (embeddedExtractor.shouldParseEmbedded(metadata)) {
                          TikaInputStream stream = TikaInputStream.get(file.createInputStream());
                          try {
                              embeddedExtractor.parseEmbedded(
                                                              stream,
                                                              new EmbeddedContentHandler(handler),
                                                              metadata, false);
                          } finally {
                              stream.close();
                          }
                      }
                  }
              }
          }
      }
  }
    private void extractAcroForm(PDDocument pdf, XHTMLContentHandler handler) throws IOException, 
    SAXException {
        //Thank you, Ben Litchfield, for org.apache.pdfbox.examples.fdf.PrintFields
        //this code derives from Ben's code
        PDDocumentCatalog catalog = pdf.getDocumentCatalog();

        if (catalog == null)
            return;

        PDAcroForm form = catalog.getAcroForm();
        if (form == null)
            return;
        
        @SuppressWarnings("rawtypes")
        List fields = form.getFields();

        if (fields == null)
           return;
        
        @SuppressWarnings("rawtypes")
        ListIterator itr  = fields.listIterator();

        if (itr == null)
           return;

        handler.startElement("div", "class", "acroform");
        handler.startElement("ol");
        while (itr.hasNext()){
           Object obj = itr.next();
           if (obj != null && obj instanceof PDField){
              processAcroField((PDField)obj, handler, 0);
           }
        }
        handler.endElement("ol");
        handler.endElement("div");
    }
    
    private void processAcroField(PDField field, XHTMLContentHandler handler, final int recurseDepth)
            throws SAXException, IOException { 
         
          if (recurseDepth >= MAX_ACROFORM_RECURSIONS){
             return;
          }
          
          addFieldString(field, handler);
          
          @SuppressWarnings("rawtypes")
          List kids = field.getKids();
          if(kids != null){
             
             @SuppressWarnings("rawtypes")
             Iterator kidsIter = kids.iterator();
             if (kidsIter == null){
                return;
             }
             int r = recurseDepth+1;
             handler.startElement("ol");
             while(kidsIter.hasNext()){
                Object pdfObj = kidsIter.next();
                if(pdfObj != null && pdfObj instanceof PDField){
                   PDField kid = (PDField)pdfObj;
                   //recurse
                   processAcroField(kid, handler, r);
                }
             }
             handler.endElement("ol");
          }
      }

      private void addFieldString(PDField field, XHTMLContentHandler handler) throws SAXException{
          //Pick partial name to present in content and altName for attribute
          //Ignoring FullyQualifiedName for now
          String partName = field.getPartialName();
          String altName = field.getAlternateFieldName();

          StringBuilder sb = new StringBuilder();
          AttributesImpl attrs = new AttributesImpl();

          if (partName != null){
             sb.append(partName).append(": ");
          }
          if (altName != null){
             attrs.addAttribute("", "altName", "altName", "CDATA", altName);
          }
          String value = "";
          try {
              value = field.getValue();
          } catch (IOException e) {
               //swallow
          }
          
          if (value != null && ! value.equals("null")){
              sb.append(value);
          }
          if (attrs.getLength() > 0 || sb.length() > 0){
              handler.startElement("li", attrs);
              handler.characters(sb.toString());
              handler.endElement("li");
          }
      }
}
