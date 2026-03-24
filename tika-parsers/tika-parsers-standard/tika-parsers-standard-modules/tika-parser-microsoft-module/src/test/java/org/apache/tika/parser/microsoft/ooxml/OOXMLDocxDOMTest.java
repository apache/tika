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
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.microsoft.OfficeParserTest;

/**
 * Runs the shared DOCX tests using the DOM-based parser (default),
 * plus DOM-specific tests.
 */
public class OOXMLDocxDOMTest extends AbstractOOXMLDocxTest {

    @Override
    ParseContext getParseContext() {
        return new ParseContext();
    }

    @Test
    public void testTextDecorationNestedUnderlineStrike() throws Exception {
        // DOM nests s inside u: <u>unde<s>r</s>line</u>
        String xml = getXML("testWORD_various.docx", getParseContext()).xml;
        assertContains("<i><u>unde<s>r</s>line</u></i>", xml);
    }

    @Test
    public void testDOCXHeaderFooterNotExtractionViaConfig() throws Exception {
        // Test configuration via tika-config
        AutoDetectParser configuredParser = (AutoDetectParser) TikaLoader.load(
                getConfigPath(OfficeParserTest.class, "tika-config-headers-footers.json"))
                .loadAutoDetectParser();
        String xml = getXML("testWORD_various.docx", configuredParser).xml;
        assertNotContained("This is the header text.", xml);
        assertNotContained("This is the footer text.", xml);
    }

    @Test
    public void testDOCXParagraphNumbering() throws Exception {
        String xml = getXML("testWORD_numbered_list.docx", getParseContext()).xml;
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
        assertContains("Some-1-CrazyFormat Greek numbering with crazy format - alpha", xml);
        assertContains("1.1.1. 1.1.1", xml);
        assertContains("1.1. 1.2-&gt;1.1  //set the value", xml);
    }

    @Test
    public void testMacrosInDocm() throws Exception {
        //test default is "don't extract macros"
        for (Metadata metadata : getRecursiveMetadata("testWORD_macros.docm", getParseContext())) {
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
                getRecursiveMetadata("testWORD_macros.docm", context));

        //test configuring via config file
        AutoDetectParser parser = (AutoDetectParser) TikaLoader.load(
                getConfigPath(OOXMLParserTest.class, "tika-config-dom-macros.json"))
                .loadAutoDetectParser();
        assertContainsAtLeast(minExpected,
                getRecursiveMetadata("testWORD_macros.docm", parser));
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

}
