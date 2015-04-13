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
package org.apache.tika.parser.odf;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;


public class OpenDocumentParserTest extends TikaTest {
  
  @Test
  public void can_parse_odt_file_with_styles_tika_6000() throws Exception {
      Parser parser = new OpenDocumentParser();
      InputStream input = ODFParserTest.class.getResourceAsStream("/test-documents/testODT-TIKA-6000.odt");
      try {
         Metadata metadata = new Metadata();
         ContentHandler handler = new BodyContentHandler();
         parser.parse(input, handler, metadata, new ParseContext());

         assertEquals("application/vnd.oasis.opendocument.text", metadata.get(Metadata.CONTENT_TYPE));

         String content = handler.toString();
         
         assertContains("Utilisation de ce document", content);
         assertContains("Copyright and License", content);
         assertContains("Changer la langue", content);
         assertContains("La page d’accueil permet de faire une recherche simple", content);                
      } finally {
         input.close();
      }
  }

}
