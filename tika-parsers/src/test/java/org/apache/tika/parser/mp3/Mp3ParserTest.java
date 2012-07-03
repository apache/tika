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
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
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
            parser.parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        assertEquals("audio/mpeg", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Test Title", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Test Artist", metadata.get(TikaCoreProperties.CREATOR));
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
            parser.parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        // Check core properties
        assertEquals("audio/mpeg", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Test Title", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Test Artist", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Test Artist", metadata.get(Metadata.AUTHOR));

        // Check the textual contents
        String content = handler.toString();
        assertTrue(content.contains("Test Title"));
        assertTrue(content.contains("Test Artist"));
        assertTrue(content.contains("Test Album"));
        assertTrue(content.contains("2008"));
        assertTrue(content.contains("Test Comment"));
        assertTrue(content.contains("Rock"));
        
        // Check un-typed audio properties
        assertEquals("MPEG 3 Layer III Version 1", metadata.get("version"));
        assertEquals("44100", metadata.get("samplerate"));
        assertEquals("2", metadata.get("channels"));
        
        // Check XMPDM-typed audio properties
        assertEquals("Test Album", metadata.get(XMPDM.ALBUM));
        assertEquals("Test Artist", metadata.get(XMPDM.ARTIST));
        assertEquals(null, metadata.get(XMPDM.COMPOSER));
        assertEquals("2008", metadata.get(XMPDM.RELEASE_DATE));
        assertEquals("Rock", metadata.get(XMPDM.GENRE));
        assertEquals("XXX - ID3v1 Comment\nTest Comment", metadata.get(XMPDM.LOG_COMMENT.getName()));
        assertEquals("1", metadata.get(XMPDM.TRACK_NUMBER));
        
        assertEquals("44100", metadata.get(XMPDM.AUDIO_SAMPLE_RATE));
        assertEquals("Stereo", metadata.get(XMPDM.AUDIO_CHANNEL_TYPE));
        assertEquals("MP3", metadata.get(XMPDM.AUDIO_COMPRESSOR));
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
            parser.parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        assertEquals("audio/mpeg", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Test Title", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Test Artist", metadata.get(TikaCoreProperties.CREATOR));
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
     * Test that with only ID3v2 tags, of version 2.4, we get the full
     *  set of information out.
     */
    public void testMp3ParsingID3v24() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = Mp3ParserTest.class.getResourceAsStream(
                "/test-documents/testMP3id3v24.mp3");
        try {
            parser.parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        assertEquals("audio/mpeg", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Test Title", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Test Artist", metadata.get(TikaCoreProperties.CREATOR));
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
     * Tests that a file with characters not in the ISO 8859-1
     *  range is correctly handled
     */
    public void testMp3ParsingID3i18n() throws Exception {
       Parser parser = new AutoDetectParser(); // Should auto-detect!
       ContentHandler handler = new BodyContentHandler();
       Metadata metadata = new Metadata();

       InputStream stream = Mp3ParserTest.class.getResourceAsStream(
               "/test-documents/testMP3i18n.mp3");
       try {
           parser.parse(stream, handler, metadata, new ParseContext());
       } finally {
           stream.close();
       }

       assertEquals("audio/mpeg", metadata.get(Metadata.CONTENT_TYPE));
       assertEquals("Une chason en Fran\u00e7ais", metadata.get(TikaCoreProperties.TITLE));
       assertEquals("Test Artist \u2468\u2460", metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("Test Artist \u2468\u2460", metadata.get(Metadata.AUTHOR));
       assertEquals("Test Artist \u2468\u2460", metadata.get(XMPDM.ARTIST));
       assertEquals("Test Album \u2460\u2468", metadata.get(XMPDM.ALBUM));

       assertEquals(
             "Eng - Comment Desc\nThis is a \u1357\u2468\u2460 Comment", 
             metadata.get(XMPDM.LOG_COMMENT)
       );
       
       assertEquals("MPEG 3 Layer III Version 1", metadata.get("version"));
       assertEquals("44100", metadata.get("samplerate"));
       assertEquals("2", metadata.get("channels"));
   }
    
    
    /**
     * Tests that a file with both lyrics and
     *  ID3v2 tags gets both extracted correctly
     */
    public void testMp3ParsingLyrics() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        // Note - our test file has a lyrics tag, but lacks any
        //  lyrics in the tags, so we can't test that bit
        // TODO Find a better sample file
        
        InputStream stream = Mp3ParserTest.class.getResourceAsStream(
                "/test-documents/testMP3lyrics.mp3");
        try {
            parser.parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        assertEquals("audio/mpeg", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Test Title", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Test Artist", metadata.get(TikaCoreProperties.CREATOR));
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
    
    /**
     * This test will do nothing, unless you've downloaded the
     *  mp3 file from TIKA-424 - the file cannot be
     *  distributed with Tika.
     * This test will check for the complicated set of ID3v2.4
     *  tags.
     */
    public void testTIKA424() throws Exception {
       Parser parser = new AutoDetectParser(); // Should auto-detect!
       ContentHandler handler = new BodyContentHandler();
       Metadata metadata = new Metadata();

       InputStream stream = Mp3ParserTest.class.getResourceAsStream(
               "/test-documents/test2.mp3");
       if(stream == null) {
          // You haven't downloaded the file
          // Skip the test
          return;
       }
       
       try {
           parser.parse(stream, handler, metadata, new ParseContext());
       } finally {
           stream.close();
       }

       assertEquals("audio/mpeg", metadata.get(Metadata.CONTENT_TYPE));
       assertEquals("Plus loin vers l'ouest", metadata.get(TikaCoreProperties.TITLE));
       assertEquals("Merzhin", metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("Merzhin", metadata.get(Metadata.AUTHOR));

       String content = handler.toString();
       assertTrue(content.contains("Plus loin vers l'ouest"));
       
       assertEquals("MPEG 3 Layer III Version 1", metadata.get("version"));
       assertEquals("44100", metadata.get("samplerate"));
       assertEquals("2", metadata.get("channels"));
    }
    
    /**
     * This tests that we can handle without errors (but perhaps not
     *  all content) a file with a very very large ID3 frame that
     *  has been truncated before the end of the ID3 tags.
     * In this case, it is a file with JPEG data in the ID3, which
     *  is trunacted before the end of the JPEG bit of the ID3 frame.
     */
    public void testTIKA474() throws Exception {
       Parser parser = new AutoDetectParser(); // Should auto-detect!
       ContentHandler handler = new BodyContentHandler();
       Metadata metadata = new Metadata();

       InputStream stream = Mp3ParserTest.class.getResourceAsStream(
               "/test-documents/testMP3truncated.mp3");
       
       
       try {
           parser.parse(stream, handler, metadata, new ParseContext());
       } finally {
           stream.close();
       }

       // Check we coud get the headers from the start
       assertEquals("audio/mpeg", metadata.get(Metadata.CONTENT_TYPE));
       assertEquals("Girl you have no faith in medicine", metadata.get(TikaCoreProperties.TITLE));
       assertEquals("The White Stripes", metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("The White Stripes", metadata.get(Metadata.AUTHOR));

       String content = handler.toString();
       assertTrue(content.contains("Girl you have no faith in medicine"));
       assertTrue(content.contains("The White Stripes"));
       assertTrue(content.contains("Elephant"));
       assertTrue(content.contains("2003"));
       
       // File lacks any audio frames, so we can't know these
       assertEquals(null, metadata.get("version"));
       assertEquals(null, metadata.get("samplerate"));
       assertEquals(null, metadata.get("channels"));
    }
}
