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

import junit.framework.TestCase;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;

public class AudioParserTest extends TestCase {

    public void testWAV() throws Exception {
        String path = "/test-documents/testWAV.wav";
        Metadata metadata = new Metadata();
        String content = new Tika().parseToString(
                AudioParserTest.class.getResourceAsStream(path), metadata);

        assertEquals("audio/x-wav", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("44100.0", metadata.get("samplerate"));
        assertEquals("2", metadata.get("channels"));
        assertEquals("16", metadata.get("bits"));
        assertEquals("PCM_SIGNED", metadata.get("encoding"));

        assertEquals("", content);
    }

    public void testAIFF() throws Exception {
        String path = "/test-documents/testAIFF.aif";
        Metadata metadata = new Metadata();
        String content = new Tika().parseToString(
                AudioParserTest.class.getResourceAsStream(path), metadata);

        assertEquals("audio/x-aiff", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("44100.0", metadata.get("samplerate"));
        assertEquals("2", metadata.get("channels"));
        assertEquals("16", metadata.get("bits"));
        assertEquals("PCM_SIGNED", metadata.get("encoding"));

        assertEquals("", content);
    }

    public void testAU() throws Exception {
        String path = "/test-documents/testAU.au";
        Metadata metadata = new Metadata();
        String content = new Tika().parseToString(
                AudioParserTest.class.getResourceAsStream(path), metadata);

        assertEquals("audio/basic", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("44100.0", metadata.get("samplerate"));
        assertEquals("2", metadata.get("channels"));
        assertEquals("16", metadata.get("bits"));
        assertEquals("PCM_SIGNED", metadata.get("encoding"));

        assertEquals("", content);
    }

}
