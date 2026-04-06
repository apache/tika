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

%%

%public
%class RTFTokenizer
%unicode
%type RTFToken
%char

%{
    private final RTFToken token = new RTFToken();

    /**
     * Returns the reusable token instance. Callers must copy data
     * before the next call to {@link #yylex()}.
     */
    public RTFToken getToken() {
        return token;
    }

    /** Control word with parameter: \ letters [-] digits [space] */
    private RTFToken controlWordWithParam() {
        int len = yylength();
        if (yycharat(len - 1) == ' ') {
            len--;
        }
        // find where letters end
        int nameEnd = 1;
        while (nameEnd < len && Character.isLetter(yycharat(nameEnd))) {
            nameEnd++;
        }
        String name = new String(zzBuffer, zzStartRead + 1, nameEnd - 1);
        int param = parseIntFromBuffer(nameEnd, len);
        token.set(RTFTokenType.CONTROL_WORD, name, param, true);
        return token;
    }

    /** Control word without parameter: \ letters [space] */
    private RTFToken controlWord() {
        int len = yylength();
        if (yycharat(len - 1) == ' ') {
            len--;
        }
        String name = new String(zzBuffer, zzStartRead + 1, len - 1);
        token.set(RTFTokenType.CONTROL_WORD, name, -1, false);
        return token;
    }

    private RTFToken hexEscape() {
        // layout: \' hex hex  (4 chars)
        int hi = Character.digit(yycharat(2), 16);
        int lo = Character.digit(yycharat(3), 16);
        token.set(RTFTokenType.HEX_ESCAPE, null, (hi << 4) | lo, true);
        return token;
    }

    private RTFToken unicodeEscape() {
        // layout: backslash u [-] digits [space]
        int len = yylength();
        if (yycharat(len - 1) == ' ') {
            len--;
        }
        int codePoint = parseIntFromBuffer(2, len);
        // RTF uses signed 16-bit: negative values map to 65536 + value
        if (codePoint < 0) {
            codePoint = 65536 + codePoint;
        }
        token.set(RTFTokenType.UNICODE_ESCAPE, null, codePoint, true);
        return token;
    }

    private RTFToken binToken() {
        // layout: \bin digits [space]
        int len = yylength();
        if (yycharat(len - 1) == ' ') {
            len--;
        }
        int count = parseIntFromBuffer(4, len);
        token.set(RTFTokenType.BIN, null, count, true);
        return token;
    }

    /**
     * Parse an integer from JFlex's internal char buffer between positions
     * start (inclusive) and end (exclusive), relative to the current match.
     * Handles optional leading '-'.
     */
    private int parseIntFromBuffer(int start, int end) {
        boolean neg = false;
        int pos = start;
        if (yycharat(pos) == '-') {
            neg = true;
            pos++;
        }
        int result = 0;
        while (pos < end) {
            result = result * 10 + (yycharat(pos) - '0');
            pos++;
        }
        return neg ? -result : result;
    }
%}

/* RTF is 7-bit ASCII; bytes above 127 are escaped. We read as Latin1/byte stream. */

/* RTF spec: a control word's delimiter space is consumed and not part of the output.
   We include the optional trailing space in each pattern so the tokenizer eats it. */
ControlWordWithParam = "\\" [a-zA-Z]+ "-"? [0-9]+ " "?
ControlWord = "\\" [a-zA-Z]+ " "?
HexEscape = "\\'" [0-9a-fA-F]{2}
UnicodeEscape = "\\u" "-"? [0-9]+ " "?
BinControl = "\\bin" [0-9]+ " "?
ControlSymbol = "\\" [^a-zA-Z0-9\r\n]
GroupOpen = "{"
GroupClose = "}"
CrLf = \r\n | \r | \n

%%

/* Order matters: more specific rules first */

{BinControl}             { return binToken(); }
{UnicodeEscape}          { return unicodeEscape(); }
{HexEscape}              { return hexEscape(); }
{ControlWordWithParam}   { return controlWordWithParam(); }
{ControlWord}            { return controlWord(); }
{ControlSymbol}          { token.setChar(RTFTokenType.CONTROL_SYMBOL, yycharat(1)); return token; }
{GroupOpen}              { token.reset(RTFTokenType.GROUP_OPEN); return token; }
{GroupClose}             { token.reset(RTFTokenType.GROUP_CLOSE); return token; }
{CrLf}                   { token.reset(RTFTokenType.CRLF); return token; }

/* Text: one char at a time. Uses yycharat(0) to avoid String allocation. */
[^\\\{\}\r\n]            { token.setChar(RTFTokenType.TEXT, yycharat(0)); return token; }

<<EOF>>                  { token.reset(RTFTokenType.EOF); return token; }
