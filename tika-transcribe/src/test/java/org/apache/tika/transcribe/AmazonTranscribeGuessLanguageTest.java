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

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AmazonTranscribeGuessLanguageTest {
    AmazonTranscribe transcriber;

    @Before
    public void setUp() {
        transcriber = new AmazonTranscribe();
    }

    @Ignore
    @Test
    public void testSimpleTranscribeUnknownAudio_enUS() {
        String audioFilePath = "src/test/resources/en-US_(A_Little_Bottle_Of_Water).mp3";
        String expected = "a little bottle of water.";
        String jobName;
        String result;

        if (transcriber.isAvailable()) {
            try {
                jobName = transcriber.transcribe(audioFilePath);
                result = transcriber.getTranscriptResult(jobName);
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
    public void testSimpleTranscribeUnknownAudio_enGB() {
        String audioFilePath = "src/test/resources/en-GB_(A_Little_Bottle_Of_Water).mp3";
        String expected = "a little bottle of water.";
        String jobName;
        String result;

        if (transcriber.isAvailable()) {
            try {
                jobName = transcriber.transcribe(audioFilePath);
                result = transcriber.getTranscriptResult(jobName);
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
    public void testSimpleTranscribeUnknownVideo_enAU() {
        String videoFilePath = "src/test/resources/en-AU_(A_Little_Bottle_Of_Water).mp3";
        String expected = "a little bottle of water.";
        String jobName;
        String result;

        if (transcriber.isAvailable()) {
            try {
                jobName = transcriber.transcribe(videoFilePath);
                result = transcriber.getTranscriptResult(jobName);
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
    public void testSimpleTranscribeUnknownAudio_deDE() {
        String audioFilePath = "src/test/resources/de-DE_(We_Are_At_School_x2).mp3";
        String expected = "Wir sind in der Schule. Wir sind in der Schule.";
        String jobName;
        String result;

        if (transcriber.isAvailable()) {
            try {
                jobName = transcriber.transcribe(audioFilePath);
                result = transcriber.getTranscriptResult(jobName);
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
    public void testSimpleTranscribeUnknownAudio_itIT() {
        String audioFilePath = "src/test/resources/it-IT_(We_Are_Having_Class_x2).mp3";
        String expected = "stiamo facendo lezione. stiamo facendo lezione.";
        String jobName;
        String result;

        if (transcriber.isAvailable()) {
            try {
                jobName = transcriber.transcribe(audioFilePath);
                result = transcriber.getTranscriptResult(jobName);
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
    public void testSimpleTranscribeUnknownAudio_jaJP() {
        String audioFilePath = "src/test/resources/ja-JP_(We_Are_At_School).mp3";
        String expected = "私達は学校にいます"; //TODO or Watashitachi wa gakkō ni imasu
        String jobName;
        String result;

        if (transcriber.isAvailable()) {
            try {
                jobName = transcriber.transcribe(audioFilePath);
                result = transcriber.getTranscriptResult(jobName);
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
    public void testSimpleTranscribeUnknownAudio_koKR() {
        String audioFilePath = "src/test/resources/ko-KR_(We_Are_Having_Class_x2).mp3";
        String expected = "우리는 수업을하고있다"; //TODO or ulineun sueob-eulhagoissda
        String jobName;
        String result;

        if (transcriber.isAvailable()) {
            try {
                jobName = transcriber.transcribe(audioFilePath);
                result = transcriber.getTranscriptResult(jobName);
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
    public void testSimpleTranscribeUnknownAudio_ptBR(){
        String audioFilePath = "src/test/resources/pt-BR_(We_Are_At_School).mp3";
        String expected = "nós estamos na escola.";
        String jobName;
        String result;

        if (transcriber.isAvailable()) {
            try {
                jobName = transcriber.transcribe(audioFilePath);
                result = transcriber.getTranscriptResult(jobName);
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
    public void testSimpleTranscribeUnknownVideoShortKorean() {
        String videoFilePath = "src/test/resources/ShortVideoSampleKorean";
        String expected = "안녕하세요"; //TODO: Check whether output is Annyeonghaseyo or 안녕하세요
        String jobName;
        String result;

        if (transcriber.isAvailable()) {
            try {
                jobName = transcriber.transcribe(videoFilePath);
                result = transcriber.getTranscriptResult(jobName);
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