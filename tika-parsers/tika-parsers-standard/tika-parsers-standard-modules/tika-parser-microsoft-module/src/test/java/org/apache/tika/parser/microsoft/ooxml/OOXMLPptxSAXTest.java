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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Tests for the PPTX parser.
 */
public class OOXMLPptxSAXTest extends TikaTest {

    // ---- tests from AbstractOOXMLPptxTest ----

    @Test
    public void testPowerPoint() throws Exception {
        String[] extensions = new String[]{"pptx", "pptm", "ppsm", "ppsx", "potm"};

        String[] mimeTypes = new String[]{
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint.presentation.macroenabled.12",
                "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                "application/vnd.ms-powerpoint.template.macroenabled.12"};

        for (int i = 0; i < extensions.length; i++) {
            String extension = extensions[i];
            String filename = "testPPT." + extension;

            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();

            try (TikaInputStream tis = getResourceAsStream("/test-documents/" + filename)) {
                AUTO_DETECT_PARSER.parse(tis, handler, metadata, new ParseContext());

                assertEquals(mimeTypes[i], metadata.get(Metadata.CONTENT_TYPE),
                        "Mime-type checking for " + filename);
                assertEquals("Attachment Test", metadata.get(TikaCoreProperties.TITLE));
                assertEquals("Rajiv", metadata.get(TikaCoreProperties.CREATOR));

                String content = handler.toString();
                if (extension.equals("thmx")) {
                    assertEquals("", content);
                } else {
                    assertTrue(content.contains("Attachment Test"),
                            "Text missing for " + filename + "\n" + content);
                    assertTrue(content.contains("This is a test file data with the same content"),
                            "Text missing for " + filename + "\n" + content);
                    assertTrue(content.contains("content parsing"),
                            "Text missing for " + filename + "\n" + content);
                    assertTrue(content.contains("Different words to test against"),
                            "Text missing for " + filename + "\n" + content);
                    assertTrue(content.contains("Mystery"),
                            "Text missing for " + filename + "\n" + content);
                }
            }
        }
    }

    @Test
    public void testPowerPointMetadataEarly() throws Exception {
        String[] extensions = new String[]{"pptx", "pptm", "ppsm", "ppsx", "potm"};

        final String[] mimeTypes = new String[]{
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint.presentation.macroenabled.12",
                "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                "application/vnd.ms-powerpoint.template.macroenabled.12"};

        for (int i = 0; i < extensions.length; i++) {
            String extension = extensions[i];
            final String filename = "testPPT." + extension;
            final Metadata metadata = new Metadata();

            final int currentI = i;
            ContentHandler handler = new BodyContentHandler() {
                public void startDocument() {
                    assertEquals(mimeTypes[currentI], metadata.get(Metadata.CONTENT_TYPE),
                            "Mime-type checking for " + filename);
                    assertEquals("Attachment Test", metadata.get(TikaCoreProperties.TITLE));
                    assertEquals("Rajiv", metadata.get(TikaCoreProperties.CREATOR));
                }
            };

            try (TikaInputStream tis = getResourceAsStream("/test-documents/" + filename)) {
                AUTO_DETECT_PARSER.parse(tis, handler, metadata, new ParseContext());
            }
        }
    }

    @Test
    public void testUnsupportedPowerPoint() throws Exception {
        String[] extensions = new String[]{"xps", "thmx"};
        String[] mimeTypes = new String[]{"application/vnd.ms-xpsdocument",
                "application/vnd.openxmlformats-officedocument"};

        for (int i = 0; i < extensions.length; i++) {
            String extension = extensions[i];
            String filename = "testPPT." + extension;

            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            ContentHandler handler = new BodyContentHandler();

            try (TikaInputStream tis = getResourceAsStream("/test-documents/" + filename)) {
                AUTO_DETECT_PARSER.parse(tis, handler, metadata, new ParseContext());
                assertEquals(mimeTypes[i], metadata.get(Metadata.CONTENT_TYPE),
                        "Mime-type checking for " + filename);
            }
        }
    }

    @Test
    public void testVariousPPTX() throws Exception {
        Metadata metadata = new Metadata();
        String xml = getXML("testPPT_various.pptx", metadata).xml;
        assertContains("<p>Footnote appears here", xml);
        assertContains("<p>[1] This is a footnote.", xml);
        assertContains("<p>This is the header text.</p>", xml);
        assertContains("<p>This is the footer text.</p>", xml);
        assertContains("<p>Here is a text box</p>", xml);
        assertContains("<p>Bold", xml);
        assertContains("italic underline superscript subscript", xml);
        assertContains("<p>Here is a citation:", xml);
        assertContains("Figure 1 This is a caption for Figure 1", xml);
        assertContains("(Kramer)", xml);
        assertContains("<table><tr>\t<td>Row 1 Col 1</td>", xml);
        assertContains("<td>Row 2 Col 2</td>\t<td>Row 2 Col 3</td></tr>", xml);
        assertContains("<p>Row 1 column 1</p>", xml);
        assertContains("<p>Row 2 column 2</p>", xml);
        assertContains("<p><a href=\"http://tika.apache.org/\">This is a hyperlink</a>", xml);
        assertContains("<p>Here is a list:", xml);
        for (int row = 1; row <= 3; row++) {
            assertContains("<p>Bullet " + row, xml);
        }
        assertContains("Here is a numbered list:", xml);
        for (int row = 1; row <= 3; row++) {
            assertContains("<p>Number bullet " + row, xml);
        }
        for (int row = 1; row <= 2; row++) {
            for (int col = 1; col <= 3; col++) {
                assertContains("Row " + row + " Col " + col, xml);
            }
        }
        assertContains("Keyword1 Keyword2", xml);
        assertEquals("Keyword1 Keyword2", metadata.get(Office.KEYWORDS));
        assertContains("Subject is here", xml);
        assertContains("Suddenly some Japanese text:", xml);
        assertContains("\uff08\uff27\uff28\uff31\uff09", xml);
        assertContains("\u30be\u30eb\u30b2\u3068\u5c3e\u5d0e\u3001\u6de1\u3005\u3068\u6700\u671f",
                xml);
        assertContains("And then some Gothic text:", xml);
        assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44\uD800\uDF39\uD800\uDF43\uD800\uDF3A",
                xml);
    }

    @Test
    public void testCommentPPTX() throws Exception {
        XMLResult r = getXML("testPPT_comment.pptx");
        assertContains("<p class=\"slide-comment\"><b>Allison, Timothy B. (ATB)", r.xml);
    }

    @Test
    public void testMasterFooter() throws Exception {
        assertContains("Master footer is here",
                getXML("testPPT_masterFooter.pptx").xml);
    }

    @Test
    @Disabled("can't tell why this isn't working")
    public void testTurningOffMasterContent() throws Exception {
        ParseContext context = new ParseContext();
        OfficeParserConfig config = new OfficeParserConfig();
        config.setIncludeSlideMasterContent(false);
        context.set(OfficeParserConfig.class, config);
        String xml = getXML("testPPT_masterFooter.pptx", context).xml;
        assertNotContained("Master footer", xml);
    }

    @Test
    public void testMasterText() throws Exception {
        assertContains("Text that I added to the master slide",
                getXML("testPPT_masterText.pptx").xml);

        ParseContext context = new ParseContext();
        OfficeParserConfig config = new OfficeParserConfig();
        config.setIncludeSlideMasterContent(false);
        context.set(OfficeParserConfig.class, config);
        assertNotContained("Text that I added",
                getXML("testPPT_masterText.pptx", context).xml);
    }

    @Test
    public void testMasterText2() throws Exception {
        assertContains("Text that I added to the master slide",
                getXML("testPPT_masterText2.pptx").xml);

        ParseContext context = new ParseContext();
        OfficeParserConfig config = new OfficeParserConfig();
        config.setIncludeSlideMasterContent(false);
        context.set(OfficeParserConfig.class, config);
        assertNotContained("Text that I added",
                getXML("testPPT_masterText2.pptx", context).xml);
    }

    @Test
    public void testWordArt() throws Exception {
        assertContains("Here is some red word Art",
                getXML("testWordArt.pptx").xml);
    }

    @Test
    public void testPPTXAutodate() throws Exception {
        XMLResult result = getXML("testPPT_autodate.pptx");
        assertContains("<p>Now</p>\n" + "<p>2011-12-19 10:20:04 AM</p>\n", result.xml);
    }

    @Test
    public void testPPTXThumbnail() throws Exception {
        String xml = getXML("testPPTX_Thumbnail.pptx").xml;
        int a = xml.indexOf(
                "<body><div class=\"slide-content\"><p>This file contains an embedded thumbnail");
        int b = xml.indexOf("<div class=\"embedded\" id=\"/docProps/thumbnail.jpeg\" />");
        assertTrue(a != -1);
        assertTrue(b != -1);
        assertTrue(a < b);
    }

    @Test
    public void testPPTXDiagramData() throws Exception {
        assertContains("President",
                getXML("testPPT_diagramData.pptx").xml);
    }

    @Test
    public void testPPTXChartData() throws Exception {
        String xml = getXML("testPPT_charts.pptx").xml;
        assertContains("peach", xml);
        assertContains("March\tApril", xml);
        assertNotContained("chartSpace", xml);
    }

    @Test
    public void testPPTXGroups() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testPPT_groups.pptx");
        assertEquals(3, metadataList.size());
        String content = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("WordArt1", content);
        assertContains("WordArt2", content);
        assertContainsCount("Ungrouped text box", content, 1);
        assertContains("Text box1", content);
        assertContains("Text box2", content);
        assertContains("Text box3", content);
        assertContains("Text box4", content);
        assertContains("Text box5", content);
        assertContains("href=\"http://tika.apache.org", content);
        assertContains("smart1", content);
        assertContains("MyTitle", content);
        assertEquals("/image1.jpg",
                metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
        assertEquals("/thumbnail.jpeg",
                metadataList.get(2).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
    }

    @Test
    public void testEmbeddedMedia() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testPPT_embeddedMP3.pptx");
        assertEquals(4, metadataList.size());
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertEquals("audio/mpeg", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals("image/png", metadataList.get(2).get(Metadata.CONTENT_TYPE));
        assertEquals("image/jpeg", metadataList.get(3).get(Metadata.CONTENT_TYPE));
    }

    // ---- SAX-specific tests ----

    @Test
    public void basicTest() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testPPT_various2.pptx");

        assertEquals(15, metadataList.size(), "right number of attachments");

        String mainContent = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("This slide is hidden", mainContent);
        assertContains("FirstBullet", mainContent);
        assertContains("<a href=\"http://tika.apache.org/\">tika_hyperlink</a>", mainContent);
        assertContains("<a href=\"http://lucene.apache.org/\">lucene_hyperlink</a>",
                mainContent);
        assertContains("Slide2TextBox", mainContent);
        assertContains("<td>R1c1</td>", mainContent);
        assertContains("This is some WordART", mainContent);
        assertContains("NotesForSlide2", mainContent);
        assertContains("Notes for slide3", mainContent);
        assertContains("NotesMasterHeader", mainContent);
        assertContains("NotesMasterFooter", mainContent);
        assertContains("NotesMasterPageNumber", mainContent);
        assertContains("NotesWordArt", mainContent);
        assertContains("NotesWordArtPage2", mainContent);
        assertContains("NotesTableSlide2", mainContent);
        assertContains(
                "<p class=\"slide-comment\"><b>Timothy Allison (TA)</b>This is a reply to the " +
                        "initial comment</p>",
                mainContent);
        assertContains("HandoutHeader1", mainContent);
        assertContains("HandoutFooter", mainContent);
        assertContains("HandoutDate", mainContent);
        assertContains("TextBoxInHandOut", mainContent);
        assertContains("MASTERTEXTBOX", mainContent);
        assertContains("3/4", mainContent);
        assertContains("<p>12/16/2016</p>", mainContent);
        assertContains("<p>8</p>", mainContent);
        assertContains("<td>NotesTableSlide2", mainContent);
        assertContains("MASTERFOOTERMSG", mainContent);
        assertNotContained("Click to edit Master", mainContent);
        assertNotContained("Second level", mainContent);
    }

    @Test
    public void poiBug54916Test() throws Exception {
        String xml = getXML("testPPTX_overlappingRelations.pptx").xml;
        assertContains("POI cannot read this", xml);
        assertContains("Has a relationship to another slide", xml);
        assertContains("can read this too", xml);
    }

    @Test
    public void testPowerPointCustomProperties() throws Exception {
        Metadata metadata = new Metadata();
        getXML("testPPT_custom_props.pptx", metadata);
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("JOUVIN ETIENNE", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("EJ04325S", metadata.get(TikaCoreProperties.MODIFIER));
        assertEquals("2011-08-22T13:30:53Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2011-08-22T13:32:49Z", metadata.get(TikaCoreProperties.MODIFIED));
        assertEquals("1", metadata.get(Office.SLIDE_COUNT));
        assertEquals("3", metadata.get(Office.WORD_COUNT));
        assertEquals("Test extraction properties pptx", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("true", metadata.get("custom:myCustomBoolean"));
        assertEquals("3", metadata.get("custom:myCustomNumber"));
        assertEquals("MyStringValue", metadata.get("custom:MyCustomString"));
        assertEquals("2010-12-30T22:00:00Z", metadata.get("custom:MyCustomDate"));
        assertEquals("2010-12-29T22:00:00Z", metadata.get("custom:myCustomSecondDate"));
    }

    @Test
    @Disabled("TODO: add in embedded file markup")
    public void testEmbeddedZipInPPTX() throws Exception {
        String xml = getXML("test_embedded_zip.pptx").xml;
        int h = xml.indexOf("<div class=\"embedded\" id=\"slide1_rId3\" />");
        int i = xml.indexOf("Send me a note");
        int j = xml.indexOf("<div class=\"embedded\" id=\"slide2_rId4\" />");
        int k = xml.indexOf("<p>No title</p>");
        assertTrue(h != -1);
        assertTrue(i != -1);
        assertTrue(j != -1);
        assertTrue(k != -1);
        assertTrue(h < i);
        assertTrue(i < j);
        assertTrue(j < k);
    }

    @Test
    @Disabled("TODO: add in embedded file markup")
    public void testEmbeddedPPTXTwoSlides() throws Exception {
        String xml = getXML("testPPT_embedded_two_slides.pptx").xml;
        assertContains("<div class=\"embedded\" id=\"slide1_rId7\" />", xml);
        assertContains("<div class=\"embedded\" id=\"slide2_rId7\" />", xml);
    }

    @Test
    public void testMacrosInPptm() throws Exception {
        Metadata parsedBy = new Metadata();
        parsedBy.add(TikaCoreProperties.TIKA_PARSED_BY,
                "org.apache.tika.parser.microsoft.ooxml.xslf.XSLFEventBasedPowerPointExtractor");

        List<Metadata> metadataList =
                getRecursiveMetadata("testPPT_macros.pptm");

        for (Metadata metadata : metadataList) {
            if (metadata.get(Metadata.CONTENT_TYPE).equals("text/x-vbasic")) {
                fail("Shouldn't have extracted macros as default");
            }
        }
        assertContainsAtLeast(parsedBy, metadataList);

        ParseContext context = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractMacros(true);
        context.set(OfficeParserConfig.class, officeParserConfig);

        Metadata minExpected = new Metadata();
        minExpected.add(TikaCoreProperties.TIKA_CONTENT.getName(), "Sub Embolden()");
        minExpected.add(TikaCoreProperties.TIKA_CONTENT.getName(), "Sub Italicize()");
        minExpected.add(Metadata.CONTENT_TYPE, "text/x-vbasic");
        minExpected.add(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());

        metadataList = getRecursiveMetadata("testPPT_macros.pptm", context);
        assertContainsAtLeast(minExpected, metadataList);
        assertContainsAtLeast(parsedBy, metadataList);

        AutoDetectParser parser = (AutoDetectParser) TikaLoader.load(
                getConfigPath(OOXMLPptxSAXTest.class, "tika-config-sax-macros.json"))
                .loadAutoDetectParser();
        metadataList = getRecursiveMetadata("testPPT_macros.pptm", parser);
        assertContainsAtLeast(minExpected, metadataList);
        assertContainsAtLeast(parsedBy, metadataList);
    }

    @Test
    public void testEncrypted() throws Exception {
        Map<String, String> tests = new HashMap<>();
        tests.put("testPPT_protected_passtika.pptx",
                "This is an encrypted PowerPoint 2007 slide.");

        Metadata m = new Metadata();
        PasswordProvider passwordProvider = metadata -> "tika";
        ParseContext passwordContext = new ParseContext();
        passwordContext.set(PasswordProvider.class, passwordProvider);

        for (Map.Entry<String, String> e : tests.entrySet()) {
            try (TikaInputStream tis = getResourceAsStream("/test-documents/" + e.getKey())) {
                ContentHandler handler = new BodyContentHandler();
                AUTO_DETECT_PARSER.parse(tis, handler, m, passwordContext);
                assertContains(e.getValue(), handler.toString());
            }
        }

        ParseContext context = new ParseContext();
        for (Map.Entry<String, String> e : tests.entrySet()) {
            boolean exc = false;
            try (TikaInputStream tis = getResourceAsStream("/test-documents/" + e.getKey())) {
                ContentHandler handler = new BodyContentHandler();
                AUTO_DETECT_PARSER.parse(tis, handler, m, context);
            } catch (EncryptedDocumentException ex) {
                exc = true;
            }
            assertTrue(exc);
        }
    }
}
