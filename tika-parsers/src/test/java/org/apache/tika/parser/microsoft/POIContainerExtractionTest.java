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

import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.mime.MediaType;

/**
 * Tests that the various POI powered parsers are
 *  able to extract their embedded contents.
 */
public class POIContainerExtractionTest extends AbstractPOIContainerExtractionTest {
   
    /**
     * For office files which don't have anything embedded in them
     */
    public void testWithoutEmbedded() throws Exception {
       ContainerExtractor extractor = new ParserContainerExtractor();
       
       String[] files = new String[] {
             "testEXCEL.xls", "testWORD.doc", "testPPT.ppt",
             "testVISIO.vsd", "test-outlook.msg"
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
       ContainerExtractor extractor = new ParserContainerExtractor();
       TrackingHandler handler;
       
       // Excel with 1 image
       handler = process("testEXCEL_1img.xls", extractor, false);
       assertEquals(1, handler.filenames.size());
       assertEquals(1, handler.mediaTypes.size());
       
       assertEquals(null, handler.filenames.get(0));
       assertEquals(TYPE_PNG, handler.mediaTypes.get(0));

       
       // PowerPoint with 2 images + sound
       // TODO
       
       // Word with 1 image
       handler = process("testWORD_1img.doc", extractor, false);
       assertEquals(1, handler.filenames.size());
       assertEquals(1, handler.mediaTypes.size());
       
       assertEquals(null, handler.filenames.get(0));
       assertEquals(TYPE_PNG, handler.mediaTypes.get(0));

       
       // Word with 3 images
       handler = process("testWORD_3imgs.doc", extractor, false);
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
       ContainerExtractor extractor = new ParserContainerExtractor();
       TrackingHandler handler;
       
       
       // Excel with a word doc and a powerpoint doc, both of which have images in them
       // Without recursion, should see both documents + the images
       handler = process("testEXCEL_embeded.xls", extractor, false);
       assertEquals(5, handler.filenames.size());
       assertEquals(5, handler.mediaTypes.size());
       
       // We don't know their filenames
       assertEquals(null, handler.filenames.get(0));
       assertEquals(null, handler.filenames.get(1));
       assertEquals(null, handler.filenames.get(2));
       assertEquals(null, handler.filenames.get(3));
       assertEquals(null, handler.filenames.get(4));
       // But we do know their types
       assertEquals(TYPE_EMF, handler.mediaTypes.get(0)); // Icon of embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(1)); // Icon of embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(2)); // Embedded image
       assertEquals(TYPE_PPT, handler.mediaTypes.get(3)); // Embedded office doc
       assertEquals(TYPE_DOC, handler.mediaTypes.get(4)); // Embedded office doc
       
       
       // With recursion, should get the images embedded in the office files too
       handler = process("testEXCEL_embeded.xls", extractor, true);
       assertEquals(6, handler.filenames.size());
       assertEquals(6, handler.mediaTypes.size());
       
       assertEquals(null, handler.filenames.get(0));
       assertEquals(null, handler.filenames.get(1));
       assertEquals(null, handler.filenames.get(2));
       assertEquals(null, handler.filenames.get(3));
       assertEquals(null, handler.filenames.get(4));
       assertEquals(null, handler.filenames.get(5));
       
       assertEquals(TYPE_EMF, handler.mediaTypes.get(0)); // Icon of embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(1)); // Icon of embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(2)); // Embedded image
       assertEquals(TYPE_PPT, handler.mediaTypes.get(3)); // Embedded office doc
       assertEquals(TYPE_DOC, handler.mediaTypes.get(4)); // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(5)); //   PNG inside .doc
       
       
       // Word with .docx, powerpoint and excel
       handler = process("testWORD_embeded.doc", extractor, false);
       assertEquals(8, handler.filenames.size());
       assertEquals(8, handler.mediaTypes.size());
       
       // We don't know their filenames
       for(String filename : handler.filenames)
          assertEquals(null, filename);
       // But we do know their types
       assertEquals(MediaType.parse("image/unknown"), handler.mediaTypes.get(0)); // Icon of embedded office doc?
       assertEquals(MediaType.parse("image/unknown"), handler.mediaTypes.get(1)); // Icon of embedded office doc?
       assertEquals(TYPE_PNG, handler.mediaTypes.get(2)); // Embedded image
       assertEquals(TYPE_JPG, handler.mediaTypes.get(3)); // Embedded image
       assertEquals(TYPE_PNG, handler.mediaTypes.get(4)); // Embedded image
       assertEquals(TYPE_DOCX, handler.mediaTypes.get(5)); // Embedded office doc
       assertEquals(TYPE_PPT, handler.mediaTypes.get(6)); // Embedded office doc
       assertEquals(TYPE_XLS, handler.mediaTypes.get(7)); // Embedded office doc
       
       
       // With recursion, should get their images too
       handler = process("testWORD_embeded.doc", extractor, true);
       // TODO - Not all resources of embedded files are currently extracted 
       assertEquals(12, handler.filenames.size());
       assertEquals(12, handler.mediaTypes.size());
       
       // We don't know their filenames
       for(String filename : handler.filenames)
          assertEquals(null, filename);
       // But we do know their types
       assertEquals(MediaType.parse("image/unknown"), handler.mediaTypes.get(0)); // Icon of embedded office doc?
       assertEquals(MediaType.parse("image/unknown"), handler.mediaTypes.get(1)); // Icon of embedded office doc?
       assertEquals(TYPE_PNG, handler.mediaTypes.get(2));  // Embedded image
       assertEquals(TYPE_JPG, handler.mediaTypes.get(3));  // Embedded image
       assertEquals(TYPE_PNG, handler.mediaTypes.get(4));  // Embedded image
       assertEquals(TYPE_DOCX, handler.mediaTypes.get(5)); // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(6));  //    PNG inside .docx
       assertEquals(TYPE_JPG, handler.mediaTypes.get(7));  //    JPG inside .docx
       assertEquals(TYPE_PNG, handler.mediaTypes.get(8));  //    PNG inside .docx
       assertEquals(TYPE_PPT, handler.mediaTypes.get(9));  // Embedded office doc
       assertEquals(TYPE_XLS, handler.mediaTypes.get(10)); // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(11)); //    PNG inside .xls
       
       // PowerPoint with excel and word
       // TODO
       
       
       // Outlook with a text file and a word document
       // TODO
       
       
       // Outlook with a pdf and another outlook message
       // TODO
    }
}
