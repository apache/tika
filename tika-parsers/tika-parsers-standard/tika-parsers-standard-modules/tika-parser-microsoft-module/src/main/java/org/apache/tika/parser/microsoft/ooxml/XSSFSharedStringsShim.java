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
 * it has formatted runs or a phonetic reading and some cell style's font is
 * superscript or subscript.
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
        SharedStringsHandler handler =
                new SharedStringsHandler(includePhoneticRuns, cellFontsMayBeScript);
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

    private static class SharedStringsHandler extends RichTextHandler {

        final List<String> strings = new ArrayList<>();
        final Map<Integer, List<Run>> scriptedRuns = new HashMap<>();

        SharedStringsHandler(boolean includePhoneticRuns, boolean cellFontsMayBeScript) {
            super("si", includePhoneticRuns, cellFontsMayBeScript);
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) {
            if ((uri == null || NS.equals(uri)) && "sst".equals(localName)) {
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
            }
            super.startElement(uri, localName, qName, attributes);
        }

        @Override
        void entry(String text, List<Run> runs) {
            if (runs != null) {
                scriptedRuns.put(strings.size(), runs);
            }
            strings.add(text);
        }
    }

    /**
     * Reads rich text entries, {@code <si>} in the shared strings or {@code <is>} in an
     * inline string cell: each entry's text, and its runs when they can change the
     * output (see the class javadoc).
     */
    static class RichTextHandler extends DefaultHandler {

        static final String NS =
                "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

        private final String entryName;
        private final boolean includePhoneticRuns;
        private final boolean cellFontsMayBeScript;
        private final StringBuilder characters = new StringBuilder(64);
        // <r> and <rPh> as spans of characters; text between them is a direct <t>
        private final List<Span> spans = new ArrayList<>();
        private boolean inEntry;
        private boolean tIsOpen;
        private boolean inR;
        private boolean inRPh;
        private int spanStart;
        private boolean runHasRPr;
        private Tag runTag;
        private boolean entryHasRuns;
        private boolean entryHasScript;
        private String text;
        private List<Run> runs;

        RichTextHandler(String entryName, boolean includePhoneticRuns,
                        boolean cellFontsMayBeScript) {
            this.entryName = entryName;
            this.includePhoneticRuns = includePhoneticRuns;
            this.cellFontsMayBeScript = cellFontsMayBeScript;
        }

        /**
         * Called at the end of each entry.
         *
         * @param runs the entry's runs, or {@code null} if it doesn't keep them
         */
        void entry(String text, List<Run> runs) {
            this.text = text;
            this.runs = runs;
        }

        /**
         * @return the last entry's text
         */
        String getText() {
            return text;
        }

        /**
         * @return the last entry's runs, or {@code null} if it didn't keep them
         */
        List<Run> getRuns() {
            return runs;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) {
            if (uri != null && !NS.equals(uri)) {
                return;
            }
            if (entryName.equals(localName)) {
                inEntry = true;
                characters.setLength(0);
                spans.clear();
                entryHasRuns = false;
                entryHasScript = false;
                return;
            }
            if (!inEntry) {
                return;
            }
            switch (localName) {
                case "r":
                    inR = true;
                    spanStart = characters.length();
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
                    break;
                case "rPh":
                    inRPh = true;
                    spanStart = characters.length();
                    if (includePhoneticRuns && characters.length() > 0) {
                        characters.append(" ");
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if ((uri != null && !NS.equals(uri)) || !inEntry) {
                return;
            }
            if (entryName.equals(localName)) {
                inEntry = false;
                boolean keep = entryHasScript || (entryHasRuns && cellFontsMayBeScript);
                entry(characters.toString(), keep ? toRuns() : null);
                return;
            }
            switch (localName) {
                case "r":
                    // an empty run adds nothing; keeping it would cost every cell using the entry
                    if (inR && characters.length() > spanStart) {
                        spans.add(new Span(spanStart, characters.length(), runTag, runHasRPr));
                    }
                    inR = false;
                    break;
                case "t":
                    tIsOpen = false;
                    break;
                case "rPh":
                    inRPh = false;
                    if (characters.length() > spanStart) {
                        // the phonetic text, with its leading space, as plain text
                        spans.add(new Span(spanStart, characters.length(), null, true));
                        entryHasRuns = true;
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inEntry && tIsOpen && (!inRPh || includePhoneticRuns)) {
                characters.append(ch, start, length);
            }
        }

        private List<Run> toRuns() {
            List<Run> result = new ArrayList<>(spans.size() + 1);
            int pos = 0;
            for (Span span : spans) {
                // nested <r>/<rPh> give overlapping spans; the runs must partition the text
                if (span.end <= pos) {
                    continue;
                }
                int start = Math.max(span.start, pos);
                if (start > pos) {
                    // a <t> directly in the entry is in the cell's font
                    result.add(new Run(characters.substring(pos, start), null, false));
                }
                result.add(new Run(characters.substring(start, span.end), span.tag,
                        span.hasRPr));
                pos = span.end;
            }
            if (pos < characters.length()) {
                result.add(new Run(characters.substring(pos), null, false));
            }
            return result;
        }

        private record Span(int start, int end, Tag tag, boolean hasRPr) {
        }
    }
}
