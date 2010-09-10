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
package org.apache.tika.parser.microsoft.ooxml;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.ContainerAwareDetector;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.microsoft.AbstractPOIContainerExtractionTest;

/**
 * Tests that the various POI OOXML powered parsers are
 *  able to extract their embedded contents.
 */
public class OOXMLContainerExtractionTest extends AbstractPOIContainerExtractionTest {
    private ContainerExtractor extractor;
    
    @Override
    protected void setUp() throws Exception {
       ContainerAwareDetector detector = new ContainerAwareDetector(
             (new TikaConfig()).getMimeRepository()
       );
       extractor = new ParserContainerExtractor(
             new AutoDetectParser(detector), detector
       );
    }

   /**
     * For office files which don't have anything embedded in them
     */
    public void testWithoutEmbedded() throws Exception {
       String[] files = new String[] {
             "testEXCEL.xlsx", "testWORD.docx", "testPPT.pptx",
       };
       for(String file : files) {
          // Process it without recursing
          TrackingHandler handler = process(file, extractor, false);
          
          // Won't have fired
          assertEquals(0, handler.filenames.size());
          assertEquals(0, handler.mediaTypes.size());
          
          // Ditto with recursing
          handler = process(file, extractor, true);
          assertEquals(0, handler.filenames.size());
          assertEquals(0, handler.mediaTypes.size());
       }
    }
    
    /**
     * Office files with embedded images, but no other
     *  office files in them
     */
    public void testEmbeddedImages() throws Exception {
       TrackingHandler handler;
       
       // Excel with 1 image
       handler = process("testEXCEL_1img.xlsx", extractor, false);
       assertEquals(1, handler.filenames.size());
       assertEquals(1, handler.mediaTypes.size());
       
       assertEquals(null, handler.filenames.get(0));
       assertEquals(TYPE_PNG, handler.mediaTypes.get(0));

       
       // PowerPoint with 2 images + sound
       // TODO
       
       // Word with 1 image
       handler = process("testWORD_1img.docx", extractor, false);
       assertEquals(1, handler.filenames.size());
       assertEquals(1, handler.mediaTypes.size());
       
       assertEquals(null, handler.filenames.get(0));
       assertEquals(TYPE_PNG, handler.mediaTypes.get(0));

       
       // Word with 3 images
       handler = process("testWORD_3imgs.docx", extractor, false);
       assertEquals(3, handler.filenames.size());
       assertEquals(3, handler.mediaTypes.size());
       
       assertEquals(null, handler.filenames.get(0));
       assertEquals(null, handler.filenames.get(1));
       assertEquals(null, handler.filenames.get(2));
       assertEquals(TYPE_PNG, handler.mediaTypes.get(0));
       assertEquals(TYPE_JPG, handler.mediaTypes.get(1));
       assertEquals(TYPE_PNG, handler.mediaTypes.get(2));
    }
    
    /**
     * Office files which have other office files
     *  embedded into them. The embedded office files
     *  will sometimes have images in them.
     *  
     *  eg xls
     *       -> word
     *           -> image
     *           -> image
     *       -> powerpoint
     *       -> excel
     *           -> image
     */
    public void testEmbeddedOfficeFiles() throws Exception {
       TrackingHandler handler;
       
       
       // Excel with a word doc and a powerpoint doc, both of which have images in them
       // Without recursion, should see both documents + the images
       handler = process("testEXCEL_embeded.xlsx", extractor, false);
       assertEquals(7, handler.filenames.size());
       assertEquals(7, handler.mediaTypes.size());
       
       // We don't know their filenames
       for(String filename : handler.filenames)
          assertEquals(null, filename);
       // But we do know their types
       assertEquals(TYPE_PPTX, handler.mediaTypes.get(0)); // Embedded office doc
       assertEquals(TYPE_DOC, handler.mediaTypes.get(1));  // Embedded office doc
       assertEquals(TYPE_DOCX, handler.mediaTypes.get(2)); // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(3)); // Embedded image
       assertEquals(TYPE_EMF, handler.mediaTypes.get(4)); // Icon of embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(5)); // Icon of embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(6)); // Icon of embedded office doc
       
       
       // With recursion, should get the images embedded in the office files too
       handler = process("testEXCEL_embeded.xlsx", extractor, true);
       assertEquals(9, handler.filenames.size());
       assertEquals(9, handler.mediaTypes.size());
       
       for(String filename : handler.filenames)
          assertEquals(null, filename);
       
       assertEquals(TYPE_PPTX, handler.mediaTypes.get(0)); // Embedded office doc
       assertEquals(TYPE_DOC, handler.mediaTypes.get(1));  // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(2));  //   PNG inside .doc
       assertEquals(TYPE_DOCX, handler.mediaTypes.get(3)); // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(4));  //   PNG inside .docx
       assertEquals(TYPE_PNG, handler.mediaTypes.get(5)); // Embedded image
       assertEquals(TYPE_EMF, handler.mediaTypes.get(6)); // Icon of embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(7)); // Icon of embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(8)); // Icon of embedded office doc
       
       
       // Word with .docx, powerpoint and excel
       handler = process("testWORD_embeded.docx", extractor, false);
       assertEquals(9, handler.filenames.size());
       assertEquals(9, handler.mediaTypes.size());
       
       // We don't know their filenames
       for(String filename : handler.filenames)
          assertEquals(null, filename);
       // But we do know their types
       assertEquals(TYPE_PPTX, handler.mediaTypes.get(0)); // Embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(1));  // Icon of embedded office doc
       assertEquals(TYPE_DOC, handler.mediaTypes.get(2));  // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(3));  // Embedded image
       assertEquals(TYPE_JPG, handler.mediaTypes.get(4));  // Embedded image
       assertEquals(TYPE_PNG, handler.mediaTypes.get(5));  // Embedded image
       assertEquals(TYPE_EMF, handler.mediaTypes.get(6));  // Icon of embedded office doc 
       assertEquals(TYPE_XLSX, handler.mediaTypes.get(7)); // Embeded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(8));  // Icon of embedded office doc
       
       
       // With recursion, should get their images too
       handler = process("testWORD_embeded.docx", extractor, true);
       assertEquals(11, handler.filenames.size());
       assertEquals(11, handler.mediaTypes.size());
       
       // We don't know their filenames
       for(String filename : handler.filenames)
          assertEquals(null, filename);
       // But we do know their types
       assertEquals(TYPE_PPTX, handler.mediaTypes.get(0)); // Embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(1));  // Icon of embedded office doc
       assertEquals(TYPE_DOC, handler.mediaTypes.get(2));  // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(3));  //   PNG inside .doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(4));  // Embedded image
       assertEquals(TYPE_JPG, handler.mediaTypes.get(5));  // Embedded image
       assertEquals(TYPE_PNG, handler.mediaTypes.get(6));  // Embedded image
       assertEquals(TYPE_EMF, handler.mediaTypes.get(7));  // Icon of embedded office doc 
       assertEquals(TYPE_XLSX, handler.mediaTypes.get(8)); // Embeded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(9));  //   PNG inside .xlsx
       assertEquals(TYPE_EMF, handler.mediaTypes.get(10)); // Icon of embedded office doc
       
       // PowerPoint with excel and word
       // TODO
    }
}
