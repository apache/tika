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

import org.apache.tika.parser.microsoft.ScriptedTextCell;

/**
 * Sheet contents handler that uses {@link XSSFCommentsShim.CommentData}
 * instead of POI's XMLBeans-dependent {@code XSSFComment}.
 */
interface TikaSheetContentsHandler {

    void startRow(int rowNum);

    void endRow(int rowNum);

    void cell(String cellRef, String formattedValue, XSSFCommentsShim.CommentData comment);

    /**
     * A cell whose text has superscript or subscript in it. The default drops the
     * formatting.
     */
    default void scriptedCell(String cellRef, ScriptedTextCell text,
                              XSSFCommentsShim.CommentData comment) {
        cell(cellRef, text.getText(), comment);
    }

    default void headerFooter(String text, boolean isHeader, String tagName) {
    }

    default void endSheet() {
    }
}
