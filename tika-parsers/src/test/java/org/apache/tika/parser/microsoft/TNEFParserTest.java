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

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Tests for the TNEF (winmail.dat) parser
 */
public class TNEFParserTest extends AbstractPOIContainerExtractionTest {
   private static final String file = "testWINMAIL.dat";
   
   public void testBasics() throws Exception {
      TikaInputStream stream = getTestFile(file);
      Detector detector = new DefaultDetector();
      try {
         assertEquals(
                 MediaType.application("vnd.ms-tnef"),
                 detector.detect(stream, new Metadata()));
     } finally {
         stream.close();
     }
   }
   
   public void testMetadata() throws Exception {
      TikaInputStream stream = getTestFile(file);
      
      Metadata metadata = new Metadata();
      ContentHandler handler = new BodyContentHandler();
      
      TNEFParser tnef = new TNEFParser();
      tnef.parse(stream, handler, metadata, new ParseContext());
      
      assertEquals("This is a test message", metadata.get(TikaCoreProperties.TITLE));
      assertEquals("This is a test message", metadata.get(Metadata.SUBJECT));
   }
   
    /**
     * Check the Rtf and Attachments are returned
     *  as expected
     */
    public void testBodyAndAttachments() throws Exception {
       ContainerExtractor extractor = new ParserContainerExtractor();
       
       // Process it with recursing
       // Will have the message body RTF and the attachments
       TrackingHandler handler = process(file, extractor, true);
       assertEquals(6, handler.filenames.size());
       assertEquals(6, handler.mediaTypes.size());
       
       // We know the filenames for all of them
       assertEquals("message.rtf", handler.filenames.get(0));
       assertEquals(MediaType.application("rtf"), handler.mediaTypes.get(0));
       
       assertEquals("quick.doc", handler.filenames.get(1));
       assertEquals(MediaType.application("msword"), handler.mediaTypes.get(1));
       
       assertEquals("quick.html", handler.filenames.get(2));
       assertEquals(MediaType.text("html"), handler.mediaTypes.get(2));
       
       assertEquals("quick.pdf", handler.filenames.get(3));
       assertEquals(MediaType.application("pdf"), handler.mediaTypes.get(3));
       
       assertEquals("quick.txt", handler.filenames.get(4));
       assertEquals(MediaType.text("plain"), handler.mediaTypes.get(4));
       
       assertEquals("quick.xml", handler.filenames.get(5));
       assertEquals(MediaType.application("xml"), handler.mediaTypes.get(5));
    }
}
