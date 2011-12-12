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
package org.apache.tika.parser.fork;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.fork.ForkParser;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Test that the ForkParser correctly behaves when
 *  wired in to the regular Parsers and their test data
 */
public class ForkParserIntegrationTest extends TestCase {
    /**
     * Simple text parsing
     * TODO Fix this test so it passes
     */
    public void DISABLEDtestForkedTextParsing() throws Exception {
       final ForkParser parser = new ForkParser(
             ForkParserIntegrationTest.class.getClassLoader(),
             new ForkParser());

       try {
          ContentHandler output = new BodyContentHandler();
          InputStream stream = ForkParserIntegrationTest.class.getResourceAsStream("testTXT.txt");
          ParseContext context = new ParseContext();
          parser.parse(stream, output, new Metadata(), context);

          String content = output.toString();
          assertTrue(content.contains("Test d'indexation"));
          assertTrue(content.contains("http://www.apache.org/"));
       } finally {
          parser.close();
       }
    }
   
    /**
     * TIKA-808 - Ensure that parsing of our test PDFs work under
     *  the Fork Parser, to ensure that complex parsing behaves
     * TODO Fix this test so it passes
     */
    public void DISABLEDtestForkedPDFParsing() throws Exception {
       final ForkParser parser = new ForkParser(
             ForkParserIntegrationTest.class.getClassLoader(),
             new ForkParser());
       
       try {
          ContentHandler output = new BodyContentHandler();
          InputStream stream = ForkParserIntegrationTest.class.getResourceAsStream("testPDF.pdf");
          ParseContext context = new ParseContext();
          parser.parse(stream, output, new Metadata(), context);
          
          String content = output.toString();
          assertTrue(content.contains("Apache Tika"));
          assertTrue(content.contains("Tika - Content Analysis Toolkit"));
          assertTrue(content.contains("incubator"));
          assertTrue(content.contains("Apache Software Foundation"));
      } finally {
          parser.close();
      }
    }
    
    public void testDUMMY() {
       // To avoid warnings about no tests while others are disabled
    }
}
