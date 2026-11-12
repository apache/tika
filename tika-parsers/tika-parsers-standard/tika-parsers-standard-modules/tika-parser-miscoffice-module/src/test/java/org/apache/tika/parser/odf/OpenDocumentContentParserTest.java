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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
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
                + "<tr><td><p>z</p></td></tr>", table);
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

    private static final String NS =
            " xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\""
            + " xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\""
            + " xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\"";

    private static final String SHEET_MIME = "application/vnd.oasis.opendocument.spreadsheet";

    private static String row(String... values) {
        StringBuilder sb = new StringBuilder("<table:table-row>");
        for (String v : values) {
            sb.append("<table:table-cell><text:p>").append(v).append("</text:p></table:table-cell>");
        }
        return sb.append("</table:table-row>").toString();
    }

    private static String emptyRows(int n) {
        return "<table:table-row table:number-rows-repeated=\"" + n + "\">"
                + "<table:table-cell table:number-columns-repeated=\"16384\"/></table:table-row>";
    }

    private static String emptyCells(int n) {
        return "<table:table-cell table:number-columns-repeated=\"" + n + "\"/>";
    }

    private static byte[] fods(String rows) {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document" + NS
                + " office:version=\"1.3\" office:mimetype=\"" + SHEET_MIME + "\">"
                + "<office:body><office:spreadsheet><table:table table:name=\"S\">" + rows
                + "</table:table></office:spreadsheet></office:body></office:document>")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] ods(String rows) throws Exception {
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-content" + NS
                + " office:version=\"1.3\"><office:body><office:spreadsheet>"
                + "<table:table table:name=\"S\">" + rows
                + "</table:table></office:spreadsheet></office:body></office:document-content>";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            ZipEntry mime = new ZipEntry("mimetype");
            mime.setMethod(ZipEntry.STORED);
            byte[] mimeBytes = SHEET_MIME.getBytes(StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32();
            crc.update(mimeBytes);
            mime.setSize(mimeBytes.length);
            mime.setCrc(crc.getValue());
            zos.putNextEntry(mime);
            zos.write(mimeBytes);
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("content.xml"));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    private XMLResult autoDetect(byte[] bytes, String name, Metadata metadata) throws Exception {
        return autoDetect(bytes, name, metadata, AUTO_DETECT_PARSER);
    }

    private XMLResult autoDetect(byte[] bytes, String name, Metadata metadata, Parser parser)
            throws Exception {
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            return getXML(tis, parser, metadata);
        }
    }

    private static final String INCLUDE_MISSING_ROWS = "{\"includeMissingRows\": true}";

    // the AutoDetectParser wrapper keeps the zip-bomb guard in play
    private static Parser odsWithMissingRows() {
        return new AutoDetectParser(
                new OpenDocumentParser(JsonConfig.trusted(INCLUDE_MISSING_ROWS)));
    }

    private static Parser fodsWithMissingRows() {
        return new AutoDetectParser(
                new FlatOpenDocumentParser(JsonConfig.trusted(INCLUDE_MISSING_ROWS)));
    }

    private static String table(String xml) {
        return xml.substring(xml.indexOf("<table>"), xml.indexOf("</table>")).replaceAll("\\s+", "");
    }

    private static int count(String haystack, String needle) {
        return haystack.split(Pattern.quote(needle), -1).length - 1;
    }

    @Test
    public void testMillionRowGapFods() throws Exception {
        byte[] bytes = fods(row("before") + emptyRows(1048565) + row("after"));
        String xml = autoDetect(bytes, "gap.fods", new Metadata()).xml;
        assertEquals("<table><tr><td><p>before</p></td></tr><tr><td><p>after</p></td></tr>",
                table(xml));
        assertTrue(xml.length() < 2000, "output length " + xml.length());
    }

    @Test
    public void testMillionRowGapOds() throws Exception {
        byte[] bytes = ods(row("before") + emptyRows(1048565) + row("after"));
        String xml = autoDetect(bytes, "gap.ods", new Metadata()).xml;
        assertEquals("<table><tr><td><p>before</p></td></tr><tr><td><p>after</p></td></tr>",
                table(xml));
    }

    @Test
    public void testWideCellGapInRepeatedRows() throws Exception {
        byte[] bytes = fods("<table:table-row table:number-rows-repeated=\"100000\">"
                + emptyCells(16383) + "<table:table-cell><text:p>x</text:p></table:table-cell>"
                + "</table:table-row>" + row("after"));
        Metadata metadata = new Metadata();
        String xml = autoDetect(bytes, "wide.fods", metadata).xml;
        // the row is too costly to repeat, so it is written once
        assertEquals(16383, count(xml, "<td />"));
        assertEquals(1, count(xml, "<p>x</p>"));
        assertContains("<p>after</p>", xml);
        assertContains("written once",
                metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testSmallGapsKept() throws Exception {
        byte[] bytes = fods("<table:table-row><table:table-cell><text:p>a</text:p></table:table-cell>"
                + emptyCells(3) + "<table:table-cell><text:p>b</text:p></table:table-cell>"
                + emptyCells(16379) + "</table:table-row>" + emptyRows(5) + row("c")
                + emptyRows(1048000));
        Metadata metadata = new Metadata();
        String xml = autoDetect(bytes, "small.fods", metadata).xml;
        assertEquals("<table><tr><td><p>a</p></td><td/><td/><td/><td><p>b</p></td></tr>"
                + "<tr><td><p>c</p></td></tr>", table(xml));
        assertEquals(null, metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testRepeatedValuesExpanded() throws Exception {
        byte[] bytes = fods("<table:table-row table:number-rows-repeated=\"1000\">"
                + "<table:table-cell table:number-columns-repeated=\"3\"><text:p>v</text:p>"
                + "</table:table-cell></table:table-row>");
        String xml = autoDetect(bytes, "values.fods", new Metadata()).xml;
        assertEquals(3000, count(xml, "<p>v</p>"));
    }

    @Test
    public void testEmptyCellBudgetExhausted() throws Exception {
        // each row charges 1024 for its gap; the budget runs out after 9765 rows
        StringBuilder rows = new StringBuilder();
        int n = 10000;
        for (int i = 0; i < n; i++) {
            rows.append("<table:table-row>").append(emptyCells(1024))
                    .append("<table:table-cell><text:p>r").append(i)
                    .append("</text:p></table:table-cell></table:table-row>");
        }
        Metadata metadata = new Metadata();
        String xml;
        try (TikaInputStream tis = TikaInputStream.get(fods(rows.toString()))) {
            xml = getXML(tis, new FlatOpenDocumentParser(), metadata).xml;
        }
        String first = xml.substring(xml.indexOf("<tr>"), xml.indexOf("</tr>"));
        assertEquals(1024, count(first, "<td />"));
        String last = xml.substring(xml.lastIndexOf("<tr>"), xml.lastIndexOf("</tr>"));
        assertEquals(0, count(last, "<td />"));
        assertContains("<p>r" + (n - 1) + "</p>", last);
        assertContains("written once",
                metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testIncludeMissingRowsConfig() throws Exception {
        assertFalse(new OpenDocumentParser().isIncludeMissingRows());
        assertFalse(new FlatOpenDocumentParser().isIncludeMissingRows());
        assertTrue(new OpenDocumentParser(JsonConfig.trusted(INCLUDE_MISSING_ROWS))
                .isIncludeMissingRows());
        assertTrue(new FlatOpenDocumentParser(JsonConfig.trusted(INCLUDE_MISSING_ROWS))
                .isIncludeMissingRows());
    }

    @Test
    public void testIncludeMissingRows() throws Exception {
        String rows = row("a") + "<table:table-row><table:table-cell/></table:table-row>"
                + emptyRows(2) + row("b") + emptyRows(1048000);
        String expected = "<table><tr><td><p>a</p></td></tr><tr><td/></tr><tr><td/></tr><tr><td/></tr>"
                + "<tr><td><p>b</p></td></tr>";
        assertEquals(expected,
                table(autoDetect(fods(rows), "e.fods", new Metadata(), fodsWithMissingRows()).xml));
        assertEquals(expected,
                table(autoDetect(ods(rows), "e.ods", new Metadata(), odsWithMissingRows()).xml));
        assertEquals("<table><tr><td><p>a</p></td></tr><tr><td><p>b</p></td></tr>",
                table(autoDetect(fods(rows), "e.fods", new Metadata()).xml));
    }

    @Test
    public void testIncludeMissingRowsMillionRowGap() throws Exception {
        String rows = row("before") + emptyRows(1048565) + row("after");
        for (boolean packaged : new boolean[]{false, true}) {
            String xml = packaged
                    ? autoDetect(ods(rows), "gap.ods", new Metadata(), odsWithMissingRows()).xml
                    : autoDetect(fods(rows), "gap.fods", new Metadata(), fodsWithMissingRows()).xml;
            assertEquals(100_000, xml.split("<tr>\\s*<td />\\s*</tr>", -1).length - 1);
            assertTrue(xml.length() < 3_000_000, "output length " + xml.length());
            assertContains("<p>after</p>", xml);
        }
    }
}
