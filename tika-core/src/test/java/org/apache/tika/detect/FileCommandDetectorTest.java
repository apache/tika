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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.ExternalProcess;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;

public class FileCommandDetectorTest extends TikaTest {

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
                MediaType answer = detector.detect(tis, metadata, new ParseContext());
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
                MediaType answer = detector.detect(tis, new Metadata(), new ParseContext());
                assertTrue(MediaType.text("xml").equals(answer) ||
                        MediaType.application("xml").equals(answer),
                        "Expected text/xml or application/xml but got: " + answer);
            }
        }
    }

    /**
     * TIKA-4813 follow-up: unlike every other ProcessUtils-backed detector/parser
     * (MagikaDetector, SiegfriedDetector, GDALParser, ExternalParser), this detector only
     * used to set ExternalProcess.IS_TIMEOUT on the timeout path, leaving it absent (not
     * "false") on a normal, non-timed-out run -- inconsistent with the property being
     * present-and-explicit everywhere else in the codebase.
     */
    @Test
    public void testIsTimeoutIsExplicitlyFalseOnSuccess() throws Exception {
        assumeTrue(FileCommandDetector.checkHasFile());

        FileCommandDetector fileDetector = new FileCommandDetector();
        try (TikaInputStream tis = TikaInputStream.get(getClass().getResourceAsStream(TEST_FILE))) {
            Metadata metadata = new Metadata();
            fileDetector.detect(tis, metadata, new ParseContext());

            assertNotNull(metadata.get(ExternalProcess.IS_TIMEOUT),
                    "IS_TIMEOUT must be explicitly recorded even on a successful run, "
                            + "matching every other ProcessUtils-backed detector/parser");
            assertFalse(Boolean.parseBoolean(metadata.get(ExternalProcess.IS_TIMEOUT)));
        }
    }
}
