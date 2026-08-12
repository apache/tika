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
package org.apache.tika.parser.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Audio;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;

public class AudioParserTest {

    // javax.sound SPI properties: Tika's own WAV/AIFF/AU fixtures never populate these (stock
    // JDK providers emit no properties()), so the stock-vs-residual routing is exercised
    // directly against a synthetic properties map instead of a real audio file. TIKA-4816.
    @Test
    public void testStockAndResidualSpiProperties() {
        AudioParser parser = new AudioParser();
        Metadata metadata = new Metadata();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("duration", 216048000L);
        properties.put("author", "Nikolai Lobachevsky");
        properties.put("bitrate", 128000);
        properties.put("vbr", Boolean.FALSE);
        properties.put("quality", 80);
        properties.put("x-vendor-extension", "custom-value");   // not stock -> residual audio:

        parser.addMetadata(metadata, properties);

        assertEquals("216048000", metadata.get(Audio.SPI_DURATION));
        assertEquals("Nikolai Lobachevsky", metadata.get(Audio.SPI_AUTHOR));
        assertEquals("128000", metadata.get(Audio.BITRATE));
        assertEquals("false", metadata.get(Audio.IS_VARIABLE_BITRATE));
        assertEquals("80", metadata.get(Audio.SPI_QUALITY));
        assertEquals("custom-value", metadata.get("audio:x-vendor-extension"));
        assertNull(metadata.get("duration"), "the unprefixed legacy key must not appear");
        assertNull(metadata.get("x-vendor-extension"), "the unprefixed legacy key must not appear");
    }

    @Test
    public void testWAV() throws Exception {
        String path = "/test-documents/testWAV.wav";
        Metadata metadata = new Metadata();
        String content =
                new Tika().parseToString(AudioParserTest.class.getResourceAsStream(path), metadata);

        assertEquals("audio/vnd.wave", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("44100", metadata.get(XMPDM.AUDIO_SAMPLE_RATE));
        assertEquals("2", metadata.get(Audio.CHANNELS));
        assertEquals("16", metadata.get(Audio.BITS_PER_SAMPLE));
        assertEquals("PCM_SIGNED", metadata.get(Audio.ENCODING));

        assertEquals("", content);
    }

    @Test
    public void testAIFF() throws Exception {
        String path = "/test-documents/testAIFF.aif";
        Metadata metadata = new Metadata();
        String content =
                new Tika().parseToString(AudioParserTest.class.getResourceAsStream(path), metadata);

        assertEquals("audio/x-aiff", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("44100", metadata.get(XMPDM.AUDIO_SAMPLE_RATE));
        assertEquals("2", metadata.get(Audio.CHANNELS));
        assertEquals("16", metadata.get(Audio.BITS_PER_SAMPLE));
        assertEquals("PCM_SIGNED", metadata.get(Audio.ENCODING));

        assertEquals("", content);
    }

    @Test
    public void testAU() throws Exception {
        String path = "/test-documents/testAU.au";
        Metadata metadata = new Metadata();
        String content =
                new Tika().parseToString(AudioParserTest.class.getResourceAsStream(path), metadata);

        assertEquals("audio/basic", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("44100", metadata.get(XMPDM.AUDIO_SAMPLE_RATE));
        assertEquals("2", metadata.get(Audio.CHANNELS));
        assertEquals("16", metadata.get(Audio.BITS_PER_SAMPLE));
        assertEquals("PCM_SIGNED", metadata.get(Audio.ENCODING));

        assertEquals("", content);
    }

}
