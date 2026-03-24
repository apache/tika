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

import java.math.BigInteger;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import org.apache.tika.parser.microsoft.AbstractListManager;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFNumberingShim;


public class XWPFListManager extends AbstractListManager {

    /**
     * Empty singleton to be used when there is no list manager.
     * Always returns empty string.
     */
    public final static XWPFListManager EMPTY_LIST = new EmptyListManager();

    private final XWPFNumberingShim numbering;

    public XWPFListManager(XWPFNumberingShim numbering) {
        this.numbering = numbering;
    }

    /**
     * @param paragraph paragraph
     * @return the formatted number or an empty string if something went wrong
     */
    public String getFormattedNumber(final XWPFParagraph paragraph) {
        return getFormattedNumber(paragraph.getNumID(),
                paragraph.getNumIlvl() == null ? -1 : paragraph.getNumIlvl().intValue());
    }

    public String getFormattedNumber(BigInteger numId, int iLvl) {
        if (numbering == null || iLvl < 0 || numId == null) {
            return "";
        }

        int currNumId = numId.intValue();
        int currAbNumId = numbering.getAbstractNumId(currNumId);
        if (currAbNumId < 0) {
            return "";
        }

        ParagraphLevelCounter lc = listLevelMap.get(currAbNumId);
        LevelTuple[] overrideTuples = overrideTupleMap.get(currNumId);
        if (lc == null) {
            lc = loadLevelTuples(currAbNumId);
            if (lc == null) {
                return "";
            }
        }
        if (overrideTuples == null) {
            overrideTuples = numbering.getOverrideLevels(currNumId, lc.getNumberOfLevels());
        }

        String formattedString = lc.incrementLevel(iLvl, overrideTuples);

        listLevelMap.put(currAbNumId, lc);
        overrideTupleMap.put(currNumId, overrideTuples);

        return formattedString;

    }

    private ParagraphLevelCounter loadLevelTuples(int abstractNumId) {
        LevelTuple[] levels = numbering.getAbstractNumLevels(abstractNumId);
        if (levels == null) {
            return null;
        }
        return new ParagraphLevelCounter(levels);
    }

    private static class EmptyListManager extends XWPFListManager {
        EmptyListManager() {
            super(null);
        }

        @Override
        public String getFormattedNumber(XWPFParagraph paragraph) {
            return "";
        }

        @Override
        public String getFormattedNumber(BigInteger numId, int iLvl) {
            return "";
        }

    }
}
