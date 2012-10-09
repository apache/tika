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

package org.apache.tika.parser.rtf;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.CharsetUtils;
import org.xml.sax.SAXException;

/* Tokenizes and performs a "shallow" parse of the RTF
 * document, just enough to properly decode the text.
 *
 * TODO: we should cutover to a "real" tokenizer (eg JFlex);
 * it should give better perf, by replacing the excessive
 * "else if" string compares with FSA traversal. */

final class TextExtractor {

    private static final Charset ASCII = Charset.forName("US-ASCII");

    private static Charset getCharset(String name) {
        try {
            return CharsetUtils.forName(name);
        } catch (Exception e) {
            return ASCII;
        }
    }

    private static final Charset WINDOWS_1252 = getCharset("WINDOWS-1252");
    private static final Charset MAC_ROMAN = getCharset("MacRoman");
    private static final Charset SHIFT_JIS = getCharset("Shift_JIS");
    private static final Charset WINDOWS_57011 = getCharset("windows-57011");
    private static final Charset WINDOWS_57010 = getCharset("windows-57010");
    private static final Charset WINDOWS_57009 = getCharset("windows-57009");
    private static final Charset WINDOWS_57008 = getCharset("windows-57008");
    private static final Charset WINDOWS_57007 = getCharset("windows-57007");
    private static final Charset WINDOWS_57006 = getCharset("windows-57006");
    private static final Charset WINDOWS_57005 = getCharset("windows-57005");
    private static final Charset WINDOWS_57004 = getCharset("windows-57004");
    private static final Charset WINDOWS_57003 = getCharset("windows-57003");
    private static final Charset X_ISCII91 = getCharset("x-ISCII91");
    private static final Charset X_MAC_CENTRAL_EUROPE = getCharset("x-MacCentralEurope");
    private static final Charset MAC_CYRILLIC = getCharset("MacCyrillic");
    private static final Charset X_JOHAB = getCharset("x-Johab");
    private static final Charset CP12582 = getCharset("CP1258");
    private static final Charset CP12572 = getCharset("CP1257");
    private static final Charset CP12562 = getCharset("CP1256");
    private static final Charset CP12552 = getCharset("CP1255");
    private static final Charset CP12542 = getCharset("CP1254");
    private static final Charset CP12532 = getCharset("CP1253");
    private static final Charset CP1252 = getCharset("CP1252");
    private static final Charset CP12512 = getCharset("CP1251");
    private static final Charset CP12502 = getCharset("CP1250");
    private static final Charset CP950 = getCharset("CP950");
    private static final Charset CP949 = getCharset("CP949");
    private static final Charset MS9362 = getCharset("MS936");
    private static final Charset MS8742 = getCharset("MS874");
    private static final Charset CP866 = getCharset("CP866");
    private static final Charset CP865 = getCharset("CP865");
    private static final Charset CP864 = getCharset("CP864");
    private static final Charset CP863 = getCharset("CP863");
    private static final Charset CP862 = getCharset("CP862");
    private static final Charset CP860 = getCharset("CP860");
    private static final Charset CP852 = getCharset("CP852");
    private static final Charset CP8502 = getCharset("CP850");
    private static final Charset CP819 = getCharset("CP819");
    private static final Charset WINDOWS_720 = getCharset("windows-720");
    private static final Charset WINDOWS_711 = getCharset("windows-711");
    private static final Charset WINDOWS_710 = getCharset("windows-710");
    private static final Charset WINDOWS_709 = getCharset("windows-709");
    private static final Charset ISO_8859_6 = getCharset("ISO-8859-6");
    private static final Charset CP4372 = getCharset("CP437");
    private static final Charset CP850 = getCharset("cp850");
    private static final Charset CP437 = getCharset("cp437");
    private static final Charset MS874 = getCharset("ms874");
    private static final Charset CP1257 = getCharset("cp1257");
    private static final Charset CP1256 = getCharset("cp1256");
    private static final Charset CP1255 = getCharset("cp1255");
    private static final Charset CP1258 = getCharset("cp1258");
    private static final Charset CP1254 = getCharset("cp1254");
    private static final Charset CP1253 = getCharset("cp1253");
    private static final Charset MS950 = getCharset("ms950");
    private static final Charset MS936 = getCharset("ms936");
    private static final Charset MS1361 = getCharset("ms1361");
    private static final Charset MS932 = getCharset("MS932");
    private static final Charset CP1251 = getCharset("cp1251");
    private static final Charset CP1250 = getCharset("cp1250");
    private static final Charset MAC_THAI = getCharset("MacThai");
    private static final Charset MAC_TURKISH = getCharset("MacTurkish");
    private static final Charset MAC_GREEK = getCharset("MacGreek");
    private static final Charset MAC_ARABIC = getCharset("MacArabic");
    private static final Charset MAC_HEBREW = getCharset("MacHebrew");
    private static final Charset JOHAB = getCharset("johab");
    private static final Charset BIG5 = getCharset("Big5");
    private static final Charset GB2312 = getCharset("GB2312");
    private static final Charset MS949 = getCharset("ms949");

    // Hold pending bytes (encoded in the current charset)
    // for text output:
    private byte[] pendingBytes = new byte[16];
    private int pendingByteCount;
    private ByteBuffer pendingByteBuffer = ByteBuffer.wrap(pendingBytes);
      
    // Holds pending chars for text output
    private char[] pendingChars = new char[10];
    private int pendingCharCount;

    // Holds chars for a still-being-tokenized control word
    private byte[] pendingControl = new byte[10];
    private int pendingControlCount;

    // Used when we decode bytes -> chars using CharsetDecoder:
    private final char[] outputArray = new char[128];
    private final CharBuffer outputBuffer = CharBuffer.wrap(outputArray);

    // Reused when possible:
    private CharsetDecoder decoder;
    private Charset lastCharset;

    private Charset globalCharset = WINDOWS_1252;
    private int globalDefaultFont = -1;
    private int curFontID = -1;

    // Holds the font table from this RTF doc, mapping
    // the font number (from \fN control word) to the
    // corresponding charset:
    private final Map<Integer, Charset> fontToCharset =
            new HashMap<Integer, Charset>();

    // Group stack: when we open a new group, we push
    // the previous group state onto the stack; when we
    // close the group, we restore it
    private final LinkedList<GroupState> groupStates = new LinkedList<GroupState>();

    // Current group state; in theory this initial
    // GroupState is unused because the RTF doc should
    // immediately open the top group (start with {):
    private GroupState groupState = new GroupState();

    private boolean inHeader = true;
    private int fontTableState;
    private int fontTableDepth;

    // Non null if we are processing metadata (title,
    // keywords, etc.) inside the info group:
    private Property nextMetaData;
    private boolean inParagraph;

    // Non-zero if we are processing inside a field destination:
    private int fieldState;
    
    // Non-null if we've seen the url for a HYPERLINK but not yet
    // its text:
    private String pendingURL;

    private final StringBuilder pendingBuffer = new StringBuilder();

    // Used to process the sub-groups inside the upr
    // group:
    private int uprState = -1;

    private final XHTMLContentHandler out;
    private final Metadata metadata;

    // Used when extracting CREATION date:
    private int year, month, day, hour, minute;

    // How many next ansi chars we should skip; this
    // is 0 except when we are still in the "ansi
    // shadow" after seeing a unicode escape, at which
    // point it's set to the last ucN skip we had seen:
    int ansiSkip = 0;

    // The RTF doc has a "font table" that assigns ords
    // (f0, f1, f2, etc.) to fonts and charsets, using the
    // \fcharsetN control word.  This mapping maps from the
    // N to corresponding Java charset:
    private static final Map<Integer, Charset> FCHARSET_MAP =
            new HashMap<Integer, Charset>();

    static {
        FCHARSET_MAP.put(0, WINDOWS_1252); // ANSI
        // charset 1 is Default
        // charset 2 is Symbol

        FCHARSET_MAP.put(77, MAC_ROMAN); // Mac Roman
        FCHARSET_MAP.put(78, SHIFT_JIS); // Mac Shift Jis
        FCHARSET_MAP.put(79, MS949); // Mac Hangul
        FCHARSET_MAP.put(80, GB2312); // Mac GB2312
        FCHARSET_MAP.put(81, BIG5); // Mac Big5
        FCHARSET_MAP.put(82, JOHAB); // Mac Johab (old)
        FCHARSET_MAP.put(83, MAC_HEBREW); // Mac Hebrew
        FCHARSET_MAP.put(84, MAC_ARABIC); // Mac Arabic
        FCHARSET_MAP.put(85, MAC_GREEK); // Mac Greek
        FCHARSET_MAP.put(86, MAC_TURKISH); // Mac Turkish
        FCHARSET_MAP.put(87, MAC_THAI); // Mac Thai
        FCHARSET_MAP.put(88, CP1250); // Mac East Europe
        FCHARSET_MAP.put(89, CP1251); // Mac Russian

        FCHARSET_MAP.put(128, MS932); // Shift JIS
        FCHARSET_MAP.put(129, MS949); // Hangul
        FCHARSET_MAP.put(130, MS1361); // Johab
        FCHARSET_MAP.put(134, MS936); // GB2312
        FCHARSET_MAP.put(136, MS950); // Big5
        FCHARSET_MAP.put(161, CP1253); // Greek
        FCHARSET_MAP.put(162, CP1254); // Turkish
        FCHARSET_MAP.put(163, CP1258); // Vietnamese
        FCHARSET_MAP.put(177, CP1255); // Hebrew
        FCHARSET_MAP.put(178, CP1256); // Arabic
        // FCHARSET_MAP.put( 179, "" ); // Arabic Traditional
        // FCHARSET_MAP.put( 180, "" ); // Arabic user
        // FCHARSET_MAP.put( 181, "" ); // Hebrew user
        FCHARSET_MAP.put(186, CP1257); // Baltic

        FCHARSET_MAP.put(204, CP1251); // Russian
        FCHARSET_MAP.put(222, MS874); // Thai
        FCHARSET_MAP.put(238, CP1250); // Eastern European
        FCHARSET_MAP.put(254, CP437); // PC 437
        FCHARSET_MAP.put(255, CP850); // OEM
    }

    // The RTF may specify the \ansicpgN charset in the
    // header; this maps the N to the corresponding Java
    // character set:
    private static final Map<Integer, Charset> ANSICPG_MAP =
            new HashMap<Integer, Charset>();
    static {
        ANSICPG_MAP.put(437, CP4372);   // US IBM
        ANSICPG_MAP.put(708, ISO_8859_6);   // Arabic (ASMO 708)
      
        ANSICPG_MAP.put(709, WINDOWS_709);  // Arabic (ASMO 449+, BCON V4)
        ANSICPG_MAP.put(710, WINDOWS_710);  // Arabic (transparent Arabic)
        ANSICPG_MAP.put(710, WINDOWS_711);  // Arabic (Nafitha Enhanced)
        ANSICPG_MAP.put(710, WINDOWS_720);  // Arabic (transparent ASMO)
        ANSICPG_MAP.put(819, CP819);  // Windows 3.1 (US & Western Europe)
        ANSICPG_MAP.put(819, CP819);  // Windows 3.1 (US & Western Europe)

        ANSICPG_MAP.put(819, CP819);  // Windows 3.1 (US & Western Europe)
        ANSICPG_MAP.put(850, CP8502);  // IBM Multilingual
        ANSICPG_MAP.put(852, CP852);  // Eastern European
        ANSICPG_MAP.put(860, CP860);  // Portuguese
        ANSICPG_MAP.put(862, CP862);  // Hebrew
        ANSICPG_MAP.put(863, CP863);  // French Canadian
        ANSICPG_MAP.put(864, CP864);  // Arabic
        ANSICPG_MAP.put(865, CP865);  // Norwegian
        ANSICPG_MAP.put(866, CP866);  // Soviet Union
        ANSICPG_MAP.put(874, MS8742);  // Thai
        ANSICPG_MAP.put(932, MS932);  // Japanese
        ANSICPG_MAP.put(936, MS9362);  // Simplified Chinese
        ANSICPG_MAP.put(949, CP949);  // Korean
        ANSICPG_MAP.put(950, CP950);  // Traditional Chinese
        ANSICPG_MAP.put(1250, CP12502);  // Eastern European
        ANSICPG_MAP.put(1251, CP12512);  // Cyrillic
        ANSICPG_MAP.put(1252, CP1252);  // Western European
        ANSICPG_MAP.put(1253, CP12532);  // Greek
        ANSICPG_MAP.put(1254, CP12542);  // Turkish
        ANSICPG_MAP.put(1255, CP12552);  // Hebrew
        ANSICPG_MAP.put(1256, CP12562);  // Arabic
        ANSICPG_MAP.put(1257, CP12572);  // Baltic
        ANSICPG_MAP.put(1258, CP12582);  // Vietnamese
        ANSICPG_MAP.put(1361, X_JOHAB);  // Johab
        ANSICPG_MAP.put(10000, MAC_ROMAN);  // Mac Roman
        ANSICPG_MAP.put(10001, SHIFT_JIS);  // Mac Japan
        ANSICPG_MAP.put(10004, MAC_ARABIC);  // Mac Arabic
        ANSICPG_MAP.put(10005, MAC_HEBREW);  // Mac Hebrew
        ANSICPG_MAP.put(10006, MAC_GREEK);  // Mac Hebrew
        ANSICPG_MAP.put(10007, MAC_CYRILLIC);  // Mac Cyrillic
        ANSICPG_MAP.put(10029, X_MAC_CENTRAL_EUROPE);  // MAC Latin2
        ANSICPG_MAP.put(10081, MAC_TURKISH);  // Mac Turkish
        ANSICPG_MAP.put(57002, X_ISCII91);   // Devanagari

        // TODO: in theory these other charsets are simple
        // shifts off of Devanagari, so we could impl that
        // here:
        ANSICPG_MAP.put(57003, WINDOWS_57003);   // Bengali
        ANSICPG_MAP.put(57004, WINDOWS_57004);   // Tamil
        ANSICPG_MAP.put(57005, WINDOWS_57005);   // Telugu
        ANSICPG_MAP.put(57006, WINDOWS_57006);   // Assamese
        ANSICPG_MAP.put(57007, WINDOWS_57007);   // Oriya
        ANSICPG_MAP.put(57008, WINDOWS_57008);   // Kannada
        ANSICPG_MAP.put(57009, WINDOWS_57009);   // Malayalam
        ANSICPG_MAP.put(57010, WINDOWS_57010);   // Gujariti
        ANSICPG_MAP.put(57011, WINDOWS_57011);   // Punjabi
    }

    public TextExtractor(XHTMLContentHandler out, Metadata metadata) {
        this.metadata = metadata;
        this.out = out;
    }

    private static boolean isHexChar(int ch) {
        return (ch >= '0' && ch <= '9') ||
            (ch >= 'a' && ch <= 'f') ||
            (ch >= 'A' && ch <= 'F');
    }

    private static boolean isAlpha(int ch) {
        return (ch >= 'a' && ch <= 'z') ||
            (ch >= 'A' && ch <= 'Z');
    }

    private static boolean isDigit(int ch) {
        return ch >= '0' && ch <= '9';
    }

    private static int hexValue(int ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        } else if (ch >= 'a' && ch <= 'z') {
            return 10 + (ch - 'a');
        } else {
            assert ch >= 'A' && ch <= 'Z';
            return 10 + (ch - 'A');
        }
    }

    // Push pending bytes or pending chars:
    private void pushText() throws IOException, SAXException, TikaException {
        if (pendingByteCount != 0) {
            assert pendingCharCount == 0;
            pushBytes();
        } else {
            pushChars();
        }
    }

    // Buffers the byte (unit in the current charset) for
    // output:
    private void addOutputByte(int b) throws IOException, SAXException, TikaException {
        assert b >= 0 && b < 256 : "byte value out of range: " + b;

        if (pendingCharCount != 0) {
            pushChars();
        }

        // Save the byte in pending buffer:
        if (pendingByteCount == pendingBytes.length) {
            // Gradual but exponential growth:
            final byte[] newArray = new byte[(int) (pendingBytes.length*1.25)];
            System.arraycopy(pendingBytes, 0, newArray, 0, pendingBytes.length);
            pendingBytes = newArray;
            pendingByteBuffer = ByteBuffer.wrap(pendingBytes);
        }
        pendingBytes[pendingByteCount++] = (byte) b;
    }

   // Buffers a byte as part of a control word:
    private void addControl(int b) {
        assert isAlpha(b);
        // Save the byte in pending buffer:
        if (pendingControlCount == pendingControl.length) {
            // Gradual but exponential growth:
            final byte[] newArray = new byte[(int) (pendingControl.length*1.25)];
            System.arraycopy(pendingControl, 0, newArray, 0, pendingControl.length);
            pendingControl = newArray;
        }
        pendingControl[pendingControlCount++] = (byte) b;
    }

    // Buffers a UTF16 code unit for output
    private void addOutputChar(char ch) throws IOException, SAXException, TikaException {
        if (pendingByteCount != 0) {
            pushBytes();
        }

        if (inHeader || fieldState == 1) {
            pendingBuffer.append(ch);
        } else {
            if (pendingCharCount == pendingChars.length) {
                // Gradual but exponential growth:
                final char[] newArray = new char[(int) (pendingChars.length*1.25)];
                System.arraycopy(pendingChars, 0, newArray, 0, pendingChars.length);
                pendingChars = newArray;
            }
            pendingChars[pendingCharCount++] = ch;
        }
    }

    // Shallow parses the entire doc, writing output to
    // this.out and this.metadata
    public void extract(InputStream in) throws IOException, SAXException, TikaException {
//        in = new FilterInputStream(in) {
//            public int read() throws IOException {
//                int r = super.read();
//                System.out.write(r);
//                System.out.flush();
//                return r;
//            }
//            public int read(byte b[], int off, int len) throws IOException {
//                int r = super.read(b, off, len);
//                System.out.write(b, off, r);
//                System.out.flush();
//                return r;
//            }
//        };
        extract(new PushbackInputStream(in, 2));
    }
    
    private void extract(PushbackInputStream in) throws IOException, SAXException, TikaException {
        out.startDocument();

        while (true) {
            final int b = in.read();
            if (b == -1) {
                break;
            } else if (b == '\\') {
                parseControlToken(in);
            } else if (b == '{') {
                pushText();
                processGroupStart(in);
             } else if (b == '}') {
                pushText();
                processGroupEnd();
                if (groupStates.isEmpty()) {
                    // parsed document closing brace
                    break;
                }
            } else if (b != '\r' && b != '\n' && (!groupState.ignore || nextMetaData != null)) {
                // Linefeed and carriage return are not
                // significant
                if (ansiSkip != 0) {
                    ansiSkip--;
                } else {
                    addOutputByte(b);
                }
            }
        }

        endParagraph(false);
        out.endDocument();
    }
    
    private void parseControlToken(PushbackInputStream in) throws IOException, SAXException, TikaException {
        int b = in.read();
        if (b == '\'') {
            // escaped hex char
            parseHexChar(in);
        } else if (isAlpha(b)) {
            // control word
            parseControlWord((char)b, in);
        } else if (b == '{' || b == '}' || b == '\\' || b == '\r' || b == '\n') {
            // escaped char
            addOutputByte(b);
        } else if (b != -1) {
            // control symbol, eg \* or \~
            processControlSymbol((char)b);
        }
    }
    
    private void parseHexChar(PushbackInputStream in) throws IOException, SAXException, TikaException {
        int hex1 = in.read();
        if (!isHexChar(hex1)) {
            // DOC ERROR (malformed hex escape): ignore 
            in.unread(hex1);
            return;
        }
        
        int hex2 = in.read();
        if (!isHexChar(hex2)) {
            // TODO: log a warning here, somehow?
            // DOC ERROR (malformed hex escape):
            // ignore
            in.unread(hex2);
            return;
        }
        
        if (ansiSkip != 0) {
            // Skip this ansi char since we are
            // still in the shadow of a unicode
            // escape:
            ansiSkip--;
        } else {
            // Unescape:
            addOutputByte(16*hexValue(hex1) + hexValue(hex2));
        }
    }

    private void parseControlWord(int firstChar, PushbackInputStream in) throws IOException, SAXException, TikaException {
        addControl(firstChar);
        
        int b = in.read();
        while (isAlpha(b)) {
            addControl(b);
            b = in.read();
        }
        
        boolean hasParam = false;
        boolean negParam = false;
        if (b == '-') {
            negParam = true;
            hasParam = true;
            b = in.read();
        }

        int param = 0;
        while (isDigit(b)) {
            param *= 10;
            param += (b - '0');
            hasParam = true;
            b = in.read();
        }
        
        // space is consumed as part of the
        // control word, but is not added to the
        // control word
        if (b != ' ') {
            in.unread(b);
        }
        
        if (hasParam) {
            if (negParam) {
                param = -param;
            }
            processControlWord(param, in);
        } else {
            processControlWord();
        }
        
        pendingControlCount = 0;
    }

    private void lazyStartParagraph() throws IOException, SAXException, TikaException {
        if (!inParagraph) {
            // Ensure </i></b> order
            if (groupState.italic) {
                end("i");
            }
            if (groupState.bold) {
                end("b");
            }
            out.startElement("p");
            // Ensure <b><i> order
            if (groupState.bold) {
                start("b");
            }
            if (groupState.italic) {
                start("i");
            }
            inParagraph = true;
        }
    }

    private void endParagraph(boolean preserveStyles) throws IOException, SAXException, TikaException {
        pushText();
        if (inParagraph) {
            if (groupState.italic) {
                end("i");
                groupState.italic = preserveStyles;
            }
            if (groupState.bold) {
                end("b");
                groupState.bold = preserveStyles;
            }
            out.endElement("p");
            if (preserveStyles && (groupState.bold || groupState.italic)) {
                start("p");
                if (groupState.bold) {
                    start("b");
                }
                if (groupState.italic) {
                    start("i");
                }
                inParagraph = true;
            } else {
                inParagraph = false;
            }
        }
    }

    // Push pending UTF16 units to out ContentHandler
    private void pushChars() throws IOException, SAXException, TikaException {
        if (pendingCharCount != 0) {
            lazyStartParagraph();
            out.characters(pendingChars, 0, pendingCharCount);
            pendingCharCount = 0;
        }
    }

    // Decodes the buffered bytes in pendingBytes
    // into UTF16 code units, and sends the characters
    // to the out ContentHandler, if we are in the body,
    // else appends the characters to the pendingBuffer
    private void pushBytes() throws IOException, SAXException, TikaException {
        if (pendingByteCount > 0 && (!groupState.ignore || nextMetaData != null)) {

            final CharsetDecoder decoder = getDecoder();
            pendingByteBuffer.limit(pendingByteCount);
            assert pendingByteBuffer.position() == 0;
            assert outputBuffer.position() == 0;

            while (true) {
                // We pass true for endOfInput because, when
                // we are called, we should have seen a
                // complete sequence of characters for this
                // charset:
                final CoderResult result = decoder.decode(pendingByteBuffer, outputBuffer, true);

                final int pos = outputBuffer.position();
                if (pos > 0) {
                    if (inHeader || fieldState == 1) {
                        pendingBuffer.append(outputArray, 0, pos);
                    } else {
                        lazyStartParagraph();
                        out.characters(outputArray, 0, pos);
                    }
                    outputBuffer.position(0);
                }

                if (result == CoderResult.UNDERFLOW) {
                    break;
                }
            }

            while (true) {
                final CoderResult result = decoder.flush(outputBuffer);

                final int pos = outputBuffer.position();
                if (pos > 0) {
                    if (inHeader || fieldState == 1) {
                        pendingBuffer.append(outputArray, 0, pos);
                    } else {
                        lazyStartParagraph();
                        out.characters(outputArray, 0, pos);
                    }
                    outputBuffer.position(0);
                }

                if (result == CoderResult.UNDERFLOW) {
                    break;
                }
            }

            // Reset for next decode
            decoder.reset();
            pendingByteBuffer.position(0);
        }

        pendingByteCount = 0;
    }

    // NOTE: s must be ascii alpha only
    private boolean equals(String s) {
        if (pendingControlCount != s.length()) {
            return false;
        }
        for(int idx=0;idx<pendingControlCount;idx++) {
            assert isAlpha(s.charAt(idx));
            if (((byte) s.charAt(idx)) != pendingControl[idx]) {
                return false;
            }
        }
        return true;
    }

    private void processControlSymbol(char ch) throws IOException, SAXException, TikaException {
        switch(ch) {
        case '~':
            // Non-breaking space -> unicode NON-BREAKING SPACE
            addOutputChar('\u00a0');
            break;
        case '*':
            // Ignorable destination (control words defined after
            // the 1987 RTF spec). These are already handled by
            // processGroupStart()
            break;
        case '-':
            // Optional hyphen -> unicode SOFT HYPHEN
            addOutputChar('\u00ad');
            break;
        case '_':
            // Non-breaking hyphen -> unicode NON-BREAKING HYPHEN
            addOutputChar('\u2011');
            break;
        default:
            break;
        }
    }

    private CharsetDecoder getDecoder() throws TikaException {
        Charset charset = getCharset();

        // Common case: charset is same as last time, so
        // just reuse it:
        if (lastCharset == null || !charset.equals(lastCharset)) {
            decoder = charset.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPLACE);
            decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
            lastCharset = charset;
        }

        return decoder;
    }

    // Return current charset in-use
    private Charset getCharset() throws TikaException {
        // If a specific font (fN) was set, use its charset
        if (groupState.fontCharset != null) {
            return groupState.fontCharset;
        }

        // Else, if global default font (defN) was set, use that one
        if (globalDefaultFont != -1 && !inHeader) {
            Charset cs = fontToCharset.get(globalDefaultFont);
            if (cs != null) {
                return cs;
            }
        }

        // Else, use the global charset
        if (globalCharset == null) {
            throw new TikaException("unable to determine charset");
        }

        return globalCharset;
    }

    // Handle control word that takes a parameter:
    private void processControlWord(int param, PushbackInputStream in) throws IOException, SAXException, TikaException {

        // TODO: afN?  (associated font number)

        // TODO: do these alter text output...?
        /*
            } else if (equals("stshfdbch")) {
                // font to be used by default in
                // style sheet for East Asian chars
                // arg N is font table entry
            } else if (equals("stshfloch")) {
                // font to be used by default in
                // style sheet for ASCII chars
                // arg N is font table entry
            } else if (equals("stshfhich")) {
                // font to be used by default in
                // style sheet for High Ansi chars
                // arg N is font table entry
            } else if (equals("stshfbi")) {
                // style sheet for Complex Scripts (BIDI) chars
                // arg N is font table entry
                */

        // TODO: inefficient that we check equals N times;
        // we'd get better perf w/ real lexer (eg
        // JFlex), which uses single-pass FSM to do cmp:
        if (inHeader) {
            if (equals("ansicpg")) {
                // ANSI codepage
                Charset cs = ANSICPG_MAP.get(param);
                if (cs != null) {
                    globalCharset = cs;
                }
            } else if (equals("deff")) {
                // Default font
                globalDefaultFont = param;
            } else if (equals("nofpages")) {
                metadata.add(Office.PAGE_COUNT, Integer.toString(param));
            } else if (equals("nofwords")) {
                metadata.add(Office.WORD_COUNT, Integer.toString(param));
            } else if (equals("nofchars")) {
                metadata.add(Office.CHARACTER_COUNT, Integer.toString(param));
            } else if (equals("yr")) {
                year = param;
            } else if (equals("mo")) {
                month = param;
            } else if (equals("dy")) {
                day = param;
            } else if (equals("hr")) {
                hour = param;
            } else if (equals("min")) {
                minute = param;
            }

            if (fontTableState == 1) {
                // Still inside font table -- record the
                // mappings of fN to the fcharset:
                if (groupState.depth < fontTableDepth) {
                    fontTableState = 2;
                } else {
                    if (equals("f")) {
                        // Start new font definition
                        curFontID = param;
                    } else if (equals("fcharset")) {
                        Charset cs = FCHARSET_MAP.get(param);
                        if (cs != null) {
                            fontToCharset.put(curFontID, cs);
                        }
                    }
                }
            }
        } else {
            // In document
            if (equals("b")) {
                // b0
                assert param == 0;
                if (groupState.bold) {
                    pushText();
                    if (groupState.italic) {
                        end("i");
                    }
                    end("b");
                    if (groupState.italic) {
                        start("i");
                    }
                    groupState.bold = false;
                }
            } else if (equals("i")) {
                // i0
                assert param == 0;
                if (groupState.italic) {
                    pushText();
                    end("i");
                    groupState.italic = false;
                }
            } else if (equals("f")) {
                // Change current font
                Charset fontCharset = fontToCharset.get(param);

                // Push any buffered text before changing
                // font:
                pushText();

                if (fontCharset != null) {
                    groupState.fontCharset = fontCharset;
                } else {
                    // DOC ERROR: font change referenced a
                    // non-table'd font number
                    // TODO: log a warning?  Throw an exc?
                    groupState.fontCharset = null;
                }
            }
        }

        // Process unicode escape. This can appear in doc
        // or in header, since the metadata (info) fields
        // in the header can be unicode escaped as well:
        if (equals("u")) {
            // Unicode escape
            if (!groupState.ignore) {
                final char utf16CodeUnit = (char) (param & 0xffff);
                addOutputChar(utf16CodeUnit);
            }

            // After seeing a unicode escape we must
            // skip the next ucSkip ansi chars (the
            // "unicode shadow")
            ansiSkip = groupState.ucSkip;
        } else if (equals("uc")) {
            // Change unicode shadow length
            groupState.ucSkip = (int) param;
        } else if (equals("bin")) {
            if (param >= 0) {
                int bytesToRead = param;
                byte[] tmpArray = new byte[Math.min(1024, bytesToRead)];
                while (bytesToRead > 0) {
                    int r = in.read(tmpArray, 0, Math.min(bytesToRead, tmpArray.length));
                    if (r < 0) {
                        throw new TikaException("unexpected end of file: need " + param + " bytes of binary data, found " + (param-bytesToRead));
                    }
                    bytesToRead -= r;
                }
            } else {
                // log some warning?
            }
        }
    }

    private void end(String tag) throws IOException, SAXException, TikaException {
        out.endElement(tag);
    }

    private void start(String tag) throws IOException, SAXException, TikaException {
        out.startElement(tag);
    }

    // Handle non-parameter control word:
    private void processControlWord() throws IOException, SAXException, TikaException {
        if (inHeader) {
            if (equals("ansi")) {
                globalCharset = WINDOWS_1252;
            } else if (equals("pca")) { 
                globalCharset = CP850;
            } else if (equals("pc")) { 
                globalCharset = CP437;
            } else if (equals("mac")) { 
                globalCharset = MAC_ROMAN;
            }

            if (equals("colortbl") || equals("stylesheet") || equals("fonttbl")) {
                groupState.ignore = true;
            }

            if (uprState == -1) {
                // TODO: we can also parse \creatim, \revtim,
                // \printim, \version, etc.
                if (equals("author")) {
                    nextMetaData = TikaCoreProperties.CREATOR;
                } else if (equals("title")) {
                    nextMetaData = TikaCoreProperties.TITLE;
                } else if (equals("subject")) {
                    // TODO: Move to OO subject in Tika 2.0
                    nextMetaData = TikaCoreProperties.TRANSITION_SUBJECT_TO_OO_SUBJECT;
                } else if (equals("keywords")) {
                    nextMetaData = TikaCoreProperties.TRANSITION_KEYWORDS_TO_DC_SUBJECT;
                } else if (equals("category")) {
                    nextMetaData = OfficeOpenXMLCore.CATEGORY;
                } else if (equals("comment")) {
                    nextMetaData = TikaCoreProperties.COMMENTS;
                } else if (equals("company")) {
                    nextMetaData = OfficeOpenXMLExtended.COMPANY;
                } else if (equals("manager")) {
                    nextMetaData = OfficeOpenXMLExtended.MANAGER;
                } else if (equals("template")) {
                    nextMetaData = OfficeOpenXMLExtended.TEMPLATE;
                } else if (equals("creatim")) {
                    nextMetaData = TikaCoreProperties.CREATED;
                }
            }

            if (fontTableState == 0) {
                // Didn't see font table yet
                if (equals("fonttbl")) {
                    fontTableState = 1;
                    fontTableDepth = groupState.depth;
                }
            } else if (fontTableState == 1) {
                // Inside font table
                if (groupState.depth < fontTableDepth) {
                    fontTableState = 2;
                }
            }

            if (!groupState.ignore && (equals("par") || equals("pard") || equals("sect") || equals("sectd") || equals("plain") || equals("ltrch") || equals("rtlch"))) {
                inHeader = false;
            }
        } else {
            if (equals("b")) {
                if (!groupState.bold) {
                    pushText();
                    lazyStartParagraph();
                    if (groupState.italic) {
                        // Make sure nesting is always <b><i>
                        end("i");
                    }
                    groupState.bold = true;
                    start("b");
                    if (groupState.italic) {
                        start("i");
                    }
                }
            } else if (equals("i")) {
                if (!groupState.italic) {
                    pushText();
                    lazyStartParagraph();
                    groupState.italic = true;
                    start("i");
                }
            }
        }

        final boolean ignored = groupState.ignore;

        if (equals("pard")) {
            // Reset styles
            pushText();
            if (groupState.italic) {
                end("i");
                groupState.italic = false;
            }
            if (groupState.bold) {
                end("b");
                groupState.bold = false;
            }
        } else if (equals("par")) {
            if (!ignored) {
                endParagraph(true);
            }
        } else if (equals("shptxt")) {
            pushText();
            // Text inside a shape
            groupState.ignore = false;
        } else if (equals("atnid")) {
            pushText();
            // Annotation ID
            groupState.ignore = false;
        } else if (equals("atnauthor")) {
            pushText();
            // Annotation author
            groupState.ignore = false;
        } else if (equals("annotation")) {
            pushText();
            // Annotation
            groupState.ignore = false;
        } else if (equals("cell")) {
            // TODO: we should produce a table output here?
            //addOutputChar(' ');
            endParagraph(true);
        } else if (equals("pict")) {
            pushText();
            // TODO: create img tag?  but can that support
            // embedded image data?
            groupState.ignore = true;
        } else if (equals("line")) {
            if (!ignored) {
                addOutputChar('\n');
            }
        } else if (equals("column")) {
            if (!ignored) {
                addOutputChar(' ');
            }
        } else if (equals("page")) {
            if (!ignored) {
                addOutputChar('\n');
            }
        } else if (equals("softline")) {
            if (!ignored) {
                addOutputChar('\n');
            }
        } else if (equals("softcolumn")) {
            if (!ignored) {
                addOutputChar(' ');
            }
        } else if (equals("softpage")) {
            if (!ignored) {
                addOutputChar('\n');
            }
        } else if (equals("tab")) {
            if (!ignored) {
                addOutputChar('\t');
            }
        } else if (equals("upr")) {
            uprState = 0;
        } else if (equals("ud") && uprState == 1) {
            uprState = -1;
            // 2nd group inside the upr destination, which
            // contains the unicode encoding of the text, so
            // we want to keep that:
            groupState.ignore = false;
        } else if (equals("bullet")) {
            if (!ignored) {
                // unicode BULLET
                addOutputChar('\u2022');
            }
        } else if (equals("endash")) {
            if (!ignored) {
                // unicode EN DASH
                addOutputChar('\u2013');
            }
        } else if (equals("emdash")) {
            if (!ignored) {
                // unicode EM DASH
                addOutputChar('\u2014');
            }
        } else if (equals("enspace")) {
            if (!ignored) {
                // unicode EN SPACE
                addOutputChar('\u2002');
            }
        } else if (equals("qmspace")) {
            if (!ignored) {
                // quarter em space -> unicode FOUR-PER-EM SPACE
                addOutputChar('\u2005');
            }
        } else if (equals("emspace")) {
            if (!ignored) {
                // unicode EM SPACE
                addOutputChar('\u2003');
            }
        } else if (equals("lquote")) {
            if (!ignored) {
                // unicode LEFT SINGLE QUOTATION MARK
                addOutputChar('\u2018');
            }
        } else if (equals("rquote")) {
            if (!ignored) {
                // unicode RIGHT SINGLE QUOTATION MARK
                addOutputChar('\u2019');
            }
        } else if (equals("ldblquote")) {
            if (!ignored) {
                // unicode LEFT DOUBLE QUOTATION MARK
                addOutputChar('\u201C');
            }
        } else if (equals("rdblquote")) {
            if (!ignored) {
                // unicode RIGHT DOUBLE QUOTATION MARK
                addOutputChar('\u201D');
            }
        } else if (equals("fldinst")) {
            fieldState = 1;
            groupState.ignore = false;
        } else if (equals("fldrslt") && fieldState == 2) {
            assert pendingURL != null;
            lazyStartParagraph();
            out.startElement("a", "href", pendingURL);
            pendingURL = null;
            fieldState = 3;
            groupState.ignore = false;
        }
    }

    // Push new GroupState
    private void processGroupStart(PushbackInputStream in) throws IOException {
        ansiSkip = 0;
        // Push current groupState onto the stack
        groupStates.add(groupState);

        // Make new GroupState
        groupState = new GroupState(groupState);
        assert groupStates.size() == groupState.depth: "size=" + groupStates.size() + " depth=" + groupState.depth;

        if (uprState == 0) {
            uprState = 1;
            groupState.ignore = true;
        }
        
        // Check for ignorable groups. Note that
        // sometimes we un-ignore within this group, eg
        // when handling upr escape.
        int b2 = in.read();
        if (b2 == '\\') {
            int b3 = in.read();
            if (b3 == '*') {
                groupState.ignore = true;
            }
               in.unread(b3);
        }
        in.unread(b2);
    }

    // Pop current GroupState
    private void processGroupEnd() throws IOException, SAXException, TikaException {

        if (inHeader) {
            if (nextMetaData != null) {
                if (nextMetaData == TikaCoreProperties.CREATED) {
                    Calendar cal = Calendar.getInstance();
                    cal.set(year, month-1, day, hour, minute, 0);
                    metadata.set(nextMetaData, cal.getTime());
                } else if (nextMetaData.isMultiValuePermitted()) {
                    metadata.add(nextMetaData, pendingBuffer.toString());
                } else {
                    metadata.set(nextMetaData, pendingBuffer.toString());
                }
                nextMetaData = null;
            }
            pendingBuffer.setLength(0);
        }

        assert groupState.depth > 0;
        ansiSkip = 0;

        // Be robust if RTF doc is corrupt (has too many
        // closing }s):
        // TODO: log a warning?
        if (groupStates.size() > 0) {
            // Restore group state:
            final GroupState outerGroupState = groupStates.removeLast();

            // Close italic, if outer does not have italic or
            // bold changed:
            if (groupState.italic) {
                if (!outerGroupState.italic ||
                    groupState.bold != outerGroupState.bold) {
                    end("i");
                    groupState.italic = false;
                }
            }

            // Close bold
            if (groupState.bold && !outerGroupState.bold) {
                end("b");
            }

            // Open bold
            if (!groupState.bold && outerGroupState.bold) {
                start("b");
            }

            // Open italic
            if (!groupState.italic && outerGroupState.italic) {
                start("i");
            }
            groupState = outerGroupState;
        }
        assert groupStates.size() == groupState.depth;

        if (fieldState == 1) {
            String s = pendingBuffer.toString().trim();
            pendingBuffer.setLength(0);
            if (s.startsWith("HYPERLINK")) {
                s = s.substring(9).trim();
                // TODO: what other instructions can be in a
                // HYPERLINK destination?
                final boolean isLocalLink = s.indexOf("\\l ") != -1;
                int idx = s.indexOf('"');
                if (idx != -1) {
                    int idx2 = s.indexOf('"', 1+idx);
                    if (idx2 != -1) {
                        s = s.substring(1+idx, idx2);
                    }
                }
                pendingURL = (isLocalLink ? "#" : "") + s;
                fieldState = 2;
            } else {
                fieldState = 0;
            }

            // TODO: we could process the other known field
            // types.  Right now, we will extract their text
            // inlined, but fail to record them in metadata
            // as a field value.
        } else if (fieldState == 3) {
            out.endElement("a");
            fieldState = 0;
        }
    }
}
