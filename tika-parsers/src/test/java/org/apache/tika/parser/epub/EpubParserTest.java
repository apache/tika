/**
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
package org.apache.tika.parser.epub;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class EpubParserTest extends TestCase {

    public void testXMLParser() throws Exception {
        InputStream input = EpubParserTest.class.getResourceAsStream(
                "/test-documents/testEPUB.epub");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new EpubParser().parse(input, handler, metadata, new ParseContext());

            assertEquals("application/epub+zip",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("en",
                    metadata.get(TikaCoreProperties.LANGUAGE));
            assertEquals("This is an ePub test publication for Tika.",
                    metadata.get(TikaCoreProperties.DESCRIPTION));
            assertEquals("Apache",
                    metadata.get(TikaCoreProperties.PUBLISHER));

            String content = handler.toString();
            assertTrue(content.contains("Plus a simple div"));
            assertTrue(content.contains("First item"));
            assertTrue(content.contains("The previous headings were subchapters"));
            assertTrue(content.contains("Table data"));
        } finally {
            input.close();
        }
    }

}
