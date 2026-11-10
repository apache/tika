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

/**
 * Configuration for {@link OneNoteParser}, set in the parser's JSON config or per parse in the
 * {@link org.apache.tika.parser.ParseContext}.
 */
public class OneNoteParserConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean includePageHistory = false;
    private boolean includeDeletedContent = false;

    /**
     * Whether text and metadata (authors, timestamps, page GUIDs) include every earlier revision
     * and saved page version, not only each page's current one. Off, the output describes the
     * section as it is now, like the final state of an incrementally updated PDF.
     * <p>
     * Exception: .one (2010-2016) sections keep attachments in one store shared by all
     * versions, so every stored attachment is extracted either way, including files that only
     * earlier page versions used.
     */
    public boolean isIncludePageHistory() {
        return includePageHistory;
    }

    public void setIncludePageHistory(boolean includePageHistory) {
        this.includePageHistory = includePageHistory;
    }

    /**
     * Whether text includes pages the section still stores but no longer holds, e.g. pages moved
     * to the recycle bin, with all their stored versions. Only FSSHTTPB sections (saved by OneNote
     * for the web, Windows 10 and later) are checked.
     */
    public boolean isIncludeDeletedContent() {
        return includeDeletedContent;
    }

    public void setIncludeDeletedContent(boolean includeDeletedContent) {
        this.includeDeletedContent = includeDeletedContent;
    }
}
