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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Runs the shared PPTX tests using the DOM-based parser (default),
 * plus DOM-specific tests.
 */
public class OOXMLPptxDOMTest extends AbstractOOXMLPptxTest {

    @Override
    ParseContext getParseContext() {
        return new ParseContext();
    }

    @Test
    public void testVariousPPTXDOMExtra() throws Exception {
        // DOM version has extra assertions for DublinCore.SUBJECT and TikaCoreProperties.SUBJECT
        Metadata metadata = new Metadata();
        getXML("testPPT_various.pptx", metadata, getParseContext());

        assertEquals("Subject is here", metadata.get(DublinCore.SUBJECT));

        assertContains("Keyword1 Keyword2",
                Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));
        assertContains("Subject is here",
                Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));
    }

    @Test
    public void testPowerPointCustomProperties() throws Exception {
        Metadata metadata = new Metadata();

        try (TikaInputStream tis =
                     getResourceAsStream("/test-documents/testPPT_custom_props.pptx")) {
            ContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            new OOXMLParser().parse(tis, handler, metadata, context);
        }

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
    public void testEmbeddedPPTXTwoSlides() throws Exception {
        String xml = getXML("testPPT_embedded_two_slides.pptx", getParseContext()).xml;
        assertContains("<div class=\"embedded\" id=\"slide1_rId7\" />", xml);
        assertContains("<div class=\"embedded\" id=\"slide2_rId7\" />", xml);
    }

    @Test
    public void testMacrosInPptm() throws Exception {

        //test default is "don't extract macros"
        for (Metadata metadata : getRecursiveMetadata("testPPT_macros.pptm", getParseContext())) {
            if (metadata.get(Metadata.CONTENT_TYPE).equals("text/x-vbasic")) {
                fail("Shouldn't have extracted macros as default");
            }
        }

        //now test that they were extracted
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

        assertContainsAtLeast(minExpected,
                getRecursiveMetadata("testPPT_macros.pptm", context));

        //test configuring via config file
        Parser parser = TikaLoader.load(
                getConfigPath(OOXMLParserTest.class, "tika-config-dom-macros.json"))
                .loadAutoDetectParser();
        assertContainsAtLeast(minExpected,
                getRecursiveMetadata("testPPT_macros.pptm", parser));
    }

    @Test
    public void testEncrypted() throws Exception {
        Map<String, String> tests = new HashMap<>();
        tests.put("testPPT_protected_passtika.pptx",
                "This is an encrypted PowerPoint 2007 slide.");

        PasswordProvider passwordProvider = metadata -> "tika";
        ParseContext passwordContext = new ParseContext();
        passwordContext.set(PasswordProvider.class, passwordProvider);

        for (Map.Entry<String, String> e : tests.entrySet()) {
            assertContains(e.getValue(), getXML(e.getKey(), passwordContext).xml);
        }

        //now try with no password
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
    public void testSkipHeaderFooter() throws Exception {
        //now test turning off header/footer
        OfficeParserConfig config = new OfficeParserConfig();
        config.setIncludeHeadersAndFooters(false);
        ParseContext context = new ParseContext();
        context.set(OfficeParserConfig.class, config);
        String xml = getXML("testPPT_various.pptx", context).xml;
        assertNotContained("This is the header text", xml);
    }

    @Test
    public void testEmbeddedMediaDOMExtra() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testPPT_embeddedMP3.pptx", getParseContext());
        // Verify INTERNAL_PATH is set for embedded media (DOM-specific)
        assertNotNull(metadataList.get(1).get(TikaCoreProperties.INTERNAL_PATH));
        assertTrue(metadataList.get(1).get(TikaCoreProperties.INTERNAL_PATH)
                .contains("/ppt/media/"));
    }
}
