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

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;

/**
 * Test case for parsing zip files.
 */
public class ZipParserTest extends TikaTest {


    @Test
    public void testKMZDetection() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testKMZ.kmz");
        assertEquals("application/vnd.google-earth.kmz",
                metadataList.get(0).get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testJARDetection() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testJAR.jar");
        assertEquals("application/java-archive", metadataList.get(0).get(HttpHeaders.CONTENT_TYPE));
    }
}
