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

package org.apache.tika.parser.stanfordNer;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

public class StanfordNerTest {
    private Parser stanfordNerParser = new StanfordNerParser();

    @Test
    public void testFunctions() throws UnsupportedEncodingException,
            IOException, SAXException, TikaException {
        String text = "Good afternoon Rajat Raina, how are you today? " +
                "Hi, I am Tom Brady. I go to school at Stanford University, which is located in California.";
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        InputStream s = new ByteArrayInputStream(text.getBytes(UTF_8));
        /* if it's not available no tests to run */
        if (!((StanfordNerParser) stanfordNerParser).isAvailable()) {
            fail("Parser Not Available");
            return;
        }
        stanfordNerParser.parse(s, new BodyContentHandler(), metadata, context);
        assertNotNull(metadata.get("ORGANIZATION"));
        assertNotNull(metadata.get("LOCATION"));
        assertNotNull(metadata.get("PERSON"));
        assertEquals("[Stanford University]", metadata.get("ORGANIZATION"));
        assertEquals("[California]", metadata.get("LOCATION"));
        assertEquals("[Rajat Raina, Tom Brady]", metadata.get("PERSON"));

    }

    @Test
    public void testNulls() throws UnsupportedEncodingException, IOException,
            SAXException, TikaException {
        String text = "";

        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        stanfordNerParser.parse(new ByteArrayInputStream(text.getBytes(UTF_8)),
                new BodyContentHandler(), metadata, context);
        assertNull(metadata.get("ORGANIZATION"));
        assertNull(metadata.get("LOCATION"));
        assertNull(metadata.get("PERSON"));

    }
}
