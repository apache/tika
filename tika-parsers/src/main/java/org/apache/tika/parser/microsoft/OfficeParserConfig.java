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
package org.apache.tika.parser.microsoft;


import org.apache.tika.config.Field;

import java.io.Serializable;

public class OfficeParserConfig implements Serializable {

    private boolean extractMacros = false;

    private boolean includeDeletedContent = false;
    private boolean includeMoveFromContent = false;
    private boolean includeShapeBasedContent = true;
    private boolean includeHeadersAndFooters = true;
    private boolean concatenatePhoneticRuns = true;

    private boolean useSAXDocxExtractor = false;
    private boolean useSAXPptxExtractor = false;
    private boolean extractAllAlternativesFromMSG;

    /**
     * Sets whether or not MSOffice parsers should extract macros.
     * As of Tika 1.15, the default is <code>false</code>.
     *
     * @param extractMacros
     */
    public void setExtractMacros(boolean extractMacros) {
        this.extractMacros = extractMacros;
    }

    /**
     *
     * @return whether or not to extract macros
     */
    public boolean getExtractMacros() {
        return extractMacros;
    }
    /**
     * Sets whether or not the parser should include deleted content.
     * <p/>
     * <b>This has only been implemented in the streaming docx parser
     * ({@link org.apache.tika.parser.microsoft.ooxml.SXWPFWordExtractorDecorator} so far!!!</b>
     * @param includeDeletedContent
     */
    public void setIncludeDeletedContent(boolean includeDeletedContent) {
        this.includeDeletedContent = includeDeletedContent;
    }

    public boolean getIncludeDeletedContent() {
        return includeDeletedContent;
    }

    /**
     * With track changes on, when a section is moved, the content
     * is stored in both the "moveFrom" section and in the "moveTo" section.
     * <p/>
     * If you'd like to include the section both in its original location (moveFrom)
     * and in its new location (moveTo), set this to <code>true</code>
     * <p/>
     * Default: <code>false</code>
     * <p/>
     * <b>This has only been implemented in the streaming docx parser
     * ({@link org.apache.tika.parser.microsoft.ooxml.SXWPFWordExtractorDecorator} so far!!!</b>
     * @param includeMoveFromContent
     */
    public void setIncludeMoveFromContent(boolean includeMoveFromContent) {
        this.includeMoveFromContent = includeMoveFromContent;
    }

    public boolean getIncludeMoveFromContent() {
        return includeMoveFromContent;
    }

    /**
     * In Excel and Word, there can be text stored within drawing shapes.
     * (In PowerPoint everything is in a Shape)
     * <p/>
     * If you'd like to skip processing these to look for text, set this to
     *  <code>false</code>
     * <p/>
     * Default: <code>true</code>
     * @param includeShapeBasedContent
     */
    public void setIncludeShapeBasedContent(boolean includeShapeBasedContent) {
        this.includeShapeBasedContent = includeShapeBasedContent;
    }

    public boolean getIncludeShapeBasedContent() {
        return includeShapeBasedContent;
    }

    /**
     * Whether or not to include headers and footers.
     * <p/>
     * This only operates on headers and footers in Word and Excel,
     * not master slide content in Powerpoint.
     * <p/>
     * Default: <code>true</code>
     * @param includeHeadersAndFooters
     */
    public void setIncludeHeadersAndFooters(boolean includeHeadersAndFooters) {
        this.includeHeadersAndFooters = includeHeadersAndFooters;
    }

    public boolean getIncludeHeadersAndFooters() {
        return includeHeadersAndFooters;
    }
    public boolean getUseSAXDocxExtractor() {
        return useSAXDocxExtractor;
    }

    /**
     * Use the experimental SAX-based streaming DOCX parser?
     * If set to <code>false</code>, the classic parser will be used; if <code>true</code>,
     * the new experimental parser will be used.
     * <p/>
     * Default: <code>false</code> (classic DOM parser)
     * @param useSAXDocxExtractor
     */
    public void setUseSAXDocxExtractor(boolean useSAXDocxExtractor) {
        this.useSAXDocxExtractor = useSAXDocxExtractor;
    }

    /**
     * Use the experimental SAX-based streaming DOCX parser?
     * If set to <code>false</code>, the classic parser will be used; if <code>true</code>,
     * the new experimental parser will be used.
     * <p/>
     * Default: <code>false</code> (classic DOM parser)
     * @param useSAXPptxExtractor
     */
    public void setUseSAXPptxExtractor(boolean useSAXPptxExtractor) {
        this.useSAXPptxExtractor = useSAXPptxExtractor;
    }

    public boolean getUseSAXPptxExtractor() {
        return useSAXPptxExtractor;
    }


    public boolean getConcatenatePhoneticRuns() {
        return concatenatePhoneticRuns;
    }

    /**
     * Microsoft Excel files can sometimes contain phonetic (furigana) strings.
     * See <a href="https://support.office.com/en-us/article/PHONETIC-function-9a329dac-0c0f-42f8-9a55-639086988554">PHONETIC</a>.
     * This sets whether or not the parser will concatenate the phonetic runs to the original text.
     * <p>
     * This is currently only supported by the xls and xlsx parsers (not the xlsb parser),
     * and the default is <code>true</code>.
     * </p>
     *
     * @param concatenatePhoneticRuns
     */
    public void setConcatenatePhoneticRuns(boolean concatenatePhoneticRuns) {
        this.concatenatePhoneticRuns = concatenatePhoneticRuns;
    }


    /**
     * Some .msg files can contain body content in html, rtf and/or text.
     * The default behavior is to pick the first non-null value and include only that.
     * If you'd like to extract all non-null body content, which is likely duplicative,
     * set this value to true.
     *
     * @param extractAllAlternativesFromMSG whether or not to extract all alternative parts
     * @since 1.17
     */
    public void setExtractAllAlternativesFromMSG(boolean extractAllAlternativesFromMSG) {
        this.extractAllAlternativesFromMSG = extractAllAlternativesFromMSG;
    }


    public boolean getExtractAllAlternativesFromMSG() {
        return extractAllAlternativesFromMSG;
    }
}


