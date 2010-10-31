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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import junit.framework.TestCase;

import org.apache.tika.config.TikaConfig;

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
    private SortedSet<Magic> magics;
    private SortedSet<MimeType> xmls;

    @Override
    @SuppressWarnings("unchecked")
    protected void setUp() throws Exception {
        super.setUp();
        this.mimeTypes = TikaConfig.getDefaultConfig().getMimeRepository();

        Field magicsField = mimeTypes.getClass().getDeclaredField("magics");
        magicsField.setAccessible(true);
        magics = (SortedSet<Magic>)magicsField.get(mimeTypes);

        Field xmlsField = mimeTypes.getClass().getDeclaredField("xmls");
        xmlsField.setAccessible(true);
        xmls = (SortedSet<MimeType>)xmlsField.get(mimeTypes);
    }

    public void testHtmlMatches() throws Exception {
       int minMatches = 10;

       // Check on the type
       MimeType html = mimeTypes.forName("text/html");
       assertTrue(html.hasMagic());
       assertTrue(
             "There should be at least "+minMatches+" HTML matches, found " + html.getMagics().length,
             html.getMagics().length >= minMatches
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
             "There should be at least "+minMatches+" Excel matches, found " + excel.getMagics().length,
             excel.getMagics().length >= minMatches
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

}
