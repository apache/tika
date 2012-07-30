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
package org.apache.tika.parser.microsoft;

import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import junit.framework.TestCase;

/**
 * Tests for Microsoft Project (MPP) Files.
 * 
 * Note - we don't currently have a dedicated Project
 *  Parser, all we have is the common office metadata
 */
public class ProjectParserTest extends TestCase {
    public void testProject2003() throws Exception {
       InputStream input = ProjectParserTest.class.getResourceAsStream(
             "/test-documents/testPROJECT2003.mpp");
       try {
          doTestProject(input);
       } finally {
          input.close();
       }
    }

    public void testProject2007() throws Exception {
        InputStream input = ProjectParserTest.class.getResourceAsStream(
                "/test-documents/testPROJECT2007.mpp");
        try {
            doTestProject(input);
        } finally {
            input.close();
        }
    }

    private void doTestProject(InputStream input) throws Exception {
       Metadata metadata = new Metadata();
       ContentHandler handler = new BodyContentHandler();
       new OfficeParser().parse(input, handler, metadata, new ParseContext());

       assertEquals(
               "application/vnd.ms-project",
               metadata.get(Metadata.CONTENT_TYPE));
       
       assertEquals("The quick brown fox jumps over the lazy dog", metadata.get(TikaCoreProperties.TITLE));
       assertEquals("Gym class featuring a brown fox and lazy dog", metadata.get(OfficeOpenXMLCore.SUBJECT));
       assertEquals("Gym class featuring a brown fox and lazy dog", metadata.get(Metadata.SUBJECT));
       assertEquals("Nevin Nollop", metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("", metadata.get(TikaCoreProperties.MODIFIER));
       assertEquals("Pangram, fox, dog", metadata.get(TikaCoreProperties.KEYWORDS));
       assertEquals("Comment Vulpes vulpes comment", metadata.get(TikaCoreProperties.COMMENTS));
       
       assertEquals("Category1", metadata.get(OfficeOpenXMLCore.CATEGORY));
       assertEquals("Mr Burns", metadata.get(OfficeOpenXMLExtended.MANAGER));
       assertEquals("CompanyA", metadata.get(OfficeOpenXMLExtended.COMPANY));
       
       assertEquals("2011-11-24T10:58:00Z", metadata.get(TikaCoreProperties.CREATED));
       assertEquals("2011-11-24T10:58:00Z", metadata.get(Metadata.CREATION_DATE));
       assertEquals("2011-11-24T11:31:00Z", metadata.get(TikaCoreProperties.MODIFIED));
       assertEquals("2011-11-24T11:31:00Z", metadata.get(Metadata.DATE));
       
       // Custom Project metadata is present with prefix
       assertEquals("0%", metadata.get("custom:% Complete"));
       assertEquals("0%", metadata.get("custom:% Work Complete"));
       assertEquals("\u00a3"+"0.00", metadata.get("custom:Cost"));
       assertEquals("2d?", metadata.get("custom:Duration"));
       assertEquals("16h", metadata.get("custom:Work"));
       
       // Currently, we don't do textual contents of the file
       String content = handler.toString();
       assertEquals("", content);
    }
}
