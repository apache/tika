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

package org.apache.tika.parser.pkg;


import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ZipContainerDetectorTest extends TikaTest {

    @Test
    public void testTiffWorkaround() throws Exception {
        //TIKA-2591
        ZipContainerDetector zipContainerDetector = new ZipContainerDetector();
        Metadata metadata = new Metadata();
        try (InputStream is = TikaInputStream.get(getResourceAsStream("/test-documents/testTIFF.tif"))) {
            MediaType mt = zipContainerDetector.detect(is, metadata);
            assertEquals(MediaType.image("tiff"), mt);
        }
        metadata = new Metadata();
        try (InputStream is = TikaInputStream.get(getResourceAsStream("/test-documents/testTIFF_multipage.tif"))) {
            MediaType mt = zipContainerDetector.detect(is, metadata);
            assertEquals(MediaType.image("tiff"), mt);
        }

    }
}