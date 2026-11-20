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
package org.apache.tika.parser.microsoft.ooxml;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.XMLReaderUtils;

public class OOXMLSupSubTest extends TikaTest {

    private static final String W =
            "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";

    @Test
    public void testDocx() throws Exception {
        String styles = "<w:styles " + W + "><w:style w:type=\"character\" w:styleId=\"Note\">"
                + "<w:name w:val=\"Note Mark\"/><w:rPr><w:vertAlign w:val=\"superscript\"/>"
                + "</w:rPr></w:style></w:styles>";
        String body = run("E = mc", "") + run("2", "<w:vertAlign w:val=\"superscript\"/>")
                + run(" and H", "") + run("2", "<w:vertAlign w:val=\"subscript\"/>")
                + run("O, note", "") + run("3", "<w:rStyle w:val=\"Note\"/>")
                + run(" bold", "<w:b/>")
                + run("x", "<w:b/><w:vertAlign w:val=\"superscript\"/>")
                + run("y", "<w:vertAlign w:val=\"superscript\"/>")
                // a direct baseline overrides the style
                + run(" 4", "<w:rStyle w:val=\"Note\"/><w:vertAlign w:val=\"baseline\"/>");
        String xml = parse(docx("<w:document " + W + "><w:body><w:p>" + body
                + "</w:p></w:body></w:document>", styles));
        assertContains("E = mc<sup>2</sup> and H<sub>2</sub>O, note<sup>3</sup>", xml);
        // sup nests inside b, and closes before </b>; the run's leading space sits outside <b>
        assertContains("<sup>3</sup> <b>bold<sup>x</sup></b><sup>y</sup>", xml);
        assertNotContained("<sup> 4", xml);
    }

    @Test
    public void testPptx() throws Exception {
        String xml = parse(pptx("<a:r><a:rPr lang=\"en-US\"/><a:t>E = mc</a:t></a:r>"
                + "<a:r><a:rPr lang=\"en-US\" baseline=\"30000\"/><a:t>2</a:t></a:r>"
                + "<a:r><a:rPr lang=\"en-US\"/><a:t> and H</a:t></a:r>"
                + "<a:r><a:rPr lang=\"en-US\" baseline=\"-25000\"/><a:t>2</a:t></a:r>"
                + "<a:r><a:rPr lang=\"en-US\" baseline=\"0\"/><a:t>O</a:t></a:r>"));
        assertContains("E = mc<sup>2</sup> and H<sub>2</sub>O", xml);
    }

    //TIKA-5071
    @Test
    public void testXlsx() throws Exception {
        String sml = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"";
        String sharedStrings = "<sst " + sml + ">"
                + "<si><r><t>E = mc</t></r>"
                + "<r><rPr><vertAlign val=\"superscript\"/></rPr><t>2</t></r></si>"
                // a run with no rPr is in the cell's font; one with rPr is not
                + "<si><r><t>CO</t></r>"
                + "<r><rPr><vertAlign val=\"subscript\"/></rPr><t>2</t></r>"
                + "<r><rPr><sz val=\"11\"/></rPr><t xml:space=\"preserve\"> gas</t></r></si>"
                + "<si><t>n</t></si>"
                // the phonetic reading follows the runs as plain text
                + "<si><r><t>x</t></r><r><rPr><vertAlign val=\"superscript\"/></rPr><t>2</t></r>"
                + "<rPh sb=\"0\" eb=\"1\"><t>ekusu</t></rPh></si>"
                // in a superscript cell, a run with rPr but no vertAlign is baseline
                + "<si><r><t>a</t></r><r><rPr><b/></rPr><t>b</t></r></si>"
                // a direct <t> before the runs is kept, in the cell's font
                + "<si><t>abc</t><r><rPr><vertAlign val=\"superscript\"/></rPr><t>2</t></r>"
                + "</si></sst>";
        String styles = "<styleSheet " + sml + "><fonts count=\"3\"><font><sz val=\"11\"/></font>"
                + "<font><vertAlign val=\"superscript\"/><sz val=\"11\"/></font>"
                + "<font><vertAlign val=\"subscript\"/><sz val=\"11\"/></font></fonts>"
                + "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\"/><xf numFmtId=\"0\" fontId=\"2\"/></cellXfs>"
                + "</styleSheet>";
        String sheet = "<worksheet " + sml + "><sheetData><row r=\"1\">"
                + "<c r=\"A1\" t=\"s\"><v>0</v></c>"
                + "<c r=\"B1\" s=\"2\" t=\"s\"><v>1</v></c>"
                + "<c r=\"C1\" s=\"1\" t=\"s\"><v>2</v></c>"
                + "<c r=\"D1\" s=\"2\"><v>219</v></c>"
                + "<c r=\"E1\"><v>5</v></c>"
                + "<c r=\"F1\" t=\"s\"><v>3</v></c>"
                + "<c r=\"G1\" s=\"1\" t=\"s\"><v>4</v></c>"
                + "<c r=\"H1\" t=\"s\"><v>5</v></c>"
                + "</row></sheetData></worksheet>";
        String xml = parse(xlsx(sharedStrings, styles, sheet));
        assertContains("<td>E = mc<sup>2</sup></td>", xml);
        assertContains("<td><sub>CO2</sub> gas</td>", xml);
        assertContains("<td><sup>n</sup></td>", xml);
        assertContains("<td><sub>219</sub></td>", xml);
        assertContains("<td>5</td>", xml);
        assertContains("<td>x<sup>2</sup> ekusu</td>", xml);
        assertContains("<td><sup>a</sup>b</td>", xml);
        assertContains("<td>abc<sup>2</sup></td>", xml);
    }

    //TIKA-5071
    @Test
    public void testXlsxSharedStringRunsKeptOnlyForScript() throws Exception {
        String sst = "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<si><r><t>a</t></r><r><rPr><b/></rPr><t>b</t></r></si>"
                + "<si><r><t>x</t></r><r><rPr><vertAlign val=\"superscript\"/></rPr>"
                + "<t>2</t></r></si></sst>";
        XSSFSharedStringsShim shim = new XSSFSharedStringsShim(
                new ByteArrayInputStream(sst.getBytes(UTF_8)), false, false, new ParseContext());
        // no cell style is superscript or subscript, so a bold-only entry's runs don't matter
        assertNull(shim.getScriptedRunsAt(0));
        assertNotNull(shim.getScriptedRunsAt(1));
    }

    private static String run(String text, String rPr) {
        return "<w:r>" + (rPr.isEmpty() ? "" : "<w:rPr>" + rPr + "</w:rPr>")
                + "<w:t xml:space=\"preserve\">" + text + "</w:t></w:r>";
    }

    private String parse(byte[] bytes) throws Exception {
        String xml;
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            xml = getXML(tis, AUTO_DETECT_PARSER, new Metadata()).xml;
        }
        XMLReaderUtils.parseSAX(TikaInputStream.get(xml.getBytes(UTF_8)), new DefaultHandler(),
                new ParseContext());
        return xml;
    }

    private static byte[] docx(String document, String styles) throws Exception {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/"
                + "package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-"
                + "package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/"
                + "vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/"
                + "vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
                + "</Types>");
        parts.put("_rels/.rels", rels("rId1", "http://schemas.openxmlformats.org/officeDocument/"
                + "2006/relationships/officeDocument", "word/document.xml"));
        parts.put("word/_rels/document.xml.rels", rels("rId1",
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles",
                "styles.xml"));
        parts.put("word/document.xml", document);
        parts.put("word/styles.xml", styles);
        return zip(parts);
    }

    private static byte[] pptx(String runs) throws Exception {
        String pml = "http://schemas.openxmlformats.org/presentationml/2006/main";
        String dml = "http://schemas.openxmlformats.org/drawingml/2006/main";
        String rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/"
                + "package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-"
                + "package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/"
                + "vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>"
                + "<Override PartName=\"/ppt/slides/slide1.xml\" ContentType=\"application/"
                + "vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>"
                + "</Types>");
        parts.put("_rels/.rels", rels("rId1", rel + "/officeDocument", "ppt/presentation.xml"));
        parts.put("ppt/_rels/presentation.xml.rels", rels("rId2", rel + "/slide",
                "slides/slide1.xml"));
        parts.put("ppt/presentation.xml", "<p:presentation xmlns:p=\"" + pml + "\" xmlns:r=\""
                + rel + "\"><p:sldIdLst><p:sldId id=\"256\" r:id=\"rId2\"/></p:sldIdLst>"
                + "</p:presentation>");
        parts.put("ppt/slides/slide1.xml", "<p:sld xmlns:p=\"" + pml + "\" xmlns:a=\"" + dml
                + "\"><p:cSld><p:spTree><p:sp><p:txBody><a:bodyPr/><a:p>" + runs
                + "</a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>");
        return zip(parts);
    }

    private static byte[] xlsx(String sharedStrings, String styles, String sheet)
            throws Exception {
        String rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
        String ct = "application/vnd.openxmlformats-officedocument.spreadsheetml.";
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/"
                + "package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-"
                + "package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"" + ct
                + "sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"" + ct
                + "worksheet+xml\"/>"
                + "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"" + ct
                + "sharedStrings+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"" + ct
                + "styles+xml\"/>"
                + "</Types>");
        parts.put("_rels/.rels", rels("rId1", rel + "/officeDocument", "xl/workbook.xml"));
        parts.put("xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"http://schemas."
                + "openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"" + rel + "/worksheet\" "
                + "Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"" + rel + "/sharedStrings\" "
                + "Target=\"sharedStrings.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"" + rel + "/styles\" "
                + "Target=\"styles.xml\"/></Relationships>");
        parts.put("xl/workbook.xml", "<workbook xmlns=\"http://schemas.openxmlformats.org/"
                + "spreadsheetml/2006/main\" xmlns:r=\"" + rel + "\"><sheets>"
                + "<sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        parts.put("xl/worksheets/sheet1.xml", sheet);
        parts.put("xl/sharedStrings.xml", sharedStrings);
        parts.put("xl/styles.xml", styles);
        return zip(parts);
    }

    private static String rels(String id, String type, String target) {
        return "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/"
                + "relationships\"><Relationship Id=\"" + id + "\" Type=\"" + type
                + "\" Target=\"" + target + "\"/></Relationships>";
    }

    private static byte[] zip(Map<String, String> parts) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, String> e : parts.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(UTF_8));
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
