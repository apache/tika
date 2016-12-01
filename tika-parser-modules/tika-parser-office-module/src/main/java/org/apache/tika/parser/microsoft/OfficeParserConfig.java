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

    private boolean includeDeletedContent = false;
    private boolean includeMoveFromContent = false;

    private boolean useSAXDocxExtractor = false;

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

    public boolean getUseSAXDocxExtractor() {
        return useSAXDocxExtractor;
    }

    /**
     * Use the experimental SAX-based streaming DOCX parser?
     * If set to <code>false</code>, the classic parser will be used; if <code>true</code>,
     * the new experimental parser will be used.
     * <p/>
     * Default: classic parser
     * @param useSAXDocxExtractor
     */
    public void setUseSAXDocxExtractor(boolean useSAXDocxExtractor) {
        this.useSAXDocxExtractor = useSAXDocxExtractor;
    }
}


