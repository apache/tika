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

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.xml.sax.helpers.DefaultHandler;

public class AudioParserTest extends TestCase {

    private final Parser parser = new AudioParser();

    public void testWAV() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "audio/x-wav");
        InputStream stream = getClass().getResourceAsStream(
                "/test-documents/testWAV.wav");

        parser.parse(stream, new DefaultHandler(), metadata);

        assertEquals("44100", metadata.get("samplerate"));
        assertEquals("2", metadata.get("channels"));
        assertEquals("16", metadata.get("bits"));
        assertEquals("PCM_SIGNED", metadata.get("encoding"));

    }

    public void testAIFF() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "audio/x-aiff");
        InputStream stream = getClass().getResourceAsStream(
                "/test-documents/testAIFF.aif");

        parser.parse(stream, new DefaultHandler(), metadata);

        assertEquals("44100", metadata.get("samplerate"));
        assertEquals("2", metadata.get("channels"));
        assertEquals("16", metadata.get("bits"));
        assertEquals("PCM_SIGNED", metadata.get("encoding"));

    }

    public void testAU() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "audio/basic");
        InputStream stream = getClass().getResourceAsStream(
                "/test-documents/testAU.au");

        parser.parse(stream, new DefaultHandler(), metadata);

        assertEquals("44100", metadata.get("samplerate"));
        assertEquals("2", metadata.get("channels"));
        assertEquals("16", metadata.get("bits"));
        assertEquals("PCM_SIGNED", metadata.get("encoding"));

    }

}
