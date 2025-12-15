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

import org.apache.tika.TikaTest;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

public class POIFSContainerDetectorTest extends TikaTest {

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
        // With unlimited markLimit (-1), full detection is possible
        for (int i = 0; i < files.length; i++) {
            testStream(files[i], expected[i], -1);
            testTikaInputStream(files[i], expected[i], -1);
        }
        // With limited markLimit, detection falls back to generic OLE
        for (String fileName : files) {
            testStream(fileName, "application/x-tika-msoffice", 0);
            testStream(fileName, "application/x-tika-msoffice", 100);
            testTikaInputStream(fileName, "application/x-tika-msoffice", 10);
        }
    }

    private void testStream(String fileName, String expectedMime, int markLimit) throws IOException {
        String expectedDigest = digest(getStream(fileName));
        POIFSContainerDetector detector = new POIFSContainerDetector();
        detector.setMarkLimit(markLimit);
        try (TikaInputStream tis = getStream(fileName)) {
            assertExpected(detector, tis, expectedMime, expectedDigest);
        }
    }

    private void testTikaInputStream(String fileName, String expectedMime, int markLimit) throws IOException {
        String expectedDigest = digest(getStream(fileName));
        POIFSContainerDetector detector = new POIFSContainerDetector();
        detector.setMarkLimit(markLimit);
        try (TikaInputStream tis = TikaInputStream.get(getStream(fileName))) {
            assertExpected(detector, tis, expectedMime, expectedDigest);
        }
    }

    private TikaInputStream getStream(String fileName) {
        return getResourceAsStream("/test-documents/" + fileName);
    }

    private void assertExpected(Detector detector, TikaInputStream tis, String expectedMime, String expectedDigest) throws IOException {
        MediaType mt = detector.detect(tis, new Metadata());
        assertEquals(expectedMime, mt.toString());
        assertEquals(expectedDigest, digest(tis));
    }

    private String digest(String fileName) throws IOException {
        return digest(POIFSContainerDetectorTest.class.getResourceAsStream("/test-documents/" + fileName));
    }

    private String digest(InputStream tis) throws IOException {
        return DigestUtils.sha256Hex(tis);
    }
}
