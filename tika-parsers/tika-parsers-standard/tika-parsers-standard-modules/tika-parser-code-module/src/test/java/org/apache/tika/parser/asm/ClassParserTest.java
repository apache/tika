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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Test case for parsing Java class files.
 */
public class ClassParserTest extends TikaTest {

    @Test
    public void testClassParsing() throws Exception {
        Metadata metadata = new Metadata();
        String content = getText("AutoDetectParser.class", metadata);
        assertEquals("AutoDetectParser", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("AutoDetectParser.class", metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));

        assertTrue(content.contains("package org.apache.tika.parser;"));
        assertTrue(content.contains("class AutoDetectParser extends CompositeParser"));
        assertTrue(content.contains("private org.apache.tika.mime.MimeTypes types"));
        assertTrue(content.contains(
                "public void parse(" + "java.io.InputStream, org.xml.sax.ContentHandler," +
                        " org.apache.tika.metadata.Metadata) throws" +
                        " java.io.IOException, org.xml.sax.SAXException," +
                        " org.apache.tika.exception.TikaException;"));
        assertTrue(content.contains("private byte[] getPrefix(java.io.InputStream, int)" +
                " throws java.io.IOException;"));
    }

    @Test
    public void testJava11() throws Exception {
        //Make sure that this java 11 target .class
        //file doesn't throw an exception
        //TIKA-2992
        XMLResult xmlResult = getXML("AppleSingleFileParser.class");
        assertContains("<title>AppleSingleFileParser</title>", xmlResult.xml);
    }
}
