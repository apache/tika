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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class EMFParserTest extends TikaTest {


    @Test
    public void testTextExtractionMac() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testEXCEL_embeddedPDF_mac.xls");
        Metadata emfMetadata = metadataList.get(2);
        assertEquals("image/emf", emfMetadata.get(Metadata.CONTENT_TYPE));
        assertContains("is a toolkit for detecting",
                emfMetadata.get(TikaCoreProperties.TIKA_CONTENT));
        //test that a space was inserted before url
        assertContains("Tika http://incubator.apache.org/tika/",
                emfMetadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testIconOnly() throws Exception {
        String fullFileName = "some word doc with a very long name that should be wrapped.docx";
        //test file contributed by Ross Spencer on TIKA-3968
        List<Metadata> metadataList = getRecursiveMetadata("testEMF_iconOnlyLongFilename.emf");
        assertEquals("true", metadataList.get(0).get(EMFParser.EMF_ICON_ONLY));
        assertEquals(fullFileName, metadataList.get(0).get(EMFParser.EMF_ICON_STRING));
        assertContains("some word doc", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("a very long name that should be wrapped.docx",
                metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }


}

