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
package org.apache.tika.parser.prt;

import java.io.InputStream;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class PRTParserTest extends TikaTest {
    /**
     * Try with a simple file
     */
    public void testPRTParserBasics() throws Exception {
       InputStream input = getResourceAsStream("/test-documents/testCADKEY.prt");
       try  {
          Metadata metadata = new Metadata();
          ContentHandler handler = new BodyContentHandler();
          new PRTParser().parse(input, handler, metadata);

          assertEquals("application/x-prt", metadata.get(Metadata.CONTENT_TYPE));

          // This file has a date
          assertEquals("2011-06-20T16:54:00",
                metadata.get(TikaCoreProperties.CREATED));
          assertEquals("2011-06-20T16:54:00",
                metadata.get(Metadata.CREATION_DATE));
          // But no description
          assertEquals(null, metadata.get(TikaCoreProperties.DESCRIPTION));

          String contents = handler.toString();
          
          assertContains("Front View", contents);
          assertContains("Back View", contents);
          assertContains("Bottom View", contents);
          assertContains("Right View", contents);
          assertContains("Left View", contents);
          //assertContains("Isometric View", contents); // Can't detect yet
          assertContains("Axonometric View", contents);

          assertContains("You've managed to extract all the text!", contents);
          assertContains("This is more text", contents);
          assertContains("Text Inside a PRT file", contents);
       } finally {
          input.close();
       }
    }

    /**
     * Now a more complex one
     */
    public void testPRTParserComplex() throws Exception {
       InputStream input = getResourceAsStream("/test-documents/testCADKEY2.prt");
       try  {
          Metadata metadata = new Metadata();
          ContentHandler handler = new BodyContentHandler();
          new PRTParser().parse(input, handler, metadata);

          assertEquals("application/x-prt", metadata.get(Metadata.CONTENT_TYPE));

          // File has both a date and a description
          assertEquals("1997-04-01T08:59:00",
                metadata.get(Metadata.DATE));
          assertEquals("1997-04-01T08:59:00",
                metadata.get(Metadata.CREATION_DATE));
          assertEquals("TIKA TEST PART DESCRIPTION INFORMATION\r\n",
                metadata.get(TikaCoreProperties.DESCRIPTION));

          String contents = handler.toString();
          
          assertContains("ITEM", contents);
          assertContains("REQ.", contents);
          assertContains("DESCRIPTION", contents);
          assertContains("MAT'L", contents);
          assertContains("TOLERANCES UNLESS", contents);
          assertContains("FRACTIONS", contents);
          assertContains("ANGLES", contents);
          assertContains("Acme Corporation", contents);

          assertContains("DATE", contents);
          assertContains("CHANGE", contents);
          assertContains("DRAWN BY", contents);
          assertContains("SCALE", contents);
          assertContains("TIKA TEST DRAWING", contents);
          assertContains("TIKA LETTERS", contents);
          assertContains("5.82", contents);
          assertContains("112"+'\u00b0', contents); // Degrees
          assertContains("TIKA TEST LETTER", contents);
          assertContains("17.11", contents);
          assertContains('\u00d8'+"\ufffd2.000", contents); // Diameter
          assertContains("Diameter", contents);
          assertContains("The Apache Tika toolkit", contents);
       } finally {
          input.close();
       }
    }
}
