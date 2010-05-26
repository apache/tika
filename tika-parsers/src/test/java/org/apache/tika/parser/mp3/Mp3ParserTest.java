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
package org.apache.tika.parser.mp3;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing mp3 files.
 */
public class Mp3ParserTest extends TestCase {

    /**
     * Test that with only ID3v1 tags, we get some information out   
     */
    public void testMp3ParsingID3v1() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = Mp3ParserTest.class.getResourceAsStream(
                "/test-documents/testMP3id3v1.mp3");
        try {
            parser.parse(stream, handler, metadata);
        } finally {
            stream.close();
        }

        assertEquals("audio/mpeg", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Test Title", metadata.get(Metadata.TITLE));
        assertEquals("Test Artist", metadata.get(Metadata.AUTHOR));

        String content = handler.toString();
        assertTrue(content.contains("Test Title"));
        assertTrue(content.contains("Test Artist"));
        assertTrue(content.contains("Test Album"));
        assertTrue(content.contains("2008"));
        assertTrue(content.contains("Test Comment"));
        assertTrue(content.contains("Rock"));
        
        assertEquals("MPEG 3 Layer III Version 1", metadata.get("version"));
        assertEquals("44100", metadata.get("samplerate"));
        assertEquals("2", metadata.get("channels"));
    }

    /**
     * Test that with only ID3v2 tags, we get the full
     *  set of information out.
     */
    public void testMp3ParsingID3v2() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = Mp3ParserTest.class.getResourceAsStream(
                "/test-documents/testMP3id3v2.mp3");
        try {
            parser.parse(stream, handler, metadata);
        } finally {
            stream.close();
        }

        assertEquals("audio/mpeg", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Test Title", metadata.get(Metadata.TITLE));
        assertEquals("Test Artist", metadata.get(Metadata.AUTHOR));

        String content = handler.toString();
        assertTrue(content.contains("Test Title"));
        assertTrue(content.contains("Test Artist"));
        assertTrue(content.contains("Test Album"));
        assertTrue(content.contains("2008"));
        assertTrue(content.contains("Test Comment"));
        assertTrue(content.contains("Rock"));
        
        assertEquals("MPEG 3 Layer III Version 1", metadata.get("version"));
        assertEquals("44100", metadata.get("samplerate"));
        assertEquals("2", metadata.get("channels"));
    }

    /**
     * Test that with both id3v2 and id3v1, we prefer the
     *  details from id3v2
     */
    public void testMp3ParsingID3v1v2() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = Mp3ParserTest.class.getResourceAsStream(
                "/test-documents/testMP3id3v1_v2.mp3");
        try {
            parser.parse(stream, handler, metadata);
        } finally {
            stream.close();
        }

        assertEquals("audio/mpeg", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Test Title", metadata.get(Metadata.TITLE));
        assertEquals("Test Artist", metadata.get(Metadata.AUTHOR));

        String content = handler.toString();
        assertTrue(content.contains("Test Title"));
        assertTrue(content.contains("Test Artist"));
        assertTrue(content.contains("Test Album"));
        assertTrue(content.contains("2008"));
        assertTrue(content.contains("Test Comment"));
        assertTrue(content.contains("Rock"));
        
        assertEquals("MPEG 3 Layer III Version 1", metadata.get("version"));
        assertEquals("44100", metadata.get("samplerate"));
        assertEquals("2", metadata.get("channels"));
    }

    public void testID3v2Frame() throws Exception {
       byte[] empty = new byte[] {
             0x49, 0x44, 0x33, 3, 1, 0,
             0, 0, 0, 0
       };
       
       assertEquals(11, ID3v2Frame.getInt(new byte[] {0,0,0,0x0b}));
       assertEquals(257, ID3v2Frame.getInt(new byte[] {0,0,1,1}));
       
       ID3v2Frame f = (ID3v2Frame)
            ID3v2Frame.createFrameIfPresent(new ByteArrayInputStream(empty));
       assertEquals(3, f.getMajorVersion());
       assertEquals(1, f.getMinorVersion());
       assertEquals(0, f.getFlags());
       assertEquals(0, f.getLength());
       assertEquals(0, f.getData().length);
       
       assertEquals("", ID3v2Frame.getTagString(f.getData(), 0, 0));
       assertEquals("", ID3v2Frame.getTagString(new byte[] {0,0,0,0}, 0, 3));
       assertEquals("A", ID3v2Frame.getTagString(new byte[] {(byte)'A',0,0,0}, 0, 3));
    }
}
