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
package org.apache.tika.plugins;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ThreadSafeUnzipperTest {

    private static final String MARKER = ".tika-extraction-complete";

    /**
     * Regression: a stale destination directory left over from a previous
     * extraction (killed mid-stream, marker removed, etc.) used to wedge the
     * unzipper into a permanent DirectoryNotEmptyException loop. The fix
     * detects the no-marker case and rebuilds.
     */
    @Test
    public void testStaleDestinationIsRebuilt(@TempDir Path tmp) throws Exception {
        Path zip = writeTrivialZip(tmp.resolve("plugin.zip"));
        Path destination = tmp.resolve("plugin");

        // Simulate a half-extracted state: destination exists with some files
        // but no completion marker.
        Files.createDirectories(destination);
        Files.writeString(destination.resolve("leftover.txt"), "stale");
        assertFalse(Files.exists(destination.resolve(MARKER)),
                "precondition: no completion marker");

        ThreadSafeUnzipper.unzipPlugin(zip);

        // After unzip, the leftover file must be gone (destination rebuilt)
        // and the marker must be present (extraction completed).
        assertFalse(Files.exists(destination.resolve("leftover.txt")),
                "stale file should be removed when destination is rebuilt");
        assertTrue(Files.exists(destination.resolve(MARKER)),
                "completion marker should be present after rebuild");
        assertTrue(Files.exists(destination.resolve("inside.txt")),
                "actual zip contents should be extracted");
    }

    /**
     * Happy path: when the destination already has the completion marker,
     * extraction is a no-op (does not touch the marker or contents).
     */
    @Test
    public void testCompletedDestinationIsLeftAlone(@TempDir Path tmp) throws Exception {
        Path zip = writeTrivialZip(tmp.resolve("plugin.zip"));
        Path destination = tmp.resolve("plugin");

        // Pre-populate as if a previous extraction completed successfully.
        Files.createDirectories(destination);
        Files.writeString(destination.resolve(MARKER), "");
        Files.writeString(destination.resolve("already-here.txt"), "untouched");

        ThreadSafeUnzipper.unzipPlugin(zip);

        assertTrue(Files.exists(destination.resolve("already-here.txt")),
                "no-op extraction should not touch existing contents");
        assertTrue(Files.exists(destination.resolve(MARKER)),
                "marker should still be present");
        assertFalse(Files.exists(destination.resolve("inside.txt")),
                "no-op extraction should NOT extract zip contents over the existing dir");
    }

    @Test
    public void testReadOnlyRootFailsNamingTheRoot(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("root"));
        Path zip = writeTrivialZip(root.resolve("plugin.zip"));
        assumeTrue(root.toFile().setWritable(false) && !Files.isWritable(root),
                "cannot make a directory read-only here (Windows, or running as root)");
        try {
            IOException e = assertThrows(IOException.class,
                    () -> ThreadSafeUnzipper.unzipPlugin(zip));
            assertTrue(e.getMessage().contains("not writable")
                    && e.getMessage().contains(root.toString()), e.getMessage());

            // an already-unzipped plugin in a read-only root still loads
            root.toFile().setWritable(true);
            ThreadSafeUnzipper.unzipPlugin(zip);
            root.toFile().setWritable(false);
            ThreadSafeUnzipper.unzipPlugin(zip);
        } finally {
            root.toFile().setWritable(true);
        }
    }

    private static Path writeTrivialZip(Path zipPath) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("inside.txt"));
            zos.write("hello".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return zipPath;
    }
}
