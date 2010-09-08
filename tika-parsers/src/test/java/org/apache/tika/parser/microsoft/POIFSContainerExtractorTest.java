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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.apache.tika.extractor.ContainerEmbededResourceHandler;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

public class POIFSContainerExtractorTest extends TestCase {
    private static final MediaType TYPE_DOC = MediaType.application("msword");
    private static final MediaType TYPE_PPT = MediaType.application("vnd.ms-powerpoint");
    private static final MediaType TYPE_XLS = MediaType.application("vnd.ms-excel");
   
    /**
     * For office files which don't have anything embeded in them
     */
    public void testWithoutEmbeded() throws Exception {
       POIFSContainerExtractor extractor = new POIFSContainerExtractor();
       
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
     * Office files with embeded images, but no other
     *  office files in them
     */
    public void testEmbededImages() throws Exception {
       POIFSContainerExtractor extractor = new POIFSContainerExtractor();
       TrackingHandler handler;
       
       // Excel with 1 image
       handler = process("testEXCEL_1img.xls", extractor, false);
       // TODO
       assertEquals(0, handler.filenames.size());
       assertEquals(0, handler.mediaTypes.size());
       
       // PowerPoint with 2 images + sound
       // TODO
       
       // Word with 1 image
       // TODO
       
       // Word with 3 images
       // TODO
    }
    
    /**
     * Office files which have other office files
     *  embeded into them. The embeded office files
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
    public void testEmbededOfficeFiles() throws Exception {
       POIFSContainerExtractor extractor = new POIFSContainerExtractor();
       TrackingHandler handler;
       
       // Excel with a word doc and a powerpoint doc, both of which have images in them
       // Without recursion, should see both
       handler = process("testEXCEL_embeded.xls", extractor, false);
       assertEquals(2, handler.filenames.size());
       assertEquals(2, handler.mediaTypes.size());
       
       // We don't know their filenames
       assertEquals(null, handler.filenames.get(0));
       assertEquals(null, handler.filenames.get(1));
       // But we do know their types
       assertEquals(TYPE_PPT, handler.mediaTypes.get(0));
       assertEquals(TYPE_DOC, handler.mediaTypes.get(1));
       
       // With recursion, should get their images too
       handler = process("testEXCEL_embeded.xls", extractor, true);
       // TODO
       
       
       // Word with .docx, powerpoint and excel
       handler = process("testWORD_embeded.doc", extractor, false);
       assertEquals(3, handler.filenames.size());
       assertEquals(3, handler.mediaTypes.size());
       
       // We don't know their filenames
       assertEquals(null, handler.filenames.get(0));
       assertEquals(null, handler.filenames.get(1));
       assertEquals(null, handler.filenames.get(2));
       // But we do know their types
       assertEquals(MediaType.application("x-tika-msoffice"), handler.mediaTypes.get(0)); // TODO
       assertEquals(TYPE_PPT, handler.mediaTypes.get(1));
       assertEquals(TYPE_XLS, handler.mediaTypes.get(2));
       
       // With recursion, should get their images too
       handler = process("testWORD_embeded.doc", extractor, true);
       // TODO
       
       
       // PowerPoint with excel and word
       // TODO
       
       
       // Outlook with a text file and a word document
       // TODO
       
       
       // Outlook with a pdf and another outlook message
       // TODO
    }
    
    private TrackingHandler process(String filename, ContainerExtractor extractor, boolean recurse) throws Exception {
       InputStream input = POIFSContainerExtractorTest.class.getResourceAsStream(
             "/test-documents/" + filename);
        TikaInputStream stream = TikaInputStream.get(input);
        
        assertEquals(true, extractor.isSupported(stream));
        
        // Process it
        TrackingHandler handler = new TrackingHandler();
        extractor.extract(stream, null, handler);
        
        // So they can check what happened
        return handler;
    }
    
    private static class TrackingHandler implements ContainerEmbededResourceHandler {
       private List<String> filenames = new ArrayList<String>();
       private List<MediaType> mediaTypes = new ArrayList<MediaType>();
       
       public void handle(String filename, MediaType mediaType,
            InputStream stream) {
          filenames.add(filename);
          mediaTypes.add(mediaType);
      }
    }
}
