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
package org.apache.tika.pipes.core.fetcher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.io.TikaInputStream;

/**
 * The inline-vs-spool decision has to be made from bytes actually read -- a declared length is
 * absent under chunked transfer encoding and client-supplied besides -- so the boundary
 * arithmetic and the draining of the already-read prefix are where this can silently truncate a
 * document.
 */
public class PayloadRouterTest {

    @TempDir
    Path tmp;

    private static byte[] body(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) (i % 251);
        }
        return b;
    }

    private PayloadRouter.SpoolTarget target(AtomicInteger calls) {
        return () -> {
            calls.incrementAndGet();
            return Files.createTempFile(tmp, "spool-", ".bin");
        };
    }

    private PayloadRouter.Routed route(byte[] content, int threshold, AtomicInteger calls)
            throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(content))) {
            return PayloadRouter.route(tis, threshold, target(calls));
        }
    }

    @Test
    public void underThresholdIsInlined() throws IOException {
        AtomicInteger spools = new AtomicInteger();
        byte[] expected = body(99);
        try (PayloadRouter.Routed r = route(expected, 100, spools)) {
            assertTrue(r.isInline());
            assertArrayEquals(expected, r.inlineBytes().getBytes());
            assertNull(r.path());
            assertEquals(0, spools.get(), "should not have created a spool file");
        }
    }

    /** Exactly at the threshold still inlines; only strictly larger spills. */
    @Test
    public void atThresholdIsInlined() throws IOException {
        AtomicInteger spools = new AtomicInteger();
        byte[] expected = body(100);
        try (PayloadRouter.Routed r = route(expected, 100, spools)) {
            assertTrue(r.isInline());
            assertArrayEquals(expected, r.inlineBytes().getBytes());
            assertEquals(0, spools.get());
        }
    }

    /**
     * One byte over spills -- and the spilled file must hold the whole document, not just what
     * was left after the threshold probe already consumed the head.
     */
    @Test
    public void oneOverThresholdSpillsWholeBody() throws IOException {
        AtomicInteger spools = new AtomicInteger();
        byte[] expected = body(101);
        try (PayloadRouter.Routed r = route(expected, 100, spools)) {
            assertFalse(r.isInline());
            assertEquals(1, spools.get());
            assertArrayEquals(expected, Files.readAllBytes(r.path()));
        }
    }

    @Test
    public void largeBodySpillsWholeBody() throws IOException {
        AtomicInteger spools = new AtomicInteger();
        byte[] expected = body(64 * 1024 + 7);
        try (PayloadRouter.Routed r = route(expected, 4096, spools)) {
            assertFalse(r.isInline());
            assertEquals(expected.length, Files.size(r.path()));
            assertArrayEquals(expected, Files.readAllBytes(r.path()));
        }
    }

    /**
     * TikaInputStream spills its own cache to disk at 1MB, which would mean a disk write we
     * thought we had avoided plus a heap copy on top. That cache is opt-in (enableRewind), so
     * routing a body larger than 1MB but under the inline threshold must still inline and must
     * leave the stream with no file behind it. If someone later rewinds before routing, this
     * fails rather than silently reintroducing the write.
     */
    @Test
    public void aboveTikaInputStreamCacheThresholdStillInlines() throws IOException {
        AtomicInteger spools = new AtomicInteger();
        byte[] expected = body(3 * 1024 * 1024);
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(expected));
                PayloadRouter.Routed r =
                        PayloadRouter.route(tis, 10 * 1024 * 1024, target(spools))) {
            assertTrue(r.isInline(), "3MB should inline under a 10MB threshold");
            assertEquals(0, spools.get(), "no spool file should have been created");
            assertFalse(tis.hasFile(), "TikaInputStream must not have spilled its cache to disk");
            assertArrayEquals(expected, r.inlineBytes().getBytes());
        }
    }

    /** A file-backed stream keeps its file: reading it into heap would be strictly worse. */
    @Test
    public void fileBackedStreamKeepsItsFile() throws IOException {
        AtomicInteger spools = new AtomicInteger();
        Path existing = Files.createTempFile(tmp, "existing-", ".bin");
        byte[] expected = body(50);
        Files.write(existing, expected);

        try (TikaInputStream tis = TikaInputStream.get(existing);
                PayloadRouter.Routed r = PayloadRouter.route(tis, 10_000, target(spools))) {
            assertEquals(PayloadRouter.Route.EXISTING_FILE, r.route());
            assertEquals(existing, r.path());
            assertEquals(0, spools.get());
        }
        assertTrue(Files.exists(existing), "must not delete a file it did not create");
    }

    @Test
    public void spooledFileIsDeletedOnClose() throws IOException {
        AtomicInteger spools = new AtomicInteger();
        Path spooled;
        try (PayloadRouter.Routed r = route(body(500), 100, spools)) {
            spooled = r.path();
            assertTrue(Files.exists(spooled));
        }
        assertFalse(Files.exists(spooled), "spool file should be deleted on close");
    }

    /** A zero threshold turns inlining off. */
    @Test
    public void zeroThresholdAlwaysSpills() throws IOException {
        AtomicInteger spools = new AtomicInteger();
        byte[] expected = body(1);
        try (PayloadRouter.Routed r = route(expected, 0, spools)) {
            assertFalse(r.isInline());
            assertArrayEquals(expected, Files.readAllBytes(r.path()));
        }
    }

    @Test
    public void emptyBodyIsInlined() throws IOException {
        AtomicInteger spools = new AtomicInteger();
        try (PayloadRouter.Routed r = route(new byte[0], 100, spools)) {
            assertTrue(r.isInline());
            assertEquals(0, r.inlineBytes().length());
        }
    }

    /**
     * If the source dies after the spool file is created, no Routed exists yet, so route()
     * itself must delete the partial file.
     */
    @Test
    public void sourceFailureDuringSpoolDeletesPartialFile() throws IOException {
        AtomicInteger spools = new AtomicInteger();
        InputStream failing = new InputStream() {
            private int count = 0;

            @Override
            public int read() throws IOException {
                if (count < 200) {
                    count++;
                    return 'x';
                }
                throw new IOException("source died mid-stream");
            }
        };
        try (TikaInputStream tis = TikaInputStream.get(failing)) {
            assertThrows(IOException.class, () -> PayloadRouter.route(tis, 100, target(spools)));
        }
        assertEquals(1, spools.get(), "spool file should have been created before the failure");
        try (var files = Files.list(tmp)) {
            assertEquals(0, files.count(), "partial spool file must be deleted");
        }
    }

    @Test
    public void plainStreamOverloadRoutesTheSameWay() throws IOException {
        AtomicInteger spools = new AtomicInteger();
        byte[] expected = body(101);
        try (InputStream is = new ByteArrayInputStream(expected);
                PayloadRouter.Routed r = PayloadRouter.route(is, 100, target(spools))) {
            assertFalse(r.isInline());
            assertArrayEquals(expected, Files.readAllBytes(r.path()));
        }
    }
}
