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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.Tika;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

public class TestZipDetector extends TikaTest {

    private static final String ZIP_FILE = "testTika4424.zip";
    private static final String SKIP_ZIP_CONTAINER_CONFIG = "tika-4424-config.xml";

    private static final Detector DETECTOR = TikaConfig
            .getDefaultConfig()
            .getDetector();

    private static Path DOCX;
    @BeforeAll
    public static void setUp() throws Exception {
        DOCX = Files.createTempFile("test-zip-", ".docx");
        Files.copy(TestZipDetector.class.getResourceAsStream("/test-documents/testWORD.docx"),
                DOCX, StandardCopyOption.REPLACE_EXISTING);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        Files.delete(DOCX);
    }

    @Test
    public void testBasic() throws Exception {
        String expectedMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        Path p = DOCX;
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(p, metadata)) {
            assertEquals(expectedMime, DETECTOR
                    .detect(tis, metadata)
                    .toString());
        }

        byte[] bytes = Files.readAllBytes(p);
        metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            assertEquals(expectedMime, DETECTOR
                    .detect(tis, metadata)
                    .toString());
        }

        metadata = new Metadata();
        try (InputStream is = new BufferedInputStream(Files.newInputStream(p))) {
            assertEquals(expectedMime, DETECTOR
                    .detect(is, metadata)
                    .toString());
        }

        metadata = new Metadata();
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            assertEquals(expectedMime, DETECTOR
                    .detect(is, metadata)
                    .toString());
        }
    }

    @Test
    public void detectKmzUsingPlainInputStream() throws Exception {
        try (InputStream inputStream = TestZipDetector.class.getResourceAsStream("/test-documents/" + ZIP_FILE)) {

            assertNotNull(inputStream);

            Tika tika = new Tika();

            String result = tika.detect(inputStream, ZIP_FILE);
            assertEquals("application/vnd.google-earth.kmz", result);
        }
    }

    @Test
    public void detectKmzUsingTikaInputStream() throws Exception {
        try (InputStream inputStream = TestZipDetector.class.getResourceAsStream("/test-documents/" + ZIP_FILE);
                TikaInputStream tikaInputStream = TikaInputStream.get(inputStream)) {

            assertNotNull(tikaInputStream);

            Tika tika = new Tika();

            String result = tika.detect(tikaInputStream, ZIP_FILE);
            assertEquals("application/vnd.google-earth.kmz", result);
        }
    }

    @Test
    public void detectPlainZipUsingPlainInputStream() throws Exception {
        try (InputStream tikaConfigInputStream = TestZipDetector.class.getResourceAsStream("/configs/" + SKIP_ZIP_CONTAINER_CONFIG);
                InputStream inputStream = TestZipDetector.class.getResourceAsStream("/test-documents/" + ZIP_FILE)) {

            assertNotNull(tikaConfigInputStream);
            assertNotNull(inputStream);

            Tika tika = new Tika(new TikaConfig(tikaConfigInputStream));

            String result = tika.detect(inputStream, ZIP_FILE);
            assertEquals("application/zip", result);
        }
    }

}
