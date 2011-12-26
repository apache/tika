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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.fork.ForkParser;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Test that the ForkParser correctly behaves when
 *  wired in to the regular Parsers and their test data
 */
public class ForkParserIntegrationTest extends TestCase {
//    private TikaConfig tika = TikaConfig.getDefaultConfig();
    private Tika tika = new Tika(); // TODO Use TikaConfig instead, when it works
    
    /**
     * Simple text parsing
     */
    public void testForkedTextParsing() throws Exception {
        ForkParser parser = new ForkParser(
                ForkParserIntegrationTest.class.getClassLoader(),
                tika.getParser());

       try {
          ContentHandler output = new BodyContentHandler();
          InputStream stream = ForkParserIntegrationTest.class.getResourceAsStream(
                  "/test-documents/testTXT.txt");
          ParseContext context = new ParseContext();
          parser.parse(stream, output, new Metadata(), context);

          String content = output.toString();
          assertTrue(content.contains("Test d'indexation"));
          assertTrue(content.contains("http://www.apache.org"));
       } finally {
          parser.close();
       }
    }
   
    /**
     * This error has a message and an equals() implementation as to be able 
     * to match it against the serialized version of itself.
     */
    @SuppressWarnings("serial")
    static class AnError extends Error {
        private String message;
        AnError(String message) {
            super(message);
            this.message = message;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AnError anError = (AnError) o;

            if (!message.equals(anError.message)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return message.hashCode();
        }
    }
    
    static class BrokenParser implements Parser {
        public Error e = new AnError("Simulated fail"); 
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return new HashSet<MediaType>(Arrays.asList(MediaType.TEXT_PLAIN));
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
            throw e;
        }
    }
    
    /**
     * TIKA-831 Parsers throwing errors should be caught and
     *  properly reported
     * TODO Disabled, pending a fix for the not serialized exception
     */
    public void DISABLEDtestParsingErrorInForkedParserShouldBeReported() throws Exception {
        BrokenParser brokenParser = new BrokenParser();
        Parser parser = new ForkParser(ForkParser.class.getClassLoader(), brokenParser);
        Tika forkedTika = new Tika(TikaConfig.getDefaultConfig().getDetector(), parser);
        InputStream stream = getClass().getResourceAsStream("/test-documents/testTXT.txt");
        try {
            forkedTika.parseToString(stream);
            fail("Expected TikaException caused by Error");
        } catch (TikaException e) {
            assertEquals(brokenParser.e, e.getCause());
        }
    }

    /**
     * TIKA-808 - Ensure that parsing of our test PDFs work under
     * the Fork Parser, to ensure that complex parsing behaves
     */
    public void testForkedPDFParsing() throws Exception {
        ForkParser parser = new ForkParser(
                ForkParserIntegrationTest.class.getClassLoader(),
                tika.getParser());
        try {
            ContentHandler output = new BodyContentHandler();
            InputStream stream = ForkParserIntegrationTest.class.getResourceAsStream(
                    "/test-documents/testPDF.pdf");
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
}
