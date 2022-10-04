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
package org.apache.tika.parser.ocr;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class TestOCR extends TikaTest {

    @BeforeAll
    public static void checkTesseract() throws Exception {
        TesseractOCRParser p = new TesseractOCRParser();
        assumeTrue(p.hasTesseract());
    }

    @Test
    public void testJPEG() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testOCR.jpg");
        assertContains("OCR Testing", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testPNG() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testOCR.png");
        assertContains("file contains", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }
}
