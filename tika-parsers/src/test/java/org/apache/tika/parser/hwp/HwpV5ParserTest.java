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
package org.apache.tika.parser.hwp;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class HwpV5ParserTest extends TikaTest {

	@Test
    public void testHwpV5Parser() throws Exception {

        try (InputStream input = HwpV5ParserTest.class.getResourceAsStream(
                "/test-documents/test-documents-v5.hwp")) {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new HwpV5Parser().parse(input, handler, metadata, new ParseContext());

            assertEquals(
                    "application/x-hwp-v5",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Apache Tika", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("SooMyung Lee", metadata.get(TikaCoreProperties.CREATOR));
            
            assertContains("Apache Tika", handler.toString());
        }
    }
	
	@Test
    public void testAutoDetectParser() throws Exception {
	    AutoDetectParser parser = new AutoDetectParser();
	    BodyContentHandler handler = new BodyContentHandler();
	    Metadata metadata = new Metadata();
	    try (InputStream stream = HwpV5ParserTest.class.getResourceAsStream("/test-documents/test-documents-v5.hwp")) {
	        parser.parse(stream, handler, metadata);
	        
	        assertContains("Apache Tika", handler.toString());
	        
           assertEquals(
                    "application/x-hwp-v5",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Apache Tika", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("SooMyung Lee", metadata.get(TikaCoreProperties.CREATOR));
	    }

    }
	
	@Test
    public void testDistributedHwp() throws Exception {
	    AutoDetectParser parser = new AutoDetectParser();
	    BodyContentHandler handler = new BodyContentHandler();
	    Metadata metadata = new Metadata();
	    try (InputStream stream = HwpV5ParserTest.class.getResourceAsStream("/test-documents/test-documents-v5-dist.hwp")) {
	        parser.parse(stream, handler, metadata);
	        
	        assertContains("Apache Tika", handler.toString());
	        
           assertEquals(
                    "application/x-hwp-v5",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Apache Tika", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("SooMyung Lee", metadata.get(TikaCoreProperties.CREATOR));
	    }

    }
}
