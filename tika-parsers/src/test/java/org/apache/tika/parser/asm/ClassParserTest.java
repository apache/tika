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
package org.apache.tika.parser.asm;

import junit.framework.TestCase;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Test case for parsing Java class files.
 */
public class ClassParserTest extends TestCase {

    public void testClassParsing() throws Exception {
        String path = "/test-documents/AutoDetectParser.class";
        Metadata metadata = new Metadata();
        String content = new Tika().parseToString(
                ClassParserTest.class.getResourceAsStream(path), metadata);

        assertEquals("AutoDetectParser", metadata.get(TikaCoreProperties.TITLE));
        assertEquals(
                "AutoDetectParser.class",
                metadata.get(Metadata.RESOURCE_NAME_KEY));

        assertTrue(content.contains("package org.apache.tika.parser;"));
        assertTrue(content.contains(
                "class AutoDetectParser extends CompositeParser"));
        assertTrue(content.contains(
                "private org.apache.tika.mime.MimeTypes types"));
        assertTrue(content.contains(
                "public void parse("
                + "java.io.InputStream, org.xml.sax.ContentHandler,"
                + " org.apache.tika.metadata.Metadata) throws"
                + " java.io.IOException, org.xml.sax.SAXException,"
                + " org.apache.tika.exception.TikaException;"));
        assertTrue(content.contains(
                "private byte[] getPrefix(java.io.InputStream, int)"
                + " throws java.io.IOException;"));
    }

}
