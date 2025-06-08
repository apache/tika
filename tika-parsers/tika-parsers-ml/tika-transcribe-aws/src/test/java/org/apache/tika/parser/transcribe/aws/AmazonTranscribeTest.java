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
package org.apache.tika.parser.transcribe.aws;

import java.io.InputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.transcribe.model.LanguageCode;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

//TODO: Check the ACTUAL output of Amazon Transcribe.

/**
 * Tests tika-trancribe by creating an AmazonTranscribe() object.
 * 1) Tests that transcribe functions properly when it is given just a filepath.
 * 2) Both audio (mp3) and video (mp4) files are used in these tests.
 * 
 * How to get this to work:
 * 1) remove "@Disabled"
 * 2) Get an amazon aws account (preferably the free tier)
 * 3) include access key (clientid), secret and bucket name
 * in "tika-config-aws-transcribe.xml" (do not commit this file!). The bucket name must be unique worldwide.
 * 4) Make sure you have the needed permissions policies, AmazonS3FullAccess and AmazonTranscribeFullAccess.
 * 5) Be aware that as of 6/2025, you can get only 60 min / month free transscripts.
 */
@Disabled("Ignore until finalize AmazonTranscribe Interface & build Tika")
public class AmazonTranscribeTest extends TikaTest {

    static Parser PARSER;

    @BeforeAll
    public static void setUp() throws Exception {
        try (InputStream is = AmazonTranscribeTest.class
                .getResourceAsStream("/tika-config-aws-transcribe.xml")) {
            PARSER = new AutoDetectParser(new TikaConfig(is));
        }
    }

    /**
     * Tests transcribe with an audio file given the source language
     * The source language of the file is en-US (English - United States)
     */
    @Test
    public void testAmazonTranscribeAudio_enUS() throws Exception {
        ParseContext context = new ParseContext();
        context.set(LanguageCode.class, LanguageCode.EN_US);
        String xml = getXML("en-US_(A_Little_Bottle_Of_Water).mp3", PARSER, context).xml;
        String expected = "A little bottle of water";
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is en-US (English - United States)
     */
    @Test
    public void testAmazonTranscribeUnknownAudio_enUS() throws Exception {
        String xml = getXML("en-US_(A_Little_Bottle_Of_Water).mp3", PARSER).xml;
        String expected = "A little bottle of water";
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file given the source language
     * The source language of the file is en-US (English - United States)
     */
    @Test
    public void testAmazonTranscribeVideo_enUS() throws Exception {
        String expected = "Hi";
        ParseContext context = new ParseContext();
        context.set(LanguageCode.class, LanguageCode.EN_US);
        String xml = getXML("en-US_(Hi).mp4", PARSER, context).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with a video file without passing in the source language.
     * The source language of the file is en-US (English - United States)
     */
    @Test
    public void testAmazonTranscribeUnknownVideo_enUS() throws Exception {
        String expected = "Hi";
        String xml = getXML("en-US_(Hi).mp4", PARSER).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file given the source language
     * The source language of the file is en-GB (English - Great Britain)
     */
    @Test
    public void testAmazonTranscribeAudio_enGB() throws Exception {
        String file = "en-GB_(A_Little_Bottle_Of_Water).mp3";
        String expected = "A little bottle of water";
        ParseContext context = new ParseContext();
        context.set(LanguageCode.class, LanguageCode.EN_GB);
        String xml = getXML(file, PARSER, context).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is en-GB (English - Great Britain)
     */
    @Test
    public void testAmazonTranscribeUnknownAudio_enGB() throws Exception {
        String file = "en-GB_(A_Little_Bottle_Of_Water).mp3";
        String expected = "A little bottle of water";
        String xml = getXML(file, PARSER).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file given the source language
     * The source language of the file is en-AU (English - Australia)
     */
    @Test
    public void testAmazonTranscribeAudio_enAU() throws Exception {
        String file = "en-AU_(A_Little_Bottle_Of_Water).mp3";
        String expected = "A little bottle of water";
        ParseContext context = new ParseContext();
        context.set(LanguageCode.class, LanguageCode.EN_AU);
        String xml = getXML(file, PARSER, context).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is en-AU (English - Australian)
     */
    @Test
    public void testAmazonTranscribeUnknownAudio_enAU() throws Exception {
        String file = "en-AU_(A_Little_Bottle_Of_Water).mp3";
        String expected = "A little bottle of water";
        String xml = getXML(file, PARSER).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file given the source language
     * The source language of the file is de-DE (German)
     */
    @Test
    public void testAmazonTranscribeAudio_deDE() throws Exception {
        String file = "de-DE_(We_Are_At_School_x2).mp3";
        String expected = "Wir sind in der Schule. Wir sind in der Schule.";
        ParseContext context = new ParseContext();
        context.set(LanguageCode.class, LanguageCode.DE_DE);
        String xml = getXML(file, PARSER, context).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is de-DE (German)
     */
    @Test
    public void testAmazonTranscribeUnknownAudio_deDE() throws Exception {
        String file = "de-DE_(We_Are_At_School_x2).mp3";
        String expected = "Wir sind in der Schule. Wir sind in der Schule.";
        String xml = getXML(file, PARSER).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file given the source language
     * The source language of the file is it-IT (Italian)
     */
    @Test
    public void testAmazonTranscribeAudio_itIT() throws Exception {
        String file = "it-IT_(We_Are_Having_Class_x2).mp3";
        String expected = "stiamo facendo lezione. stiamo facendo lezione.";
        ParseContext context = new ParseContext();
        context.set(LanguageCode.class, LanguageCode.IT_IT);
        String xml = getXML(file, PARSER, context).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is it-IT (Italian)
     */
    @Test
    public void testAmazonTranscribeUnknownAudio_itIT() throws Exception {
        String file = "it-IT_(We_Are_Having_Class_x2).mp3";
        String expected = "stiamo facendo lezione. stiamo facendo lezione.";
        String xml = getXML(file, PARSER).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file given the source language
     * The source language of the file is ja-JP (Japanese)
     */
    @Test
    public void testAmazonTranscribeAudio_jaJP() throws Exception {
        String file = "ja-JP_(We_Are_At_School).mp3";
        String expected = "私達は学校にいます"; //TODO or Watashitachi wa gakkō ni imasu
        ParseContext context = new ParseContext();
        context.set(LanguageCode.class, LanguageCode.JA_JP);
        String xml = getXML(file, PARSER, context).xml;
        assertContains(expected, xml);

    }

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is ja-JP (Japanese)
     */
    @Test
    public void testAmazonTranscribeUnknownAudio_jaJP() throws Exception {
        String file = "ja-JP_(We_Are_At_School).mp3";
        String expected = "私達は学校にいます"; //TODO or Watashitachi wa gakkō ni imasu
        String xml = getXML(file, PARSER).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file given the source language
     * The source language of the file is ko-KR (Korean)
     */
    @Test
    public void testAmazonTranscribeAudio_koKR() throws Exception {
        String file = "ko-KR_(We_Are_Having_Class_x2).mp3";
        String expected = "우리는 수업을하고있다"; //TODO or ulineun sueob-eulhagoissda
        ParseContext context = new ParseContext();
        context.set(LanguageCode.class, LanguageCode.KO_KR);
        String xml = getXML(file, PARSER, context).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is ko-KR (Korean)
     */
    @Test
    public void testAmazonTranscribeUnknownAudio_koKR() throws Exception {
        String file = "ko-KR_(We_Are_Having_Class_x2).mp3";
        String expected = "우리는 수업을하고있다"; //TODO or ulineun sueob-eulhagoissda
        String xml = getXML(file, PARSER).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with a video file given the source language
     * The source language of the file is ko-KR (Korean)
     */
    @Test
    public void testAmazonTranscribeVideo_koKR() throws Exception {
        String file = "ko-KR_(Annyeonghaseyo).mp4";
        //TODO: Check whether output is Annyeonghaseyo or 안녕하세요
        String expected = "Annyeonghaseyo";
        ParseContext context = new ParseContext();
        context.set(LanguageCode.class, LanguageCode.KO_KR);
        String xml = getXML(file, PARSER, context).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an video file without passing in the source language.
     * The source language of the file is ko-KR (Korean)
     */
    @Test
    public void testAmazonTranscribeUnknownVideo_koKR() throws Exception {
        String file = "ko-KR_(Annyeonghaseyo).mp4";
        //TODO: Check whether output is Annyeonghaseyo or 안녕하세요
        String expected = "Annyeonghaseyo";
        String xml = getXML(file, PARSER).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file given the source language
     * The source language of the file is pt-BR (Portuguese - Brazil)
     */
    @Test
    public void testAmazonTranscribeAudio_ptBR() throws Exception {
        String file = "pt-BR_(We_Are_At_School).mp3";
        String expected = "nós estamos na escola.";
        ParseContext context = new ParseContext();
        context.set(LanguageCode.class, LanguageCode.PT_BR);
        String xml = getXML(file, PARSER, context).xml;
        assertContains(expected, xml);
    }

    /**
     * Tests transcribe with an audio file without passing in the source language.
     * The source language of the file is pt-BR (Portuguese - Brazil)
     */
    @Test
    public void testAmazonTranscribeUnknownAudio_ptBR() throws Exception {
        String file = "pt-BR_(We_Are_At_School).mp3";
        String expected = "nós estamos na escola.";
        String xml = getXML(file, PARSER).xml;
        assertContains(expected, xml);
    }

}
