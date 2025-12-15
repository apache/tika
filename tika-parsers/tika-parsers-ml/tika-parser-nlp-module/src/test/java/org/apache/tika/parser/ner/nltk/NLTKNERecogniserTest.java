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
package org.apache.tika.parser.ner.nltk;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ner.NamedEntityParser;

public class NLTKNERecogniserTest extends TikaTest {

    @Test
    public void testGetEntityTypes() throws Exception {
        String text = "America is a big country.";
        System.setProperty(NamedEntityParser.SYS_PROP_NER_IMPL, NLTKNERecogniser.class.getName());

        Parser parser = TikaLoader.load(
                        getConfigPath(NLTKNERecogniserTest.class, "tika-config.json"))
                .loadAutoDetectParser();
        Metadata md = getXML(
                TikaInputStream.get(text.getBytes(StandardCharsets.UTF_8)),
                parser, new Metadata()).metadata;

        Set<String> names = new HashSet<>(Arrays.asList(md.getValues("NER_NAMES")));
        if (names.size() != 0) {
            assertTrue(names.contains("America"));
            assertTrue(names.size() == 1);
        }
    }
}
