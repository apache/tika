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
package org.apache.tika.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParserTest;

public class AutoDetectParserConfigTest extends TikaTest {

    @Test
    public void testConfiguringEmbeddedDocExtractor() throws Exception {

        TikaConfig tikaConfig = null;
        try (InputStream is = OOXMLParserTest.class.getResourceAsStream(
                "/configs/tika-config-no-names.xml")) {
            tikaConfig = new TikaConfig(is);
        }
        Parser p = new AutoDetectParser(tikaConfig);
        String xml = getXML("testEmbedded.zip", p).xml;
        assertNotContained("<h1>image3.jpg</h1>", xml);

        try (InputStream is = OOXMLParserTest.class.getResourceAsStream(
                "/configs/tika-config-with-names.xml")) {
            tikaConfig = new TikaConfig(is);
        }
        p = new AutoDetectParser(tikaConfig);
        xml = getXML("testPPT_EmbeddedPDF.pptx", p).xml;
        assertContains("<h1>image3.jpg</h1>", xml);
    }

    @Test
    public void testContentHandlerDecoratorFactory() throws Exception {
        TikaConfig tikaConfig = null;
        try (InputStream is = OOXMLParserTest.class.getResourceAsStream(
                "/configs/tika-config-upcasing-custom-handler-decorator.xml")) {
            tikaConfig = new TikaConfig(is);
        }
        Parser p = new AutoDetectParser(tikaConfig);
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_EmbeddedPDF.pptx", p);
        Metadata pdfMetadata1 = metadataList.get(4);
        assertContains("APACHE TIKA", pdfMetadata1.get(TikaCoreProperties.TIKA_CONTENT));
        Metadata pdfMetadata2 = metadataList.get(5);
        assertContains("HELLO WORLD", pdfMetadata2.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testRecursiveContentHandlerDecoratorFactory() throws Exception {
        TikaConfig tikaConfig = null;
        try (InputStream is = OOXMLParserTest.class.getResourceAsStream(
                "/configs/tika-config-doubling-custom-handler-decorator.xml")) {
            tikaConfig = new TikaConfig(is);
        }
        Parser p = new AutoDetectParser(tikaConfig);
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_EmbeddedPDF.pptx", p);
        assertContainsCount("IMAGE2.EMF",
                metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT), 2);
        assertContainsCount("15.9.2007 11:02",
                metadataList.get(4).get(TikaCoreProperties.TIKA_CONTENT), 2);
        assertContainsCount("HELLO WORLD",
                metadataList.get(5).get(TikaCoreProperties.TIKA_CONTENT), 4);
    }

    @Test
    public void testXMLContentHandlerDecoratorFactory() throws Exception {
        //test to make sure that the decorator is only applied once for
        //legacy (e.g. not RecursiveParserWrapperHandler) parsing
        TikaConfig tikaConfig = null;
        try (InputStream is = OOXMLParserTest.class.getResourceAsStream(
                "/configs/tika-config-doubling-custom-handler-decorator.xml")) {
            tikaConfig = new TikaConfig(is);
        }
        Parser p = new AutoDetectParser(tikaConfig);
        String txt = getXML("testPPT_EmbeddedPDF.pptx", p).xml;
        assertContainsCount("THE APACHE TIKA PROJECT WAS FORMALLY", txt, 2);
        assertContainsCount("15.9.2007 11:02", txt, 2);
    }

    @Test
    public void testDigests() throws Exception {
        //test to make sure that the decorator is only applied once for
        //legacy (e.g. not RecursiveParserWrapperHandler) parsing
        TikaConfig tikaConfig = null;
        try (InputStream is = AutoDetectParserConfigTest.class.getResourceAsStream(
                "/configs/tika-config-digests.xml")) {
            tikaConfig = new TikaConfig(is);
        }
        Parser p = new AutoDetectParser(tikaConfig);
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_EmbeddedPDF.pptx", p);
        assertEquals("SO67W5OGGMOFPMFQTHTNL5YU5EQXWPMNEPU7HKOZX2ULHRQICRZA====",
                metadataList.get(0).get("X-TIKA:digest:SHA256"));

        assertEquals("a16f14215ebbfa47bd995e799f03cb18",
                metadataList.get(0).get("X-TIKA:digest:MD5"));

        assertEquals("Q7D3RFV6DNGZ4BQIS6UKNWX4CDIKPIGDU2D7ADBUDVOBYSZHF7FQ====",
                metadataList.get(6).get("X-TIKA:digest:SHA256"));
        assertEquals("90a8b249a6d6b6cb127c59e01cef3aaa",
                metadataList.get(6).get("X-TIKA:digest:MD5"));
    }

    @Test
    public void testDigestsEmptyParser() throws Exception {
        //TIKA-3939 -- ensure that digesting happens even with EmptyParser
        TikaConfig tikaConfig = null;
        try (InputStream is = OOXMLParserTest.class.getResourceAsStream(
                "/configs/tika-config-digests-pdf-only.xml")) {
            tikaConfig = new TikaConfig(is);
        }
        Parser p = new AutoDetectParser(tikaConfig);
        List<Metadata> metadataList = getRecursiveMetadata("testPDF.pdf", p);
        assertEquals(1, metadataList.size());
        assertEquals("4ef0d3bdb12ba603f4caf7d2e2c6112e",
                metadataList.get(0).get("X-TIKA:digest:MD5"));
        assertEquals("org.apache.tika.parser.EmptyParser",
                metadataList.get(0).get("X-TIKA:Parsed-By"));
    }

    @Test
    public void testContainerZeroBytes() throws Exception {
        Path tmp = Files.createTempFile("tika-test", "");
        try {
            TikaConfig tikaConfig = null;
            try (InputStream is = AutoDetectParserConfigTest.class.getResourceAsStream(
                    "/configs/tika-config-digests.xml")) {
                tikaConfig = new TikaConfig(is);
            }
            Parser p = new AutoDetectParser(tikaConfig);
            List<Metadata> metadataList = getRecursiveMetadata(tmp, p, true);
            assertEquals("d41d8cd98f00b204e9800998ecf8427e",
                    metadataList.get(0).get("X-TIKA:digest:MD5"));
            assertEquals("0", metadataList.get(0).get(Metadata.CONTENT_LENGTH));
        } finally {
            Files.delete(tmp);
        }
    }
}
