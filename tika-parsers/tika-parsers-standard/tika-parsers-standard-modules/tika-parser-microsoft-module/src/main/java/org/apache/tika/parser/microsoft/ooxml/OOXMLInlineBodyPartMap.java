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
package org.apache.tika.parser.microsoft.ooxml;

import java.util.Collections;
import java.util.Map;

/**
 * Holds pre-parsed XML content fragments for OOXML document parts that are
 * referenced inline from the main document body. Each map stores
 * ID → raw XML bytes for a specific part type.
 * <p>
 * Used for footnotes, endnotes, and comments so that their content can be
 * inlined at the point of reference rather than dumped at the end.
 */
class OOXMLInlineBodyPartMap {

    static final OOXMLInlineBodyPartMap EMPTY = new OOXMLInlineBodyPartMap(
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyMap());

    private final Map<String, byte[]> footnotes;
    private final Map<String, byte[]> endnotes;
    private final Map<String, byte[]> comments;
    private final Map<String, String> linkedRelationships;

    OOXMLInlineBodyPartMap(Map<String, byte[]> footnotes,
            Map<String, byte[]> endnotes,
            Map<String, byte[]> comments,
            Map<String, String> linkedRelationships) {
        this.footnotes = footnotes;
        this.endnotes = endnotes;
        this.comments = comments;
        this.linkedRelationships = linkedRelationships;
    }

    Map<String, String> getLinkedRelationships() {
        return linkedRelationships;
    }

    byte[] getFootnote(String id) {
        return footnotes.get(id);
    }

    byte[] getEndnote(String id) {
        return endnotes.get(id);
    }

    byte[] getComment(String id) {
        return comments.get(id);
    }

    boolean hasFootnotes() {
        return !footnotes.isEmpty();
    }

    boolean hasEndnotes() {
        return !endnotes.isEmpty();
    }

    boolean hasComments() {
        return !comments.isEmpty();
    }
}
