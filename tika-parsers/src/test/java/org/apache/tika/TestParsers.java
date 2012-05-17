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
package org.apache.tika;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Junit test class for Tika {@link Parser}s.
 */
public class TestParsers extends TikaTest {

    private TikaConfig tc;

    private Tika tika;

    public void setUp() throws Exception {
        tc = TikaConfig.getDefaultConfig();
        tika = new Tika(tc);
    }

    public void testWORDxtraction() throws Exception {
        File file = getResourceAsFile("/test-documents/testWORD.doc");
        Parser parser = tika.getParser();
        Metadata metadata = new Metadata();
        InputStream stream = new FileInputStream(file);
        try {
            parser.parse(
                    stream, new DefaultHandler(), metadata, new ParseContext());
        } finally {
            stream.close();
        }
        assertEquals("Sample Word Document", metadata.get(TikaCoreProperties.TITLE));
    }

    public void testEXCELExtraction() throws Exception {
        final String expected = "Numbers and their Squares";
        File file = getResourceAsFile("/test-documents/testEXCEL.xls");
        String s1 = tika.parseToString(file);
        assertTrue("Text does not contain '" + expected + "'", s1
                .contains(expected));
        Parser parser = tika.getParser();
        Metadata metadata = new Metadata();
        InputStream stream = new FileInputStream(file);
        try {
            parser.parse(
                    stream, new DefaultHandler(), metadata, new ParseContext());
        } finally {
            stream.close();
        }
        assertEquals("Simple Excel document", metadata.get(TikaCoreProperties.TITLE));
    }

    public void testOptionalHyphen() throws Exception {
        String[] extensions =
                new String[] { "ppt", "pptx", "doc", "docx", "rtf", "pdf"};
        for (String extension : extensions) {
            File file = getResourceAsFile("/test-documents/testOptionalHyphen." + extension);
            String content = tika.parseToString(file);
            assertTrue("optional hyphen was not handled for '" + extension + "' file type: " + content,
                       content.contains("optionalhyphen") ||
                       content.contains("optional\u00adhyphen") ||   // soft hyphen
                       content.contains("optional\u200bhyphen") ||   // zero width space
                       content.contains("optional\u2027"));          // hyphenation point
            
        }
    }

    private void verifyComment(String extension, String fileName) throws Exception {
        File file = getResourceAsFile("/test-documents/" + fileName + "." + extension);
        String content = tika.parseToString(file);
        assertTrue(extension + ": content=" + content + " did not extract text",
                   content.contains("Here is some text"));
        assertTrue(extension + ": content=" + content + " did not extract comment",
                   content.contains("Here is a comment"));
    }

    public void testComment() throws Exception {
        final String[] extensions = new String[] {"ppt", "pptx", "doc", "docx", "pdf", "rtf"};
        for(String extension : extensions) {
            verifyComment(extension, "testComment");
        }
    }
}
