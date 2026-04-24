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
package org.apache.tika.parser.html;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public class HtmlEncodingDetectorTest {

    @Test
    public void basic() throws IOException {
        assertWindows1252("<meta charset='WINDOWS-1252'>");
    }

    @Test
    @Disabled("can we can prove this harms detection")
    public void utf16() throws IOException {
        // According to the specification 'If charset is a UTF-16 encoding,
        // then set charset to UTF-8.'
        assertCharset("<meta charset='UTF-16BE'>", StandardCharsets.UTF_8);
    }

    @Test
    public void xUserDefined() throws IOException {
        // According to the specification 'If charset is x-user-defined,
        // then set charset to windows-1252.'
        assertWindows1252("<meta charset='x-user-defined'>");
    }

    @Test
    public void iso88591IsWindows1252() throws IOException {
        // WHATWG: iso-8859-1 is an alias for windows-1252.
        assertWindows1252("<meta charset='iso-8859-1'>");
    }

    @Test
    public void usAsciiIsWindows1252() throws IOException {
        assertWindows1252("<meta charset='us-ascii'>");
    }

    @Test
    public void iso88599IsWindows1254() throws IOException {
        assertCharset("<meta charset='iso-8859-9'>", Charset.forName("windows-1254"));
    }

    @Test
    public void tis620IsWindows874() throws IOException {
        assertCharset("<meta charset='tis-620'>", Charset.forName("windows-874"));
    }

    @Test
    public void gb2312IsGbk() throws IOException {
        assertCharset("<meta charset='gb2312'>", Charset.forName("GBK"));
    }

    @Test
    public void ms932IsShiftJis() throws IOException {
        assertCharset("<meta charset='ms932'>", Charset.forName("Shift_JIS"));
    }

    @Test
    public void ms949IsXWindows949() throws IOException {
        // Tika convention (differs from WHATWG which downgrades to EUC-KR):
        // route MS949 labels to x-windows-949 to preserve extension bytes.
        assertCharset("<meta charset='ms949'>", Charset.forName("x-windows-949"));
        assertCharset("<meta charset='windows-949'>", Charset.forName("x-windows-949"));
    }

    @Test
    public void nakedUtf16IsUtf16Le() throws IOException {
        // WHATWG: naked 'utf-16' (no BOM) defaults to UTF-16LE.
        assertCharset("<meta charset='utf-16'>", StandardCharsets.UTF_16LE);
    }

    @Test
    public void hebrewLabelIsIso88598() throws IOException {
        assertCharset("<meta charset='hebrew'>", Charset.forName("ISO-8859-8"));
    }

    @Test
    public void iso2022KrIsNotReplaced() throws IOException {
        // WHATWG replaces iso-2022-kr with a dummy "replacement" decoder;
        // Tika keeps the real ISO-2022-KR charset because we want to extract
        // text, not block attacks.
        assertCharset("<meta charset='iso-2022-kr'>", Charset.forName("ISO-2022-KR"));
    }

    @Test
    public void withSlash() throws IOException {
        assertWindows1252("<meta/charset='WINDOWS-1252'>");
    }

    @Test
    @Disabled("until we do a full parse")
    public void insideTag() throws IOException {
        assertWindows1252("<meta name='description'" +
                "content='If I write charset=UTF-8 here, it doesnt mean the page is in UTF-8'/>" +
                "<meta charset='WINDOWS-1252'>");
    }

    @Test
    @Disabled("until we do a full parse")
    public void missingAttribute() throws IOException {
        assertWindows1252("<meta content='charset=UTF-8'>" + // missing http-equiv attribute
                "<meta charset='WINDOWS-1252'>" // valid declaration
        );
    }

    @Test
    @Disabled("until we do a full parse")
    public void insideSpecialTag() throws IOException {
        // Content inside <?, <!, and </ should be ignored
        for (byte b : "?!/".getBytes(StandardCharsets.US_ASCII))
            assertWindows1252("<" + (char) b + // start comment
                    "<meta charset='UTF-8'>" + // inside special tag
                    "<meta charset='WINDOWS-1252'>" // real charset declaration
            );
    }

    @Test
    @Disabled("until we can prove this harms detection")
    public void spaceBeforeTag() throws IOException {
        assertWindows1252("< meta charset='UTF-8'>" + // invalid charset declaration
                "<meta charset='WINDOWS-1252'>" // real charset declaration
        );
    }

    @Test
    public void invalidAttribute() throws IOException {
        assertWindows1252("<meta " + "badcharset='UTF-8' " + // invalid charset declaration
                "charset='WINDOWS-1252'>" // real charset declaration
        );
    }

    @Test
    @Disabled("until we can prove this harms detection")
    public void unmatchedQuote() throws IOException {
        assertWindows1252("<meta http-equiv='content-type' content='charset=\"UTF-8'>" +
                // invalid charset declaration
                "<meta charset='WINDOWS-1252'>" // real charset declaration
        );
    }


    @Test
    @Disabled("until we do a full parse")
    public void withCompactComment() throws IOException {
        // <!--> is a valid comment
        assertWindows1252("<!--" + // start comment
                "<meta charset='UTF-8'>" + // inside comment
                "-->" + // end comment
                "<!-->" + // compact comment
                "<meta charset='WINDOWS-1252'>" // outside comment, charset declaration
        );
    }

    private void assertWindows1252(String html) throws IOException {
        assertCharset(html, Charset.forName("WINDOWS-1252"));
    }

    private void assertCharset(String html, Charset charset) throws IOException {
        assertEquals(charset, detectCharset(html),
                html + " should be detected as " + charset);
    }

    private Charset detectCharset(String test) throws IOException {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(test.getBytes(StandardCharsets.UTF_8))) {
            List<EncodingResult> results =
                    new HtmlEncodingDetector().detect(tis, metadata, new ParseContext());
            return results.isEmpty() ? null : results.get(0).getCharset();
        }
    }
}
