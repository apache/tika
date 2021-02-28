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
    public void AmazonTranscribeGuessLanguageAudioShortTest() {
        String expected = "where is the bus stop? where is the bus stop?";
        //TODO: "expected" should be changed to reflect the contents of ShortAudioSampleEnglish.mp3
        /*
        URL res = getClass().getClassLoader().getResource("ShortAudioSampleEnglish.mp3");
        File file = Paths.get(res.toURI()).toFile();
        String absolutePath = file.getAbsolutePath();
        Necessary to get the correct file path from our test resource folder? */
        //TODO: is the above commented block necessary to obtain the proper filepath for a file located in the tika-translate/test/resources directory?

        String audioFilePath = "src/test/resources/ShortAudioSampleEnglish.mp3";
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
    public void AmazonTranscribeGuessLanguageAudioLongTest() {
        String expected = "where is the bus stop? where is the bus stop?";
        //TODO: "expected" should be changed to reflect the contents of LongAudioSample.mp3
        String audioFilePath = "src/test/resources/LongAudioSample.mp3";
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
    public void AmazonTranscribeGuessLanguageShortVideoTest() {
        String expected = "where is the bus stop? where is the bus stop?";
        //TODO: "expected" should be changed to reflect the contents of ShortVideoSample.mp4
        String videoFilePath = "src/test/resources/ShortVideoSample.mp4";
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
    public void AmazonTranscribeGuessLanguageLongVideoTest() {
        String expected = "hello sir";
        //TODO: "expected" should be changed to reflect the contents of LongVideoSample.mp4
        String videoFilePath = "src/test/resources/LongVideoSample.mp4";
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