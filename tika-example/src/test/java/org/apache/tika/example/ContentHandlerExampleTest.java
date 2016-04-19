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

package org.apache.tika.example;

import org.apache.tika.exception.TikaException;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

import static org.apache.tika.TikaTest.assertContains;
import static org.apache.tika.TikaTest.assertNotContained;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContentHandlerExampleTest {
    ContentHandlerExample example;
    
    @Before
    public void setUp() {
        example = new ContentHandlerExample();
    }

    @Test
    public void testParseToPlainText() throws IOException, SAXException, TikaException {
        String result = example.parseToPlainText().trim();
        assertEquals("Expected 'test', but got '" + result + "'", "test", result);
    }

    @Test
    public void testParseToHTML() throws IOException, SAXException, TikaException {
        String result = example.parseToHTML().trim();
        
        assertContains("<html", result);
        assertContains("<head>", result);
        assertContains("<meta name=\"dc:creator\"", result);
        assertContains("<title>", result);
        assertContains("<body>", result);
        assertContains(">test", result);
    }

    @Test
    public void testParseBodyToHTML() throws IOException, SAXException, TikaException {
        String result = example.parseBodyToHTML().trim();
        
        assertNotContained("<html", result);
        assertNotContained("<head>", result);
        assertNotContained("<meta name=\"dc:creator\"", result);
        assertNotContained("<title>", result);
        assertNotContained("<body>", result);
        assertContains(">test", result);
    }

    @Test
    public void testParseOnePartToHTML() throws IOException, SAXException, TikaException {
        String result = example.parseOnePartToHTML().trim();
        
        assertNotContained("<html", result);
        assertNotContained("<head>", result);
        assertNotContained("<meta name=\"dc:creator\"", result);
        assertNotContained("<title>", result);
        assertNotContained("<body>", result);
        assertContains("<p class=\"header\"", result);
        assertContains("This is in the header", result);
        assertNotContained("<h1>Test Document", result);
        assertNotContained("<p>1 2 3", result);
    }


    @Test
    public void testParseToPlainTextChunks() throws IOException, SAXException, TikaException {
        List<String> result = example.parseToPlainTextChunks();
        
        assertEquals(3, result.size());
        for (String chunk : result) {
            assertTrue("Chunk under max size", chunk.length() <= example.MAXIMUM_TEXT_CHUNK_SIZE);
        }

        assertContains("This is in the header", result.get(0));
        assertContains("Test Document", result.get(0));
        
        assertContains("Testing", result.get(1));
        assertContains("1 2 3", result.get(1));
        assertContains("TestTable", result.get(1));
        
        assertContains("Testing 123", result.get(2));
    }
}
