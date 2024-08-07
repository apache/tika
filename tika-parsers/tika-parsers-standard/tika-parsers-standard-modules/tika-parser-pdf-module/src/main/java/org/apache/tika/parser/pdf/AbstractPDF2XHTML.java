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

import static org.apache.tika.parser.pdf.PDFParserConfig.OCR_STRATEGY.AUTO;
import static org.apache.tika.parser.pdf.PDFParserConfig.OCR_STRATEGY.NO_OCR;
import static org.apache.tika.parser.pdf.PDFParserConfig.OCR_STRATEGY.OCR_AND_TEXT_EXTRACTION;
import static org.apache.tika.parser.pdf.PDFParserConfig.OCR_STRATEGY.OCR_ONLY;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.PDDestinationOrAction;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.common.filespecification.PDFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDSimpleFileSpecification;
import org.apache.pdfbox.pdmodel.font.PDFont;
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
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Font;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.updates.IncrementalUpdateRecord;
import org.apache.tika.parser.pdf.updates.IsIncrementalUpdate;
import org.apache.tika.parser.pdf.updates.StartXRefOffset;
import org.apache.tika.renderer.CompositeRenderer;
import org.apache.tika.renderer.PageBasedRenderResults;
import org.apache.tika.renderer.PageRangeRequest;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.renderer.RenderingTracker;
import org.apache.tika.renderer.pdf.pdfbox.NoTextPDFRenderer;
import org.apache.tika.renderer.pdf.pdfbox.PDDocumentRenderer;
import org.apache.tika.renderer.pdf.pdfbox.PDFRenderingState;
import org.apache.tika.renderer.pdf.pdfbox.TextOnlyPDFRenderer;
import org.apache.tika.renderer.pdf.pdfbox.VectorGraphicsOnlyPDFRenderer;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.StringUtils;

class AbstractPDF2XHTML extends PDFTextStripper {

    public static final String XMP_DOCUMENT_CATALOG_LOCATION = "documentCatalog";

    public static final String XMP_PAGE_LOCATION_PREFIX = "page ";
    /**
     * Maximum recursive depth to prevent cycles/recursion bombs.
     * This applies to AcroForm processing and processing
     * the embedded document tree.
     */
    private final static int MAX_RECURSION_DEPTH = 100;
    private final static int MAX_BOOKMARK_ITEMS = 10000;

    //This is used for both types and subtypes.
    //These can be unbounded.  We need to limit the number we store.
    private final static int MAX_ANNOTATION_TYPES = 100;
    private static final String THREE_D = "3D";
    private static final COSName THREE_DD = COSName.getPDFName("3DD");
    private static final String NULL_STRING = "null";
    private static final MediaType XFA_MEDIA_TYPE = MediaType.application("vnd.adobe.xdp+xml");
    private static final MediaType XMP_MEDIA_TYPE = MediaType.application("rdf+xml");

    final List<IOException> exceptions = new ArrayList<>();
    final PDDocument pdDocument;
    final XHTMLContentHandler xhtml;
    final ParseContext context;
    final Metadata metadata;
    final EmbeddedDocumentExtractor embeddedDocumentExtractor;
    final PDFParserConfig config;
    final Parser ocrParser;
    /**
     * Format used for signature dates
     * TODO Make this thread-safe
     */
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT);
    private final Set<String> fontNames = new TreeSet<>();
    private final Set<String> annotationTypes = new TreeSet<>();
    private final Set<String> annotationSubtypes = new TreeSet<>();

    private final Set<String> triggers = new TreeSet<>();

    private final Set<String> actionTypes = new TreeSet<>();

    //these are files that we extract as part of Annotations
    //We don't want to extract them twice when we go through the
    //full DOM looking for /Type = /EmbeddedFile
    private final Set<COSBase> extractedFiles = new HashSet<>();
    //zero-based pageIndex
    int pageIndex = 0;
    int startPage = -1;
    //private in PDFTextStripper...must have own copy because we override processpages
    int unmappedUnicodeCharsPerPage = 0;
    int totalCharsPerPage = 0;

    int totalUnmappedUnicodeCharacters;
    int totalCharacters;

    //contains at least one font that is not embedded
    boolean containsNonEmbeddedFont = false;

    //contains at least one broken font
    boolean containsDamagedFont = false;

    int num3DAnnotations = 0;

    AbstractPDF2XHTML(PDDocument pdDocument, ContentHandler handler, ParseContext context,
                      Metadata metadata, PDFParserConfig config) throws IOException {
        this.pdDocument = pdDocument;
        this.xhtml = new XHTMLContentHandler(handler, metadata);
        this.context = context;
        this.metadata = metadata;
        this.config = config;
        embeddedDocumentExtractor = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        if (config.getOcrStrategy() == NO_OCR) {
            ocrParser = null;
        } else {
            ocrParser = EmbeddedDocumentUtil.getStatelessParser(context);
        }
    }

    private static void addNonNullAttribute(String name, String value, AttributesImpl attributes) {
        if (name == null || value == null) {
            return;
        }
        attributes.addAttribute("", name, name, "CDATA", value);
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
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            //swallow
        }
        return null;
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        try {
            xhtml.startElement("div", "class", "page");
        } catch (SAXException e) {
            throw new IOException("Unable to start a page", e);
        }
        writeParagraphStart();
    }

    private void extractXMPXFA(PDDocument pdfDocument, Metadata parentMetadata,
                               ParseContext context) throws IOException, SAXException {
        Set<MediaType> supportedTypes = Collections.EMPTY_SET;
        Parser embeddedParser = context.get(Parser.class);
        if (embeddedParser != null) {
            supportedTypes = embeddedParser.getSupportedTypes(context);
        }

        if (supportedTypes == null || supportedTypes.size() == 0) {
            return;
        }

        if (supportedTypes.contains(XMP_MEDIA_TYPE)) {
            //try the main metadata
            if (pdfDocument.getDocumentCatalog().getMetadata() != null) {
                try (InputStream is = pdfDocument.getDocumentCatalog().getMetadata()
                        .exportXMPMetadata()) {
                    extractXMPAsEmbeddedFile(is, XMP_DOCUMENT_CATALOG_LOCATION);
                } catch (IOException e) {
                    EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
                }
            }
            //now iterate through the pages
            int pageNumber = 1;
            for (PDPage page : pdfDocument.getPages()) {
                if (page.getMetadata() != null) {
                    try (InputStream is = page.getMetadata().exportXMPMetadata()) {
                        extractXMPAsEmbeddedFile(is, XMP_PAGE_LOCATION_PREFIX + pageNumber);
                    } catch (IOException e) {
                        EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
                    }
                }
                pageNumber++;
            }
        }

        //now try the xfa
        if (pdfDocument.getDocumentCatalog().getAcroForm(null) != null &&
                pdfDocument.getDocumentCatalog().getAcroForm(null).getXFA() != null) {

            Metadata xfaMetadata = new Metadata();
            xfaMetadata.set(Metadata.CONTENT_TYPE, XFA_MEDIA_TYPE.toString());
            xfaMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                    TikaCoreProperties.EmbeddedResourceType.METADATA.toString());
            if (embeddedDocumentExtractor.shouldParseEmbedded(xfaMetadata) &&
                    supportedTypes.contains(XFA_MEDIA_TYPE)) {
                byte[] bytes = null;
                try {
                    bytes = pdfDocument.getDocumentCatalog().getAcroForm(null).getXFA().getBytes();
                } catch (IOException e) {
                    EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
                }
                if (bytes != null) {
                    try (InputStream is = new UnsynchronizedByteArrayInputStream(bytes)) {
                        parseMetadata(is, xfaMetadata);
                    }
                }
            }
        }
    }

    private void extractXMPAsEmbeddedFile(InputStream is, String location)
            throws IOException, SAXException {
        if (is == null) {
            return;
        }
        Metadata xmpMetadata = new Metadata();
        xmpMetadata.set(Metadata.CONTENT_TYPE, XMP_MEDIA_TYPE.toString());
        xmpMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.METADATA.toString());
        xmpMetadata.set(PDF.XMP_LOCATION, location);
        if (embeddedDocumentExtractor.shouldParseEmbedded(xmpMetadata)) {
            try {
                parseMetadata(is, xmpMetadata);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }

    }

    private void parseMetadata(InputStream stream, Metadata embeddedMetadata)
            throws IOException, SAXException {
        try {
            embeddedDocumentExtractor.parseEmbedded(stream, new EmbeddedContentHandler(xhtml),
                    embeddedMetadata, true);
        } catch (IOException e) {
            handleCatchableIOE(e);
        }
    }

    private void extractEmbeddedDocuments(PDDocument document)
            throws IOException, SAXException, TikaException {
        //See 14.13.10 for the 2.0 spec.  Associated files can show up in lots of places...even
        // streams.
        // It would be great to get more context from the /AF info, but we risk missing files
        //if we don't look everywhere.  With the current method, we're at least getting all
        //filespecs at the cost of losing context (to what was this file attached: doc, page,
        // stream, etc?).

        //find all Filespecs TIKA-4012
        List<COSObject> objs = document.getDocument().getObjectsByType(COSName.FILESPEC);
        Set<COSBase> seen = new HashSet<>();
        for (COSObject obj : objs) {
            processDoc("", "", createFileSpecification(obj.getObject()), new AttributesImpl());
            seen.add(obj.getObject());
        }

        //now go through the embedded files names tree to get those rare cases where
        //a file (instead of a filespec) is attached directly to the names tree
        //or where the filespec is a direct object

        if (document.getDocumentCatalog() == null) {
            return;
        }
        if (document.getDocumentCatalog().getNames() == null) {
            return;
        }
        if (document.getDocumentCatalog().getNames().getEmbeddedFiles() == null) {
            return;
        }
        //use a list instead of a name-based map in case there are key collisions
        //that could hide attachments
        List<NameSpecTuple> specs = new ArrayList<>();
        extractFilesfromEFTree(document.getDocumentCatalog().getNames().getEmbeddedFiles(), specs,
                0);
        //this avoids duplication with the above /FileSpec searching, but also in the case
        //where the same underlying file has different names in the EFTree
        for (NameSpecTuple nameSpecTuple : specs) {
            if (seen.contains(nameSpecTuple.getSpec().getCOSObject())) {
                continue;
            }
            processDoc(nameSpecTuple.getName(), "", nameSpecTuple.getSpec(), new AttributesImpl());
            seen.add(nameSpecTuple.getSpec().getCOSObject());
        }
    }

    private void processDocOnAction(String name, String annotationType, PDFileSpecification spec,
                                    AttributesImpl attributes)
            throws TikaException, SAXException, IOException {
        if (spec == null) {
            return;
        }
        processDoc(name, annotationType, spec, attributes);
        extractedFiles.add(spec.getCOSObject());
    }

    private void processDoc(String name, String annotationType, PDFileSpecification spec,
                            AttributesImpl attributes)
            throws TikaException, SAXException, IOException {
        if (spec == null) {
            return;
        }
        if (extractedFiles.contains(spec.getCOSObject())) {
            return;
        }
        if (spec instanceof PDSimpleFileSpecification) {
            //((PDSimpleFileSpecification)spec).getFile();
            attributes.addAttribute("", "class", "class", "CDATA", "linked");
            attributes.addAttribute("", "id", "id", "CDATA", spec.getFile());
            xhtml.startElement("div", attributes);
            xhtml.endElement("div");
        } else if (spec instanceof PDComplexFileSpecification) {
            if (attributes.getIndex("source") < 0) {
                attributes.addAttribute("", "source", "source", "CDATA", "attachment");
            }
            extractMultiOSPDEmbeddedFiles(name, annotationType, (PDComplexFileSpecification) spec,
                    attributes);
        }
    }

    private void extractMultiOSPDEmbeddedFiles(String displayName, String annotationType,
                                               PDComplexFileSpecification spec,
                                               AttributesImpl attributes)
            throws IOException, SAXException, TikaException {

        if (spec == null) {
            return;
        }

        //current strategy is to pull all, not just first non-null
        extractPDEmbeddedFile(displayName, annotationType, spec, spec.getFile(),
                spec.getEmbeddedFile(), attributes);
        extractPDEmbeddedFile(displayName, annotationType, spec, spec.getFileMac(),
                spec.getEmbeddedFileMac(), attributes);
        extractPDEmbeddedFile(displayName, annotationType, spec, spec.getFileDos(),
                spec.getEmbeddedFileDos(), attributes);
        extractPDEmbeddedFile(displayName, annotationType, spec, spec.getFileUnix(),
                spec.getEmbeddedFileUnix(), attributes);

        //Check for /Thumb (thumbnail image);
        // /CI (collection item) adobe specific, can have /adobe:DisplayName and a summary
    }

    private void extractPDEmbeddedFile(String displayName, String annotationType,
                                       PDComplexFileSpecification spec, String fileName,
                                       PDEmbeddedFile pdEmbeddedFile, AttributesImpl attributes)
            throws SAXException, IOException {

        if (pdEmbeddedFile == null) {
            //skip silently
            return;
        }

        fileName =
                (fileName == null || "".equals(fileName.trim())) ? spec.getFileUnicode() : fileName;
        fileName = (fileName == null || "".equals(fileName.trim())) ? displayName : fileName;

        // TODO: other metadata?
        Metadata embeddedMetadata = new Metadata();
        embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        //if the stream is missing a size, -1 is returned
        long sz = pdEmbeddedFile.getSize();
        if (sz > -1) {
            embeddedMetadata.set(Metadata.CONTENT_LENGTH, Long.toString(sz));
        }
        embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString());
        embeddedMetadata.set(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, fileName);
        if (!StringUtils.isBlank(annotationType)) {
            embeddedMetadata.set(PDF.EMBEDDED_FILE_ANNOTATION_TYPE, annotationType);
        }
        if (!StringUtils.isBlank(pdEmbeddedFile.getSubtype())) {
            embeddedMetadata.set(PDF.EMBEDDED_FILE_SUBTYPE, pdEmbeddedFile.getSubtype());
        }
        if (!StringUtils.isBlank(spec.getFileDescription())) {
            embeddedMetadata.set(PDF.EMBEDDED_FILE_DESCRIPTION, spec.getFileDescription());
        }
        String afRelationship = spec.getCOSObject().getNameAsString(PDFParser.AF_RELATIONSHIP);
        if (StringUtils.isBlank(afRelationship)) {
            afRelationship = spec.getCOSObject().getString(PDFParser.AF_RELATIONSHIP);
        }
        if (!StringUtils.isBlank(afRelationship)) {
            embeddedMetadata.set(PDF.ASSOCIATED_FILE_RELATIONSHIP, afRelationship);
        }
        if (!embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata)) {
            return;
        }
        TikaInputStream stream = null;
        try {
            stream = TikaInputStream.get(pdEmbeddedFile.createInputStream());
        } catch (IOException e) {
            //store this exception in the parent's metadata
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
            return;
        }

        attributes.addAttribute("", "class", "class", "CDATA", "embedded");
        attributes.addAttribute("", "id", "id", "CDATA", fileName);
        xhtml.startElement("div", attributes);
        xhtml.endElement("div");

        try {
            embeddedDocumentExtractor.parseEmbedded(stream, new EmbeddedContentHandler(xhtml),
                    embeddedMetadata, false);
        } finally {
            IOUtils.closeQuietly(stream);
        }

    }

    void handleCatchableIOE(IOException e) throws IOException {

        if (WriteLimitReachedException.isWriteLimitReached(e)) {
            metadata.set(TikaCoreProperties.WRITE_LIMIT_REACHED, "true");
            throw e;
        }

        if (config.isCatchIntermediateIOExceptions()) {

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

    void doOCROnCurrentPage(PDPage pdPage, PDFParserConfig.OCR_STRATEGY ocrStrategy)
            throws IOException, TikaException, SAXException {
        if (ocrStrategy.equals(NO_OCR)) {
            //I don't think this is reachable?
            return;
        }
        //count the number of times that OCR would have been called
        OCRPageCounter c = context.get(OCRPageCounter.class);
        if (c != null) {
            c.increment();
        }
        MediaType ocrImageMediaType = MediaType.image("ocr-" + config.getOcrImageFormatName());
        if (!ocrParser.getSupportedTypes(context).contains(ocrImageMediaType)) {
            if (ocrStrategy == OCR_ONLY || ocrStrategy == OCR_AND_TEXT_EXTRACTION) {
                throw new TikaException(
                        "" + "I regret that I couldn't find an OCR parser to handle " +
                                ocrImageMediaType + "." +
                                "Please set the OCR_STRATEGY to NO_OCR or configure your" +
                                "OCR parser correctly");
            } else if (ocrStrategy == AUTO) {
                //silently skip if there's no parser to run ocr
                return;
            }
        }

        try (TemporaryResources tmp = new TemporaryResources()) {
            try (RenderResult renderResult = renderCurrentPage(pdPage, context, tmp)) {
                Metadata renderMetadata = renderResult.getMetadata();
                try (InputStream is = renderResult.getInputStream()) {
                    renderMetadata.set(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE,
                            ocrImageMediaType.toString());
                    ocrParser.parse(is, new EmbeddedContentHandler(new BodyContentHandler(xhtml)),
                            renderMetadata, context);
                }
            }
        } catch (IOException e) {
            handleCatchableIOE(e);
        } catch (SAXException e) {
            throw new IOException("error writing OCR content from PDF", e);
        }
    }

    private RenderResult renderCurrentPage(PDPage pdPage, ParseContext parseContext,
                                           TemporaryResources tmpResources)
            throws IOException, TikaException {
        PDFRenderingState renderingState = parseContext.get(PDFRenderingState.class);
        if (renderingState == null) {
            Metadata pageMetadata = getCurrentPageMetadata(pdPage);
            noContextRenderCurrentPage(pageMetadata, parseContext, tmpResources);
        }
        //if the full document has already been rendered, then reuse that file
        //TODO: we need to prevent this if only a portion of the page or portions
        //of the page have been rendered.
        //TODO: we should also figure out how to not reuse the rendering if
        //the user wants to render twice (say, full color to display to users, but
        //grayscale for (notionally?) better OCR).
        PageBasedRenderResults results = (PageBasedRenderResults) renderingState.getRenderResults();
        if (results != null) {
            List<RenderResult> pageResults = results.getPage(getCurrentPageNo());
            if (pageResults.size() == 1) {
                return pageResults.get(0);
            }
        }
        Metadata pageMetadata = getCurrentPageMetadata(pdPage);
        Renderer thisRenderer = getPDFRenderer(config.getRenderer());
        //if there's a configured renderer and if the rendering strategy is "all"
        if (thisRenderer != null &&
                config.getOcrRenderingStrategy() == PDFParserConfig.OCR_RENDERING_STRATEGY.ALL) {
            PageRangeRequest pageRangeRequest =
                    new PageRangeRequest(getCurrentPageNo(), getCurrentPageNo());
            if (thisRenderer instanceof PDDocumentRenderer) {
                //do not do autocloseable.  We need to leave the pdDocument open!
                TikaInputStream tis = TikaInputStream.get(new byte[0]);
                tis.setOpenContainer(pdDocument);
                return thisRenderer.render(tis, pageMetadata, parseContext, pageRangeRequest)
                        .getResults().get(0);

            } else {
                PDFRenderingState state = context.get(PDFRenderingState.class);
                if (state == null) {
                    throw new IllegalArgumentException("RenderingState must not be null");
                }
                return thisRenderer.render(state.getTikaInputStream(), pageMetadata, parseContext,
                        pageRangeRequest).getResults().get(0);
            }
        } else {
            return noContextRenderCurrentPage(pageMetadata, parseContext, tmpResources);
        }
    }

    private Renderer getPDFRenderer(Renderer renderer) {
        if (renderer == null) {
            return renderer;
        }
        if (renderer instanceof CompositeRenderer) {
            return ((CompositeRenderer) renderer).getLeafRenderer(PDFParser.MEDIA_TYPE);
        } else if (renderer.getSupportedTypes(context).contains(PDFParser.MEDIA_TYPE)) {
            return renderer;
        }
        return null;
    }


    private Metadata getCurrentPageMetadata(PDPage pdPage) {
        Metadata pageMetadata = new Metadata();
        pageMetadata.set(TikaCoreProperties.TYPE, PDFParser.MEDIA_TYPE.toString());
        pageMetadata.set(TikaPagedText.PAGE_NUMBER, getCurrentPageNo());
        pageMetadata.set(TikaPagedText.PAGE_ROTATION, (float) pdPage.getRotation());
        return pageMetadata;
    }

    private RenderResult noContextRenderCurrentPage(Metadata pageMetadata,
                                                    ParseContext parseContext,
                                                    TemporaryResources tmpResources)
            throws IOException, TikaException {
        PDFRenderer renderer = null;
        switch (config.getOcrRenderingStrategy()) {
            case NO_TEXT:
                renderer = new NoTextPDFRenderer(pdDocument);
                break;
            case TEXT_ONLY:
                renderer = new TextOnlyPDFRenderer(pdDocument);
                break;
            case VECTOR_GRAPHICS_ONLY:
                renderer = new VectorGraphicsOnlyPDFRenderer(pdDocument);
                break;
            case ALL:
                renderer = new PDFRenderer(pdDocument);
                break;
        }

        int dpi = config.getOcrDPI();
        Path tmpFile = null;

        RenderingTracker renderingTracker = parseContext.get(RenderingTracker.class);
        if (renderingTracker == null) {
            renderingTracker = new RenderingTracker();
            parseContext.set(RenderingTracker.class, renderingTracker);
        }
        int id = renderingTracker.getNextId();

        try {
            BufferedImage image =
                    renderer.renderImageWithDPI(pageIndex, dpi, config.getOcrImageType().getImageType());

            //TODO -- get suffix based on OcrImageType
            tmpFile = tmpResources.createTempFile();
            try (OutputStream os = Files.newOutputStream(tmpFile)) {
                //TODO: get output format from TesseractConfig
                ImageIOUtil.writeImage(image, config.getOcrImageFormatName(), os, dpi,
                        config.getOcrImageQuality());
            }
        } catch (SecurityException e) {
            //throw SecurityExceptions immediately
            throw e;
        } catch (IOException | RuntimeException e) {
            //image rendering can throw a variety of runtime exceptions, not just
            // IOExceptions...
            //need to have a wide catch
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM,
                    ExceptionUtils.getStackTrace(e));

            return new RenderResult(RenderResult.STATUS.EXCEPTION, id, null, pageMetadata);
        }
        return new RenderResult(RenderResult.STATUS.SUCCESS, id, tmpFile, pageMetadata);
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
        metadata.add(PDF.CHARACTERS_PER_PAGE, totalCharsPerPage);
        metadata.add(PDF.UNMAPPED_UNICODE_CHARS_PER_PAGE, unmappedUnicodeCharsPerPage);


        try {
            for (PDAnnotation annotation : page.getAnnotations()) {
                String annotationName = annotation.getAnnotationName();
                if (annotationTypes.size() < MAX_ANNOTATION_TYPES) {
                    if (annotationName != null) {
                        annotationTypes.add(annotationName);
                    } else {
                        annotationTypes.add(NULL_STRING);
                    }
                }
                String annotationSubtype = annotation.getSubtype();
                if (annotationSubtypes.size() < MAX_ANNOTATION_TYPES) {
                    if (annotationSubtype != null) {
                        annotationSubtypes.add(annotationSubtype);
                    } else {
                        annotationSubtypes.add(NULL_STRING);
                    }
                }
                if (annotation instanceof PDAnnotationFileAttachment) {
                    PDAnnotationFileAttachment fann = (PDAnnotationFileAttachment) annotation;
                    String subtype = "annotationFileAttachment";
                    AttributesImpl attributes = new AttributesImpl();
                    attributes.addAttribute("", "source", "source", "CDATA", subtype);
                    processDocOnAction("", subtype, fann.getFile(), attributes);
                } else if (annotation instanceof PDAnnotationWidget) {
                    handleWidget((PDAnnotationWidget) annotation);
                } else {
                    if (annotationSubtype == null) {
                        annotationSubtype = "unknown";
                    } else if (annotationSubtype.equals(THREE_D) ||
                            annotation.getCOSObject().containsKey(THREE_DD)) {
                        //To make this stricter, we could get the 3DD stream object and see if the
                        //subtype is U3D or PRC or model/ (prefix for model mime type)
                        metadata.set(PDF.HAS_3D, true);
                        num3DAnnotations++;
                    }
                    for (COSDictionary fileSpec : findFileSpecs(annotation.getCOSObject())) {
                        AttributesImpl attributes = new AttributesImpl();
                        attributes.addAttribute("", "source", "source", "CDATA", annotationSubtype);
                        processDocOnAction("", annotationSubtype, createFileSpecification(fileSpec),
                                attributes);
                    }
                }
                // TODO: remove once PDFBOX-1143 is fixed:
                if (config.isExtractAnnotationText()) {
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
            if (config.getOcrStrategy() == PDFParserConfig.OCR_STRATEGY.OCR_AND_TEXT_EXTRACTION) {
                doOCROnCurrentPage(page, OCR_AND_TEXT_EXTRACTION);
            } else if (config.getOcrStrategy() == PDFParserConfig.OCR_STRATEGY.AUTO) {
                boolean unmappedExceedsLimit = false;
                if (totalCharsPerPage > config.getOcrStrategyAuto().getTotalCharsPerPage()) {
                    // There are enough characters to not have to do OCR.  Check number of unmapped characters
                    final float percentUnmapped =
                            (float) unmappedUnicodeCharsPerPage / totalCharsPerPage;
                    final float unmappedCharacterLimit =
                            config.getOcrStrategyAuto().getUnmappedUnicodeCharsPerPage();
                    unmappedExceedsLimit = (unmappedCharacterLimit < 1) ?
                            percentUnmapped > unmappedCharacterLimit :
                            unmappedUnicodeCharsPerPage > unmappedCharacterLimit;
                }
                if (totalCharsPerPage <= config.getOcrStrategyAuto().getTotalCharsPerPage() ||
                        unmappedExceedsLimit) {
                    doOCROnCurrentPage(page, AUTO);
                }
            }

            PDPageAdditionalActions pageActions = page.getActions();
            if (pageActions != null) {
                handleDestinationOrAction(pageActions.getC(), ActionTrigger.PAGE_CLOSE);
                handleDestinationOrAction(pageActions.getO(), ActionTrigger.PAGE_OPEN);
            }
            xhtml.endElement("div");
        } catch (SAXException | TikaException e) {
            throw new IOException("Unable to end a page", e);
        } catch (IOException e) {
            handleCatchableIOE(e);
        } finally {
            totalCharsPerPage = 0;
            unmappedUnicodeCharsPerPage = 0;
        }

        if (config.isExtractFontNames()) {
            for (COSName n : page.getResources().getFontNames()) {
                PDFont font = page.getResources().getFont(n);
                if (font != null && font.getFontDescriptor() != null) {
                    String fontName = font.getFontDescriptor().getFontName();
                    if (fontName != null) {
                        fontNames.add(fontName);
                    }
                }
            }
        }
    }

    private List<COSDictionary> findFileSpecs(COSDictionary cosDict) {
        Set<COSName> types = new HashSet<>();
        types.add(COSName.FILESPEC);
        return PDFDOMUtil.findType(cosDict, types, MAX_RECURSION_DEPTH);
    }

    private void extractFilesfromEFTree(PDNameTreeNode efTree,
                                        List<NameSpecTuple> embeddedFileNames, int depth)
            throws IOException {
        if (depth > MAX_RECURSION_DEPTH) {
            throw new IOException("Hit max recursion depth");
        }
        Map<String, PDComplexFileSpecification> names = null;
        try {
            names = efTree.getNames();
        } catch (IOException e) {
            //LOG?
        }
        if (names != null) {
            for (Map.Entry<String, PDComplexFileSpecification> e : names.entrySet()) {
                embeddedFileNames.add(new NameSpecTuple(e.getKey(), e.getValue()));
            }
        }

        List<PDNameTreeNode<PDComplexFileSpecification>> kids = efTree.getKids();
        if (kids == null) {
            return;
        } else {
            for (PDNameTreeNode<PDComplexFileSpecification> node : kids) {
                extractFilesfromEFTree(node, embeddedFileNames, depth + 1);
            }
        }
    }


    private void handleWidget(PDAnnotationWidget widget)
            throws TikaException, SAXException, IOException {
        if (widget == null) {
            return;
        }
        handleDestinationOrAction(widget.getAction(), ActionTrigger.ANNOTATION_WIDGET);
        PDAnnotationAdditionalActions annotationActions = widget.getActions();
        if (annotationActions != null) {
            handleDestinationOrAction(annotationActions.getBl(),
                    ActionTrigger.ANNOTATION_LOSE_INPUT_FOCUS);
            handleDestinationOrAction(annotationActions.getD(),
                    ActionTrigger.ANNOTATION_MOUSE_CLICK);
            handleDestinationOrAction(annotationActions.getE(),
                    ActionTrigger.ANNOTATION_CURSOR_ENTERS);
            handleDestinationOrAction(annotationActions.getFo(),
                    ActionTrigger.ANNOTATION_RECEIVES_FOCUS);
            handleDestinationOrAction(annotationActions.getPC(),
                    ActionTrigger.ANNOTATION_PAGE_CLOSED);
            handleDestinationOrAction(annotationActions.getPI(),
                    ActionTrigger.ANNOTATION_PAGE_NO_LONGER_VISIBLE);
            handleDestinationOrAction(annotationActions.getPO(),
                    ActionTrigger.ANNOTATION_PAGE_OPENED);
            handleDestinationOrAction(annotationActions.getPV(),
                    ActionTrigger.ANNOTATION_PAGE_VISIBLE);
            handleDestinationOrAction(annotationActions.getU(),
                    ActionTrigger.ANNOTATION_MOUSE_RELEASED);
            handleDestinationOrAction(annotationActions.getX(),
                    ActionTrigger.ANNOTATION_CURSOR_EXIT);
        }

    }

    @Override
    protected void startDocument(PDDocument pdf) throws IOException {
        try {
            xhtml.startDocument();
            try {
                handleDestinationOrAction(pdf.getDocumentCatalog().getOpenAction(),
                        ActionTrigger.DOCUMENT_OPEN);
            } catch (IOException e) {
                //See PDFBOX-3773
                //swallow -- no need to report this
            }
        } catch (TikaException | SAXException e) {
            throw new IOException("Unable to start a document", e);
        }
    }

    private void handleDestinationOrAction(PDDestinationOrAction action,
                                           ActionTrigger actionTrigger)
            throws IOException, SAXException, TikaException {
        if (action == null || !config.isExtractActions()) {
            return;
        }
        triggers.add(actionTrigger.name());
        String actionOrDestString = "destination";
        if (action instanceof PDAction) {
            actionOrDestString = "action";
            String actionType = ((PDAction) action).getType();
            if (!StringUtils.isBlank(actionType)) {
                actionTypes.add(actionType);
            }
        }
        AttributesImpl attributes = new AttributesImpl();

        addNonNullAttribute("class", actionOrDestString, attributes);
        addNonNullAttribute("type", action.getClass().getSimpleName(), attributes);
        addNonNullAttribute("trigger", actionTrigger.name(), attributes);

        if (action instanceof PDActionImportData) {
            processDocOnAction("", "", ((PDActionImportData) action).getFile(), attributes);
        } else if (action instanceof PDActionLaunch) {
            PDActionLaunch pdActionLaunch = (PDActionLaunch) action;
            addNonNullAttribute("id", pdActionLaunch.getF(), attributes);
            addNonNullAttribute("defaultDirectory", pdActionLaunch.getD(), attributes);
            addNonNullAttribute("operation", pdActionLaunch.getO(), attributes);
            addNonNullAttribute("parameters", pdActionLaunch.getP(), attributes);
            processDocOnAction(pdActionLaunch.getF(), "", pdActionLaunch.getFile(), attributes);
        } else if (action instanceof PDActionRemoteGoTo) {
            PDActionRemoteGoTo remoteGoTo = (PDActionRemoteGoTo) action;
            processDocOnAction("", "", remoteGoTo.getFile(), attributes);
        } else if (action instanceof PDActionJavaScript) {
            PDActionJavaScript jsAction = (PDActionJavaScript) action;
            Metadata m = new Metadata();
            m.set(Metadata.CONTENT_TYPE, "application/javascript");
            m.set(Metadata.CONTENT_ENCODING, StandardCharsets.UTF_8.toString());
            m.set(PDF.ACTION_TRIGGER, actionTrigger.toString());
            m.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                    TikaCoreProperties.EmbeddedResourceType.MACRO.name());
            String js = jsAction.getAction();
            js = (js == null) ? "" : js;
            if (embeddedDocumentExtractor.shouldParseEmbedded(m)) {
                try (InputStream is = TikaInputStream.get(js.getBytes(StandardCharsets.UTF_8))) {
                    embeddedDocumentExtractor.parseEmbedded(is, xhtml, m, true);
                }
            }
            addNonNullAttribute("class", "javascript", attributes);
            addNonNullAttribute("type", jsAction.getType(), attributes);
            addNonNullAttribute("subtype", jsAction.getSubType(), attributes);
            xhtml.startElement("div", attributes);
            xhtml.endElement("div");
        /*} else if (action instanceof PDActionSubmitForm) {
            PDActionSubmitForm submitForm = (PDActionSubmitForm) action;
            //these are typically urls, not actual file specification
            PDFileSpecification fileSpecification = submitForm.getFile();
            processDoc("", fileSpecification, new AttributesImpl());*/
        } else {
            xhtml.startElement("div", attributes);
            xhtml.endElement("div");
        }
    }

    @Override
    protected void endDocument(PDDocument pdf) throws IOException {
        try {
            // Extract text for any bookmarks:
            if (config.isExtractBookmarksText()) {
                extractBookmarkText();
            }

            try {
                extractEmbeddedDocuments(pdf);
            } catch (IOException e) {
                handleCatchableIOE(e);
            }

            try {
                extractIncrementalUpdates();
            } catch (IOException e) {
                handleCatchableIOE(e);
            }

            extractXMPXFA(pdf, metadata, context);

            //extract acroform data at end of doc
            if (config.isExtractAcroFormContent() == true) {
                try {
                    extractAcroForm(pdf);
                } catch (IOException e) {
                    handleCatchableIOE(e);
                }
            }
            PDDocumentCatalogAdditionalActions additionalActions =
                    pdf.getDocumentCatalog().getActions();
            handleDestinationOrAction(additionalActions.getDP(),
                    ActionTrigger.AFTER_DOCUMENT_PRINT);
            handleDestinationOrAction(additionalActions.getDS(), ActionTrigger.AFTER_DOCUMENT_SAVE);
            handleDestinationOrAction(additionalActions.getWC(),
                    ActionTrigger.BEFORE_DOCUMENT_CLOSE);
            handleDestinationOrAction(additionalActions.getWP(),
                    ActionTrigger.BEFORE_DOCUMENT_PRINT);
            handleDestinationOrAction(additionalActions.getWS(),
                    ActionTrigger.BEFORE_DOCUMENT_SAVE);
            //now record annotationtypes and subtypes
            for (String annotationType : annotationTypes) {
                metadata.add(PDF.ANNOTATION_TYPES, annotationType);
            }
            for (String annotationSubtype : annotationSubtypes) {
                metadata.add(PDF.ANNOTATION_SUBTYPES, annotationSubtype);
            }

            for (String trigger : triggers) {
                metadata.add(PDF.ACTION_TRIGGERS, trigger);
            }

            for (String actionType : actionTypes) {
                metadata.add(PDF.ACTION_TYPES, actionType);
            }
            xhtml.endDocument();
        } catch (TikaException | SAXException e) {
            throw new IOException("Unable to end a document", e);
        }
        if (fontNames.size() > 0) {
            for (String fontName : fontNames) {
                metadata.add(Font.FONT_NAME, fontName);
            }
        }
        metadata.set(PDF.TOTAL_UNMAPPED_UNICODE_CHARS, totalUnmappedUnicodeCharacters);
        if (totalCharacters > 0) {
            metadata.set(PDF.OVERALL_PERCENTAGE_UNMAPPED_UNICODE_CHARS,
                    (float) totalUnmappedUnicodeCharacters / (float) totalCharacters);
        }
        metadata.set(PDF.CONTAINS_DAMAGED_FONT, containsDamagedFont);
        metadata.set(PDF.CONTAINS_NON_EMBEDDED_FONT, containsNonEmbeddedFont);
        metadata.set(PDF.NUM_3D_ANNOTATIONS, num3DAnnotations);
    }

    private void extractIncrementalUpdates() throws SAXException, IOException {
        if (!config.isParseIncrementalUpdates()) {
            return;
        }
        IncrementalUpdateRecord incrementalUpdateRecord =
                context.get(IncrementalUpdateRecord.class);
        if (incrementalUpdateRecord == null) {
            //should log
            return;
        }

        int count = 0;
        //don't include the last xref (coz that's the full pdf)
        for (int i = 0; i < incrementalUpdateRecord.getOffsets().size() - 1
                && i < config.getMaxIncrementalUpdates(); i++) {
            StartXRefOffset xRefOffset = incrementalUpdateRecord.getOffsets().get(i);
            //don't count linearized dummy xref offset
            //TODO figure out better way of managing this
            if (xRefOffset.getStartxref() == 0) {
                continue;
            }
            try {
                parseIncrementalUpdate(count, incrementalUpdateRecord.getPath(), xRefOffset);
                count++;
            } catch (IOException e) {
                handleCatchableIOE(e);
            }
        }
    }

    private void parseIncrementalUpdate(int count, Path path, StartXRefOffset xRefOffset)
            throws SAXException, IOException {
        TemporaryResources tmp = new TemporaryResources();
        try {
            Path update = tmp.createTempFile();
            try (InputStream input = Files.newInputStream(path);
                    OutputStream outputStream = Files.newOutputStream(update, StandardOpenOption.WRITE)) {
                IOUtils.copyLarge(input, outputStream, 0, xRefOffset.getEndEofOffset());
            }
            Metadata updateMetadata = new Metadata();
            updateMetadata.set(PDF.INCREMENTAL_UPDATE_NUMBER, count);
            updateMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                    TikaCoreProperties.EmbeddedResourceType.VERSION.toString());
            if (embeddedDocumentExtractor.shouldParseEmbedded(updateMetadata)) {
                try (InputStream tis = TikaInputStream.get(update)) {
                    context.set(IsIncrementalUpdate.class, IsIncrementalUpdate.IS_INCREMENTAL_UPDATE);
                    embeddedDocumentExtractor.parseEmbedded(tis, xhtml, updateMetadata, false);
                }
            }
        } finally {
            tmp.close();
        }
    }

    void extractBookmarkText() throws SAXException, IOException, TikaException {
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline != null) {
            Set<COSObjectable> seen = new HashSet<>();
            extractBookmarkText(outline, seen, 0);
        }
    }

    void extractBookmarkText(PDOutlineNode bookmark, Set<COSObjectable> seen, int itemCount)
            throws SAXException, IOException, TikaException {
        PDOutlineItem current = bookmark.getFirstChild();
        if (itemCount > MAX_BOOKMARK_ITEMS) {
            return;
        }
        if (current != null) {
            if (seen.contains(current)) {
                return;
            }
            xhtml.startElement("ul");
            while (current != null) {
                if (seen.contains(current)) {
                    break;
                }
                if (itemCount > MAX_BOOKMARK_ITEMS) {
                    break;
                }
                seen.add(current);
                xhtml.startElement("li");
                xhtml.characters(current.getTitle());
                xhtml.endElement("li");
                handleDestinationOrAction(current.getAction(), ActionTrigger.BOOKMARK);
                // Recurse:
                extractBookmarkText(current, seen, itemCount + 1);
                current = current.getNextSibling();
                itemCount++;
            }
            xhtml.endElement("ul");
        }
    }

    void extractAcroForm(PDDocument pdf) throws IOException, SAXException, TikaException {
        //Thank you, Ben Litchfield, for org.apache.pdfbox.examples.fdf.PrintFields
        //this code derives from Ben's code
        PDDocumentCatalog catalog = pdf.getDocumentCatalog();

        if (catalog == null) {
            return;
        }

        PDAcroForm form = catalog.getAcroForm(null);
        if (form == null) {
            return;
        }

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
                        new UnsynchronizedByteArrayInputStream(pdxfa.getBytes()));
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

        @SuppressWarnings("rawtypes") List fields = form.getFields();

        if (fields == null) {
            return;
        }

        @SuppressWarnings("rawtypes") ListIterator itr = fields.listIterator();

        if (itr == null) {
            return;
        }

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

        if (currentRecursiveDepth >= MAX_RECURSION_DEPTH) {
            return;
        }

        PDFormFieldAdditionalActions pdFormFieldAdditionalActions = field.getActions();
        if (pdFormFieldAdditionalActions != null) {
            handleDestinationOrAction(pdFormFieldAdditionalActions.getC(),
                    ActionTrigger.FORM_FIELD_RECALCULATE);
            handleDestinationOrAction(pdFormFieldAdditionalActions.getF(),
                    ActionTrigger.FORM_FIELD_FORMATTED);
            handleDestinationOrAction(pdFormFieldAdditionalActions.getK(),
                    ActionTrigger.FORM_FIELD_KEYSTROKE);
            handleDestinationOrAction(pdFormFieldAdditionalActions.getV(),
                    ActionTrigger.FORM_FIELD_VALUE_CHANGE);
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
            for (PDField child : ((PDNonTerminalField) field).getChildren()) {
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
            metadata.set(TikaCoreProperties.HAS_SIGNATURE, "true");
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

    /**
     * we need to override this because we are overriding {@link #processPages(PDPageTree)}
     *
     * @return
     */
    @Override
    public int getCurrentPageNo() {
        return pageIndex + 1;
    }

    /**
     * See TIKA-2845 for why we need to override this.
     *
     * @param pages
     * @throws IOException
     */
    @Override
    protected void processPages(PDPageTree pages) throws IOException {
        //we currently need this hack because we aren't able to increment
        //the private currentPageNo in PDFTextStripper,
        //and PDFTextStripper's processPage relies on that variable
        //being >= startPage when deciding whether or not to process a page
        // See:
        // if (currentPageNo >= startPage && currentPageNo <= endPage
        //                && (startBookmarkPageNumber == -1 ||
        //                currentPageNo >= startBookmarkPageNumber)
        //                && (endBookmarkPageNumber == -1 ||
        //                currentPageNo <= endBookmarkPageNumber))
        //        {
        super.setStartPage(-1);
        for (PDPage page : pages) {
            if (getCurrentPageNo() >= getStartPage() && getCurrentPageNo() <= getEndPage()) {
                processPage(page);
            }
            pageIndex++;
        }
    }

    @Override
    public void setStartBookmark(PDOutlineItem pdOutlineItem) {
        throw new UnsupportedOperationException(
                "We don't currently support this -- See PDFTextStripper's processPages() for how " +
                        "to implement this.");
    }

    @Override
    public void setEndBookmark(PDOutlineItem pdOutlineItem) {
        throw new UnsupportedOperationException(
                "We don't currently support this -- See PDFTextStripper's processPages() for how " +
                        "to implement this.");
    }

    @Override
    public int getStartPage() {
        return startPage;
    }

    @Override
    public void setStartPage(int startPage) {
        this.startPage = startPage;
    }

    @Override
    protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code,
                             Vector displacement) throws IOException {
        super.showGlyph(textRenderingMatrix, font, code, displacement);
        String unicode = font.toUnicode(code);
        if (unicode == null || unicode.isEmpty()) {
            unmappedUnicodeCharsPerPage++;
            totalUnmappedUnicodeCharacters++;
        }
        totalCharsPerPage++;
        totalCharacters++;

        if (font.isDamaged()) {
            containsDamagedFont = true;
        }
        if (!font.isEmbedded()) {
            containsNonEmbeddedFont = true;
        }
    }

    private PDFileSpecification createFileSpecification(COSBase cosBase) {
        try {
            return PDFileSpecification.createFS(cosBase);
        } catch (IOException e) {
            //swallow for now
        }
        return null;
    }

    private static class NameSpecTuple {
        private final String name;
        private final PDComplexFileSpecification spec;

        public NameSpecTuple(String name, PDComplexFileSpecification spec) {
            this.name = name;
            this.spec = spec;
        }

        public String getName() {
            return name;
        }

        public PDComplexFileSpecification getSpec() {
            return spec;
        }
    }

    enum ActionTrigger {
        AFTER_DOCUMENT_PRINT, AFTER_DOCUMENT_SAVE, ANNOTATION_CURSOR_ENTERS, ANNOTATION_CURSOR_EXIT,
        ANNOTATION_LOSE_INPUT_FOCUS, ANNOTATION_MOUSE_CLICK, ANNOTATION_MOUSE_RELEASED,
        ANNOTATION_PAGE_CLOSED, ANNOTATION_PAGE_NO_LONGER_VISIBLE, ANNOTATION_PAGE_OPENED,
        ANNOTATION_PAGE_VISIBLE, ANNOTATION_RECEIVES_FOCUS, ANNOTATION_WIDGET,
        BEFORE_DOCUMENT_CLOSE, BEFORE_DOCUMENT_PRINT, BEFORE_DOCUMENT_SAVE, DOCUMENT_OPEN,
        FORM_FIELD, FORM_FIELD_FORMATTED, FORM_FIELD_KEYSTROKE, FORM_FIELD_RECALCULATE,
        FORM_FIELD_VALUE_CHANGE, PAGE_CLOSE, PAGE_OPEN, BOOKMARK,
    }
}
