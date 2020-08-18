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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.ContentHandler;


public class SXSLFExtractorTest extends TikaTest {

    private ParseContext parseContext;
    OfficeParserConfig officeParserConfig = new OfficeParserConfig();

    @Before
    public void setUp() {
        parseContext = new ParseContext();
        officeParserConfig.setUseSAXPptxExtractor(true);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);

    }

    @Test
    public void basicTest() throws Exception {

        List<Metadata> metadataList = getRecursiveMetadata("testPPT_various2.pptx", parseContext);

        assertEquals("right number of attachments", 14, metadataList.size());

        String mainContent = metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT);

        assertContains("This slide is hidden", mainContent);//TODO: parameterize this

        //basic content
        assertContains("FirstBullet", mainContent);

        //hyperlink
        assertContains("<a href=\"http://tika.apache.org/\">tika_hyperlink</a>", mainContent);
        //hyperlink in cell
        assertContains("<a href=\"http://lucene.apache.org/\">lucene_hyperlink</a>", mainContent);

        //text box
        assertContains("Slide2TextBox", mainContent);
        assertContains("<td>R1c1</td>", mainContent);

        //wordArt
        assertContains("This is some WordART", mainContent);

        //notes
        assertContains("NotesForSlide2", mainContent);
        assertContains("Notes for slide3", mainContent);
        assertContains("NotesMasterHeader", mainContent);
        assertContains("NotesMasterFooter", mainContent);
        assertContains("NotesMasterPageNumber", mainContent);
        assertContains("NotesWordArt", mainContent);
        assertContains("NotesWordArtPage2", mainContent);
        assertContains("NotesTableSlide2", mainContent);

        //comments
        assertContains(
                "<p class=\"slide-comment\"><b>Timothy Allison (TA)</b>This is a reply to the initial comment</p>",
                    mainContent);

        //HandoutMaster
        assertContains(
                "HandoutHeader1", mainContent);
        assertContains("HandoutFooter", mainContent);
        assertContains("HandoutDate", mainContent);
        assertContains("TextBoxInHandOut", mainContent);

        //text box in master
        assertContains("MASTERTEXTBOX", mainContent);

        //equation
        assertContains("3/4", mainContent);

        //make sure footer elements are in their own <p/>
        assertContains("<p>12/16/2016</p>", mainContent);
        assertContains("<p>8</p>", mainContent);


        assertContains("<td>NotesTableSlide2", mainContent);

        assertContains("MASTERFOOTERMSG", mainContent);


        //should not include boilerplate from master
        assertNotContained("Click to edit Master", mainContent);
        assertNotContained("Second level", mainContent);

        //TODO: chart content
        //assertContains("SLIDE3ChartTitle", mainContent);
        //assertContains("Category 1", mainContent);
    }

    @Test
    public void  poiBug54916Test() throws Exception {
        String xml = getXML("testPPTX_overlappingRelations.pptx", parseContext).xml;
        assertContains("POI cannot read this", xml);
        assertContains("Has a relationship to another slide", xml);
        assertContains("can read this too", xml);
    }

    /**
     * We have a number of different powerpoint files,
     * such as presentation, macro-enabled etc
     */
    @Test
    public void testPowerPoint() throws Exception {
        String[] extensions = new String[]{
                "pptx", "pptm",
                "ppsm",
                "ppsx", "potm",
                //"thmx", // TIKA-418: Will be supported in POI 3.7 beta 2
                //"xps" // TIKA-418: Not yet supported by POI
        };

        String[] mimeTypes = new String[]{
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint.presentation.macroenabled.12",
                "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                "application/vnd.ms-powerpoint.template.macroenabled.12",
        };

        for (int i = 0; i < extensions.length; i++) {
            String extension = extensions[i];
            String filename = "testPPT." + extension;

            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();

            try (InputStream input = getResourceAsStream("/test-documents/" + filename)) {
                AUTO_DETECT_PARSER.parse(input, handler, metadata, parseContext);

                assertEquals(
                        "Mime-type checking for " + filename,
                        mimeTypes[i],
                        metadata.get(Metadata.CONTENT_TYPE));
                assertEquals("Attachment Test", metadata.get(TikaCoreProperties.TITLE));
                assertEquals("Rajiv", metadata.get(TikaCoreProperties.CREATOR));

                String content = handler.toString();
                // Theme files don't have the text in them
                if (extension.equals("thmx")) {
                    assertEquals("", content);
                } else {
                    assertTrue(
                            "Text missing for " + filename + "\n" + content,
                            content.contains("Attachment Test")
                    );
                    assertTrue(
                            "Text missing for " + filename + "\n" + content,
                            content.contains("This is a test file data with the same content")
                    );
                    assertTrue(
                            "Text missing for " + filename + "\n" + content,
                            content.contains("content parsing")
                    );
                    assertTrue(
                            "Text missing for " + filename + "\n" + content,
                            content.contains("Different words to test against")
                    );
                    assertTrue(
                            "Text missing for " + filename + "\n" + content,
                            content.contains("Mystery")
                    );
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
        String[] extensions = new String[]{
                "pptx", "pptm", "ppsm", "ppsx", "potm"
                //"thmx", // TIKA-418: Will be supported in POI 3.7 beta 2
                //"xps" // TIKA-418: Not yet supported by POI
        };

        final String[] mimeTypes = new String[]{
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint.presentation.macroenabled.12",
                "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                "application/vnd.ms-powerpoint.template.macroenabled.12"
        };

        for (int i = 0; i < extensions.length; i++) {
            String extension = extensions[i];
            final String filename = "testPPT." + extension;
            final Metadata metadata = new Metadata();

            // Allow the value to be access from the inner class
            final int currentI = i;
            ContentHandler handler = new BodyContentHandler() {
                public void startDocument() {
                    assertEquals(
                            "Mime-type checking for " + filename,
                            mimeTypes[currentI],
                            metadata.get(Metadata.CONTENT_TYPE));
                    assertEquals("Attachment Test", metadata.get(TikaCoreProperties.TITLE));
                    assertEquals("Rajiv", metadata.get(TikaCoreProperties.CREATOR));

                }

            };

            try (InputStream input = getResourceAsStream("/test-documents/" + filename)) {
                AUTO_DETECT_PARSER.parse(input, handler, metadata, parseContext);
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
        String[] mimeTypes = new String[]{
                "application/vnd.ms-xpsdocument",
                "application/vnd.openxmlformats-officedocument" // Is this right?
        };

        for (int i = 0; i < extensions.length; i++) {
            String extension = extensions[i];
            String filename = "testPPT." + extension;

            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            ContentHandler handler = new BodyContentHandler();

            try (InputStream input = getResourceAsStream("/test-documents/" + filename)) {
                AUTO_DETECT_PARSER.parse(input, handler, metadata, parseContext);

                // Should get the metadata
                assertEquals(
                        "Mime-type checking for " + filename,
                        mimeTypes[i],
                        metadata.get(Metadata.CONTENT_TYPE));

                // But that's about it
            }
        }
    }

    @Test
    public void testVariousPPTX() throws Exception {
        Metadata metadata = new Metadata();
        String xml = getXML("testPPT_various.pptx", metadata, parseContext).xml;
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
            //assertContains("·\tBullet " + row, content);
            //assertContains("\u00b7\tBullet " + row, content);
            assertContains("<p>Bullet " + row, xml);
        }
        assertContains("Here is a numbered list:", xml);
        for (int row = 1; row <= 3; row++) {
            //assertContains(row + ")\tNumber bullet " + row, content);
            //assertContains(row + ") Number bullet " + row, content);
            // TODO: OOXMLExtractor fails to number the bullets:
            assertContains("<p>Number bullet " + row, xml);
        }

        for (int row = 1; row <= 2; row++) {
            for (int col = 1; col <= 3; col++) {
                assertContains("Row " + row + " Col " + col, xml);
            }
        }

        assertContains("Keyword1 Keyword2", xml);
        assertEquals("Keyword1 Keyword2",
                metadata.get(Office.KEYWORDS));

        assertContains("Subject is here", xml);

        assertContains("Suddenly some Japanese text:", xml);
        // Special version of (GHQ)
        assertContains("\uff08\uff27\uff28\uff31\uff09", xml);
        // 6 other characters
        assertContains("\u30be\u30eb\u30b2\u3068\u5c3e\u5d0e\u3001\u6de1\u3005\u3068\u6700\u671f", xml);

        assertContains("And then some Gothic text:", xml);
        assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44\uD800\uDF39\uD800\uDF43\uD800\uDF3A", xml);
    }

    @Test
    public void testCommentPPTX() throws Exception {
        XMLResult r = getXML("testPPT_comment.pptx", parseContext);
        assertContains("<p class=\"slide-comment\"><b>Allison, Timothy B. (ATB)", r.xml);
    }

    @Test
    public void testMasterFooter() throws Exception {

        assertContains("Master footer is here",
                getXML("testPPT_masterFooter.pptx", parseContext).xml);
    }

    @Test
    @Ignore("can't tell why this isn't working")
    public void testTurningOffMasterContent() throws Exception {
        //now test turning off master content

        //the underlying xml has "Master footer" in
        //the actual slide's xml, not just in the master slide.
        OfficeParserConfig config = new OfficeParserConfig();
        config.setIncludeSlideMasterContent(false);
        config.setUseSAXPptxExtractor(true);
        ParseContext context = new ParseContext();
        context.set(OfficeParserConfig.class, config);
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
                getXML("testPPT_masterText.pptx", parseContext).xml);

        //now test turning off master content
        OfficeParserConfig config = new OfficeParserConfig();
        config.setIncludeSlideMasterContent(false);
        config.setUseSAXPptxExtractor(true);
        ParseContext context = new ParseContext();
        context.set(OfficeParserConfig.class, config);

        assertNotContained("Text that I added",
                getXML("testPPT_masterText.pptx", context).xml);
    }

    @Test
    public void testMasterText2() throws Exception {
        assertContains("Text that I added to the master slide",
                getXML("testPPT_masterText2.pptx", parseContext).xml);

        //now test turning off master content
        OfficeParserConfig config = new OfficeParserConfig();
        config.setIncludeSlideMasterContent(false);
        config.setUseSAXPptxExtractor(true);
        ParseContext context = new ParseContext();
        context.set(OfficeParserConfig.class, config);

        assertNotContained("Text that I added",
                getXML("testPPT_masterText2.pptx", context).xml);
    }

    @Test
    public void testWordArt() throws Exception {
        assertContains("Here is some red word Art",
                getXML("testWordArt.pptx", parseContext).xml);
    }

    @Test
    public void testPowerPointCustomProperties() throws Exception {
        Metadata metadata = new Metadata();

        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setUseSAXPptxExtractor(true);
        context.set(OfficeParserConfig.class, officeParserConfig);

        getXML("testPPT_custom_props.pptx", metadata, parseContext);
        assertEquals(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
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

    // TIKA-997:
    @Test
    @Ignore("TODO: add in embedded file markup")
    public void testEmbeddedZipInPPTX() throws Exception {
        String xml = getXML("test_embedded_zip.pptx", parseContext).xml;
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

    // TIKA-1032:
    @Test
    @Ignore("TODO: add in embedded file markup")
    public void testEmbeddedPPTXTwoSlides() throws Exception {
        String xml = getXML("testPPT_embedded_two_slides.pptx", parseContext).xml;
        assertContains("<div class=\"embedded\" id=\"slide1_rId7\" />", xml);
        assertContains("<div class=\"embedded\" id=\"slide2_rId7\" />", xml);
    }

    //TIKA-817
    @Test
    public void testPPTXAutodate() throws Exception {
        //Following POI-52368, the stored date is extracted,
        //not the auto-generated date.

        XMLResult result = getXML("testPPT_autodate.pptx", parseContext);
        assertContains("<p>Now</p>\n" +
                "<p>2011-12-19 10:20:04 AM</p>\n", result.xml);

    }

    @Test
    public void testPPTXThumbnail() throws Exception {
        String xml = getXML("testPPTX_Thumbnail.pptx", parseContext).xml;
        int a = xml.indexOf("<body><div class=\"slide-content\"><p>This file contains an embedded thumbnail");
        int b = xml.indexOf("<div class=\"embedded\" id=\"/docProps/thumbnail.jpeg\" />");
        assertTrue(a != -1);
        assertTrue(b != -1);
        assertTrue(a < b);
    }

    @Test
    public void testEncrypted() throws Exception {
        Map<String, String> tests = new HashMap<String, String>();
        tests.put("testPPT_protected_passtika.pptx",
                "This is an encrypted PowerPoint 2007 slide.");

        Metadata m = new Metadata();
        PasswordProvider passwordProvider = new PasswordProvider() {
            @Override
            public String getPassword(Metadata metadata) {
                return "tika";
            }
        };
        ParseContext passwordContext = new ParseContext();
        passwordContext.set(org.apache.tika.parser.PasswordProvider.class, passwordProvider);
        passwordContext.set(OfficeParserConfig.class, officeParserConfig);

        for (Map.Entry<String, String> e : tests.entrySet()) {
            try (InputStream is = getResourceAsStream("/test-documents/"+e.getKey())) {
                ContentHandler handler = new BodyContentHandler();
                AUTO_DETECT_PARSER.parse(is, handler, m, passwordContext);
                assertContains(e.getValue(), handler.toString());
            }
        }

        ParseContext context = new ParseContext();
        //now try with no password
        for (Map.Entry<String, String> e : tests.entrySet()) {
            boolean exc = false;
            try (InputStream is = getResourceAsStream("/test-documents/"+e.getKey())) {
                ContentHandler handler = new BodyContentHandler();
                AUTO_DETECT_PARSER.parse(is, handler, m, context);
            } catch (EncryptedDocumentException ex) {
                exc = true;
            }
            assertTrue(exc);
        }

    }


    @Test
    public void testMacrosInPptm() throws Exception {

        Metadata parsedBy = new Metadata();
        parsedBy.add("X-Parsed-By",
                "org.apache.tika.parser.microsoft.ooxml.xslf.XSLFEventBasedPowerPointExtractor");

        List<Metadata> metadataList = getRecursiveMetadata("testPPT_macros.pptm", parseContext);

        //test default is "don't extract macros"
        for (Metadata metadata : metadataList) {
            if (metadata.get(Metadata.CONTENT_TYPE).equals("text/x-vbasic")) {
                fail("Shouldn't have extracted macros as default");
            }
        }

        assertContainsAtLeast(parsedBy, metadataList);

        //now test that they are extracted
        ParseContext context = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractMacros(true);
        officeParserConfig.setUseSAXPptxExtractor(true);
        context.set(OfficeParserConfig.class, officeParserConfig);

        Metadata minExpected = new Metadata();
        minExpected.add(RecursiveParserWrapper.TIKA_CONTENT.getName(), "Sub Embolden()");
        minExpected.add(RecursiveParserWrapper.TIKA_CONTENT.getName(), "Sub Italicize()");
        minExpected.add(Metadata.CONTENT_TYPE, "text/x-vbasic");
        minExpected.add(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());

        metadataList = getRecursiveMetadata("testPPT_macros.pptm", context);

        assertContainsAtLeast(minExpected, metadataList);
        assertContainsAtLeast(parsedBy, metadataList);

        //test configuring via config file
        TikaConfig tikaConfig = new TikaConfig(this.getClass().getResourceAsStream("tika-config-sax-macros.xml"));
        AutoDetectParser parser = new AutoDetectParser(tikaConfig);
        metadataList = getRecursiveMetadata("testPPT_macros.pptm", parser);
        assertContainsAtLeast(minExpected, metadataList);
        assertContainsAtLeast(parsedBy, metadataList);

    }

    @Test
    public void testDiagramData() throws Exception {
        assertContains("President", getXML("testPPT_diagramData.pptx", parseContext).xml);
    }

    @Test
    public void testPPTXChartData() throws Exception {
        String xml = getXML("testPPT_charts.pptx", parseContext).xml;
        assertContains("peach", xml);
        assertContains("March\tApril", xml);
        assertNotContained("chartSpace", xml);
    }

    @Test
    public void testEmbeddedMedia() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_embeddedMP3.pptx", parseContext);
        assertEquals(4, metadataList.size());
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertEquals("audio/mpeg", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals("image/png", metadataList.get(2).get(Metadata.CONTENT_TYPE));
        assertEquals("image/jpeg", metadataList.get(3).get(Metadata.CONTENT_TYPE));

    }

    @Test
    public void testPPTXGroups() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_groups.pptx", parseContext);
        assertEquals(3, metadataList.size());
        String content = metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT);
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
                metadataList.get(1).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH));

        assertEquals("/thumbnail.jpeg",
                metadataList.get(2).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH));
    }

}
