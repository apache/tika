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
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.helpers.DefaultHandler;

import junit.framework.TestCase;

import static junit.framework.Assert.assertEquals;

public class PSDParserTest extends TestCase {

    private final Parser parser = new PSDParser();

    /**
     * Tests a very basic file, without much metadata
     */
    public void testPSD() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/x-psd");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testPSD.psd");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("537", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("51", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("8", metadata.get(Metadata.BITS_PER_SAMPLE));
    }
    
    /**
     * Tests a very basic file, without much metadata,
     *  where some of the data lengths are padded to be even
     */
    public void testOddPSD() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/x-psd");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testPSD2.psd");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        assertEquals("69", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("70", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("8", metadata.get(Metadata.BITS_PER_SAMPLE));
    }
}
