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
package org.apache.tika.detect.zip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Test case for detecting the zip-based GeoGebra formats by their contents.
 * The streams are parsed without a resource name, so detection must rely on
 * the zip entry names, not the *.ggb/*.ggs/*.ggt globs.
 */
public class GeoGebraDetectionTest extends TikaTest {

    private List<Metadata> getRecursiveMetadataWithoutName(String fileName) throws Exception {
        InputStream is = getClass().getResourceAsStream("/test-documents/" + fileName);
        assertNotNull(is, "missing test resource " + fileName);
        try (TikaInputStream tis = TikaInputStream.get(is, new Metadata())) {
            return getRecursiveMetadata(tis, AUTO_DETECT_PARSER, new Metadata(),
                    new ParseContext(), true);
        }
    }

    @Test
    public void testGGBDetection() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadataWithoutName("testGeoGebra.ggb");
        assertEquals("application/vnd.geogebra.file",
                metadataList.get(0).get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testGGSDetection() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadataWithoutName("testGeoGebraSlides.ggs");
        assertEquals("application/vnd.geogebra.slides",
                metadataList.get(0).get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testGGTDetection() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadataWithoutName("testGeoGebraTool.ggt");
        assertEquals("application/vnd.geogebra.tool",
                metadataList.get(0).get(HttpHeaders.CONTENT_TYPE));
    }
}
