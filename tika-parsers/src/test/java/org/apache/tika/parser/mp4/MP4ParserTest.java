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
package org.apache.tika.parser.mp4;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing mp4 files.
 */
public class MP4ParserTest extends TestCase {
    /**
     * Test that we can extract information from
     *  a M4A MP4 Audio file
     */
    public void testMP4ParsingAudio() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = MP4ParserTest.class.getResourceAsStream(
                "/test-documents/testMP4.m4a");
        try {
            parser.parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        // Check core properties
        assertEquals("audio/mp4", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Test Title", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Test Artist", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Test Artist", metadata.get(Metadata.AUTHOR));
        assertEquals("2012-01-28T18:39:18Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2012-01-28T18:39:18Z", metadata.get(Metadata.CREATION_DATE));
        assertEquals("2012-01-28T18:40:25Z", metadata.get(TikaCoreProperties.MODIFIED));
        assertEquals("2012-01-28T18:40:25Z", metadata.get(Metadata.DATE));

        // Check the textual contents
        String content = handler.toString();
        assertTrue(content.contains("Test Title"));
        assertTrue(content.contains("Test Artist"));
        assertTrue(content.contains("Test Album"));
        assertTrue(content.contains("2008"));
        assertTrue(content.contains("Test Comment"));
        assertTrue(content.contains("Test Genre"));
        
        // Check XMPDM-typed audio properties
        assertEquals("Test Album", metadata.get(XMPDM.ALBUM));
        assertEquals("Test Artist", metadata.get(XMPDM.ARTIST));
        assertEquals("Test Composer", metadata.get(XMPDM.COMPOSER));
        assertEquals("2008", metadata.get(XMPDM.RELEASE_DATE));
        assertEquals("Test Genre", metadata.get(XMPDM.GENRE));
        assertEquals("Test Comments", metadata.get(XMPDM.LOG_COMMENT.getName()));
        assertEquals("1", metadata.get(XMPDM.TRACK_NUMBER));
        
        assertEquals("44100", metadata.get(XMPDM.AUDIO_SAMPLE_RATE));
        //assertEquals("Stereo", metadata.get(XMPDM.AUDIO_CHANNEL_TYPE)); // TODO Extract
        assertEquals("M4A", metadata.get(XMPDM.AUDIO_COMPRESSOR));
        
        
        // Check again by file, rather than stream
        TikaInputStream tstream = TikaInputStream.get(
              MP4ParserTest.class.getResourceAsStream("/test-documents/testMP4.m4a"));
        tstream.getFile();
        try {
           parser.parse(tstream, handler, metadata, new ParseContext());
        } finally {
           tstream.close();
        }
    }
    
    // TODO Test a MP4 Video file
    // TODO Test an old QuickTime Video File
}
