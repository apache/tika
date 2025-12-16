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

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.pf4j.util.Unzip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe and process-safe plugin unzipper using atomic rename.
 * <p>
 * This avoids file locking issues on Windows by using a simple strategy:
 * <ol>
 *   <li>Check if destination directory exists with completion marker - if yes, already extracted</li>
 *   <li>Extract to a temporary directory with a unique name</li>
 *   <li>Create a completion marker file in the temp directory</li>
 *   <li>Atomically rename temp dir to final destination</li>
 *   <li>If rename fails (another process won), clean up temp dir</li>
 * </ol>
 * <p>
 * The completion marker ensures that even if atomic move is not supported,
 * other processes won't attempt to load a partially-moved directory.
 */
public class ThreadSafeUnzipper {
    private static final Logger LOG = LoggerFactory.getLogger(TikaPluginManager.class);
    private static final String COMPLETE_MARKER = ".tika-extraction-complete";

    /**
     * Unzips a plugin zip file to a directory with the same name (minus .zip extension).
     * Safe for concurrent calls from multiple threads or processes. See
     * documentation at the head of this class for how it works.
     *
     * @param source path to the .zip file
     * @throws IOException if extraction fails
     */
    public static void unzipPlugin(Path source) throws IOException {
        if (!source.getFileName().toString().endsWith(".zip")) {
            throw new IllegalArgumentException("source file name must end in '.zip'");
        }

        Path destination = getDestination(source);

        // Already extracted - check for both directory AND completion marker
        if (isExtractionComplete(destination)) {
            LOG.debug("{} is already extracted", source);
            return;
        }

        // Extract to a unique temp directory
        Path tempDir = destination.resolveSibling(
                destination.getFileName() + ".tmp." + UUID.randomUUID());

        try {
            LOG.debug("extracting {} to temp dir {}", source, tempDir);
            new Unzip(source.toFile(), tempDir.toFile()).extract();

            // Create completion marker in temp dir before moving
            Files.createFile(tempDir.resolve(COMPLETE_MARKER));

            // Atomically rename to final destination
            try {
                Files.move(tempDir, destination, StandardCopyOption.ATOMIC_MOVE);
                LOG.debug("successfully extracted {}", destination);
            } catch (FileAlreadyExistsException | DirectoryNotEmptyException e) {
                // Another process extracted it first - wait for completion marker
                LOG.debug("plugin already extracted by another process: {}", destination);
                waitForExtractionComplete(destination);
            } catch (AtomicMoveNotSupportedException e) {
                // Filesystem doesn't support atomic move, try regular move
                try {
                    Files.move(tempDir, destination);
                    LOG.debug("successfully extracted {} (non-atomic)", destination);
                } catch (FileAlreadyExistsException | DirectoryNotEmptyException e2) {
                    // Another process extracted it first - wait for completion marker
                    LOG.debug("plugin already extracted by another process: {}", destination);
                    waitForExtractionComplete(destination);
                }
            }
        } finally {
            // Clean up temp dir if it still exists (we lost the race or there was an error)
            if (Files.exists(tempDir)) {
                deleteRecursively(tempDir);
            }
        }
    }

    /**
     * Checks if extraction is complete by verifying both directory exists and completion marker is present.
     */
    private static boolean isExtractionComplete(Path destination) {
        return Files.isDirectory(destination) && Files.exists(destination.resolve(COMPLETE_MARKER));
    }

    /**
     * Waits for extraction to complete by polling for the completion marker.
     * This is called when we detect another process is extracting.
     */
    private static void waitForExtractionComplete(Path destination) throws IOException {
        long maxWaitMs = 60000; // 1 minute max wait
        long pollIntervalMs = 100;
        long waited = 0;

        while (waited < maxWaitMs) {
            if (isExtractionComplete(destination)) {
                LOG.debug("extraction completed by another process: {}", destination);
                return;
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for extraction to complete", e);
            }
            waited += pollIntervalMs;
        }

        throw new IOException("timed out waiting for extraction to complete: " + destination);
    }

    private static Path getDestination(Path source) {
        String fName = source.getFileName().toString();
        fName = fName.substring(0, fName.length() - 4);
        return source.toAbsolutePath().getParent().resolve(fName);
    }

    private static void deleteRecursively(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            LOG.warn("failed to delete temp file: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            LOG.warn("failed to clean up temp directory: {}", path, e);
        }
    }
}
