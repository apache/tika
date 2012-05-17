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
package org.apache.tika.parser.feed;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class FeedParserTest extends TestCase {

    public void testXMLParser() throws Exception {
        InputStream input = FeedParserTest.class
                .getResourceAsStream("/test-documents/rsstest.rss");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();

            new FeedParser().parse(input, handler, metadata, context);

            String content = handler.toString();
            assertFalse(content == null);

            assertEquals("Sample RSS File for Junit test",
                    metadata.get(TikaCoreProperties.DESCRIPTION));
            assertEquals("TestChannel", metadata.get(TikaCoreProperties.TITLE));

            // TODO find a way of testing the paragraphs and anchors

        } finally {
            input.close();
        }
    }

}
