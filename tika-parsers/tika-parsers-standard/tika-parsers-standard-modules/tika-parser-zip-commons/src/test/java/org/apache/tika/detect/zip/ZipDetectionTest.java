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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Test cases for detecting zip-based files.
 */
public class ZipDetectionTest extends TikaTest {


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

    @Test
    public void testStreaming() throws Exception {
        String expectedDigest = digest("testJAR.jar");
        DefaultZipContainerDetector detector = new DefaultZipContainerDetector();
        try (InputStream is = TikaInputStream.get(getStream("testJAR.jar"))) {
            assertExpected(detector, is, "application/java-archive", expectedDigest);
        }

        for (int markLimit : new int[]{-1,0,10,100,1000}) {
            detector = new DefaultZipContainerDetector();
            //mark limit is ignored for a TikaInputStream
            try (InputStream is = TikaInputStream.get(getStream("testJAR.jar"))) {
                detector.setMarkLimit(markLimit);
                assertExpected(detector, is, "application/java-archive", expectedDigest);
            }
        }

        detector = new DefaultZipContainerDetector();
        //mark limit is ignored for a TikaInputStream
        try (InputStream is = TikaInputStream.get(getStream("testJAR.jar"))) {
            detector.setMarkLimit(-1);
            assertExpected(detector, is, "application/java-archive", expectedDigest);
        }

        detector = new DefaultZipContainerDetector();
        //try on a file that isn't a TikaInputStream
        try (InputStream is = new BufferedInputStream(Files.newInputStream(TikaInputStream.get(getStream("testJAR.jar")).getPath()))) {
            assertExpected(detector, is, "application/java-archive", expectedDigest);
        }

        detector = new DefaultZipContainerDetector();
        try (InputStream is = ZipDetectionTest.class.getResourceAsStream("/test-documents/testJAR.jar")) {
            assertExpected(detector, is, "application/java-archive", expectedDigest);
        }

        detector = new DefaultZipContainerDetector();
        detector.setMarkLimit(100);
        try (InputStream is = ZipDetectionTest.class.getResourceAsStream("/test-documents/testJAR.jar")) {
            assertExpected(detector, is, "application/zip", expectedDigest);
        }

        detector = new DefaultZipContainerDetector();
        detector.setMarkLimit(0);
        try (InputStream is = ZipDetectionTest.class.getResourceAsStream("/test-documents/testJAR.jar")) {
            assertExpected(detector, is, "application/zip", expectedDigest);
        }

        detector = new DefaultZipContainerDetector();
        detector.setMarkLimit(100000);
        try (InputStream is = ZipDetectionTest.class.getResourceAsStream("/test-documents/testJAR.jar")) {
            assertExpected(detector, is, "application/java-archive", expectedDigest);
        }
    }

    private InputStream getStream(String fileName) {
        return ZipDetectionTest.class.getResourceAsStream("/test-documents/" + fileName);
    }

    private void assertExpected(Detector detector, InputStream is, String expectedMime, String expectedDigest) throws IOException {
        MediaType mt = detector.detect(is, new Metadata());
        assertEquals(expectedMime, mt.toString());
        assertEquals(expectedDigest, digest(is));

    }

    private String digest(String fileName) throws IOException {
        return digest(ZipDetectionTest.class.getResourceAsStream("/test-documents/" + fileName));
    }

    private String digest(InputStream is) throws IOException {
        return DigestUtils.sha256Hex(is);
    }

}
