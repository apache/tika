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

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMP;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerFactory;
import org.junit.Test;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing mp4 files.
 */
public class MP4ParserTest extends TikaTest {
    /**
     * Test that we can extract information from
     *  a M4A MP4 Audio file
     */
    @Test
    public void testMP4ParsingAudio() throws Exception {
        Metadata metadata = new Metadata();
        String content = getText("testMP4.m4a", metadata);

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
        assertContains("Test Title", content);
        assertContains("Test Artist", content);
        assertContains("Test Album", content);
        assertContains("2008", content);
        assertContains("Test Comment", content);
        assertContains("Test Genre", content);
        
        // Check XMPDM-typed audio properties
        assertEquals("Test Album", metadata.get(XMPDM.ALBUM));
        assertEquals("Test Artist", metadata.get(XMPDM.ARTIST));
        assertEquals("Test Composer", metadata.get(XMPDM.COMPOSER));
        assertEquals("2008", metadata.get(XMPDM.RELEASE_DATE));
        assertEquals("Test Genre", metadata.get(XMPDM.GENRE));
        assertEquals("Test Comments", metadata.get(XMPDM.LOG_COMMENT.getName()));
        assertEquals("1", metadata.get(XMPDM.TRACK_NUMBER));
        assertEquals("Test Album Artist", metadata.get(XMPDM.ALBUM_ARTIST));
        assertEquals("6", metadata.get(XMPDM.DISC_NUMBER));
        assertEquals("0", metadata.get(XMPDM.COMPILATION));
        
        
        assertEquals("44100", metadata.get(XMPDM.AUDIO_SAMPLE_RATE));
        assertEquals("Stereo", metadata.get(XMPDM.AUDIO_CHANNEL_TYPE));
        assertEquals("M4A", metadata.get(XMPDM.AUDIO_COMPRESSOR));
        assertEquals("0.07", metadata.get(XMPDM.DURATION));
        
        assertEquals("iTunes 10.5.3.3", metadata.get(XMP.CREATOR_TOOL));
        
        
        // Check again by file, rather than stream
        TikaInputStream tstream = TikaInputStream.get(
              MP4ParserTest.class.getResourceAsStream("/test-documents/testMP4.m4a"));
        tstream.getFile();
        ContentHandler handler = new BodyContentHandler();
        try {
           AUTO_DETECT_PARSER.parse(tstream, handler, metadata, new ParseContext());
        } finally {
           tstream.close();
        }
        //TODO: why don't we check the output here?
    }
    
    // TODO Test a MP4 Video file
    // TODO Test an old QuickTime Video File
    @Test(timeout = 30000)
    public void testInfiniteLoop() throws Exception {
        //test that a truncated mp4 doesn't cause an infinite loop
        //TIKA-1931 and TIKA-1924
        XMLResult r = getXML("testMP4_truncated.m4a");
        assertEquals("audio/mp4", r.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("M4A", r.metadata.get(XMPDM.AUDIO_COMPRESSOR));
    }
}
