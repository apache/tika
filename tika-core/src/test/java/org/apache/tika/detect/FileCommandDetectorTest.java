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
package org.apache.tika.detect;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;

public class FileCommandDetectorTest {

    // Use undeclared_entity.xml instead of basic_embedded.xml because
    // basic_embedded.xml has <mock> root which triggers custom mime type
    private static final String TEST_FILE = "/test-documents/undeclared_entity.xml";

    @Test
    public void testBasic() throws Exception {
        assumeTrue(FileCommandDetector.checkHasFile());

        // Create a composite detector that includes FileCommandDetector
        FileCommandDetector fileDetector = new FileCommandDetector();
        Detector defaultDetector = new DefaultDetector();
        Detector detector = new CompositeDetector(
                MimeTypes.getDefaultMimeTypes().getMediaTypeRegistry(),
                Arrays.asList(fileDetector, defaultDetector));

        try (TikaInputStream tis = TikaInputStream.get(getClass()
                .getResourceAsStream(TEST_FILE))) {
            //run more than once to ensure that the input stream is reset
            for (int i = 0; i < 2; i++) {
                Metadata metadata = new Metadata();
                MediaType answer = detector.detect(tis, metadata);
                String fileMime = metadata.get(FileCommandDetector.FILE_MIME);
                assertTrue(MediaType.text("xml").equals(answer) ||
                        MediaType.application("xml").equals(answer),
                        "Expected text/xml or application/xml but got: " + answer);
                assertTrue("application/xml".equals(fileMime) ||
                        "text/xml".equals(fileMime),
                        "Expected application/xml or text/xml but got: " + fileMime);
            }
        }

        //now try with TikaInputStream
        try (TikaInputStream tis = TikaInputStream
                .get(getClass().getResourceAsStream(TEST_FILE))) {
            //run more than once to ensure that the input stream is reset
            for (int i = 0; i < 2; i++) {
                MediaType answer = detector.detect(tis, new Metadata());
                assertTrue(MediaType.text("xml").equals(answer) ||
                        MediaType.application("xml").equals(answer),
                        "Expected text/xml or application/xml but got: " + answer);
            }
        }
    }
}
