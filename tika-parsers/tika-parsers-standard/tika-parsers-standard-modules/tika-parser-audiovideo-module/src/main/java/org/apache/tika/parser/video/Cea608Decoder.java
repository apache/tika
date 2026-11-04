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
package org.apache.tika.parser.video;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CEA-608 (line 21) caption decoder for text extraction. Byte pairs go in in presentation
 * order; each channel (CC1-CC4) comes out as a {@link SubtitleTrack}. A pop-on caption is one
 * cue from its display to its erasure; a roll-up line is one cue when it is completed, so a
 * line is never repeated as it scrolls; paint-on text is one cue per erasure. A pop-on or
 * paint-on caption shown again the moment it was taken down extends its cue instead of adding
 * one. Styling, positioning, text mode (T1-T4) and XDS are dropped. The WebVTT each cue adds is
 * charged to a budget shared by the caller, so a stream that re-displays its screen cannot
 * multiply its own size.
 */
public final class Cea608Decoder {

    private static final int ROWS = 15;
    private static final int COLS = 32;

    // 608 basic characters that differ from ASCII
    private static final char[] BASIC = new char[128];
    private static final String SPECIAL = "®°½¿™¢£♪à èâêîôû";
    // extended sets after 0x12 and 0x13 (0x1A, 0x1B on channel 2), 0x20-0x3F
    private static final String EXTENDED_12 = "ÁÉÓÚÜü‘¡*'—©℠•“”ÀÂÇÈÊËëÎÏïÔÙùÛ«»";
    private static final String EXTENDED_13 = "ÃãÍÌìÒòÕõ{}\\^_|~ÄäÖöß¥¤¦ÅåØø┌┐└┘";
    // PAC row (0-based) by first byte 0x10-0x17, then whether the second byte has 0x20
    private static final int[][] PAC_ROWS = {
            {10, 10}, {0, 1}, {2, 3}, {11, 12}, {13, 14}, {4, 5}, {6, 7}, {8, 9}};

    static {
        for (int c = 0x20; c < 0x80; c++) {
            BASIC[c] = (char) c;
        }
        BASIC[0x2A] = 'á';
        BASIC[0x5C] = 'é';
        BASIC[0x5E] = 'í';
        BASIC[0x5F] = 'ó';
        BASIC[0x60] = 'ú';
        BASIC[0x7B] = 'ç';
        BASIC[0x7C] = '÷';
        BASIC[0x7D] = 'Ñ';
        BASIC[0x7E] = 'ñ';
        BASIC[0x7F] = '█';
    }

    private enum Mode { POP_ON, ROLL_UP, PAINT_ON }

    // a cue's WebVTT beyond its text: timestamps, arrow and newlines
    private static final int CUE_OVERHEAD = 32;

    private final String prefix;
    private final Channel[] channels = new Channel[4];
    // per field: data channel of the last control code, last control pair, XDS in progress
    private final int[] current = new int[2];
    private final int[] lastControl = {-1, -1};
    private final boolean[] xds = new boolean[2];
    // cue characters the caller still allows, shared across decoders; negative once spent
    private final long[] budget;
    private long lastMillis;

    /** @param prefix resource-name prefix, e.g. {@code track-1-}; tracks are prefix + cc1.vtt */
    public Cea608Decoder(String prefix) {
        this(prefix, new long[]{Long.MAX_VALUE});
    }

    /** @param budget WebVTT characters allowed, decremented as cues are made; -1 once spent */
    public Cea608Decoder(String prefix, long[] budget) {
        this.prefix = prefix;
        this.budget = budget;
    }

    /** Padding on a field: a control code after it is not a repeat. */
    public void padding(int field) {
        lastControl[field] = -1;
    }

    /**
     * One byte pair as carried in {@code cc_data}, parity bits included.
     *
     * @param field 0 for field 1 (CC1/CC2), 1 for field 2 (CC3/CC4)
     */
    public void pair(long millis, int field, int b1, int b2) {
        lastMillis = Math.max(lastMillis, millis);
        if (budget[0] < 0) {
            return;
        }
        if (!oddParity(b1) && (b1 & 0x7F) >= 0x10 && (b1 & 0x7F) <= 0x1F) {
            // a control code with a damaged first byte: its second byte is not a character
            return;
        }
        int c1 = oddParity(b1) ? b1 & 0x7F : 0;
        int c2 = oddParity(b2) ? b2 & 0x7F : 0;
        if (c1 == 0 && c2 == 0) {
            padding(field);
            return;
        }
        if (c1 >= 0x10 && c1 <= 0x1F) {
            xds[field] = false;
            int code = (c1 << 8) | c2;
            // control codes are sent twice; the repeat is dropped
            if (lastControl[field] == code) {
                lastControl[field] = -1;
                return;
            }
            lastControl[field] = code;
            current[field] = (c1 & 0x08) != 0 ? 1 : 0;
            control(channel(field, current[field]), c1 & 0xF7, c2, millis);
            return;
        }
        lastControl[field] = -1;
        if (field == 1 && c1 > 0 && c1 < 0x10) {
            // XDS on field 2 runs from a 0x01-0x0E start to the 0x0F end (plus checksum)
            xds[field] = c1 != 0x0F;
            return;
        }
        if (xds[field] || (c1 < 0x20 && c1 != 0)) {
            return;
        }
        Channel ch = channel(field, current[field]);
        if (c1 >= 0x20) {
            ch.put(BASIC[c1], millis);
        }
        if (c2 >= 0x20) {
            ch.put(BASIC[c2], millis);
        }
    }

    /** Closes open captions at the last time seen; tracks without cues are left out. */
    public List<SubtitleTrack> finish() {
        return finish(lastMillis);
    }

    /** As {@link #finish()}, closing open captions at {@code endMillis} if that is later. */
    public List<SubtitleTrack> finish(long endMillis) {
        long end = Math.max(lastMillis, endMillis);
        List<SubtitleTrack> out = new ArrayList<>();
        for (int i = 0; i < channels.length; i++) {
            Channel ch = channels[i];
            if (ch == null) {
                continue;
            }
            ch.flushAll(end);
            ch.emitPending();
            if (ch.track.getCueCount() > 0) {
                out.add(ch.track);
            }
        }
        return out;
    }

    private Channel channel(int field, int data) {
        int i = field * 2 + data;
        if (channels[i] == null) {
            SubtitleTrack track = new SubtitleTrack(prefix + "cc" + (i + 1) + ".vtt", null);
            track.setTitle("CC" + (i + 1));
            channels[i] = new Channel(track, budget);
        }
        return channels[i];
    }

    private static void control(Channel ch, int c1, int c2, long t) {
        if ((c1 == 0x14 || c1 == 0x15) && c2 >= 0x20 && c2 <= 0x2F) {
            ch.misc(c2, t);
        } else if (c1 == 0x17 && c2 >= 0x21 && c2 <= 0x23) {
            ch.tab(c2 - 0x20);
        } else if (c1 == 0x11 && c2 >= 0x20 && c2 <= 0x2F) {
            // mid-row style change, shown as a space
            ch.put(' ', t);
        } else if (c1 == 0x11 && c2 >= 0x30 && c2 <= 0x3F) {
            ch.put(SPECIAL.charAt(c2 - 0x30), t);
        } else if ((c1 == 0x12 || c1 == 0x13) && c2 >= 0x20 && c2 <= 0x3F) {
            ch.extended((c1 == 0x12 ? EXTENDED_12 : EXTENDED_13).charAt(c2 - 0x20), t);
        } else if (c2 >= 0x40 && c2 <= 0x7F) {
            int row = PAC_ROWS[c1 - 0x10][(c2 & 0x20) != 0 ? 1 : 0];
            int col = (c2 & 0x10) != 0 ? ((c2 & 0x0E) >> 1) * 4 : 0;
            ch.pac(row, col, t);
        }
    }

    static boolean oddParity(int b) {
        return (Integer.bitCount(b & 0xFF) & 1) == 1;
    }

    private static final class Channel {
        final SubtitleTrack track;
        final long[] budget;
        // a stream joined after its mode command is most often roll-up
        Mode mode = Mode.ROLL_UP;
        // TR/RTD hand the channel to a text service until a caption command takes it back
        boolean textService;
        // the last cue is held back so a re-display can extend it
        String pendingText;
        long pendingStart;
        long pendingEnd;
        boolean pendingMergeable;
        char[][] displayed = new char[ROWS][COLS];
        char[][] hidden = new char[ROWS][COLS];
        int row = ROWS - 1;
        int col;
        int rollRows = 2;
        // when the displayed memory began to show; -1 when it shows nothing
        long shownSince = -1;
        // roll-up: when the current row got its first character; -1 when it is empty
        long lineSince = -1;

        Channel(SubtitleTrack track, long[] budget) {
            this.track = track;
            this.budget = budget;
        }

        void misc(int c2, long t) {
            if (c2 == 0x20 || c2 == 0x25 || c2 == 0x26 || c2 == 0x27 || c2 == 0x29
                    || c2 == 0x2F) {
                textService = false;
            }
            if (textService && (c2 == 0x21 || c2 == 0x24 || c2 == 0x2D)) {
                return;
            }
            switch (c2) {
                case 0x20: // RCL
                    enter(Mode.POP_ON, t);
                    break;
                case 0x21: // BS
                    if (col > 0) {
                        col--;
                        target()[row][col] = 0;
                    }
                    break;
                case 0x24: // DER
                    char[] r = target()[row];
                    for (int c = col; c < COLS; c++) {
                        r[c] = 0;
                    }
                    break;
                case 0x25: // RU2
                case 0x26: // RU3
                case 0x27: // RU4
                    rollRows = c2 - 0x23;
                    if (mode != Mode.ROLL_UP) {
                        enter(Mode.ROLL_UP, t);
                        clear(hidden);
                        row = ROWS - 1;
                        col = 0;
                    }
                    break;
                case 0x29: // RDC
                    enter(Mode.PAINT_ON, t);
                    break;
                case 0x2A: // TR
                case 0x2B: // RTD
                    textService = true;
                    break;
                case 0x2C: // EDM
                    if (mode == Mode.ROLL_UP) {
                        flushLine(t);
                    } else {
                        flushShown(t);
                    }
                    clear(displayed);
                    break;
                case 0x2D: // CR
                    if (mode == Mode.ROLL_UP) {
                        flushLine(t);
                        roll();
                    }
                    break;
                case 0x2E: // ENM
                    clear(hidden);
                    break;
                case 0x2F: // EOC
                    enter(Mode.POP_ON, t);
                    flushShown(t);
                    char[][] swap = displayed;
                    displayed = hidden;
                    hidden = swap;
                    shownSince = hasText(displayed) ? t : -1;
                    mode = Mode.POP_ON;
                    break;
                default:
                    break;
            }
        }

        void enter(Mode next, long t) {
            if (mode == next) {
                return;
            }
            if (mode == Mode.ROLL_UP) {
                flushLine(t);
                clear(displayed);
            } else if (next == Mode.ROLL_UP) {
                flushShown(t);
                clear(displayed);
            }
            mode = next;
        }

        void pac(int newRow, int newCol, long t) {
            if (textService) {
                return;
            }
            if (mode == Mode.ROLL_UP) {
                // the base row moves; a pending line is finished first
                if (newRow != row) {
                    flushLine(t);
                    clear(displayed);
                }
            }
            row = newRow;
            col = newCol;
        }

        void tab(int n) {
            if (!textService) {
                col = Math.min(col + n, COLS - 1);
            }
        }

        void put(char c, long t) {
            if (textService) {
                return;
            }
            char[][] target = target();
            if (mode == Mode.ROLL_UP && lineSince < 0) {
                lineSince = t;
            } else if (mode == Mode.PAINT_ON && shownSince < 0) {
                shownSince = t;
            }
            target[row][col] = c;
            col = Math.min(col + 1, COLS - 1);
        }

        // an extended character replaces the standard one sent before it as a fallback
        void extended(char c, long t) {
            if (textService) {
                return;
            }
            if (col > 0) {
                col--;
            }
            put(c, t);
        }

        char[][] target() {
            return mode == Mode.POP_ON ? hidden : displayed;
        }

        // the track or the caller's budget takes no more; text is not even built then
        boolean closed() {
            return track.isTruncated() || budget[0] < 0;
        }

        void flushShown(long t) {
            if (shownSince >= 0 && !closed()) {
                cue(shownSince, t, text(displayed, 0, ROWS), true);
            }
            shownSince = -1;
        }

        void flushLine(long t) {
            if (lineSince >= 0 && !closed()) {
                // roll-up lines that read the same are separate lines, never merged
                cue(lineSince, t, text(displayed, row, row + 1), false);
            }
            lineSince = -1;
        }

        void cue(long start, long end, String text, boolean mergeable) {
            if (text.isEmpty()) {
                return;
            }
            if (mergeable && pendingMergeable && start == pendingEnd
                    && text.equals(pendingText)) {
                pendingEnd = end;
                return;
            }
            emitPending();
            pendingText = text;
            pendingStart = start;
            pendingEnd = end;
            pendingMergeable = mergeable;
        }

        // charged with what the cue adds to the WebVTT, escapes included
        void emitPending() {
            if (pendingText == null || closed()) {
                pendingText = null;
                return;
            }
            if (budget[0] < pendingText.length() + CUE_OVERHEAD) {
                budget[0] = -1;
            } else {
                int before = track.length();
                track.addCue(pendingStart, pendingEnd, pendingText);
                budget[0] -= Math.max(CUE_OVERHEAD, track.length() - before);
            }
            pendingText = null;
        }

        void flushAll(long t) {
            if (mode == Mode.ROLL_UP) {
                flushLine(t);
            } else {
                flushShown(t);
            }
        }

        // rows above the base row move up one; the base row is emptied
        void roll() {
            int top = Math.max(0, row - rollRows + 1);
            for (int r = top; r < row; r++) {
                System.arraycopy(displayed[r + 1], 0, displayed[r], 0, COLS);
            }
            displayed[row] = new char[COLS];
            col = 0;
        }

        static String text(char[][] rows, int from, int to) {
            StringBuilder sb = new StringBuilder();
            for (int r = from; r < to; r++) {
                StringBuilder line = new StringBuilder(COLS);
                for (char c : rows[r]) {
                    line.append(c == 0 ? ' ' : c);
                }
                String s = line.toString().strip();
                if (!s.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(s);
                }
            }
            return sb.toString();
        }

        static boolean hasText(char[][] rows) {
            for (char[] r : rows) {
                for (char c : r) {
                    if (c != 0 && c != ' ') {
                        return true;
                    }
                }
            }
            return false;
        }

        static void clear(char[][] rows) {
            for (char[] r : rows) {
                Arrays.fill(r, (char) 0);
            }
        }
    }
}
