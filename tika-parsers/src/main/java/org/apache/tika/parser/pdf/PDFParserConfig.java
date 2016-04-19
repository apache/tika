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

import org.apache.pdfbox.text.PDFTextStripper;

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
    private boolean sortByPosition = false;

    //True if acroform content should be extracted
    private boolean extractAcroFormContent = true;

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

    private AccessChecker accessChecker;

    //The PDFParser can throw IOExceptions if there is a problem
    //with a streams.  If this is set to true, Tika's
    //parser catches these exceptions, reports them in the metadata
    //and then throws the first stored exception after the parse has completed.
    private boolean isCatchIntermediateIOExceptions = true;

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
                getProp(props.getProperty("enableAutoSpace"), getEnableAutoSpace()));
        setSuppressDuplicateOverlappingText(
                getProp(props.getProperty("suppressDuplicateOverlappingText"),
                        getSuppressDuplicateOverlappingText()));
        setExtractAnnotationText(
                getProp(props.getProperty("extractAnnotationText"),
                        getExtractAnnotationText()));
        setSortByPosition(
                getProp(props.getProperty("sortByPosition"),
                        getSortByPosition()));
        setExtractAcroFormContent(
                getProp(props.getProperty("extractAcroFormContent"),
                        getExtractAcroFormContent()));
        setExtractInlineImages(
                getProp(props.getProperty("extractInlineImages"),
                        getExtractInlineImages()));
        setExtractUniqueInlineImagesOnly(
                getProp(props.getProperty("extractUniqueInlineImagesOnly"),
                        getExtractUniqueInlineImagesOnly()));

        setIfXFAExtractOnlyXFA(
            getProp(props.getProperty("ifXFAExtractOnlyXFA"),
                getIfXFAExtractOnlyXFA()));

        setCatchIntermediateIOExceptions(
                getProp(props.getProperty("catchIntermediateIOExceptions"),
                isCatchIntermediateIOExceptions()));

        boolean checkExtractAccessPermission = getProp(props.getProperty("checkExtractAccessPermission"), false);
        boolean allowExtractionForAccessibility = getProp(props.getProperty("allowExtractionForAccessibility"), true);

        if (checkExtractAccessPermission == false) {
            //silently ignore the crazy configuration of checkExtractAccessPermission = false,
            //but allowExtractionForAccessibility=false
            accessChecker = new AccessChecker();
        } else {
            accessChecker = new AccessChecker(allowExtractionForAccessibility);
        }
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
     */
    public boolean isCatchIntermediateIOExceptions() {
        return isCatchIntermediateIOExceptions;
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
        isCatchIntermediateIOExceptions = catchIntermediateIOExceptions;
    }

    private boolean getProp(String p, boolean defaultMissing) {
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime
                * result
                + ((averageCharTolerance == null) ? 0 : averageCharTolerance
                .hashCode());
        result = prime * result + (enableAutoSpace ? 1231 : 1237);
        result = prime * result + (extractAcroFormContent ? 1231 : 1237);
        result = prime * result + (extractAnnotationText ? 1231 : 1237);
        result = prime * result + (extractInlineImages ? 1231 : 1237);
        result = prime * result + (extractUniqueInlineImagesOnly ? 1231 : 1237);
        result = prime * result + (sortByPosition ? 1231 : 1237);
        result = prime
                * result
                + ((spacingTolerance == null) ? 0 : spacingTolerance.hashCode());
        result = prime * result
                + (suppressDuplicateOverlappingText ? 1231 : 1237);
        result = prime * result + (ifXFAExtractOnlyXFA ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PDFParserConfig other = (PDFParserConfig) obj;
        if (averageCharTolerance == null) {
            if (other.averageCharTolerance != null)
                return false;
        } else if (!averageCharTolerance.equals(other.averageCharTolerance))
            return false;
        if (enableAutoSpace != other.enableAutoSpace)
            return false;
        if (extractAcroFormContent != other.extractAcroFormContent)
            return false;
        if (extractAnnotationText != other.extractAnnotationText)
            return false;
        if (extractInlineImages != other.extractInlineImages)
            return false;
        if (extractUniqueInlineImagesOnly != other.extractUniqueInlineImagesOnly)
            return false;
        if (sortByPosition != other.sortByPosition)
            return false;
        if (spacingTolerance == null) {
            if (other.spacingTolerance != null)
                return false;
        } else if (!spacingTolerance.equals(other.spacingTolerance))
            return false;
        if (suppressDuplicateOverlappingText != other.suppressDuplicateOverlappingText)
            return false;
        if (ifXFAExtractOnlyXFA != other.ifXFAExtractOnlyXFA)
            return false;

        return true;
    }

    @Override
    public String toString() {
        return "PDFParserConfig [enableAutoSpace=" + enableAutoSpace
                + ", suppressDuplicateOverlappingText="
                + suppressDuplicateOverlappingText + ", extractAnnotationText="
                + extractAnnotationText + ", sortByPosition=" + sortByPosition
                + ", extractAcroFormContent=" + extractAcroFormContent
                + ", ifXFAExtractOnlyXFA=" + ifXFAExtractOnlyXFA
                + ", extractInlineImages=" + extractInlineImages
                + ", extractUniqueInlineImagesOnly="
                + extractUniqueInlineImagesOnly + ", averageCharTolerance="
                + averageCharTolerance + ", spacingTolerance="
                + spacingTolerance + "]";
    }


}
