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
package org.apache.tika.parser.sentiment.analysis;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

import static org.junit.Assert.*;

/**
 * Test case for {@link SentimentParser}
 */
public class SentimentParserTest {

    @Test
    public void endToEndTest() throws Exception {

        Tika tika;
        try (InputStream confStream = getClass().getResourceAsStream("tika-config-sentiment-opennlp.xml")) {
            assert confStream != null;
            TikaConfig config = new TikaConfig(confStream);
            tika = new Tika(config);
        }
        String text = "What a wonderful thought it is that" +
                " some of the best days of our lives haven't happened yet.";
        ByteArrayInputStream stream = new ByteArrayInputStream(text.getBytes(Charset.defaultCharset()));
        Metadata md = new Metadata();
        tika.parse(stream, md);
        String sentiment = md.get("Sentiment");
        assertNotNull(sentiment);
        assertEquals(sentiment, "positive");

    }
}