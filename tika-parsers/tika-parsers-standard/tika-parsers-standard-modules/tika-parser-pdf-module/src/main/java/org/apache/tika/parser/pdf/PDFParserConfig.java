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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.text.PDFTextStripper;

import org.apache.tika.exception.TikaException;
import org.apache.tika.renderer.Renderer;

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
    private final Set<String> userConfigured = new HashSet<>();
    // True if we let PDFBox "guess" where spaces should go:
    private boolean enableAutoSpace = true;

    // True if we let PDFBox remove duplicate overlapping text:
    private boolean suppressDuplicateOverlappingText = false;

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

    private OCR_STRATEGY ocrStrategy = OCR_STRATEGY.AUTO;

    // If OCR_Strategy=AUTO, then this controls the algorithm used
    private static final OCRStrategyAuto OCR_STRATEGY_AUTO_BETTER = new OCRStrategyAuto(10, 10);
    private static final OCRStrategyAuto OCR_STRATEGY_AUTO_FASTER = new OCRStrategyAuto(.1f, 10);
    private static final int OCR_STRATEGY_AUTO_DEFAULT_CHARS_PER_PAGE = 10;

    private OCRStrategyAuto ocrStrategyAuto = OCR_STRATEGY_AUTO_BETTER;

    private OCR_RENDERING_STRATEGY ocrRenderingStrategy = OCR_RENDERING_STRATEGY.ALL;

    private int ocrDPI = 300;
    private ImageType ocrImageType = ImageType.GRAY;
    private String ocrImageFormatName = "png";
    private float ocrImageQuality = 1.0f;

    /**
     * Should the entire document be rendered?
     */
    private IMAGE_STRATEGY imageStrategy = IMAGE_STRATEGY.NONE;
    private AccessChecker accessChecker = new AccessChecker();

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

    private Renderer renderer;

    /**
     * @return whether or not to extract only inline image metadata and not render the images
     */
    boolean isExtractInlineImageMetadataOnly() {
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
    void setExtractInlineImageMetadataOnly(boolean extractInlineImageMetadataOnly) {
        this.extractInlineImageMetadataOnly = extractInlineImageMetadataOnly;
        userConfigured.add("extractInlineImageMetadataOnly");
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
        userConfigured.add("extractMarkedContent");
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
        userConfigured.add("extractAcroFormContent");
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
        userConfigured.add("ifXFAExtractOnlyXFA");
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
        userConfigured.add("extractBookmarksText");
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
        userConfigured.add("extractFontNames");
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
        userConfigured.add("extractInlineImages");
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
        userConfigured.add("extractUniqueInlineImagesOnly");
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
        userConfigured.add("enableAutoSpace");
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
        userConfigured.add("suppressDuplicateOverlappingText");
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
        userConfigured.add("extractAnnotationText");
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
        userConfigured.add("sortByPosition");
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
        userConfigured.add("averageCharTolerance");
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
        userConfigured.add("spacingTolerance");
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
        userConfigured.add("dropThreshold");
    }

    public AccessChecker getAccessChecker() {
        return accessChecker;
    }

    public void setAccessChecker(AccessChecker accessChecker) {
        this.accessChecker = accessChecker;
        userConfigured.add("accessChecker");
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
        userConfigured.add("catchIntermediateIOExceptions");
    }

    /**
     * @return strategy to use for OCR
     */
    public OCR_STRATEGY getOcrStrategy() {
        return ocrStrategy;
    }

    /**
     * @return ocr auto strategy to use when ocr_strategy = Auto
     */
    public OCRStrategyAuto getOcrStrategyAuto() {
        return ocrStrategyAuto;
    }

    /**
     * Which strategy to use for OCR
     *
     * @param ocrStrategy
     */
    public void setOcrStrategy(OCR_STRATEGY ocrStrategy) {
        this.ocrStrategy = ocrStrategy;
        userConfigured.add("ocrStrategy");
    }


    public void setOcrStrategyAuto(String ocrStrategyAuto) {
        final String regex = "^\\s*(faster|better)|(\\d{1,3})(%)?(?:,\\s*(\\d{1,3}))?\\s*$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(ocrStrategyAuto);
        if (matcher.matches()) {
            final String group1 = matcher.group(1);

            if ("better".equals(group1)) {
                this.ocrStrategyAuto = OCR_STRATEGY_AUTO_BETTER;
            } else if ("faster".equals(group1)) {
                this.ocrStrategyAuto = OCR_STRATEGY_AUTO_FASTER;
            } else {
                float unmappedUnicodeCharsPerPage = Integer.parseInt(matcher.group(2));
                if (matcher.group(3) != null) {
                    // If we have the percent sign, then convert
                    if (unmappedUnicodeCharsPerPage > 100.0) {
                        throw new IllegalArgumentException
                        ("Error parsing OCRStrategyAuto - Percent cannot exceed 100%");
                    }
                    unmappedUnicodeCharsPerPage = unmappedUnicodeCharsPerPage / 100f;
                }
                // The 2nd number is optional.  Default to 10 chars per page
                int totalCharsPerPage = matcher.group(4) == null
                        ? OCR_STRATEGY_AUTO_DEFAULT_CHARS_PER_PAGE
                        : Integer.parseInt(matcher.group(4));
                this.ocrStrategyAuto = new OCRStrategyAuto(unmappedUnicodeCharsPerPage, totalCharsPerPage);
            }
            userConfigured.add("ocrStrategyAuto");

        } else {
            throw new IllegalArgumentException("Error parsing OCRStrategyAuto - Must be in the form 'num[%], num'");
        }
    }

    /**
     * Which strategy to use for OCR
     *
     * @param ocrStrategyString
     */
    public void setOcrStrategy(String ocrStrategyString) {
        setOcrStrategy(OCR_STRATEGY.parse(ocrStrategyString));
    }

    public OCR_RENDERING_STRATEGY getOcrRenderingStrategy() {
        return ocrRenderingStrategy;
    }

    public void setOcrRenderingStrategy(String ocrRenderingStrategyString) {
        setOcrRenderingStrategy(OCR_RENDERING_STRATEGY.parse(ocrRenderingStrategyString));
    }

    /**
     * When rendering the page for OCR, do you want to include the rendering of the electronic text,
     * ALL, or do you only want to run OCR on the images and vector graphics (NO_TEXT)?
     *
     * @param ocrRenderingStrategy
     */
    public void setOcrRenderingStrategy(OCR_RENDERING_STRATEGY ocrRenderingStrategy) {
        this.ocrRenderingStrategy = ocrRenderingStrategy;
        userConfigured.add("ocrRenderingStrategy");
    }

    /**
     * String representation of the image format used to render
     * the page image for OCR (examples: png, tiff, jpeg)
     *
     * @return
     */
    public String getOcrImageFormatName() {
        return ocrImageFormatName;
    }

    /**
     * @param ocrImageFormatName name of image format used to render
     *                           page image
     * @see #getOcrImageFormatName()
     */
    public void setOcrImageFormatName(String ocrImageFormatName) {
        if (!ocrImageFormatName.equals("png") && !ocrImageFormatName.equals("tiff") &&
                !ocrImageFormatName.equals("jpeg")) {
            throw new IllegalArgumentException(
                    "Available options: png, tiff, jpeg. " + "I'm sorry, but I don't recognize: " +
                            ocrImageFormatName);
        }
        this.ocrImageFormatName = ocrImageFormatName;
        userConfigured.add("ocrImageFormatName");
    }

    /**
     * Image type used to render the page image for OCR.
     *
     * @return image type
     * @see #setOcrImageType(ImageType)
     */
    public ImageType getOcrImageType() {
        return ocrImageType;
    }

    /**
     * Image type used to render the page image for OCR.
     *
     * @param ocrImageType
     */
    public void setOcrImageType(ImageType ocrImageType) {
        this.ocrImageType = ocrImageType;
        userConfigured.add("ocrImageType");
    }

    /**
     * Image type used to render the page image for OCR.
     *
     * @see #setOcrImageType(ImageType)
     */
    public void setOcrImageType(String ocrImageTypeString) {
        this.ocrImageType = parseImageType(ocrImageTypeString);
    }

    /**
     * Dots per inch used to render the page image for OCR
     *
     * @return dots per inch
     */
    public int getOcrDPI() {
        return ocrDPI;
    }

    /**
     * Dots per inch used to render the page image for OCR.
     * This does not apply to all image formats.
     *
     * @param ocrDPI
     */
    public void setOcrDPI(int ocrDPI) {
        this.ocrDPI = ocrDPI;
        userConfigured.add("ocrDPI");
    }

    /**
     * Image quality used to render the page image for OCR.
     * This does not apply to all image formats
     *
     * @return
     */
    public float getOcrImageQuality() {
        return ocrImageQuality;
    }

    /**
     * Image quality used to render the page image for OCR.
     * This does not apply to all image formats
     */
    public void setOcrImageQuality(float ocrImageQuality) {
        this.ocrImageQuality = ocrImageQuality;
        userConfigured.add("ocrImageQuality");
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
        userConfigured.add("extractActions");
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
        userConfigured.add("maxMainMemoryBytes");
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
        userConfigured.add("setKCMS");
    }

    private ImageType parseImageType(String ocrImageType) {
        for (ImageType t : ImageType.values()) {
            if (ocrImageType.equalsIgnoreCase(t.toString())) {
                return t;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("I regret that I could not parse '");
        sb.append(ocrImageType);
        sb.append("'. I'm only familiar with: ");
        int i = 0;
        for (ImageType t : ImageType.values()) {
            if (i++ == 0) {
                sb.append(", ");
            }
            sb.append(t.toString());
        }
        throw new IllegalArgumentException(sb.toString());
    }

    public boolean isDetectAngles() {
        return detectAngles;
    }

    public void setDetectAngles(boolean detectAngles) {
        this.detectAngles = detectAngles;
        userConfigured.add("detectAngles");
    }

    public PDFParserConfig cloneAndUpdate(PDFParserConfig updates) throws TikaException {
        PDFParserConfig updated = new PDFParserConfig();
        for (Field field : this.getClass().getDeclaredFields()) {
            if (Modifier.isFinal(field.getModifiers())) {
                continue;
            } else if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if ("userConfigured".equals(field.getName())) {
                continue;
            }
            if (updates.userConfigured.contains(field.getName())) {
                try {
                    field.set(updated, field.get(updates));
                } catch (IllegalAccessException e) {
                    throw new TikaException("can't update " + field.getName(), e);
                }
            } else {
                try {
                    field.set(updated, field.get(this));
                } catch (IllegalAccessException e) {
                    throw new TikaException("can't update " + field.getName(), e);
                }
            }
        }
        return updated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PDFParserConfig config = (PDFParserConfig) o;
        return enableAutoSpace == config.enableAutoSpace &&
                suppressDuplicateOverlappingText == config.suppressDuplicateOverlappingText &&
                extractAnnotationText == config.extractAnnotationText &&
                sortByPosition == config.sortByPosition &&
                extractAcroFormContent == config.extractAcroFormContent &&
                extractBookmarksText == config.extractBookmarksText &&
                extractInlineImages == config.extractInlineImages &&
                extractInlineImageMetadataOnly == config.extractInlineImageMetadataOnly &&
                extractUniqueInlineImagesOnly == config.extractUniqueInlineImagesOnly &&
                extractMarkedContent == config.extractMarkedContent &&
                Float.compare(config.dropThreshold, dropThreshold) == 0 &&
                ifXFAExtractOnlyXFA == config.ifXFAExtractOnlyXFA && ocrDPI == config.ocrDPI &&
                Float.compare(config.ocrImageQuality, ocrImageQuality) == 0 &&
                catchIntermediateIOExceptions == config.catchIntermediateIOExceptions &&
                extractActions == config.extractActions &&
                extractFontNames == config.extractFontNames &&
                maxMainMemoryBytes == config.maxMainMemoryBytes && setKCMS == config.setKCMS &&
                detectAngles == config.detectAngles &&
                Objects.equals(userConfigured, config.userConfigured) &&
                Objects.equals(averageCharTolerance, config.averageCharTolerance) &&
                Objects.equals(spacingTolerance, config.spacingTolerance) &&
                ocrStrategy == config.ocrStrategy &&
                Objects.equals(ocrStrategyAuto, config.ocrStrategyAuto) &&
                ocrRenderingStrategy == config.ocrRenderingStrategy &&
                ocrImageType == config.ocrImageType &&
                Objects.equals(ocrImageFormatName, config.ocrImageFormatName) &&
                imageStrategy == config.imageStrategy &&
                Objects.equals(accessChecker, config.accessChecker) &&
                Objects.equals(renderer, config.renderer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userConfigured, enableAutoSpace, suppressDuplicateOverlappingText,
                extractAnnotationText, sortByPosition, extractAcroFormContent, extractBookmarksText,
                extractInlineImages, extractInlineImageMetadataOnly, extractUniqueInlineImagesOnly,
                extractMarkedContent, averageCharTolerance, spacingTolerance, dropThreshold,
                ifXFAExtractOnlyXFA, ocrStrategy, ocrStrategyAuto, ocrRenderingStrategy, ocrDPI,
                ocrImageType, ocrImageFormatName, ocrImageQuality, imageStrategy, accessChecker,
                catchIntermediateIOExceptions, extractActions, extractFontNames, maxMainMemoryBytes,
                setKCMS, detectAngles, renderer);
    }

    public void setRenderer(Renderer renderer) {
        this.renderer = renderer;
    }

    public Renderer getRenderer() {
        return renderer;
    }

    public void setImageStrategy(String imageStrategy) {
        setImageStrategy(PDFParserConfig.IMAGE_STRATEGY.parse(imageStrategy));
    }

    public void setImageStrategy(IMAGE_STRATEGY imageStrategy) {
        this.imageStrategy = imageStrategy;
        userConfigured.add("imageStrategy");
    }

    public IMAGE_STRATEGY getImageStrategy() {
        return imageStrategy;
    }



    public enum OCR_STRATEGY {
        AUTO, NO_OCR, OCR_ONLY, OCR_AND_TEXT_EXTRACTION;

        private static OCR_STRATEGY parse(String s) {
            if (s == null) {
                return NO_OCR;
            } else if ("no_ocr".equals(s.toLowerCase(Locale.ROOT))) {
                return NO_OCR;
            } else if ("ocr_only".equals(s.toLowerCase(Locale.ROOT))) {
                return OCR_ONLY;
            } else if (s.toLowerCase(Locale.ROOT).contains("ocr_and_text")) {
                return OCR_AND_TEXT_EXTRACTION;
            } else if ("auto".equals(s.toLowerCase(Locale.ROOT))) {
                return AUTO;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("I regret that I don't recognize '").append(s);
            sb.append("' as an OCR_STRATEGY. I only recognize:");
            int i = 0;
            for (OCR_STRATEGY strategy : OCR_STRATEGY.values()) {
                if (i++ > 0) {
                    sb.append(", ");
                }
                sb.append(strategy.toString());

            }
            throw new IllegalArgumentException(sb.toString());
        }
    }

    /**
     * Encapsulate the numbers used to control OCR Strategy when set to auto
     * <p>
     * If the total characters on the page < this.totalCharsPerPage
     * or
     * total unmapped unicode characters on the page > this.unmappedUnicodeCharsPerPage
     * then we will perform OCR on the page
     * <p>
     * If unamppedUnicodeCharsPerPage is an integer > 0, then we compare absolute number of characters.
     * If it is a float < 1, then we assume it is a percentage and we compare it to the
     * percentage of unmappedCharactersPerPage/totalCharsPerPage
     */
    public static class OCRStrategyAuto implements Serializable {
        private final float unmappedUnicodeCharsPerPage;
        private final int totalCharsPerPage;

        public OCRStrategyAuto(float unmappedUnicodeCharsPerPage, int totalCharsPerPage) {
            this.totalCharsPerPage = totalCharsPerPage;
            this.unmappedUnicodeCharsPerPage = unmappedUnicodeCharsPerPage;
        }

        public float getUnmappedUnicodeCharsPerPage() {
            return unmappedUnicodeCharsPerPage;
        }

        public int getTotalCharsPerPage() {
            return totalCharsPerPage;
        }
    }

    public enum OCR_RENDERING_STRATEGY {

        NO_TEXT, //includes vector graphics and image
        TEXT_ONLY, //renders only glyphs
        VECTOR_GRAPHICS_ONLY, //renders only vector graphics
        ALL;
        //TODO: add AUTO?

        private static OCR_RENDERING_STRATEGY parse(String s) {
            if (s == null) {
                return ALL;
            }
            String lc = s.toLowerCase(Locale.US);
            switch (lc) {
                case "vector_graphics_only":
                    return VECTOR_GRAPHICS_ONLY;
                case "text_only":
                    return TEXT_ONLY;
                case "no_text":
                    return NO_TEXT;
                case "all":
                    return ALL;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("I regret that I don't recognize '").append(s);
            sb.append("' as an OCR_STRATEGY. I only recognize:");
            int i = 0;
            for (OCR_RENDERING_STRATEGY strategy : OCR_RENDERING_STRATEGY.values()) {
                if (i++ > 0) {
                    sb.append(", ");
                }
                sb.append(strategy.toString());

            }
            throw new IllegalArgumentException(sb.toString());
        }
    }

    public enum IMAGE_STRATEGY {
        NONE, RAW_IMAGES, RENDERED_PAGES;//TODO: add LOGICAL_IMAGES

        private static IMAGE_STRATEGY parse(String s) {
            String lc = s.toLowerCase(Locale.US);
            switch (lc) {
                case "rawImages" :
                    return RAW_IMAGES;
                case "renderedPages":
                    return RENDERED_PAGES;
                case "none":
                    return NONE;
                default:
                    //fall through to exception
                    break;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("I regret that I don't recognize '").append(s);
            sb.append("' as an IMAGE_STRATEGY. I only recognize:");
            int i = 0;
            for (IMAGE_STRATEGY strategy : IMAGE_STRATEGY.values()) {
                if (i++ > 0) {
                    sb.append(", ");
                }
                sb.append(strategy.toString());
            }
            throw new IllegalArgumentException(sb.toString());
        }
    }
}
