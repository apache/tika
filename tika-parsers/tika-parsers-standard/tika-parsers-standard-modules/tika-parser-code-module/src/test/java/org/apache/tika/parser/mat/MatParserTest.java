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
package org.apache.tika.parser.mat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToXMLContentHandler;

/**
 * Test cases to exercise the {@link MatParser}.
 */
public class MatParserTest extends TikaTest {

    @Test
    public void testParser() throws Exception {

        XMLResult xmlResult = getXML("breidamerkurjokull_radar_profiles_2009.mat");
        Metadata metadata = xmlResult.metadata;
        String content = xmlResult.xml;
        // Check Metadata
        assertEquals("PCWIN64", metadata.get("platform"));
        assertEquals("MATLAB 5.0 MAT-file", metadata.get("fileType"));
        assertEquals("IM", metadata.get("endian"));
        assertEquals("Thu Feb 21 15:52:49 2013", metadata.get("createdOn"));

        assertContains("<li>[1x909  double array]</li>", content);
        assertContains("<p>c1:[1x1  struct array]</p>", content);
        assertContains("<li>[1024x1  double array]</li>", content);
        assertContains("<p>b1:[1x1  struct array]</p>", content);
        assertContains("<p>a1:[1x1  struct array]</p>", content);
        assertContains("<li>[1024x1261  double array]</li>", content);
        assertContains("<li>[1x1  double array]</li>", content);
        assertContains("</body></html>", content);
    }

    @Test
    public void testParserForText() throws Exception {
        Parser parser = new MatParser();
        ToXMLContentHandler handler = new ToXMLContentHandler();
        Metadata metadata = new Metadata();
        String path = "/test-documents/test_mat_text.mat";

        try (InputStream stream = getResourceAsStream(path)) {
            parser.parse(stream, handler, metadata, new ParseContext());
        }

        // Check Content
        String content = handler.toString();
        assertContains("<p>double:[2x2  double array]</p>", content);
    }
}
