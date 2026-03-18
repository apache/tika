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

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.sax.BodyContentHandler;

public abstract class AbstractOOXMLPptxTest extends TikaTest {

    abstract ParseContext getParseContext();

    protected OfficeParserConfig getOrCreateOfficeParserConfig(ParseContext parseContext) {
        OfficeParserConfig config = parseContext.get(OfficeParserConfig.class);
        if (config == null) {
            config = new OfficeParserConfig();
            parseContext.set(OfficeParserConfig.class, config);
        }
        return config;
    }

    /**
     * We have a number of different powerpoint files,
     * such as presentation, macro-enabled etc
     */
    @Test
    public void testPowerPoint() throws Exception {
        String[] extensions = new String[]{"pptx", "pptm", "ppsm", "ppsx", "potm"
                //"thmx", // TIKA-418: Will be supported in POI 3.7 beta 2
                //"xps" // TIKA-418: Not yet supported by POI
        };

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
                AUTO_DETECT_PARSER.parse(tis, handler, metadata, getParseContext());

                assertEquals(mimeTypes[i], metadata.get(Metadata.CONTENT_TYPE),
                        "Mime-type checking for " + filename);
                assertEquals("Attachment Test", metadata.get(TikaCoreProperties.TITLE));
                assertEquals("Rajiv", metadata.get(TikaCoreProperties.CREATOR));

                String content = handler.toString();
                // Theme files don't have the text in them
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

    /**
     * Test that the metadata is already extracted when the body is processed.
     * See TIKA-1109
     */
    @Test
    public void testPowerPointMetadataEarly() throws Exception {
        String[] extensions = new String[]{"pptx", "pptm", "ppsm", "ppsx", "potm"
                //"thmx", // TIKA-418: Will be supported in POI 3.7 beta 2
                //"xps" // TIKA-418: Not yet supported by POI
        };

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

            // Allow the value to be access from the inner class
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
                AUTO_DETECT_PARSER.parse(tis, handler, metadata, getParseContext());
            }
        }
    }

    /**
     * For the PowerPoint formats we don't currently support, ensure that
     * we don't break either
     */
    @Test
    public void testUnsupportedPowerPoint() throws Exception {
        String[] extensions = new String[]{"xps", "thmx"};
        String[] mimeTypes = new String[]{"application/vnd.ms-xpsdocument",
                "application/vnd.openxmlformats-officedocument" // Is this right?
        };

        for (int i = 0; i < extensions.length; i++) {
            String extension = extensions[i];
            String filename = "testPPT." + extension;

            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            ContentHandler handler = new BodyContentHandler();

            try (TikaInputStream tis = getResourceAsStream("/test-documents/" + filename)) {
                AUTO_DETECT_PARSER.parse(tis, handler, metadata, getParseContext());

                // Should get the metadata
                assertEquals(mimeTypes[i], metadata.get(Metadata.CONTENT_TYPE),
                        "Mime-type checking for " + filename);

                // But that's about it
            }
        }
    }

    @Test
    public void testVariousPPTX() throws Exception {
        Metadata metadata = new Metadata();
        String xml = getXML("testPPT_various.pptx", metadata, getParseContext()).xml;
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
        // Special version of (GHQ)
        assertContains("\uff08\uff27\uff28\uff31\uff09", xml);
        // 6 other characters
        assertContains("\u30be\u30eb\u30b2\u3068\u5c3e\u5d0e\u3001\u6de1\u3005\u3068\u6700\u671f",
                xml);

        assertContains("And then some Gothic text:", xml);
        assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44\uD800\uDF39\uD800\uDF43\uD800\uDF3A",
                xml);
    }

    @Test
    public void testCommentPPTX() throws Exception {
        XMLResult r = getXML("testPPT_comment.pptx", getParseContext());
        assertContains("<p class=\"slide-comment\"><b>Allison, Timothy B. (ATB)", r.xml);
    }

    @Test
    public void testMasterFooter() throws Exception {
        assertContains("Master footer is here",
                getXML("testPPT_masterFooter.pptx", getParseContext()).xml);
    }

    @Test
    @Disabled("can't tell why this isn't working")
    public void testTurningOffMasterContent() throws Exception {
        //now test turning off master content

        //the underlying xml has "Master footer" in
        //the actual slide's xml, not just in the master slide.
        ParseContext context = getParseContext();
        OfficeParserConfig config = getOrCreateOfficeParserConfig(context);
        config.setIncludeSlideMasterContent(false);
        String xml = getXML("testPPT_masterFooter.pptx", context).xml;
        assertNotContained("Master footer", xml);
    }

    /**
     * TIKA-712 Master Slide Text from PPT and PPTX files
     * should be extracted too
     */
    @Test
    public void testMasterText() throws Exception {
        assertContains("Text that I added to the master slide",
                getXML("testPPT_masterText.pptx", getParseContext()).xml);

        //now test turning off master content
        ParseContext context = getParseContext();
        OfficeParserConfig config = getOrCreateOfficeParserConfig(context);
        config.setIncludeSlideMasterContent(false);

        assertNotContained("Text that I added",
                getXML("testPPT_masterText.pptx", context).xml);
    }

    @Test
    public void testMasterText2() throws Exception {
        assertContains("Text that I added to the master slide",
                getXML("testPPT_masterText2.pptx", getParseContext()).xml);

        //now test turning off master content
        ParseContext context = getParseContext();
        OfficeParserConfig config = getOrCreateOfficeParserConfig(context);
        config.setIncludeSlideMasterContent(false);

        assertNotContained("Text that I added",
                getXML("testPPT_masterText2.pptx", context).xml);
    }

    @Test
    public void testWordArt() throws Exception {
        assertContains("Here is some red word Art",
                getXML("testWordArt.pptx", getParseContext()).xml);
    }

    //TIKA-817
    @Test
    public void testPPTXAutodate() throws Exception {
        //Following POI-52368, the stored date is extracted,
        //not the auto-generated date.

        XMLResult result = getXML("testPPT_autodate.pptx", getParseContext());
        assertContains("<p>Now</p>\n" + "<p>2011-12-19 10:20:04 AM</p>\n", result.xml);
    }

    @Test
    public void testPPTXThumbnail() throws Exception {
        String xml = getXML("testPPTX_Thumbnail.pptx", getParseContext()).xml;
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
                getXML("testPPT_diagramData.pptx", getParseContext()).xml);
    }

    @Test
    public void testPPTXChartData() throws Exception {
        String xml = getXML("testPPT_charts.pptx", getParseContext()).xml;
        assertContains("peach", xml);
        assertContains("March\tApril", xml);
        assertNotContained("chartSpace", xml);
    }

    @Test
    public void testPPTXGroups() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testPPT_groups.pptx", getParseContext());
        assertEquals(3, metadataList.size());
        String content = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("WordArt1", content);
        assertContains("WordArt2", content);
        assertContainsCount("Ungrouped text box", content, 1);//should only be 1
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
                getRecursiveMetadata("testPPT_embeddedMP3.pptx", getParseContext());
        assertEquals(4, metadataList.size());
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertEquals("audio/mpeg", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals("image/png", metadataList.get(2).get(Metadata.CONTENT_TYPE));
        assertEquals("image/jpeg", metadataList.get(3).get(Metadata.CONTENT_TYPE));
    }
}
