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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class VSDXParserTest extends TikaTest {

    @Test
    public void testBasicTextExtraction() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testVISIO.vsdx");
        String content = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        assertEquals("application/vnd.ms-visio.drawing",
                metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertContains("test", content);
        assertContains("This is a test.", content);
        assertContains("Nothing fancy.", content);
    }

    @Test
    public void testTextExtraction() throws Exception {
        // POI test file with multiple shapes containing text
        List<Metadata> metadataList = getRecursiveMetadata("testVISIO_text.vsdx");
        String content = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("Text here", content);
        assertContains("Text there", content);
        assertContains("Text, text, everywhere!", content);
        assertContains("Router here", content);
    }

    @Test
    public void testRealWorldVSDX() throws Exception {
        // POI test file 60489 - real-world Visio diagram
        List<Metadata> metadataList = getRecursiveMetadata("testVISIO_60489.vsdx");
        String content = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("HousingConnections.ca", content);
        assertContains("Content Authors", content);
        assertContains("Submit Application", content);
    }
}
