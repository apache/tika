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
       
       assertEquals("image1.png", handler.filenames.get(0));
       assertEquals(TYPE_PNG, handler.mediaTypes.get(0));

       
       // Word with 3 images
       handler = process("testWORD_3imgs.doc", extractor, false);
       assertEquals(3, handler.filenames.size());
       assertEquals(3, handler.mediaTypes.size());
       
       assertEquals("image1.png", handler.filenames.get(0));
       assertEquals("image2.jpg", handler.filenames.get(1));
       assertEquals("image3.png", handler.filenames.get(2));
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
       assertEquals("MBD0003271D.ppt", handler.filenames.get(3));
       assertEquals("MBD00032A24.doc", handler.filenames.get(4));
       // But we do know their types
       assertEquals(TYPE_EMF, handler.mediaTypes.get(0)); // Icon of embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(1)); // Icon of embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(2)); // Embedded image
       assertEquals(TYPE_PPT, handler.mediaTypes.get(3)); // Embedded office doc
       assertEquals(TYPE_DOC, handler.mediaTypes.get(4)); // Embedded office doc
       
       
       // With recursion, should get the images embedded in the office files too
       handler = process("testEXCEL_embeded.xls", extractor, true);
       assertEquals(17, handler.filenames.size());
       assertEquals(17, handler.mediaTypes.size());
       
       assertEquals(null, handler.filenames.get(0));
       assertEquals(null, handler.filenames.get(1));
       assertEquals(null, handler.filenames.get(2));
       assertEquals("MBD0003271D.ppt", handler.filenames.get(3));
       assertEquals("1", handler.filenames.get(4));
       assertEquals(null, handler.filenames.get(5));
       assertEquals("2", handler.filenames.get(6));
       assertEquals("image1.png", handler.filenames.get(7));
       assertEquals("image2.jpg", handler.filenames.get(8));
       assertEquals("image3.png", handler.filenames.get(9));
       assertEquals("image1.png", handler.filenames.get(16));

       assertEquals(TYPE_EMF, handler.mediaTypes.get(0)); // Icon of embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(1)); // Icon of embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(2)); // Embedded image
       assertEquals(TYPE_PPT, handler.mediaTypes.get(3)); // Embedded presentation
       assertEquals(TYPE_XLS, handler.mediaTypes.get(4)); // Embedded XLS
       assertEquals(TYPE_PNG, handler.mediaTypes.get(5)); // Embedded image
       assertEquals(TYPE_DOC, handler.mediaTypes.get(6)); // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(7)); // Embedded image
       assertEquals(TYPE_JPG, handler.mediaTypes.get(8)); // Embedded image
       assertEquals(TYPE_PNG, handler.mediaTypes.get(9)); // Embedded image
       assertEquals(TYPE_DOC, handler.mediaTypes.get(15)); // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(16)); // Embedded image

       // Word with .docx, powerpoint and excel
       handler = process("testWORD_embeded.doc", extractor, false);
       assertEquals(9, handler.filenames.size());
       assertEquals(9, handler.mediaTypes.size());
       
       // Filenames are a bit iffy...
       // Should really be 3*embedded pictures then 3*icons then embedded docs
       assertEquals("image1.emf", handler.filenames.get(0));
       assertEquals("image4.png", handler.filenames.get(1));
       assertEquals("image5.jpg", handler.filenames.get(2));
       assertEquals("image6.png", handler.filenames.get(3));
       assertEquals("image2.emf", handler.filenames.get(4));
       assertEquals("image3.emf", handler.filenames.get(5));
       assertEquals(null, handler.filenames.get(6));
       assertEquals("_1345471035.ppt", handler.filenames.get(7));
       assertEquals("_1345470949.xls", handler.filenames.get(8));
       
       // But we do know their types
       assertEquals(TYPE_EMF, handler.mediaTypes.get(0)); // Icon of embedded office doc?
       assertEquals(TYPE_PNG, handler.mediaTypes.get(1)); // Embedded image - logo
       assertEquals(TYPE_JPG, handler.mediaTypes.get(2)); // Embedded image - safe
       assertEquals(TYPE_PNG, handler.mediaTypes.get(3)); // Embedded image - try
       assertEquals(TYPE_EMF, handler.mediaTypes.get(4)); // Icon of embedded office doc?
       assertEquals(TYPE_EMF, handler.mediaTypes.get(5)); // Icon of embedded office doc?
       assertEquals(TYPE_DOCX, handler.mediaTypes.get(6)); // Embedded office doc
       assertEquals(TYPE_PPT, handler.mediaTypes.get(7)); // Embedded office doc
       assertEquals(TYPE_XLS, handler.mediaTypes.get(8)); // Embedded office doc
       
       
       // With recursion, should get their images too
       handler = process("testWORD_embeded.doc", extractor, true);
       assertEquals(16, handler.filenames.size());
       assertEquals(16, handler.mediaTypes.size());
       
       // We don't know their filenames, except for doc images + docx
       assertEquals("image1.emf", handler.filenames.get(0));
       assertEquals("image4.png", handler.filenames.get(1));
       assertEquals("image5.jpg", handler.filenames.get(2));
       assertEquals("image6.png", handler.filenames.get(3));
       assertEquals("image2.emf", handler.filenames.get(4));
       assertEquals("image3.emf", handler.filenames.get(5));
       assertEquals(null, handler.filenames.get(6));
       assertEquals("image2.png", handler.filenames.get(7));
       assertEquals("image3.jpeg", handler.filenames.get(8));
       assertEquals("image4.png", handler.filenames.get(9));
       for(int i=11; i<14; i++) {
          assertNull(handler.filenames.get(i));
       }
       // But we do know their types
       assertEquals(TYPE_EMF, handler.mediaTypes.get(0)); // Icon of embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(1)); // Embedded image - logo
       assertEquals(TYPE_JPG, handler.mediaTypes.get(2)); // Embedded image - safe
       assertEquals(TYPE_PNG, handler.mediaTypes.get(3)); // Embedded image - try
       assertEquals(TYPE_EMF, handler.mediaTypes.get(4)); // Icon of embedded office doc
       assertEquals(TYPE_EMF, handler.mediaTypes.get(5)); // Icon of embedded office doc
       assertEquals(TYPE_DOCX, handler.mediaTypes.get(6)); // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(7));  //    PNG inside .docx
       assertEquals(TYPE_JPG, handler.mediaTypes.get(8));  //    JPG inside .docx
       assertEquals(TYPE_PNG, handler.mediaTypes.get(9));  //    PNG inside .docx
       assertEquals(TYPE_PPT, handler.mediaTypes.get(10)); // Embedded office doc
       assertEquals(TYPE_XLS, handler.mediaTypes.get(14)); // Embedded office doc
       assertEquals(TYPE_PNG, handler.mediaTypes.get(15)); //    PNG inside .xls
       
       
       // PowerPoint with excel and word
       // TODO
       
       
       // Word, with a non-office file (PDF)
       handler = process("testWORD_embedded_pdf.doc", extractor, true);
       assertEquals(2, handler.filenames.size());
       assertEquals(2, handler.mediaTypes.size());
       
       assertEquals("image1.emf", handler.filenames.get(0));
       assertEquals("_1402837031.pdf", handler.filenames.get(1));

       assertEquals(TYPE_EMF, handler.mediaTypes.get(0)); // Icon of embedded pdf
       assertEquals(TYPE_PDF, handler.mediaTypes.get(1)); // The embedded PDF itself
       
       
       
       // Outlook with a text file and a word document
       handler = process("testMSG_att_doc.msg", extractor, true);
       assertEquals(2, handler.filenames.size());
       assertEquals(2, handler.mediaTypes.size());
       
       assertEquals("test-unicode.doc", handler.filenames.get(0));
       assertEquals(TYPE_DOC, handler.mediaTypes.get(0));
       
       assertEquals("pj1.txt", handler.filenames.get(1));
       assertEquals(TYPE_TXT, handler.mediaTypes.get(1));
       
       
       // Outlook with a pdf and another outlook message
       handler = process("testMSG_att_msg.msg", extractor, true);
       assertEquals(2, handler.filenames.size());
       assertEquals(2, handler.mediaTypes.size());
       
       assertEquals("__substg1.0_3701000D.msg", handler.filenames.get(0));
       assertEquals(TYPE_MSG, handler.mediaTypes.get(0));
       
       assertEquals("smbprn.00009008.KdcPjl.pdf", handler.filenames.get(1));
       assertEquals(TYPE_PDF, handler.mediaTypes.get(1));
    }

    public void testEmbeddedOfficeFilesXML() throws Exception {
        ContainerExtractor extractor = new ParserContainerExtractor();
        TrackingHandler handler;

        handler = process("EmbeddedDocument.docx", extractor, false);
        assertTrue(handler.filenames.contains("Microsoft_Office_Excel_97-2003_Worksheet1.bin"));
        assertEquals(2, handler.filenames.size());
    }

    public void testPowerpointImages() throws Exception {
        ContainerExtractor extractor = new ParserContainerExtractor();
        TrackingHandler handler;

        handler = process("pictures.ppt", extractor, false);
        assertTrue(handler.mediaTypes.contains(new MediaType("image", "jpeg")));
        assertTrue(handler.mediaTypes.contains(new MediaType("image", "png")));
    }
}
