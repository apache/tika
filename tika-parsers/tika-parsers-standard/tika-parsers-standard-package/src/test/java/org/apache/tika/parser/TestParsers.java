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
package org.apache.tika.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.MultiThreadedTikaTest;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Junit test class for Tika {@link Parser}s.
 */
public class TestParsers extends MultiThreadedTikaTest {

    private TikaConfig tc;

    private Tika tika;

    @BeforeEach
    public void setUp() throws Exception {
        tc = TikaConfig.getDefaultConfig();
        tika = new Tika(tc);
    }

    @Test
    public void testWORDxtraction() throws Exception {
        Parser parser = tika.getParser();
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream
                .get(getResourceAsStream("/test-documents/testWORD.doc"))) {
            try (InputStream stream = new FileInputStream(tis.getFile())) {
                parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
            }
        }
        assertEquals("Sample Word Document", metadata.get(TikaCoreProperties.TITLE));
    }

    @Test
    public void testEXCELExtraction() throws Exception {
        final String expected = "Numbers and their Squares";
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream
                .get(getResourceAsStream("/test-documents/testEXCEL.xls"))) {
            File file = tis.getFile();
            String s1 = tika.parseToString(file);
            assertTrue(s1.contains(expected), "Text does not contain '" + expected + "'");
            Parser parser = tika.getParser();
            try (InputStream stream = new FileInputStream(file)) {
                parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
            }
        }
        assertEquals("Simple Excel document", metadata.get(TikaCoreProperties.TITLE));
    }

    @Test
    public void testOptionalHyphen() throws Exception {
        String[] extensions = new String[]{"ppt", "pptx", "doc", "docx", "rtf", "pdf"};
        for (String extension : extensions) {
            try (TikaInputStream tis = TikaInputStream
                    .get(getResourceAsStream("/test-documents/testOptionalHyphen." + extension))) {

                String content = tika.parseToString(tis.getFile());
                assertTrue(content.contains("optionalhyphen") ||
                                        content.contains("optional\u00adhyphen") ||   // soft hyphen
                                        content.contains("optional\u200bhyphen") ||   // zero width space
                                        content.contains("optional\u2027"),
                        "optional hyphen was not handled for '" + extension + "' file type: " +
                                        content);          // hyphenation point
            }
        }

    }

    private void verifyComment(String extension, String fileName) throws Exception {
        String content = null;
        try (TikaInputStream tis = TikaInputStream
                .get(getResourceAsStream("/test-documents/" + fileName + "." + extension))) {
            content = tika.parseToString(tis.getFile());
        }
        assertTrue(content.contains("Here is some text"),
                extension + ": content=" + content + " did not extract text");
        assertTrue(content.contains("Here is a comment"),
                extension + ": content=" + content + " did not extract comment");
    }

    @Test
    public void testComment() throws Exception {
        final String[] extensions =
                new String[]{"ppt", "pptx", "doc", "docx", "xls", "xlsx", "pdf", "rtf"};
        for (String extension : extensions) {
            verifyComment(extension, "testComment");
        }
    }

    //TODO: add a @smoketest tag or something similar to run this occasionally automatically
    @Test
    @Disabled("disabled for regular builds; run occasionally")
    public void testAllMultiThreaded() throws Exception {
        //this runs against all files in /test-documents
        ParseContext[] contexts = new ParseContext[10];
        for (int i = 0; i < 10; i++) {
            contexts[i] = new ParseContext();
        }
        testMultiThreaded(AUTO_DETECT_PARSER, contexts, 10, 100, pathname -> true);
    }
}
