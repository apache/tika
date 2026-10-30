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
package org.apache.tika.parser.odf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

public class OpenDocumentContentParserTest extends TikaTest {

    @Test
    public void testEmbeddedLists() throws Exception {
        Parser p = new OpenDocumentContentParser();
        Metadata metadata = new Metadata();
        XMLResult r = null;
        try (TikaInputStream tis = TikaInputStream.get(new GzipCompressorInputStream(getResourceAsStream(
                "/test-documents/testODTBodyListOpenClose.xml.gz")))) {
            r = getXML(tis, p, metadata);
        }
        String xml = r.xml;
        //extract all the tags
        Matcher m = Pattern.compile("(<[^>]+>)").matcher(xml);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            sb.append(m.group(1)).append(" ");
        }
        String tags = sb.toString();
        assertContains("</p> <ol> <li> <p> </p> <ul> <li> <p> </p> </li> <li> <p> </p> </li> " +
                        "</ul> </li> </ol>",
                tags);
        assertContains("Exercice", xml);

        m = Pattern.compile("<p class=\"annotation\">[^<]+<\\/p>").matcher(xml);
        assertTrue(m.find());

        m = Pattern.compile("<p class=\"annotation\">[^<]+<\\/annotation>").matcher(xml);
        assertFalse(m.find());

        //just make sure this doesn't throw any exceptions
        XMLReaderUtils.parseSAX(TikaInputStream.get(xml.getBytes(StandardCharsets.UTF_8)),
                new DefaultHandler(), new ParseContext());
    }

    @Test
    public void testRepeatedCells() throws Exception {
        String content = "<office:document-content"
                + " xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\""
                + " xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\""
                + " xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\">"
                + "<office:body><office:spreadsheet><table:table table:name=\"S\">"
                + "<table:table-row>"
                + "<table:table-cell table:number-columns-repeated=\"3\"><text:p>TEST</text:p>"
                + "</table:table-cell>"
                + "<table:table-cell table:number-columns-repeated=\"1020\"/>"
                + "</table:table-row><table:table-row>"
                + "<table:table-cell><text:p>a</text:p></table:table-cell>"
                + "<table:table-cell table:number-columns-repeated=\"2\"/>"
                + "<table:table-cell><text:p>b</text:p></table:table-cell>"
                + "<table:table-cell table:number-columns-repeated=\"1000\"/>"
                + "</table:table-row><table:table-row>"
                + "<table:table-cell table:number-columns-spanned=\"2\"><text:p>wide</text:p>"
                + "</table:table-cell><table:covered-table-cell/>"
                + "<table:table-cell><text:p>c</text:p></table:table-cell>"
                + "</table:table-row>"
                // a repeated row with content, empty rows between values, trailing filler
                + "<table:table-row table:number-rows-repeated=\"2\">"
                + "<table:table-cell><text:p>r</text:p></table:table-cell>"
                + "<table:table-cell table:number-columns-repeated=\"2\"><text:p>s</text:p>"
                + "</table:table-cell></table:table-row>"
                + "<table:table-row table:number-rows-repeated=\"2\">"
                + "<table:table-cell table:number-columns-repeated=\"1024\"/></table:table-row>"
                + "<table:table-row><table:table-cell><text:p>z</text:p></table:table-cell>"
                + "</table:table-row>"
                + "<table:table-row table:number-rows-repeated=\"1048000\">"
                + "<table:table-cell table:number-columns-repeated=\"1024\"/></table:table-row>"
                // a plain empty row after the filler must not bring it back
                + "<table:table-row><table:table-cell/><table:table-cell/></table:table-row>"
                + "</table:table></office:spreadsheet></office:body></office:document-content>";
        String xml;
        try (TikaInputStream tis = TikaInputStream.get(content.getBytes(StandardCharsets.UTF_8))) {
            xml = getXML(tis, new OpenDocumentContentParser(), new Metadata()).xml;
        }
        String table = xml.substring(xml.indexOf("<table>"), xml.indexOf("</table>"))
                .replaceAll("\\s+", "");
        assertEquals("<table><tr><td><p>TEST</p></td><td><p>TEST</p></td><td><p>TEST</p></td></tr>"
                + "<tr><td><p>a</p></td><td/><td/><td><p>b</p></td></tr>"
                + "<tr><tdcolspan=\"2\"><p>wide</p></td><td><p>c</p></td></tr>"
                + "<tr><td><p>r</p></td><td><p>s</p></td><td><p>s</p></td></tr>"
                + "<tr><td><p>r</p></td><td><p>s</p></td><td><p>s</p></td></tr>"
                + "<tr/><tr/><tr><td><p>z</p></td></tr>", table);
    }

    @Test
    public void testRepeatedCellBudget() throws Exception {
        String big = "x".repeat(1000);
        String content = "<office:document-content"
                + " xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\""
                + " xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\""
                + " xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\">"
                + "<office:body><office:spreadsheet><table:table table:name=\"S\">"
                + "<table:table-row><table:table-cell table:number-columns-spanned=\"2\"/>"
                + "<table:covered-table-cell/><table:table-cell><text:p>v</text:p>"
                + "</table:table-cell></table:table-row>"
                + "<table:table-row><table:table-cell table:number-columns-repeated=\"16384\">"
                + "<text:p>" + big + "</text:p></table:table-cell></table:table-row>"
                + "</table:table></office:spreadsheet></office:body></office:document-content>";
        Metadata metadata = new Metadata();
        String xml;
        try (TikaInputStream tis = TikaInputStream.get(content.getBytes(StandardCharsets.UTF_8))) {
            xml = getXML(tis, new OpenDocumentContentParser(), metadata).xml;
        }
        // an empty merged cell keeps its span when a later cell is written
        assertContains("<td colspan=\"2\" />", xml);
        assertEquals(1, xml.split(big, -1).length - 1);
        assertContains("written once",
                metadata.get(org.apache.tika.metadata.TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testRowInProgressKeptOnParseFailure() throws Exception {
        String content = "<office:document-content"
                + " xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\""
                + " xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\""
                + " xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\">"
                + "<office:body><office:spreadsheet><table:table table:name=\"S\">"
                + "<table:table-row><table:table-cell><text:p>first</text:p></table:table-cell>"
                + "</table:table-row><table:table-row>"
                + "<table:table-cell><text:p>kept</text:p></table:table-cell>"
                // duplicate attribute: not well-formed
                + "<table:table-cell office:a=\"1\" office:a=\"2\"/>"
                + "</table:table-row></table:table></office:spreadsheet></office:body>"
                + "</office:document-content>";
        ToXMLContentHandler out = new ToXMLContentHandler();
        try (TikaInputStream tis = TikaInputStream.get(content.getBytes(StandardCharsets.UTF_8))) {
            assertThrows(SAXException.class, () -> new OpenDocumentContentParser()
                    .parse(tis, out, new Metadata(), new ParseContext()));
        }
        assertContains("first", out.toString());
        assertContains("kept", out.toString());
    }
}
