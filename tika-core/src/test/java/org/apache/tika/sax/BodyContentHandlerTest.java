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
package org.apache.tika.sax;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.mock.MockParser;

/**
 * Test cases for the {@link BodyContentHandler} class.
 */
public class BodyContentHandlerTest extends TikaTest {

    /**
     * Test that the conversion to an {@link OutputStream} doesn't leave
     * characters unflushed in an internal buffer.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-179">TIKA-179</a>
     */
    @Test
    public void testOutputStream() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        XHTMLContentHandler xhtml =
                new XHTMLContentHandler(new BodyContentHandler(
                        new OutputStreamWriter(buffer, UTF_8)),
                        new Metadata());
        xhtml.startDocument();
        xhtml.element("p", "Test text");
        xhtml.endDocument();

        assertEquals("Test text\n", buffer.toString(UTF_8.name()));
    }

    @Test
    public void testLimit() throws Exception {
        //TIKA-2668 - java 11-ea
        Parser p = new MockParser();
        WriteOutContentHandler handler = new WriteOutContentHandler(15);
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        Parser[] parsers = new Parser[1];
        parsers[0] = p;
        Parser autoDetectParser = new AutoDetectParser(parsers);
        try (InputStream is = getResourceAsStream("/test-documents/example.xml")) {
            autoDetectParser.parse(is, handler, metadata, parseContext);
        } catch (Exception e) {
            tryToFindIllegalStateException(e);
        }
        assertEquals("hello wo", handler.toString().trim());
    }

    private void tryToFindIllegalStateException(Throwable e) throws Exception {
        if (e instanceof IllegalStateException) {
            throw (Exception) e;
        }
        if (e.getCause() != null) {
            tryToFindIllegalStateException(e.getCause());
        }
    }
}
