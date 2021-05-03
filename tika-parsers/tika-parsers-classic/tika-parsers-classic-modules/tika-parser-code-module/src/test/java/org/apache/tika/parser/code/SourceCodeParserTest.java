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
package org.apache.tika.parser.code;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Set;

import org.junit.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;

public class SourceCodeParserTest extends TikaTest {

    private SourceCodeParser sourceCodeParser = new SourceCodeParser();

    @Test
    public void testSupportTypes() throws Exception {
        Set<MediaType> supportedTypes = sourceCodeParser.getSupportedTypes(new ParseContext());
        assertTrue(supportedTypes.contains(new MediaType("text", "x-java-source")));
        assertTrue(supportedTypes.contains(new MediaType("text", "x-groovy")));
        assertTrue(supportedTypes.contains(new MediaType("text", "x-c++src")));

        assertFalse(sourceCodeParser.getSupportedTypes(new ParseContext())
                .contains(new MediaType("text", "html")));
    }

    @Test
    public void testHTMLRenderWithReturnLine() throws Exception {
        String htmlContent =
                getXML(getResourceAsStream("/test-documents/testJAVA.java"), sourceCodeParser,
                        createMetadata("text/x-java-source")).xml;

        assertTrue(htmlContent.indexOf("<html:html lang=\"en\" xml:lang=\"en\"") == 0);
        assertTrue(htmlContent.indexOf(
                "<html:span class=\"java_keyword\">public</span><html:span class=\"java_plain\">") >
                0);
        assertTrue(htmlContent.indexOf("<html:span class=\"java_keyword\">static</span>") > 0);
        assertTrue(htmlContent.indexOf("<html:br clear=\"none\" />") > 0);
    }

    @Test
    public void testTextRender() throws Exception {
        String textContent =
                getText(getResourceAsStream("/test-documents/testJAVA.java"), sourceCodeParser,
                        createMetadata("text/x-java-source"));

        assertTrue(textContent.length() > 0);
        assertTrue(textContent.indexOf("html") < 0);

        textContent =
                getText(new ByteArrayInputStream("public class HelloWorld {}".getBytes(UTF_8)),
                        sourceCodeParser, createMetadata("text/x-java-source"));
        assertTrue(textContent.length() > 0);
        assertTrue(textContent.indexOf("html") < 0);
    }

    @Test
    public void testLoC() throws Exception {
        Metadata metadata = createMetadata("text/x-groovy");
        getText(getResourceAsStream("/test-documents/testGROOVY.groovy"), sourceCodeParser,
                metadata);

        assertEquals(metadata.get("LoC"), "9");
    }

    @Test
    public void testAuthor() throws Exception {
        Metadata metadata = createMetadata("text/x-c++src");
        getText(getResourceAsStream("/test-documents/testCPP.cpp"), sourceCodeParser, metadata);

        assertEquals("Hong-Thai Nguyen", metadata.get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testReturnContentAsIsForTextHandler() throws Exception {
        String strContent =
                getXML(getResourceAsStream("/test-documents/testJAVA.java"), AUTO_DETECT_PARSER,
                        createMetadata("text/plain")).xml;

        assertTrue(strContent.indexOf("public class HelloWorld {") > 0);
    }

    @Test
    public void testNoMarkupInToTextHandler() throws Exception {
        ContentHandler contentHandler = new ToTextContentHandler();
        ParseContext parseContext = new ParseContext();
        try (TikaInputStream tis = TikaInputStream
                .get(getResourceAsStream("/test-documents/testJAVA.java"))) {
            AUTO_DETECT_PARSER
                    .parse(tis, contentHandler, createMetadata("text/x-java-source"), parseContext);
        }
        String strContent = contentHandler.toString();
        assertContains("public class HelloWorld {", strContent);
        assertNotContained("background-color", strContent);
    }


    private Metadata createMetadata(String mimeType) {
        Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, "testFile");
        metadata.add(Metadata.CONTENT_TYPE, mimeType);
        return metadata;
    }

}
