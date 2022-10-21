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


import java.io.Serializable;

public class OfficeParserConfig implements Serializable {

    private boolean extractMacros = false;

    private boolean includeDeletedContent = false;
    private boolean includeMoveFromContent = false;
    private boolean includeShapeBasedContent = true;
    private boolean includeHeadersAndFooters = true;
    private boolean includeMissingRows = false;
    private boolean includeSlideNotes = true;
    private boolean includeSlideMasterContent = true;
    private boolean concatenatePhoneticRuns = true;

    private boolean useSAXDocxExtractor = false;
    private boolean useSAXPptxExtractor = false;
    private boolean extractAllAlternativesFromMSG;

    private String dateOverrideFormat = null;
    private int maxOverride;

    /**
     * @return whether or not to extract macros
     */
    public boolean isExtractMacros() {
        return extractMacros;
    }

    /**
     * Sets whether or not MSOffice parsers should extract macros.
     * As of Tika 1.15, the default is <code>false</code>.
     *
     * @param extractMacros
     */
    public void setExtractMacros(boolean extractMacros) {
        this.extractMacros = extractMacros;
    }

    public boolean isIncludeDeletedContent() {
        return includeDeletedContent;
    }

    /**
     * Sets whether or not the parser should include deleted content.
     * <p/>
     * <b>This has only been implemented in the streaming docx parser
     * ({@link org.apache.tika.parser.microsoft.ooxml.SXWPFWordExtractorDecorator} so far!!!</b>
     *
     * @param includeDeletedContent
     */
    public void setIncludeDeletedContent(boolean includeDeletedContent) {
        this.includeDeletedContent = includeDeletedContent;
    }

    public boolean isIncludeMoveFromContent() {
        return includeMoveFromContent;
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
     *
     * @param includeMoveFromContent
     */
    public void setIncludeMoveFromContent(boolean includeMoveFromContent) {
        this.includeMoveFromContent = includeMoveFromContent;
    }

    public boolean isIncludeShapeBasedContent() {
        return includeShapeBasedContent;
    }

    /**
     * In Excel and Word, there can be text stored within drawing shapes.
     * (In PowerPoint everything is in a Shape)
     * <p/>
     * If you'd like to skip processing these to look for text, set this to
     * <code>false</code>
     * <p/>
     * Default: <code>true</code>
     *
     * @param includeShapeBasedContent
     */
    public void setIncludeShapeBasedContent(boolean includeShapeBasedContent) {
        this.includeShapeBasedContent = includeShapeBasedContent;
    }

    public boolean isIncludeHeadersAndFooters() {
        return includeHeadersAndFooters;
    }

    /**
     * Whether or not to include headers and footers.
     * <p/>
     * This only operates on headers and footers in Word and Excel,
     * not master slide content in Powerpoint.
     * <p/>
     * Default: <code>true</code>
     *
     * @param includeHeadersAndFooters
     */
    public void setIncludeHeadersAndFooters(boolean includeHeadersAndFooters) {
        this.includeHeadersAndFooters = includeHeadersAndFooters;
    }

    public boolean isUseSAXDocxExtractor() {
        return useSAXDocxExtractor;
    }

    /**
     * Use the experimental SAX-based streaming DOCX parser?
     * If set to <code>false</code>, the classic parser will be used; if <code>true</code>,
     * the new experimental parser will be used.
     * <p/>
     * Default: <code>false</code> (classic DOM parser)
     *
     * @param useSAXDocxExtractor
     */
    public void setUseSAXDocxExtractor(boolean useSAXDocxExtractor) {
        this.useSAXDocxExtractor = useSAXDocxExtractor;
    }

    public boolean isUseSAXPptxExtractor() {
        return useSAXPptxExtractor;
    }

    /**
     * Use the experimental SAX-based streaming DOCX parser?
     * If set to <code>false</code>, the classic parser will be used; if <code>true</code>,
     * the new experimental parser will be used.
     * <p/>
     * Default: <code>false</code> (classic DOM parser)
     *
     * @param useSAXPptxExtractor
     */
    public void setUseSAXPptxExtractor(boolean useSAXPptxExtractor) {
        this.useSAXPptxExtractor = useSAXPptxExtractor;
    }

    public boolean isConcatenatePhoneticRuns() {
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

    public boolean isExtractAllAlternativesFromMSG() {
        return extractAllAlternativesFromMSG;
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

    public boolean isIncludeMissingRows() {
        return includeMissingRows;
    }

    /**
     * For table-like formats, and tables within other formats, should
     * missing rows in sparse tables be output where detected?
     * The default is to only output rows defined within the file, which
     * avoid lots of blank lines, but means layout isn't preserved.
     */
    public void setIncludeMissingRows(boolean includeMissingRows) {
        this.includeMissingRows = includeMissingRows;
    }

    public boolean isIncludeSlideNotes() {
        return includeSlideNotes;
    }

    /**
     * Whether or not to process slide notes content.  If set
     * to <code>false</code>, the parser will skip the text content
     * and all embedded objects from the slide notes in ppt and ppt[xm].
     * The default is <code>true</code>.
     *
     * @param includeSlideNotes whether or not to process slide notes
     * @since 1.19.1
     */
    public void setIncludeSlideNotes(boolean includeSlideNotes) {
        this.includeSlideNotes = includeSlideNotes;
    }

    /**
     * @return whether or not to process content in slide masters
     * @since 1.19.1
     */
    public boolean isIncludeSlideMasterContent() {
        return includeSlideMasterContent;
    }

    /**
     * Whether or not to include contents from any of the three
     * types of masters -- slide, notes, handout -- in a .ppt or ppt[xm] file.
     * If set to <code>false</code>, the parser will not extract
     * text or embedded objects from any of the masters.
     *
     * @param includeSlideMasterContent
     * @since 1.19.1
     */
    public void setIncludeSlideMasterContent(boolean includeSlideMasterContent) {
        this.includeSlideMasterContent = includeSlideMasterContent;
    }

    public String getDateFormatOverride() {
        return dateOverrideFormat;
    }

    /**
     * A user may wish to override the date formats in xls and xlsx files.
     * For example, a user might prefer 'yyyy-mm-dd' to 'mm/dd/yy'.
     * <p>
     * Note: these formats are "Excel formats" not Java's SimpleDateFormat
     *
     * @param format
     */
    public void setDateOverrideFormat(String format) {
        this.dateOverrideFormat = format;
    }

    public void setMaxOverride(int maxOverride) {
        this.maxOverride = maxOverride;
    }

    public int getMaxOverride() {
        return this.maxOverride;
    }
}


