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
package org.apache.tika.parser.sentiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;

/**
 * Test case for {@link SentimentAnalysisParser}
 */
public class SentimentAnalysisParserTest extends TikaTest {

    @Test
    public void endToEndTest() throws Exception {
        Parser parser = getParser("tika-config-sentiment-opennlp.json");
        if (parser == null) {
            return;
        }

        String text = "What a wonderful thought it is that" +
                " some of the best days of our lives haven't happened yet.";
        Metadata md = getXML(TikaInputStream.get(text.getBytes(StandardCharsets.UTF_8)),
                parser, new Metadata()).metadata;
        String sentiment = md.get("Sentiment");
        assertNotNull(sentiment);
        assertEquals("positive", sentiment);
    }

    @Test
    public void testCategorical() throws Exception {
        Parser parser = getParser("tika-config-sentiment-opennlp-cat.json");
        if (parser == null) {
            return;
        }
        String text = "Whatever, I need some cooling off time!";
        Metadata md = getXML(TikaInputStream.get(text.getBytes(StandardCharsets.UTF_8)),
                parser, new Metadata()).metadata;
        String sentiment = md.get("Sentiment");
        assertNotNull(sentiment);
        assertEquals("angry", sentiment);
    }

    private Parser getParser(String configJson) throws TikaException, IOException, URISyntaxException {
        try {
            return TikaLoader.load(
                            getConfigPath(SentimentAnalysisParserTest.class, configJson))
                    .loadAutoDetectParser();
        } catch (TikaConfigException e) {
            //if can't connect to pull sentiment model...ignore test
            if (e.getCause() instanceof IOException) {
                return null;
            }
            throw e;
        }
    }
}
