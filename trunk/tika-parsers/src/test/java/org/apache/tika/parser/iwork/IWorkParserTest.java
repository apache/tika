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
package org.apache.tika.parser.iwork;

import junit.framework.TestCase;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import java.io.InputStream;

/**
 * Tests if the IWork parser parses the content and metadata properly of the supported formats.
 */
public class IWorkParserTest extends TestCase {

    private IWorkPackageParser iWorkParser;
    private ParseContext parseContext;

    @Override
    protected void setUp() throws Exception {
        iWorkParser = new IWorkPackageParser();
        parseContext = new ParseContext();
        parseContext.set(Parser.class, new AutoDetectParser());
    }

    public void testParseKeynote() throws Exception {
        InputStream input = IWorkParserTest.class.getResourceAsStream("/test-documents/testKeynote.key");
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        iWorkParser.parse(input, handler, metadata, parseContext);

        assertEquals(6, metadata.size());
        assertEquals("application/vnd.apple.keynote", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("3", metadata.get(Metadata.SLIDE_COUNT));
        assertEquals("1024", metadata.get(KeynoteContentHandler.PRESENTATION_WIDTH));
        assertEquals("768", metadata.get(KeynoteContentHandler.PRESENTATION_HEIGHT));
        assertEquals("Tika user", metadata.get(Metadata.AUTHOR));
        assertEquals("Apache tika", metadata.get(Metadata.TITLE));

        String content = handler.toString();
        assertTrue(content.contains("A sample presentation"));
        assertTrue(content.contains("For the Apache Tika project"));
        assertTrue(content.contains("Slide 1"));
        assertTrue(content.contains("Some random text for the sake of testability."));
        assertTrue(content.contains("A nice comment"));
        assertTrue(content.contains("A nice note"));

        // test table data
        assertTrue(content.contains("Cell one"));
        assertTrue(content.contains("Cell two"));
        assertTrue(content.contains("Cell three"));
        assertTrue(content.contains("Cell four"));
        assertTrue(content.contains("Cell 5"));
        assertTrue(content.contains("Cell six"));
        assertTrue(content.contains("7"));
        assertTrue(content.contains("Cell eight"));
        assertTrue(content.contains("5/5/1985"));
    }

    public void testParsePages() throws Exception {
        InputStream input = IWorkParserTest.class.getResourceAsStream("/test-documents/testPages.pages");
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        iWorkParser.parse(input, handler, metadata, parseContext);

        assertEquals(51, metadata.size());
        assertEquals("application/vnd.apple.pages", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Tika user", metadata.get(Metadata.AUTHOR));
        assertEquals("Apache tika", metadata.get(Metadata.TITLE));
        assertEquals("2010-05-09T21:34:38+0200", metadata.get(Metadata.CREATION_DATE));
        assertEquals("2010-05-09T23:50:36+0200", metadata.get(Metadata.LAST_MODIFIED));
        assertEquals("en", metadata.get(Metadata.LANGUAGE));
        assertEquals("2", metadata.get(Metadata.PAGE_COUNT));

        String content = handler.toString();

        // text on page 1
        assertTrue(content.contains("Sample pages document"));
        assertTrue(content.contains("Some plain text to parse."));
        assertTrue(content.contains("Cell one"));
        assertTrue(content.contains("Cell two"));
        assertTrue(content.contains("Cell three"));
        assertTrue(content.contains("Cell four"));
        assertTrue(content.contains("Cell five"));
        assertTrue(content.contains("Cell six"));
        assertTrue(content.contains("Cell seven"));
        assertTrue(content.contains("Cell eight"));
        assertTrue(content.contains("Cell nine"));
        assertTrue(content.contains("Both Pages 1.x and Keynote 2.x")); // ...

        // text on page 2
        assertTrue(content.contains("A second page...."));
        assertTrue(content.contains("Extensible Markup Language")); // ...
    }

    public void testParseNumbers() throws Exception {
        InputStream input = IWorkParserTest.class.getResourceAsStream("/test-documents/testNumbers.numbers");
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();

        iWorkParser.parse(input, handler, metadata, parseContext);

        String content = handler.toString();
        assertEquals(9, metadata.size());
        assertEquals("2", metadata.get(Metadata.PAGE_COUNT));
        assertEquals("Tika User", metadata.get(Metadata.AUTHOR));
        assertEquals("Account checking", metadata.get(Metadata.TITLE));
        assertEquals("a comment", metadata.get(Metadata.COMMENT));

        assertTrue(content.contains("Category"));
        assertTrue(content.contains("Home"));
        assertTrue(content.contains("-226"));
        assertTrue(content.contains("-137.5"));
        assertTrue(content.contains("Checking Account: 300545668"));
        assertTrue(content.contains("4650"));
        assertTrue(content.contains("Credit Card"));
        assertTrue(content.contains("Groceries"));
        assertTrue(content.contains("-210"));
        assertTrue(content.contains("Food"));
        assertTrue(content.contains("Try adding your own account transactions to this table."));
    }

}
