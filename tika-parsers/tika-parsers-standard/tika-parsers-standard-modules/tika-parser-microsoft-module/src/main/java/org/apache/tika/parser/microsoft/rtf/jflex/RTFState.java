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
package org.apache.tika.parser.microsoft.rtf.jflex;

import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared RTF parsing state: group stack, font table, codepage tracking,
 * and unicode skip handling.
 *
 * <p>Both the HTML decapsulator and the full RTF parser use this class
 * to manage the stateful parts of RTF processing.</p>
 *
 * <p>Typical usage: feed every token to {@link #processToken(RTFToken)}
 * and query the current charset via {@link #getCurrentCharset()}.</p>
 */
public class RTFState {

    /** Global charset from {@code \ansicpgN} or charset family selectors. */
    private Charset globalCharset = RTFCharsetMaps.WINDOWS_1252;

    /** Default font ID from {@code \deffN}. */
    private int globalDefaultFont = -1;

    /** Font table: maps font number ({@code \fN}) to charset ({@code \fcharsetN}). */
    private final Map<Integer, Charset> fontToCharset = new HashMap<>();

    /** Group state stack. */
    private final Deque<RTFGroupState> stack = new ArrayDeque<>();

    /** Current (active) group state. */
    private RTFGroupState current = new RTFGroupState();

    /** Number of ANSI chars remaining to skip after a unicode escape. */
    private int ansiSkip = 0;

    /** The group state that was just closed (before popGroup). Set on GROUP_CLOSE. */
    private RTFGroupState lastClosedGroup;

    // Font table parsing state
    // 0 = not yet seen, 1 = inside fonttbl, 2 = finished fonttbl
    private int fontTableState = 0;
    private int fontTableDepth = -1;
    private int currentFontId = -1;

    private boolean inHeader = true;

    /**
     * Process a single token to update internal state.
     * <p>
     * This handles: group open/close, charset selectors (ansi, ansicpg,
     * deff), font table parsing (fonttbl, f, fcharset),
     * unicode skip tracking (uc), and font changes (f in body).
     *
     * @return true if the token was consumed by state management (caller should skip it),
     *         false if the caller should also process it
     */
    public boolean processToken(RTFToken tok) {
        switch (tok.getType()) {
            case GROUP_OPEN:
                pushGroup();
                return false;

            case GROUP_CLOSE:
                lastClosedGroup = current;
                popGroup();
                // Check if we've exited the font table
                if (fontTableState == 1 && current.depth < fontTableDepth) {
                    fontTableState = 2;
                }
                return false;

            case CONTROL_SYMBOL:
                if ("*".equals(tok.getName())) {
                    current.ignore = true;
                }
                return false;

            case CONTROL_WORD:
                return processControlWord(tok);

            case UNICODE_ESCAPE:
                // After a unicode escape, skip the next ucSkip ANSI chars
                ansiSkip = current.ucSkip;
                return false;

            case HEX_ESCAPE:
                // If we're in the ANSI shadow of a unicode escape, skip this byte
                if (ansiSkip > 0) {
                    ansiSkip--;
                    return true; // consumed — caller should ignore
                }
                return false;

            case TEXT:
                // If we're in the ANSI shadow, skip text chars
                if (ansiSkip > 0) {
                    // Each TEXT token is one char
                    ansiSkip--;
                    return true;
                }
                return false;

            default:
                return false;
        }
    }

    private boolean processControlWord(RTFToken tok) {
        String name = tok.getName();
        boolean hasParam = tok.hasParameter();
        int param = tok.getParameter();

        // Global charset selectors (header)
        switch (name) {
            case "ansi":
                globalCharset = RTFCharsetMaps.WINDOWS_1252;
                return true;
            case "pca":
                globalCharset = RTFCharsetMaps.getCharset("cp850");
                return true;
            case "pc":
                globalCharset = RTFCharsetMaps.getCharset("cp437");
                return true;
            case "mac":
                globalCharset = RTFCharsetMaps.getCharset("MacRoman");
                return true;
            case "ansicpg":
                if (hasParam) {
                    Charset cs = RTFCharsetMaps.ANSICPG_MAP.get(param);
                    if (cs != null) {
                        globalCharset = cs;
                    } else {
                        globalCharset = RTFCharsetMaps.resolveCodePage(param);
                    }
                }
                return true;
            case "deff":
                if (hasParam) {
                    globalDefaultFont = param;
                }
                return true;
        }

        // Font table management
        if ("fonttbl".equals(name)) {
            fontTableState = 1;
            fontTableDepth = current.depth;
            current.ignore = true;
            return true;
        }

        if (fontTableState == 1) {
            // Inside font table
            if (current.depth < fontTableDepth) {
                fontTableState = 2;
            } else {
                if ("f".equals(name) && hasParam) {
                    currentFontId = param;
                    return true;
                } else if ("fcharset".equals(name) && hasParam) {
                    Charset cs = RTFCharsetMaps.FCHARSET_MAP.get(param);
                    if (cs != null) {
                        fontToCharset.put(currentFontId, cs);
                    }
                    return true;
                }
            }
        }

        // Unicode skip count
        if ("uc".equals(name) && hasParam) {
            current.ucSkip = param;
            return true;
        }

        // Font change in body
        if ("f".equals(name) && hasParam) {
            current.fontId = param;
            Charset fontCs = fontToCharset.get(param);
            current.fontCharset = fontCs; // may be null
            // If we've seen the font table and this is a body font change,
            // we're out of the header
            if (fontTableState == 2 && !current.ignore) {
                inHeader = false;
            }
            return false; // caller may also want to know about font changes
        }

        // Header-ending control words
        if (inHeader && !current.ignore) {
            switch (name) {
                case "par":
                case "pard":
                case "sect":
                case "sectd":
                case "plain":
                case "ltrch":
                case "rtlch":
                case "htmlrtf":
                case "line":
                    inHeader = false;
                    break;
            }
        }

        // Embedded object / picture control words
        switch (name) {
            case "object":
                current.object = true;
                return false; // caller may want to know
            case "objdata":
                current.objdata = true;
                return false;
            case "pict":
                current.pictDepth = 1;
                return false;
            case "sp":
                current.sp = true;
                return false;
            case "sn":
                current.sn = true;
                return false;
            case "sv":
                current.sv = true;
                return false;
            case "wbitmap":
                return false; // caller handles
        }

        // Ignorable destinations
        if (inHeader) {
            switch (name) {
                case "colortbl":
                case "stylesheet":
                    current.ignore = true;
                    return true;
            }
        }

        return false;
    }

    /** Open a new group: push current state and create a child. */
    public void pushGroup() {
        stack.push(current);
        current = new RTFGroupState(current);
    }

    /** Close the current group: pop and restore the parent state. */
    public void popGroup() {
        if (!stack.isEmpty()) {
            current = stack.pop();
        }
    }

    /**
     * Returns the charset that should be used to decode the current hex escape
     * or text byte. Priority:
     * <ol>
     *   <li>Font-specific charset (from {@code \fN → \fcharsetN})</li>
     *   <li>Global default font's charset (from {@code \deffN})</li>
     *   <li>Global charset (from {@code \ansicpgN} or family selector)</li>
     * </ol>
     */
    public Charset getCurrentCharset() {
        if (current.fontCharset != null) {
            return current.fontCharset;
        }
        if (globalDefaultFont != -1 && !inHeader) {
            Charset cs = fontToCharset.get(globalDefaultFont);
            if (cs != null) {
                return cs;
            }
        }
        return globalCharset;
    }

    /** Returns the global charset ({@code \ansicpgN}). */
    public Charset getGlobalCharset() {
        return globalCharset;
    }

    /** Returns the current group state. */
    public RTFGroupState getCurrentGroup() {
        return current;
    }

    /** Returns true if we're still in the RTF header (before body content). */
    public boolean isInHeader() {
        return inHeader;
    }

    /** Returns the current group nesting depth. */
    public int getDepth() {
        return current.depth;
    }

    /** Returns the font-to-charset mapping table. */
    public Map<Integer, Charset> getFontToCharset() {
        return fontToCharset;
    }

    /** Returns the number of ANSI chars remaining to skip. */
    public int getAnsiSkip() {
        return ansiSkip;
    }

    /**
     * Returns the group state that was just closed on the most recent GROUP_CLOSE.
     * This is the child group's state before it was popped.
     * Useful for checking flags like objdata, pictDepth, sn, sv, sp, object
     * to trigger completion handlers.
     */
    public RTFGroupState getLastClosedGroup() {
        return lastClosedGroup;
    }
}
