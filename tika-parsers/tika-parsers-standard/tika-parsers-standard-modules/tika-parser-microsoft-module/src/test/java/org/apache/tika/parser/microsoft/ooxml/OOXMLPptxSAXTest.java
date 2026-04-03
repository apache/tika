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
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

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
 * Runs the shared PPTX tests using the SAX-based streaming parser,
 * plus SAX-specific tests.
 */
public class OOXMLPptxSAXTest extends AbstractOOXMLPptxTest {

    @Override
    ParseContext getParseContext() {
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setUseSAXPptxExtractor(true);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
        return parseContext;
    }

    @Test
    public void basicTest() throws Exception {

        List<Metadata> metadataList =
                getRecursiveMetadata("testPPT_various2.pptx", getParseContext());

        assertEquals(15, metadataList.size(), "right number of attachments");

        String mainContent = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);

        assertContains("This slide is hidden", mainContent);//TODO: parameterize this

        //basic content
        assertContains("FirstBullet", mainContent);

        //hyperlink
        assertContains("<a href=\"http://tika.apache.org/\">tika_hyperlink</a>", mainContent);
        //hyperlink in cell
        assertContains("<a href=\"http://lucene.apache.org/\">lucene_hyperlink</a>",
                mainContent);

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
                "<p class=\"slide-comment\"><b>Timothy Allison (TA)</b>This is a reply to the " +
                        "initial comment</p>",
                mainContent);

        //HandoutMaster
        assertContains("HandoutHeader1", mainContent);
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
    }

    @Test
    public void poiBug54916Test() throws Exception {
        String xml = getXML("testPPTX_overlappingRelations.pptx", getParseContext()).xml;
        assertContains("POI cannot read this", xml);
        assertContains("Has a relationship to another slide", xml);
        assertContains("can read this too", xml);
    }

    @Test
    public void testPowerPointCustomProperties() throws Exception {
        Metadata metadata = new Metadata();

        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setUseSAXPptxExtractor(true);
        context.set(OfficeParserConfig.class, officeParserConfig);

        getXML("testPPT_custom_props.pptx", metadata, getParseContext());
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

    // TIKA-997:
    @Test
    @Disabled("TODO: add in embedded file markup")
    public void testEmbeddedZipInPPTX() throws Exception {
        String xml = getXML("test_embedded_zip.pptx", getParseContext()).xml;
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
    @Disabled("TODO: add in embedded file markup")
    public void testEmbeddedPPTXTwoSlides() throws Exception {
        String xml = getXML("testPPT_embedded_two_slides.pptx", getParseContext()).xml;
        assertContains("<div class=\"embedded\" id=\"slide1_rId7\" />", xml);
        assertContains("<div class=\"embedded\" id=\"slide2_rId7\" />", xml);
    }

    @Test
    public void testMacrosInPptm() throws Exception {

        Metadata parsedBy = new Metadata();
        parsedBy.add(TikaCoreProperties.TIKA_PARSED_BY,
                "org.apache.tika.parser.microsoft.ooxml.xslf.XSLFEventBasedPowerPointExtractor");

        List<Metadata> metadataList =
                getRecursiveMetadata("testPPT_macros.pptm", getParseContext());

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
        minExpected.add(TikaCoreProperties.TIKA_CONTENT.getName(), "Sub Embolden()");
        minExpected.add(TikaCoreProperties.TIKA_CONTENT.getName(), "Sub Italicize()");
        minExpected.add(Metadata.CONTENT_TYPE, "text/x-vbasic");
        minExpected.add(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());

        metadataList = getRecursiveMetadata("testPPT_macros.pptm", context);

        assertContainsAtLeast(minExpected, metadataList);
        assertContainsAtLeast(parsedBy, metadataList);

        //test configuring via config file
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
        OfficeParserConfig opc = new OfficeParserConfig();
        opc.setUseSAXPptxExtractor(true);
        passwordContext.set(OfficeParserConfig.class, opc);

        for (Map.Entry<String, String> e : tests.entrySet()) {
            try (TikaInputStream tis = getResourceAsStream("/test-documents/" + e.getKey())) {
                ContentHandler handler = new BodyContentHandler();
                AUTO_DETECT_PARSER.parse(tis, handler, m, passwordContext);
                assertContains(e.getValue(), handler.toString());
            }
        }

        ParseContext context = new ParseContext();
        //now try with no password
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
