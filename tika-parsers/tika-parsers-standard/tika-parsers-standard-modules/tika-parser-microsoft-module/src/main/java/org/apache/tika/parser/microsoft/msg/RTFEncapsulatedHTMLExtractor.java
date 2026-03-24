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
package org.apache.tika.parser.microsoft.msg;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts the original HTML from an RTF document that contains encapsulated HTML
 * (as indicated by the {@code \fromhtml1} control word).
 *
 * <p>The encapsulated HTML format stores HTML in two places:</p>
 * <ol>
 *   <li>{@code {\*\htmltag<N> ...}} groups — contain the HTML markup (tags, style blocks, etc.)</li>
 *   <li>Text between htmltag groups — contains the actual text content, provided it is NOT
 *       wrapped in {@code \htmlrtf ... \htmlrtf0} (which marks RTF-only rendering hints)</li>
 * </ol>
 *
 * <p>Per the MS-OXRTFEX specification, {@code \'xx} hex escapes in inter-tag text are decoded
 * using the code page of the currently selected font ({@code \fN}).  The font-to-charset mapping
 * is built from the RTF font table's {@code \fcharsetN} declarations.  Inside
 * {@code {\*\htmltag}} groups, the document's default code page ({@code \ansicpgN}) is used.</p>
 */
public class RTFEncapsulatedHTMLExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RTFEncapsulatedHTMLExtractor.class);

    private static final String HTMLTAG_PREFIX = "{\\*\\htmltag";
    private static final String FROM_HTML_MARKER = "\\fromhtml";
    private static final String ANSICPG_PREFIX = "\\ansicpg";

    // Maps RTF \fcharset values to Java Charset objects.
    // Based on the Windows CharacterSet enumeration and Tika's TextExtractor.FCHARSET_MAP.
    private static final Map<Integer, Charset> FCHARSET_MAP = new HashMap<>();

    static {
        FCHARSET_MAP.put(0, Charset.forName("windows-1252"));   // ANSI
        FCHARSET_MAP.put(77, Charset.forName("MacRoman"));      // Mac Roman
        FCHARSET_MAP.put(128, Charset.forName("MS932"));         // Shift_JIS (Japanese)
        FCHARSET_MAP.put(129, Charset.forName("ms949"));         // Hangul (Korean)
        FCHARSET_MAP.put(130, charsetOrNull("x-Johab"));         // Johab (Korean)
        FCHARSET_MAP.put(134, Charset.forName("GBK"));           // GB2312 (Simplified Chinese)
        FCHARSET_MAP.put(136, Charset.forName("Big5"));          // Big5 (Traditional Chinese)
        FCHARSET_MAP.put(161, Charset.forName("windows-1253"));  // Greek
        FCHARSET_MAP.put(162, Charset.forName("windows-1254"));  // Turkish
        FCHARSET_MAP.put(163, Charset.forName("windows-1258"));  // Vietnamese
        FCHARSET_MAP.put(177, Charset.forName("windows-1255"));  // Hebrew
        FCHARSET_MAP.put(178, Charset.forName("windows-1256"));  // Arabic
        FCHARSET_MAP.put(186, Charset.forName("windows-1257"));  // Baltic
        FCHARSET_MAP.put(204, Charset.forName("windows-1251"));  // Russian
        FCHARSET_MAP.put(222, Charset.forName("ms874"));         // Thai
        FCHARSET_MAP.put(238, Charset.forName("windows-1250"));  // Eastern Europe
    }

    /**
     * Extracts the HTML content from an encapsulated-HTML RTF document.
     *
     * @param rtfBytes the decompressed RTF bytes
     * @return the extracted HTML string, or {@code null} if the RTF does not contain
     *         encapsulated HTML
     */
    public static String extract(byte[] rtfBytes) {
        if (rtfBytes == null || rtfBytes.length == 0) {
            return null;
        }
        // Work with US-ASCII — RTF is 7-bit and non-ASCII bytes are escaped as \'xx
        String rtf = new String(rtfBytes, StandardCharsets.US_ASCII);

        if (!rtf.contains(FROM_HTML_MARKER)) {
            return null;
        }

        Charset defaultCodePage = detectCodePage(rtf);
        Map<Integer, Charset> fontCharsets = parseFontTable(rtf);
        // Track the current font's charset for inter-tag text decoding
        Charset currentFontCharset = defaultCodePage;

        // Find the start of the document body (after the RTF header).
        int bodyStart = rtf.indexOf(HTMLTAG_PREFIX);
        if (bodyStart < 0) {
            return null;
        }

        StringBuilder html = new StringBuilder(rtf.length() / 2);
        ByteArrayOutputStream pendingBytes = new ByteArrayOutputStream();
        int pos = bodyStart;
        int len = rtf.length();
        boolean inHtmlRtfSkip = false;

        while (pos < len) {
            // Check if we're at an htmltag group
            if (rtf.startsWith(HTMLTAG_PREFIX, pos)) {
                flushPendingBytes(pendingBytes, html, currentFontCharset);

                // Find matching close brace
                int groupEnd = findMatchingBrace(rtf, pos);
                if (groupEnd < 0) {
                    break;
                }

                // Skip {\*\htmltag prefix and digit(s)
                int contentStart = pos + HTMLTAG_PREFIX.length();
                while (contentStart < groupEnd && Character.isDigit(rtf.charAt(contentStart))) {
                    contentStart++;
                }
                // Skip optional space after tag number
                if (contentStart < groupEnd && rtf.charAt(contentStart) == ' ') {
                    contentStart++;
                }

                // Decode the htmltag content using default code page per MS-OXRTFEX spec
                String inner = rtf.substring(contentStart, groupEnd);
                decodeRtfEscapes(inner, html, defaultCodePage);

                pos = groupEnd + 1;
                continue;
            }

            // Check for \htmlrtf control word (start or end of RTF-only block)
            if (rtf.startsWith("\\htmlrtf", pos)) {
                flushPendingBytes(pendingBytes, html, currentFontCharset);
                int afterWord = pos + "\\htmlrtf".length();

                if (afterWord < len && rtf.charAt(afterWord) == '0') {
                    // \htmlrtf0 — end of skip block
                    inHtmlRtfSkip = false;
                    afterWord++;
                    if (afterWord < len && rtf.charAt(afterWord) == ' ') {
                        afterWord++;
                    }
                } else {
                    // \htmlrtf — start of skip block
                    inHtmlRtfSkip = true;
                    if (afterWord < len && rtf.charAt(afterWord) == ' ') {
                        afterWord++;
                    }
                }
                pos = afterWord;
                continue;
            }

            // Inside \htmlrtf skip blocks: don't emit text, but DO track \fN font switches
            if (inHtmlRtfSkip) {
                if (rtf.charAt(pos) == '\\' && pos + 1 < len && rtf.charAt(pos + 1) == 'f'
                        && pos + 2 < len && Character.isDigit(rtf.charAt(pos + 2))) {
                    // Parse \fN control word
                    int numStart = pos + 2;
                    int numEnd = numStart;
                    while (numEnd < len && Character.isDigit(rtf.charAt(numEnd))) {
                        numEnd++;
                    }
                    // Make sure this is \f<digits> and not \fcharset, \fi, etc.
                    if (numEnd == numStart + (numEnd - numStart) &&
                            (numEnd >= len || !Character.isLetter(rtf.charAt(numEnd)))) {
                        int fontId = Integer.parseInt(rtf.substring(numStart, numEnd));
                        Charset fontCs = fontCharsets.get(fontId);
                        if (fontCs != null) {
                            currentFontCharset = fontCs;
                        }
                    }
                }
                pos++;
                continue;
            }

            // Check for other { groups (nested RTF groups that aren't htmltag)
            if (rtf.charAt(pos) == '{') {
                flushPendingBytes(pendingBytes, html, currentFontCharset);
                int end = findMatchingBrace(rtf, pos);
                if (end > 0) {
                    pos = end + 1;
                } else {
                    pos++;
                }
                continue;
            }

            // Skip closing braces
            if (rtf.charAt(pos) == '}') {
                flushPendingBytes(pendingBytes, html, currentFontCharset);
                pos++;
                continue;
            }

            // Handle RTF escapes in inter-tag text
            if (rtf.charAt(pos) == '\\' && pos + 1 < len) {
                char next = rtf.charAt(pos + 1);

                // \'xx hex escape — decode using current font's charset
                if (next == '\'' && pos + 3 < len) {
                    int hi = Character.digit(rtf.charAt(pos + 2), 16);
                    int lo = Character.digit(rtf.charAt(pos + 3), 16);
                    if (hi >= 0 && lo >= 0) {
                        pendingBytes.write((hi << 4) | lo);
                    }
                    pos += 4;
                    continue;
                }

                flushPendingBytes(pendingBytes, html, currentFontCharset);

                // Escaped literals
                if (next == '\\' || next == '{' || next == '}') {
                    html.append(next);
                    pos += 2;
                    continue;
                }

                // Control word
                if (Character.isLetter(next)) {
                    int wordStart = pos + 1;
                    int wordEnd = wordStart;
                    while (wordEnd < len && Character.isLetter(rtf.charAt(wordEnd))) {
                        wordEnd++;
                    }
                    String word = rtf.substring(wordStart, wordEnd);

                    // Parse optional numeric parameter
                    int paramStart = wordEnd;
                    int paramEnd = wordEnd;
                    if (paramEnd < len && (rtf.charAt(paramEnd) == '-'
                            || Character.isDigit(rtf.charAt(paramEnd)))) {
                        paramEnd++;
                        while (paramEnd < len && Character.isDigit(rtf.charAt(paramEnd))) {
                            paramEnd++;
                        }
                    }
                    // Skip optional space delimiter
                    int afterWord = paramEnd;
                    if (afterWord < len && rtf.charAt(afterWord) == ' ') {
                        afterWord++;
                    }

                    switch (word) {
                        case "par":
                        case "pard":
                            html.append('\n');
                            break;
                        case "tab":
                            html.append('\t');
                            break;
                        case "line":
                            html.append("<br>");
                            break;
                        case "f":
                            // Font switch in inter-tag text — update current charset
                            if (paramEnd > paramStart) {
                                int fontId = Integer.parseInt(
                                        rtf.substring(paramStart, paramEnd));
                                Charset fontCs = fontCharsets.get(fontId);
                                if (fontCs != null) {
                                    currentFontCharset = fontCs;
                                }
                            }
                            break;
                        default:
                            // Skip unknown control words
                            break;
                    }
                    pos = afterWord;
                    continue;
                }

                // Unknown escape — skip backslash
                pos++;
                continue;
            }

            // Newlines/carriage returns in RTF are whitespace, not content
            if (rtf.charAt(pos) == '\r' || rtf.charAt(pos) == '\n') {
                pos++;
                continue;
            }

            // Regular text character between htmltag groups — this is HTML content
            flushPendingBytes(pendingBytes, html, currentFontCharset);
            html.append(rtf.charAt(pos));
            pos++;
        }

        flushPendingBytes(pendingBytes, html, currentFontCharset);

        if (html.length() == 0) {
            return null;
        }
        return html.toString();
    }

    /**
     * Parse the RTF font table to build a mapping from font ID to charset.
     */
    static Map<Integer, Charset> parseFontTable(String rtf) {
        Map<Integer, Charset> result = new HashMap<>();
        int fontTblStart = rtf.indexOf("{\\fonttbl");
        if (fontTblStart < 0) {
            return result;
        }
        int fontTblEnd = findMatchingBrace(rtf, fontTblStart);
        if (fontTblEnd < 0) {
            return result;
        }
        String fontTable = rtf.substring(fontTblStart, fontTblEnd + 1);

        int currentFontId = -1;
        int pos = 0;
        int ftLen = fontTable.length();

        while (pos < ftLen) {
            if (fontTable.charAt(pos) == '\\' && pos + 1 < ftLen
                    && Character.isLetter(fontTable.charAt(pos + 1))) {
                int wordStart = pos + 1;
                int wordEnd = wordStart;
                while (wordEnd < ftLen && Character.isLetter(fontTable.charAt(wordEnd))) {
                    wordEnd++;
                }
                String word = fontTable.substring(wordStart, wordEnd);

                // Parse numeric parameter
                int paramStart = wordEnd;
                int paramEnd = wordEnd;
                if (paramEnd < ftLen && (fontTable.charAt(paramEnd) == '-'
                        || Character.isDigit(fontTable.charAt(paramEnd)))) {
                    paramEnd++;
                    while (paramEnd < ftLen && Character.isDigit(fontTable.charAt(paramEnd))) {
                        paramEnd++;
                    }
                }

                if ("f".equals(word) && paramEnd > paramStart) {
                    currentFontId = Integer.parseInt(fontTable.substring(paramStart, paramEnd));
                } else if ("fcharset".equals(word) && paramEnd > paramStart
                        && currentFontId >= 0) {
                    int fcharset = Integer.parseInt(fontTable.substring(paramStart, paramEnd));
                    Charset cs = FCHARSET_MAP.get(fcharset);
                    if (cs != null) {
                        result.put(currentFontId, cs);
                    }
                }
                pos = paramEnd;
            } else {
                pos++;
            }
        }
        return result;
    }

    /**
     * Find the position of the closing brace that matches the opening brace at
     * {@code openPos}.  Handles nested groups and escaped braces.
     *
     * @return index of the closing '}', or -1 if not found
     */
    static int findMatchingBrace(String rtf, int openPos) {
        int depth = 0;
        int len = rtf.length();
        for (int i = openPos; i < len; i++) {
            char c = rtf.charAt(i);
            if (c == '\\' && i + 1 < len) {
                char next = rtf.charAt(i + 1);
                if (next == '{' || next == '}' || next == '\\') {
                    i++;
                    continue;
                }
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Decode RTF escapes within an htmltag group's content.
     */
    static void decodeRtfEscapes(String content, StringBuilder out, Charset codePage) {
        int len = content.length();
        int i = 0;
        ByteArrayOutputStream pendingBytes = new ByteArrayOutputStream();

        while (i < len) {
            char c = content.charAt(i);

            if (c == '\\') {
                if (i + 1 >= len) {
                    break;
                }
                char next = content.charAt(i + 1);

                // \'xx hex escape
                if (next == '\'' && i + 3 < len) {
                    int hi = Character.digit(content.charAt(i + 2), 16);
                    int lo = Character.digit(content.charAt(i + 3), 16);
                    if (hi >= 0 && lo >= 0) {
                        pendingBytes.write((hi << 4) | lo);
                    }
                    i += 4;
                    continue;
                }

                flushPendingBytes(pendingBytes, out, codePage);

                if (next == '\\' || next == '{' || next == '}') {
                    out.append(next);
                    i += 2;
                    continue;
                }

                // Control words
                if (Character.isLetter(next)) {
                    int wordStart = i + 1;
                    int wordEnd = wordStart;
                    while (wordEnd < len && Character.isLetter(content.charAt(wordEnd))) {
                        wordEnd++;
                    }
                    String word = content.substring(wordStart, wordEnd);

                    int paramEnd = wordEnd;
                    if (paramEnd < len && (content.charAt(paramEnd) == '-'
                            || Character.isDigit(content.charAt(paramEnd)))) {
                        paramEnd++;
                        while (paramEnd < len && Character.isDigit(content.charAt(paramEnd))) {
                            paramEnd++;
                        }
                    }
                    int afterWord = paramEnd;
                    if (afterWord < len && content.charAt(afterWord) == ' ') {
                        afterWord++;
                    }

                    switch (word) {
                        case "par":
                        case "pard":
                            out.append('\n');
                            break;
                        case "tab":
                            out.append('\t');
                            break;
                        case "line":
                            out.append("<br>");
                            break;
                        case "htmlrtf":
                            // Skip \htmlrtf...\htmlrtf0 inside htmltag groups
                            i = skipHtmlRtfBlock(content, i);
                            continue;
                        default:
                            break;
                    }
                    i = afterWord;
                    continue;
                }

                i++;
                continue;
            }

            if (c == '{' || c == '}') {
                flushPendingBytes(pendingBytes, out, codePage);
                i++;
                continue;
            }

            flushPendingBytes(pendingBytes, out, codePage);
            out.append(c);
            i++;
        }

        flushPendingBytes(pendingBytes, out, codePage);
    }

    /**
     * Skip a {@code \htmlrtf ... \htmlrtf0} block within an htmltag group.
     *
     * @param content the string being parsed
     * @param pos     position of the backslash starting {@code \htmlrtf}
     * @return position after the matching {@code \htmlrtf0}
     */
    static int skipHtmlRtfBlock(String content, int pos) {
        int afterWord = pos + "\\htmlrtf".length();
        if (afterWord < content.length() && content.charAt(afterWord) == '0') {
            // This is \htmlrtf0 (end marker) — just skip past it
            afterWord++;
            if (afterWord < content.length() && content.charAt(afterWord) == ' ') {
                afterWord++;
            }
            return afterWord;
        }

        // Skip everything until \htmlrtf0
        int endPos = content.indexOf("\\htmlrtf0", afterWord);
        if (endPos < 0) {
            return content.length();
        }
        int after = endPos + "\\htmlrtf0".length();
        if (after < content.length() && content.charAt(after) == ' ') {
            after++;
        }
        return after;
    }

    /**
     * Detect the ANSI code page from the RTF header ({@code \ansicpgNNNN}).
     * Falls back to windows-1252 if not found.
     */
    static Charset detectCodePage(String rtf) {
        int idx = rtf.indexOf(ANSICPG_PREFIX);
        if (idx < 0) {
            return Charset.forName("windows-1252");
        }
        int numStart = idx + ANSICPG_PREFIX.length();
        int numEnd = numStart;
        while (numEnd < rtf.length() && Character.isDigit(rtf.charAt(numEnd))) {
            numEnd++;
        }
        if (numEnd == numStart) {
            return Charset.forName("windows-1252");
        }
        String cpNum = rtf.substring(numStart, numEnd);
        try {
            return Charset.forName("windows-" + cpNum);
        } catch (Exception e) {
            try {
                return Charset.forName("cp" + cpNum);
            } catch (Exception e2) {
                LOGGER.debug("Unknown code page {}, falling back to windows-1252", cpNum);
                return Charset.forName("windows-1252");
            }
        }
    }

    private static Charset charsetOrNull(String name) {
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return null;
        }
    }

    private static void flushPendingBytes(ByteArrayOutputStream pending, StringBuilder out,
                                          Charset codePage) {
        if (pending.size() > 0) {
            out.append(new String(pending.toByteArray(), codePage));
            pending.reset();
        }
    }
}
