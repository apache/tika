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

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ner.opennlp.OpenNLPNERecogniser;
import org.apache.tika.parser.ner.regex.RegexNERecogniser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertTrue;

/**
 *Test case for {@link NamedEntityParser}
 */
public class NamedEntityParserTest {

    public static final String CONFIG_FILE = "tika-config.xml";

    @Test
    public void testParse() throws Exception {

        //test config is added to resources directory
        TikaConfig config = new TikaConfig(getClass().getResourceAsStream(CONFIG_FILE));
        Tika tika = new Tika(config);
        String text = "I am student at University of Southern California (USC)," +
                " located in Los Angeles . USC's football team is called by name Trojans." +
                " Mr. John McKay was a head coach of the team from 1960 - 1975";
        Metadata md = new Metadata();
        tika.parse(new ByteArrayInputStream(text.getBytes(Charset.defaultCharset())), md);

        HashSet<String> set = new HashSet<String>();
        set.addAll(Arrays.asList(md.getValues("X-Parsed-By")));
        assertTrue(set.contains(NamedEntityParser.class.getName()));

        set.clear();
        set.addAll(Arrays.asList(md.getValues("NER_PERSON")));
        assertTrue(set.contains("John McKay"));

        set.clear();
        set.addAll(Arrays.asList(md.getValues("NER_LOCATION")));
        assertTrue(set.contains("Los Angeles"));

        set.clear();
        set.addAll(Arrays.asList(md.getValues("NER_ORGANIZATION")));
        assertTrue(set.contains("University of Southern California"));

        set.clear();
        set.addAll(Arrays.asList(md.getValues("NER_DATE")));
        assertTrue(set.contains("1960 - 1975"));

    }

    @Test
    public void testNerChain() throws Exception {
        String classNames = OpenNLPNERecogniser.class.getName()
                + "," + RegexNERecogniser.class.getName();
        System.setProperty(NamedEntityParser.SYS_PROP_NER_IMPL, classNames);
        TikaConfig config = new TikaConfig(getClass().getResourceAsStream(CONFIG_FILE));
        Tika tika = new Tika(config);
        String text = "University of Southern California (USC), is located in Los Angeles ." +
                " Campus is busy from monday to saturday";
        Metadata md = new Metadata();
        tika.parse(new ByteArrayInputStream(text.getBytes(Charset.defaultCharset())), md);
        HashSet<String> keys = new HashSet<>(Arrays.asList(md.names()));
        assertTrue(keys.contains("NER_WEEK_DAY"));
        assertTrue(keys.contains("NER_LOCATION"));

    }
}