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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

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
    public void testTikaInputStreamDetection() throws Exception {
        String expectedDigest = digest("testJAR.jar");
        DefaultZipContainerDetector detector = new DefaultZipContainerDetector();

        // TikaInputStream uses file-based detection
        try (TikaInputStream tis = TikaInputStream.get(getStream("testJAR.jar"))) {
            assertExpected(detector, tis, "application/java-archive", expectedDigest);
        }
    }

    @Test
    public void testStreamingDetector() throws Exception {
        // StreamingZipContainerDetector always uses streaming, never file-based
        StreamingZipContainerDetector detector = new StreamingZipContainerDetector();
        try (TikaInputStream tis = TikaInputStream.get(getStream("testJAR.jar"))) {
            MediaType mt = detector.detect(tis, new Metadata(), new ParseContext());
            assertEquals("application/java-archive", mt.toString());
        }
    }

    private InputStream getStream(String fileName) {
        return ZipDetectionTest.class.getResourceAsStream("/test-documents/" + fileName);
    }

    private void assertExpected(Detector detector, TikaInputStream tis, String expectedMime,
                                String expectedDigest) throws IOException {
        MediaType mt = detector.detect(tis, new Metadata(), new ParseContext());
        assertEquals(expectedMime, mt.toString());
        tis.rewind();
        assertEquals(expectedDigest, digest(tis));
    }

    private String digest(String fileName) throws IOException {
        return digest(ZipDetectionTest.class.getResourceAsStream("/test-documents/" + fileName));
    }

    private String digest(InputStream is) throws IOException {
        return DigestUtils.sha256Hex(is);
    }
}
