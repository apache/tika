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

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.FileInputStream;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests tika-trancribe by creating an AmazonTranscribe() object.
 * 1) Tests that transcribe functions properly when it is given just a filepath.
 * 2) Both audio (mp3) and video (mp4) files are used in these tests.
 */
public class AmazonTranscribeGuessLanguageTest {
    AmazonTranscribe transcriber;

    @Before
    public void setUp() {
        transcriber = new AmazonTranscribe();
    }

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is en-US (English - United States)
     */
    @Test
    public void testSimpleTranscribeUnknownAudio_enUS() {
        String audioFilePath = "src/test/resources/en-US_(A_Little_Bottle_Of_Water).mp3";
        String expected = "a little bottle of water.";
        String result;

        if (transcriber.isAvailable()) {
            try {
                result = transcriber.transcribe(new FileInputStream(audioFilePath));
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

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is en-GB (English - Great Britain)
     */
    @Test
    public void testSimpleTranscribeUnknownAudio_enGB() {
        String audioFilePath = "src/test/resources/en-GB_(A_Little_Bottle_Of_Water).mp3";
        String expected = "a little bottle of water.";
        String result;

        if (transcriber.isAvailable()) {
            try {
                result = transcriber.transcribe(new FileInputStream(audioFilePath));
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

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is en-AU (English - Australian)
     */
    @Test
    public void testSimpleTranscribeUnknownVideo_enAU() {
        String videoFilePath = "src/test/resources/en-AU_(A_Little_Bottle_Of_Water).mp3";
        String expected = "a little bottle of water.";
        String result;

        if (transcriber.isAvailable()) {
            try {
                result = transcriber.transcribe(new FileInputStream(videoFilePath));
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

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is de-DE (German)
     */
    @Test
    public void testSimpleTranscribeUnknownAudio_deDE() {
        String audioFilePath = "src/test/resources/de-DE_(We_Are_At_School_x2).mp3";
        String expected = "Wir sind in der Schule. Wir sind in der Schule.";
        String result;

        if (transcriber.isAvailable()) {
            try {
                result = transcriber.transcribe(new FileInputStream(audioFilePath));
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

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is it-IT (Italian)
     */
    @Test
    public void testSimpleTranscribeUnknownAudio_itIT() {
        String audioFilePath = "src/test/resources/it-IT_(We_Are_Having_Class_x2).mp3";
        String expected = "stiamo facendo lezione. stiamo facendo lezione.";
        String result;

        if (transcriber.isAvailable()) {
            try {
                result = transcriber.transcribe(new FileInputStream(audioFilePath));
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

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is ja-JP (Japanese)
     */
    @Test
    public void testSimpleTranscribeUnknownAudio_jaJP() {
        String audioFilePath = "src/test/resources/ja-JP_(We_Are_At_School).mp3";
        String expected = "私達は学校にいます"; //TODO or Watashitachi wa gakkō ni imasu
        String result;

        if (transcriber.isAvailable()) {
            try {
                result = transcriber.transcribe(new FileInputStream(audioFilePath));
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

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is ko-KR (Korean)
     */
    @Test
    public void testSimpleTranscribeUnknownAudio_koKR() {
        String audioFilePath = "src/test/resources/ko-KR_(We_Are_Having_Class_x2).mp3";
        String expected = "우리는 수업을하고있다"; //TODO or ulineun sueob-eulhagoissda
        String result;

        if (transcriber.isAvailable()) {
            try {
                result = transcriber.transcribe(new FileInputStream(audioFilePath));
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

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is pt-BR (Portuguese - Brazil)
     */
    @Test
    public void testSimpleTranscribeUnknownAudio_ptBR() {
        String audioFilePath = "src/test/resources/pt-BR_(We_Are_At_School).mp3";
        String expected = "nós estamos na escola.";
        String result;

        if (transcriber.isAvailable()) {
            try {
                result = transcriber.transcribe(new FileInputStream(audioFilePath));
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

    /**
     * Tests transcribe with a video file without passing in the source language.
     * The source language of the file is ko-KR (Korean)
     */
    @Test
    public void testSimpleTranscribeUnknownVideo_koKR() {
        String videoFilePath = "src/test/resources/ShortVideoSampleKorean";
        String expected = "안녕하세요"; //TODO: Check whether output is Annyeonghaseyo or 안녕하세요
        String result;

        if (transcriber.isAvailable()) {
            try {
                result = transcriber.transcribe(new FileInputStream(videoFilePath));
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

    /**
     * Tests transcribe with a video file without passing in the source language.
     * The source language of the file is en-US (English - United States)
     */
    @Test
    public void testSimpleTranscribeUnknownVideo_enUS() {
        String videoFilePath = "src/test/resources/ShortVideoSampleEnglish";
        String expected = "hi";
        String result;

        if (transcriber.isAvailable()) {
            try {
                result = transcriber.transcribe(new FileInputStream(videoFilePath));
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