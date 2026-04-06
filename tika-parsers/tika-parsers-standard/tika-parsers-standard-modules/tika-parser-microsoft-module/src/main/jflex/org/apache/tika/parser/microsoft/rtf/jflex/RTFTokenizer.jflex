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

    private RTFToken controlWord(String text) {
        // text is the full match including leading backslash and optional trailing
        // delimiter space, e.g. "\\fonttbl", "\\f123 ", "\\ansi "
        // strip leading backslash
        String body = text.substring(1);
        // strip trailing delimiter space if present
        if (body.endsWith(" ")) {
            body = body.substring(0, body.length() - 1);
        }

        // split into name and optional numeric parameter
        int i = 0;
        while (i < body.length() && Character.isLetter(body.charAt(i))) {
            i++;
        }
        String name = body.substring(0, i);
        if (i < body.length()) {
            // there is a numeric parameter (possibly negative)
            String paramStr = body.substring(i);
            int param = Integer.parseInt(paramStr);
            token.set(RTFTokenType.CONTROL_WORD, name, param, true);
        } else {
            token.set(RTFTokenType.CONTROL_WORD, name, -1, false);
        }
        return token;
    }

    private RTFToken hexEscape(String text) {
        // text is e.g. "\\'ab"
        int hi = Character.digit(text.charAt(2), 16);
        int lo = Character.digit(text.charAt(3), 16);
        token.set(RTFTokenType.HEX_ESCAPE, null, (hi << 4) | lo, true);
        return token;
    }

    private RTFToken unicodeEscape(String text) {
        // text is e.g. "\\u12345" or "\\u-4321 " (may have trailing delimiter space)
        String numStr = text.substring(2).trim();
        int codePoint = Integer.parseInt(numStr);
        // RTF uses signed 16-bit: negative values map to 65536 + value
        if (codePoint < 0) {
            codePoint = 65536 + codePoint;
        }
        token.set(RTFTokenType.UNICODE_ESCAPE, null, codePoint, true);
        return token;
    }

    private RTFToken binToken(String text) {
        // text is e.g. "\\bin12345 " (may have trailing delimiter space)
        String numStr = text.substring(4).trim();
        int count = Integer.parseInt(numStr);
        token.set(RTFTokenType.BIN, null, count, true);
        return token;
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

{BinControl}             { return binToken(yytext()); }
{UnicodeEscape}          { return unicodeEscape(yytext()); }
{HexEscape}              { return hexEscape(yytext()); }
{ControlWordWithParam}   { return controlWord(yytext()); }
{ControlWord}            { return controlWord(yytext()); }
{ControlSymbol}          { token.setChar(RTFTokenType.CONTROL_SYMBOL, yycharat(1)); return token; }
{GroupOpen}              { token.reset(RTFTokenType.GROUP_OPEN); return token; }
{GroupClose}             { token.reset(RTFTokenType.GROUP_CLOSE); return token; }
{CrLf}                   { token.reset(RTFTokenType.CRLF); return token; }

/* Text: one char at a time. Uses yycharat(0) to avoid String allocation. */
[^\\\{\}\r\n]            { token.setChar(RTFTokenType.TEXT, yycharat(0)); return token; }

<<EOF>>                  { token.reset(RTFTokenType.EOF); return token; }
