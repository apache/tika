/**
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
package org.apache.tika.parser.recognition;

import org.apache.commons.lang.StringUtils;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Testcases for Object Recognition Parser
 */
public class ObjectRecognitionParserTest {

    private static final String CONFIG_FILE = "org/apache/tika/parser/recognition/tika-config-tflow.xml";
    private static final String CONFIG_REST_FILE = "org/apache/tika/parser/recognition/tika-config-tflow-rest.xml";
    private static final String CAT_IMAGE = "test-documents/testJPEG.jpg";
    private static final ClassLoader loader = ObjectRecognitionParserTest.class.getClassLoader();

    @Ignore("If tensorflow not available Ignore")
    @Test
    public void jpegTesorflowTest() throws IOException, TikaException, SAXException {

        try(InputStream stream = loader.getResourceAsStream(CONFIG_FILE)){
            assert stream != null;
            Tika tika = new Tika(new TikaConfig(stream));
            Metadata metadata = new Metadata();
            try (InputStream imageStream = loader.getResourceAsStream(CAT_IMAGE)){
                Reader reader = tika.parse(imageStream, metadata);
                List<String> lines = IOUtils.readLines(reader);
                String text = StringUtils.join(lines, " ");
                String[] expectedObjects = {"Egyptian cat", "Border collie"};
                String metaValues = StringUtils.join(metadata.getValues(ObjectRecognitionParser.MD_KEY), " ");
                for (String expectedObject : expectedObjects) {
                    String message = "'" + expectedObject + "' must have been detected";
                    Assert.assertTrue(message, text.contains(expectedObject));
                    Assert.assertTrue(message, metaValues.contains(expectedObject));
                }
            }
        }
    }

    @Ignore("Configure Rest API service")
    @Test
    public void testREST() throws Exception {
        try (InputStream stream = loader.getResourceAsStream(CONFIG_REST_FILE)){
            assert stream != null;
            Tika tika = new Tika(new TikaConfig(stream));
            Metadata metadata = new Metadata();
            try (InputStream imageStream = loader.getResourceAsStream(CAT_IMAGE)){
                Reader reader = tika.parse(imageStream, metadata);
                String text = IOUtils.toString(reader);
                String[] expectedObjects = {"Egyptian cat", "tabby cat"};
                String metaValues = StringUtils.join(metadata.getValues(ObjectRecognitionParser.MD_KEY), " ");
                for (String expectedObject : expectedObjects) {
                    String message = "'" + expectedObject + "' must have been detected";
                    Assert.assertTrue(message, text.contains(expectedObject));
                    Assert.assertTrue(message, metaValues.contains(expectedObject));
                }
            }
        }
    }
}