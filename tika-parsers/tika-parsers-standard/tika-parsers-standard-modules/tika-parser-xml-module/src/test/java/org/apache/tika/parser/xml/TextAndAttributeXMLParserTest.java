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
package org.apache.tika.parser.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class TextAndAttributeXMLParserTest extends TikaTest {

    @Test
    public void testParseTextAndAttributes() throws IOException, TikaException, SAXException {
        try (InputStream input = getResourceAsStream("/test-documents/testXML2.xml")) {
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ContentHandler handler = new BodyContentHandler(buffer);
            new TextAndAttributeXMLParser().parse(input, handler, metadata, context);
            String output = buffer.toString("UTF-8");

            assertEquals("application/xml", metadata.get(Metadata.CONTENT_TYPE));
            assertTrue(output.contains("document type Microsoft Word 2003/2004"));
            assertTrue(output.contains("doc_property type title Title test"));
            assertTrue(output.contains("doc_property type subject Subject test"));
        }
    }
}
