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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.microsoft.OfficeParserConfig;

/**
 * Runs the shared DOCX tests using the SAX-based streaming parser,
 * plus SAX-specific tests (security features, deleted content, etc.).
 */
public class OOXMLDocxSAXTest extends AbstractOOXMLDocxTest {

    @Override
    ParseContext getParseContext() {
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setUseSAXDocxExtractor(true);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
        return parseContext;
    }

    @Test
    public void testTextDecorationNestedUnderlineStrike() throws Exception {
        // SAX closes u before opening s (since s is outer): <u>unde</u><s><u>r</u></s><u>line</u>
        String xml = getXML("testWORD_various.docx", getParseContext()).xml;
        assertContains("<i><u>unde</u><s><u>r</u></s><u>line</u></i>", xml);
    }

    @Test
    public void basicTest() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testWORD_2006ml.docx", getParseContext());

        assertEquals(8, metadataList.size());
        Metadata m = metadataList.get(0);

        assertEquals("2016-11-29T00:58:00Z", m.get(TikaCoreProperties.CREATED));
        assertEquals("2016-11-29T17:54:00Z", m.get(TikaCoreProperties.MODIFIED));
        assertEquals("My Document Title", m.get(TikaCoreProperties.TITLE));
        assertEquals("This is the Author", m.get(TikaCoreProperties.CREATOR));
        assertEquals("3", m.get(OfficeOpenXMLCore.REVISION));
        assertEquals("Allison, Timothy B.", m.get(TikaCoreProperties.MODIFIER));
        assertEquals("260", m.get(Office.WORD_COUNT));
        assertEquals("3", m.get(Office.PARAGRAPH_COUNT));
        assertEquals("1742", m.get(Office.CHARACTER_COUNT_WITH_SPACES));
        assertEquals("12", m.get(Office.LINE_COUNT));
        assertEquals("16.0000", m.get(OfficeOpenXMLExtended.APP_VERSION));

        String content = m.get(TikaCoreProperties.TIKA_CONTENT);

        assertContainsCount("engaging title page", content, 1);
        assertContainsCount("This is the Author\n", content, 1);
        assertContainsCount("This is an engaging title page", content, 1);

        assertContains("My Document Title", content);
        assertContains("My Document Subtitle", content);

        assertContains(
                "<p class=\"toc_1\">\t<a href=\"#_Toc467647605\">Heading1\t3</a></p>",
                content);

        assertContains("2. Really basic 2.", content);

        assertContainsCount("This is a text box", content, 1);

        assertContains(
                "<p>This is a hyperlink: <a href=\"http://tika.apache.org\">tika</a></p>",
                content);

        assertContains(
                "<p>This is a link to a local file: " +
                        "<a href=\"file:///C:/data/test.png\">test.png</a></p>",
                content);

        assertContains("<p>This is          10 spaces</p>", content);

        assertContains(
                "<p class=\"table_of_figures\">\t" +
                        "<a href=\"#_Toc467647797\">Table 1: Table1 Caption\t2</a></p>",
                content);

        assertContains("<td>Embedded table r1c1", content);
        assertContainsCount("<p>This is text within a shape", content, 1);
        assertContains("<p>Rich text content control", content);
        assertContains("<p>Simple text content control", content);
        assertContains("Repeating content", content);
        assertContains("Drop down1", content);
        assertContains("<p>11/16/2016</p>", content);
        assertContains("tab\ttab", content);
        assertContainsCount("serious word art", content, 1);
        assertContainsCount("Wordartr1c1", content, 1);
        assertContains("Click or tap to enter a date", content);
        assertContains(
                "<p>The <i>quick</i> brown <b>fox </b>j<i>um</i><b><i>ped</i></b> over",
                content);
        assertContains("This is a comment", content);
        assertContains("This is an endnote", content);
        assertContains("this is the footnote", content);
        assertContains("First page header", content);
        assertContains("Even page header", content);
        assertContains("Odd page header", content);
        assertContains("First page footer", content);
        assertContains("Even page footer", content);
        assertContains("Odd page footer", content);
        assertNotContained("frog", content);
        assertContains("Mattmann", content);
    }

    @Test
    public void testPicturesInVariousPlaces() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testWORD_embedded_pics.docx", getParseContext());

        assertEquals(3, metadataList.size());
        String content = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        for (int i = 1; i < 4; i++) {
            assertContains("header" + i + "_pic", content);
            assertContains("footer" + i + "_pic", content);
        }
        assertContains("body_pic.jpg", content);
        assertContains("sdt_pic.jpg", content);
        assertContains("deeply_embedded_pic", content);
        assertContains("deleted_pic", content);
        assertContains("footnotes_pic", content);
        assertContains("comments_pic", content);
        assertContains("endnotes_pic", content);

        assertContainsCount("<img src=", content, 14);
    }

    @Test
    public void testSkipDeleted() throws Exception {
        ParseContext pc = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeDeletedContent(true);
        officeParserConfig.setUseSAXDocxExtractor(true);
        officeParserConfig.setIncludeMoveFromContent(true);
        pc.set(OfficeParserConfig.class, officeParserConfig);

        XMLResult r = getXML("testWORD_2006ml.docx", pc);
        assertContains("frog", r.xml);
        assertContainsCount("Second paragraph", r.xml, 2);
    }

    @Test
    public void testEncrypted() throws Exception {
        Map<String, String> tests = new HashMap<>();
        tests.put("testWORD_protected_passtika.docx",
                "This is an encrypted Word 2007 File");

        PasswordProvider passwordProvider = metadata -> "tika";
        OfficeParserConfig opc = new OfficeParserConfig();
        opc.setUseSAXDocxExtractor(true);
        ParseContext passwordContext = new ParseContext();
        passwordContext.set(PasswordProvider.class, passwordProvider);
        passwordContext.set(OfficeParserConfig.class, opc);
        for (Map.Entry<String, String> e : tests.entrySet()) {
            assertContains(e.getValue(), getXML(e.getKey(), passwordContext).xml);
        }

        for (Map.Entry<String, String> e : tests.entrySet()) {
            boolean exc = false;
            try {
                getXML(e.getKey(), getParseContext());
            } catch (EncryptedDocumentException ex) {
                exc = true;
            }
            assertTrue(exc);
        }
    }

    @Test
    public void testDOCXParagraphNumbering() throws Exception {
        String xml = getXML("testWORD_numbered_list.docx", getParseContext()).xml;
        assertContains("add a list here", xml);
        assertContains("1) This", xml);
        assertContains("a) Is", xml);
        assertContains("i) A multi", xml);
        assertContains("ii) Level", xml);
        assertContains("1. Within cell 1", xml);
        assertContains("b. Cell b", xml);
        assertContains("iii) List", xml);
        assertContains("2) foo", xml);
        assertContains("ii) baz", xml);
        assertContains("ii) foo", xml);
        assertContains("II. bar", xml);
        assertContains("6. six", xml);
        assertContains("7. seven", xml);
        assertContains("a. seven a", xml);
        assertContains("e. seven e", xml);
        assertContains("2. A ii 2", xml);
        assertContains("3. page break list 3", xml);
        assertContains(
                "Some-1-CrazyFormat Greek numbering with crazy format - alpha", xml);
        assertContains("1.1.1. 1.1.1", xml);
        assertContains("1.1. 1.2-&gt;1.1  //set the value", xml);
    }

    @Test
    public void testMacrosInDocm() throws Exception {
        Metadata parsedBy = new Metadata();
        parsedBy.add(TikaCoreProperties.TIKA_PARSED_BY,
                "org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFEventBasedWordExtractor");

        List<Metadata> metadataList =
                getRecursiveMetadata("testWORD_macros.docm", getParseContext());
        for (Metadata metadata : metadataList) {
            if (metadata.get(Metadata.CONTENT_TYPE).equals("text/x-vbasic")) {
                fail("Shouldn't have extracted macros as default");
            }
        }
        assertContainsAtLeast(parsedBy, metadataList);

        ParseContext context = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractMacros(true);
        officeParserConfig.setUseSAXDocxExtractor(true);
        context.set(OfficeParserConfig.class, officeParserConfig);

        metadataList = getRecursiveMetadata("testWORD_macros.docm", context);
        assertContains("quick",
                metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertContainsAtLeast(parsedBy, metadataList);

        Metadata minExpected = new Metadata();
        minExpected.add(TikaCoreProperties.TIKA_CONTENT.getName(), "Sub Embolden()");
        minExpected.add(TikaCoreProperties.TIKA_CONTENT.getName(), "Sub Italicize()");
        minExpected.add(Metadata.CONTENT_TYPE, "text/x-vbasic");
        minExpected.add(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());

        assertContainsAtLeast(minExpected, metadataList);
        assertContainsAtLeast(parsedBy, metadataList);

        AutoDetectParser parser = (AutoDetectParser) TikaLoader.load(
                getConfigPath(OOXMLDocxSAXTest.class, "tika-config-sax-macros.json"))
                .loadAutoDetectParser();
        metadataList = getRecursiveMetadata("testWORD_macros.docm", parser);
        assertContainsAtLeast(minExpected, metadataList);
        assertContainsAtLeast(parsedBy, metadataList);
    }

    @Test
    public void testEmbedded() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testWORD_embeded.docx", getParseContext());
        Metadata main = metadataList.get(0);
        String content = main.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains(
                "<img src=\"embedded:image2.jpeg\" alt=\"A description...\" />", content);
        assertContains("<div class=\"embedded\" id=\"rId8\" />", content);
        assertEquals(16, metadataList.size());
    }

    @Test
    public void testInitializationViaConfig() throws Exception {
        //NOTE: this test relies on a bug in the DOM extractor that
        //is passing over the title information.
        //once we fix that, this test will no longer be meaningful!
        AutoDetectParser p = (AutoDetectParser) TikaLoader.load(
                getConfigPath(OOXMLDocxSAXTest.class, "tika-config-sax-docx.json"))
                .loadAutoDetectParser();
        XMLResult xml = getXML("testWORD_2006ml.docx", p, new Metadata());
        assertContains("engaging title", xml.xml);
    }

    @Test
    public void testTruncatedSAXDocx() throws Exception {
        assertThrows(TikaException.class, () -> {
            getRecursiveMetadata("testWORD_truncated.docx", getParseContext());
        });
    }

    // Security feature tests

    @Test
    public void testHoverAndVmlHyperlinks() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testHoverAndVml.docx", getParseContext());
        Metadata m = metadataList.get(0);
        assertEquals("true", m.get(Office.HAS_HOVER_HYPERLINKS));
        assertEquals("true", m.get(Office.HAS_VML_HYPERLINKS));

        String xml = getXML("testHoverAndVml.docx", getParseContext()).xml;
        assertContains("class=\"external-ref-hlinkHover\"", xml);
        assertContains("http://hover.example.com/phishing", xml);
        assertContains("class=\"external-ref-vml-shape-href\"", xml);
        assertContains("http://vml.example.org/shape-link", xml);
    }

    @Test
    public void testMailMerge() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testMailMerge.docx", getParseContext());
        Metadata m = metadataList.get(0);
        assertEquals("true", m.get(Office.HAS_MAIL_MERGE));
    }

    @Test
    public void testAttachedTemplate() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testAttachedTemplate.docx", getParseContext());
        Metadata m = metadataList.get(0);
        assertEquals("true", m.get(Office.HAS_ATTACHED_TEMPLATE));

        String xml = getXML("testAttachedTemplate.docx", getParseContext()).xml;
        assertContains("class=\"external-ref-attachedTemplate\"", xml);
        assertContains("example.com/templates", xml);
    }

    @Test
    public void testSubdocument() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testSubdocument.docx", getParseContext());
        Metadata m = metadataList.get(0);
        assertEquals("true", m.get(Office.HAS_SUBDOCUMENTS));

        String xml = getXML("testSubdocument.docx", getParseContext()).xml;
        assertContains("class=\"external-ref-subDocument\"", xml);
        assertContains("example.org/chapters", xml);
    }

    @Test
    public void testFrameset() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testFrameset.docx", getParseContext());
        Metadata m = metadataList.get(0);
        assertEquals("true", m.get(Office.HAS_FRAMESETS));
    }

    /**
     * Test with external DOCX files known to trigger "prefix not bound"
     * from missing namespace declarations in footnote/endnote fragments.
     * Enable by setting system property "tika.test.docx.namespace" to a file path.
     */
    @Test
    public void testNamespaceInFragments() throws Exception {
        String filePath = System.getProperty("tika.test.docx.namespace");
        if (filePath == null) {
            return;
        }
        java.io.File f = new java.io.File(filePath);
        if (!f.isFile()) {
            return;
        }
        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        org.xml.sax.ContentHandler handler =
                new org.apache.tika.sax.BodyContentHandler(-1);
        try (TikaInputStream tis = TikaInputStream.get(f.toPath())) {
            parser.parse(tis, handler, metadata, getParseContext());
        }
        String[] warnings = metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        for (String w : warnings) {
            assertNotContained("not bound", w);
        }
    }
}
