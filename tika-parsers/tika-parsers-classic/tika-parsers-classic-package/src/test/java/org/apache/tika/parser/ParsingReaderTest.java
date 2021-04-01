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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;

import org.junit.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class ParsingReaderTest extends TikaTest {

    @Test
    public void testPlainText() throws Exception {
        String data = "test content";
        InputStream stream = new ByteArrayInputStream(data.getBytes(UTF_8));
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

    @Test
    public void testXML() throws Exception {
        String data = "<p>test <span>content</span></p>";
        InputStream stream = new ByteArrayInputStream(data.getBytes(UTF_8));
        Reader reader = new ParsingReader(stream, "test.xml");
        assertEquals(' ', (char) reader.read());
        assertEquals('t', (char) reader.read());
        assertEquals('e', (char) reader.read());
        assertEquals('s', (char) reader.read());
        assertEquals('t', (char) reader.read());
        assertEquals(' ', (char) reader.read());
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
    @Test
    public void testMetadata() throws Exception {
        Metadata metadata = new Metadata();
        InputStream stream = getResourceAsStream("/test-documents/testEXCEL.xls");
        try (Reader reader = new ParsingReader(AUTO_DETECT_PARSER, stream, metadata,
                new ParseContext())) {
            // Metadata should already be available
            assertEquals("Simple Excel document", metadata.get(TikaCoreProperties.TITLE));
            // Check that the internal buffering isn't broken
            assertEquals('F', (char) reader.read());
            assertEquals('e', (char) reader.read());
            assertEquals('u', (char) reader.read());
            assertEquals('i', (char) reader.read());
            assertEquals('l', (char) reader.read());
            assertEquals('1', (char) reader.read());
        }
    }

    @Test
    public void testZeroByte() throws Exception {
        InputStream is = new ByteArrayInputStream(new byte[0]);
        ParsingReader r = new ParsingReader(is);
        assertEquals(-1, r.read());
    }
}
