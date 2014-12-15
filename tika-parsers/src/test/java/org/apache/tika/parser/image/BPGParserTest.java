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
package org.apache.tika.parser.image;

import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Photoshop;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.junit.Test;
import org.xml.sax.helpers.DefaultHandler;

import static junit.framework.Assert.assertEquals;

public class BPGParserTest {
    private final Parser parser = new BPGParser();

    /**
     * Tests a very basic file, without much metadata
     */
    @Test
    public void testBPG() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/x-bpg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testBPG.bpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("10", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals("YCbCr Colour", metadata.get(Photoshop.COLOR_MODE));
    }

    /**
     * Tests a file with comments
     */
    @Test
    public void testBPG_Commented() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/x-bpg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testBPG_commented.bpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("103", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("77", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("10", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals("YCbCr Colour", metadata.get(Photoshop.COLOR_MODE));
        
        // TODO Check comment data
    }

    /**
     * Tests a file with geographic information in it
     */
    @Test
    public void testBPG_Geo() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/x-bpg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testBPG_GEO.bpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("68", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("10", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals("YCbCr Colour", metadata.get(Photoshop.COLOR_MODE));
        
        // TODO Check geographic data
    }
}
