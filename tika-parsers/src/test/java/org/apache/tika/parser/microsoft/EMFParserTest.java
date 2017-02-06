/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.microsoft;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.junit.Test;

public class EMFParserTest extends TikaTest {

    @Test
    public void testTextExtractionWindows() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testEXCEL_embeddedPDF_windows.xls");
        Metadata emfMetadata = metadataList.get(1);
        assertEquals("image/emf", emfMetadata.get(Metadata.CONTENT_TYPE));
        assertContains("<p>testPDF.pdf</p>", emfMetadata.get(RecursiveParserWrapper.TIKA_CONTENT));

        //this is just the usual embedded pdf
        Metadata pdfMetadata = metadataList.get(2);
        assertEquals("application/pdf", pdfMetadata.get(Metadata.CONTENT_TYPE));
        assertContains("is a toolkit for detecting", pdfMetadata.get(RecursiveParserWrapper.TIKA_CONTENT));

    }

    @Test
    public void testTextExtractionMac() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testEXCEL_embeddedPDF_mac.xls");
        Metadata emfMetadata = metadataList.get(2);
        assertEquals("image/emf", emfMetadata.get(Metadata.CONTENT_TYPE));
        assertContains("is a toolkit for detecting", emfMetadata.get(RecursiveParserWrapper.TIKA_CONTENT));
        //test that a space was inserted before url
        assertContains("Tika http://incubator.apache.org/tika/", emfMetadata.get(RecursiveParserWrapper.TIKA_CONTENT));
    }

    @Test
    public void testPDFExtraction() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testEXCEL_embeddedPDF_mac.xls");
        //this pdf has to be extracted from within the EMF
        //it does not exist as a standalone pdf file inside the _mac.xls file.
        Metadata pdfMetadata = metadataList.get(1);
        assertEquals("application/pdf", pdfMetadata.get(Metadata.CONTENT_TYPE));
        assertContains("is a toolkit for detecting", pdfMetadata.get(RecursiveParserWrapper.TIKA_CONTENT));
    }


}

