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
package org.apache.tika.parser.xliff;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.junit.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class XLIFF12ParserTest extends TikaTest {

    @Test
    public void testXLIFF12() throws Exception {
        try (InputStream input = getResourceAsStream("/test-documents/testXLIFF12.xlf")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new XLIFF12Parser().parse(input, handler, metadata, new ParseContext());
            String content = handler.toString();
            assertContains("Hooray", content);
            assertEquals("2", metadata.get("file-count"));
            assertEquals("4", metadata.get("tu-count"));
            assertEquals("en", metadata.get("source-language"));
            assertEquals("fr", metadata.get("target-language"));
        }
    }

    @Test
    public void testXLIFF12ToXMLHandler() throws Exception {
        String xml = getXML("testXLIFF12.xlf").xml;
        assertContains("<p lang=\"en\">Another trans-unit</p>", xml);
        assertContains("<p lang=\"fr\">Un autre trans-unit</p>", xml);
    }

}
