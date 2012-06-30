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
import java.io.NotSerializableException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
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
    static class AnError extends Error {
        private static final long serialVersionUID = -6197267350768803348L;
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
    
    /**
     * This error isn't serializable on the server, so can't be sent back
     *  to the Fork Client once it has occured
     */
    static class WontBeSerializedError extends RuntimeException {
       private static final long serialVersionUID = 1L;

       WontBeSerializedError(String message) {
          super(message);
       }

       private void writeObject(java.io.ObjectOutputStream out) {
          RuntimeException e = new RuntimeException("Bang!");
          boolean found = false;
          for (StackTraceElement ste : e.getStackTrace()) {
             if (ste.getClassName().equals(ForkParser.class.getName())) {
                found = true;
             }
          }
          if (!found) {
             throw e;
          }
       }
    }
    
    static class BrokenParser implements Parser {
        private static final long serialVersionUID = 995871497930817839L;
        public Error err = new AnError("Simulated fail");
        public RuntimeException re = null;
        
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return new HashSet<MediaType>(Arrays.asList(MediaType.TEXT_PLAIN));
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
            if (re != null) throw re;
            throw err;
        }
    }
    
    /**
     * TIKA-831 Parsers throwing errors should be caught and
     *  properly reported
     */
    public void testParsingErrorInForkedParserShouldBeReported() throws Exception {
        BrokenParser brokenParser = new BrokenParser();
        Parser parser = new ForkParser(ForkParser.class.getClassLoader(), brokenParser);
        InputStream stream = getClass().getResourceAsStream("/test-documents/testTXT.txt");
        
        // With a serializable error, we'll get that back
        try {
            ContentHandler output = new BodyContentHandler();
            ParseContext context = new ParseContext();
            parser.parse(stream, output, new Metadata(), context);
            fail("Expected TikaException caused by Error");
        } catch (TikaException e) {
            assertEquals(brokenParser.err, e.getCause());
        }
        
        // With a non serializable one, we'll get something else
        // TODO Fix this test
        brokenParser = new BrokenParser();
        brokenParser.re= new WontBeSerializedError("Can't Serialize");
        parser = new ForkParser(ForkParser.class.getClassLoader(), brokenParser);
//        try {
//           ContentHandler output = new BodyContentHandler();
//           ParseContext context = new ParseContext();
//           parser.parse(stream, output, new Metadata(), context);
//           fail("Expected TikaException caused by Error");
//       } catch (TikaException e) {
//           assertEquals(TikaException.class, e.getCause().getClass());
//           assertEquals("Bang!", e.getCause().getMessage());
//       }
    }
    
    /**
     * If we supply a non serializable object on the ParseContext,
     *  check we get a helpful exception back
     */
    public void testParserHandlingOfNonSerializable() throws Exception {
       ForkParser parser = new ForkParser(
             ForkParserIntegrationTest.class.getClassLoader(),
             tika.getParser());
       
       ParseContext context = new ParseContext();
       context.set(Detector.class, new Detector() {
          public MediaType detect(InputStream input, Metadata metadata) {
             return MediaType.OCTET_STREAM;
          }
       });

       try {
          ContentHandler output = new BodyContentHandler();
          InputStream stream = ForkParserIntegrationTest.class.getResourceAsStream(
              "/test-documents/testTXT.txt");
          parser.parse(stream, output, new Metadata(), context);
          fail("Should have blown up with a non serializable ParseContext");
       } catch(TikaException e) {
          // Check the right details
          assertNotNull(e.getCause());
          assertEquals(NotSerializableException.class, e.getCause().getClass());
          assertEquals("Unable to serialize ParseContext to pass to the Forked Parser", e.getMessage());
       } finally {
          parser.close();
       }
    }

    /**
     * TIKA-832
     */
    public void testAttachingADebuggerOnTheForkedParserShouldWork()
            throws Exception {
        ParseContext context = new ParseContext();
        context.set(Parser.class, tika.getParser());

        ForkParser parser = new ForkParser(
                ForkParserIntegrationTest.class.getClassLoader(),
                tika.getParser());
        parser.setJavaCommand(
                "java -Xmx32m -Xdebug -Xrunjdwp:"
                + "transport=dt_socket,address=54321,server=y,suspend=n");
        try {
            ContentHandler body = new BodyContentHandler();
            InputStream stream = ForkParserIntegrationTest.class.getResourceAsStream(
                    "/test-documents/testTXT.txt");
            parser.parse(stream, body, new Metadata(), context);
            String content = body.toString();
            assertTrue(content.contains("Test d'indexation"));
            assertTrue(content.contains("http://www.apache.org"));
        } finally {
            parser.close();
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
