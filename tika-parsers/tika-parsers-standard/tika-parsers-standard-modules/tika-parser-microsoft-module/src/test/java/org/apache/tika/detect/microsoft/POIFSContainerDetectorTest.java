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
package org.apache.tika.detect.microsoft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public class POIFSContainerDetectorTest {

    @Test
    public void testBasic() throws Exception {
        String[] files =
                new String[]{"testEXCEL.xls", "testWORD.doc", "testPPT.ppt", "testVISIO.vsd",
                        "test-outlook.msg"};
        String[] expected =
                new String[]{
                    "application/vnd.ms-excel", "application/msword", "application/vnd.ms-powerpoint",
                        "application/vnd.visio", "application/vnd.ms-outlook"
                };
        for (String fileName : files) {
            testStream(fileName, "application/x-tika-msoffice", -1);
            testStream(fileName, "application/x-tika-msoffice", 0);
            testStream(fileName, "application/x-tika-msoffice", 100);
            testTikaInputStream(fileName, "application/x-tika-msoffice", 10);
        }
        for (int i = 0; i < files.length; i++) {
            testTikaInputStream(files[i], expected[i], -1);
        }
    }

    private void testStream(String fileName, String expectedMime, int markLimit) throws IOException {
        String expectedDigest = digest(getStream(fileName));
        POIFSContainerDetector detector = new POIFSContainerDetector();
        detector.setMarkLimit(markLimit);
        try (InputStream is = getStream(fileName)) {
            assertExpected(detector, is, expectedMime, expectedDigest);
        }
    }

    private void testTikaInputStream(String fileName, String expectedMime, int markLimit) throws IOException {
        String expectedDigest = digest(getStream(fileName));
        POIFSContainerDetector detector = new POIFSContainerDetector();
        detector.setMarkLimit(markLimit);
        try (InputStream is = TikaInputStream.get(getStream(fileName))) {
            assertExpected(detector, is, expectedMime, expectedDigest);
        }
    }

    private InputStream getStream(String fileName) {
        return POIFSContainerDetectorTest.class.getResourceAsStream("/test-documents/" + fileName);
    }

    private void assertExpected(Detector detector, InputStream is, String expectedMime, String expectedDigest) throws IOException {
        MediaType mt = detector.detect(is, new Metadata());
        assertEquals(expectedMime, mt.toString());
        assertEquals(expectedDigest, digest(is));
    }

    private String digest(String fileName) throws IOException {
        return digest(POIFSContainerDetectorTest.class.getResourceAsStream("/test-documents/" + fileName));
    }

    private String digest(InputStream is) throws IOException {
        return DigestUtils.sha256Hex(is);
    }
}
