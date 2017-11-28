package org.apache.tika.parser.pdf;

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

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Locale;
import java.util.Properties;

import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.config.Field;

/**
 * Config for PDFParser.
 * <p/>
 * This allows parameters to be set programmatically:
 * <ol>
 * <li>Calls to PDFParser, i.e. parser.getPDFParserConfig().setEnableAutoSpace() (as before)</li>
 * <li>Constructor of PDFParser</li>
 * <li>Passing to PDFParser through a ParseContext: context.set(PDFParserConfig.class, config);</li>
 * </ol>
 * <p/>
 * Parameters can also be set by modifying the PDFParserConfig.properties file,
 * which lives in the expected places, in trunk:
 * tika-parsers/src/main/resources/org/apache/tika/parser/pdf
 * <p/>
 * Or, in tika-app-x.x.jar or tika-parsers-x.x.jar:
 * org/apache/tika/parser/pdf
 */
public class PDFParserConfig implements Serializable {


    public enum OCR_STRATEGY {
        NO_OCR,
        OCR_ONLY,
        OCR_AND_TEXT_EXTRACTION;

        private static OCR_STRATEGY parse(String s) {
            if (s == null) {
                return NO_OCR;
            } else if ("no_ocr".equals(s.toLowerCase(Locale.ROOT))) {
                return NO_OCR;
            } else if ("ocr_only".equals(s.toLowerCase(Locale.ROOT))) {
                return OCR_ONLY;
            } else if (s.toLowerCase(Locale.ROOT).contains("ocr_and_text")) {
                return OCR_AND_TEXT_EXTRACTION;
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

    private static final long serialVersionUID = 6492570218190936986L;

    // True if we let PDFBox "guess" where spaces should go:
    private boolean enableAutoSpace = true;

    // True if we let PDFBox remove duplicate overlapping text:
    private boolean suppressDuplicateOverlappingText;

    // True if we extract annotation text ourselves
    // (workaround for PDFBOX-1143):
    private boolean extractAnnotationText = true;

    // True if we should sort text tokens by position
    // (necessary for some PDFs, but messes up other PDFs):
    @Field
    private boolean sortByPosition = false;

    //True if acroform content should be extracted
    private boolean extractAcroFormContent = true;

	//True if bookmarks content should be extracted
    private boolean extractBookmarksText = true;

    //True if inline PDXImage objects should be extracted
    private boolean extractInlineImages = false;

    //True if inline images (as identified by their object id within
    //a pdf file) should only be extracted once.
    private boolean extractUniqueInlineImagesOnly = true;

    //The character width-based tolerance value used to estimate where spaces in text should be added
    private Float averageCharTolerance;

    //The space width-based tolerance value used to estimate where spaces in text should be added
    private Float spacingTolerance;

    //If the PDF has an XFA element, process only that and skip extracting
    //content from elsewhere in the document.
    private boolean ifXFAExtractOnlyXFA = false;

    private OCR_STRATEGY ocrStrategy = OCR_STRATEGY.NO_OCR;

    private int ocrDPI = 300;
    private ImageType ocrImageType = ImageType.GRAY;
    private String ocrImageFormatName = "png";
    private float ocrImageQuality = 1.0f;
    private float ocrImageScale = 2.0f;

    private AccessChecker accessChecker;

    //The PDFParser can throw IOExceptions if there is a problem
    //with a streams.  If this is set to true, Tika's
    //parser catches these exceptions, reports them in the metadata
    //and then throws the first stored exception after the parse has completed.
    private boolean catchIntermediateIOExceptions = true;

    private boolean extractActions = false;

    private long maxMainMemoryBytes = -1;

    public PDFParserConfig() {
        init(this.getClass().getResourceAsStream("PDFParser.properties"));
    }

    /**
     * Loads properties from InputStream and then tries to close InputStream.
     * If there is an IOException, this silently swallows the exception
     * and goes back to the default.
     *
     * @param is
     */
    public PDFParserConfig(InputStream is) {
        init(is);
    }

    //initializes object and then tries to close inputstream
    private void init(InputStream is) {

        if (is == null) {
            return;
        }
        Properties props = new Properties();
        try {
            props.load(is);
        } catch (IOException e) {
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    //swallow
                }
            }
        }
        setEnableAutoSpace(
                getBooleanProp(props.getProperty("enableAutoSpace"), getEnableAutoSpace()));
        setSuppressDuplicateOverlappingText(
                getBooleanProp(props.getProperty("suppressDuplicateOverlappingText"),
                        getSuppressDuplicateOverlappingText()));
        setExtractAnnotationText(
                getBooleanProp(props.getProperty("extractAnnotationText"),
                        getExtractAnnotationText()));
        setSortByPosition(
                getBooleanProp(props.getProperty("sortByPosition"),
                        getSortByPosition()));
        setExtractAcroFormContent(
                getBooleanProp(props.getProperty("extractAcroFormContent"),
                        getExtractAcroFormContent()));
		setExtractBookmarksText(
				getBooleanProp(props.getProperty("extractBookmarksText"),
						getExtractBookmarksText()));
        setExtractInlineImages(
                getBooleanProp(props.getProperty("extractInlineImages"),
                        getExtractInlineImages()));
        setExtractUniqueInlineImagesOnly(
                getBooleanProp(props.getProperty("extractUniqueInlineImagesOnly"),
                        getExtractUniqueInlineImagesOnly()));

        setIfXFAExtractOnlyXFA(
            getBooleanProp(props.getProperty("ifXFAExtractOnlyXFA"),
                getIfXFAExtractOnlyXFA()));

        setCatchIntermediateIOExceptions(
                getBooleanProp(props.getProperty("catchIntermediateIOExceptions"),
                isCatchIntermediateIOExceptions()));

        setOcrStrategy(OCR_STRATEGY.parse(props.getProperty("ocrStrategy")));

        setOcrDPI(getIntProp(props.getProperty("ocrDPI"), getOcrDPI()));

        setOcrImageFormatName(props.getProperty("ocrImageFormatName"));

        setOcrImageType(parseImageType(props.getProperty("ocrImageType")));

        setOcrImageScale(getFloatProp(props.getProperty("ocrImageScale"), getOcrImageScale()));

        setExtractActions(getBooleanProp(props.getProperty("extractActions"), false));


        boolean checkExtractAccessPermission = getBooleanProp(props.getProperty("checkExtractAccessPermission"), false);
        boolean allowExtractionForAccessibility = getBooleanProp(props.getProperty("allowExtractionForAccessibility"), true);

        if (checkExtractAccessPermission == false) {
            //silently ignore the crazy configuration of checkExtractAccessPermission = false,
            //but allowExtractionForAccessibility=false
            accessChecker = new AccessChecker();
        } else {
            accessChecker = new AccessChecker(allowExtractionForAccessibility);
        }

        maxMainMemoryBytes = getIntProp(props.getProperty("maxMainMemoryBytes"), -1);
    }

    /**
     * Configures the given pdf2XHTML.
     *
     * @param pdf2XHTML
     */
    public void configure(PDF2XHTML pdf2XHTML) {
        pdf2XHTML.setSortByPosition(getSortByPosition());
        if (getEnableAutoSpace()) {
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
        pdf2XHTML.setSuppressDuplicateOverlappingText(getSuppressDuplicateOverlappingText());
    }

    /**
     * @see #setExtractAcroFormContent(boolean)
     */
    public boolean getExtractAcroFormContent() {
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
     * @see #setIfXFAExtractOnlyXFA(boolean)
     * @return how to handle XFA data if it exists
     */
    public boolean getIfXFAExtractOnlyXFA() {
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
	public boolean getExtractBookmarksText() {
		return extractBookmarksText;
	}

	/**
	 * If true, extract bookmarks (document outline) text.
	 * <p/>
	 * Te default is <code>true</code>
	 * @param extractBookmarksText
	 */
	public void setExtractBookmarksText(boolean extractBookmarksText) {
		this.extractBookmarksText = extractBookmarksText;
	}

	/**
     * @see #setExtractInlineImages(boolean)
     */
    public boolean getExtractInlineImages() {
        return extractInlineImages;
    }

    /**
     * If true, extract inline embedded OBXImages.
     * <b>Beware:</b> some PDF documents of modest size (~4MB) can contain
     * thousands of embedded images totaling > 2.5 GB.  Also, at least as of PDFBox 1.8.5,
     * there can be surprisingly large memory consumption and/or out of memory errors.
     * Set to <code>true</code> with caution.
     * <p/>
     * The default is <code>false</code>.
     * <p/>
     * See also: {@see #setExtractUniqueInlineImagesOnly(boolean)};
     *
     * @param extractInlineImages
     */
    public void setExtractInlineImages(boolean extractInlineImages) {
        this.extractInlineImages = extractInlineImages;
    }

    /**
     * @see #setExtractUniqueInlineImagesOnly(boolean)
     */
    public boolean getExtractUniqueInlineImagesOnly() {
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
    public boolean getEnableAutoSpace() {
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
    public boolean getSuppressDuplicateOverlappingText() {
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
    public void setSuppressDuplicateOverlappingText(
            boolean suppressDuplicateOverlappingText) {
        this.suppressDuplicateOverlappingText = suppressDuplicateOverlappingText;
    }

    /**
     * @see #setExtractAnnotationText(boolean)
     */
    public boolean getExtractAnnotationText() {
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
    public boolean getSortByPosition() {
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

    public AccessChecker getAccessChecker() {
        return accessChecker;
    }

    public void setAccessChecker(AccessChecker accessChecker) {
        this.accessChecker = accessChecker;
    }

    /**
     * See {@link #setCatchIntermediateIOExceptions(boolean)}
     * @return whether or not to catch IOExceptions
     * @deprecated use {@link #getCatchIntermediateIOExceptions()}
     */
    public boolean isCatchIntermediateIOExceptions() {
        return catchIntermediateIOExceptions;
    }

    /**
     * See {@link #setCatchIntermediateIOExceptions(boolean)}
     * @return whether or not to catch IOExceptions
     */
    public boolean getCatchIntermediateIOExceptions() {
        return catchIntermediateIOExceptions;
    }
    /**
     * The PDFBox parser will throw an IOException if there is
     * a problem with a stream.  If this is set to <code>true</code>,
     * Tika's PDFParser will catch these exceptions and try to parse
     * the rest of the document.  After the parse is completed,
     * Tika's PDFParser will throw the first caught exception.
     * @param catchIntermediateIOExceptions
     */
    public void setCatchIntermediateIOExceptions(boolean catchIntermediateIOExceptions) {
        this.catchIntermediateIOExceptions = catchIntermediateIOExceptions;
    }

    /**
     * Which strategy to use for OCR
     * @param ocrStrategy
     */
    public void setOcrStrategy(OCR_STRATEGY ocrStrategy) {
        this.ocrStrategy = ocrStrategy;
    }

    /**
     * Which strategy to use for OCR
     * @param ocrStrategyString
     */
    public void setOcrStrategy(String ocrStrategyString) {
        this.ocrStrategy = OCR_STRATEGY.parse(ocrStrategyString);
    }
    /**
     *
     * @return strategy to use for OCR
     */
    public OCR_STRATEGY getOcrStrategy() {
        return ocrStrategy;
    }

    private boolean getBooleanProp(String p, boolean defaultMissing) {
        if (p == null) {
            return defaultMissing;
        }
        if (p.toLowerCase(Locale.ROOT).equals("true")) {
            return true;
        } else if (p.toLowerCase(Locale.ROOT).equals("false")) {
            return false;
        } else {
            return defaultMissing;
        }
    }
    //throws NumberFormatException if there's a non-null unparseable
    //string passed in
    private int getIntProp(String p, int defaultMissing) {
        if (p == null) {
            return defaultMissing;
        }

        return Integer.parseInt(p);
    }

    //throws NumberFormatException if there's a non-null unparseable
    //string passed in
    private long getLongProp(String p, long defaultMissing) {
        if (p == null) {
            return defaultMissing;
        }

        return Long.parseLong(p);
    }

    //throws NumberFormatException if there's a non-null unparseable
    //string passed in
    private static float getFloatProp(String p, float defaultMissing) {
        if (p == null) {
            return defaultMissing;
        }

        return Float.parseFloat(p);
    }
    /**
     * String representation of the image format used to render
     * the page image for OCR (examples: png, tiff, jpeg)
     * @return
     */
    public String getOcrImageFormatName() {
        return ocrImageFormatName;
    }

    /**
     * @see #getOcrImageFormatName()
     *
     * @param ocrImageFormatName name of image format used to render
     *                           page image
     */
    public void setOcrImageFormatName(String ocrImageFormatName) {
        this.ocrImageFormatName = ocrImageFormatName;
    }

    /**
     * Image type used to render the page image for OCR.
     * @see #setOcrImageType(ImageType)
     * @return image type
     */
    public ImageType getOcrImageType() {
        return ocrImageType;
    }

    /**
     * Image type used to render the page image for OCR.
     * @param ocrImageType
     */
    public void setOcrImageType(ImageType ocrImageType) {
        this.ocrImageType = ocrImageType;
    }

    /**
     * Image type used to render the page image for OCR.
     * @see #setOcrImageType(ImageType)
    */
    public void setOcrImageType(String ocrImageTypeString) {
        this.ocrImageType = parseImageType(ocrImageTypeString);
    }

    /**
     * Dots per inch used to render the page image for OCR
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
    }

    /**
     * Image quality used to render the page image for OCR.
     * This does not apply to all image formats
     * @return
     */
    public float getOcrImageQuality() {
        return ocrImageQuality;
    }

    /**
     * Image quality used to render the page image for OCR.
     * This does not apply to all image formats
     * @return
     */
    public void setOcrImageQuality(float ocrImageQuality) {
        this.ocrImageQuality = ocrImageQuality;
    }

    /**
     * Scale to use if rendering a page and then running OCR on that rendered image.
     * Default is 2.0f.
     * @return
     */
    public float getOcrImageScale() {
        return ocrImageScale;
    }

    public void setOcrImageScale(float ocrImageScale) {
        this.ocrImageScale = ocrImageScale;
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
     * @see #setExtractActions(boolean)
     * @return whether or not to extract PDActions
     */
    public boolean getExtractActions() {
        return extractActions;
    }


    /**
     * The maximum amount of memory to use when loading a pdf into a PDDocument. Additional buffering is done using a
     * temp file.
     * @return
     */
    public long getMaxMainMemoryBytes() {
        return maxMainMemoryBytes;
    }

    public void setMaxMainMemoryBytes(int maxMainMemoryBytes) {
        this.maxMainMemoryBytes = maxMainMemoryBytes;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PDFParserConfig)) return false;

        PDFParserConfig config = (PDFParserConfig) o;

        if (getEnableAutoSpace() != config.getEnableAutoSpace()) return false;
        if (getSuppressDuplicateOverlappingText() != config.getSuppressDuplicateOverlappingText()) return false;
        if (getExtractAnnotationText() != config.getExtractAnnotationText()) return false;
        if (getSortByPosition() != config.getSortByPosition()) return false;
        if (getExtractAcroFormContent() != config.getExtractAcroFormContent()) return false;
		if (getExtractBookmarksText() != config.getExtractBookmarksText()) return false;
        if (getExtractInlineImages() != config.getExtractInlineImages()) return false;
        if (getExtractUniqueInlineImagesOnly() != config.getExtractUniqueInlineImagesOnly()) return false;
        if (getIfXFAExtractOnlyXFA() != config.getIfXFAExtractOnlyXFA()) return false;
        if (getOcrDPI() != config.getOcrDPI()) return false;
        if (isCatchIntermediateIOExceptions() != config.isCatchIntermediateIOExceptions()) return false;
        if (!getAverageCharTolerance().equals(config.getAverageCharTolerance())) return false;
        if (!getSpacingTolerance().equals(config.getSpacingTolerance())) return false;
        if (!getOcrStrategy().equals(config.getOcrStrategy())) return false;
        if (getOcrImageType() != config.getOcrImageType()) return false;
        if (!getOcrImageFormatName().equals(config.getOcrImageFormatName())) return false;
        if (getExtractActions() != config.getExtractActions()) return false;
        if (!getAccessChecker().equals(config.getAccessChecker())) return false;
        return getMaxMainMemoryBytes() == config.getMaxMainMemoryBytes();
    }

    @Override
    public int hashCode() {
        int result = (getEnableAutoSpace() ? 1 : 0);
        result = 31 * result + (getSuppressDuplicateOverlappingText() ? 1 : 0);
        result = 31 * result + (getExtractAnnotationText() ? 1 : 0);
        result = 31 * result + (getSortByPosition() ? 1 : 0);
        result = 31 * result + (getExtractAcroFormContent() ? 1 : 0);
		result = 31 * result + (getExtractBookmarksText() ? 1 : 0);
        result = 31 * result + (getExtractInlineImages() ? 1 : 0);
        result = 31 * result + (getExtractUniqueInlineImagesOnly() ? 1 : 0);
        result = 31 * result + getAverageCharTolerance().hashCode();
        result = 31 * result + getSpacingTolerance().hashCode();
        result = 31 * result + (getIfXFAExtractOnlyXFA() ? 1 : 0);
        result = 31 * result + ocrStrategy.hashCode();
        result = 31 * result + getOcrDPI();
        result = 31 * result + getOcrImageType().hashCode();
        result = 31 * result + getOcrImageFormatName().hashCode();
        result = 31 * result + getAccessChecker().hashCode();
        result = 31 * result + (isCatchIntermediateIOExceptions() ? 1 : 0);
        result = 31 * result + (getExtractActions() ? 1 : 0);
        result = 31 * result + Long.valueOf(getMaxMainMemoryBytes()).hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "PDFParserConfig{" +
                "enableAutoSpace=" + enableAutoSpace +
                ", suppressDuplicateOverlappingText=" + suppressDuplicateOverlappingText +
                ", extractAnnotationText=" + extractAnnotationText +
                ", sortByPosition=" + sortByPosition +
                ", extractAcroFormContent=" + extractAcroFormContent +
				", extractBookmarksText=" + extractBookmarksText +
                ", extractInlineImages=" + extractInlineImages +
                ", extractUniqueInlineImagesOnly=" + extractUniqueInlineImagesOnly +
                ", averageCharTolerance=" + averageCharTolerance +
                ", spacingTolerance=" + spacingTolerance +
                ", ifXFAExtractOnlyXFA=" + ifXFAExtractOnlyXFA +
                ", ocrStrategy=" + ocrStrategy +
                ", ocrDPI=" + ocrDPI +
                ", ocrImageType=" + ocrImageType +
                ", ocrImageFormatName='" + ocrImageFormatName + '\'' +
                ", accessChecker=" + accessChecker +
                ", extractActions=" + extractActions +
                ", catchIntermediateIOExceptions=" + catchIntermediateIOExceptions +
                ", maxMainMemoryBytes=" + maxMainMemoryBytes +
                '}';
    }
}
