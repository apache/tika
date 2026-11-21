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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.FormattingUtils.Tag;
import org.apache.tika.parser.microsoft.ScriptedTextCell;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * SAX-based shim that replaces POI's {@code ReadOnlySharedStringsTable}
 * for XLSX event-based parsing.
 * <p>
 * Parses {@code xl/sharedStrings.xml} and stores each shared string entry
 * as a plain {@code String}, avoiding the XMLBeans dependency that
 * {@code XSSFRichTextString} requires. Rich text runs within a single
 * {@code <si>} are concatenated into a single string. An entry also keeps its runs,
 * for {@code <sup>}/{@code <sub>}, if one of them is superscript or subscript, or if
 * it has formatted runs and some cell style's font is superscript or subscript.
 */
class XSSFSharedStringsShim {

    /**
     * A rich text run; one with no {@code <rPr>} is in the cell's font.
     *
     * @param tag {@link Tag#SUP}, {@link Tag#SUB} or {@code null}
     */
    record Run(String text, Tag tag, boolean hasRPr) {
    }

    private final List<String> strings;
    private final Map<Integer, List<Run>> scriptedRuns;
    private final boolean includePhoneticRuns;
    private final boolean cellFontsMayBeScript;

    /**
     * @param cellFontsMayBeScript whether some cell style's font is superscript or
     *                             subscript; if not, only entries with a superscript
     *                             or subscript run keep their runs
     */
    XSSFSharedStringsShim(InputStream sharedStringsData,
                           boolean includePhoneticRuns,
                           boolean cellFontsMayBeScript,
                           ParseContext parseContext)
            throws IOException, SAXException, TikaException {
        this.includePhoneticRuns = includePhoneticRuns;
        this.cellFontsMayBeScript = cellFontsMayBeScript;
        SharedStringsHandler handler = new SharedStringsHandler();
        if (sharedStringsData != null) {
            try {
                XMLReaderUtils.parseSAX(sharedStringsData, handler, parseContext);
            } finally {
                sharedStringsData.close();
            }
        }
        this.strings = handler.strings;
        this.scriptedRuns = handler.scriptedRuns;
    }

    String getItemAt(int idx) {
        return strings.get(idx);
    }

    /**
     * @return the entry's runs, phonetic runs included as plain text if they are
     * concatenated, if it kept them (see the class javadoc); else {@code null}
     */
    List<Run> getScriptedRunsAt(int idx) {
        return scriptedRuns.get(idx);
    }

    int getCount() {
        return strings.size();
    }

    private class SharedStringsHandler extends DefaultHandler {

        private static final String NS =
                "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

        final List<String> strings = new ArrayList<>();
        final Map<Integer, List<Run>> scriptedRuns = new HashMap<>();
        private StringBuilder characters;
        private boolean tIsOpen;
        private boolean inRPh;
        private int tStart;
        private int rPhStart;
        private final List<Run> runs = new ArrayList<>();
        private final StringBuilder runText = new StringBuilder();
        private boolean inR;
        private boolean runHasRPr;
        private Tag runTag;
        private boolean entryHasRuns;
        private boolean entryHasScript;

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) {
            if (uri != null && !NS.equals(uri)) {
                return;
            }
            switch (localName) {
                case "sst":
                    String uniqueCount = attributes.getValue("uniqueCount");
                    if (uniqueCount != null) {
                        try {
                            int hint = (int) Long.parseLong(uniqueCount);
                            // guard against corrupt files with absurd counts
                            ((ArrayList<String>) strings).ensureCapacity(
                                    Math.min(hint, 100_000));
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                    characters = new StringBuilder(64);
                    break;
                case "si":
                    if (characters != null) {
                        characters.setLength(0);
                    }
                    runs.clear();
                    entryHasRuns = false;
                    entryHasScript = false;
                    break;
                case "r":
                    inR = true;
                    runText.setLength(0);
                    runHasRPr = false;
                    runTag = null;
                    break;
                case "rPr":
                    if (inR) {
                        runHasRPr = true;
                        entryHasRuns = true;
                    }
                    break;
                case "vertAlign":
                    if (inR) {
                        runTag = ScriptedTextCell.fromVertAlign(attributes.getValue("val"));
                        entryHasScript |= runTag != null;
                    }
                    break;
                case "t":
                    tIsOpen = true;
                    tStart = characters != null ? characters.length() : 0;
                    break;
                case "rPh":
                    inRPh = true;
                    rPhStart = characters != null ? characters.length() : 0;
                    if (includePhoneticRuns && characters != null &&
                            characters.length() > 0) {
                        characters.append(" ");
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (uri != null && !NS.equals(uri)) {
                return;
            }
            switch (localName) {
                case "si":
                    if (characters != null) {
                        if (entryHasScript || (entryHasRuns && cellFontsMayBeScript)) {
                            scriptedRuns.put(strings.size(), List.copyOf(runs));
                        }
                        strings.add(characters.toString());
                    }
                    break;
                case "r":
                    if (inR) {
                        runs.add(new Run(runText.toString(), runTag, runHasRPr));
                        inR = false;
                    }
                    break;
                case "t":
                    tIsOpen = false;
                    // a <t> directly in <si>, before any <r>, is in the cell's font
                    if (!inR && !inRPh && characters != null && characters.length() > tStart) {
                        runs.add(new Run(characters.substring(tStart), null, false));
                    }
                    break;
                case "rPh":
                    inRPh = false;
                    if (characters != null && characters.length() > rPhStart) {
                        // the phonetic text, with its leading space, as plain text
                        runs.add(new Run(characters.substring(rPhStart), null, true));
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (tIsOpen && characters != null) {
                if (inRPh) {
                    if (includePhoneticRuns) {
                        characters.append(ch, start, length);
                    }
                } else {
                    characters.append(ch, start, length);
                    if (inR) {
                        runText.append(ch, start, length);
                    }
                }
            }
        }
    }
}
