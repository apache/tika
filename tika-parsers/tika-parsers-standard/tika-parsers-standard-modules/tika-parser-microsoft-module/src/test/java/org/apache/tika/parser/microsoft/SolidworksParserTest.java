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
package org.apache.tika.parser.microsoft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class SolidworksParserTest extends TikaTest {

    /**
     * Test the parsing of an solidWorks part in version 2013SP2
     */
    @Test
    public void testPart2013SP2Parser() throws Exception {
        try (InputStream input = getResourceAsStream(
                "/test-documents/testsolidworksPart2013SP2.SLDPRT")) {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new OfficeParser().parse(input, handler, metadata, new ParseContext());

            //Check content type
            assertEquals("application/sldworks", metadata.get(Metadata.CONTENT_TYPE));

            //Check properties
            assertEquals("2012-04-18T10:27:29Z", metadata.get(TikaCoreProperties.CREATED));
            assertEquals(null, metadata.get(TikaCoreProperties.CONTRIBUTOR));
            assertEquals("2013-09-06T08:12:12Z", metadata.get(TikaCoreProperties.MODIFIED));
            assertEquals("solidworks-dcom_dev", metadata.get(TikaCoreProperties.MODIFIER));
            assertEquals(null, metadata.get(TikaCoreProperties.RELATION));
            assertEquals(null, metadata.get(TikaCoreProperties.RIGHTS));
            assertEquals(null, metadata.get(TikaCoreProperties.SOURCE));
            assertEquals("", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("", metadata.get(TikaCoreProperties.SUBJECT));
        }
    }

    /**
     * Test the parsing of an solidWorks part in version 2014SP0
     */
    @Test
    public void testPart2014SP0Parser() throws Exception {
        try (InputStream input = getResourceAsStream(
                "/test-documents/testsolidworksPart2014SP0.SLDPRT")) {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new OfficeParser().parse(input, handler, metadata, new ParseContext());

            //Check content type
            assertEquals("application/sldworks", metadata.get(Metadata.CONTENT_TYPE));

            //Check properties
            assertEquals("2012-04-18T10:27:29Z", metadata.get(TikaCoreProperties.CREATED));
            assertEquals(null, metadata.get(TikaCoreProperties.CONTRIBUTOR));
            assertEquals("2013-11-28T12:38:28Z", metadata.get(TikaCoreProperties.MODIFIED));
            assertEquals("solidworks-dcom_dev", metadata.get(TikaCoreProperties.MODIFIER));
            assertEquals(null, metadata.get(TikaCoreProperties.RELATION));
            assertEquals(null, metadata.get(TikaCoreProperties.RIGHTS));
            assertEquals(null, metadata.get(TikaCoreProperties.SOURCE));
            assertEquals("", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("", metadata.get(TikaCoreProperties.SUBJECT));
        }
    }

    /**
     * Test the parsing of an solidWorks assembly in version 2013SP2
     */
    @Test
    public void testAssembly2013SP2Parser() throws Exception {
        try (InputStream input = getResourceAsStream(
                "/test-documents/testsolidworksAssembly2013SP2.SLDASM")) {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new OfficeParser().parse(input, handler, metadata, new ParseContext());

            //Check content type
            assertEquals("application/sldworks", metadata.get(Metadata.CONTENT_TYPE));

            //Check properties
            assertEquals("2012-04-25T09:51:38Z", metadata.get(TikaCoreProperties.CREATED));
            assertEquals(null, metadata.get(TikaCoreProperties.CONTRIBUTOR));
            assertEquals("2013-09-06T08:11:08Z", metadata.get(TikaCoreProperties.MODIFIED));
            assertEquals("solidworks-dcom_dev", metadata.get(TikaCoreProperties.MODIFIER));
            assertEquals(null, metadata.get(TikaCoreProperties.RELATION));
            assertEquals(null, metadata.get(TikaCoreProperties.RIGHTS));
            assertEquals(null, metadata.get(TikaCoreProperties.SOURCE));
            assertEquals("", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("", metadata.get(TikaCoreProperties.SUBJECT));
        }
    }

    /**
     * Test the parsing of an solidWorks assembly in version 2014SP0
     */
    @Test
    public void testAssembly2014SP0Parser() throws Exception {
        try (InputStream input = getResourceAsStream(
                "/test-documents/testsolidworksAssembly2014SP0.SLDASM")) {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new OfficeParser().parse(input, handler, metadata, new ParseContext());

            //Check content type
            assertEquals("application/sldworks", metadata.get(Metadata.CONTENT_TYPE));

            //Check properties
            assertEquals("2012-04-25T09:51:38Z", metadata.get(TikaCoreProperties.CREATED));
            assertEquals(null, metadata.get(TikaCoreProperties.CONTRIBUTOR));
            assertEquals("2013-11-28T12:41:49Z", metadata.get(TikaCoreProperties.MODIFIED));
            assertEquals("solidworks-dcom_dev", metadata.get(TikaCoreProperties.MODIFIER));
            assertEquals(null, metadata.get(TikaCoreProperties.RELATION));
            assertEquals(null, metadata.get(TikaCoreProperties.RIGHTS));
            assertEquals(null, metadata.get(TikaCoreProperties.SOURCE));
            assertEquals("", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("", metadata.get(TikaCoreProperties.SUBJECT));
        }
    }

    /*
     * Test the parsing of an solidWorks drawing in version 2013SP2
     */
    @Test
    public void testDrawing2013SP2Parser() throws Exception {
        try (InputStream input = getResourceAsStream(
                "/test-documents/testsolidworksDrawing2013SP2.SLDDRW")) {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new OfficeParser().parse(input, handler, metadata, new ParseContext());

            //Check content type
            assertEquals("application/sldworks", metadata.get(Metadata.CONTENT_TYPE));

            //Check properties
            assertEquals("2012-07-03T12:05:29Z", metadata.get(TikaCoreProperties.CREATED));
            assertEquals(null, metadata.get(TikaCoreProperties.CONTRIBUTOR));
            assertEquals("2013-09-06T08:06:57Z", metadata.get(TikaCoreProperties.MODIFIED));
            assertEquals("solidworks-dcom_dev", metadata.get(TikaCoreProperties.MODIFIER));
            assertEquals(null, metadata.get(TikaCoreProperties.RELATION));
            assertEquals(null, metadata.get(TikaCoreProperties.RIGHTS));
            assertEquals(null, metadata.get(TikaCoreProperties.SOURCE));
            assertEquals("", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("", metadata.get(TikaCoreProperties.SUBJECT));
        }
    }

    /**
     * Test the parsing of an solidWorks drawing in version 2014SP0
     */
    @Test
    public void testDrawing2014SP0Parser() throws Exception {
        try (InputStream input = getResourceAsStream(
                "/test-documents/testsolidworksDrawing2014SP0.SLDDRW")) {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new OfficeParser().parse(input, handler, metadata, new ParseContext());

            //Check content type
            assertEquals("application/sldworks", metadata.get(Metadata.CONTENT_TYPE));

            //Check properties
            assertEquals("2012-07-03T12:05:29Z", metadata.get(TikaCoreProperties.CREATED));
            assertEquals(null, metadata.get(TikaCoreProperties.CONTRIBUTOR));
            assertEquals("2013-11-28T12:41:49Z", metadata.get(TikaCoreProperties.MODIFIED));
            assertEquals("solidworks-dcom_dev", metadata.get(TikaCoreProperties.MODIFIER));
            assertEquals(null, metadata.get(TikaCoreProperties.RELATION));
            assertEquals(null, metadata.get(TikaCoreProperties.RIGHTS));
            assertEquals(null, metadata.get(TikaCoreProperties.SOURCE));
            assertEquals("", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("", metadata.get(TikaCoreProperties.SUBJECT));
        }
    }
}
