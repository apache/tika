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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.gagravarr.vorbis.VorbisInfo;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Audio;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.ParseContext;

/**
 * Tests the bitrate mapping from the Vorbis identification header, end to end
 * against a real file and at unit level with directly constructed info
 * objects for the header combinations the real fixture does not cover.
 */
public class VorbisParserTest extends TikaTest {

    @Test
    public void testRealFile() throws Exception {
        //a real quality-mode encoding: nominal 80000, no upper/lower bounds
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/testVORBIS.ogg"))) {
            new VorbisParser().parse(tis, new DefaultHandler(), metadata,
                    new ParseContext());
        }
        assertEquals("80000", metadata.get(Audio.BITRATE));
        assertEquals("true", metadata.get(Audio.IS_VARIABLE_BITRATE));
        assertEquals("44100", metadata.get(XMPDM.AUDIO_SAMPLE_RATE));
    }

    private static Metadata extractInfo(int upper, int nominal, int lower) throws Exception {
        VorbisInfo info = new VorbisInfo();
        info.setRate(44100);
        info.setBitrateUpper(upper);
        info.setBitrateNominal(nominal);
        info.setBitrateLower(lower);

        Metadata metadata = new Metadata();
        new VorbisParser().extractInfo(metadata, info);
        return metadata;
    }

    @Test
    public void testNominalOnlyIsVariable() throws Exception {
        //the common quality-mode encoding declares only a nominal rate
        Metadata metadata = extractInfo(0, 192000, 0);
        assertEquals("192000", metadata.get(Audio.BITRATE));
        assertEquals("true", metadata.get(Audio.IS_VARIABLE_BITRATE));
    }

    @Test
    public void testAllRatesEqualIsFixed() throws Exception {
        Metadata metadata = extractInfo(192000, 192000, 192000);
        assertEquals("192000", metadata.get(Audio.BITRATE));
        assertEquals("false", metadata.get(Audio.IS_VARIABLE_BITRATE));
    }

    @Test
    public void testZeroWidthBracketIsFixed() throws Exception {
        //managed-mode encoding without a nominal rate: an upper and lower
        //bound that agree still pin the stream to one exact rate
        Metadata metadata = extractInfo(128000, 0, 128000);
        assertEquals("128000", metadata.get(Audio.BITRATE));
        assertEquals("false", metadata.get(Audio.IS_VARIABLE_BITRATE));
    }

    @Test
    public void testDivergentBracketIsVariable() throws Exception {
        //a real bracket without a nominal rate: variable, no single bitrate
        Metadata metadata = extractInfo(256000, 0, 96000);
        assertNull(metadata.get(Audio.BITRATE));
        assertEquals("true", metadata.get(Audio.IS_VARIABLE_BITRATE));
    }

    @Test
    public void testNoDeclaredRates() throws Exception {
        Metadata metadata = extractInfo(0, 0, 0);
        assertNull(metadata.get(Audio.BITRATE));
        assertNull(metadata.get(Audio.IS_VARIABLE_BITRATE));
    }
}
