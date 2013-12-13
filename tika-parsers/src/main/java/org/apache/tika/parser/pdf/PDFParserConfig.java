package org.apache.tika.parser.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Properties;
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

/**
 * Config for PDFParser.
 * 
 * This allows parameters to be set programmatically:
 * <ol>
 * <li>Calls to PDFParser, i.e. parser.getPDFParserConfig().setEnableAutoSpace() (as before)</li>
 * <li>Constructor of PDFParser</li>
 * <li>Passing to PDFParser through a ParseContext: context.set(PDFParserConfig.class, config);</li>
 * </ol>
 * 
 * Parameters can also be set by modifying the PDFParserConfig.properties file,
 * which lives in the expected places, in trunk:
 * tika-parsers/src/main/resources/org/apache/tika/parser/pdf
 * 
 * Or, in tika-app-x.x.jar or tika-parsers-x.x.jar:
 * org/apache/tika/parser/pdf
 *
 */
public class PDFParserConfig implements Serializable{

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

    //True if we should use PDFBox's NonSequentialParser
    private boolean useNonSequentialParser = false;
    
    //True if acroform content should be extracted
    private boolean extractAcroFormContent = true;

    public PDFParserConfig(){
        init(this.getClass().getResourceAsStream("PDFParser.properties"));
    }

    /**
     * Loads properties from InputStream and then tries to close InputStream.
     * If there is an IOException, this silently swallows the exception
     * and goes back to the default.
     * 
     * @param is
     */
    public PDFParserConfig(InputStream is){
        init(is);
    }

    //initializes object and then tries to close inputstream
    private void init(InputStream is){

        if (is == null){
            return;
        }
        Properties props = new Properties();
        try{
            props.load(is);
        } catch (IOException e){
        } finally {
            if (is != null){
                try{
                    is.close();
                } catch (IOException e){
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
        setUseNonSequentialParser(
                getProp(props.getProperty("useNonSequentialParser"), 
                        getUseNonSequentialParser()));
        setExtractAcroFormContent(
                getProp(props.getProperty("extractAcroFormContent"),
                getExtractAcroFormContent()));
    }

    
    /**
     * If true (the default), extract content from AcroForms
     * at the end of the document.
     * 
     * @param b
     */
    public void setExtractAcroFormContent(boolean extractAcroFormContent) {
        this.extractAcroFormContent = extractAcroFormContent;
        
    }

    /** @see #setExtractAcroFormContent(boolean) */
    public boolean getExtractAcroFormContent() {
        return extractAcroFormContent;
    }

    /** @see #setEnableAutoSpace. */
    public boolean getEnableAutoSpace() {
        return enableAutoSpace;
    }

    /**
     *  If true (the default), the parser should estimate
     *  where spaces should be inserted between words.  For
     *  many PDFs this is necessary as they do not include
     *  explicit whitespace characters.
     */
    public void setEnableAutoSpace(boolean enableAutoSpace) {
        this.enableAutoSpace = enableAutoSpace;
    }

    /** @see #setSuppressDuplicateOverlappingText(boolean)*/
    public boolean getSuppressDuplicateOverlappingText() {
        return suppressDuplicateOverlappingText;
    }

    /**
     *  If true, the parser should try to remove duplicated
     *  text over the same region.  This is needed for some
     *  PDFs that achieve bolding by re-writing the same
     *  text in the same area.  Note that this can
     *  slow down extraction substantially (PDFBOX-956) and
     *  sometimes remove characters that were not in fact
     *  duplicated (PDFBOX-1155).  By default this is disabled.
     */
    public void setSuppressDuplicateOverlappingText(
            boolean suppressDuplicateOverlappingText) {
        this.suppressDuplicateOverlappingText = suppressDuplicateOverlappingText;
    }

    /** @see #setExtractAnnotationText(boolean)*/
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
    /** @see #setSortByPosition(boolean)*/
    public boolean getSortByPosition() {
        return sortByPosition;
    }

    /**
     *  If true, sort text tokens by their x/y position
     *  before extracting text.  This may be necessary for
     *  some PDFs (if the text tokens are not rendered "in
     *  order"), while for other PDFs it can produce the
     *  wrong result (for example if there are 2 columns,
     *  the text will be interleaved).  Default is false.
     */
    public void setSortByPosition(boolean sortByPosition) {
        this.sortByPosition = sortByPosition;
    }

    /** @see #setUseNonSequentialParser(boolean)*/
    public boolean getUseNonSequentialParser() {
        return useNonSequentialParser;
    }

    /**
     * If true, uses PDFBox's non-sequential parser.
     * The non-sequential parser should be much faster than the traditional
     * full doc parser.  However, until PDFBOX-XXX is fixed, 
     * the non-sequential parser fails
     * to extract some document metadata.
     * <p>
     * Default is false (use the traditional parser)
     * @param useNonSequentialParser
     */
    public void setUseNonSequentialParser(boolean useNonSequentialParser) {
        this.useNonSequentialParser = useNonSequentialParser;
    }

    private boolean getProp(String p, boolean defaultMissing){
        if (p == null){
            return defaultMissing;
        }
        if (p.toLowerCase().equals("true")){
            return true;
        } else if (p.toLowerCase().equals("false")){
            return false;
        } else {
            return defaultMissing;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (enableAutoSpace ? 1231 : 1237);
        result = prime * result + (extractAcroFormContent ? 1231 : 1237);
        result = prime * result + (extractAnnotationText ? 1231 : 1237);
        result = prime * result + (sortByPosition ? 1231 : 1237);
        result = prime * result
                + (suppressDuplicateOverlappingText ? 1231 : 1237);
        result = prime * result + (useNonSequentialParser ? 1231 : 1237);
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
        if (enableAutoSpace != other.enableAutoSpace)
            return false;
        if (extractAcroFormContent != other.extractAcroFormContent)
            return false;
        if (extractAnnotationText != other.extractAnnotationText)
            return false;
        if (sortByPosition != other.sortByPosition)
            return false;
        if (suppressDuplicateOverlappingText != other.suppressDuplicateOverlappingText)
            return false;
        if (useNonSequentialParser != other.useNonSequentialParser)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "PDFParserConfig [enableAutoSpace=" + enableAutoSpace
                + ", suppressDuplicateOverlappingText="
                + suppressDuplicateOverlappingText + ", extractAnnotationText="
                + extractAnnotationText + ", sortByPosition=" + sortByPosition
                + ", useNonSequentialParser=" + useNonSequentialParser
                + ", extractAcroFormContent=" + extractAcroFormContent + "]";
    }



}
