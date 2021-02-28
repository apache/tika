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
package org.apache.tika.transcribe;

import org.apache.tika.transcribe.AmazonTranscribe;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

//TODO: Check whether the expected Strings are correct (does it include punctuation? case?)
//TODO: Consider testing longer audio and video file, is there any points doing that?
public class AmazonTranscribeSimpleTest {
    AmazonTranscribe transcribe;

    @Before
    public void setUp() {
        transcribe = new AmazonTranscribe();
    }

    @Ignore
    @Test
    public void testSimpleTranscribeAudioEnglishShort() {
        String source = "src/test/resources/ShortAudioSampleEnglish.mp3";
        String expected = "Where is the bus stop where is the bus stop";

        String result = null;
        if (transcribe.isAvailable()) {
            try {
                result = transcribe.startTranscribeAudio(source, "en-GB");
                assertNotNull(result);
                assertEquals("Result: [" + result
                                + "]: not equal to expected: [" + expected + "]",
                                expected, result);
            } catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }
    }

    @Ignore
    @Test
    public void testSimpleTranscribeAudioFrenchShort() {
        String source = "src/test/resources/ShortAudioSampleFrench.mp3";
        String expected = "Où est l’arrêt du bus où est l’arrêt du bus";

        String result = null;
        if (transcribe.isAvailable()) {
            try {
                result = transcribe.startTranscribeAudio(source, "fr-FR");
                assertNotNull(result);
                assertEquals("Result: [" + result
                                + "]: not equal to expected: [" + expected + "]",
                        expected, result);
            } catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }
    }

    @Ignore
    @Test
    public void testSimpleTranscribeVideoEnglishShort() {
        String source = "src/test/resources/ShortVideoSampleEnglish";
        String expected = "Hi";

        String result = null;
        if (transcribe.isAvailable()) {
            try {
                result = transcribe.startTranscribeVideo(source, "en");
                assertNotNull(result);
                assertEquals("Result: [" + result
                                + "]: not equal to expected: [" + expected + "]",
                        expected, result);
            } catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }
    }

    @Ignore
    @Test
    public void testSimpleTranscribeVideoKoreanShort() {
        String source = "src/test/resources/ShortVideoSampleKorean";
        //TODO: Check whether output is Annyeonghaseyo or 안녕하세요
        String expected = "안녕하세요";

        String result = null;
        if (transcribe.isAvailable()) {
            try {
                result = transcribe.startTranscribeVideo(source, "ko-KR");
                assertNotNull(result);
                assertEquals("Result: [" + result
                                + "]: not equal to expected: [" + expected + "]",
                        expected, result);
            } catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }
    }

}
