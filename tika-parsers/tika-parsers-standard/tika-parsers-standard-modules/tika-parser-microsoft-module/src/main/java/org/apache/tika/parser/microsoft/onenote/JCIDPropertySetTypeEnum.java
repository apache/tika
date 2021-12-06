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

import java.util.HashMap;
import java.util.Map;

/**
 * The JCID property set type enum from section 2.1.13 of MS-ONE
 * specification.
 */
enum JCIDPropertySetTypeEnum {
    jcidReadOnlyPersistablePropertyContainerForAuthor(0x00120001),
    jcidPersistablePropertyContainerForTOC(0x00020001),
    jcidPersistablePropertyContainerForTOCSection(0x00020001), jcidSectionNode(0x00060007),
    jcidPageSeriesNode(0x00060008), jcidPageNode(0x0006000B), jcidOutlineNode(0x0006000C),
    jcidOutlineElementNode(0x0006000D), jcidRichTextOENode(0x0006000E), jcidImageNode(0x00060011),
    jcidNumberListNode(0x00060012), jcidOutlineGroup(0x00060019), jcidTableNode(0x00060022),
    jcidTableRowNode(0x00060023), jcidTableCellNode(0x00060024), jcidTitleNode(0x0006002C),
    jcidPageMetaData(0x00020030), jcidSectionMetaData(0x00020031), jcidEmbeddedFileNode(0x00060035),
    jcidPageManifestNode(0x00060037), jcidConflictPageMetaData(0x00020038),
    jcidVersionHistoryContent(0x0006003C), jcidVersionProxy(0x0006003D),
    jcidNoteTagSharedDefinitionContainer(0x00120043), jcidRevisionMetaData(0x00020044),
    jcidVersionHistoryMetaData(0x00020046), jcidParagraphStyleObject(0x0012004D),
    jcidParagraphStyleObjectForText(0x0012004D), unknown(0x0);

    private static final Map<Long, JCIDPropertySetTypeEnum> BY_ID = new HashMap<>();

    static {
        for (JCIDPropertySetTypeEnum e : values()) {
            BY_ID.put(e.jcid, e);
        }
    }

    private final long jcid;

    JCIDPropertySetTypeEnum(long jcid) {
        this.jcid = jcid;
    }

    public static JCIDPropertySetTypeEnum of(Long id) {
        JCIDPropertySetTypeEnum result = BY_ID.get(id);
        if (result == null) {
            return unknown;
        }
        return result;
    }
}
