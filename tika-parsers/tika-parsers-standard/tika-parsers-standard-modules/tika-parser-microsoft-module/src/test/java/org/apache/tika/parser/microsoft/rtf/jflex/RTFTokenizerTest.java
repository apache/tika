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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class RTFTokenizerTest {

    private List<RTFToken> tokenize(String input) throws Exception {
        RTFTokenizer tokenizer = new RTFTokenizer(new StringReader(input));
        List<RTFToken> tokens = new ArrayList<>();
        RTFToken tok;
        while ((tok = tokenizer.yylex()) != null) {
            if (tok.getType() == RTFTokenType.EOF) {
                break;
            }
            // copy token since it's reused
            RTFToken copy = new RTFToken();
            copy.set(tok.getType(), tok.getName(), tok.getParameter(), tok.hasParameter());
            tokens.add(copy);
        }
        return tokens;
    }

    @Test
    public void testGroupOpenClose() throws Exception {
        List<RTFToken> tokens = tokenize("{}");
        assertEquals(2, tokens.size());
        assertEquals(RTFTokenType.GROUP_OPEN, tokens.get(0).getType());
        assertEquals(RTFTokenType.GROUP_CLOSE, tokens.get(1).getType());
    }

    @Test
    public void testControlWord() throws Exception {
        List<RTFToken> tokens = tokenize("\\rtf1");
        assertEquals(1, tokens.size());
        assertEquals(RTFTokenType.CONTROL_WORD, tokens.get(0).getType());
        assertEquals("rtf", tokens.get(0).getName());
        assertEquals(1, tokens.get(0).getParameter());
        assertTrue(tokens.get(0).hasParameter());
    }

    @Test
    public void testControlWordNoParam() throws Exception {
        List<RTFToken> tokens = tokenize("\\ansi");
        assertEquals(1, tokens.size());
        assertEquals(RTFTokenType.CONTROL_WORD, tokens.get(0).getType());
        assertEquals("ansi", tokens.get(0).getName());
        assertFalse(tokens.get(0).hasParameter());
    }

    @Test
    public void testControlWordNegativeParam() throws Exception {
        List<RTFToken> tokens = tokenize("\\u-4321");
        assertEquals(1, tokens.size());
        assertEquals(RTFTokenType.UNICODE_ESCAPE, tokens.get(0).getType());
        // -4321 → 65536 - 4321 = 61215
        assertEquals(61215, tokens.get(0).getParameter());
    }

    @Test
    public void testHexEscape() throws Exception {
        List<RTFToken> tokens = tokenize("\\'e9");
        assertEquals(1, tokens.size());
        assertEquals(RTFTokenType.HEX_ESCAPE, tokens.get(0).getType());
        assertEquals(0xe9, tokens.get(0).getHexValue());
    }

    @Test
    public void testUnicodeEscape() throws Exception {
        List<RTFToken> tokens = tokenize("\\u8212");
        assertEquals(1, tokens.size());
        assertEquals(RTFTokenType.UNICODE_ESCAPE, tokens.get(0).getType());
        assertEquals(8212, tokens.get(0).getParameter());
    }

    @Test
    public void testBinControl() throws Exception {
        List<RTFToken> tokens = tokenize("\\bin1024");
        assertEquals(1, tokens.size());
        assertEquals(RTFTokenType.BIN, tokens.get(0).getType());
        assertEquals(1024, tokens.get(0).getParameter());
    }

    @Test
    public void testControlSymbol() throws Exception {
        List<RTFToken> tokens = tokenize("\\~");
        assertEquals(1, tokens.size());
        assertEquals(RTFTokenType.CONTROL_SYMBOL, tokens.get(0).getType());
        assertEquals("~", tokens.get(0).getName());
    }

    @Test
    public void testEscapedBraces() throws Exception {
        List<RTFToken> tokens = tokenize("\\{\\}\\\\");
        assertEquals(3, tokens.size());
        assertEquals(RTFTokenType.CONTROL_SYMBOL, tokens.get(0).getType());
        assertEquals("{", tokens.get(0).getName());
        assertEquals(RTFTokenType.CONTROL_SYMBOL, tokens.get(1).getType());
        assertEquals("}", tokens.get(1).getName());
        assertEquals(RTFTokenType.CONTROL_SYMBOL, tokens.get(2).getType());
        assertEquals("\\", tokens.get(2).getName());
    }

    @Test
    public void testText() throws Exception {
        List<RTFToken> tokens = tokenize("Hello");
        assertEquals(5, tokens.size()); // one char at a time
        for (RTFToken t : tokens) {
            assertEquals(RTFTokenType.TEXT, t.getType());
        }
        StringBuilder sb = new StringBuilder();
        for (RTFToken t : tokens) {
            sb.append(t.getName());
        }
        assertEquals("Hello", sb.toString());
    }

    @Test
    public void testCrLf() throws Exception {
        List<RTFToken> tokens = tokenize("a\r\nb");
        assertEquals(3, tokens.size());
        assertEquals(RTFTokenType.TEXT, tokens.get(0).getType());
        assertEquals(RTFTokenType.CRLF, tokens.get(1).getType());
        assertEquals(RTFTokenType.TEXT, tokens.get(2).getType());
    }

    @Test
    public void testIgnorableDestination() throws Exception {
        // {  \*  \htmltag84_  <  p  >  }
        // The space after \htmltag84 is consumed as the control word delimiter
        List<RTFToken> tokens = tokenize("{\\*\\htmltag84 <p>}");
        assertEquals(RTFTokenType.GROUP_OPEN, tokens.get(0).getType());
        assertEquals(RTFTokenType.CONTROL_SYMBOL, tokens.get(1).getType());
        assertEquals("*", tokens.get(1).getName());
        assertEquals(RTFTokenType.CONTROL_WORD, tokens.get(2).getType());
        assertEquals("htmltag", tokens.get(2).getName());
        assertEquals(84, tokens.get(2).getParameter());
        // remaining tokens are < p > }
        assertEquals(RTFTokenType.TEXT, tokens.get(3).getType());
        assertEquals("<", tokens.get(3).getName());
        assertEquals(RTFTokenType.TEXT, tokens.get(4).getType());
        assertEquals("p", tokens.get(4).getName());
        assertEquals(RTFTokenType.TEXT, tokens.get(5).getType());
        assertEquals(">", tokens.get(5).getName());
        assertEquals(RTFTokenType.GROUP_CLOSE, tokens.get(6).getType());
        assertEquals(7, tokens.size());
    }

    @Test
    public void testMixedRtf() throws Exception {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252 Hello}";
        List<RTFToken> tokens = tokenize(rtf);
        // { \rtf1 \ansi \ansicpg1252 SPACE H e l l o }
        assertEquals(RTFTokenType.GROUP_OPEN, tokens.get(0).getType());
        assertEquals(RTFTokenType.CONTROL_WORD, tokens.get(1).getType());
        assertEquals("rtf", tokens.get(1).getName());
        assertEquals(1, tokens.get(1).getParameter());
        assertEquals(RTFTokenType.CONTROL_WORD, tokens.get(2).getType());
        assertEquals("ansi", tokens.get(2).getName());
        assertEquals(RTFTokenType.CONTROL_WORD, tokens.get(3).getType());
        assertEquals("ansicpg", tokens.get(3).getName());
        assertEquals(1252, tokens.get(3).getParameter());
    }
}
