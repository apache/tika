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
package org.apache.tika.detect.microsoft.detect.microsoft.ooxml;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.tika.TikaTest;
import org.apache.tika.detect.microsoft.ooxml.OPCPackageDetector;
import org.apache.tika.detect.zip.ZipContainerDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import static org.apache.tika.detect.microsoft.ooxml.OPCPackageDetector.DOCX;
import static org.apache.tika.detect.microsoft.ooxml.OPCPackageDetector.PPTX;
import static org.apache.tika.detect.microsoft.ooxml.OPCPackageDetector.XLSX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class OPCPackageDetectorTest extends TikaTest {

    private final ZipContainerDetector zipContainerDetector = new OPCPackageDetector();

    @Test
    public void testDetectFile() throws Exception {
        testDetectFileType("testWORD.docx", DOCX);
        testDetectFileType("testPPT.pptx", PPTX);
        testDetectFileType("testEXCEL.xlsx", XLSX);
    }

    @Test
    public void testDetectNotSupportFileType() throws Exception {
        testDetectFileType("testZIP_corrupted_oom.zip", null);
    }

    /**
     * Use {@link OPCPackageDetector} to detect file's type.
     *
     * @param fileName  test file's name
     * @param mediaType expect result for test file's detection
     */
    private void testDetectFileType(String fileName, MediaType mediaType) throws IOException, URISyntaxException {
        File file = getResourceAsFile("/test-documents/" + fileName);
        try (TikaInputStream tis = TikaInputStream.get(file.toPath());
             ZipFile zipFile = new ZipFile(file)
        ) {
            // detect type
            MediaType type = zipContainerDetector.detect(zipFile, tis);

            if (mediaType != null) {
                // assert result is not null and equals given media type
                assertNotNull(type);
                assertEquals(type.toString(), mediaType.toString());
            } else {
                // assert result is null
                assertNull(type);
            }
        }
    }
}
