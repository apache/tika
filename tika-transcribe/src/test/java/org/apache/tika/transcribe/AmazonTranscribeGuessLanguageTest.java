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
    public void testSimpleTranscribeUnknownAudioShortEnglish() {
        String audioFilePath = "src/test/resources/ShortAudioSampleEnglish.mp3";
        String expected = "Where is the bus stop where is the bus stop";
        String jobName;
        String result;

        if (transcriber.isAvailable()) {
            try {
                jobName = transcriber.startTranscribeAudio(audioFilePath);
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
    public void testSimpleTranscribeUnknownAudioShortFrench() {
        String audioFilePath = "src/test/resources/ShortAudioSampleFrench.mp3";
        String expected = "Où est l’arrêt du bus où est l’arrêt du bus";
        String jobName;
        String result;

        if (transcriber.isAvailable()) {
            try {
                jobName = transcriber.startTranscribeAudio(audioFilePath);
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
    public void testSimpleTranscribeUnknownVideoShortEnglish() {
        String videoFilePath = "src/test/resources/ShortVideoSampleEnglish";
        String expected = "Hi";
        String jobName;
        String result;

        if (transcriber.isAvailable()) {
            try {
                jobName = transcriber.startTranscribeAudio(videoFilePath);
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
                jobName = transcriber.startTranscribeAudio(videoFilePath);
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