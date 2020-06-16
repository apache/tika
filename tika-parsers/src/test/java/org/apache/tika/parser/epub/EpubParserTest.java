/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.epub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.util.List;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.junit.Test;

public class EpubParserTest extends TikaTest {

    @Test
    public void testXMLParser() throws Exception {

        XMLResult xmlResult = getXML("testEPUB.epub");

        assertEquals("application/epub+zip",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("en",
                xmlResult.metadata.get(TikaCoreProperties.LANGUAGE));
        assertEquals("This is an ePub test publication for Tika.",
                xmlResult.metadata.get(TikaCoreProperties.DESCRIPTION));
        assertEquals("Apache",
                xmlResult.metadata.get(TikaCoreProperties.PUBLISHER));

        String content = xmlResult.xml;
        assertContains("Plus a simple div", content);
        assertContains("First item", content);
        assertContains("The previous headings were <strong>subchapters</strong>", content);
        assertContains("Table data", content);
        assertContains("This is the text for chapter Two", content);

        //make sure style/script elements aren't extracted
        assertNotContained("nothing to see here", content);
        assertNotContained("nor here", content);
        assertNotContained("font-style", content);

        //make sure that there is only one of each
        assertContainsCount("<html", content, 1);
        assertContainsCount("<head", content, 1);
        assertContainsCount("<body", content, 1);
    }

    @Test
    public void testEpubOrder() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testEPUB.epub");

        //test attachments
        assertEquals(2, metadataList.size());
        assertEquals("image/jpeg", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        String xml = metadataList.get(0).get(RecursiveParserWrapperHandler.TIKA_CONTENT);
        int tocIndex = xml.indexOf("h3 class=\"toc_heading\">Table of Contents<");
        int ch1 = xml.indexOf("<h1>Chapter 1");
        int ch2 = xml.indexOf("<h1>Chapter 2");
        assert(tocIndex > -1 && ch1 > -1 && ch2 > -1);
        assert(tocIndex < ch1);
        assert(tocIndex < ch2);
        assert(ch1 < ch2);

        InputStream is = getClass().getResourceAsStream("/org/apache/tika/parser/epub/tika-config.xml");
        assertNotNull(is);
        Parser p = new AutoDetectParser(new TikaConfig(is));
        xml = getXML("testEPUB.epub", p).xml;
        tocIndex = xml.indexOf("h3 class=\"toc_heading\">Table of Contents<");
        ch1 = xml.indexOf("<h1>Chapter 1");
        ch2 = xml.indexOf("<h1>Chapter 2");
        assert(tocIndex > -1 && ch1 > -1 && ch2 > -1);
        assert(tocIndex > ch1);
        assert(tocIndex > ch2);
        assert(ch1 < ch2);
    }


    @Test
    public void testTruncated() throws Exception {
        Parser p = new EpubParser();
        List<Metadata> metadataList;
        try (InputStream is = truncate("testEPUB.epub", 10000)) {
            metadataList = getRecursiveMetadata(is, p, true);
        }
        String xml = metadataList.get(0).get(RecursiveParserWrapperHandler.TIKA_CONTENT);
        int ch1 = xml.indexOf("<h1>Chapter 1");
        int ch2 = xml.indexOf("<h1>Chapter 2");
        assert(ch1 < ch2);
    }

    @Test
    public void testContentsWXMLExtensions() throws Exception {
        //TIKA-2310
        List<Metadata> metadataList = getRecursiveMetadata("testEPUB_xml_ext.epub");
        assertEquals(1, metadataList.size());
        assertContains("It was a bright cold day in April",
                metadataList.get(0).get(RecursiveParserWrapperHandler.TIKA_CONTENT));
    }
}
