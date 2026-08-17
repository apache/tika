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
package org.apache.tika.zip.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

public class ZipSalvagerTest {

    @Test
    public void testFailedSalvageDoesNotOrphanTempFile() throws Exception {
        Set<Path> before = listSalvagedTempFiles();
        byte[] corrupt = "PK not really a zip".getBytes(StandardCharsets.ISO_8859_1);
        try (InputStream is = new ByteArrayInputStream(corrupt);
                TikaInputStream tis = TikaInputStream.get(is)) {
            //an already-advanced stream can be neither spooled nor rewound, so the direct
            //open and the salvage both fail
            assertNotEquals(-1, tis.read());
            try {
                assertNull(ZipSalvager.tryToOpenZipFile(tis, new Metadata()));
            } catch (RuntimeException e) {
                //salvage is best effort; the assertion below is on the temp file, not on
                //how the failure surfaces
            }
        }
        assertEquals(before, listSalvagedTempFiles(), "salvage temp file was orphaned");
    }

    private static Set<Path> listSalvagedTempFiles() throws IOException {
        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Set<Path> paths = new HashSet<>();
        try (DirectoryStream<Path> ds =
                     Files.newDirectoryStream(tmpDir, "tika-salvaged-*.zip")) {
            for (Path p : ds) {
                paths.add(p);
            }
        }
        return paths;
    }
}
