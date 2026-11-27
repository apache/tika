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
package org.apache.tika.pipes.ignite.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IgniteStoreServerTest {

    @Test
    public void testDefaultWorkDirIsPrivateAndUnique() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path first = IgniteStoreServer.prepareWorkDir(null);
        Path second = IgniteStoreServer.prepareWorkDir(null);
        try {
            assertNotEquals(first, second);
            Path tmp = Paths.get(System.getProperty("java.io.tmpdir")).toRealPath();
            assertEquals(tmp, first.getParent().toRealPath());
            assertTrue(first.getFileName().toString().startsWith("tika-ignite-work-"));
            Set<PosixFilePermission> ownerOnly = Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
            assertEquals(ownerOnly, Files.getPosixFilePermissions(first));
            assertEquals(ownerOnly, Files.getPosixFilePermissions(second));
        } finally {
            Files.deleteIfExists(first);
            Files.deleteIfExists(second);
        }
    }

    @Test
    public void testConfiguredWorkDirIsEmptiedAndKept(@TempDir Path tmp) throws Exception {
        Path configured = tmp.resolve("work");
        Files.createDirectories(configured.resolve("partitions"));
        Files.writeString(configured.resolve("partitions").resolve("stale"), "x");

        assertEquals(configured, IgniteStoreServer.prepareWorkDir(configured));
        try (Stream<Path> contents = Files.list(configured)) {
            assertEquals(0, contents.count());
        }
    }
}
