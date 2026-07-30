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
package org.apache.tika.parser.ogg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.gagravarr.vorbis.VorbisComments;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Audio;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Tests the mapping of Vorbis-style comments shared by all Ogg family parsers
 * (Vorbis, Opus, Speex, FLAC). The comments are fed into
 * {@link OggAudioParser#extractComments} directly; building a valid Ogg
 * container around them would exercise the container library rather than the
 * mapping under test.
 */
public class OggAudioParserTest {

    @Test
    public void testCopyrightCommentIsMapped() throws Exception {
        Metadata metadata = extractComments("copyright", "Test Copyright");

        assertEquals("Test Copyright", metadata.get(XMPDM.COPYRIGHT));
        //like vendor, the raw comment stays available under the vorbis: name
        assertEquals("Test Copyright", metadata.get("vorbis:copyright"));
    }

    @Test
    public void testAdditionalCopyrightCommentsAreKept() throws Exception {
        Metadata metadata = extractComments("copyright", "Test Copyright",
                "copyright", "Second Copyright");

        //xmpDM:copyright is single-valued: the first comment wins, while the
        //vorbis: passthrough keeps all values
        assertEquals("Test Copyright", metadata.get(XMPDM.COPYRIGHT));
        assertArrayEquals(new String[]{"Test Copyright", "Second Copyright"},
                metadata.getValues("vorbis:copyright"));
    }

    @Test
    public void testTrackAndDiscTotals() throws Exception {
        Metadata metadata = extractComments("tracknumber", "3/12",
                "discnumber", "1", "disctotal", "2");

        assertEquals("3", metadata.get(XMPDM.TRACK_NUMBER));
        assertEquals("12", metadata.get(Audio.TRACK_COUNT));
        assertEquals("3/12", metadata.get(Audio.RAW_TRACK_NUMBER));
        assertEquals("1", metadata.get(XMPDM.DISC_NUMBER));
        assertEquals("2", metadata.get(Audio.DISC_COUNT));
    }

    @Test
    public void testDegenerateTrackValues() throws Exception {
        Metadata metadata = extractComments("tracknumber", "3/of twelve",
                "discnumber", "/2");

        //non-numeric parts stay out of the typed properties but survive raw
        assertEquals("3", metadata.get(XMPDM.TRACK_NUMBER));
        assertNull(metadata.get(Audio.TRACK_COUNT));
        assertEquals("3/of twelve", metadata.get(Audio.RAW_TRACK_NUMBER));
        assertNull(metadata.get(XMPDM.DISC_NUMBER));
        assertEquals("2", metadata.get(Audio.DISC_COUNT));
        assertEquals("/2", metadata.get(Audio.RAW_DISC_NUMBER));
    }

    @Test
    public void testExplicitTotalWinsOverCombinedForm() throws Exception {
        Metadata metadata = extractComments("tracknumber", "3/12",
                "totaltracks", "14");

        assertEquals("3", metadata.get(XMPDM.TRACK_NUMBER));
        assertEquals("14", metadata.get(Audio.TRACK_COUNT));
    }

    /**
     * Runs the given key/value comment pairs through the shared comment
     * extraction and returns the resulting metadata.
     */
    private static Metadata extractComments(String... keysAndValues) throws Exception {
        VorbisComments comments = new VorbisComments();
        comments.addComment("title", "Test Title");
        comments.addComment("artist", "Test Artist");
        comments.addComment("album", "Test Album");
        for (int i = 0; i < keysAndValues.length; i += 2) {
            comments.addComment(keysAndValues[i], keysAndValues[i + 1]);
        }

        Metadata metadata = new Metadata();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(new DefaultHandler(), metadata);
        xhtml.startDocument();
        OggAudioParser.extractComments(metadata, xhtml, comments);
        xhtml.endDocument();
        return metadata;
    }
}
