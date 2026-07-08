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
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * SAX-based shim that replaces POI's {@code ReadOnlySharedStringsTable}
 * for XLSX event-based parsing.
 * <p>
 * Parses {@code xl/sharedStrings.xml} and stores each shared string entry
 * as a plain {@code String}, avoiding the XMLBeans dependency that
 * {@code XSSFRichTextString} requires. Rich text runs within a single
 * {@code <si>} are concatenated into a single string.
 */
class XSSFSharedStringsShim {

    private final List<String> strings;
    private final boolean includePhoneticRuns;

    XSSFSharedStringsShim(InputStream sharedStringsData,
                           boolean includePhoneticRuns,
                           ParseContext parseContext)
            throws IOException, SAXException, TikaException {
        this.includePhoneticRuns = includePhoneticRuns;
        SharedStringsHandler handler = new SharedStringsHandler();
        if (sharedStringsData != null) {
            try {
                XMLReaderUtils.parseSAX(sharedStringsData, handler, parseContext);
            } finally {
                sharedStringsData.close();
            }
        }
        this.strings = handler.strings;
    }

    String getItemAt(int idx) {
        return strings.get(idx);
    }

    int getCount() {
        return strings.size();
    }

    private class SharedStringsHandler extends DefaultHandler {

        private static final String NS =
                "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

        final List<String> strings = new ArrayList<>();
        private StringBuilder characters;
        private boolean tIsOpen;
        private boolean inRPh;

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
                    break;
                case "t":
                    tIsOpen = true;
                    break;
                case "rPh":
                    inRPh = true;
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
                        strings.add(characters.toString());
                    }
                    break;
                case "t":
                    tIsOpen = false;
                    break;
                case "rPh":
                    inRPh = false;
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
                }
            }
        }
    }
}
