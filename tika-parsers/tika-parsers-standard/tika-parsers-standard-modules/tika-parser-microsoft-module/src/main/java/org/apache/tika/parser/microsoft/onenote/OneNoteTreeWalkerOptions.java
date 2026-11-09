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
package org.apache.tika.parser.microsoft.onenote;


import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Options when walking the one note tree.
 */
public class OneNoteTreeWalkerOptions implements Serializable {
    private boolean crawlAllFileNodesFromRoot = false;
    private boolean includeDeletedContent = false;
    private boolean onlyLatestRevision = true;
    private Set<OneNotePropertyEnum> utf16PropertiesToPrint = new HashSet<>(
            Arrays.asList(OneNotePropertyEnum.ImageFilename, OneNotePropertyEnum.ImageAltText,
                    OneNotePropertyEnum.Author, OneNotePropertyEnum.FolderChildFilename));

    /**
     * Whether text comes from every revision, page history included, rather than only each
     * page's current revision. Metadata and attachments come from every revision either way.
     */
    public boolean isCrawlAllFileNodesFromRoot() {
        return crawlAllFileNodesFromRoot;
    }

    /**
     * @param crawlAllFileNodesFromRoot whether to emit text from every revision
     * @return this
     */
    public OneNoteTreeWalkerOptions setCrawlAllFileNodesFromRoot(
            boolean crawlAllFileNodesFromRoot) {
        this.crawlAllFileNodesFromRoot = crawlAllFileNodesFromRoot;
        return this;
    }

    /** Whether text includes pages the section no longer holds, e.g. moved to the recycle bin. */
    public boolean isIncludeDeletedContent() {
        return includeDeletedContent;
    }

    public OneNoteTreeWalkerOptions setIncludeDeletedContent(boolean includeDeletedContent) {
        this.includeDeletedContent = includeDeletedContent;
        return this;
    }

    /**
     * Only parse the latest revision.
     */
    public boolean isOnlyLatestRevision() {
        return onlyLatestRevision;
    }

    /**
     * Only parse the latest revision.
     *
     * @param onlyLatestRevision
     * @return Returns this, as per builder pattern.
     */
    public OneNoteTreeWalkerOptions setOnlyLatestRevision(boolean onlyLatestRevision) {
        this.onlyLatestRevision = onlyLatestRevision;
        return this;
    }

    /**
     * Print file node data in UTF-16 format when they match these props.
     */
    public Set<OneNotePropertyEnum> getUtf16PropertiesToPrint() {
        return utf16PropertiesToPrint;
    }

    /**
     * Print file node data in UTF-16 format when they match these props.
     *
     * @param utf16PropertiesToPrint The set of UTF properties you want to print UTF-16 for.
     *                               Defaults are usually ok here.
     * @return Returns this, as per builder pattern.
     */
    public OneNoteTreeWalkerOptions setUtf16PropertiesToPrint(
            Set<OneNotePropertyEnum> utf16PropertiesToPrint) {
        this.utf16PropertiesToPrint = utf16PropertiesToPrint;
        return this;
    }
}
