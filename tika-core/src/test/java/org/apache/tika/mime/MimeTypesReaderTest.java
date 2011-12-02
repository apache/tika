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
package org.apache.tika.mime;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import junit.framework.TestCase;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;

/**
 * These tests try to ensure that the MimeTypesReader
 *  has correctly processed the mime-types.xml file.
 * To do this, it tests that various aspects of the
 *  mime-types.xml file have ended up correctly as
 *  globs, matches, magics etc.
 *  
 * If you make updates to mime-types.xml, then the
 *  checks in this test may no longer hold true.
 * As such, if tests here start failing after your
 *  changes, please review the test details, and
 *  update it to match the new state of the file! 
 */
public class MimeTypesReaderTest extends TestCase {

    private MimeTypes mimeTypes;
    private List<Magic> magics;

    @Override
    @SuppressWarnings("unchecked")
    protected void setUp() throws Exception {
        super.setUp();
        this.mimeTypes = TikaConfig.getDefaultConfig().getMimeRepository();

        Field magicsField = mimeTypes.getClass().getDeclaredField("magics");
        magicsField.setAccessible(true);
        magics = (List<Magic>)magicsField.get(mimeTypes);
    }

    public void testHtmlMatches() throws Exception {
       int minMatches = 10;

       // Check on the type
       MimeType html = mimeTypes.forName("text/html");
       assertTrue(html.hasMagic());
       assertTrue(
             "There should be at least "+minMatches+" HTML matches, found " + html.getMagics().size(),
             html.getMagics().size() >= minMatches
       );

       // Check on the overall magics
       List<Magic> htmlMagics = new ArrayList<Magic>();
       for(Magic magic : magics) {
          if(magic.getType().toString().equals("text/html")) {
             htmlMagics.add(magic);
          }
       }

       assertTrue(
             "There should be at least "+minMatches+" HTML matches, found " + htmlMagics.size(),
             htmlMagics.size() >= minMatches
       );
    }

    public void testExcelMatches() throws Exception {
       int minMatches = 4;

       // Check on the type
       MimeType excel = mimeTypes.forName("application/vnd.ms-excel");
       assertTrue(excel.hasMagic());
       assertTrue(
             "There should be at least "+minMatches+" Excel matches, found " + excel.getMagics().size(),
             excel.getMagics().size() >= minMatches
       );

       // Check on the overall magics
       List<Magic> excelMagics = new ArrayList<Magic>();
       for(Magic magic : magics) {
          if(magic.getType().toString().equals("application/vnd.ms-excel")) {
             excelMagics.add(magic);
          }
       }

       assertTrue(
             "There should be at least "+minMatches+" Excel matches, found " + excelMagics.size(),
             excelMagics.size() >= minMatches
       );
    }
    
    /**
     * @since TIKA-515
     */
    public void testReadComment() {
        try {
            assertNotNull(this.mimeTypes.forName("application/msword")
                    .getDescription());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    /**
     * TIKA-746 Ensures that the custom mimetype maps were also 
     *  loaded and used
     */
    public void testCustomMimeTypes() {
       // Check that it knows about our two special ones
       String helloWorld = "hello/world";
       String helloWorldFile = "hello/world-file";
       try {
          assertNotNull(this.mimeTypes.forName(helloWorld));
          assertNotNull(this.mimeTypes.forName(helloWorldFile));
       } catch (Exception e) {
          fail(e.getMessage());
       }
       
       // Check that the details come through as expected
       try {
          MimeType hw = this.mimeTypes.forName(helloWorld);
          MimeType hwf = this.mimeTypes.forName(helloWorldFile);
          
          // The parent has no comments, globs etc
          assertEquals("", hw.getDescription());
          assertEquals("", hw.getExtension());
          assertEquals(0, hw.getExtensions().size());
          
          // The file one does
          assertEquals("A \"Hello World\" file", hwf.getDescription());
          assertEquals(".hello.world", hwf.getExtension());
          
          // Check that we can correct detect with the file one:
          // By name
          Metadata m = new Metadata();
          m.add(Metadata.RESOURCE_NAME_KEY, "test.hello.world");
          assertEquals(hwf.toString(), this.mimeTypes.detect(null, m).toString());
          
          // By contents
          m = new Metadata();
          ByteArrayInputStream s = new ByteArrayInputStream(
                "Hello, World!".getBytes("ASCII"));
          assertEquals(hwf.toString(), this.mimeTypes.detect(s, m).toString());
       } catch (Exception e) {
          fail(e.getMessage());
       }
    }
    
    public void testGetExtensionForPowerPoint() throws Exception {
        MimeType mt = this.mimeTypes.forName("application/vnd.ms-powerpoint");
        String ext = mt.getExtension();
        assertEquals(".ppt",ext);
        assertEquals(".ppt",mt.getExtensions().get(0));
    }

}
