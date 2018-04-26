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
package org.apache.tika.parser.sas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

public class SAS7BDATParserTest extends TikaTest {
    private Parser parser = new SAS7BDATParser();
    
    @Test
    public void testSimpleFile() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = SAS7BDATParserTest.class.getResourceAsStream(
                "/test-documents/testSAS.sas7bdat")) {
            parser.parse(stream, handler, metadata, new ParseContext());
        }

        assertEquals("application/x-sas-data", metadata.get(Metadata.CONTENT_TYPE));
        // TODO Test the metadata
        // TODO Test the contents
    }
    
    @Test
    public void testMultiColumns() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = SAS7BDATParserTest.class.getResourceAsStream(
                "/test-documents/test-columnar.sas7bdat")) {
            parser.parse(stream, handler, metadata, new ParseContext());
        }

        assertEquals("application/x-sas-data", metadata.get(Metadata.CONTENT_TYPE));
        // TODO Test the metadata
        // TODO Test the contents
    }

    // TODO HTML contents unit test
}
