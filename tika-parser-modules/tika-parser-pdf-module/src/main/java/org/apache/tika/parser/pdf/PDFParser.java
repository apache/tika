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

import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.AccessPermissions;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

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
 * Please note that tables are not stored as entities within PDFs. It
 * takes significant computation to identify and then correctly extract
 * tables from PDFs. As of this writing, the {@link PDFParser} extracts
 * text within tables, but it does not compute table cell boundaries or
 * table row boundaries. Please see
 * <a href="http://tabula.technology/">tabula</a> for one project that
 * tries to maintain the structure of tables represented in PDFs.
 */
public class PDFParser extends AbstractParser implements Initializable {

    private static volatile boolean HAS_WARNED = false;
    private static final Object[] LOCK = new Object[0];

    /**
     * Metadata key for giving the document password to the parser.
     *
     * @since Apache Tika 0.5
     * @deprecated Supply a {@link PasswordProvider} on the {@link ParseContext} instead
     */
    public static final String PASSWORD = "org.apache.tika.parser.pdf.password";
    private static final MediaType MEDIA_TYPE = MediaType.application("pdf");
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -752276948656079347L;
    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MEDIA_TYPE);
    private PDFParserConfig defaultConfig = new PDFParserConfig();
    private InitializableProblemHandler initializableProblemHandler = null;

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        PDFParserConfig localConfig = context.get(PDFParserConfig.class, defaultConfig);
        if (localConfig.getSetKCMS()) {
            System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");
        }

        PDDocument pdfDocument = null;

        String password = "";
        try {
            TikaInputStream tstream = TikaInputStream.cast(stream);
            password = getPassword(metadata, context);
            MemoryUsageSetting memoryUsageSetting = MemoryUsageSetting.setupMainMemoryOnly();
            if (localConfig.getMaxMainMemoryBytes() >= 0) {
                memoryUsageSetting = MemoryUsageSetting.setupMixed(localConfig.getMaxMainMemoryBytes());
            }
            if (tstream != null && tstream.hasFile()) {
                // File based -- send file directly to PDFBox
                pdfDocument = getPDDocument(tstream, password, memoryUsageSetting, metadata,
                        context);
            } else {
                pdfDocument = getPDDocument(new CloseShieldInputStream(stream), password,
                        memoryUsageSetting, metadata, context);
            }
            metadata.set(PDF.IS_ENCRYPTED, Boolean.toString(pdfDocument.isEncrypted()));

            metadata.set(Metadata.CONTENT_TYPE, MEDIA_TYPE.toString());
            extractMetadata(pdfDocument, metadata, context);
            AccessChecker checker = localConfig.getAccessChecker();
            checker.check(metadata);
            if (handler != null) {
                boolean hasXFA = hasXFA(pdfDocument);
                metadata.set(PDF.HAS_XFA, Boolean.toString(hasXFA));
                boolean hasMarkedContent = hasMarkedContent(pdfDocument);
                metadata.set(PDF.HAS_MARKED_CONTENT, Boolean.toString(hasMarkedContent));
                if (shouldHandleXFAOnly(hasXFA, localConfig)) {
                    handleXFAOnly(pdfDocument, handler, metadata, context);
                } else if (localConfig.getOcrStrategy().equals(PDFParserConfig.OCR_STRATEGY.OCR_ONLY)) {
                    metadata.add("X-Parsed-By", TesseractOCRParser.class.toString());
                    OCR2XHTML.process(pdfDocument, handler, context, metadata, localConfig);
                } else if (hasMarkedContent && localConfig.getExtractMarkedContent()) {
                    PDFMarkedContent2XHTML.process(pdfDocument, handler, context, metadata, localConfig);
                } else {
                    if (localConfig.getOcrStrategy().equals(PDFParserConfig.OCR_STRATEGY.OCR_AND_TEXT_EXTRACTION)) {
                        metadata.add("X-Parsed-By", TesseractOCRParser.class.toString());
                    }
                    PDF2XHTML.process(pdfDocument, handler, context, metadata, localConfig);
                }
            }
        } catch (InvalidPasswordException e) {
            metadata.set(PDF.IS_ENCRYPTED, "true");
            throw new EncryptedDocumentException(e);
        } finally {
            if (pdfDocument != null) {
                pdfDocument.close();
            }
        }
    }

    protected PDDocument getPDDocument(InputStream inputStream, String password,
                                     MemoryUsageSetting memoryUsageSetting,
                                       Metadata metadata, ParseContext parseContext) throws IOException {
        return PDDocument.load(inputStream, password, memoryUsageSetting);
    }

    protected PDDocument getPDDocument(Path path, String password,
                                       MemoryUsageSetting memoryUsageSetting,
                                       Metadata metadata, ParseContext parseContext) throws IOException {
        return PDDocument.load(path.toFile(), password, memoryUsageSetting);
    }

    private boolean hasMarkedContent(PDDocument pdDocument) {
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
            if (((COSDictionary)base).keySet().size() > 0) {
                return true;
            }
        } else if (base instanceof COSArray) {
            if (((COSArray) base).size() > 0) {
                return true;
            }
        }
        return false;
    }

    private String getPassword(Metadata metadata, ParseContext context) {
        String password = null;

        // Did they supply a new style Password Provider?
        PasswordProvider passwordProvider = context.get(PasswordProvider.class);
        if (passwordProvider != null) {
            password = passwordProvider.getPassword(metadata);
        }

        // Fall back on the old style metadata if set
        if (password == null && metadata.get(PASSWORD) != null) {
            password = metadata.get(PASSWORD);
        }

        // If no password is given, use an empty string as the default
        if (password == null) {
            password = "";
        }
        return password;
    }


    private void extractMetadata(PDDocument document, Metadata metadata, ParseContext context)
            throws TikaException {

        //first extract AccessPermissions
        AccessPermission ap = document.getCurrentAccessPermission();
        metadata.set(AccessPermissions.EXTRACT_FOR_ACCESSIBILITY,
                Boolean.toString(ap.canExtractForAccessibility()));
        metadata.set(AccessPermissions.EXTRACT_CONTENT,
                Boolean.toString(ap.canExtractContent()));
        metadata.set(AccessPermissions.ASSEMBLE_DOCUMENT,
                Boolean.toString(ap.canAssembleDocument()));
        metadata.set(AccessPermissions.FILL_IN_FORM,
                Boolean.toString(ap.canFillInForm()));
        metadata.set(AccessPermissions.CAN_MODIFY,
                Boolean.toString(ap.canModify()));
        metadata.set(AccessPermissions.CAN_MODIFY_ANNOTATIONS,
                Boolean.toString(ap.canModifyAnnotations()));
        metadata.set(AccessPermissions.CAN_PRINT,
                Boolean.toString(ap.canPrint()));
        metadata.set(AccessPermissions.CAN_PRINT_DEGRADED,
                Boolean.toString(ap.canPrintDegraded()));

        if (document.getDocumentCatalog().getLanguage() != null) {
            metadata.set(TikaCoreProperties.LANGUAGE, document.getDocumentCatalog().getLanguage());
        }
        if (document.getDocumentCatalog().getAcroForm() != null &&
            document.getDocumentCatalog().getAcroForm().getFields() != null &&
            document.getDocumentCatalog().getAcroForm().getFields().size() > 0) {
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
        PDMetadataExtractor.addMetadata(metadata, TikaCoreProperties.CREATOR_TOOL, info.getCreator());
        PDMetadataExtractor.addMetadata(metadata, Office.KEYWORDS, info.getKeywords());
        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_KEY_WORDS, info.getKeywords());
        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_PRODUCER, info.getProducer());

        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_SUBJECT, info.getSubject());

        PDMetadataExtractor.addMetadata(metadata, TikaCoreProperties.SUBJECT, info.getKeywords());
        PDMetadataExtractor.addMetadata(metadata, TikaCoreProperties.SUBJECT, info.getSubject());
        PDMetadataExtractor.addMetadata(metadata, OfficeOpenXMLCore.SUBJECT, info.getSubject());

        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_TRAPPED, info.getTrapped());
        Calendar created = info.getCreationDate();
        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_CREATED, created);
        PDMetadataExtractor.addMetadata(metadata, TikaCoreProperties.CREATED, created);
        Calendar modified = info.getModificationDate();
        PDMetadataExtractor.addMetadata(metadata, TikaCoreProperties.MODIFIED, modified);
        PDMetadataExtractor.addMetadata(metadata, PDF.DOC_INFO_MODIFICATION_DATE, modified);

        // All remaining metadata is custom
        // Copy this over as-is
        List<String> handledMetadata = Arrays.asList("Author", "Creator", "CreationDate", "ModDate",
                "Keywords", "Producer", "Subject", "Title", "Trapped");
        for (COSName key : info.getCOSObject().keySet()) {
            String name = key.getName();
            if (!handledMetadata.contains(name)) {
                PDMetadataExtractor.addMetadata(metadata, name, info.getCOSObject().getDictionaryObject(key));
                PDMetadataExtractor.addMetadata(metadata, PDF.PDF_DOC_INFO_CUSTOM_PREFIX + name,
                        info.getCOSObject().getDictionaryObject(key));
            }
        }

        //try to get the various versions
        //Caveats:
        //    there is currently a fair amount of redundancy
        //    TikaCoreProperties.FORMAT can be multivalued
        //    There are also three potential pdf specific version keys: pdf:PDFVersion, pdfa:PDFVersion, pdf:PDFExtensionVersion
        metadata.set(PDF.PDF_VERSION, Float.toString(document.getDocument().getVersion()));
        metadata.add(TikaCoreProperties.FORMAT.getName(),
                MEDIA_TYPE.toString() + "; version=" +
                        Float.toString(document.getDocument().getVersion()));


        //TODO: Let's try to move this into PDFBox.
        //Attempt to determine Adobe extension level, if present:
        COSDictionary root = document.getDocumentCatalog().getCOSObject();
        COSDictionary extensions = (COSDictionary) root.getDictionaryObject(COSName.getPDFName("Extensions"));
        if (extensions != null) {
            for (COSName extName : extensions.keySet()) {
                // If it's an Adobe one, interpret it to determine the extension level:
                if (extName.equals(COSName.getPDFName("ADBE"))) {
                    COSDictionary adobeExt = (COSDictionary) extensions.getDictionaryObject(extName);
                    if (adobeExt != null) {
                        String baseVersion = adobeExt.getNameAsString(COSName.getPDFName("BaseVersion"));
                        int el = adobeExt.getInt(COSName.getPDFName("ExtensionLevel"));
                        //-1 is sentinel value that something went wrong in getInt
                        if (el != -1) {
                            metadata.set(PDF.PDF_EXTENSION_VERSION, baseVersion + " Adobe Extension Level " + el);
                            metadata.add(TikaCoreProperties.FORMAT.getName(),
                                    MEDIA_TYPE.toString() + "; version=\"" + baseVersion + " Adobe Extension Level " + el + "\"");
                        }
                    }
                } else {
                    // WARN that there is an Extension, but it's not Adobe's, and so is a 'new' format'.
                    metadata.set("pdf:foundNonAdobeExtensionName", extName.getName());
                }
            }
        }
    }


    private boolean hasXFA(PDDocument pdDocument) {
        return pdDocument.getDocumentCatalog() != null &&
                pdDocument.getDocumentCatalog().getAcroForm() != null &&
                pdDocument.getDocumentCatalog().getAcroForm().hasXFA();
    }

    private boolean shouldHandleXFAOnly(boolean hasXFA, PDFParserConfig config) {
        return config.getIfXFAExtractOnlyXFA() && hasXFA;
    }

    private void handleXFAOnly(PDDocument pdDocument, ContentHandler handler,
                               Metadata metadata, ParseContext context)
        throws SAXException, IOException, TikaException {
        XFAExtractor ex = new XFAExtractor();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        try (InputStream is = new ByteArrayInputStream(
                pdDocument.getDocumentCatalog().getAcroForm().getXFA().getBytes())) {
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
    public boolean getEnableAutoSpace() {
        return defaultConfig.getEnableAutoSpace();
    }

    /**
     * If true (the default), the parser should estimate
     * where spaces should be inserted between words.  For
     * many PDFs this is necessary as they do not include
     * explicit whitespace characters.
     */
    @Field
    public void setEnableAutoSpace(boolean v) {
        defaultConfig.setEnableAutoSpace(v);
    }

    /**
     * If true, text in annotations will be extracted.
     *
     * @deprecated use {@link #getPDFParserConfig()}
     */
    public boolean getExtractAnnotationText() {
        return defaultConfig.getExtractAnnotationText();
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
     * @deprecated use {@link #getPDFParserConfig()}
     */
    public boolean getSuppressDuplicateOverlappingText() {
        return defaultConfig.getSuppressDuplicateOverlappingText();
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
    @Field
    public void setSuppressDuplicateOverlappingText(boolean v) {
        defaultConfig.setSuppressDuplicateOverlappingText(v);
    }

    /**
     * @see #setSortByPosition(boolean)
     * @deprecated use {@link #getPDFParserConfig()}
     */
    public boolean getSortByPosition() {
        return defaultConfig.getSortByPosition();
    }

    /**
     * If true, sort text tokens by their x/y position
     * before extracting text.  This may be necessary for
     * some PDFs (if the text tokens are not rendered "in
     * order"), while for other PDFs it can produce the
     * wrong result (for example if there are 2 columns,
     * the text will be interleaved).  Default is false.
     */
    @Field
    public void setSortByPosition(boolean v) {
        defaultConfig.setSortByPosition(v);
    }

    @Field
    public void setOcrStrategy(String ocrStrategyString) {
        defaultConfig.setOcrStrategy(ocrStrategyString);
    }

    @Field
    public void setOcrImageType(String imageType) {
        defaultConfig.setOcrImageType(imageType);
    }

    @Field
    void setOcrDPI(int dpi) {
        defaultConfig.setOcrDPI(dpi);
    }

    @Field
    void setOcrImageQuality(float imageQuality) {
        defaultConfig.setOcrImageQuality(imageQuality);
    }

    @Field
    void setOcrImageFormatName(String formatName) {
        defaultConfig.setOcrImageFormatName(formatName);
    }

	@Field
	void setExtractBookmarksText(boolean extractBookmarksText) {
		defaultConfig.setExtractBookmarksText(extractBookmarksText);
	}

    @Field
    void setExtractInlineImages(boolean extractInlineImages) {
        defaultConfig.setExtractInlineImages(extractInlineImages);
    }

    @Field
    void setExtractInlineImageMetadataOnly(boolean extractInlineImageMetadataOnly) {
        defaultConfig.setExtractInlineImageMetadataOnly(extractInlineImageMetadataOnly);
    }

    @Field
    void setAverageCharTolerance(float averageCharTolerance) {
        defaultConfig.setAverageCharTolerance(averageCharTolerance);
    }

    @Field
    void setSpacingTolerance(float spacingTolerance) {
        defaultConfig.setSpacingTolerance(spacingTolerance);
    }


    @Field
    void setCatchIntermediateExceptions(boolean catchIntermediateExceptions) {
        defaultConfig.setCatchIntermediateIOExceptions(catchIntermediateExceptions);
    }

    @Field
    void setExtractAcroFormContent(boolean extractAcroFormContent) {
        defaultConfig.setExtractAcroFormContent(extractAcroFormContent);
    }

    @Field
    void setIfXFAExtractOnlyXFA(boolean ifXFAExtractOnlyXFA) {
        defaultConfig.setIfXFAExtractOnlyXFA(ifXFAExtractOnlyXFA);
    }

    @Field
    void setAllowExtractionForAccessibility(boolean allowExtractionForAccessibility) {
        defaultConfig.setAccessChecker(new AccessChecker(allowExtractionForAccessibility));
    }

    @Field
    void setExtractUniqueInlineImagesOnly(boolean extractUniqueInlineImagesOnly) {
        defaultConfig.setExtractUniqueInlineImagesOnly(extractUniqueInlineImagesOnly);
    }

    @Field
    void setExtractActions(boolean extractActions) {
        defaultConfig.setExtractActions(extractActions);
    }

    @Field
    void setExtractFontNames(boolean extractFontNames) {
        defaultConfig.setExtractFontNames(extractFontNames);
    }

    @Field
    void setSetKCMS(boolean setKCMS) {
        defaultConfig.setSetKCMS(setKCMS);
    }

    @Field
    void setDetectAngles(boolean detectAngles) {
        defaultConfig.setDetectAngles(detectAngles);
    }

    @Field
    void setExtractMarkedContent(boolean extractMarkedContent) {
        defaultConfig.setExtractMarkedContent(extractMarkedContent);
    }

    @Field
    void setInitializableProblemHander(String name) {
        if ("ignore".equals(name)) {
            setInitializableProblemHandler(InitializableProblemHandler.IGNORE);
        } else if ("info".equalsIgnoreCase(name)) {
            setInitializableProblemHandler(InitializableProblemHandler.INFO);
        }   else if ("warn".equalsIgnoreCase(name)) {
            setInitializableProblemHandler(InitializableProblemHandler.WARN);
        }   else if ("throw".equalsIgnoreCase(name)) {
            setInitializableProblemHandler(InitializableProblemHandler.THROW);
        }
    }

    public void setInitializableProblemHandler(InitializableProblemHandler initializableProblemHandler) {
        this.initializableProblemHandler = initializableProblemHandler;
    }

    @Field
    public void setDropThreshold(float dropThreshold) {
        defaultConfig.setDropThreshold(dropThreshold);
    }

    @Field
    public void setMaxMainMemoryBytes(long maxMainMemoryBytes) {
        defaultConfig.setMaxMainMemoryBytes(maxMainMemoryBytes);
    }
    /**
     * This is a no-op.  There is no need to initialize multiple fields.
     * The regular field loading should happen without this.
     *
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //no-op
    }

    @Override
    public void checkInitialization(InitializableProblemHandler handler) throws TikaConfigException {
        //only check for these libraries once!
        if (HAS_WARNED) {
            return;
        }
        synchronized (LOCK) {
            if (HAS_WARNED) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            try {
                Class.forName("com.github.jaiimageio.impl.plugins.tiff.TIFFImageWriter");
            } catch (ClassNotFoundException e) {
                sb.append("TIFFImageWriter not loaded. tiff files will not be processed\n");
                sb.append("See https://pdfbox.apache.org/2.0/dependencies.html#jai-image-io\n");
                sb.append("for optional dependencies.\n");

            }

            try {
                Class.forName("com.github.jaiimageio.jpeg2000.impl.J2KImageReader");
            } catch (ClassNotFoundException e) {
                sb.append("J2KImageReader not loaded. JPEG2000 files will not be processed.\n");
                sb.append("See https://pdfbox.apache.org/2.0/dependencies.html#jai-image-io\n");
                sb.append("for optional dependencies.\n");
            }

            if (sb.length() > 0) {
                InitializableProblemHandler localInitializableProblemHandler =
                        (initializableProblemHandler == null) ?
                                handler : initializableProblemHandler;
                localInitializableProblemHandler.handleInitializableProblem("org.apache.tika.parsers.PDFParser",
                        sb.toString());
            }
            HAS_WARNED = true;
        }
    }
}
