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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaLoaderHelper;
import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class AutoDetectParserConfigTest extends TikaTest {

    @Test
    public void testConfiguringEmbeddedDocExtractor() throws Exception {

        Parser p = TikaLoaderHelper.getLoader("tika-config-no-names.json").loadAutoDetectParser();
        String xml = getXML("testEmbedded.zip", p).xml;
        assertNotContained("<h1>image3.jpg</h1>", xml);

        p = TikaLoaderHelper.getLoader("tika-config-with-names.json").loadAutoDetectParser();
        xml = getXML("testPPT_EmbeddedPDF.pptx", p).xml;
        assertContains("<h1>image3.jpg</h1>", xml);
    }

    @Test
    public void testContentHandlerDecoratorFactory() throws Exception {
        Parser p = TikaLoaderHelper.getLoader("tika-config-upcasing-custom-handler-decorator.json").loadAutoDetectParser();
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_EmbeddedPDF.pptx", p);
        Metadata pdfMetadata1 = metadataList.get(4);
        assertContains("APACHE TIKA", pdfMetadata1.get(TikaCoreProperties.TIKA_CONTENT));
        Metadata pdfMetadata2 = metadataList.get(5);
        assertContains("HELLO WORLD", pdfMetadata2.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testRecursiveContentHandlerDecoratorFactory() throws Exception {
        Parser p = TikaLoaderHelper.getLoader("tika-config-doubling-custom-handler-decorator.json").loadAutoDetectParser();
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

        Parser p = TikaLoaderHelper.getLoader("tika-config-doubling-custom-handler-decorator.json").loadAutoDetectParser();
        String txt = getXML("testPPT_EmbeddedPDF.pptx", p).xml;
        assertContainsCount("THE APACHE TIKA PROJECT WAS FORMALLY", txt, 2);
        assertContainsCount("15.9.2007 11:02", txt, 2);
    }

    @Test
    public void testWriteFilter() throws Exception {
        TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-write-filter.json");
        Parser p = loader.loadAutoDetectParser();
        ParseContext parseContext = loader.loadParseContext();
        Metadata metadata = Metadata.newInstance(parseContext);
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_EmbeddedPDF.pptx", p,
                metadata, parseContext, true);
        for (Metadata m : metadataList) {
            for (String k : m.names()) {
                assertTrue(k.startsWith("X-TIKA:") || k.startsWith("access_permission:")
                        || k.equals("Content-Type") || k.equals("dc:creator"),
                        "unexpected key: " + k);
            }
        }
    }

    @Test
    public void testDigests() throws Exception {
        //test to make sure that the decorator is only applied once for
        //legacy (e.g. not RecursiveParserWrapperHandler) parsing
        TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-digests.json");
        Parser p = loader.loadAutoDetectParser();
        ParseContext context = loader.loadParseContext();
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_EmbeddedPDF.pptx", p, context);
        // SHA256 with BASE32 encoding includes encoding in the key
        assertEquals("SO67W5OGGMOFPMFQTHTNL5YU5EQXWPMNEPU7HKOZX2ULHRQICRZA====",
                metadataList.get(0).get("X-TIKA:digest:SHA256:BASE32"));

        assertEquals("a16f14215ebbfa47bd995e799f03cb18",
                metadataList.get(0).get("X-TIKA:digest:MD5"));

        assertEquals("Q7D3RFV6DNGZ4BQIS6UKNWX4CDIKPIGDU2D7ADBUDVOBYSZHF7FQ====",
                metadataList.get(6).get("X-TIKA:digest:SHA256:BASE32"));
        assertEquals("90a8b249a6d6b6cb127c59e01cef3aaa",
                metadataList.get(6).get("X-TIKA:digest:MD5"));
    }

    @Test
    public void testDigestsSkipContainer() throws Exception {
        //test to make sure that the decorator is only applied once for
        //legacy (e.g. not RecursiveParserWrapperHandler) parsing
        TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-digests-skip-container.json");
        Parser p = loader.loadAutoDetectParser();
        ParseContext context = loader.loadParseContext();
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_EmbeddedPDF.pptx", p, context);
        // SHA256 with BASE32 encoding includes encoding in the key
        assertNull(metadataList.get(0).get("X-TIKA:digest:SHA256:BASE32"));
        assertNull(metadataList.get(0).get("X-TIKA:digest:MD5"));

        assertEquals("Q7D3RFV6DNGZ4BQIS6UKNWX4CDIKPIGDU2D7ADBUDVOBYSZHF7FQ====",
                metadataList.get(6).get("X-TIKA:digest:SHA256:BASE32"));
        assertEquals("90a8b249a6d6b6cb127c59e01cef3aaa",
                metadataList.get(6).get("X-TIKA:digest:MD5"));
    }

    @Test
    public void testDigestsEmptyParser() throws Exception {
        //TIKA-3939 -- ensure that digesting happens even with EmptyParser
        TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-digests-pdf-only.json");
        Parser p = loader.loadAutoDetectParser();
        ParseContext context = loader.loadParseContext();
        List<Metadata> metadataList = getRecursiveMetadata("testPDF.pdf", p, context);
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
            TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-digests.json");
            Parser p = loader.loadAutoDetectParser();
            ParseContext context = loader.loadParseContext();
            List<Metadata> metadataList = getRecursiveMetadata(tmp, p, context, true);
            assertEquals("d41d8cd98f00b204e9800998ecf8427e",
                    metadataList.get(0).get("X-TIKA:digest:MD5"));
            assertEquals("0", metadataList.get(0).get(Metadata.CONTENT_LENGTH));
        } finally {
            Files.delete(tmp);
        }
    }
}
