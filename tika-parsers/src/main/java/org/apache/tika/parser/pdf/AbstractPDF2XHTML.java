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

import static org.apache.tika.parser.pdf.PDFParserConfig.OCR_STRATEGY.NO_OCR;

import javax.xml.stream.XMLStreamException;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.IOExceptionWithCause;
import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDDestinationOrAction;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.common.filespecification.PDFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDSimpleFileSpecification;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionImportData;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionLaunch;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionRemoteGoTo;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.action.PDAnnotationAdditionalActions;
import org.apache.pdfbox.pdmodel.interactive.action.PDDocumentCatalogAdditionalActions;
import org.apache.pdfbox.pdmodel.interactive.action.PDFormFieldAdditionalActions;
import org.apache.pdfbox.pdmodel.interactive.action.PDPageAdditionalActions;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFileAttachment;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.form.PDXFAResource;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

class AbstractPDF2XHTML extends PDFTextStripper {

    enum ActionTrigger {
        AFTER_DOCUMENT_PRINT,
        AFTER_DOCUMENT_SAVE,
        ANNOTATION_CURSOR_ENTERS,
        ANNOTATION_CURSOR_EXIT,
        ANNOTATION_LOSE_INPUT_FOCUS,
        ANNOTATION_MOUSE_CLICK,
        ANNOTATION_MOUSE_RELEASED,
        ANNOTATION_PAGE_CLOSED,
        ANNOTATION_PAGE_NO_LONGER_VISIBLE,
        ANNOTATION_PAGE_OPENED,
        ANNOTATION_PAGE_VISIBLE,
        ANNOTATION_RECEIVES_FOCUS,
        ANNOTATION_WIDGET,
        BEFORE_DOCUMENT_CLOSE,
        BEFORE_DOCUMENT_PRINT,
        BEFORE_DOCUMENT_SAVE,
        DOCUMENT_OPEN,
        FORM_FIELD,
        FORM_FIELD_FORMATTED,
        FORM_FIELD_KEYSTROKE,
        FORM_FIELD_RECALCULATE,
        FORM_FIELD_VALUE_CHANGE,
        PAGE_CLOSE,
        PAGE_OPEN, BOOKMARK,
    };

    /**
     * Maximum recursive depth during AcroForm processing.
     * Prevents theoretical AcroForm recursion bomb.
     */
    private final static int MAX_ACROFORM_RECURSIONS = 10;

    private final static TesseractOCRConfig DEFAULT_TESSERACT_CONFIG = new TesseractOCRConfig();

    /**
     * Format used for signature dates
     * TODO Make this thread-safe
     */
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT);


    final List<IOException> exceptions = new ArrayList<>();
    final PDDocument pdDocument;
    final XHTMLContentHandler xhtml;
    private final ParseContext context;
    final Metadata metadata;
    final EmbeddedDocumentExtractor embeddedDocumentExtractor;
    final PDFParserConfig config;

    private int pageIndex = 0;

    AbstractPDF2XHTML(PDDocument pdDocument, ContentHandler handler, ParseContext context, Metadata metadata,
                      PDFParserConfig config) throws IOException {
        this.pdDocument = pdDocument;
        this.xhtml = new XHTMLContentHandler(handler, metadata);
        this.context = context;
        this.metadata = metadata;
        this.config = config;
        embeddedDocumentExtractor = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        try {
            xhtml.startElement("div", "class", "page");
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to start a page", e);
        }
        writeParagraphStart();
    }

    private void extractEmbeddedDocuments(PDDocument document)
            throws IOException, SAXException, TikaException {
            PDDocumentNameDictionary namesDictionary =
                    new PDDocumentNameDictionary(document.getDocumentCatalog());
            PDEmbeddedFilesNameTreeNode efTree = namesDictionary.getEmbeddedFiles();
            if (efTree == null) {
                return;
            }

        Map<String, PDComplexFileSpecification> embeddedFileNames = efTree.getNames();
        //For now, try to get the embeddedFileNames out of embeddedFiles or its kids.
        //This code follows: pdfbox/examples/pdmodel/ExtractEmbeddedFiles.java
        //If there is a need we could add a fully recursive search to find a non-null
        //Map<String, COSObjectable> that contains the doc info.
        if (embeddedFileNames != null) {
            processEmbeddedDocNames(embeddedFileNames);
        } else {
            List<PDNameTreeNode<PDComplexFileSpecification>> kids = efTree.getKids();
            if (kids == null) {
                return;
            }
            for (PDNameTreeNode<PDComplexFileSpecification> node : kids) {
                embeddedFileNames = node.getNames();
                if (embeddedFileNames != null) {
                    processEmbeddedDocNames(embeddedFileNames);
                }
            }
        }
    }

    private void processDoc(String name, PDFileSpecification spec, AttributesImpl attributes) throws TikaException, SAXException, IOException {
        if (spec instanceof PDSimpleFileSpecification) {
            attributes.addAttribute("", "class", "class", "CDATA", "linked");
            attributes.addAttribute("", "id", "id", "CDATA", spec.getFile());
            xhtml.startElement("div", attributes);
            xhtml.endElement("div");
        } else if (spec instanceof  PDComplexFileSpecification){
            if (attributes.getIndex("source") < 0) {
                attributes.addAttribute("", "source", "source", "CDATA", "attachment");
            }
            extractMultiOSPDEmbeddedFiles(name, (PDComplexFileSpecification)spec, attributes);
        }
    }

    private void processEmbeddedDocNames(Map<String, PDComplexFileSpecification> embeddedFileNames)
            throws IOException, SAXException, TikaException {
        if (embeddedFileNames == null || embeddedFileNames.isEmpty()) {
            return;
        }

        for (Map.Entry<String, PDComplexFileSpecification> ent : embeddedFileNames.entrySet()) {
            processDoc(ent.getKey(), ent.getValue(), new AttributesImpl());
        }
    }

    private void extractMultiOSPDEmbeddedFiles(String displayName,
                                       PDComplexFileSpecification spec, AttributesImpl attributes) throws IOException,
            SAXException, TikaException {

        if (spec == null) {
            return;
        }
        //current strategy is to pull all, not just first non-null
        extractPDEmbeddedFile(displayName, spec.getFileUnicode(),
                spec.getFile(), spec.getEmbeddedFile(), attributes);
        extractPDEmbeddedFile(displayName, spec.getFileUnicode(),
                spec.getFileMac(), spec.getEmbeddedFileMac(), attributes);
        extractPDEmbeddedFile(displayName, spec.getFileUnicode(),
                spec.getFileDos(), spec.getEmbeddedFileDos(), attributes);
        extractPDEmbeddedFile(displayName, spec.getFileUnicode(),
                spec.getFileUnix(), spec.getEmbeddedFileUnix(), attributes);
    }

    private void extractPDEmbeddedFile(String displayName, String unicodeFileName,
                                       String fileName, PDEmbeddedFile file, AttributesImpl attributes)
            throws SAXException, IOException, TikaException {

        if (file == null) {
            //skip silently
            return;
        }

        fileName = (fileName == null || "".equals(fileName.trim())) ? unicodeFileName : fileName;
        fileName = (fileName == null || "".equals(fileName.trim())) ? displayName : fileName;

        // TODO: other metadata?
        Metadata embeddedMetadata = new Metadata();
        embeddedMetadata.set(Metadata.RESOURCE_NAME_KEY, fileName);
        embeddedMetadata.set(Metadata.CONTENT_TYPE, file.getSubtype());
        embeddedMetadata.set(Metadata.CONTENT_LENGTH, Long.toString(file.getSize()));
        embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString());
        embeddedMetadata.set(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, fileName);
        if (!embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata)) {
            return;
        }
        TikaInputStream stream = null;
        try {
            stream = TikaInputStream.get(file.createInputStream());
        } catch (IOException e) {
            //store this exception in the parent's metadata
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
            return;
        }
        try {
            embeddedDocumentExtractor.parseEmbedded(
                    stream,
                    new EmbeddedContentHandler(xhtml),
                    embeddedMetadata, false);

            attributes.addAttribute("", "class", "class", "CDATA", "embedded");
            attributes.addAttribute("", "id", "id", "CDATA", fileName);
            xhtml.startElement("div", attributes);
            xhtml.endElement("div");
        } finally {
            IOUtils.closeQuietly(stream);
        }

    }

    void handleCatchableIOE(IOException e) throws IOException {
        if (config.isCatchIntermediateIOExceptions()) {
            if (e.getCause() instanceof SAXException && e.getCause().getMessage() != null &&
                    e.getCause().getMessage().contains("Your document contained more than")) {
                //TODO -- is there a cleaner way of checking for:
                // WriteOutContentHandler.WriteLimitReachedException?
                throw e;
            }

            String msg = e.getMessage();
            if (msg == null) {
                msg = "IOException, no message";
            }
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, msg);
            exceptions.add(e);
        } else {
            throw e;
        }
    }

    void doOCROnCurrentPage() throws IOException, TikaException, SAXException {
        if (config.getOcrStrategy().equals(NO_OCR)) {
            return;
        }
        TesseractOCRConfig tesseractConfig =
                context.get(TesseractOCRConfig.class, DEFAULT_TESSERACT_CONFIG);

        TesseractOCRParser tesseractOCRParser = new TesseractOCRParser();
        if (! tesseractOCRParser.hasTesseract(tesseractConfig)) {
            throw new TikaException("Tesseract is not available. "+
                    "Please set the OCR_STRATEGY to NO_OCR or configure Tesseract correctly");
        }

        PDFRenderer renderer = new PDFRenderer(pdDocument);
        TemporaryResources tmp = new TemporaryResources();
        try {

            BufferedImage image = renderer.renderImage(pageIndex, config.getOcrImageScale(), config.getOcrImageType());
            Path tmpFile = tmp.createTempFile();
            try (OutputStream os = Files.newOutputStream(tmpFile)) {
                //TODO: get output format from TesseractConfig
                ImageIOUtil.writeImage(image, config.getOcrImageFormatName(),
                        os, config.getOcrDPI(), config.getOcrImageQuality());
            }
            try (InputStream is = TikaInputStream.get(tmpFile)) {
                tesseractOCRParser.parseInline(is, xhtml, tesseractConfig);
            }
        } catch (IOException e) {
            handleCatchableIOE(e);
        } catch (SAXException e) {
            throw new IOExceptionWithCause("error writing OCR content from PDF", e);
        } finally {
            tmp.dispose();
        }
    }

    @Override
    protected void endPage(PDPage page) throws IOException {

        try {
            for (PDAnnotation annotation : page.getAnnotations()) {

                if (annotation instanceof PDAnnotationFileAttachment) {
                    PDAnnotationFileAttachment fann = (PDAnnotationFileAttachment) annotation;
                    PDComplexFileSpecification fileSpec = (PDComplexFileSpecification) fann.getFile();
                    try {
                        AttributesImpl attributes = new AttributesImpl();
                        attributes.addAttribute("", "source", "source", "CDATA", "annotation");
                        extractMultiOSPDEmbeddedFiles(fann.getAttachmentName(), fileSpec, attributes);
                    } catch (SAXException e) {
                        throw new IOExceptionWithCause("file embedded in annotation sax exception", e);
                    } catch (TikaException e) {
                        throw new IOExceptionWithCause("file embedded in annotation tika exception", e);
                    } catch (IOException e) {
                        handleCatchableIOE(e);
                    }
                } else if (annotation instanceof PDAnnotationWidget) {
                    handleWidget((PDAnnotationWidget)annotation);
                }
                // TODO: remove once PDFBOX-1143 is fixed:
                if (config.getExtractAnnotationText()) {
                    PDActionURI uri = getActionURI(annotation);
                    if (uri != null) {
                        String link = uri.getURI();
                        if (link != null && link.trim().length() > 0) {
                            xhtml.startElement("div", "class", "annotation");
                            xhtml.startElement("a", "href", link);
                            xhtml.characters(link);
                            xhtml.endElement("a");
                            xhtml.endElement("div");
                        }
                    }

                    if (annotation instanceof PDAnnotationMarkup) {
                        PDAnnotationMarkup annotationMarkup = (PDAnnotationMarkup) annotation;
                        String title = annotationMarkup.getTitlePopup();
                        String subject = annotationMarkup.getSubject();
                        String contents = annotationMarkup.getContents();
                        // TODO: maybe also annotationMarkup.getRichContents()?
                        if (title != null || subject != null || contents != null) {
                            xhtml.startElement("div", "class", "annotation");

                            if (title != null) {
                                xhtml.startElement("div", "class", "annotationTitle");
                                xhtml.characters(title);
                                xhtml.endElement("div");
                            }

                            if (subject != null) {
                                xhtml.startElement("div", "class", "annotationSubject");
                                xhtml.characters(subject);
                                xhtml.endElement("div");
                            }

                            if (contents != null) {
                                xhtml.startElement("div", "class", "annotationContents");
                                xhtml.characters(contents);
                                xhtml.endElement("div");
                            }

                            xhtml.endElement("div");
                        }
                    }
                }
            }
            if (config.getOcrStrategy().equals(PDFParserConfig.OCR_STRATEGY.OCR_AND_TEXT_EXTRACTION)) {
                doOCROnCurrentPage();
            }

            PDPageAdditionalActions pageActions = page.getActions();
            if (pageActions != null) {
                handleDestinationOrAction(pageActions.getC(), ActionTrigger.PAGE_CLOSE);
                handleDestinationOrAction(pageActions.getO(), ActionTrigger.PAGE_OPEN);
            }
            xhtml.endElement("div");
        } catch (SAXException|TikaException e) {
            throw new IOExceptionWithCause("Unable to end a page", e);
        } catch (IOException e) {
            exceptions.add(e);
        } finally {
            pageIndex++;
        }
    }

    private void handleWidget(PDAnnotationWidget widget) throws TikaException, SAXException, IOException {
        if (widget == null) {
            return;
        }
        handleDestinationOrAction(widget.getAction(), ActionTrigger.ANNOTATION_WIDGET);
        PDAnnotationAdditionalActions annotationActions = widget.getActions();
        if (annotationActions != null) {
            handleDestinationOrAction(annotationActions.getBl(), ActionTrigger.ANNOTATION_LOSE_INPUT_FOCUS);
            handleDestinationOrAction(annotationActions.getD(), ActionTrigger.ANNOTATION_MOUSE_CLICK);
            handleDestinationOrAction(annotationActions.getE(), ActionTrigger.ANNOTATION_CURSOR_ENTERS);
            handleDestinationOrAction(annotationActions.getFo(), ActionTrigger.ANNOTATION_RECEIVES_FOCUS);
            handleDestinationOrAction(annotationActions.getPC(), ActionTrigger.ANNOTATION_PAGE_CLOSED);
            handleDestinationOrAction(annotationActions.getPI(), ActionTrigger.ANNOTATION_PAGE_NO_LONGER_VISIBLE);
            handleDestinationOrAction(annotationActions.getPO(), ActionTrigger.ANNOTATION_PAGE_OPENED);
            handleDestinationOrAction(annotationActions.getPV(), ActionTrigger.ANNOTATION_PAGE_VISIBLE);
            handleDestinationOrAction(annotationActions.getU(), ActionTrigger.ANNOTATION_MOUSE_RELEASED);
            handleDestinationOrAction(annotationActions.getX(), ActionTrigger.ANNOTATION_CURSOR_EXIT);
        }

    }

    @Override
    protected void startDocument(PDDocument pdf) throws IOException {
        try {
            xhtml.startDocument();
            try {
                handleDestinationOrAction(pdf.getDocumentCatalog().getOpenAction(), ActionTrigger.DOCUMENT_OPEN);
            } catch (IOException e) {
                //See PDFBOX-3773
                //swallow -- no need to report this
            }
        } catch (TikaException|SAXException e) {
            throw new IOExceptionWithCause("Unable to start a document", e);
        }
    }

    private void handleDestinationOrAction(PDDestinationOrAction action,
                                           ActionTrigger actionTrigger) throws IOException, SAXException, TikaException {
        if (action == null || ! config.getExtractActions()) {
            return;
        }
        AttributesImpl attributes = new AttributesImpl();
        String actionOrDestString = (action instanceof PDAction) ? "action" : "destination";

        addNonNullAttribute("class",  actionOrDestString, attributes);
        addNonNullAttribute("type", action.getClass().getSimpleName(), attributes);
        addNonNullAttribute("trigger", actionTrigger.name(), attributes);

        if (action instanceof PDActionImportData) {
            processDoc("", ((PDActionImportData)action).getFile(), attributes);
        } else if (action instanceof PDActionLaunch) {
            PDActionLaunch pdActionLaunch = (PDActionLaunch)action;
            addNonNullAttribute("id", pdActionLaunch.getF(), attributes);
            addNonNullAttribute("defaultDirectory", pdActionLaunch.getD(), attributes);
            addNonNullAttribute("operation", pdActionLaunch.getO(), attributes);
            addNonNullAttribute("parameters", pdActionLaunch.getP(), attributes);
            processDoc(pdActionLaunch.getF(), pdActionLaunch.getFile(), attributes);
        } else if (action instanceof PDActionRemoteGoTo) {
            PDActionRemoteGoTo remoteGoTo = (PDActionRemoteGoTo)action;
            processDoc("", remoteGoTo.getFile(), attributes);
        } else if (action instanceof PDActionJavaScript) {
            PDActionJavaScript jsAction = (PDActionJavaScript)action;
            Metadata m = new Metadata();
            m.set(Metadata.CONTENT_TYPE, "application/javascript");
            m.set(Metadata.CONTENT_ENCODING, StandardCharsets.UTF_8.toString());
            m.set(PDF.ACTION_TRIGGER, actionTrigger.toString());
            m.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, TikaCoreProperties.EmbeddedResourceType.MACRO.name());
            String js = jsAction.getAction();
            js = (js == null) ? "" : js;
            if (embeddedDocumentExtractor.shouldParseEmbedded(m)) {
                try (InputStream is = TikaInputStream.get(js.getBytes(StandardCharsets.UTF_8))) {
                    embeddedDocumentExtractor.parseEmbedded(is, xhtml, m, false);
                }
            }
            addNonNullAttribute("class", "javascript", attributes);
            addNonNullAttribute("type", jsAction.getType(), attributes);
            addNonNullAttribute("subtype", jsAction.getSubType(), attributes);
            xhtml.startElement("div", attributes);
            xhtml.endElement("div");
        } else {
            xhtml.startElement("div", attributes);
            xhtml.endElement("div");
        }
    }

    private static void addNonNullAttribute(String name, String value, AttributesImpl attributes) {
        if (name == null || value == null) {
            return;
        }
        attributes.addAttribute("", name, name, "CDATA", value);
    }

    @Override
    protected void endDocument(PDDocument pdf) throws IOException {
        try {
            // Extract text for any bookmarks:
			if(config.getExtractBookmarksText()) {
                extractBookmarkText();
            }

            try {
                extractEmbeddedDocuments(pdf);
            } catch (IOException e) {
                handleCatchableIOE(e);
            }

            //extract acroform data at end of doc
            if (config.getExtractAcroFormContent() == true) {
                try {
                    extractAcroForm(pdf);
                } catch (IOException e) {
                    handleCatchableIOE(e);
                }
            }
            PDDocumentCatalogAdditionalActions additionalActions = pdf.getDocumentCatalog().getActions();
            handleDestinationOrAction(additionalActions.getDP(), ActionTrigger.AFTER_DOCUMENT_PRINT);
            handleDestinationOrAction(additionalActions.getDS(), ActionTrigger.AFTER_DOCUMENT_SAVE);
            handleDestinationOrAction(additionalActions.getWC(), ActionTrigger.BEFORE_DOCUMENT_CLOSE);
            handleDestinationOrAction(additionalActions.getWP(), ActionTrigger.BEFORE_DOCUMENT_PRINT);
            handleDestinationOrAction(additionalActions.getWS(), ActionTrigger.BEFORE_DOCUMENT_SAVE);
            xhtml.endDocument();
        } catch (TikaException e) {
            throw new IOExceptionWithCause("Unable to end a document", e);
        } catch (SAXException e) {
            throw new IOExceptionWithCause("Unable to end a document", e);
        }
    }

    void extractBookmarkText() throws SAXException, IOException, TikaException {
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline != null) {
            extractBookmarkText(outline);
        }
    }

    void extractBookmarkText(PDOutlineNode bookmark) throws SAXException, IOException, TikaException {
        PDOutlineItem current = bookmark.getFirstChild();

        if (current != null) {
            xhtml.startElement("ul");
            while (current != null) {
                xhtml.startElement("li");
                xhtml.characters(current.getTitle());
                xhtml.endElement("li");
                handleDestinationOrAction(current.getAction(), ActionTrigger.BOOKMARK);
                // Recurse:
                extractBookmarkText(current);
                current = current.getNextSibling();
            }
            xhtml.endElement("ul");
        }
    }

    void extractAcroForm(PDDocument pdf) throws IOException,
            SAXException, TikaException {
        //Thank you, Ben Litchfield, for org.apache.pdfbox.examples.fdf.PrintFields
        //this code derives from Ben's code
        PDDocumentCatalog catalog = pdf.getDocumentCatalog();

        if (catalog == null)
            return;

        PDAcroForm form = catalog.getAcroForm();
        if (form == null)
            return;

        //if it has xfa, try that.
        //if it doesn't exist or there's an exception,
        //go with traditional AcroForm
        PDXFAResource pdxfa = form.getXFA();

        if (pdxfa != null) {
            //if successful, return
            XFAExtractor xfaExtractor = new XFAExtractor();
            InputStream is = null;
            try {
                is = new BufferedInputStream(
                        new ByteArrayInputStream(pdxfa.getBytes()));
            } catch (IOException e) {
                EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
            }
            if (is != null) {
                try {
                    xfaExtractor.extract(is, xhtml, metadata, context);
                    return;
                } catch (XMLStreamException e) {
                    //if there was an xml parse exception in xfa, try the AcroForm
                    EmbeddedDocumentUtil.recordException(e, metadata);
                } finally {
                    IOUtils.closeQuietly(is);
                }
            }
        }

        @SuppressWarnings("rawtypes")
        List fields = form.getFields();

        if (fields == null)
            return;

        @SuppressWarnings("rawtypes")
        ListIterator itr = fields.listIterator();

        if (itr == null)
            return;

        xhtml.startElement("div", "class", "acroform");
        xhtml.startElement("ol");

        while (itr.hasNext()) {
            Object obj = itr.next();
            if (obj != null && obj instanceof PDField) {
                processAcroField((PDField) obj, 0);
            }
        }
        xhtml.endElement("ol");
        xhtml.endElement("div");
    }

    private void processAcroField(PDField field, final int currentRecursiveDepth)
            throws SAXException, IOException, TikaException {

        if (currentRecursiveDepth >= MAX_ACROFORM_RECURSIONS) {
            return;
        }

        PDFormFieldAdditionalActions pdFormFieldAdditionalActions = field.getActions();
        if (pdFormFieldAdditionalActions != null) {
            handleDestinationOrAction(pdFormFieldAdditionalActions.getC(), ActionTrigger.FORM_FIELD_RECALCULATE);
            handleDestinationOrAction(pdFormFieldAdditionalActions.getF(), ActionTrigger.FORM_FIELD_FORMATTED);
            handleDestinationOrAction(pdFormFieldAdditionalActions.getK(), ActionTrigger.FORM_FIELD_KEYSTROKE);
            handleDestinationOrAction(pdFormFieldAdditionalActions.getV(), ActionTrigger.FORM_FIELD_VALUE_CHANGE);
        }
        if (field.getWidgets() != null) {
            for (PDAnnotationWidget widget : field.getWidgets()) {
                handleWidget(widget);
            }
        }


        addFieldString(field);
        if (field instanceof PDNonTerminalField) {
            int r = currentRecursiveDepth + 1;
            xhtml.startElement("ol");
            for (PDField child : ((PDNonTerminalField)field).getChildren()) {
                processAcroField(child, r);
            }
            xhtml.endElement("ol");
        }
    }

    private void addFieldString(PDField field) throws SAXException {
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
            handleSignature(attrs, (PDSignatureField) field);
            return;
        }
        String value = field.getValueAsString();
        if (value != null && !value.equals("null")) {
            sb.append(value);
        }

        if (attrs.getLength() > 0 || sb.length() > 0) {
            xhtml.startElement("li", attrs);
            xhtml.characters(sb.toString());
            xhtml.endElement("li");
        }
    }

    private void handleSignature(AttributesImpl parentAttributes, PDSignatureField sigField)
            throws SAXException {

        PDSignature sig = sigField.getSignature();
        if (sig == null) {
            return;
        }
        Map<String, String> vals = new TreeMap<>();
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
            if (val != null && !val.equals("")) {
                nonNull++;
            }
        }
        //if there is, process it
        if (nonNull > 0) {
            xhtml.startElement("li", parentAttributes);

            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute("", "type", "type", "CDATA", "signaturedata");

            xhtml.startElement("ol", attrs);
            for (Map.Entry<String, String> e : vals.entrySet()) {
                if (e.getValue() == null || e.getValue().equals("")) {
                    continue;
                }
                attrs = new AttributesImpl();
                attrs.addAttribute("", "signdata", "signdata", "CDATA", e.getKey());
                xhtml.startElement("li", attrs);
                xhtml.characters(e.getValue());
                xhtml.endElement("li");
            }
            xhtml.endElement("ol");
            xhtml.endElement("li");
        }
    }


    private static PDActionURI getActionURI(PDAnnotation annot) {
        //copied and pasted from PDFBox's PrintURLs

        // use reflection to catch all annotation types that have getAction()
        // If you can't use reflection, then check for classes
        // PDAnnotationLink and PDAnnotationWidget, and call getAction() and check for a
        // PDActionURI result type
        try {
            Method actionMethod = annot.getClass().getDeclaredMethod("getAction");
            if (actionMethod.getReturnType().equals(PDAction.class)) {
                PDAction action = (PDAction) actionMethod.invoke(annot);
                if (action instanceof PDActionURI) {
                    return (PDActionURI) action;
                }
            }
        }
        catch (NoSuchMethodException|IllegalAccessException|InvocationTargetException e) {
        }
        return null;
    }
}
