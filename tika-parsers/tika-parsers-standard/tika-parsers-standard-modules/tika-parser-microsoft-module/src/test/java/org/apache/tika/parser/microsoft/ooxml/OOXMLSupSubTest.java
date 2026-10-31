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
        // sup nests inside b, and closes before </b>
        assertContains("<b> bold<sup>x</sup></b><sup>y</sup>", xml);
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
