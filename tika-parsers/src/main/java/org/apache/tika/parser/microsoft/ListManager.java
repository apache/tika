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

import java.util.NoSuchElementException;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.model.ListData;
import org.apache.poi.hwpf.model.ListFormatOverrideLevel;
import org.apache.poi.hwpf.model.ListLevel;
import org.apache.poi.hwpf.model.ListTables;
import org.apache.poi.hwpf.usermodel.Paragraph;

/**
 * Computes the number text which goes at the beginning of each list paragraph
 * <p/>
 * <p><em>Note:</em> This class only handles the raw number text and does not apply any further formatting as described in [MS-DOC], v20140721, 2.4.6.3, Part 3 to it.<p>
 * <p><em>Note 2:</em> The {@code tplc}, a visual override for the appearance of list levels, as defined in [MS-DOC], v20140721, 2.9.328 is not taken care of in this class.</p>
 * <p>Further, this class does not yet handle overrides</p>
 */
public class ListManager extends AbstractListManager {
    private final ListTables listTables;

    /**
     * Ordinary constructor for a new list reader
     *
     * @param document Document to process
     */
    public ListManager(final HWPFDocument document) {
        this.listTables = document.getListTables();
    }

    /**
     * Get the formatted number for a given paragraph
     * <p/>
     * <p><em>Note:</em> This only works correctly if called subsequently for <em>all</em> paragraphs in a valid selection (main document, text field, ...) which are part of a list.</p>
     *
     * @param paragraph list paragraph to process
     * @return String which represents the numbering of this list paragraph; never {@code null}, can be empty string, though, 
     *        if something goes wrong in getList()
     * @throws IllegalArgumentException If the given paragraph is {@code null} or is not part of a list
     */
    public String getFormattedNumber(final Paragraph paragraph) {
        if (paragraph == null) throw new IllegalArgumentException("Given paragraph cannot be null.");
        if (!paragraph.isInList()) throw new IllegalArgumentException("Can only process list paragraphs.");
        //lsid is equivalent to docx's abnum
        //ilfo is equivalent to docx's num
        int currAbNumId = -1;
        try{
            currAbNumId = paragraph.getList().getLsid();
        } catch (NoSuchElementException e) {
            //somewhat frequent exception when initializing HWPFList
            return "";
        } catch (IllegalArgumentException e) {
            return "";
        } catch (NullPointerException e) {
            return "";
        }

        int currNumId = paragraph.getIlfo();
        ParagraphLevelCounter lc = listLevelMap.get(currAbNumId);
        LevelTuple[] overrideTuples = overrideTupleMap.get(currNumId);

        if (lc == null) {
            ListData listData = listTables.getListData(paragraph.getList().getLsid());
            if (listData == null) {
                //silently skip
                return "";
            }
            LevelTuple[] levelTuples = new LevelTuple[listData.getLevels().length];
            for (int i = 0; i < listData.getLevels().length; i++) {
                levelTuples[i] = buildTuple(i, listData.getLevels()[i]);
            }
            lc = new ParagraphLevelCounter(levelTuples);
        }
        if (overrideTuples == null) {
            overrideTuples = buildOverrideTuples(paragraph, lc.getNumberOfLevels());
        }
        String formattedString = lc.incrementLevel(paragraph.getIlvl(), overrideTuples);

        listLevelMap.put(currAbNumId, lc);
        overrideTupleMap.put(currNumId, overrideTuples);
        return formattedString;
    }

    private LevelTuple buildTuple(int i, ListLevel listLevel) {
        boolean isLegal = false;
        int start = 1;
        int restart = -1;
        String lvlText = "%" + i + ".";
        String numFmt = "decimal";

        start = listLevel.getStartAt();
        restart = listLevel.getRestart();
        isLegal = listLevel.isLegalNumbering();
        numFmt = convertToNewNumFormat(listLevel.getNumberFormat());
        lvlText = convertToNewNumberText(listLevel.getNumberText(), listLevel.getLevelNumberingPlaceholderOffsets());
        return new LevelTuple(start, restart, lvlText, numFmt, isLegal);
    }

    private LevelTuple[] buildOverrideTuples(Paragraph par, int length) {
        ListFormatOverrideLevel overrideLevel;
        // find the override for this level
        if (listTables.getLfoData(par.getIlfo()).getRgLfoLvl().length == 0) {
            return null;
        }
        overrideLevel = listTables.getLfoData(par.getIlfo()).getRgLfoLvl()[0];
        if (overrideLevel == null) {
            return null;
        }
        LevelTuple[] levelTuples = new LevelTuple[length];
        ListLevel listLevel = overrideLevel.getLevel();
        if (listLevel == null) {
            return null;
        }
        for (int i = 0; i < length; i++) {
            levelTuples[i] = buildTuple(i, listLevel);
        }

        return levelTuples;

    }

    private String convertToNewNumberText(String numberText, byte[] numberOffsets) {

        StringBuilder sb = new StringBuilder();
        int last = 0;
        for (int i = 0; i < numberOffsets.length; i++) {
            int offset = (int) numberOffsets[i];

            if (offset == 0) {
                break;
            }
            if (offset-1 < last || offset > numberText.length()) {
                //something went wrong.
                //silently stop
                break;
            }
            sb.append(numberText.substring(last, offset - 1));
            //need to add one because newer format
            //adds one.  In .doc, this was the array index;
            //but in .docx, this is the level number
            int lvlNum = (int) numberText.charAt(offset - 1) + 1;
            sb.append("%" + lvlNum);
            last = offset;
        }
        if (last < numberText.length()) {
            sb.append(numberText.substring(last));
        }
        return sb.toString();
    }

    private String convertToNewNumFormat(int numberFormat) {
        switch (numberFormat) {
            case -1:
                return "none";
            case 0:
                return "decimal";
            case 1:
                return "upperRoman";
            case 2:
                return "lowerRoman";
            case 3:
                return "upperLetter";
            case 4:
                return "lowerLetter";
            case 5:
                return "ordinal";
            case 22:
                return "decimalZero";
            case 23:
                return "bullet";
            case 47:
                return "none";
            default:
                //do we really want to silently swallow these uncovered cases?
                //throw new RuntimeException("NOT COVERED: " + numberFormat);
                return "decimal";
        }
    }
}
