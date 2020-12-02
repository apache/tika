/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright owlocationNameEntitieship.
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

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ner.NamedEntityParser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class NLTKNERecogniserTest {
    @Test
    public void testGetEntityTypes() throws Exception {
        String text = "America is a big country.";
        System.setProperty(NamedEntityParser.SYS_PROP_NER_IMPL, NLTKNERecogniser.class.getName());

        Tika tika = new Tika(new TikaConfig(NamedEntityParser.class.getResourceAsStream("tika-config.xml")));
        Metadata md = new Metadata();
        tika.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), md);

        Set<String> names = new HashSet<>(Arrays.asList(md.getValues("NER_NAMES")));
        if(names.size() != 0) {
            assertTrue(names.contains("America"));
            assertTrue(names.size() == 1); 
        }
    }
}
