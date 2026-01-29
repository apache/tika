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
package org.apache.tika.sax;

import java.io.Serializable;

/**
 * Configuration for SAX output behavior.
 * <p>
 * This can be stored in a {@link org.apache.tika.parser.ParseContext} to control
 * how content handlers and embedded document extractors generate output.
 * </p>
 */
public class SAXOutputConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Whether to write metadata as &lt;meta&gt; elements in the XHTML head.
     * Default is {@code true} for backward compatibility.
     */
    private boolean writeMetadataToHead = true;

    /**
     * Whether to include the &lt;title&gt; element in the XHTML head.
     * Default is {@code true} for backward compatibility.
     */
    private boolean includeTitle = true;

    /**
     * Whether to write embedded file names to content (e.g., as &lt;h1&gt; elements).
     * Default is {@code true} for backward compatibility.
     */
    private boolean writeFileNameToContent = true;

    public SAXOutputConfig() {
    }

    public boolean isWriteMetadataToHead() {
        return writeMetadataToHead;
    }

    public void setWriteMetadataToHead(boolean writeMetadataToHead) {
        this.writeMetadataToHead = writeMetadataToHead;
    }

    public boolean isIncludeTitle() {
        return includeTitle;
    }

    public void setIncludeTitle(boolean includeTitle) {
        this.includeTitle = includeTitle;
    }

    public boolean isWriteFileNameToContent() {
        return writeFileNameToContent;
    }

    public void setWriteFileNameToContent(boolean writeFileNameToContent) {
        this.writeFileNameToContent = writeFileNameToContent;
    }
}
