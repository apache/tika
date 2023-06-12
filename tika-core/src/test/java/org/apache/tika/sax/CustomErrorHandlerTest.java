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
package org.apache.tika.sax;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * Test that validates a custom {@link ContentHandlerDecorator} can handle errors during XML parsing
 *
 * @see <a href="https://issues.apache.org/jira/browse/TIKA-4062">TIKA-4062</a>
 */
public class CustomErrorHandlerTest extends TikaTest {

    private static String DEFAULT_SAX_PARSER_FACTORY;
    private static String SAX_PARSER_FACTORY_KEY = "javax.xml.parsers.SAXParserFactory";
    @BeforeAll
    public static void setUp() throws TikaException {
        DEFAULT_SAX_PARSER_FACTORY = System.getProperty(SAX_PARSER_FACTORY_KEY);
        System.setProperty(SAX_PARSER_FACTORY_KEY,
                "org.apache.tika.sax.ErrorResistentSAXParserFactory");
        //forces re-initialization
        XMLReaderUtils.setPoolSize(10);
    }

    @AfterAll
    public static void tearDown() throws TikaException {
        if (DEFAULT_SAX_PARSER_FACTORY == null) {
            System.clearProperty(SAX_PARSER_FACTORY_KEY);
        } else {
            System.setProperty(SAX_PARSER_FACTORY_KEY, DEFAULT_SAX_PARSER_FACTORY);
        }
        //forces re-initialization
        XMLReaderUtils.setPoolSize(10);
    }
    private void extractXml(InputStream blobStream, OutputStream textStream)
            throws IOException, SAXException, TikaException, ParserConfigurationException {

        try {
            ToXMLContentHandler contentHandler =
                    new ToXMLContentHandler(textStream, StandardCharsets.UTF_8.toString());
            NonValidatingContentHandler handler = new NonValidatingContentHandler(contentHandler);
            XMLReaderUtils.parseSAX(blobStream, handler, new ParseContext());
        } finally {
            textStream.close();
        }
    }

    private String extractTestData(String name)
            throws IOException, SAXException, TikaException, ParserConfigurationException {
        try (InputStream is = getResourceAsStream("/test-documents/" + name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            extractXml(is, out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    @Test
    void testUndeclaredEntityXML() throws Exception {
        try {
            String content = extractTestData("undeclared_entity.xml");
            assertContains("START", content);
            //This assertion passes only if custom error handler is called to handle fatal exception
            assertContains("END", content);
        } catch (SAXException e) {
            fail("Exception resturned from parser and not handled in error handler " + e);
        }
    }
}
