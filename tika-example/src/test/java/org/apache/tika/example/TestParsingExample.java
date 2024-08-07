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

package org.apache.tika.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.serialization.JsonMetadataList;

public class TestParsingExample extends TikaTest {
    ParsingExample parsingExample;

    @BeforeEach
    public void setUp() {
        parsingExample = new ParsingExample();
    }

    @Test
    public void testParseToStringExample() throws IOException, SAXException, TikaException {
        String result = parsingExample
                .parseToStringExample()
                .trim();
        assertEquals("test", result, "enough detectors?");
    }

    @Test
    public void testParseExample() throws IOException, SAXException, TikaException {
        String result = parsingExample
                .parseExample()
                .trim();
        assertEquals("test", result, "Expected 'test', but got '" + result + "'");
    }

    @Test
    public void testNoEmbeddedExample() throws IOException, SAXException, TikaException {
        String result = parsingExample.parseNoEmbeddedExample();
        assertContains("embed_0", result);
        assertNotContained("embed1/embed1a.txt", result);
        assertNotContained("embed3/embed3.txt", result);
        assertNotContained("When in the Course", result);
    }

    @Test
    public void testRecursiveParseExample() throws IOException, SAXException, TikaException {
        String result = parsingExample.parseEmbeddedExample();
        assertContains("embed_0", result);
        assertContains("embed1/embed1a.txt", result);
        assertContains("embed3/embed3.txt", result);
        assertContains("When in the Course", result);
    }

    @Test
    public void testRecursiveParserWrapperExample() throws IOException, SAXException, TikaException {
        List<Metadata> metadataList = parsingExample.recursiveParserWrapperExample();
        assertEquals(12, metadataList.size(), "Number of embedded documents + 1 for the container document");
        Metadata m = metadataList.get(6);
        //this is the location the embed3.txt text file within the outer .docx
        assertEquals("/embed1.zip/embed2.zip/embed3.zip/embed3.txt", m.get("X-TIKA:embedded_resource_path"));
        //it contains some html encoded content
        assertContains("When in the Course", m.get("X-TIKA:content"));
    }

    @Test
    public void testSerializedRecursiveParserWrapperExample() throws IOException, SAXException, TikaException {
        String json = parsingExample.serializedRecursiveParserWrapperExample();
        assertTrue(json.contains("When in the Course"));
        //now try deserializing the JSON
        List<Metadata> metadataList = JsonMetadataList.fromJson(new StringReader(json));
        assertEquals(12, metadataList.size());
    }

}
