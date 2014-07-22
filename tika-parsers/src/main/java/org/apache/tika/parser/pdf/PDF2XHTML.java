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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDCcitt;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDJpeg;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDPixelMap;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObjectForm;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObjectImage;
import org.apache.pdfbox.pdmodel.interactive.action.type.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.type.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.util.PDFTextStripper;
import org.apache.pdfbox.util.TextPosition;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
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
     * format used for signature dates
     */
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
 
    /**
     * Maximum recursive depth during AcroForm processing.
     * Prevents theoretical AcroForm recursion bomb. 
     */
    private final static int MAX_ACROFORM_RECURSIONS = 10;


    // TODO: remove once PDFBOX-2160 is fixed:
    private boolean inParagraph = false;

    /**
     * This keeps track of the pdf object ids for inline
     * images that have been processed.  If {@link PDFParserConfig#getExtractUniqueInlineImagesOnly()
     * is true, this will be checked before extracting an embedded image.
     */
    private Set<String> processedInlineImages = new HashSet<String>();


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
            
            config.configure(pdf2XHTML);

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
            if (config.getExtractAcroFormContent() == true) {
                extractAcroForm(pdf, handler);
             }
            handler.endDocument();
        } catch (TikaException e) {
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

            extractImages(page.getResources());

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

    private void extractImages(PDResources resources) throws SAXException {
        if (resources == null || config.getExtractInlineImages() == false) {
            return;
        }

        for (Map.Entry<String, PDXObject> entry : resources.getXObjects().entrySet()) {
                        
            PDXObject object = entry.getValue();
            if (object instanceof PDXObjectForm) {
                extractImages(((PDXObjectForm) object).getResources());
            } else if (object instanceof PDXObjectImage) {
                
                //Do we only want to process unique COSObject ids?
                //If so, have we already processed this one?
                if (config.getExtractUniqueInlineImagesOnly() == true) {
                    String cosObjectId = entry.getKey();
                    if (processedInlineImages.contains(cosObjectId)){
                        continue;
                    }
                    processedInlineImages.add(cosObjectId);
                }

                PDXObjectImage image = (PDXObjectImage) object;

                Metadata metadata = new Metadata();
                if (image instanceof PDJpeg) {
                    metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
                } else if (image instanceof PDCcitt) {
                    metadata.set(Metadata.CONTENT_TYPE, "image/tiff");
                } else if (image instanceof PDPixelMap) {
                    metadata.set(Metadata.CONTENT_TYPE, "image/png");
                }
                metadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, 
                        TikaCoreProperties.EmbeddedResourceType.INLINE.toString());

                EmbeddedDocumentExtractor extractor =
                        getEmbeddedDocumentExtractor();
                if (extractor.shouldParseEmbedded(metadata)) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    try {
                        image.write2OutputStream(buffer);
                        extractor.parseEmbedded(
                                new ByteArrayInputStream(buffer.toByteArray()),
                                new EmbeddedContentHandler(handler),
                                metadata, false);
                    } catch (IOException e) {
                        // could not extract this image, so just skip it...
                    }
                }
            }
        }
    }

    protected EmbeddedDocumentExtractor getEmbeddedDocumentExtractor() {
        EmbeddedDocumentExtractor extractor =
                context.get(EmbeddedDocumentExtractor.class);
        if (extractor == null) {
            extractor = new ParsingEmbeddedDocumentExtractor(context);
        }
        return extractor;
    }

    @Override
    protected void writeParagraphStart() throws IOException {
        // TODO: remove once PDFBOX-2160 is fixed
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
        // TODO: remove once PDFBOX-2160 is fixed
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
        if (names == null) {
            return;
        }
        PDEmbeddedFilesNameTreeNode embeddedFiles = names.getEmbeddedFiles();

        if (embeddedFiles == null) {
            return;
        }

        Map<String, COSObjectable> embeddedFileNames = embeddedFiles.getNames();
        //For now, try to get the embeddedFileNames out of embeddedFiles or its kids.
        //This code follows: pdfbox/examples/pdmodel/ExtractEmbeddedFiles.java
        //If there is a need we could add a fully recursive search to find a non-null
        //Map<String, COSObjectable> that contains the doc info.
        if (embeddedFileNames != null) {
            processEmbeddedDocNames(embeddedFileNames);
        } else {
            List<PDNameTreeNode> kids = embeddedFiles.getKids();
            if (kids == null) {
                return;
            }
            for (PDNameTreeNode n : kids) {
                Map<String, COSObjectable> childNames = n.getNames();
                if (childNames != null) {
                    processEmbeddedDocNames(childNames);
                }
            }
        }
    }


    private void processEmbeddedDocNames(Map<String, COSObjectable> embeddedFileNames)
            throws IOException, SAXException, TikaException {
        if (embeddedFileNames == null || embeddedFileNames.isEmpty()) {
            return;
        }

        EmbeddedDocumentExtractor extractor = getEmbeddedDocumentExtractor();
        for (Map.Entry<String,COSObjectable> ent : embeddedFileNames.entrySet()) {
            PDComplexFileSpecification spec = (PDComplexFileSpecification) ent.getValue();
            if (spec == null) {
                //skip silently
                continue;
            }
            PDEmbeddedFile file = spec.getEmbeddedFile();
            if (file == null) {
                //skip silently
                continue;
            }
            Metadata metadata = new Metadata();
            // TODO: other metadata?
            metadata.set(Metadata.RESOURCE_NAME_KEY, ent.getKey());
            metadata.set(Metadata.CONTENT_TYPE, file.getSubtype());
            metadata.set(Metadata.CONTENT_LENGTH, Long.toString(file.getSize()));
            metadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, 
                    TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString());

            if (extractor.shouldParseEmbedded(metadata)) {
                TikaInputStream stream = TikaInputStream.get(file.createInputStream());
                try {
                    extractor.parseEmbedded(
                            stream,
                            new EmbeddedContentHandler(handler),
                            metadata, false);
                } finally {
                    stream.close();
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

        while (itr.hasNext()) {
            Object obj = itr.next();
            if (obj != null && obj instanceof PDField) {
                processAcroField((PDField)obj, handler, 0);
            }
        }
        handler.endElement("ol");
        handler.endElement("div");
    }

    private void processAcroField(PDField field, XHTMLContentHandler handler, final int currentRecursiveDepth)
            throws SAXException, IOException { 

        if (currentRecursiveDepth >= MAX_ACROFORM_RECURSIONS) {
            return;
        }

        addFieldString(field, handler);

        List<COSObjectable> kids = field.getKids();
        if(kids != null) {

            int r = currentRecursiveDepth+1;
            handler.startElement("ol");
            //TODO: can generate <ol/>. Rework to avoid that.
            for(COSObjectable pdfObj : kids) {
                if(pdfObj != null && pdfObj instanceof PDField) {
                    PDField kid = (PDField)pdfObj;
                    //recurse
                    processAcroField(kid, handler, r);
                }
            }
            handler.endElement("ol");
        }
    }

    private void addFieldString(PDField field, XHTMLContentHandler handler) throws SAXException {
        //Pick partial name to present in content and altName for attribute
        //Ignoring FullyQualifiedName for now
        String partName = field.getPartialName();
        String altName = field.getAlternateFieldName();

        StringBuilder sb = new StringBuilder();
        AttributesImpl attrs = new AttributesImpl();

        if (partName != null) {
            sb.append(partName).append(": ");
        }
        if (altName != null) {
            attrs.addAttribute("", "altName", "altName", "CDATA", altName);
        }
        //return early if PDSignature field
        if (field instanceof PDSignatureField) {
            handleSignature(attrs, (PDSignatureField)field, handler);
            return;
        }
        try {
            //getValue can throw an IOException if there is no value
            String value = field.getValue();
            if (value != null && ! value.equals("null")) {
                sb.append(value);
            }
        } catch (IOException e) {
            //swallow
        } catch (NullPointerException e) {
            //TODO: remove once PDFBOX-2161 is fixed
        }

        if (attrs.getLength() > 0 || sb.length() > 0) {
            handler.startElement("li", attrs);
            handler.characters(sb.toString());
            handler.endElement("li");
        }
    }

    private void handleSignature(AttributesImpl parentAttributes, PDSignatureField sigField,
            XHTMLContentHandler handler) throws SAXException {


        PDSignature sig = sigField.getSignature();
        if (sig == null) {
            return;
        }
        Map<String, String> vals= new TreeMap<String, String>();
        vals.put("name", sig.getName());
        vals.put("contactInfo", sig.getContactInfo());
        vals.put("location", sig.getLocation());
        vals.put("reason", sig.getReason());

        Calendar cal = sig.getSignDate();
        if (cal != null) {
            dateFormat.setTimeZone(cal.getTimeZone());
            vals.put("date", dateFormat.format(cal.getTime()));
        }
        //see if there is any data
        int nonNull = 0;
        for (String val : vals.keySet()) {
            if (val != null && ! val.equals("")) {
                nonNull++;
            }
        }
        //if there is, process it
        if (nonNull > 0) {
            handler.startElement("li", parentAttributes);

            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute("", "type", "type", "CDATA", "signaturedata");

            handler.startElement("ol", attrs);
            for (Map.Entry<String, String> e : vals.entrySet()) {
                if (e.getValue() == null || e.getValue().equals("")) {
                    continue;
                }
                attrs = new AttributesImpl();
                attrs.addAttribute("", "signdata", "signdata", "CDATA", e.getKey());
                handler.startElement("li", attrs);
                handler.characters(e.getValue());
                handler.endElement("li");
            }
            handler.endElement("ol");
            handler.endElement("li");
        }
    }
}

