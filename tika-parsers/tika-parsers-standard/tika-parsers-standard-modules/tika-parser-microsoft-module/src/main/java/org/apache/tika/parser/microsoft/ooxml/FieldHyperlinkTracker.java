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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks field hyperlink state across multiple runs within a paragraph.
 * Field codes span multiple runs: begin -> instrText -> separate -> text runs -> end
 * <p>
 * This class handles HYPERLINK field codes as well as other external references
 * like INCLUDEPICTURE, INCLUDETEXT, IMPORT, and LINK.
 */
class FieldHyperlinkTracker {

    // Patterns for extracting URLs from field codes
    private static final Pattern HYPERLINK_PATTERN =
            Pattern.compile("HYPERLINK\\s{1,100}\"([^\"]{1,10000})\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern INCLUDEPICTURE_PATTERN =
            Pattern.compile("INCLUDEPICTURE\\s{1,100}\"([^\"]{1,10000})\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern INCLUDETEXT_PATTERN =
            Pattern.compile("INCLUDETEXT\\s{1,100}\"([^\"]{1,10000})\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("IMPORT\\s{1,100}\"([^\"]{1,10000})\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_PATTERN =
            Pattern.compile("LINK\\s{1,100}[\\w.]{1,50}\\s{1,100}\"([^\"]{1,10000})\"",
                    Pattern.CASE_INSENSITIVE);

    private boolean inField = false;
    private boolean inFieldHyperlink = false;
    private final StringBuilder instrTextBuffer = new StringBuilder();
    private String lastExternalRefType = null;
    private String lastExternalRefUrl = null;

    void startField() {
        inField = true;
        instrTextBuffer.setLength(0);
        lastExternalRefType = null;
        lastExternalRefUrl = null;
    }

    void addInstrText(String text) {
        if (inField && text != null) {
            instrTextBuffer.append(text);
        }
    }

    /**
     * Called when fldChar separate is encountered.
     *
     * @return the hyperlink URL if this is a HYPERLINK field, null otherwise
     */
    String separate() {
        if (inField) {
            String url = parseHyperlinkFromInstrText(instrTextBuffer.toString());
            if (url != null) {
                inFieldHyperlink = true;
                return url;
            }
            // Check for other external refs (INCLUDEPICTURE, INCLUDETEXT, IMPORT, LINK)
            StringBuilder fieldType = new StringBuilder();
            String extUrl = parseExternalRefFromInstrText(instrTextBuffer.toString(), fieldType);
            if (extUrl != null) {
                lastExternalRefType = fieldType.toString();
                lastExternalRefUrl = extUrl;
            }
        }
        return null;
    }

    void endField() {
        inField = false;
        inFieldHyperlink = false;
        instrTextBuffer.setLength(0);
        lastExternalRefType = null;
        lastExternalRefUrl = null;
    }

    boolean isInFieldHyperlink() {
        return inFieldHyperlink;
    }

    String getLastExternalRefType() {
        return lastExternalRefType;
    }

    String getLastExternalRefUrl() {
        return lastExternalRefUrl;
    }

    void clearExternalRef() {
        lastExternalRefType = null;
        lastExternalRefUrl = null;
    }

    /**
     * Parses a HYPERLINK URL from instrText field code content.
     *
     * @param instrText the accumulated instrText content
     * @return the URL if found, or null
     */
    private static String parseHyperlinkFromInstrText(String instrText) {
        if (instrText == null || instrText.isEmpty()) {
            return null;
        }
        Matcher m = HYPERLINK_PATTERN.matcher(instrText.trim());
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Parses external reference URLs from instrText field codes
     * (INCLUDEPICTURE, INCLUDETEXT, IMPORT, LINK).
     *
     * @param instrText the accumulated instrText content
     * @param fieldType output parameter - will contain the field type if found
     * @return the URL if found, or null
     */
    private static String parseExternalRefFromInstrText(String instrText, StringBuilder fieldType) {
        if (instrText == null || instrText.isEmpty()) {
            return null;
        }
        String trimmed = instrText.trim();

        Matcher m = INCLUDEPICTURE_PATTERN.matcher(trimmed);
        if (m.find()) {
            fieldType.append("INCLUDEPICTURE");
            return m.group(1);
        }

        m = INCLUDETEXT_PATTERN.matcher(trimmed);
        if (m.find()) {
            fieldType.append("INCLUDETEXT");
            return m.group(1);
        }

        m = IMPORT_PATTERN.matcher(trimmed);
        if (m.find()) {
            fieldType.append("IMPORT");
            return m.group(1);
        }

        m = LINK_PATTERN.matcher(trimmed);
        if (m.find()) {
            fieldType.append("LINK");
            return m.group(1);
        }

        return null;
    }
}
