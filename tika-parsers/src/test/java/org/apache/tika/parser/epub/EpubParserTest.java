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

import static org.junit.Assert.assertEquals;
import static org.apache.tika.TikaTest.assertContains;

import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class EpubParserTest {

    @Test
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
            assertContains("Plus a simple div", content);
            assertContains("First item", content);
            assertContains("The previous headings were subchapters", content);
            assertContains("Table data", content);
        } finally {
            input.close();
        }
    }

}
