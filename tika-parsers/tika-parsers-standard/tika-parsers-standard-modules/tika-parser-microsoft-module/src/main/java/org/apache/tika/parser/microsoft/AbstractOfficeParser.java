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

import org.apache.poi.util.IOUtils;

import org.apache.tika.config.Field;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;

/**
 * Intermediate layer to set {@link OfficeParserConfig} uniformly.
 */
public abstract class AbstractOfficeParser extends AbstractParser {

    private final OfficeParserConfig defaultOfficeParserConfig = new OfficeParserConfig();

    /**
     * Checks to see if the user has specified an {@link OfficeParserConfig}.
     * If so, no changes are made; if not, one is added to the context.
     *
     * @param parseContext
     */
    public void configure(ParseContext parseContext) {
        OfficeParserConfig officeParserConfig =
                parseContext.get(OfficeParserConfig.class, defaultOfficeParserConfig);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
    }

    /**
     * @return
     * @see OfficeParserConfig#isIncludeDeletedContent
     */
    public boolean isIncludeDeletedContent() {
        return defaultOfficeParserConfig.isIncludeDeletedContent();
    }

    @Field
    public void setIncludeDeletedContent(boolean includeDeletedConent) {
        defaultOfficeParserConfig.setIncludeDeletedContent(includeDeletedConent);
    }

    /**
     * @return
     * @see OfficeParserConfig#isIncludeMoveFromContent()
     */

    public boolean isIncludeMoveFromContent() {
        return defaultOfficeParserConfig.isIncludeMoveFromContent();
    }

    @Field
    public void setIncludeMoveFromContent(boolean includeMoveFromContent) {
        defaultOfficeParserConfig.setIncludeMoveFromContent(includeMoveFromContent);
    }

    /**
     * @return
     * @see OfficeParserConfig#isUseSAXDocxExtractor()
     */
    public boolean isUseSAXDocxExtractor() {
        return defaultOfficeParserConfig.isUseSAXDocxExtractor();
    }

    @Field
    public void setUseSAXDocxExtractor(boolean useSAXDocxExtractor) {
        defaultOfficeParserConfig.setUseSAXDocxExtractor(useSAXDocxExtractor);
    }

    /**
     * @return whether or not to extract macros
     * @see OfficeParserConfig#isExtractMacros()
     */
    public boolean isExtractMacros() {
        return defaultOfficeParserConfig.isExtractMacros();
    }

    @Field
    public void setExtractMacros(boolean extractMacros) {
        defaultOfficeParserConfig.setExtractMacros(extractMacros);
    }

    @Field
    public void setIncludeShapeBasedContent(boolean includeShapeBasedContent) {
        defaultOfficeParserConfig.setIncludeShapeBasedContent(includeShapeBasedContent);
    }

    @Field
    public void setUseSAXPptxExtractor(boolean useSAXPptxExtractor) {
        defaultOfficeParserConfig.setUseSAXPptxExtractor(useSAXPptxExtractor);
    }

    @Field
    public void setConcatenatePhoneticRuns(boolean concatenatePhoneticRuns) {
        defaultOfficeParserConfig.setConcatenatePhoneticRuns(concatenatePhoneticRuns);
    }

    void getConcatenatePhoneticRuns() {
        defaultOfficeParserConfig.isConcatenatePhoneticRuns(); // TODO result is ignored and there is no side effect
    }

    public boolean isExtractAllAlternativesFromMSG() {
        return defaultOfficeParserConfig.isExtractAllAlternativesFromMSG();
    }

    /**
     * Some .msg files can contain body content in html, rtf and/or text.
     * The default behavior is to pick the first non-null value and include only that.
     * If you'd like to extract all non-null body content, which is likely duplicative,
     * set this value to true.
     *
     * @param extractAllAlternativesFromMSG whether or not to extract all alternative parts from
     *                                     msg files
     * @since 1.17
     */
    @Field
    public void setExtractAllAlternativesFromMSG(boolean extractAllAlternativesFromMSG) {
        defaultOfficeParserConfig.setExtractAllAlternativesFromMSG(extractAllAlternativesFromMSG);
    }

    /**
     * <b>WARNING:</b> this sets a static variable in POI.
     * This allows users to override POI's protection of the allocation
     * of overly large byte arrays.  Use carefully; and please open up issues on
     * POI's bugzilla to bump values for specific records.
     *
     * @param maxOverride
     */
    @Field
    public void setByteArrayMaxOverride(int maxOverride) {
        IOUtils.setByteArrayMaxOverride(maxOverride);
    }

    @Field
    public void setDateFormatOverride(String format) {
        defaultOfficeParserConfig.setDateOverrideFormat(format);
    }
}
