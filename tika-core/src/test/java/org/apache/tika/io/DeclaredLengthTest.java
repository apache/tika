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
package org.apache.tika.io;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;

/**
 * Declared-vs-measured length semantics of {@link TikaInputStream}: a
 * Content-Length hint is served without a spool but is never "reliable";
 * a measured length (byte array, file, spool) is.
 */
public class DeclaredLengthTest {

    @TempDir
    Path tmpDir;

    @Test
    public void testStreamHonorsDeclaredLength() throws Exception {
        byte[] data = "0123456789".getBytes(UTF_8);
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_LENGTH, "7");
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data), new TemporaryResources(), metadata)) {
            assertTrue(tis.hasLength());
            assertFalse(tis.hasReliableLength(), "declared length is a hint, not ground truth");
            // declared value served without forcing a spool
            assertEquals(7, tis.getLength());
            assertFalse(tis.hasFile());
        }
    }

    @Test
    public void testSpoolMeasuresOverDeclaredLength() throws Exception {
        byte[] data = "0123456789".getBytes(UTF_8);
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_LENGTH, "7");
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data), new TemporaryResources(), metadata)) {
            tis.getPath();
            assertTrue(tis.hasReliableLength());
            assertEquals(data.length, tis.getLength(), "spooled size wins over the lying hint");
        }
    }

    @Test
    public void testStreamWithNoDeclaredLengthStillSpoolsToMeasure() throws Exception {
        byte[] data = "0123456789".getBytes(UTF_8);
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data), new TemporaryResources(), new Metadata())) {
            assertFalse(tis.hasLength());
            assertEquals(data.length, tis.getLength());
            assertTrue(tis.hasReliableLength());
        }
    }

    @Test
    public void testByteArrayAndFileAreReliable() throws Exception {
        try (TikaInputStream tis = TikaInputStream.get("abc".getBytes(UTF_8))) {
            assertTrue(tis.hasReliableLength());
            assertEquals(3, tis.getLength());
        }
        Path f = tmpDir.resolve("len.bin");
        java.nio.file.Files.write(f, "abcd".getBytes(UTF_8));
        try (TikaInputStream tis = TikaInputStream.get(f)) {
            assertTrue(tis.hasReliableLength());
            assertEquals(4, tis.getLength());
        }
    }

    @Test
    public void testReopenableDeclaredLengthNotReliableUntilSpool() throws Exception {
        byte[] data = "0123456789".getBytes(UTF_8);
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_LENGTH, "3");
        try (TikaInputStream tis = TikaInputStream.get(
                () -> new ByteArrayInputStream(data), new TemporaryResources(), metadata)) {
            assertTrue(tis.hasLength());
            assertFalse(tis.hasReliableLength());
            assertEquals(3, tis.getLength());
            tis.getPath();
            assertTrue(tis.hasReliableLength());
            assertEquals(data.length, tis.getLength());
        }
    }
}
