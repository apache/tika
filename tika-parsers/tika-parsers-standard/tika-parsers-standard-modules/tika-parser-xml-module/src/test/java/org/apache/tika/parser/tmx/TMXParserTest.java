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
package org.apache.tika.parser.tmx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Test Case for <code>TMXParser</code>.
 */
public class TMXParserTest extends TikaTest {

    @Test
    public void testTMX() throws Exception {
        try (InputStream input = getResourceAsStream("/test-documents/testTMX.tmx")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new TMXParser().parse(input, handler, metadata, new ParseContext());
            String content = handler.toString();
            assertContains("Hello world!", content);
            assertContains("Salut lume!", content);

            assertEquals("1", metadata.get("tu-count"));
            assertEquals("2", metadata.get("tuv-count"));
            assertEquals("en-us", metadata.get("source-language"));
            assertEquals("ro-ro", metadata.get("target-language"));
            assertEquals("apache-tika", metadata.get("creation-tool"));
        }
    }

    @Test
    public void testTMXToXMLHandler() throws Exception {
        String xml = getXML("testTMX.tmx").xml;
        assertContains("<p lang=\"en-us\">Hello world!</p>", xml);
        assertContains("<p lang=\"ro-ro\">Salut lume!</p>", xml);
    }

}
