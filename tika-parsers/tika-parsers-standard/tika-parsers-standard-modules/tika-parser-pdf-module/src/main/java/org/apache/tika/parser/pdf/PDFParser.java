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

import static org.apache.tika.metadata.PDF.OCR_PAGE_COUNT;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.fixup.AbstractFixup;
import org.apache.pdfbox.pdmodel.fixup.PDDocumentFixup;
import org.apache.pdfbox.pdmodel.fixup.processor.AcroFormDefaultsProcessor;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.AccessPermissionException;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.AccessPermissions;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.RenderingParser;
import org.apache.tika.parser.pdf.image.ImageGraphicsEngineFactory;
import org.apache.tika.parser.pdf.updates.IncrementalUpdateRecord;
import org.apache.tika.parser.pdf.updates.IsIncrementalUpdate;
import org.apache.tika.parser.pdf.updates.StartXRefOffset;
import org.apache.tika.parser.pdf.updates.StartXRefScanner;
import org.apache.tika.parser.pdf.xmpschemas.XMPSchemaIllustrator;
import org.apache.tika.renderer.PageRangeRequest;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.renderer.pdf.pdfbox.PDFBoxRenderer;
import org.apache.tika.renderer.pdf.pdfbox.PDFRenderingState;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * PDF parser.
 * <p/>
 * This parser can process also encrypted PDF documents if the required
 * password is given as a part of the input metadata associated with a
 * document. If no password is given, then this parser will try decrypting
 * the document using the empty password that's often used with PDFs. If
 * the PDF contains any embedded documents (for example as part of a PDF
 * package) then this parser will use the {@link EmbeddedDocumentExtractor}
 * to handle them.
 * <p/>
 * As of Tika 1.6, it is possible to extract inline images with
 * the {@link EmbeddedDocumentExtractor} as if they were regular
 * attachments.  By default, this feature is turned off because of
 * the potentially enormous number and size of inline images.  To
 * turn this feature on, see
 * {@link PDFParserConfig#setExtractInlineImages(boolean)}.
 * <p/>
 * Please note that many pdfs do not store table structures.
 * So you should not expect table markup for what looks like a table. It
 * takes significant computation to identify and then correctly extract
 * tables from PDFs. As of this writing, the {@link PDFParser} extracts
 * text within tables, but it does not compute table cell boundaries or
 * table row boundaries. Please see
 * <a href="http://tabula.technology/">tabula</a> for one project that
 * tries to maintain the structure of tables represented in PDFs.
 *
 * If your PDFs contain marked content or tags, consider
 * {@link PDFParserConfig#setExtractMarkedContent(boolean)}
 */
@TikaComponent
public class PDFParser implements Parser, RenderingParser {

    public static final MediaType MEDIA_TYPE = MediaType.application("pdf");
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -752276948656079347L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MEDIA_TYPE);

    static COSName AF_RELATIONSHIP = COSName.getPDFName("AFRelationship");

    private static COSName ENCRYPTED_PAYLOAD = COSName.getPDFName("EncryptedPayload");
    private PDFParserConfig defaultConfig = new PDFParserConfig();
    private Renderer renderer;

    public PDFParser() {
    }

    /**
     * Constructor with explicit PDFParserConfig object.
     *
     * @param config the configuration
     */
    public PDFParser(PDFParserConfig config) {
        this.defaultConfig = config;
    }

    /**
     * Constructor for JSON configuration.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig JSON configuration
     */
    public PDFParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, PDFParserConfig.class));
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        PDFParserConfig localConfig = getConfig(context);
        if (localConfig.isSetKCMS()) {
            System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");
        }
        IncrementalUpdateRecord incomingIncrementalUpdateRecord = context.get(IncrementalUpdateRecord.class);
        context.set(IncrementalUpdateRecord.class, null);
        initRenderer(localConfig, context);
        PDDocument pdfDocument = null;

        String password = "";
        PDFRenderingState incomingRenderingState = context.get(PDFRenderingState.class);
        OCRPageCounter prevOCRCounter = context.get(OCRPageCounter.class);
        context.set(OCRPageCounter.class, new OCRPageCounter());
        try {
            if (shouldSpool(localConfig)) {
                context.set(PDFRenderingState.class, new PDFRenderingState(tis));
            }


            scanXRefOffsets(localConfig, tis, metadata, context);

            password = getPassword(metadata, context);
            MemoryUsageSetting memoryUsageSetting = null;

            if (localConfig.getMaxMainMemoryBytes() >= 0) {
                memoryUsageSetting =
                        MemoryUsageSetting.setupMixed(localConfig.getMaxMainMemoryBytes());
            } else {
                memoryUsageSetting = MemoryUsageSetting.setupMainMemoryOnly();
            }

            pdfDocument = getPDDocument(tis, password,
                    memoryUsageSetting.streamCache, metadata, context);


            boolean hasCollection = hasCollection(pdfDocument, metadata);

            checkEncryptedPayload(pdfDocument, hasCollection, localConfig);

            boolean hasXFA = hasXFA(pdfDocument, metadata);
            boolean hasMarkedContent = hasMarkedContent(pdfDocument, metadata);
            extractMetadata(pdfDocument, metadata, context);
            extractSignatures(pdfDocument, metadata);
            checkIllustrator(pdfDocument, metadata);
            checkAccessPermissions(localConfig.getAccessCheckMode(), metadata);
            renderPagesBeforeParse(tis, handler, metadata, context, localConfig);
            if (handler != null) {
                if (shouldHandleXFAOnly(hasXFA, localConfig)) {
                    handleXFAOnly(pdfDocument, handler, metadata, context);
                } else if (localConfig.getOcrStrategy()
                        .equals(OcrConfig.Strategy.OCR_ONLY)) {
                    OCR2XHTML.process(pdfDocument, handler, context, metadata,
                            localConfig, renderer);
                } else if (hasMarkedContent && localConfig.isExtractMarkedContent()) {
                    PDFMarkedContent2XHTML
                            .process(pdfDocument, handler, context, metadata,
                                    localConfig, renderer);
                } else {
                    PDF2XHTML.process(pdfDocument, handler, context, metadata,
                            localConfig, renderer);
                }
            }
        } catch (InvalidPasswordException e) {
            metadata.set(PDF.IS_ENCRYPTED, "true");
            throw new EncryptedDocumentException(e);
        } finally {
            metadata.set(OCR_PAGE_COUNT, context.get(OCRPageCounter.class).getCount());
            context.set(OCRPageCounter.class, prevOCRCounter);
            //reset the incrementalUpdateRecord even if null
            context.set(IncrementalUpdateRecord.class, incomingIncrementalUpdateRecord);
            PDFRenderingState currState = context.get(PDFRenderingState.class);
            try {
                if (currState != null && currState.getRenderResults() != null) {
                    currState.getRenderResults().close();
                }
                if (pdfDocument != null) {
                    pdfDocument.close();
                }
            } finally {
                //replace the one that was here
                context.set(PDFRenderingState.class, incomingRenderingState);
            }

        }
    }

    private PDFParserConfig getConfig(ParseContext parseContext) throws TikaException, IOException {
        // ParseContextConfig.getConfig() handles:
        // 1. Check for PDFParserConfig already in ParseContext (fast path for embedded docs)
        // 2. Check ConfigContainer for "pdf-parser" and deserialize if present
        // 3. Set deserialized config in ParseContext for future lookups
        // 4. Return defaultConfig if no runtime config found
        return ParseContextConfig.getConfig(
                parseContext,
                "pdf-parser",
                PDFParserConfig.class,
                defaultConfig);
    }

    private void checkEncryptedPayload(PDDocument pdfDocument,
                                       boolean hasCollection, PDFParserConfig localConfig)
            throws IOException, EncryptedDocumentException {
        if (! localConfig.isThrowOnEncryptedPayload()) {
            return;
        }
        //We require a collection. We could also require that it have View=H(idden)
        //as the spec suggests for Wrapped encrypted files (7.6.7).
        if (! hasCollection) {
            return;
        }
        List<COSObject> fileSpecs = pdfDocument.getDocument().getObjectsByType(COSName.FILESPEC);
        //Do we want to also check that this is a portfolio PDF/contains a "collection"?
        for (COSObject obj : fileSpecs) {
            if (obj.getObject() instanceof COSDictionary) {
                COSDictionary dict = (COSDictionary) obj.getObject();
                COSBase relationship = dict.getDictionaryObject(AF_RELATIONSHIP);
                if (relationship != null && relationship.equals(ENCRYPTED_PAYLOAD)) {
                    String name = "";
                    COSBase uf = dict.getDictionaryObject(COSName.UF);
                    COSBase f = dict.getDictionaryObject(COSName.F);
                    if (uf != null && uf instanceof COSString) {
                        name = ((COSString)uf).getString();
                    } else if (f != null && f instanceof COSString) {
                        name = ((COSString)f).getString();
                    }
                    throw new EncryptedDocumentException("PDF file contains an encrypted " +
                                    "payload: '" + name + "'");
                }
            }
        }
    }

    private void scanXRefOffsets(PDFParserConfig localConfig,
                                 TikaInputStream tikaInputStream,
                                 Metadata metadata,
                                 ParseContext parseContext) {

        if (!localConfig.isParseIncrementalUpdates() &&
                !localConfig.isExtractIncrementalUpdateInfo()) {
            return;
        }
        //do not scan for xrefoffsets if this is an incremental update
        if (parseContext.get(IsIncrementalUpdate.class) != null) {
            //nullify it so that child documents do not see this
            parseContext.set(IsIncrementalUpdate.class, null);
            return;
        }
        List<StartXRefOffset> xRefOffsets = new ArrayList<>();
        //TODO -- can we use the PDFBox parser's RandomAccessRead
        //so that we don't have to reopen from file?
        try (RandomAccessRead ra =
                     new RandomAccessReadBufferedFile(tikaInputStream.getFile())) {
            StartXRefScanner xRefScanner = new StartXRefScanner(ra);
            xRefOffsets.addAll(xRefScanner.scan());
        } catch (IOException e) {
            //swallow
        }

        int startXrefs = 0;
        for (StartXRefOffset offset : xRefOffsets) {
            //don't count linearized dummy xref offset
            //TODO figure out better way of managing this
            if (offset.getStartxref() == 0) {
                continue;
            }
            startXrefs++;
            metadata.add(PDF.EOF_OFFSETS, Long.toString(offset.getEndEofOffset()));
        }

        if (startXrefs > 0) {
            //don't count the last xref as an incremental update
            startXrefs--;
        }
        metadata.set(PDF.PDF_INCREMENTAL_UPDATE_COUNT, startXrefs);
        if (localConfig.isParseIncrementalUpdates()) {
            try {
                parseContext.set(IncrementalUpdateRecord.class,
                        new IncrementalUpdateRecord(tikaInputStream.getPath(), xRefOffsets));
            } catch (IOException e) {
                //swallow
            }
        }
    }

    private void checkIllustrator(final PDDocument pdfDocument, Metadata metadata) {

        PDPage page = null;
        try {
            page = pdfDocument.getPage(0);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            //things can go wrong
            return;
        }
        COSDictionary pieceInfoDict = page.getCOSObject().getCOSDictionary(COSName.PIECE_INFO);
        if (pieceInfoDict == null) {
            return;
        }

        COSDictionary illustratorDict = pieceInfoDict.getCOSDictionary(COSName.ILLUSTRATOR);
        if (illustratorDict == null) {
            return;
        }

        COSDictionary privateDict = illustratorDict.getCOSDictionary(COSName.PRIVATE);
        if (privateDict == null) {
            return;
        }
        metadata.set(Metadata.CONTENT_TYPE, XMPSchemaIllustrator.ILLUSTRATOR);
        //TODO -- consider parsing the metadata
        //COSStream aiMetaData = privateDict.getCOSStream(COSName.AI_META_DATA);
    }

    private void checkAccessPermissions(PDFParserConfig.AccessCheckMode mode, Metadata metadata)
            throws AccessPermissionException {
        if (mode == PDFParserConfig.AccessCheckMode.DONT_CHECK) {
            return;
        }

        if ("false".equals(metadata.get(AccessPermissions.EXTRACT_CONTENT))) {
            if (mode == PDFParserConfig.AccessCheckMode.ALLOW_EXTRACTION_FOR_ACCESSIBILITY) {
                if ("true".equals(metadata.get(AccessPermissions.EXTRACT_FOR_ACCESSIBILITY))) {
                    return;
                }
                throw new AccessPermissionException(
                        "Content extraction for accessibility is not allowed.");
            }
            // IGNORE_ACCESSIBILITY_ALLOWANCE - don't extract even if accessibility is allowed
            throw new AccessPermissionException("Content extraction is not allowed.");
        }
    }

    private void extractSignatures(PDDocument pdfDocument, Metadata metadata) {
        boolean hasSignature = false;
        for (PDSignature signature : pdfDocument.getSignatureDictionaries()) {
            if (signature == null) {
                continue;
            }
            PDMetadataExtractor.addNotNull(signature.getName(), metadata, TikaCoreProperties.SIGNATURE_NAME);

            Calendar date = signature.getSignDate();
            if (date != null) {
                metadata.add(TikaCoreProperties.SIGNATURE_DATE, date);
            }
            PDMetadataExtractor.addNotNull(signature.getContactInfo(), metadata, TikaCoreProperties.SIGNATURE_CONTACT_INFO);
            PDMetadataExtractor.addNotNull(signature.getFilter(), metadata, TikaCoreProperties.SIGNATURE_FILTER);
            PDMetadataExtractor.addNotNull(signature.getLocation(), metadata, TikaCoreProperties.SIGNATURE_LOCATION);
            PDMetadataExtractor.addNotNull(signature.getReason(), metadata, TikaCoreProperties.SIGNATURE_REASON);
            hasSignature = true;

        }

        if (hasSignature) {
            metadata.set(TikaCoreProperties.HAS_SIGNATURE, hasSignature);
        }
    }

    private boolean shouldSpool(PDFParserConfig localConfig) {
        if (localConfig.getImageStrategy() == PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_BEFORE_PARSE
                || localConfig.getImageStrategy() == PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_AT_PAGE_END) {
            return true;
        }
        if (localConfig.isExtractIncrementalUpdateInfo() ||
                localConfig.isParseIncrementalUpdates()) {
            return true;
        }

        if (localConfig.getOcrStrategy() == OcrConfig.Strategy.NO_OCR) {
            return false;
        }
        //TODO: test that this is not AUTO with no OCR parser installed
        return true;
    }

    private void renderPagesBeforeParse(TikaInputStream tstream,
                                        ContentHandler xhtml, Metadata parentMetadata,
                                        ParseContext context,
                                        PDFParserConfig config) {
        if (config.getImageStrategy() != PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_BEFORE_PARSE) {
            return;
        }
        RenderResults renderResults = null;
        try {
            renderResults = renderPDF(tstream, context, config);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            EmbeddedDocumentUtil.recordException(e, parentMetadata);
            return;
        }
        context.get(PDFRenderingState.class).setRenderResults(renderResults);
        EmbeddedDocumentExtractor embeddedDocumentExtractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);

        for (RenderResult result : renderResults.getResults()) {
            if (result.getStatus() == RenderResult.STATUS.SUCCESS) {
                if (embeddedDocumentExtractor.shouldParseEmbedded(result.getMetadata())) {
                    try (TikaInputStream tis = result.getInputStream()) {
                        embeddedDocumentExtractor.parseEmbedded(tis, xhtml, result.getMetadata(),
                                false);
                    } catch (SecurityException e) {
                        throw e;
                    } catch (Exception e) {
                        EmbeddedDocumentUtil.recordException(e, parentMetadata);
                    }
                }
            }
        }
    }

    private RenderResults renderPDF(TikaInputStream tstream,
                                    ParseContext parseContext, PDFParserConfig localConfig)
            throws IOException, TikaException {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TYPE, MEDIA_TYPE.toString());
        return renderer.render(
                tstream, metadata, parseContext, PageRangeRequest.RENDER_ALL);
    }

    protected PDDocument getPDDocument(TikaInputStream tis, String password,
                                       RandomAccessStreamCache.StreamCacheCreateFunction streamCacheCreateFunction,
                                       Metadata metadata,
                                       ParseContext context)
            throws IOException, EncryptedDocumentException {
        try {
            PDDocument pdDocument = null;
            if (tis.hasFile()) {
                // File based -- send file directly to PDFBox
                pdDocument =
                        getPDDocument(tis.getPath(), password, streamCacheCreateFunction, metadata, context);
            } else {
                tis.setCloseShield();
                try {
                    pdDocument = getPDDocumentFromStream(tis, password,
                            streamCacheCreateFunction, metadata, context);
                } finally {
                    tis.removeCloseShield();
                }
            }
            return pdDocument;
        } catch (IOException e) {
            if (e.getMessage() != null &&
                    e.getMessage().contains("No security handler for filter")) {
                throw new EncryptedDocumentException(e);
            }
            throw e;
        }
    }

    protected PDDocument getPDDocumentFromStream(InputStream inputStream, String password,
                                       RandomAccessStreamCache.StreamCacheCreateFunction streamCacheCreateFunction,
                                       Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        return Loader.loadPDF(new RandomAccessReadBuffer(inputStream), password, streamCacheCreateFunction);
    }

    protected PDDocument getPDDocument(Path path, String password,
                                       RandomAccessStreamCache.StreamCacheCreateFunction
                                        streamCacheCreateFunction, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        return Loader.loadPDF(path.toFile(), password, streamCacheCreateFunction);
    }

    private boolean hasMarkedContent(PDDocument pdDocument, Metadata metadata) {
        boolean hasMarkedContent = hasMarkedContent(pdDocument);
        metadata.set(PDF.HAS_MARKED_CONTENT, hasMarkedContent);
        return hasMarkedContent;
    }

    private boolean hasMarkedContent(PDDocument pdDocument) {
        boolean hasMarkedContent;
        PDStructureTreeRoot root = pdDocument.getDocumentCatalog().getStructureTreeRoot();
        if (root == null) {
            return false;
        }
        COSBase base = root.getK();
        if (base == null) {
            return false;
        }
        //TODO: are there other checks we need to perform?
        if (base instanceof COSDictionary) {
            if (((COSDictionary) base).keySet().size() > 0) {
                return true;
            }
        } else if (base instanceof COSArray) {
            if (((COSArray) base).size() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCollection(PDDocument pdDocument, Metadata metadata) {
        boolean hasCollection = hasCollection(pdDocument);
        metadata.set(PDF.HAS_COLLECTION, hasCollection);
        return hasCollection;
    }

    private boolean hasCollection(PDDocument pdfDocument) {
        COSDictionary cosDict = pdfDocument.getDocumentCatalog().getCOSObject();
        if (cosDict.containsKey(COSName.COLLECTION)) {
            return true;
        }
        return false;
    }

    private String getPassword(Metadata metadata, ParseContext context) {
        String password = null;

        // Did they supply a Password Provider?
        PasswordProvider passwordProvider = context.get(PasswordProvider.class);
        if (passwordProvider != null) {
            password = passwordProvider.getPassword(metadata);
        }

        // If no password is given, use an empty string as the default
        if (password == null) {
            password = "";
        }
        return password;
    }


    private void extractMetadata(PDDocument document, Metadata metadata, ParseContext context)
            throws TikaException {
        metadata.set(Metadata.CONTENT_TYPE, MEDIA_TYPE.toString());

        //first extract AccessPermissions
        AccessPermission ap = document.getCurrentAccessPermission();
        metadata.set(AccessPermissions.EXTRACT_FOR_ACCESSIBILITY,
                Boolean.toString(ap.canExtractForAccessibility()));
        metadata.set(AccessPermissions.EXTRACT_CONTENT, Boolean.toString(ap.canExtractContent()));
        metadata.set(AccessPermissions.ASSEMBLE_DOCUMENT,
                Boolean.toString(ap.canAssembleDocument()));
        metadata.set(AccessPermissions.FILL_IN_FORM, Boolean.toString(ap.canFillInForm()));
        metadata.set(AccessPermissions.CAN_MODIFY, Boolean.toString(ap.canModify()));
        metadata.set(AccessPermissions.CAN_MODIFY_ANNOTATIONS,
                Boolean.toString(ap.canModifyAnnotations()));
        metadata.set(AccessPermissions.CAN_PRINT, Boolean.toString(ap.canPrint()));
        metadata.set(AccessPermissions.CAN_PRINT_FAITHFUL,
                Boolean.toString(ap.canPrintFaithful()));
        metadata.set(PDF.IS_ENCRYPTED, Boolean.toString(document.isEncrypted()));

        if (document.getDocumentCatalog().getLanguage() != null) {
            metadata.set(TikaCoreProperties.LANGUAGE, document.getDocumentCatalog().getLanguage());
        }
        // TIKA-3246: Do this for the first call of getAcroForm(),
        // subsequent calls should use the same fixup or null to avoid a default fixup.
        // Do not call without parameters (would mean default fixup which is slower because
        // it creates annotation appearances)
        PDDocumentFixup fixup = new TikaAcroFormFixup(document);
        PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm(fixup);
        if (acroForm != null && acroForm.getFields() != null && !acroForm.getFields().isEmpty()) {
            metadata.set(PDF.HAS_ACROFORM_FIELDS, "true");
        }
        PDMetadataExtractor.extract(document.getDocumentCatalog().getMetadata(), metadata, context);

        PDDocumentInformation info = document.getDocumentInformation();
        metadata.set(PagedText.N_PAGES, document.getNumberOfPages());
        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_TITLE, info.getTitle());
        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_CREATOR, info.getAuthor());
        //if this wasn't already set by xmp, use doc info
        if (metadata.get(TikaCoreProperties.CREATOR) == null) {
            PDMetadataExtractor.addMetadata(metadata, TikaCoreProperties.CREATOR, info.getAuthor());
        }
        if (metadata.get(TikaCoreProperties.TITLE) == null) {
            PDMetadataExtractor.addMetadata(metadata, TikaCoreProperties.TITLE, info.getTitle());
        }
        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_CREATOR_TOOL, info.getCreator());
        PDMetadataExtractor
                .addMetadata(metadata, TikaCoreProperties.CREATOR_TOOL, info.getCreator());
        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_KEY_WORDS, info.getKeywords());
        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_PRODUCER, info.getProducer());
        PDMetadataExtractor.addMetadata(metadata, PDF.PRODUCER, info.getProducer());

        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_SUBJECT, info.getSubject());

        PDMetadataExtractor.addMetadata(metadata, TikaCoreProperties.SUBJECT, info.getKeywords());
        PDMetadataExtractor.addMetadata(metadata, TikaCoreProperties.SUBJECT, info.getSubject());

        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_TRAPPED, info.getTrapped());
        Calendar created = info.getCreationDate();
        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_CREATED, created);
        PDMetadataExtractor.addMetadata(metadata, TikaCoreProperties.CREATED, created);
        Calendar modified = info.getModificationDate();
        PDMetadataExtractor.addMetadata(metadata, TikaCoreProperties.MODIFIED, modified);
        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_MODIFICATION_DATE, modified);

        // All remaining metadata is custom
        // Copy this over as-is
        List<String> handledMetadata =
                Arrays.asList("Author", "Creator", "CreationDate", "ModDate", "Keywords",
                        "Producer", "Subject", "Title", "Trapped");
        for (COSName key : info.getCOSObject().keySet()) {
            String name = key.getName();
            if (!handledMetadata.contains(name)) {
                PDMetadataExtractor.addMetadata(metadata, PDF.PDF_DOC_INFO_CUSTOM_PREFIX + name,
                        info.getCOSObject().getDictionaryObject(key));
            }
        }

        //try to get the various versions
        //Caveats:
        //    there is currently a fair amount of redundancy
        //    TikaCoreProperties.FORMAT can be multivalued
        //    There are also three potential pdf specific version keys:
        //    pdf:PDFVersion, pdfa:PDFVersion, pdf:PDFExtensionVersion
        metadata.set(PDF.PDF_VERSION, Float.toString(document.getDocument().getVersion()));
        metadata.add(TikaCoreProperties.FORMAT.getName(), MEDIA_TYPE.toString() + "; version=" +
                Float.toString(document.getDocument().getVersion()));


        //TODO: Let's try to move this into PDFBox.
        //Attempt to determine Adobe extension level, if present:
        COSDictionary root = document.getDocumentCatalog().getCOSObject();
        COSDictionary extensions =
                (COSDictionary) root.getDictionaryObject(COSName.getPDFName("Extensions"));
        if (extensions != null) {
            for (COSName extName : extensions.keySet()) {
                // If it's an Adobe one, interpret it to determine the extension level:
                if (extName.equals(COSName.getPDFName("ADBE"))) {
                    COSDictionary adobeExt =
                            (COSDictionary) extensions.getDictionaryObject(extName);
                    if (adobeExt != null) {
                        String baseVersion =
                                adobeExt.getNameAsString(COSName.getPDFName("BaseVersion"));
                        int el = adobeExt.getInt(COSName.getPDFName("ExtensionLevel"));
                        //-1 is sentinel value that something went wrong in getInt
                        if (el != -1) {
                            metadata.set(PDF.PDF_EXTENSION_VERSION,
                                    baseVersion + " Adobe Extension Level " + el);
                            metadata.add(TikaCoreProperties.FORMAT.getName(),
                                    MEDIA_TYPE.toString() + "; version=\"" + baseVersion +
                                            " Adobe Extension Level " + el + "\"");
                        }
                    }
                } else {
                    // WARN that there is an Extension, but it's not Adobe's, and so is a 'new'
                    // format'.
                    metadata.set("pdf:foundNonAdobeExtensionName", extName.getName());
                }
            }
        }
    }


    private boolean hasXFA(PDDocument pdDocument, Metadata metadata) {
        boolean hasXFA = pdDocument.getDocumentCatalog() != null &&
                pdDocument.getDocumentCatalog().getAcroForm(null) != null &&
                pdDocument.getDocumentCatalog().getAcroForm(null).hasXFA();
        metadata.set(PDF.HAS_XFA, Boolean.toString(hasXFA));
        return hasXFA;
    }

    private boolean shouldHandleXFAOnly(boolean hasXFA, PDFParserConfig config) {
        return config.isIfXFAExtractOnlyXFA() && hasXFA;
    }

    private void handleXFAOnly(PDDocument pdDocument, ContentHandler handler, Metadata metadata,
                               ParseContext context)
            throws SAXException, IOException, TikaException {
        XFAExtractor ex = new XFAExtractor();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        try (InputStream is = 
                UnsynchronizedByteArrayInputStream.builder().setByteArray(pdDocument.getDocumentCatalog().getAcroForm(null).getXFA().getBytes()).get()) {
            ex.extract(is, xhtml, metadata, context);
        } catch (XMLStreamException e) {
            throw new TikaException("XML error in XFA", e);
        }
        xhtml.endDocument();
    }

    public PDFParserConfig getPDFParserConfig() {
        return defaultConfig;
    }

    public void setPDFParserConfig(PDFParserConfig config) {
        this.defaultConfig = config;
    }

    /**
     * @see #setEnableAutoSpace(boolean)
     */
    public boolean isEnableAutoSpace() {
        return defaultConfig.isEnableAutoSpace();
    }

    /**
     * If true (the default), the parser should estimate
     * where spaces should be inserted between words.  For
     * many PDFs this is necessary as they do not include
     * explicit whitespace characters.
     */
    public void setEnableAutoSpace(boolean v) {
        defaultConfig.setEnableAutoSpace(v);
    }

    /**
     * If true, text in annotations will be extracted.
     *
     */
    public boolean isExtractAnnotationText() {
        return defaultConfig.isExtractAnnotationText();
    }

    /**
     * If true (the default), text in annotations will be
     * extracted.
     */
    public void setExtractAnnotationText(boolean v) {
        defaultConfig.setExtractAnnotationText(v);
    }

    /**
     * @see #setSuppressDuplicateOverlappingText(boolean)
     */
    public boolean isSuppressDuplicateOverlappingText() {
        return defaultConfig.isSuppressDuplicateOverlappingText();
    }

    /**
     * If true, the parser should ignore spaces in the content stream and rely purely on the
     * algorithm to determine where word breaks are (PDFBOX-3774). This can improve text extraction
     * results where the content stream is sorted by position and has text overlapping spaces, but
     * could cause some word breaks to not be added to the output. By default this is disabled.
     */
    public void setIgnoreContentStreamSpaceGlyphs(boolean v) {
        defaultConfig.setIgnoreContentStreamSpaceGlyphs(v);
    }

    /**
     * @see #setIgnoreContentStreamSpaceGlyphs(boolean)
     */
    public boolean isIgnoreContentStreamSpaceGlyphs() {
        return defaultConfig.isIgnoreContentStreamSpaceGlyphs();
    }

    /**
     * If true, the parser should try to remove duplicated
     * text over the same region.  This is needed for some
     * PDFs that achieve bolding by re-writing the same
     * text in the same area.  Note that this can
     * slow down extraction substantially (PDFBOX-956) and
     * sometimes remove characters that were not in fact
     * duplicated (PDFBOX-1155).  By default this is disabled.
     */
    public void setSuppressDuplicateOverlappingText(boolean v) {
        defaultConfig.setSuppressDuplicateOverlappingText(v);
    }

    /**
     * @see #setSortByPosition(boolean)
     */
    public boolean isSortByPosition() {
        return defaultConfig.isSortByPosition();
    }

    /**
     * If true, sort text tokens by their x/y position
     * before extracting text.  This may be necessary for
     * some PDFs (if the text tokens are not rendered "in
     * order"), while for other PDFs it can produce the
     * wrong result (for example if there are 2 columns,
     * the text will be interleaved).  Default is false.
     */
    public void setSortByPosition(boolean v) {
        defaultConfig.setSortByPosition(v);
    }

    public void setOcrStrategy(OcrConfig.Strategy ocrStrategy) {
        defaultConfig.setOcrStrategy(ocrStrategy);
    }

    public OcrConfig.Strategy getOcrStrategy() {
        return defaultConfig.getOcrStrategy();
    }

    public void setOcrStrategyAuto(OcrConfig.StrategyAuto ocrStrategyAuto) {
        defaultConfig.setOcrStrategyAuto(ocrStrategyAuto);
    }

    public OcrConfig.StrategyAuto getOcrStrategyAuto() {
        return defaultConfig.getOcrStrategyAuto();
    }

    public void setOcrRenderingStrategy(OcrConfig.RenderingStrategy ocrRenderingStrategy) {
        defaultConfig.setOcrRenderingStrategy(ocrRenderingStrategy);
    }

    public OcrConfig.RenderingStrategy getOcrRenderingStrategy() {
        return defaultConfig.getOcrRenderingStrategy();
    }

    public void setOcrImageType(OcrConfig.ImageType ocrImageType) {
        defaultConfig.setOcrImageType(ocrImageType);
    }

    public OcrConfig.ImageType getOcrImageType() {
        return defaultConfig.getOcrImageType();
    }

    public void setOcrDPI(int dpi) {
        defaultConfig.setOcrDPI(dpi);
    }

    public int getOcrDPI() {
        return defaultConfig.getOcrDPI();
    }
    public void setOcrImageQuality(float imageQuality) {
        defaultConfig.setOcrImageQuality(imageQuality);
    }

    public float getOcrImageQuality() {
        return defaultConfig.getOcrImageQuality();
    }

    public void setOcrImageFormat(OcrConfig.ImageFormat imageFormat) {
        defaultConfig.setOcrImageFormat(imageFormat);
    }

    public OcrConfig.ImageFormat getOcrImageFormat() {
        return defaultConfig.getOcrImageFormat();
    }

    public void setExtractBookmarksText(boolean extractBookmarksText) {
        defaultConfig.setExtractBookmarksText(extractBookmarksText);
    }

    public boolean isExtractBookmarksText() {
        return defaultConfig.isExtractBookmarksText();
    }

    public void setExtractInlineImages(boolean extractInlineImages) {
        defaultConfig.setExtractInlineImages(extractInlineImages);
    }

    public boolean isExtractInlineImages() {
        return defaultConfig.isExtractInlineImages();
    }

    public void setExtractInlineImageMetadataOnly(boolean extractInlineImageMetadataOnly) {
        defaultConfig.setExtractInlineImageMetadataOnly(extractInlineImageMetadataOnly);
    }

    public boolean isExtractInlineImageMetadataOnly() {
        return defaultConfig.isExtractInlineImageMetadataOnly();
    }

    public void setAverageCharTolerance(float averageCharTolerance) {
        defaultConfig.setAverageCharTolerance(averageCharTolerance);
    }

    public float getAverageCharTolerance() {
        return defaultConfig.getAverageCharTolerance();
    }

    public void setSpacingTolerance(float spacingTolerance) {
        defaultConfig.setSpacingTolerance(spacingTolerance);
    }

    public float getSpacingTolerance() {
        return defaultConfig.getSpacingTolerance();
    }


    public void setCatchIntermediateExceptions(boolean catchIntermediateExceptions) {
        defaultConfig.setCatchIntermediateIOExceptions(catchIntermediateExceptions);
    }

    public boolean isCatchIntermediateExceptions() {
        return defaultConfig.isCatchIntermediateIOExceptions();
    }

    public void setExtractAcroFormContent(boolean extractAcroFormContent) {
        defaultConfig.setExtractAcroFormContent(extractAcroFormContent);
    }

    public boolean isExtractAcroFormContent() {
        return defaultConfig.isExtractAcroFormContent();
    };

    public void setIfXFAExtractOnlyXFA(boolean ifXFAExtractOnlyXFA) {
        defaultConfig.setIfXFAExtractOnlyXFA(ifXFAExtractOnlyXFA);
    }

    public boolean isIfXFAExtractOnlyXFA() {
        return defaultConfig.isIfXFAExtractOnlyXFA();
    }
    public void setAccessCheckMode(PDFParserConfig.AccessCheckMode mode) {
        defaultConfig.setAccessCheckMode(mode);
    }

    public PDFParserConfig.AccessCheckMode getAccessCheckMode() {
        return defaultConfig.getAccessCheckMode();
    }

    public void setExtractUniqueInlineImagesOnly(boolean extractUniqueInlineImagesOnly) {
        defaultConfig.setExtractUniqueInlineImagesOnly(extractUniqueInlineImagesOnly);
    }

    public boolean isExtractUniqueInlineImagesOnly() {
        return defaultConfig.isExtractUniqueInlineImagesOnly();
    }

    public void setExtractActions(boolean extractActions) {
        defaultConfig.setExtractActions(extractActions);
    }

    public boolean isExtractActions() {
        return defaultConfig.isExtractActions();
    }

    public void setExtractFontNames(boolean extractFontNames) {
        defaultConfig.setExtractFontNames(extractFontNames);
    }

    public boolean isExtractFontNames() {
        return defaultConfig.isExtractFontNames();
    }

    public void setSetKCMS(boolean setKCMS) {
        defaultConfig.setSetKCMS(setKCMS);
    }

    public boolean isSetKCMS() {
        return defaultConfig.isSetKCMS();
    }
    public void setDetectAngles(boolean detectAngles) {
        defaultConfig.setDetectAngles(detectAngles);
    }

    public boolean isDetectAngles() {
        return defaultConfig.isDetectAngles();
    }
    public void setExtractMarkedContent(boolean extractMarkedContent) {
        defaultConfig.setExtractMarkedContent(extractMarkedContent);
    }

    public boolean isExtractMarkedContent() {
        return defaultConfig.isExtractMarkedContent();
    }

    public void setDropThreshold(float dropThreshold) {
        defaultConfig.setDropThreshold(dropThreshold);
    }

    public float getDropThreshold() {
        return defaultConfig.getDropThreshold();
    }

    public void setMaxMainMemoryBytes(long maxMainMemoryBytes) {
        defaultConfig.setMaxMainMemoryBytes(maxMainMemoryBytes);
    }

    /**
     * Whether or not to scan a PDF for incremental updates.
     * @param setExtractIncrementalUpdateInfo
     */
    public void setExtractIncrementalUpdateInfo(boolean setExtractIncrementalUpdateInfo) {
        defaultConfig.setExtractIncrementalUpdateInfo(setExtractIncrementalUpdateInfo);
    }

    public long getMaxMainMemoryBytes() {
        return defaultConfig.getMaxMainMemoryBytes();
    }

    public boolean isExtractIncrementalUpdateInfo() {
        return defaultConfig.isExtractIncrementalUpdateInfo();
    }

    /**
     * If set to true, this will parse incremental updates if they exist
     * within a PDF.  If set to <code>true</code>, this will override
     * {@link #setExtractIncrementalUpdateInfo(boolean)}.
     *
     * @param parseIncrementalUpdates
     */
    public void setParseIncrementalUpdates(boolean parseIncrementalUpdates) {
        defaultConfig.setParseIncrementalUpdates(parseIncrementalUpdates);
    }

    public boolean isParseIncrementalUpdates() {
        return defaultConfig.isParseIncrementalUpdates();
    }

    /**
     * Set the maximum number of incremental updates to parse
     * @param maxIncrementalUpdates
     */
    public void setMaxIncrementalUpdates(int maxIncrementalUpdates) {
        defaultConfig.setMaxIncrementalUpdates(maxIncrementalUpdates);
    }

    public int getMaxIncrementalUpdates() {
        return defaultConfig.getMaxIncrementalUpdates();
    }

    /**
     * If the file is a 'Collection' and contains an embedded file with a
     * defined 'AssociatedFile' value of 'EncryptedPayload', then throw an
     * {@link EncryptedDocumentException}.
     *<p>
     * Microsoft IRM v2 wraps the encrypted document inside a container PDF.
     * See TIKA-4082.
     * <p>
     * The goal of this is to make the user experience the same for
     * traditionally encrypted files and PDFs that are containers
     * for `EncryptedPayload`s.
     * <p>
     * The default value is <code>false</code>.
     *
     * @param throwOnEncryptedPayload
     */
    public void setThrowOnEncryptedPayload(boolean throwOnEncryptedPayload) {
        defaultConfig.setThrowOnEncryptedPayload(throwOnEncryptedPayload);
    }

    public boolean isThrowOnEncryptedPayload() {
        return defaultConfig.isThrowOnEncryptedPayload();
    }
    private void initRenderer(PDFParserConfig config, ParseContext context) {
        if (this.renderer != null &&
                this.renderer.getSupportedTypes(context).contains(MEDIA_TYPE)) {
            return;
        }
        //set a default renderer if nothing was defined
        PDFBoxRenderer pdfBoxRenderer = new PDFBoxRenderer();
        pdfBoxRenderer.setDPI(config.getOcrDPI());
        pdfBoxRenderer.setImageType(config.getOcrImageType().getPdfBoxImageType());
        pdfBoxRenderer.setImageFormatName(config.getOcrImageFormat().getFormatName());
        this.renderer = pdfBoxRenderer;
    }

    @Override
    public void setRenderer(Renderer renderer) {
        this.renderer = renderer;
    }

    public Renderer getRenderer() {
        return renderer;
    }

    public void setImageGraphicsEngineFactory(ImageGraphicsEngineFactory imageGraphicsEngineFactory) {
        defaultConfig.setImageGraphicsEngineFactory(imageGraphicsEngineFactory);
    }

    public ImageGraphicsEngineFactory getImageGraphicsEngineFactory() {
        return defaultConfig.getImageGraphicsEngineFactory();
    }

    public void setImageStrategy(PDFParserConfig.IMAGE_STRATEGY imageStrategy) {
        defaultConfig.setImageStrategy(imageStrategy);
    }

    public PDFParserConfig.IMAGE_STRATEGY getImageStrategy() {
        return defaultConfig.getImageStrategy();
    }

    /**
     * Copied from AcroformDefaultFixup minus generation of appearances and handling of orphan
     * widgets, which we don't need.
     */
    static class TikaAcroFormFixup extends AbstractFixup {
        TikaAcroFormFixup(PDDocument document) {
            super(document);
        }

        @Override
        public void apply() {
            new AcroFormDefaultsProcessor(document).process();
        }
    }
}
