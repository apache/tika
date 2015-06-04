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

import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNum;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.tika.parser.microsoft.AbstractListManager;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNum;


public class XWPFListManager extends AbstractListManager {
    private final static boolean OVERRIDE_AVAILABLE;
    private final static String SKIP_FORMAT = Character.toString((char) 61623);//if this shows up as the lvlText, don't show a number

    static {
        boolean b = false;
        try {
            Class.forName("org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumLvl");
            b = true;
        } catch (ClassNotFoundException e) {
        }
        b = OVERRIDE_AVAILABLE = false;

    }

    private final XWPFNumbering numbering;

    //map of numId (which paragraph series is this a member of?), levelcounts
    public XWPFListManager(XWPFDocument document) {
        numbering = document.getNumbering();
    }

    /**
     *
     * @param paragraph paragraph
     * @return the formatted number or an empty string if something went wrong
     */
    public String getFormattedNumber(final XWPFParagraph paragraph) {
        int currNumId = paragraph.getNumID().intValue();
        XWPFNum xwpfNum = numbering.getNum(paragraph.getNumID());
        if (xwpfNum == null) {
            return "";
        }
        CTNum ctNum = xwpfNum.getCTNum();
        CTDecimalNumber abNum = ctNum.getAbstractNumId();
        int currAbNumId = abNum.getVal().intValue();

        ParagraphLevelCounter lc = listLevelMap.get(currAbNumId);
        LevelTuple[] overrideTuples = overrideTupleMap.get(currNumId);
        if (lc == null) {
            lc = loadLevelTuples(abNum);
        }
        if (overrideTuples == null) {
            overrideTuples = loadOverrideTuples(ctNum, lc.getNumberOfLevels());
        }

        String formattedString = lc.incrementLevel(paragraph.getNumIlvl().intValue(), overrideTuples);

        listLevelMap.put(currAbNumId, lc);
        overrideTupleMap.put(currNumId, overrideTuples);

        return formattedString;
    }

    /**
     * WARNING: currently always returns null.
     * TODO: Once CTNumLvl is available to Tika,
     * we can turn this back on.
     *
     * @param ctNum  number on which to build the overrides
     * @param length length of intended array
     * @return null or an array of override tuples of length {@param length}
     */
    private LevelTuple[] loadOverrideTuples(CTNum ctNum, int length) {
        return null;
/*        LevelTuple[] levelTuples = new LevelTuple[length];
        int overrideLength = ctNum.sizeOfLvlOverrideArray();
        if (overrideLength == 0) {
            return null;
        }
        for (int i = 0; i < length; i++) {
            LevelTuple tuple;
            if (i >= overrideLength) {
                tuple = new LevelTuple("%"+i+".");
            } else {
                CTNumLvl ctNumLvl = ctNum.getLvlOverrideArray(i);
                if (ctNumLvl != null) {
                    tuple = buildTuple(i, ctNumLvl.getLvl());
                } else {
                    tuple = new LevelTuple("%"+i+".");
                }
            }
            levelTuples[i] = tuple;
        }
        return levelTuples;*/
    }


    private ParagraphLevelCounter loadLevelTuples(CTDecimalNumber abNum) {
        //Unfortunately, we need to go this far into the underlying structure
        //to get the abstract num information for the edge case where
        //someone skips a level and the format is not context-free, e.g. "1.B.i".
        XWPFAbstractNum abstractNum = numbering.getAbstractNum(abNum.getVal());
        CTAbstractNum ctAbstractNum = abstractNum.getCTAbstractNum();

        LevelTuple[] levels = new LevelTuple[ctAbstractNum.sizeOfLvlArray()];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = buildTuple(i, ctAbstractNum.getLvlArray(i));
        }
        return new ParagraphLevelCounter(levels);
    }

    private LevelTuple buildTuple(int level, CTLvl ctLvl) {
        boolean isLegal = false;
        int start = 1;
        int restart = -1;
        String lvlText = "%" + level + ".";
        String numFmt = "decimal";


        if (ctLvl != null && ctLvl.getIsLgl() != null) {
            isLegal = true;
        }

        if (ctLvl != null && ctLvl.getNumFmt() != null &&
                ctLvl.getNumFmt().getVal() != null) {
            numFmt = ctLvl.getNumFmt().getVal().toString();
        }
        if (ctLvl != null && ctLvl.getLvlRestart() != null &&
                ctLvl.getLvlRestart().getVal() != null) {
            restart = ctLvl.getLvlRestart().getVal().intValue();
        }
        if (ctLvl != null && ctLvl.getStart() != null &&
                ctLvl.getStart().getVal() != null) {
            start = ctLvl.getStart().getVal().intValue();
        } else {

            //this is a hack. Currently, this gets the lowest possible
            //start for a given numFmt.  We should probably try to grab the
            //restartNumberingAfterBreak value in
            //e.g. <w:abstractNum w:abstractNumId="12" w15:restartNumberingAfterBreak="0">???
            if ("decimal".equals(numFmt) || "ordinal".equals(numFmt) || "decimalZero".equals(numFmt)) {
                start = 0;
            } else {
                start = 1;
            }
        }
        if (ctLvl != null && ctLvl.getLvlText() != null && ctLvl.getLvlText().getVal() != null) {
            lvlText = ctLvl.getLvlText().getVal();
        }
        return new LevelTuple(start, restart, lvlText, numFmt, isLegal);
    }

}
