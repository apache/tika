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
package org.apache.tika.parser.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class TSDParserTest extends TikaTest {

    @Test
    public void testBrokenPdf() throws Exception {
        //make sure that embedded file appears in list
        //and make sure embedded exception is recorded
        List<Metadata> list = getRecursiveMetadata("testTSD_broken_pdf.tsd");
        assertEquals(2, list.size());
        assertEquals("application/pdf", list.get(1).get(Metadata.CONTENT_TYPE));
        assertNotNull(list.get(1).get(TikaCoreProperties.EMBEDDED_EXCEPTION));
        assertContains("org.apache.pdfbox.pdmodel.PDDocument.load",
                list.get(1).get(TikaCoreProperties.EMBEDDED_EXCEPTION));
    }

    @Test
    public void testToXML() throws Exception {
        String xml = getXML("Test4.pdf.tsd").xml;
        assertContains("Empty doc", xml);
    }
}
