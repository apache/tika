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
package org.apache.tika.parser.ibooks;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.epub.EpubParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class iBooksParserTest extends TestCase {

    public void testiBooksParser() throws Exception {
        InputStream input = iBooksParserTest.class.getResourceAsStream(
                "/test-documents/testiBooks.ibooks");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new EpubParser().parse(input, handler, metadata, new ParseContext());

            assertEquals("application/x-ibooks+zip",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("en-GB",
                    metadata.get(TikaCoreProperties.LANGUAGE));
            assertEquals("iBooks Author v1.0",
                    metadata.get(TikaCoreProperties.CONTRIBUTOR));
            assertEquals("Apache",
                    metadata.get(TikaCoreProperties.CREATOR));

            /* TODO For some reason, the xhtml files in iBooks-style ePub are not parsed properly, and the content comes back empty.git che
            String content = handler.toString();
            System.out.println("content="+content);
            assertTrue(content.contains("Plus a simple div"));
            assertTrue(content.contains("First item"));
            assertTrue(content.contains("The previous headings were subchapters"));
            assertTrue(content.contains("Table data"));
            assertTrue(content.contains("Lorem ipsum dolor rutur amet"));
            */
        } finally {
            input.close();
        }
    }

}
