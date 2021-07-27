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
package org.apache.tika.parser.microsoft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Tests for Microsoft Project (MPP) Files.
 * <p>
 * Note - we don't currently have a dedicated Project
 * Parser, all we have is the common office metadata
 */
public class ProjectParserTest {

    @Test
    public void testProject2003() throws Exception {
        try (InputStream input = ProjectParserTest.class
                .getResourceAsStream("/test-documents/testPROJECT2003.mpp")) {
            doTestProject(input);
        }
    }

    @Test
    public void testProject2007() throws Exception {
        try (InputStream input = ProjectParserTest.class
                .getResourceAsStream("/test-documents/testPROJECT2007.mpp")) {
            doTestProject(input);
        }
    }

    private void doTestProject(InputStream input) throws Exception {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        new OfficeParser().parse(input, handler, metadata, new ParseContext());

        assertEquals("application/vnd.ms-project", metadata.get(Metadata.CONTENT_TYPE));

        assertEquals("The quick brown fox jumps over the lazy dog",
                metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Gym class featuring a brown fox and lazy dog",
                metadata.get(OfficeOpenXMLCore.SUBJECT));
        assertEquals("Nevin Nollop", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("", metadata.get(TikaCoreProperties.MODIFIER));
        assertEquals("Pangram, fox, dog", metadata.get(TikaCoreProperties.SUBJECT));
        assertEquals("Comment Vulpes vulpes comment", metadata.get(TikaCoreProperties.COMMENTS));

        assertEquals("Category1", metadata.get(OfficeOpenXMLCore.CATEGORY));
        assertEquals("Mr Burns", metadata.get(OfficeOpenXMLExtended.MANAGER));
        assertEquals("CompanyA", metadata.get(OfficeOpenXMLExtended.COMPANY));

        assertEquals("2011-11-24T10:58:00Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2011-11-24T11:31:00Z", metadata.get(TikaCoreProperties.MODIFIED));

        // Custom Project metadata is present with prefix
        assertEquals("0%", metadata.get("custom:% Complete"));
        assertEquals("0%", metadata.get("custom:% Work Complete"));
        assertEquals("\u00a3" + "0.00", metadata.get("custom:Cost"));
        assertEquals("2d?", metadata.get("custom:Duration"));
        assertEquals("16h", metadata.get("custom:Work"));

        // Currently, we don't do textual contents of the file
        String content = handler.toString();
        assertEquals("", content);
    }
}
