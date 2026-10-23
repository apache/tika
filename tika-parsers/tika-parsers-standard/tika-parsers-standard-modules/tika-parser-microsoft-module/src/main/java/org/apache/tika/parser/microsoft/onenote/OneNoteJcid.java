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

/** JCID index values from MS-ONE 2.1.13. */
public final class OneNoteJcid {

    public static final int SECTION_NODE = 0x07;
    public static final int PAGE_SERIES_NODE = 0x08;
    public static final int PAGE_NODE = 0x0B;
    public static final int OUTLINE_NODE = 0x0C;
    public static final int OUTLINE_ELEMENT_NODE = 0x0D;
    public static final int NUMBER_LIST_NODE = 0x12;
    public static final int TABLE_NODE = 0x22;
    public static final int TABLE_ROW_NODE = 0x23;
    public static final int TABLE_CELL_NODE = 0x24;
    public static final int PAGE_METADATA = 0x30;
    public static final int CONFLICT_PAGE_METADATA = 0x38;

    private OneNoteJcid() {
    }
}
