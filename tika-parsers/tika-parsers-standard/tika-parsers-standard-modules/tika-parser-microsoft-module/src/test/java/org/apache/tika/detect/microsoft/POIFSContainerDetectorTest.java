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

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

public class POIFSContainerDetectorTest extends TikaTest {

    @Test
    public void testFullDetection() throws Exception {
        String[] files =
                new String[]{"testEXCEL.xls", "testWORD.doc", "testPPT.ppt", "testVISIO.vsd",
                        "test-outlook.msg"};
        String[] expected =
                new String[]{
                    "application/vnd.ms-excel", "application/msword", "application/vnd.ms-powerpoint",
                        "application/vnd.visio", "application/vnd.ms-outlook"
                };
        // TikaInputStream always allows full detection (spills to file if needed)
        for (int i = 0; i < files.length; i++) {
            testTikaInputStream(files[i], expected[i]);
        }
    }

    private void testTikaInputStream(String fileName, String expectedMime) throws IOException {
        POIFSContainerDetector detector = new POIFSContainerDetector();
        try (TikaInputStream tis = TikaInputStream.get(getRawStream(fileName))) {
            MediaType mt = detector.detect(tis, new Metadata(), new ParseContext());
            assertEquals(expectedMime, mt.toString());
        }
    }

    private InputStream getRawStream(String fileName) {
        return POIFSContainerDetectorTest.class.getResourceAsStream("/test-documents/" + fileName);
    }
}
