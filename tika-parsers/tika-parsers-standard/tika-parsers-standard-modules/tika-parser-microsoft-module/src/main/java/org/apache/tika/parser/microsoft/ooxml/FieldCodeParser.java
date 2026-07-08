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
 * Parses OOXML field codes (instrText) to extract URLs from HYPERLINK,
 * INCLUDEPICTURE, INCLUDETEXT, IMPORT, and LINK fields.
 * <p>
 * This class has no Tika dependencies and could be contributed to POI.
 */
public class FieldCodeParser {

    private static final Pattern HYPERLINK_PATTERN =
            Pattern.compile("HYPERLINK\\s{1,100}\"([^\"]{1,10000})\"",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern INCLUDEPICTURE_PATTERN =
            Pattern.compile("INCLUDEPICTURE\\s{1,100}\"([^\"]{1,10000})\"",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern INCLUDETEXT_PATTERN =
            Pattern.compile("INCLUDETEXT\\s{1,100}\"([^\"]{1,10000})\"",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("IMPORT\\s{1,100}\"([^\"]{1,10000})\"",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_PATTERN =
            Pattern.compile(
                    "LINK\\s{1,100}[\\w.]{1,50}\\s{1,100}\"([^\"]{1,10000})\"",
                    Pattern.CASE_INSENSITIVE);

    private FieldCodeParser() {
    }

    /**
     * Parses a HYPERLINK URL from instrText field code content.
     * Field codes like: {@code HYPERLINK "https://example.com"}
     *
     * @param instrText the accumulated instrText content
     * @return the URL if found, or null
     */
    public static String parseHyperlinkFromInstrText(String instrText) {
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
     * Parses URLs from instrText field codes that reference external resources.
     * This includes INCLUDEPICTURE, INCLUDETEXT, IMPORT, and LINK fields.
     *
     * @param instrText the accumulated instrText content
     * @param fieldType output parameter - will contain the field type if found
     * @return the URL if found, or null
     */
    public static String parseExternalRefFromInstrText(String instrText,
            StringBuilder fieldType) {
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
