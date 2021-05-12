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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;

public class ODFParserTest extends TikaTest {
    private static Parser MACRO_PARSER;

    @BeforeClass
    public static void setUp() throws IOException, TikaException, SAXException {
        MACRO_PARSER = new AutoDetectParser(
                new TikaConfig(ODFParserTest.class.getResourceAsStream("tika-config-macros.xml")));
    }

    @Test
    public void testMacroODT() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testODTMacro.odt", MACRO_PARSER);
        assertEquals(5, metadataList.size());
        Metadata parent = metadataList.get(0);

        assertContains("<p>Hello dear user,</p>", parent.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("application/vnd.oasis.opendocument.text", parent.get(Metadata.CONTENT_TYPE));

        //make sure metadata came through
        assertEquals("LibreOffice/6.4.4.2$Linux_X86_64 LibreOffice_project/40$Build-2",
                parent.get("generator"));
        assertEquals(1, parent.getInt(PagedText.N_PAGES).intValue());

        Metadata macro1 = metadataList.get(1);
        assertEquals("MACRO", macro1.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE_KEY));
        assertContains("If WsGQFM Or 2 Then", macro1.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("test", macro1.get(TikaCoreProperties.RESOURCE_NAME_KEY));

        Metadata macro2 = metadataList.get(2);
        assertEquals("MACRO", macro2.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE_KEY));
        assertContains("If WsGQFM Or 1 Then", macro2.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("test2", macro2.get(TikaCoreProperties.RESOURCE_NAME_KEY));

        Metadata image = metadataList.get(3);
        assertEquals("image/png", image.get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testMacroODTandXMLHandler() throws Exception {
        String xml = getXML("testODTMacro.odt", MACRO_PARSER).xml;
        assertContains("Hello dear user", xml);
        assertContains("If WsGQFM Or 1", xml);
        assertContains("If WsGQFM Or 2 Then", xml);
    }

    @Test
    public void testMacroODTandXMLHandlerDefault() throws Exception {
        //test to make sure that macros aren't extracted by the default AutoDetectParser
        String xml = getXML("testODTMacro.odt").xml;
        assertContains("Hello dear user", xml);
        assertNotContained("If WsGQFM Or 1", xml);
        assertNotContained("If WsGQFM Or 2 Then", xml);
    }

    @Test
    public void testMacroODS() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testODSMacro.ods", MACRO_PARSER);
        assertEquals(4, metadataList.size());
        Metadata parent = metadataList.get(0);

        assertContains("<tr>", parent.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("application/vnd.oasis.opendocument.spreadsheet",
                parent.get(Metadata.CONTENT_TYPE));

        Metadata macro = metadataList.get(1);
        assertEquals("MACRO", macro.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE_KEY));
        assertContains("If WsGQFM Or 2 Then", macro.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("test1", macro.get(TikaCoreProperties.RESOURCE_NAME_KEY));

        Metadata image = metadataList.get(2);
        assertEquals("image/png", image.get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testMacroODP() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testODPMacro.odp", MACRO_PARSER);
        assertEquals(3, metadataList.size());
        Metadata parent = metadataList.get(0);

        assertContains("<p", parent.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("application/vnd.oasis.opendocument.presentation",
                parent.get(Metadata.CONTENT_TYPE));
        //make sure metadata came through
        assertEquals(
                "LibreOffice/6.4.3.2$MacOSX_X86_64 " +
                        "LibreOffice_project/747b5d0ebf89f41c860ec2a39efd7cb15b54f2d8",
                parent.get("generator"));

        assertEquals("2", parent.get("editing-cycles"));

        Metadata macro = metadataList.get(1);
        assertEquals("MACRO", macro.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE_KEY));
        assertContains("If WsGQFM Or 2 Then", macro.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("testmodule", macro.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("testmodule", macro.get(TikaCoreProperties.RESOURCE_NAME_KEY));

    }

    @Test
    public void testMacroFODT() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testODTMacro.fodt", MACRO_PARSER);
        assertEquals(3, metadataList.size());
        Metadata parent = metadataList.get(0);

        assertContains("<p>Hello dear user,</p>", parent.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("application/vnd.oasis.opendocument.flat.text",
                parent.get(Metadata.CONTENT_TYPE));

        //make sure metadata came through
        assertEquals(
                "LibreOffice/6.4.3.2$MacOSX_X86_64 " +
                        "LibreOffice_project/747b5d0ebf89f41c860ec2a39efd7cb15b54f2d8",
                parent.get("generator"));
        assertEquals(1, parent.getInt(PagedText.N_PAGES).intValue());

        Metadata macro = metadataList.get(1);
        assertEquals("MACRO", macro.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE_KEY));
        assertContains("If WsGQFM Or 2 Then", macro.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("test", macro.get(TikaCoreProperties.RESOURCE_NAME_KEY));

        Metadata image = metadataList.get(2);
        assertEquals("image/png", image.get(Metadata.CONTENT_TYPE));
    }


    @Test
    public void testMacroFODTandXMLOutput() throws Exception {
        String xml = getXML("testODTMacro.fodt", MACRO_PARSER).xml;
        assertContains("Hello dear user", xml);
        assertContains("If WsGQFM Or 2", xml);
    }

    @Test
    public void testMacroFODS() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testODSMacro.fods", MACRO_PARSER);
        assertEquals(3, metadataList.size());
        Metadata parent = metadataList.get(0);

        assertContains("<tr>", parent.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("application/vnd.oasis.opendocument.flat.spreadsheet",
                parent.get(Metadata.CONTENT_TYPE));

        Metadata macro = metadataList.get(1);
        assertEquals("MACRO", macro.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE_KEY));
        assertContains("If WsGQFM Or 2 Then", macro.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("test1", macro.get(TikaCoreProperties.RESOURCE_NAME_KEY));

        Metadata image = metadataList.get(2);
        assertEquals("image/png", image.get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testMacroFODP() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testODPMacro.fodp", MACRO_PARSER);
        assertEquals(2, metadataList.size());
        Metadata parent = metadataList.get(0);

        assertContains("<p", parent.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("application/vnd.oasis.opendocument.flat.presentation",
                parent.get(Metadata.CONTENT_TYPE));
        //make sure metadata came through
        assertEquals(
                "LibreOffice/6.4.3.2$MacOSX_X86_64 " +
                        "LibreOffice_project/747b5d0ebf89f41c860ec2a39efd7cb15b54f2d8",
                parent.get("generator"));

        assertEquals("3", parent.get("editing-cycles"));

        Metadata macro = metadataList.get(1);
        assertEquals("MACRO", macro.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE_KEY));
        assertContains("If WsGQFM Or 2 Then", macro.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("test", macro.get(TikaCoreProperties.RESOURCE_NAME_KEY));

    }

}
