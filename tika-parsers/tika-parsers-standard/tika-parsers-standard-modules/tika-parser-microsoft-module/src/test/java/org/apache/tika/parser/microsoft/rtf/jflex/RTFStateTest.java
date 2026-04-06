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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.StringReader;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

public class RTFStateTest {

    private RTFState processRtf(String rtf) throws Exception {
        RTFTokenizer tokenizer = new RTFTokenizer(new StringReader(rtf));
        RTFState state = new RTFState();
        RTFToken tok;
        while ((tok = tokenizer.yylex()) != null) {
            if (tok.getType() == RTFTokenType.EOF) {
                break;
            }
            state.processToken(tok);
        }
        return state;
    }

    @Test
    public void testGlobalCharsetFromAnsicpg() throws Exception {
        RTFState state = processRtf("{\\rtf1\\ansi\\ansicpg1251}");
        assertEquals(Charset.forName("CP1251"), state.getGlobalCharset());
    }

    @Test
    public void testGlobalCharsetDefaultWindows1252() throws Exception {
        RTFState state = processRtf("{\\rtf1\\ansi}");
        assertEquals(RTFCharsetMaps.WINDOWS_1252, state.getGlobalCharset());
    }

    @Test
    public void testGlobalCharsetPca() throws Exception {
        RTFState state = processRtf("{\\rtf1\\pca}");
        assertEquals(Charset.forName("cp850"), state.getGlobalCharset());
    }

    @Test
    public void testGlobalCharsetPc() throws Exception {
        RTFState state = processRtf("{\\rtf1\\pc}");
        assertEquals(Charset.forName("cp437"), state.getGlobalCharset());
    }

    @Test
    public void testGlobalCharsetMac() throws Exception {
        RTFState state = processRtf("{\\rtf1\\mac}");
        assertEquals(Charset.forName("MacRoman"), state.getGlobalCharset());
    }

    @Test
    public void testFontTableParsing() throws Exception {
        // Realistic font table: f0=Times New Roman (ANSI), f1=MS Mincho (Shift_JIS)
        String rtf = "{\\rtf1\\ansi\\deff0" +
                "{\\fonttbl" +
                "{\\f0\\froman\\fcharset0 Times New Roman;}" +
                "{\\f1\\fnil\\fcharset128 MS Mincho;}" +
                "}" +
                "\\f0 Hello}";
        RTFState state = processRtf(rtf);

        // fcharset 0 = ANSI = WINDOWS-1252
        assertEquals(RTFCharsetMaps.WINDOWS_1252, state.getFontToCharset().get(0));
        // fcharset 128 = Shift JIS = MS932
        assertEquals(Charset.forName("MS932"), state.getFontToCharset().get(1));
    }

    @Test
    public void testCurrentCharsetFollowsFont() throws Exception {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\deff0" +
                "{\\fonttbl" +
                "{\\f0\\froman\\fcharset0 Times;}" +
                "{\\f1\\fnil\\fcharset161 Greek;}" +
                "}" +
                "\\f1 text}";
        RTFTokenizer tokenizer = new RTFTokenizer(new java.io.StringReader(rtf));
        RTFState state = new RTFState();
        Charset charsetAtText = null;

        RTFToken tok;
        while ((tok = tokenizer.yylex()) != null) {
            if (tok.getType() == RTFTokenType.EOF) {
                break;
            }
            state.processToken(tok);
            // Capture charset when we see the first body text char
            if (tok.getType() == RTFTokenType.TEXT && "t".equals(tok.getName())
                    && charsetAtText == null) {
                charsetAtText = state.getCurrentCharset();
            }
        }

        // Verify font table was populated
        assertEquals(2, state.getFontToCharset().size());
        assertEquals(Charset.forName("cp1253"), state.getFontToCharset().get(1));

        // After \f1, charset should be cp1253 (Greek)
        assertNotNull(charsetAtText);
        assertEquals(Charset.forName("cp1253"), charsetAtText);
    }

    @Test
    public void testCurrentCharsetFallsBackToGlobal() throws Exception {
        String rtf = "{\\rtf1\\ansi\\ansicpg1254\\deff0" +
                "{\\fonttbl" +
                "{\\f0\\froman\\fcharset0 Times;}" +
                "}" +
                "\\f0 text}";
        RTFState state = processRtf(rtf);

        // fcharset 0 = WINDOWS-1252 (ANSI)
        assertEquals(RTFCharsetMaps.WINDOWS_1252, state.getCurrentCharset());
    }

    @Test
    public void testDefaultFontCharset() throws Exception {
        // \deff1 sets default font to f1, which maps to fcharset 162 (Turkish = cp1254)
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\deff1" +
                "{\\fonttbl" +
                "{\\f0\\froman\\fcharset0 Times;}" +
                "{\\f1\\fnil\\fcharset162 Arial;}" +
                "}" +
                "\\pard text}";
        RTFState state = processRtf(rtf);

        // No explicit \fN in body, so should fall back to deff1 -> fcharset 162 -> cp1254
        assertEquals(Charset.forName("cp1254"), state.getCurrentCharset());
    }

    @Test
    public void testUcSkipInherited() throws Exception {
        // RTF uc control word sets skip count to 2, inherited by child groups
        // We process token-by-token and check inside the inner group
        String rtf = "{\\rtf1\\ansi\\uc2{inner}}";
        RTFTokenizer tokenizer = new RTFTokenizer(new java.io.StringReader(rtf));
        RTFState state = new RTFState();

        int ucSkipInInnerGroup = -1;
        boolean seenInnerText = false;
        RTFToken tok;
        while ((tok = tokenizer.yylex()) != null) {
            if (tok.getType() == RTFTokenType.EOF) {
                break;
            }
            state.processToken(tok);
            // Check ucSkip when we see the first char of "inner"
            if (tok.getType() == RTFTokenType.TEXT && "i".equals(tok.getName()) && !seenInnerText) {
                ucSkipInInnerGroup = state.getCurrentGroup().ucSkip;
                seenInnerText = true;
            }
        }
        // Inside {inner}, ucSkip should be inherited as 2 from parent
        assertEquals(2, ucSkipInInnerGroup);
    }

    @Test
    public void testAnsiSkipAfterUnicode() throws Exception {
        // After \u8212, the next ucSkip (default 1) ANSI chars should be skipped
        String rtf = "{\\rtf1\\ansi\\ansicpg1252" +
                "{\\fonttbl{\\f0\\fcharset0 Times;}}" +
                "\\f0 A\\u8212\\'97B}";
        RTFTokenizer tokenizer = new RTFTokenizer(new StringReader(rtf));
        RTFState state = new RTFState();
        StringBuilder textOutput = new StringBuilder();

        RTFToken tok;
        while ((tok = tokenizer.yylex()) != null) {
            if (tok.getType() == RTFTokenType.EOF) {
                break;
            }
            boolean consumed = state.processToken(tok);
            if (!consumed && !state.getCurrentGroup().ignore) {
                if (tok.getType() == RTFTokenType.TEXT) {
                    textOutput.append(tok.getName());
                } else if (tok.getType() == RTFTokenType.UNICODE_ESCAPE) {
                    int cp = tok.getParameter();
                    if (Character.isValidCodePoint(cp)) {
                        textOutput.appendCodePoint(cp);
                    }
                }
            }
        }
        // A + \u8212 (em dash) + B.  The \'97 should be skipped as unicode shadow.
        assertEquals("A\u2014B", textOutput.toString());
    }

    @Test
    public void testGroupStateRestored() throws Exception {
        // Font change inside a group should be reverted when group closes
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\deff0" +
                "{\\fonttbl" +
                "{\\f0\\fcharset0 Times;}" +
                "{\\f1\\fcharset161 Greek;}" +
                "}" +
                "\\f0 {\\f1 greek}{back to times}}";
        RTFTokenizer tokenizer = new RTFTokenizer(new StringReader(rtf));
        RTFState state = new RTFState();

        Charset charsetInsideGroup = null;
        Charset charsetAfterGroup = null;
        boolean seenGreekGroup = false;
        int bodyGroupDepth = 0;

        RTFToken tok;
        while ((tok = tokenizer.yylex()) != null) {
            if (tok.getType() == RTFTokenType.EOF) {
                break;
            }
            state.processToken(tok);

            if (tok.getType() == RTFTokenType.TEXT) {
                String text = tok.getName();
                if ("g".equals(text) && !seenGreekGroup) {
                    // First char of "greek"
                    charsetInsideGroup = state.getCurrentCharset();
                    seenGreekGroup = true;
                } else if ("b".equals(text)) {
                    // First char of "back to times"
                    charsetAfterGroup = state.getCurrentCharset();
                }
            }
        }

        assertNotNull(charsetInsideGroup);
        assertNotNull(charsetAfterGroup);
        // Inside the {\f1 ...} group, charset should be Greek (cp1253)
        assertEquals(Charset.forName("cp1253"), charsetInsideGroup);
        // After the group closes, should be back to f0 (WINDOWS-1252)
        assertEquals(RTFCharsetMaps.WINDOWS_1252, charsetAfterGroup);
    }
}
