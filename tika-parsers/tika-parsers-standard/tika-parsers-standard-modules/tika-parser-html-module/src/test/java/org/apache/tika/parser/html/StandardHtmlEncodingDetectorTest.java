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


import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.junit.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.html.charsetdetector.StandardHtmlEncodingDetector;
import org.apache.tika.parser.html.charsetdetector.charsets.ReplacementCharset;

public class StandardHtmlEncodingDetectorTest {
    private Metadata metadata = new Metadata();

    @Before
    public void setUp() {
        this.metadata = new Metadata();
    }

    @Test
    public void basic() throws IOException {
        assertWindows1252("<meta charset=WINDOWS-1252>");
    }

    @Test
    public void quoted() throws IOException {
        assertWindows1252("<meta charset='WINDOWS-1252'>");
    }

    @Test
    public void duplicateMeta() throws IOException {
        assertWindows1252("<meta charset='WINDOWS-1252'>" + "<meta charset='UTF-8'>");
    }

    @Test
    public void duplicateAttribute() throws IOException {
        assertWindows1252("<meta charset='WINDOWS-1252' charset='UTF-8'>");
    }

    @Test
    public void invalidThenValid() throws IOException {
        assertCharset("<meta charset=blah>" + "<meta charset=WINDOWS-1252>", null);
    }

    @Test
    public void spacesInAttributes() throws IOException {
        assertWindows1252("<meta charset\f=  \t  WINDOWS-1252>");
    }

    @Test
    public void httpEquiv() throws IOException {
        assertWindows1252("<meta " + "http-equiv='content-type' " +
                "content='text/html; charset=\"WINDOWS-1252\"'>"); // quotes around the
        // charset are allowed
        assertWindows1252("<meta " + "content=' charset  =  WINDOWS-1252' " +
                // The charset may be anywhere in the content attribute
                "http-equiv='content-type' >");
    }

    @Test
    public void emptyAttributeEnd() throws IOException {
        assertWindows1252("<meta charset=WINDOWS-1252 a>");
    }

    @Test
    public void httpEquivDuplicateCharset() throws IOException {
        assertWindows1252(
                "<meta " + "http-equiv='content-type' " + "content='charset=WINDOWS-1252;" +
                        // The detection should stop after the semicolon
                        "charset=UTF-8'>");
    }

    @Test
    public void htmlFragment() throws IOException {
        assertWindows1252("<!doctype html><html class=nojs><head><meta charset='WINDOWS-1252'>");
    }

    @Test
    public void veryBadHtml() throws IOException {
        // check that the parser is not confused by garbage before the declaration
        assertWindows1252("<< l \" == / '=x\n >" + "<!--> " + "< <x'/ <=> " + "<meta/>" + "<meta>" +
                "<a x/>" + "<meta charset='WINDOWS-1252'>");
    }

    @Test
    public void specialTag() throws IOException {
        // special tags cannot have arguments, any '>' ends them
        assertWindows1252("<? x='><meta charset='WINDOWS-1252'>");
    }

    @Test
    public void longHtml() throws IOException {
        StringBuilder sb = new StringBuilder(
                "<!doctype html>\n" + "<html>\n" + "<head>\n" + "<title>Hello world</title>\n");
        String repeated = "<meta x='y' />\n";
        String charsetMeta = "<meta charset='windows-1252'>";

        while (sb.length() + repeated.length() + charsetMeta.length() < 1024) sb.append(repeated);

        sb.append(charsetMeta);

        assertWindows1252(sb.toString());
    }

    @Test
    public void tooLong() throws IOException {
        // Create a string with 1Mb of '\0' followed by a meta
        String padded = new String(new byte[1000000], StandardCharsets.ISO_8859_1) +
                "<meta charset='windows-1252'>";
        // Only the first bytes should be prescanned, so the algorithm should stop before
        // the meta tag
        assertCharset(padded, null);
    }

    @Test
    public void incompleteMeta() throws IOException {
        assertCharset("<meta charset='WINDOWS-1252'", null); // missing '>' at the end
    }

    @Test
    public void charsetWithWhiteSpaces() throws IOException {
        assertWindows1252("<meta charset='   \t\n  WINDOWS-1252 \t\n'>");
    }

    @Test
    public void mixedCase() throws IOException {
        assertWindows1252("<mEtA chArsEt='WInDOWs-1252'>");
    }

    @Test
    public void utf16() throws IOException {
        // According to the specification 'If charset is a UTF-16 encoding, then set
        // charset to UTF-8.'
        assertCharset("<meta charset='UTF-16BE'>", StandardCharsets.UTF_8);
    }

    @Test
    public void xUserDefined() throws IOException {
        // According to the specification 'If charset is x-user-defined, then set charset
        // to windows-1252.'
        assertWindows1252("<meta charset='x-user-defined'>");
    }

    @Test
    public void replacement() throws IOException {
        // Several dangerous charsets should are aliases of 'replacement' in the spec
        String inString = "<meta charset='iso-2022-cn'>";
        assertCharset(new ByteArrayInputStream(inString.getBytes(StandardCharsets.ISO_8859_1)),
                new ReplacementCharset());
    }

    @Test
    public void iso88591() throws IOException {
        // In the spec, iso-8859-1 is an alias for WINDOWS-1252
        assertWindows1252("<meta charset='iso-8859-1'>");
    }

    @Test
    public void macintoshEncoding() throws IOException {
        // The mac roman encoding exists in java, but under the name x-MacRoman
        assertCharset("<meta charset='macintosh'>", Charset.forName("x-MacRoman"));
    }

    @Test
    public void bom() throws IOException {
        // A BOM should have precedence over the meta
        assertCharset("\ufeff<meta charset='WINDOWS-1252'>", StandardCharsets.UTF_8);
        assertCharset("\ufeff<meta charset='WINDOWS-1252'>", StandardCharsets.UTF_16LE);
        assertCharset("\ufeff<meta charset='WINDOWS-1252'>", StandardCharsets.UTF_16BE);
    }

    @Test
    public void withSlash() throws IOException {
        assertWindows1252("<meta/charset='WINDOWS-1252'>");
    }

    @Test
    public void insideDescription() throws IOException {
        assertWindows1252("<meta name='description'" +
                "content='If I write charset=UTF-8 here, it doesnt mean the page is in UTF-8'/>" +
                "<meta charset='WINDOWS-1252'>");
    }

    @Test
    public void insideTag() throws IOException {
        assertWindows1252("<tag " + "attribute=\"<meta charset='UTF-8'>\" " + // inside attribute
                "<meta charset='UTF-8' " + // still inside tag
                "/>" + // tag end
                "<meta charset='WINDOWS-1252'>");
    }

    @Test
    public void missingAttribute() throws IOException {
        assertWindows1252("<meta content='charset=UTF-8'>" + // missing http-equiv attribute
                "<meta charset='WINDOWS-1252'>" // valid declaration
        );
    }

    @Test
    public void insideSpecialTag() throws IOException {
        // Content inside <?, <!, and </ should be ignored
        for (byte b : "?!/".getBytes(StandardCharsets.US_ASCII))
            assertWindows1252("<" + (char) b + // start comment
                    "<meta charset='UTF-8'>" + // inside special tag
                    "<meta charset='WINDOWS-1252'>" // real charset declaration
            );
    }

    @Test
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
    public void unmatchedQuote() throws IOException {
        assertWindows1252("<meta http-equiv='content-type' content='charset=\"UTF-8'>" +
                // invalid charset declaration
                "<meta charset='WINDOWS-1252'>" // real charset declaration
        );
    }

    @Test
    public void realWorld() throws IOException {
        assertWindows1252("<!DOCTYPE html>\n" + "<html lang=\"fr\">\n" + "<head>\n" +
                "<script>(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':\n" +
                "\t\t\tnew Date().getTime(),event:'gtm.js'});var " +
                "f=d.getElementsByTagName(s)[0],\n" +
                "\t\t\tj=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src=\n" +
                "\t\t\t'https://www.googletagmanager.com/gtm.js?id='+i+dl;" +
                "f.parentNode.insertBefore(j,f);\n" +
                "\t\t\t})(window,document,'script','dataLayer','GTM-PNX8H8X');</script>\n" +
                "<title>Horaires Transilien 2018 - Lignes A B C D E H J K L N P R U</title>\n" +
                "<meta name=\"description\" content=\"Consultez les horaires du Transilien en " +
                "temps réel. Lignes A et B du RER. Lignes C " +
                "D E H J K L N P R U du Transilien.\">\n" +
                "<meta name=\"keywords\" content=\"horaires transilien\">\n" +
                "<meta charset=\"windows-1252\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "<meta name=\"robots\" content=\"follow, index\">\n" + "<base hr");
    }

    @Test
    public void withCompactComment() throws IOException {
        // <!--> is a valid comment
        assertWindows1252("<!--" + // start comment
                "<meta charset='UTF-8'>" + // inside comment
                "-->" + // end comment
                "<!-->" + // compact comment
                "<meta charset='WINDOWS-1252'>" // outside comment, charset declaration
        );
    }

    @Test
    public void withCharsetInContentType() throws IOException {
        metadata.set(Metadata.CONTENT_TYPE, "text/html; Charset=ISO-8859-1");
        // ISO-8859-1 is an alias for WINDOWS-1252, even if it's set at the transport layer level
        assertWindows1252("");
        assertWindows1252("<meta charset='UTF-8'>");
        assertWindows1252("<meta http-equiv='content-type' content='charset=utf-8'>");
        // if a BOM is present, it has precedence over transport layer information
        assertCharset("\ufeff<meta charset='WINDOWS-1252'>", StandardCharsets.UTF_8);
        assertCharset("\ufeff<meta charset='WINDOWS-1252'>", StandardCharsets.UTF_16LE);
        assertCharset("\ufeff<meta charset='WINDOWS-1252'>", StandardCharsets.UTF_16BE);
    }

    @Test
    public void throwResistance() throws IOException {
        // The preprocessing should return right after having found the charset
        // So if an error is thrown in the stream AFTER the declaration,
        // it shouldn't see it
        assertWindows1252(throwAfter("<meta charset='WINDOWS-1252'>"));
        assertWindows1252(throwAfter("<meta charset='WINDOWS-1252'><some other tag"));

        // But if an error is thrown before the end of the meta tag, it should see it
        // and return unsuccessfully
        assertCharset(throwAfter("<meta charset='WINDOWS-1252'"), null);

        // If there is no meta, but an error is thrown, the detector simply returns
        // unsuccessfully (it should not throw runtime errors)
        assertCharset(throwAfter("<"), null);
        assertCharset(throwAfter("<!"), null);
        assertCharset(throwAfter("<!doctype"), null);
        assertCharset(throwAfter("<!doctype html><html"), null);
        assertCharset(throwAfter("<!doctype html><html attr"), null);
        assertCharset(throwAfter("<!doctype html><html attr="), null);
        assertCharset(throwAfter("<!doctype html><html attr=x"), null);
        assertCharset(throwAfter("<!doctype html><html attr='x"), null);
    }

    @Test
    public void streamReset() throws IOException {
        // The stream should be reset after detection
        byte[] inBytes = {0, 1, 2, 3, 4};
        byte[] outBytes = new byte[5];
        InputStream inStream = new ByteArrayInputStream(inBytes);
        detectCharset(inStream);
        // The stream should still be readable from the beginning after detection
        inStream.read(outBytes);
        assertArrayEquals(inBytes, outBytes);
    }

    private void assertWindows1252(String html) throws IOException {
        assertCharset(html, Charset.forName("WINDOWS-1252"));
    }

    private void assertWindows1252(InputStream inStream) throws IOException {
        assertCharset(inStream, Charset.forName("WINDOWS-1252"));
    }

    private void assertCharset(String html, Charset charset) throws IOException {
        final Charset contentsCharset = (charset == null) ? StandardCharsets.UTF_8 : charset;
        InputStream inStream = new ByteArrayInputStream(html.getBytes(contentsCharset));
        final Charset detected = detectCharset(inStream);
        assertEquals(html + " should be detected as " + charset, charset, detected);
    }

    private void assertCharset(InputStream inStream, Charset charset) throws IOException {
        final Charset detected = detectCharset(inStream);
        assertEquals(charset, detected);
    }

    private Charset detectCharset(InputStream inStream) throws IOException {
        return new StandardHtmlEncodingDetector().detect(inStream, metadata);
    }

    private InputStream throwAfter(String html) {
        byte[] contents = html.getBytes(StandardCharsets.UTF_8);
        InputStream contentsInStream = new ByteArrayInputStream(contents);
        InputStream errorThrowing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("test exception");
            }
        };
        return new BufferedInputStream(new SequenceInputStream(contentsInStream, errorThrowing));
    }
}
