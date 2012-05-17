/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import junit.framework.TestCase;

public class ParsingReaderTest extends TestCase {

    public void testPlainText() throws Exception {
        String data = "test content";
        InputStream stream = new ByteArrayInputStream(data.getBytes("UTF-8"));
        Reader reader = new ParsingReader(stream, "test.txt");
        assertEquals('t', reader.read());
        assertEquals('e', reader.read());
        assertEquals('s', reader.read());
        assertEquals('t', reader.read());
        assertEquals(' ', reader.read());
        assertEquals('c', reader.read());
        assertEquals('o', reader.read());
        assertEquals('n', reader.read());
        assertEquals('t', reader.read());
        assertEquals('e', reader.read());
        assertEquals('n', reader.read());
        assertEquals('t', reader.read());
        assertEquals('\n', reader.read());
        assertEquals(-1, reader.read());
        reader.close();
        assertEquals(-1, stream.read());
    }

    public void testXML() throws Exception {
        String data = "<p>test <span>content</span></p>";
        InputStream stream = new ByteArrayInputStream(data.getBytes("UTF-8"));
        Reader reader = new ParsingReader(stream, "test.xml");
        assertEquals('t', (char) reader.read());
        assertEquals('e', (char) reader.read());
        assertEquals('s', (char) reader.read());
        assertEquals('t', (char) reader.read());
        assertEquals(' ', (char) reader.read());
        assertEquals('c', (char) reader.read());
        assertEquals('o', (char) reader.read());
        assertEquals('n', (char) reader.read());
        assertEquals('t', (char) reader.read());
        assertEquals('e', (char) reader.read());
        assertEquals('n', (char) reader.read());
        assertEquals('t', (char) reader.read());
        assertEquals('\n', (char) reader.read());
        assertEquals(-1, reader.read());
        reader.close();
        assertEquals(-1, stream.read());
    }

    /**
     * Test case for TIKA-203
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-203">TIKA-203</a>
     */
    public void testMetadata() throws Exception {
        Metadata metadata = new Metadata();
        InputStream stream = ParsingReaderTest.class.getResourceAsStream(
                "/test-documents/testEXCEL.xls");
        Reader reader = new ParsingReader(
                new AutoDetectParser(), stream, metadata, new ParseContext());
        try {
            // Metadata should already be available
            assertEquals("Simple Excel document", metadata.get(TikaCoreProperties.TITLE));
            // Check that the internal buffering isn't broken
            assertEquals('F', (char) reader.read());
            assertEquals('e', (char) reader.read());
            assertEquals('u', (char) reader.read());
            assertEquals('i', (char) reader.read());
            assertEquals('l', (char) reader.read());
            assertEquals('1', (char) reader.read());
        } finally {
            reader.close();
        }
    }

}
