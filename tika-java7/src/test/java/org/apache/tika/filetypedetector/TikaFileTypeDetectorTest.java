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
package org.apache.tika.filetypedetector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;
import java.util.Iterator;
import java.util.ServiceLoader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TikaFileTypeDetectorTest {

    private static final String TEST_CLASSPATH = "/test-documents/test.html";
    private static final String TEST_HTML = "test.html";
    private static final String TEST_UNRECOGNISED_EXTENSION = "test.unrecognisedextension";
    @TempDir
    public Path tempDir;
    private Path testDirectory = null;

    @BeforeEach
    public void setUp() throws Exception {
        testDirectory = tempDir;
        System.out.println(testDirectory.toAbsolutePath());
        try (InputStream is = this.getClass().getResourceAsStream(TEST_CLASSPATH)) {
            Files.copy(is, testDirectory.resolve(TEST_HTML));
        }
        try (InputStream is = this.getClass().getResourceAsStream(TEST_CLASSPATH)) {
            Files.copy(is, testDirectory.resolve(TEST_UNRECOGNISED_EXTENSION));
        }
    }

    @Test
    public final void testDirectAccess() throws Exception {
        String contentType =
                new TikaFileTypeDetector().probeContentType(testDirectory.resolve(TEST_HTML));
        assertNotNull(contentType);
        assertEquals("text/html", contentType);
    }

    @Test
    public final void testFilesProbeContentTypePathExtension() throws Exception {
        String contentType = Files.probeContentType(testDirectory.resolve(TEST_HTML));
        assertNotNull(contentType);
        assertEquals("text/html", contentType);
    }

    @Test
    public final void testFilesProbeContentTypePathUnrecognised() throws Exception {
        String contentType =
                Files.probeContentType(testDirectory.resolve(TEST_UNRECOGNISED_EXTENSION));
        assertNotNull(contentType);
        assertEquals("text/html", contentType);
    }

    @Test
    public final void testMetaInfServicesLoad() throws Exception {
        ServiceLoader<FileTypeDetector> serviceLoader = ServiceLoader.load(FileTypeDetector.class);

        Iterator<FileTypeDetector> iterator = serviceLoader.iterator();
        assertTrue(iterator.hasNext());
        boolean foundTika = false;
        while (iterator.hasNext()) {
            FileTypeDetector fileTypeDetector = iterator.next();
            assertNotNull(fileTypeDetector);
            if (fileTypeDetector instanceof TikaFileTypeDetector) {
                foundTika = true;
            }
        }
        //o.a.sis.internal.storage.StoreTypeDetector appears with latest upgrade
        //check that TikaFileTypeDetector appears at all
        assertTrue(foundTika);
    }
}
