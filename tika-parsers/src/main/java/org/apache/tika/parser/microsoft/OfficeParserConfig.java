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

    private boolean useSAXDocxExtractor = false;
    private boolean useSAXPptxExtractor = false;

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
}


