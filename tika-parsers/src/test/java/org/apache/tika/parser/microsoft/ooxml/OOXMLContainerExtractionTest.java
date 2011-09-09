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
package org.apache.tika.parser.microsoft.ooxml;

import org.apache.tika.Tika;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.parser.microsoft.AbstractPOIContainerExtractionTest;

/**
 * Tests that the various POI OOXML powered parsers are
 *  able to extract their embedded contents.
 */
public class OOXMLContainerExtractionTest extends AbstractPOIContainerExtractionTest {
    private ContainerExtractor extractor;
    
    @Override
    protected void setUp() throws Exception {
        Tika tika = new Tika();
        extractor = new ParserContainerExtractor(
                tika.getParser(), tika.getDetector());
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
       
       assertEquals("image1.png", handler.filenames.get(0));
       assertEquals(TYPE_PNG, handler.mediaTypes.get(0));

       
       // PowerPoint with 2 images + sound
       // TODO Figure out why we can't find the sound anywhere...
       handler = process("testPPT_2imgs.pptx", extractor, false);
       assertEquals(3, handler.filenames.size());
       assertEquals(3, handler.mediaTypes.size());
       
       assertEquals("image1.png", handler.filenames.get(0));
       assertEquals("image2.gif", handler.filenames.get(1));
       assertEquals("image3.png", handler.filenames.get(2));
       assertEquals(TYPE_PNG, handler.mediaTypes.get(0));
       assertEquals(TYPE_GIF, handler.mediaTypes.get(1)); // icon of sound
       assertEquals(TYPE_PNG, handler.mediaTypes.get(2));
       
       
       // Word with 1 image
       handler = process("testWORD_1img.docx", extractor, false);
       assertEquals(1, handler.filenames.size());
       assertEquals(1, handler.mediaTypes.size());
       
       assertEquals("image1.png", handler.filenames.get(0));
       assertEquals(TYPE_PNG, handler.mediaTypes.get(0));

       
       // Word with 3 images
       handler = process("testWORD_3imgs.docx", extractor, false);
       assertEquals(3, handler.filenames.size());
       assertEquals(3, handler.mediaTypes.size());
       
       assertEquals("image2.png", handler.filenames.get(0));
       assertEquals("image3.jpeg", handler.filenames.get(1));
       assertEquals("image4.png", handler.filenames.get(2));
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
       
       // We know the rough filenames
       assertEquals("Microsoft_Office_PowerPoint_Presentation1.pptx", handler.filenames.get(0));
       assertEquals("Microsoft_Office_Word_97_-_2003_Document1.doc", handler.filenames.get(1));
       assertEquals("Microsoft_Office_Word_Document2.docx", handler.filenames.get(2));
       assertEquals("image1.png", handler.filenames.get(3));
       assertEquals("image2.emf", handler.filenames.get(4));
       assertEquals("image3.emf", handler.filenames.get(5));
       assertEquals("image4.emf", handler.filenames.get(6));
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
       assertEquals(23, handler.filenames.size());
       assertEquals(23, handler.mediaTypes.size());
       
       assertEquals(TYPE_PPTX, handler.mediaTypes.get(0)); // Embedded office doc
        assertEquals(TYPE_PNG, handler.mediaTypes.get(1));  //   PNG inside .pptx
        assertEquals(TYPE_GIF, handler.mediaTypes.get(2));  //   PNG inside .pptx
        assertEquals(TYPE_PNG, handler.mediaTypes.get(3));  //   PNG inside .pptx
        assertEquals(TYPE_XLSX, handler.mediaTypes.get(4)); //   .xlsx inside .pptx
         assertEquals(TYPE_PNG, handler.mediaTypes.get(5)); //     PNG inside .xlsx inside .pptx
        assertEquals(TYPE_DOCX, handler.mediaTypes.get(6)); //   .docx inside .pptx
         assertEquals(TYPE_PNG, handler.mediaTypes.get(7)); //     PNG inside .docx inside .pptx
         assertEquals(TYPE_JPG, handler.mediaTypes.get(8)); //     JPG inside .docx inside .pptx
         assertEquals(TYPE_PNG, handler.mediaTypes.get(9)); //     PNG inside .docx inside .pptx
        assertEquals(TYPE_DOC, handler.mediaTypes.get(10)); //   .doc inside .pptx
         assertEquals(TYPE_PNG, handler.mediaTypes.get(11)); //    PNG inside .doc inside .pptx
        assertEquals(TYPE_EMF, handler.mediaTypes.get(12)); //   Icon of item inside .pptx
        assertEquals(TYPE_EMF, handler.mediaTypes.get(13)); //   Icon of item inside .pptx
        assertEquals(TYPE_EMF, handler.mediaTypes.get(14)); //   Icon of item inside .pptx
       assertEquals(TYPE_DOC, handler.mediaTypes.get(15));  // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(16));  //   PNG inside .doc
       assertEquals(TYPE_DOCX, handler.mediaTypes.get(17)); // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(18));  //   PNG inside .docx
       assertEquals(TYPE_PNG, handler.mediaTypes.get(19)); // Embedded image
       assertEquals(TYPE_EMF, handler.mediaTypes.get(20)); // Icon of embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(21)); // Icon of embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(22)); // Icon of embedded office doc
       
       
       // Word with .docx, powerpoint and excel
       handler = process("testWORD_embeded.docx", extractor, false);
       assertEquals(9, handler.filenames.size());
       assertEquals(9, handler.mediaTypes.size());
       
       // We know their rough filenames
       assertEquals("Microsoft_Office_PowerPoint_Presentation2.pptx", handler.filenames.get(0));
       assertEquals("image6.emf", handler.filenames.get(1));
       assertEquals("Microsoft_Office_Word_97_-_2003_Document1.doc", handler.filenames.get(2));
       assertEquals("image1.png", handler.filenames.get(3));
       assertEquals("image2.jpeg", handler.filenames.get(4));
       assertEquals("image3.png", handler.filenames.get(5));
       assertEquals("image4.emf", handler.filenames.get(6));
       assertEquals("Microsoft_Office_Excel_Worksheet1.xlsx", handler.filenames.get(7));
       assertEquals("image5.emf", handler.filenames.get(8));
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
       assertEquals(14, handler.filenames.size());
       assertEquals(14, handler.mediaTypes.size());
       
       // But we do know their types
       assertEquals(TYPE_PPTX, handler.mediaTypes.get(0)); // Embedded office doc
        assertEquals(TYPE_PNG, handler.mediaTypes.get(1));  //   PNG inside .pptx
        assertEquals(TYPE_GIF, handler.mediaTypes.get(2));  //   GIF inside .pptx
        assertEquals(TYPE_PNG, handler.mediaTypes.get(3));  //   PNG inside .pptx
       assertEquals(TYPE_EMF, handler.mediaTypes.get(4));  // Icon of embedded office doc
       assertEquals(TYPE_DOC, handler.mediaTypes.get(5));  // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(6));  //   PNG inside .doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(7));  // Embedded image
       assertEquals(TYPE_JPG, handler.mediaTypes.get(8));  // Embedded image
       assertEquals(TYPE_PNG, handler.mediaTypes.get(9));  // Embedded image
       assertEquals(TYPE_EMF, handler.mediaTypes.get(10));  // Icon of embedded office doc 
       assertEquals(TYPE_XLSX, handler.mediaTypes.get(11)); // Embeded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(12));  //   PNG inside .xlsx
       assertEquals(TYPE_EMF, handler.mediaTypes.get(13)); // Icon of embedded office doc
       
       
       // PowerPoint with excel and word
       handler = process("testPPT_embeded.pptx", extractor, false);
       assertEquals(9, handler.filenames.size());
       assertEquals(9, handler.mediaTypes.size());
       
       // We don't know their exact filenames
       assertEquals("image4.png", handler.filenames.get(0));
       assertEquals("image5.gif", handler.filenames.get(1));
       assertEquals("image6.png", handler.filenames.get(2));
       assertEquals("Microsoft_Office_Excel_Worksheet1.xlsx", handler.filenames.get(3));
       assertEquals("Microsoft_Office_Word_Document2.docx", handler.filenames.get(4));
       assertEquals("Microsoft_Office_Word_97_-_2003_Document1.doc", handler.filenames.get(5));
       assertEquals("image1.emf", handler.filenames.get(6));
       assertEquals("image2.emf", handler.filenames.get(7));
       assertEquals("image3.emf", handler.filenames.get(8));
       // But we do know their types
       assertEquals(TYPE_PNG, handler.mediaTypes.get(0));  // Embedded image
       assertEquals(TYPE_GIF, handler.mediaTypes.get(1));  // Embedded image
       assertEquals(TYPE_PNG, handler.mediaTypes.get(2));  // Embedded image
       assertEquals(TYPE_XLSX, handler.mediaTypes.get(3)); // Embedded office doc
       assertEquals(TYPE_DOCX, handler.mediaTypes.get(4)); // Embedded office doc
       assertEquals(TYPE_DOC, handler.mediaTypes.get(5));  // Embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(6));  // Icon of embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(7));  // Icon of embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(8));  // Icon of embedded office doc
    }

    public void testEmbeddedOutlook() throws Exception {
        TrackingHandler handler =
                process("EmbeddedOutlook.docx", extractor, false);

        assertEquals(2, handler.filenames.size());
        assertEquals(2, handler.mediaTypes.size());

        assertEquals("image1.emf", handler.filenames.get(0));
        assertEquals(TYPE_EMF, handler.mediaTypes.get(0));

        assertEquals("licensedTestMsgwAtt.msg", handler.filenames.get(1));
        assertEquals(TYPE_MSG, handler.mediaTypes.get(1));
    }

    public void testEmbeddedPDF() throws Exception {
        TrackingHandler handler =
                process("EmbeddedPDF.docx", extractor, false);

        assertEquals(2, handler.filenames.size());
        assertEquals(2, handler.mediaTypes.size());

        assertEquals("image1.emf", handler.filenames.get(0));
        assertEquals(TYPE_EMF, handler.mediaTypes.get(0));

        assertNull(handler.filenames.get(1));
        assertEquals(TYPE_PDF, handler.mediaTypes.get(1));
    }

}
