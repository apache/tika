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
package org.apache.tika.parser.microsoft.ooxml.xwpf;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.openxml4j.opc.PackagePart;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.AbstractListManager.LevelTuple;
import org.apache.tika.parser.microsoft.ooxml.OOXMLWordAndPowerPointTextHandler;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * SAX-based parser for numbering.xml that replaces the XMLBeans-dependent
 * POI XWPFNumbering. This eliminates the need for ooxml-lite schema classes
 * in the SAX parsing chain.
 * <p>
 * Follows the same pattern as {@link XWPFStylesShim}.
 */
public class XWPFNumberingShim {

    public static final XWPFNumberingShim EMPTY = new EmptyNumberingShim();

    // abstractNumId -> list of LevelTuples (indexed by ilvl)
    private final Map<Integer, LevelTuple[]> abstractNumLevels = new HashMap<>();
    // numId -> abstractNumId
    private final Map<Integer, Integer> numToAbstractNum = new HashMap<>();
    // numId -> override LevelTuples (indexed by ilvl), null entries for non-overridden levels
    private final Map<Integer, Map<Integer, LevelTuple>> overrideLevels = new HashMap<>();

    private XWPFNumberingShim() {
    }

    public XWPFNumberingShim(PackagePart part, ParseContext parseContext)
            throws IOException, TikaException, SAXException {
        try (InputStream is = part.getInputStream()) {
            XMLReaderUtils.parseSAX(is, new NumberingHandler(), parseContext);
        }
    }

    /**
     * @return the abstractNumId for the given numId, or -1 if not found
     */
    public int getAbstractNumId(int numId) {
        Integer id = numToAbstractNum.get(numId);
        return id != null ? id : -1;
    }

    /**
     * @return the level tuples for the given abstractNumId, or null if not found
     */
    public LevelTuple[] getAbstractNumLevels(int abstractNumId) {
        return abstractNumLevels.get(abstractNumId);
    }

    /**
     * Build override level tuples array for a given numId with the specified length.
     * Returns null if there are no overrides for this numId.
     */
    public LevelTuple[] getOverrideLevels(int numId, int length) {
        Map<Integer, LevelTuple> overrides = overrideLevels.get(numId);
        if (overrides == null || overrides.isEmpty()) {
            return null;
        }
        LevelTuple[] result = new LevelTuple[length];
        for (int i = 0; i < length; i++) {
            LevelTuple override = overrides.get(i);
            if (override != null) {
                result[i] = override;
            } else {
                result[i] = new LevelTuple("%" + i + ".");
            }
        }
        return result;
    }

    private static class EmptyNumberingShim extends XWPFNumberingShim {
        @Override
        public int getAbstractNumId(int numId) {
            return -1;
        }

        @Override
        public LevelTuple[] getAbstractNumLevels(int abstractNumId) {
            return null;
        }

        @Override
        public LevelTuple[] getOverrideLevels(int numId, int length) {
            return null;
        }
    }

    private class NumberingHandler extends DefaultHandler {

        private static final String W_NS = OOXMLWordAndPowerPointTextHandler.W_NS;

        // Current context
        private boolean inAbstractNum = false;
        private int currentAbstractNumId = -1;
        private boolean inNum = false;
        private int currentNumId = -1;
        private boolean inLvl = false;
        private boolean inLvlOverride = false;
        private int currentIlvl = -1;

        // Level accumulators (reset for each lvl element)
        private int lvlStart = -1;
        private int lvlRestart = -1;
        private String lvlText = null;
        private String lvlNumFmt = null;
        private boolean lvlIsLegal = false;

        // Collecting levels for current abstractNum
        private final Map<Integer, LevelTuple> currentAbstractLevels = new HashMap<>();
        // Collecting overrides for current num
        private final Map<Integer, LevelTuple> currentOverrides = new HashMap<>();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            if (!W_NS.equals(uri)) {
                return;
            }

            switch (localName) {
                case "abstractNum":
                    inAbstractNum = true;
                    currentAbstractNumId = getIntAttr(atts, W_NS, "abstractNumId", -1);
                    currentAbstractLevels.clear();
                    break;
                case "num":
                    inNum = true;
                    currentNumId = getIntAttr(atts, W_NS, "numId", -1);
                    currentOverrides.clear();
                    break;
                case "lvlOverride":
                    if (inNum) {
                        inLvlOverride = true;
                        currentIlvl = getIntAttr(atts, W_NS, "ilvl", -1);
                    }
                    break;
                case "lvl":
                    inLvl = true;
                    currentIlvl = getIntAttr(atts, W_NS, "ilvl", -1);
                    // Reset accumulators
                    lvlStart = -1;
                    lvlRestart = -1;
                    lvlText = null;
                    lvlNumFmt = null;
                    lvlIsLegal = false;
                    break;
                case "start":
                    if (inLvl) {
                        lvlStart = getIntAttr(atts, W_NS, "val", -1);
                    }
                    break;
                case "numFmt":
                    if (inLvl) {
                        lvlNumFmt = atts.getValue(W_NS, "val");
                    }
                    break;
                case "lvlText":
                    if (inLvl) {
                        lvlText = atts.getValue(W_NS, "val");
                    }
                    break;
                case "lvlRestart":
                    if (inLvl) {
                        lvlRestart = getIntAttr(atts, W_NS, "val", -1);
                    }
                    break;
                case "isLgl":
                    if (inLvl) {
                        lvlIsLegal = true;
                    }
                    break;
                case "abstractNumId":
                    if (inNum && !inLvl) {
                        int absId = getIntAttr(atts, W_NS, "val", -1);
                        if (currentNumId >= 0 && absId >= 0) {
                            numToAbstractNum.put(currentNumId, absId);
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (!W_NS.equals(uri)) {
                return;
            }
            switch (localName) {
                case "lvl":
                    if (inLvl && currentIlvl >= 0) {
                        LevelTuple tuple = buildLevelTuple(currentIlvl);
                        if (inLvlOverride && inNum) {
                            currentOverrides.put(currentIlvl, tuple);
                        } else if (inAbstractNum) {
                            currentAbstractLevels.put(currentIlvl, tuple);
                        }
                    }
                    inLvl = false;
                    break;
                case "lvlOverride":
                    inLvlOverride = false;
                    break;
                case "abstractNum":
                    if (inAbstractNum && currentAbstractNumId >= 0 &&
                            !currentAbstractLevels.isEmpty()) {
                        int maxLevel = currentAbstractLevels.keySet().stream()
                                .mapToInt(Integer::intValue).max().orElse(-1);
                        LevelTuple[] levels = new LevelTuple[maxLevel + 1];
                        for (int i = 0; i <= maxLevel; i++) {
                            LevelTuple t = currentAbstractLevels.get(i);
                            levels[i] = t != null ? t : new LevelTuple("%" + i + ".");
                        }
                        abstractNumLevels.put(currentAbstractNumId, levels);
                    }
                    inAbstractNum = false;
                    currentAbstractNumId = -1;
                    break;
                case "num":
                    if (inNum && currentNumId >= 0 && !currentOverrides.isEmpty()) {
                        overrideLevels.put(currentNumId, new HashMap<>(currentOverrides));
                    }
                    inNum = false;
                    currentNumId = -1;
                    break;
                default:
                    break;
            }
        }

        private LevelTuple buildLevelTuple(int level) {
            int start = lvlStart;
            int restart = lvlRestart;
            String text = lvlText != null ? lvlText : "%" + level + ".";
            String numFmt = lvlNumFmt != null ? lvlNumFmt : "decimal";

            if (start < 0) {
                // Same hack as XWPFListManager.buildTuple
                if ("decimal".equals(numFmt) || "ordinal".equals(numFmt) ||
                        "decimalZero".equals(numFmt)) {
                    start = 0;
                } else {
                    start = 1;
                }
            }
            return new LevelTuple(start, restart, text, numFmt, lvlIsLegal);
        }

        private int getIntAttr(Attributes atts, String ns, String localName, int defaultVal) {
            String val = atts.getValue(ns, localName);
            if (val == null) {
                return defaultVal;
            }
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }
    }
}
