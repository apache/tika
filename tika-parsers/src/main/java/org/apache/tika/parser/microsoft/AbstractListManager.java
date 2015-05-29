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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.hwpf.converter.NumberFormatter;

public abstract class AbstractListManager {
    private final static String BULLET = "\u00b7";

    protected Map<Integer, ParagraphLevelCounter> listLevelMap = new HashMap<Integer, ParagraphLevelCounter>();
    protected Map<Integer, LevelTuple[]> overrideTupleMap = new HashMap<Integer, LevelTuple[]>();

    //helper class that is docx/doc format agnostic
    protected class ParagraphLevelCounter {

        //counts can == 0 if the format is decimal, make sure
        //that flag values are < 0
        private final Integer NOT_SEEN_YET = -1;
        private final Integer FIRST_SKIPPED = -2;
        private final LevelTuple[] levelTuples;
        Pattern LEVEL_INTERPOLATOR = Pattern.compile("%(\\d+)");
        private List<Integer> counts = new ArrayList<Integer>();
        private int lastLevel = -1;

        public ParagraphLevelCounter(LevelTuple[] levelTuples) {
            this.levelTuples = levelTuples;
        }

        public int getNumberOfLevels() {
            return levelTuples.length;
        }

        /**
         * Apply this to every numbered paragraph in order.
         *
         * @param levelNumber level number that is being incremented
         * @return the new formatted number string for this level
         */
        public String incrementLevel(int levelNumber, LevelTuple[] overrideLevelTuples) {

            for (int i = lastLevel + 1; i < levelNumber; i++) {
                if (i >= counts.size()) {
                    int val = getStart(i, overrideLevelTuples);
                    counts.add(i, val);
                } else {
                    int count = counts.get(i);
                    if (count == NOT_SEEN_YET) {
                        count = getStart(i, overrideLevelTuples);
                        counts.set(i, count);
                    }
                }
            }

            if (levelNumber < counts.size()) {
                resetAfter(levelNumber, overrideLevelTuples);
                int count = counts.get(levelNumber);
                if (count == NOT_SEEN_YET) {
                    count = getStart(levelNumber, overrideLevelTuples);
                } else {
                    count++;
                }
                counts.set(levelNumber, count);
                lastLevel = levelNumber;
                return format(levelNumber, overrideLevelTuples);
            }

            counts.add(levelNumber, getStart(levelNumber, overrideLevelTuples));
            lastLevel = levelNumber;
            return format(levelNumber, overrideLevelTuples);
        }

        /**
         * @param level which level to format
         * @return the string that represents the number and the surrounding text for this paragraph
         */
        private String format(int level, LevelTuple[] overrideLevelTuples) {
            if (level < 0 || level >= levelTuples.length) {
                //log?
                return "";
            }
            boolean isLegal = (overrideLevelTuples != null) ? overrideLevelTuples[level].isLegal : levelTuples[level].isLegal;
            //short circuit bullet
            String numFmt = getNumFormat(level, isLegal, overrideLevelTuples);
            if ("bullet".equals(numFmt)) {
                return BULLET + " ";
            }

            String lvlText = (overrideLevelTuples == null || overrideLevelTuples[level].lvlText == null) ?
                    levelTuples[level].lvlText : overrideLevelTuples[level].lvlText;
            StringBuilder sb = new StringBuilder();
            Matcher m = LEVEL_INTERPOLATOR.matcher(lvlText);
            int last = 0;
            while (m.find()) {
                sb.append(lvlText.substring(last, m.start()));
                String lvlString = m.group(1);
                int lvlNum = -1;
                try {
                    lvlNum = Integer.parseInt(lvlString);
                } catch (NumberFormatException e) {
                    //swallow
                }
                String numString = "";
                //need to subtract 1 because, e.g. %1 is the format
                //for the number at array offset 0
                numString = formatNum(lvlNum - 1, isLegal, overrideLevelTuples);

                sb.append(numString);
                last = m.end();
            }
            sb.append(lvlText.substring(last));
            if (sb.length() > 0) {
                //TODO: add in character after number
                sb.append(" ");
            }
            return sb.toString();
        }

        //actual level number
        private String formatNum(int lvlNum, boolean isLegal, LevelTuple[] overrideLevelTuples) {

            int numFmtStyle = 0;
            String numFmt = getNumFormat(lvlNum, isLegal, overrideLevelTuples);

            int count = getCount(lvlNum);
            if (count < 0) {
                count = 1;
            }
            if ("lowerLetter".equals(numFmt)) {
                numFmtStyle = 4;
            } else if ("lowerRoman".equals(numFmt)) {
                numFmtStyle = 2;
            } else if ("decimal".equals(numFmt)) {
                numFmtStyle = 0;
            } else if ("upperLetter".equals(numFmt)) {
                numFmtStyle = 3;
            } else if ("upperRoman".equals(numFmt)) {
                numFmtStyle = 1;
            } else if ("bullet".equals(numFmt)) {
                return "";
                //not yet handled by NumberFormatter...TODO: add to NumberFormatter?
            } else if ("ordinal".equals(numFmt)) {
                return ordinalize(count);
            } else if ("decimalZero".equals(numFmt)) {
                return "0" + NumberFormatter.getNumber(count, 0);
            } else if ("none".equals(numFmt)) {
                return "";
            }
            return NumberFormatter.getNumber(count, numFmtStyle);
        }

        private String ordinalize(int count) {
            //this is only good for locale == English
            String countString = Integer.toString(count);
            if (countString.endsWith("1")) {
                return countString + "st";
            } else if (countString.endsWith("2")) {
                return countString + "nd";
            } else if (countString.endsWith("3")) {
                return countString + "rd";
            }
            return countString + "th";
        }

        private String getNumFormat(int lvlNum, boolean isLegal, LevelTuple[] overrideLevelTuples) {
            if (lvlNum < 0 || lvlNum >= levelTuples.length) {
                //log?
                return "decimal";
            }
            if (isLegal) {
                //return decimal no matter the level if isLegal is true
                return "decimal";
            }
            return (overrideLevelTuples == null || overrideLevelTuples[lvlNum].numFmt == null) ?
                    levelTuples[lvlNum].numFmt : overrideLevelTuples[lvlNum].numFmt;
        }

        private int getCount(int lvlNum) {
            if (lvlNum < 0 || lvlNum >= counts.size()) {
                //log?
                return 1;
            }
            return counts.get(lvlNum);
        }

        private void resetAfter(int startlevelNumber, LevelTuple[] overrideLevelTuples) {
            for (int levelNumber = startlevelNumber + 1; levelNumber < counts.size(); levelNumber++) {
                int cnt = counts.get(levelNumber);
                if (cnt == NOT_SEEN_YET) {
                    //do nothing
                } else if (cnt == FIRST_SKIPPED) {
                    //do nothing
                } else if (levelTuples.length > levelNumber) {
                    //never reset if restarts == 0
                    int restart = (overrideLevelTuples == null || overrideLevelTuples[levelNumber].restart < 0) ?
                            levelTuples[levelNumber].restart : overrideLevelTuples[levelNumber].restart;
                    if (restart == 0) {
                        return;
                    } else if (restart == -1 ||
                            startlevelNumber <= restart - 1) {
                        counts.set(levelNumber, NOT_SEEN_YET);
                    } else {
                        //do nothing/don't reset
                    }
                } else {
                    //reset!
                    counts.set(levelNumber, NOT_SEEN_YET);
                }
            }
        }

        private int getStart(int levelNumber, LevelTuple[] overrideLevelTuples) {
            if (levelNumber >= levelTuples.length) {
                return 1;
            } else {
                return (overrideLevelTuples == null || overrideLevelTuples[levelNumber].start < 0) ?
                        levelTuples[levelNumber].start : overrideLevelTuples[levelNumber].start;
            }
        }
    }

    protected class LevelTuple {
        private final int start;
        private final int restart;
        private final String lvlText;
        private final String numFmt;
        private final boolean isLegal;

        public LevelTuple(String lvlText) {
            this.lvlText = lvlText;
            start = 1;
            restart = -1;
            numFmt = "decimal";
            isLegal = false;
        }

        public LevelTuple(int start, int restart, String lvlText, String numFmt, boolean isLegal) {
            this.start = start;
            this.restart = restart;
            this.lvlText = lvlText;
            this.numFmt = numFmt;
            this.isLegal = isLegal;
        }
    }
}
