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
import org.apache.tika.parser.Parser;
import org.xml.sax.helpers.DefaultHandler;

import junit.framework.TestCase;

public class ImageParserTest extends TestCase {

    private final Parser parser = new ImageParser();

    public void testBMP() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/bmp");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testBMP.bmp");
        parser.parse(stream, new DefaultHandler(), metadata);

        assertEquals("75", metadata.get("height"));
        assertEquals("100", metadata.get("width"));
    }

    public void testGIF() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/gif");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testGIF.gif");
        parser.parse(stream, new DefaultHandler(), metadata);

        assertEquals("75", metadata.get("height"));
        assertEquals("100", metadata.get("width"));
    }

    public void testJPEG() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testJPEG.jpg");
        parser.parse(stream, new DefaultHandler(), metadata);

        assertEquals("75", metadata.get("height"));
        assertEquals("100", metadata.get("width"));
    }

    public void testPNG() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testPNG.png");
        parser.parse(stream, new DefaultHandler(), metadata);

        assertEquals("75", metadata.get("height"));
        assertEquals("100", metadata.get("width"));
    }

// TODO: Add TIFF support
//    public void testTIFF() throws Exception {
//        Metadata metadata = new Metadata();
//        metadata.set(Metadata.CONTENT_TYPE, "image/tiff");
//        InputStream stream =
//            getClass().getResourceAsStream("/test-documents/testTIFF.tif");
//        parser.parse(stream, new DefaultHandler(), metadata);
//
//        assertEquals("75", metadata.get("height"));
//        assertEquals("100", metadata.get("width"));
//    }

}
