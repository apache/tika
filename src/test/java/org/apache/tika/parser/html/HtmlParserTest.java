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
package org.apache.tika.parser.html;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import junit.framework.TestCase;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.WriteOutContentHandler;
import org.xml.sax.SAXException;

public class HtmlParserTest extends TestCase {

    private Parser parser = new HtmlParser();

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(name);
    }

    public void testParseAscii() throws IOException, SAXException,
            TikaException {

        StringWriter writer = new StringWriter();
        Metadata metadata = new Metadata();

        parser.parse(getStream("test-documents/testHTML.html"),
                new WriteOutContentHandler(writer), metadata);
        String content = writer.toString();

        assertTrue("Did not contain expected text:"
                + "Title : Test Indexation Html", content
                .contains("Title : Test Indexation Html"));

        assertTrue("Did not contain expected text:" + "Test Indexation Html",
                content.contains("Test Indexation Html"));

        assertTrue("Did not contain expected text:" + "Indexation du fichier",
                content.contains("Indexation du fichier"));

    }

    public void XtestParseUTF8() throws IOException, SAXException, TikaException {

        StringWriter writer = new StringWriter();
        Metadata metadata = new Metadata();

        parser.parse(getStream("test-documents/testHTML_utf8.html"),
                new WriteOutContentHandler(writer), metadata);
        String content = writer.toString();

        assertTrue("Did not contain expected text:"
                + "Title : Tilte with UTF-8 chars öäå", content
                .contains("Title : Tilte with UTF-8 chars öäå"));

        assertTrue("Did not contain expected text:"
                + "Content with UTF-8 chars", content
                .contains("Content with UTF-8 chars"));

        assertTrue("Did not contain expected text:" + "åäö", content
                .contains("åäö"));

    }

    public void testParseEmpty() throws Exception {
        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        parser.parse(new ByteArrayInputStream(new byte[0]),
                new WriteOutContentHandler(writer), metadata);
        String content = writer.toString();
        assertEquals("", content);
    }

}
