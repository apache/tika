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
package org.apache.tika.parser.ner;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

import org.apache.tika.Tika;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ner.opennlp.OpenNLPNERecogniser;
import org.apache.tika.parser.ner.regex.RegexNERecogniser;

/**
 * Test case for {@link NamedEntityParser}
 */
public class NamedEntityParserTest extends TikaTest {

    public static final String CONFIG_FILE = "tika-config.xml";

    @Test
    public void testParse() throws Exception {

        //test config is added to resources directory
        try (InputStream is = getResourceAsStream(CONFIG_FILE)) {
            TikaConfig config = new TikaConfig(is);
            Tika tika = new Tika(config);
            String text = "I am student at University of Southern California (USC)," +
                    " located in Los Angeles . USC's football team is called by name Trojans." +
                    " Mr. John McKay was a head coach of the team from 1960 - 1975";
            Metadata md = new Metadata();
            tika.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), md);

            HashSet<String> set = new HashSet<>(
                    Arrays.asList(md.getValues(TikaCoreProperties.TIKA_PARSED_BY)));
            assumeTrue(set.contains(NamedEntityParser.class.getName()));

            set.clear();
            set.addAll(Arrays.asList(md.getValues("NER_PERSON")));
            assumeTrue(set.contains("John McKay"));

            set.clear();
            set.addAll(Arrays.asList(md.getValues("NER_LOCATION")));
            assumeTrue(set.contains("Los Angeles"));

            set.clear();
            set.addAll(Arrays.asList(md.getValues("NER_ORGANIZATION")));
            assumeTrue(set.contains("University of Southern California"));

            set.clear();
            set.addAll(Arrays.asList(md.getValues("NER_DATE")));
            assumeTrue(set.contains("1960 - 1975"));
        }
    }

    @Test
    public void testNerChain() throws Exception {
        String classNames =
                OpenNLPNERecogniser.class.getName() + "," + RegexNERecogniser.class.getName();
        System.setProperty(NamedEntityParser.SYS_PROP_NER_IMPL, classNames);
        try (InputStream is = getResourceAsStream(CONFIG_FILE)) {
            TikaConfig config = new TikaConfig(is);
            Tika tika = new Tika(config);
            String text = "University of Southern California (USC), is located in Los Angeles ." +
                    " Campus is busy from monday to saturday";
            Metadata md = new Metadata();
            tika.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), md);
            HashSet<String> keys = new HashSet<>(Arrays.asList(md.names()));
            assumeTrue(keys.contains("NER_WEEK_DAY"));
            assumeTrue(keys.contains("NER_LOCATION"));
        }
    }
}
