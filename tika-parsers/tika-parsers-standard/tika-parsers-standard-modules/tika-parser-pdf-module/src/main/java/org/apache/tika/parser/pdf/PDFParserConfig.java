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

import java.io.Serializable;

import org.apache.pdfbox.text.PDFTextStripper;

import org.apache.tika.parser.pdf.image.ImageGraphicsEngineFactory;

/**
 * Config for PDFParser.
 * <p/>
 * This allows parameters to be set programmatically:
 * <ol>
 * <li>Calls to PDFParser, i.e. parser.getPDFParserConfig().setEnableAutoSpace() (as before)</li>
 * <li>Passing to PDFParser through a ParseContext: context.set(PDFParserConfig.class, config);</li>
 * </ol>
 * <p/>
 */
public class PDFParserConfig implements Serializable {

    private static final long serialVersionUID = 6492570218190936986L;

    /**
     * Mode for checking document access permissions.
     */
    public enum AccessCheckMode {
        /**
         * Don't check extraction permissions. Content will always be extracted
         * regardless of document permissions. This is the default for backwards
         * compatibility with Tika's legacy behavior (&lt;= v1.7).
         */
        DONT_CHECK,

        /**
         * Check permissions, but allow extraction for accessibility purposes if
         * extraction for accessibility is allowed.
         */
        ALLOW_EXTRACTION_FOR_ACCESSIBILITY,

        /**
         * If extraction is blocked, throw an {@link org.apache.tika.exception.AccessPermissionException}
         * even if the document allows extraction for accessibility.
         */
        IGNORE_ACCESSIBILITY_ALLOWANCE
    }

    // True if we let PDFBox "guess" where spaces should go:
    private boolean enableAutoSpace = true;

    // True if we let PDFBox remove duplicate overlapping text:
    private boolean suppressDuplicateOverlappingText = false;

    // True if we let PDFBox ignore spaces in the content stream and rely purely on the algorithm:
    private boolean ignoreContentStreamSpaceGlyphs = false;

    // True if we extract annotation text ourselves
    // (workaround for PDFBOX-1143):
    private boolean extractAnnotationText = true;

    // True if we should sort text tokens by position
    // (necessary for some PDFs, but messes up other PDFs):
    private boolean sortByPosition = false;

    //True if acroform content should be extracted
    private boolean extractAcroFormContent = true;

    //True if bookmarks content should be extracted
    private boolean extractBookmarksText = true;

    //True if inline PDXImage objects should be extracted
    private boolean extractInlineImages = false;

    //True if inline images should only have their metadata
    //extracted.
    private boolean extractInlineImageMetadataOnly = false;

    private ImageGraphicsEngineFactory imageGraphicsEngineFactory =
            new ImageGraphicsEngineFactory();

    //True if inline images (as identified by their object id within
    //a pdf file) should only be extracted once.
    private boolean extractUniqueInlineImagesOnly = true;

    //Should the PDFParser _try_ to extract marked content/structure tags (backoff to regular
    //text extraction if the given PDF doesn't have marked content)
    private boolean extractMarkedContent = false;

    //The character width-based tolerance value used to estimate where spaces in text should be
    // added. Default taken from PDFBox.
    private Float averageCharTolerance = 0.3f;

    //The space width-based tolerance value used to estimate where spaces in text should be added
    //Default taken from PDFBox.
    private Float spacingTolerance = 0.5f;

    // The multiplication factor for line height to decide when a new paragraph starts.
    //Default taken from PDFBox.
    private float dropThreshold = 2.5f;

    //If the PDF has an XFA element, process only that and skip extracting
    //content from elsewhere in the document.
    private boolean ifXFAExtractOnlyXFA = false;

    private OcrConfig ocr = new OcrConfig();

    /**
     * Should the entire document be rendered?
     */
    private IMAGE_STRATEGY imageStrategy = IMAGE_STRATEGY.NONE;
    private AccessCheckMode accessCheckMode = AccessCheckMode.DONT_CHECK;

    //The PDFParser can throw IOExceptions if there is a problem
    //with a streams.  If this is set to true, Tika's
    //parser catches these exceptions, reports them in the metadata
    //and then throws the first stored exception after the parse has completed.
    private boolean catchIntermediateIOExceptions = true;

    private boolean extractActions = false;

    private boolean extractFontNames = false;

    private long maxMainMemoryBytes = 512 * 1024 * 1024;

    private boolean setKCMS = false;

    private boolean detectAngles = false;

    private boolean extractIncrementalUpdateInfo = true;

    private boolean parseIncrementalUpdates = false;

    int maxIncrementalUpdates = 10;

    private boolean throwOnEncryptedPayload = false;

    /**
     * @return whether or not to extract only inline image metadata and not render the images
     */
    public boolean isExtractInlineImageMetadataOnly() {
        return extractInlineImageMetadataOnly;
    }

    /**
     * Use this when you want to know how many images of what formats are in a PDF
     * but you don't need to render the images (e.g. for OCR).  This is far
     * faster than {@link #extractInlineImages} because it doesn't have to render the
     * images, which can be very slow.  This does not extract metadata from
     * within each image, rather it extracts the XMP that may be stored
     * external to an image in PDImageXObjects.
     *
     * @param extractInlineImageMetadataOnly
     * @since 1.25
     */
    public void setExtractInlineImageMetadataOnly(boolean extractInlineImageMetadataOnly) {
        this.extractInlineImageMetadataOnly = extractInlineImageMetadataOnly;
    }

    public boolean isExtractMarkedContent() {
        return extractMarkedContent;
    }

    /**
     * If the PDF contains marked content, try to extract text and its marked structure.
     * If the PDF does not contain marked content, backoff to the regular PDF2XHTML for
     * text extraction.  As of 1.24, this is an "alpha" version.
     *
     * @param extractMarkedContent
     * @since 1.24
     */
    public void setExtractMarkedContent(boolean extractMarkedContent) {
        this.extractMarkedContent = extractMarkedContent;
    }

    /**
     * Configures the given pdf2XHTML.
     *
     * @param pdf2XHTML
     */
    public void configure(PDF2XHTML pdf2XHTML) {
        pdf2XHTML.setSortByPosition(isSortByPosition());
        if (isEnableAutoSpace()) {
            pdf2XHTML.setWordSeparator(" ");
        } else {
            pdf2XHTML.setWordSeparator("");
        }
        if (getAverageCharTolerance() != null) {
            pdf2XHTML.setAverageCharTolerance(getAverageCharTolerance());
        }
        if (getSpacingTolerance() != null) {
            pdf2XHTML.setSpacingTolerance(getSpacingTolerance());
        }
        if (getDropThreshold() != null) {
            pdf2XHTML.setDropThreshold(dropThreshold);
        }
        pdf2XHTML.setSuppressDuplicateOverlappingText(isSuppressDuplicateOverlappingText());
        pdf2XHTML.setIgnoreContentStreamSpaceGlyphs(isIgnoreContentStreamSpaceGlyphs());
    }

    /**
     * @see #setExtractAcroFormContent(boolean)
     */
    public boolean isExtractAcroFormContent() {
        return extractAcroFormContent;
    }

    /**
     * If true (the default), extract content from AcroForms
     * at the end of the document.  If an XFA is found,
     * try to process that, otherwise, process the AcroForm.
     *
     * @param extractAcroFormContent
     */
    public void setExtractAcroFormContent(boolean extractAcroFormContent) {
        this.extractAcroFormContent = extractAcroFormContent;
    }

    /**
     * @return how to handle XFA data if it exists
     * @see #setIfXFAExtractOnlyXFA(boolean)
     */
    public boolean isIfXFAExtractOnlyXFA() {
        return ifXFAExtractOnlyXFA;
    }

    /**
     * If false (the default), extract content from the full PDF
     * as well as the XFA form.  This will likely lead to some duplicative
     * content.
     *
     * @param ifXFAExtractOnlyXFA
     */
    public void setIfXFAExtractOnlyXFA(boolean ifXFAExtractOnlyXFA) {
        this.ifXFAExtractOnlyXFA = ifXFAExtractOnlyXFA;
    }

    /**
     * @see #setExtractBookmarksText(boolean)
     */
    public boolean isExtractBookmarksText() {
        return extractBookmarksText;
    }

    /**
     * If true, extract bookmarks (document outline) text.
     * <p/>
     * Te default is <code>true</code>
     *
     * @param extractBookmarksText
     */
    public void setExtractBookmarksText(boolean extractBookmarksText) {
        this.extractBookmarksText = extractBookmarksText;
    }

    public boolean isExtractFontNames() {
        return extractFontNames;
    }

    /**
     * Extract font names into a metadata field
     *
     * @param extractFontNames
     */
    public void setExtractFontNames(boolean extractFontNames) {
        this.extractFontNames = extractFontNames;
    }

    /**
     * @see #setExtractInlineImages(boolean)
     */
    public boolean isExtractInlineImages() {
        return extractInlineImages;
    }

    /**
     * If <code>true</code>, extract the literal inline embedded OBXImages.
     * <p/>
     * <b>Beware:</b> some PDF documents of modest size (~4MB) can contain
     * thousands of embedded images totaling &gt; 2.5 GB.  Also, at least as of PDFBox 1.8.5,
     * there can be surprisingly large memory consumption and/or out of memory errors.
     * <p/>
     * Along the same lines, note that this does not extract "logical" images.  Some PDF writers
     * break up a single logical image into hundreds of little images.  With this option set to
     * <code>true</code>, you might get those hundreds of little images.
     * <p/>
     * NOTE ALSO: this extracts the raw images without clipping, rotation, masks, color
     * inversion, etc. The images that this extracts may look nothing like what a human
     * would expect given the appearance of the PDF.
     * <p/>
     * Set to <code>true</code> only with the greatest caution.
     *
     * The default is <code>false</code>.
     * <p/>
     *
     * @param extractInlineImages
     * @see #setExtractUniqueInlineImagesOnly(boolean)
     */
    public void setExtractInlineImages(boolean extractInlineImages) {
        this.extractInlineImages = extractInlineImages;
    }

    /**
     * @see #setExtractUniqueInlineImagesOnly(boolean)
     */
    public boolean isExtractUniqueInlineImagesOnly() {
        return extractUniqueInlineImagesOnly;
    }

    /**
     * Multiple pages within a PDF file might refer to the same underlying image.
     * If {@link #extractUniqueInlineImagesOnly} is set to <code>false</code>, the
     * parser will call the EmbeddedExtractor each time the image appears on a page.
     * This might be desired for some use cases.  However, to avoid duplication of
     * extracted images, set this to <code>true</code>.  The default is <code>true</code>.
     * <p/>
     * Note that uniqueness is determined only by the underlying PDF COSObject id, not by
     * file hash or similar equality metric.
     * If the PDF actually contains multiple copies of the same image
     * -- all with different object ids -- then all images will be extracted.
     * <p/>
     * For this parameter to have any effect, {@link #extractInlineImages} must be
     * set to <code>true</code>.
     * <p>
     * Because of TIKA-1742 -- to avoid infinite recursion -- no matter the setting
     * of this parameter, the extractor will only pull out one copy of each image per
     * page.  This parameter tries to capture uniqueness across the entire document.
     *
     * @param extractUniqueInlineImagesOnly
     */
    public void setExtractUniqueInlineImagesOnly(boolean extractUniqueInlineImagesOnly) {
        this.extractUniqueInlineImagesOnly = extractUniqueInlineImagesOnly;
    }

    /**
     * @see #setEnableAutoSpace(boolean)
     */
    public boolean isEnableAutoSpace() {
        return enableAutoSpace;
    }

    /**
     * If true (the default), the parser should estimate
     * where spaces should be inserted between words.  For
     * many PDFs this is necessary as they do not include
     * explicit whitespace characters.
     */
    public void setEnableAutoSpace(boolean enableAutoSpace) {
        this.enableAutoSpace = enableAutoSpace;
    }

    /**
     * @see #setSuppressDuplicateOverlappingText(boolean)
     */
    public boolean isSuppressDuplicateOverlappingText() {
        return suppressDuplicateOverlappingText;
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
    public void setSuppressDuplicateOverlappingText(boolean suppressDuplicateOverlappingText) {
        this.suppressDuplicateOverlappingText = suppressDuplicateOverlappingText;
    }

    /**
     * @see #setIgnoreContentStreamSpaceGlyphs(boolean)
     */
    public boolean isIgnoreContentStreamSpaceGlyphs() {
        return ignoreContentStreamSpaceGlyphs;
    }

    /**
     * If true, the parser should ignore spaces in the content stream and rely purely on the
     * algorithm to determine where word breaks are (PDFBOX-3774). This can improve text extraction
     * results where the content stream is sorted by position and has text overlapping spaces, but
     * could cause some word breaks to not be added to the output. By default this is disabled.
     */
    public void setIgnoreContentStreamSpaceGlyphs(boolean ignoreContentStreamSpaceGlyphs) {
        this.ignoreContentStreamSpaceGlyphs = ignoreContentStreamSpaceGlyphs;
    }

    /**
     * @see #setExtractAnnotationText(boolean)
     */
    public boolean isExtractAnnotationText() {
        return extractAnnotationText;
    }

    /**
     * If true (the default), text in annotations will be
     * extracted.
     */
    public void setExtractAnnotationText(boolean extractAnnotationText) {
        this.extractAnnotationText = extractAnnotationText;
    }

    /**
     * @see #setSortByPosition(boolean)
     */
    public boolean isSortByPosition() {
        return sortByPosition;
    }

    /**
     * If true, sort text tokens by their x/y position
     * before extracting text.  This may be necessary for
     * some PDFs (if the text tokens are not rendered "in
     * order"), while for other PDFs it can produce the
     * wrong result (for example if there are 2 columns,
     * the text will be interleaved).  Default is false.
     */
    public void setSortByPosition(boolean sortByPosition) {
        this.sortByPosition = sortByPosition;
    }

    /**
     * @see #setAverageCharTolerance(Float)
     */
    public Float getAverageCharTolerance() {
        return averageCharTolerance;
    }

    /**
     * See {@link PDFTextStripper#setAverageCharTolerance(float)}
     */
    public void setAverageCharTolerance(Float averageCharTolerance) {
        this.averageCharTolerance = averageCharTolerance;
    }

    /**
     * @see #setSpacingTolerance(Float)
     */
    public Float getSpacingTolerance() {
        return spacingTolerance;
    }

    /**
     * See {@link PDFTextStripper#setSpacingTolerance(float)}
     */
    public void setSpacingTolerance(Float spacingTolerance) {
        this.spacingTolerance = spacingTolerance;
    }

    /**
     * @see #setDropThreshold(Float)
     */
    public Float getDropThreshold() {
        return dropThreshold;
    }

    /**
     * See {@link PDFTextStripper#setDropThreshold(float)}
     */
    public void setDropThreshold(Float dropThreshold) {
        this.dropThreshold = dropThreshold;
    }

    public AccessCheckMode getAccessCheckMode() {
        return accessCheckMode;
    }

    public void setAccessCheckMode(AccessCheckMode accessCheckMode) {
        this.accessCheckMode = accessCheckMode;
    }

    /**
     * See {@link #setCatchIntermediateIOExceptions(boolean)}
     *
     * @return whether or not to catch IOExceptions
     */
    public boolean isCatchIntermediateIOExceptions() {
        return catchIntermediateIOExceptions;
    }

    /**
     * The PDFBox parser will throw an IOException if there is
     * a problem with a stream.  If this is set to <code>true</code>,
     * Tika's PDFParser will catch these exceptions and try to parse
     * the rest of the document.  After the parse is completed,
     * Tika's PDFParser will throw the first caught exception.
     *
     * @param catchIntermediateIOExceptions
     */
    public void setCatchIntermediateIOExceptions(boolean catchIntermediateIOExceptions) {
        this.catchIntermediateIOExceptions = catchIntermediateIOExceptions;
    }

    /**
     * @return the OCR configuration
     */
    public OcrConfig getOcr() {
        return ocr;
    }

    /**
     * @param ocr the OCR configuration
     */
    public void setOcr(OcrConfig ocr) {
        this.ocr = ocr;
    }

    /**
     * @return strategy to use for OCR
     */
    public OcrConfig.Strategy getOcrStrategy() {
        return ocr.getStrategy();
    }

    /**
     * @return ocr auto strategy to use when ocr_strategy = Auto
     */
    public OcrConfig.StrategyAuto getOcrStrategyAuto() {
        return ocr.getStrategyAuto();
    }

    /**
     * Which strategy to use for OCR
     */
    public void setOcrStrategy(OcrConfig.Strategy ocrStrategy) {
        ocr.setStrategy(ocrStrategy);
    }

    /**
     * Sets the OCR strategy auto configuration.
     */
    public void setOcrStrategyAuto(OcrConfig.StrategyAuto ocrStrategyAuto) {
        ocr.setStrategyAuto(ocrStrategyAuto);
    }

    public OcrConfig.RenderingStrategy getOcrRenderingStrategy() {
        return ocr.getRenderingStrategy();
    }

    /**
     * When rendering the page for OCR, do you want to include the rendering of the electronic text,
     * ALL, or do you only want to run OCR on the images and vector graphics (NO_TEXT)?
     */
    public void setOcrRenderingStrategy(OcrConfig.RenderingStrategy ocrRenderingStrategy) {
        ocr.setRenderingStrategy(ocrRenderingStrategy);
    }

    /**
     * @return lowercase format name (e.g., "png", "tiff", "jpeg")
     */
    public String getOcrImageFormatName() {
        return ocr.getImageFormatName();
    }

    /**
     * No-op setter for Jackson deserialization compatibility.
     * Use {@link #setOcrImageFormat(OcrConfig.ImageFormat)} instead.
     */
    public void setOcrImageFormatName(String ocrImageFormatName) {
        // Ignored - use setOcrImageFormat instead
    }

    public OcrConfig.ImageFormat getOcrImageFormat() {
        return ocr.getImageFormat();
    }

    public void setOcrImageFormat(OcrConfig.ImageFormat ocrImageFormat) {
        ocr.setImageFormat(ocrImageFormat);
    }

    public OcrConfig.ImageType getOcrImageType() {
        return ocr.getImageType();
    }

    public void setOcrImageType(OcrConfig.ImageType ocrImageType) {
        ocr.setImageType(ocrImageType);
    }

    /**
     * @return dots per inch used to render the page image for OCR
     */
    public int getOcrDPI() {
        return ocr.getDpi();
    }

    /**
     * Dots per inch used to render the page image for OCR.
     */
    public void setOcrDPI(int ocrDPI) {
        ocr.setDpi(ocrDPI);
    }

    /**
     * @return image quality used to render the page image for OCR
     */
    public float getOcrImageQuality() {
        return ocr.getImageQuality();
    }

    /**
     * Image quality used to render the page image for OCR.
     */
    public void setOcrImageQuality(float ocrImageQuality) {
        ocr.setImageQuality(ocrImageQuality);
    }

    /**
     * @return whether or not to extract PDActions
     * @see #setExtractActions(boolean)
     */
    public boolean isExtractActions() {
        return extractActions;
    }

    /**
     * Whether or not to extract PDActions from the file.
     * Most Action types are handled inline; javascript macros
     * are processed as embedded documents.
     *
     * @param v
     */
    public void setExtractActions(boolean v) {
        extractActions = v;
    }

    /**
     * The maximum amount of memory to use when loading a pdf into a PDDocument. Additional
     * buffering is done using a temp file. The default is 512MB.
     *
     * @return
     */
    public long getMaxMainMemoryBytes() {
        return maxMainMemoryBytes;
    }

    public void setMaxMainMemoryBytes(long maxMainMemoryBytes) {
        this.maxMainMemoryBytes = maxMainMemoryBytes;
    }

    public boolean isSetKCMS() {
        return setKCMS;
    }

    /**
     * <p>
     * Whether to call <code>System.setProperty("sun.java2d.cmm",
     * "sun.java2d.cmm.kcms.KcmsServiceProvider")</code>.
     * KCMS is the unmaintained, legacy provider and is far faster than the newer replacement.
     * However, there are stability and security risks with using the unmaintained legacy provider.
     * </p>
     * <p>
     * Note, of course, that this is <b>not</b> thread safe.  If the value is <code>false</code>
     * in your first thread, and the second thread changes this to <code>true</code>,
     * the system property in the first thread will now be <code>true</code>.
     * </p>
     * <p>
     * Default is <code>false</code>.
     * </p>
     *
     * @param setKCMS whether or not to set KCMS
     */
    public void setSetKCMS(boolean setKCMS) {
        this.setKCMS = setKCMS;
    }

    public boolean isDetectAngles() {
        return detectAngles;
    }

    public void setDetectAngles(boolean detectAngles) {
        this.detectAngles = detectAngles;
    }

    public void setImageStrategy(IMAGE_STRATEGY imageStrategy) {
        this.imageStrategy = imageStrategy;
    }

    /**
     * EXPERT: Customize the class that handles inline images within a PDF page.
     *
     * @param imageGraphicsEngineFactory
     */
    public void setImageGraphicsEngineFactory(ImageGraphicsEngineFactory imageGraphicsEngineFactory) {
        this.imageGraphicsEngineFactory = imageGraphicsEngineFactory;
    }

    /**
     * EXPERT: Customize the class that handles inline images within a PDF page.
     * Use this setter when specifying the factory class name in JSON config.
     *
     * @param className fully qualified class name of an ImageGraphicsEngineFactory implementation
     */
    public void setImageGraphicsEngineFactoryClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            this.imageGraphicsEngineFactory = (ImageGraphicsEngineFactory) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate ImageGraphicsEngineFactory: " + className, e);
        }
    }

    public ImageGraphicsEngineFactory getImageGraphicsEngineFactory() {
        return imageGraphicsEngineFactory;
    }

    public IMAGE_STRATEGY getImageStrategy() {
        return imageStrategy;
    }

    public boolean isExtractIncrementalUpdateInfo() {
        return extractIncrementalUpdateInfo;
    }

    public void setExtractIncrementalUpdateInfo(boolean extractIncrementalUpdateInfo) {
        this.extractIncrementalUpdateInfo = extractIncrementalUpdateInfo;
    }

    public boolean isParseIncrementalUpdates() {
        return parseIncrementalUpdates;
    }

    public void setParseIncrementalUpdates(boolean parseIncrementalUpdates) {
        this.parseIncrementalUpdates = parseIncrementalUpdates;
    }

    public int getMaxIncrementalUpdates() {
        return maxIncrementalUpdates;
    }

    /**
     * The maximum number of incremental updates to parse if
     * {@link #setParseIncrementalUpdates(boolean)} is set to <code>true</code>
     *
     * @param maxIncrementalUpdates
     */
    public void setMaxIncrementalUpdates(int maxIncrementalUpdates) {
        this.maxIncrementalUpdates = maxIncrementalUpdates;
    }

    public void setThrowOnEncryptedPayload(boolean throwOnEncryptedPayload) {
        this.throwOnEncryptedPayload = throwOnEncryptedPayload;
    }

    public boolean isThrowOnEncryptedPayload() {
        return throwOnEncryptedPayload;
    }

    public enum IMAGE_STRATEGY {
        NONE,
        /**
         * This is the more modern version of {@link PDFParserConfig#extractInlineImages}
         */
        RAW_IMAGES,
        /**
         * If you want the rendered images, and you don't care that there's
         * markup in the xhtml handler per page then go with this option.
         * For some rendering engines, it is faster to render the full document
         * upfront than to parse a page, render a page, etc.
         */
        RENDER_PAGES_BEFORE_PARSE,
        /**
         * This renders each page, one at a time, at the end of the page.
         * For some rendering engines, this may be slower, but it allows the writing
         * of image metadata into the xhtml in the proper location
         */
        RENDER_PAGES_AT_PAGE_END
        //TODO: add LOGICAL_IMAGES
    }
}
