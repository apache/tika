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
import java.util.Set;

import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.text.PDFTextStripper;

import org.apache.tika.exception.TikaException;

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

    private int ocrDPI = 300;
    private ImageType ocrImageType = ImageType.GRAY;
    private String ocrImageFormatName = "png";
    private float ocrImageQuality = 1.0f;

    private AccessChecker accessChecker = new AccessChecker();

    //The PDFParser can throw IOExceptions if there is a problem
    //with a streams.  If this is set to true, Tika's
    //parser catches these exceptions, reports them in the metadata
    //and then throws the first stored exception after the parse has completed.
    private boolean catchIntermediateIOExceptions = true;

    private boolean extractActions = false;

    private boolean extractFontNames = false;

    private long maxMainMemoryBytes = -1;

    private boolean setKCMS = false;

    private boolean detectAngles = false;

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
     * If true, extract inline embedded OBXImages.
     * <b>Beware:</b> some PDF documents of modest size (~4MB) can contain
     * thousands of embedded images totaling &gt; 2.5 GB.  Also, at least as of PDFBox 1.8.5,
     * there can be surprisingly large memory consumption and/or out of memory errors.
     * Set to <code>true</code> with caution.
     * <p/>
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
     * Which strategy to use for OCR
     *
     * @param ocrStrategy
     */
    public void setOcrStrategy(OCR_STRATEGY ocrStrategy) {
        this.ocrStrategy = ocrStrategy;
        userConfigured.add("ocrStrategy");
    }

    /**
     * Which strategy to use for OCR
     *
     * @param ocrStrategyString
     */
    public void setOcrStrategy(String ocrStrategyString) {
        setOcrStrategy(OCR_STRATEGY.parse(ocrStrategyString));
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
     * buffering is done using a temp file.
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
        if (!(o instanceof PDFParserConfig)) {
            return false;
        }

        PDFParserConfig config = (PDFParserConfig) o;

        if (isEnableAutoSpace() != config.isEnableAutoSpace()) {
            return false;
        }
        if (isSuppressDuplicateOverlappingText() != config.isSuppressDuplicateOverlappingText()) {
            return false;
        }
        if (isExtractAnnotationText() != config.isExtractAnnotationText()) {
            return false;
        }
        if (isSortByPosition() != config.isSortByPosition()) {
            return false;
        }
        if (isExtractAcroFormContent() != config.isExtractAcroFormContent()) {
            return false;
        }
        if (isExtractBookmarksText() != config.isExtractBookmarksText()) {
            return false;
        }
        if (isExtractInlineImages() != config.isExtractInlineImages()) {
            return false;
        }
        if (isExtractUniqueInlineImagesOnly() != config.isExtractUniqueInlineImagesOnly()) {
            return false;
        }
        if (isIfXFAExtractOnlyXFA() != config.isIfXFAExtractOnlyXFA()) {
            return false;
        }
        if (getOcrDPI() != config.getOcrDPI()) {
            return false;
        }
        if (isCatchIntermediateIOExceptions() != config.isCatchIntermediateIOExceptions()) {
            return false;
        }
        if (!getAverageCharTolerance().equals(config.getAverageCharTolerance())) {
            return false;
        }
        if (!getSpacingTolerance().equals(config.getSpacingTolerance())) {
            return false;
        }
        if (!getDropThreshold().equals(config.getDropThreshold())) {
            return false;
        }
        if (!getOcrStrategy().equals(config.getOcrStrategy())) {
            return false;
        }
        if (getOcrImageType() != config.getOcrImageType()) {
            return false;
        }
        if (!getOcrImageFormatName().equals(config.getOcrImageFormatName())) {
            return false;
        }
        if (isExtractActions() != config.isExtractActions()) {
            return false;
        }
        if (!getAccessChecker().equals(config.getAccessChecker())) {
            return false;
        }
        return getMaxMainMemoryBytes() == config.getMaxMainMemoryBytes();
    }

    @Override
    public int hashCode() {
        int result = (isEnableAutoSpace() ? 1 : 0);
        result = 31 * result + (isSuppressDuplicateOverlappingText() ? 1 : 0);
        result = 31 * result + (isExtractAnnotationText() ? 1 : 0);
        result = 31 * result + (isSortByPosition() ? 1 : 0);
        result = 31 * result + (isExtractAcroFormContent() ? 1 : 0);
        result = 31 * result + (isExtractBookmarksText() ? 1 : 0);
        result = 31 * result + (isExtractInlineImages() ? 1 : 0);
        result = 31 * result + (isExtractUniqueInlineImagesOnly() ? 1 : 0);
        result = 31 * result + getAverageCharTolerance().hashCode();
        result = 31 * result + getSpacingTolerance().hashCode();
        result = 31 * result + getDropThreshold().hashCode();
        result = 31 * result + (isIfXFAExtractOnlyXFA() ? 1 : 0);
        result = 31 * result + ocrStrategy.hashCode();
        result = 31 * result + getOcrDPI();
        result = 31 * result + getOcrImageType().hashCode();
        result = 31 * result + getOcrImageFormatName().hashCode();
        result = 31 * result + getAccessChecker().hashCode();
        result = 31 * result + (isCatchIntermediateIOExceptions() ? 1 : 0);
        result = 31 * result + (isExtractActions() ? 1 : 0);
        result = 31 * result + Long.valueOf(getMaxMainMemoryBytes()).hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "PDFParserConfig{" + "enableAutoSpace=" + enableAutoSpace +
                ", suppressDuplicateOverlappingText=" + suppressDuplicateOverlappingText +
                ", extractAnnotationText=" + extractAnnotationText + ", sortByPosition=" +
                sortByPosition + ", extractAcroFormContent=" + extractAcroFormContent +
                ", extractBookmarksText=" + extractBookmarksText + ", extractInlineImages=" +
                extractInlineImages + ", extractUniqueInlineImagesOnly=" +
                extractUniqueInlineImagesOnly + ", averageCharTolerance=" + averageCharTolerance +
                ", spacingTolerance=" + spacingTolerance + ", dropThreshold=" + dropThreshold +
                ", ifXFAExtractOnlyXFA=" + ifXFAExtractOnlyXFA + ", ocrStrategy=" + ocrStrategy +
                ", ocrDPI=" + ocrDPI + ", ocrImageType=" + ocrImageType + ", ocrImageFormatName='" +
                ocrImageFormatName + '\'' + ", accessChecker=" + accessChecker +
                ", extractActions=" + extractActions + ", catchIntermediateIOExceptions=" +
                catchIntermediateIOExceptions + ", maxMainMemoryBytes=" + maxMainMemoryBytes + '}';
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
}
