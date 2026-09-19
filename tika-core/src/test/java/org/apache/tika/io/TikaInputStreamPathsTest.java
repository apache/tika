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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;

/** Happy and unhappy paths through every source: what is released, and what bytes come back. */
public class TikaInputStreamPathsTest {

    private static final int MB = 1024 * 1024;

    @TempDir
    Path dir;

    private static byte[] data(int size) {
        byte[] d = new byte[size];
        for (int i = 0; i < size; i++) {
            d[i] = (byte) (i * 13 + 5);
        }
        return d;
    }

    /** Counts closes; throws after {@code failAt} bytes when that is positive. */
    private static final class Probe extends InputStream {
        final ByteArrayInputStream in;
        final long failAt;
        long read;
        int closes;

        Probe(byte[] d, long failAt) {
            this.in = new ByteArrayInputStream(d);
            this.failAt = failAt;
        }

        @Override
        public int read() throws IOException {
            if (failAt > 0 && read >= failAt) {
                throw new IOException("probe failure at " + read);
            }
            int b = in.read();
            if (b != -1) {
                read++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (failAt > 0 && read >= failAt) {
                throw new IOException("probe failure at " + read);
            }
            int n = in.read(b, off, failAt > 0 ? (int) Math.min(len, failAt - read) : len);
            if (n > 0) {
                read += n;
            }
            return n;
        }

        @Override
        public void close() {
            closes++;
        }
    }

    private TemporaryResources tmp() {
        TemporaryResources tmp = new TemporaryResources();
        tmp.setTemporaryFileDirectory(dir);
        return tmp;
    }

    private long tempFiles() throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    private static byte[] readChannel(SeekableByteChannel ch) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) ch.size());
        ch.position(0);
        while (buf.hasRemaining() && ch.read(buf) != -1) {
            // fill
        }
        return buf.array();
    }

    @Test
    public void failureMidReadReleasesBudgetFilesAndStream() throws Exception {
        CacheMemoryBudget budget = new CacheMemoryBudget(64L * MB);
        Probe probe = new Probe(data(3 * MB), 2 * MB);
        TemporaryResources tmp = tmp();
        TikaInputStream tis = TikaInputStream.get(probe, tmp, new Metadata());
        tis.enableRewind(budget);
        assertThrows(IOException.class, () -> IOUtils.toByteArray(tis));
        assertTrue(budget.getReservedBytes() > 0, "the cache grew past the floor before the failure");
        tis.close();
        assertEquals(0, budget.getReservedBytes());
        assertEquals(0, tempFiles());
        assertEquals(1, probe.closes, "the wrapped stream is closed once");
    }

    @Test
    public void spillIsCleanedUpAndReplaysTheSameBytes() throws Exception {
        byte[] d = data(3 * MB);
        TemporaryResources tmp = tmp();
        TikaInputStream tis = TikaInputStream.get(new Probe(d, 0), tmp, new Metadata());
        tis.enableRewind(null);
        tis.mark(1000);
        assertArrayEquals(d, IOUtils.toByteArray(tis), "first pass");
        assertTrue(tis.hasFile(), "past the threshold the cache spilled");
        assertEquals(1, tempFiles());
        tis.reset();
        assertEquals(d[0] & 0xff, tis.read(), "reset across the spill lands on the mark");
        tis.rewind();
        assertArrayEquals(d, IOUtils.toByteArray(tis), "second pass, from the spill");
        assertEquals(d.length, tis.getLength());
        assertTrue(tis.hasReliableLength());
        tis.close();
        tis.close();
        assertEquals(0, tempFiles(), "spill deleted on close, double close harmless");
    }

    @Test
    public void channelsFromEverySourceCloseWithTheStream() throws Exception {
        byte[] d = data(3 * MB);
        Metadata md = new Metadata();
        md.set(HttpHeaders.CONTENT_LENGTH, Integer.toString(d.length));
        // spilled cache
        TikaInputStream caching = TikaInputStream.get(new Probe(d, 0), tmp(), new Metadata());
        caching.enableRewind(null);
        SeekableByteChannel c1 = caching.getSeekableByteChannel();
        assertArrayEquals(d, readChannel(c1));
        caching.close();
        assertFalse(c1.isOpen(), "a spill-file channel is the stream's to close");
        // re-openable, spilled (no budget: over the floor it cannot be retained)
        TikaInputStream reopen = TikaInputStream.get(() -> new ByteArrayInputStream(d), tmp(), md);
        SeekableByteChannel c2 = reopen.getSeekableByteChannel();
        assertArrayEquals(d, readChannel(c2));
        reopen.close();
        assertFalse(c2.isOpen(), "a re-openable source's spill channel is the stream's to close");
        // file
        Path file = dir.resolve("f.bin");
        Files.write(file, d);
        TikaInputStream fs = TikaInputStream.get(file);
        SeekableByteChannel c3 = fs.getSeekableByteChannel();
        assertArrayEquals(d, readChannel(c3));
        fs.close();
        assertFalse(c3.isOpen(), "a file channel is the stream's to close");
    }

    @Test
    public void reopenableFailureMidRetentionReleasesTheReservation() throws Exception {
        CacheMemoryBudget budget = new CacheMemoryBudget(64L * MB);
        byte[] d = data(3 * MB);
        Metadata md = new Metadata();
        md.set(HttpHeaders.CONTENT_LENGTH, Integer.toString(d.length));
        TemporaryResources tmp = tmp();
        TikaInputStream tis = TikaInputStream.get(() -> new Probe(d, 2 * MB), tmp, md);
        tis.enableRewind(budget);
        assertThrows(IOException.class, tis::tryRetainInMemory);
        assertEquals(0, budget.getReservedBytes(), "a failed drain returns what it reserved");
        tis.close();
        assertEquals(0, budget.getReservedBytes());
        assertEquals(0, tempFiles());
    }

    @Test
    public void unknownLengthOverTheFloorOpensTheSourceTwice() throws Exception {
        byte[] d = data(2 * MB);
        AtomicInteger opens = new AtomicInteger();
        TikaInputStream tis = TikaInputStream.get(() -> {
            opens.incrementAndGet();
            return new ByteArrayInputStream(d);
        }, tmp(), new Metadata());
        try (SeekableByteChannel ch = tis.getSeekableByteChannel()) {
            assertEquals(d.length, ch.size());
        }
        // a doomed in-memory attempt up to the floor, then the spool: known cost, pinned here
        assertEquals(2, opens.get());
        tis.close();
    }

    @Test
    public void pinnedChannelSurvivesASpill() throws Exception {
        CacheMemoryBudget budget = new CacheMemoryBudget(2L * MB);
        byte[] d = data(4 * MB);
        TemporaryResources tmp = tmp();
        TikaInputStream tis = TikaInputStream.get(new Probe(d, 0), tmp, new Metadata());
        tis.enableRewind(budget);
        // the channel drains everything: 4 MB does not fit a 2 MB budget, so it spills
        try (SeekableByteChannel ch = tis.getSeekableByteChannel()) {
            assertArrayEquals(d, readChannel(ch));
        }
        assertEquals(0, budget.getReservedBytes(), "spilled: nothing stays reserved");
        assertArrayEquals(d, IOUtils.toByteArray(tis), "the stream still reads the whole source");
        tis.close();
        assertEquals(0, tempFiles());
    }

    @Test
    public void inMemoryChannelIsClosedWithTheStream() throws Exception {
        CacheMemoryBudget budget = new CacheMemoryBudget(64L * MB);
        byte[] d = data(3 * MB);
        TemporaryResources tmp = tmp();
        TikaInputStream tis = TikaInputStream.get(new Probe(d, 0), tmp, new Metadata());
        tis.enableRewind(budget);
        SeekableByteChannel ch = tis.getSeekableByteChannel();
        assertTrue(budget.getReservedBytes() > 0);
        assertArrayEquals(d, readChannel(ch));
        tis.close();   // a parser that threw before closing its channel
        assertFalse(ch.isOpen());
        assertEquals(0, budget.getReservedBytes(), "the pin goes with the stream");
        ch.close();
        assertEquals(0, budget.getReservedBytes(), "closing it again releases nothing twice");
    }

    @Test
    public void failedReopenDoesNotOrphanTheFreshStream() throws Exception {
        byte[] full = data(64 * 1024);
        byte[] truncated = data(100);
        AtomicInteger opens = new AtomicInteger();
        Probe[] streams = new Probe[2];
        TikaInputStream tis = TikaInputStream.get(() -> {
            int n = opens.getAndIncrement();
            return streams[n] = new Probe(n == 0 ? full : truncated, 0);
        }, tmp(), new Metadata());
        tis.readNBytes(new byte[1000], 0, 1000);
        tis.mark(10);
        tis.readNBytes(new byte[32 * 1024], 0, 32 * 1024);   // past the limit: reset must reopen
        assertThrows(IOException.class, tis::reset, "the re-opened source is too short to skip to the mark");
        assertEquals(2, opens.get());
        assertEquals(1, streams[1].closes, "the stream the failed re-open left behind is closed");
        tis.close();
    }

    @Test
    public void fileSourceMarkResetAndChannelDoNotMovePosition() throws Exception {
        byte[] d = data(64 * 1024);
        Path file = dir.resolve("g.bin");
        Files.write(file, d);
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            tis.readNBytes(new byte[100], 0, 100);
            tis.mark(1000);
            tis.readNBytes(new byte[400], 0, 400);
            try (SeekableByteChannel ch = tis.getSeekableByteChannel()) {
                assertEquals(d.length, ch.size());
            }
            assertEquals(500, tis.getPosition());
            tis.reset();
            assertEquals(d[100] & 0xff, tis.read());
            assertEquals(101, tis.getPosition());
            tis.mark(10);
            tis.readNBytes(new byte[32 * 1024], 0, 32 * 1024);   // past the limit: reopen path
            tis.reset();
            assertEquals(d[101] & 0xff, tis.read());
        }
    }

    @Test
    public void passthroughRefusesLateRewindButReadsCorrectly() throws Exception {
        byte[] d = data(64 * 1024);
        TemporaryResources tmp = tmp();
        TikaInputStream tis = TikaInputStream.get(new Probe(d, 0), tmp, new Metadata());
        tis.readNBytes(new byte[10], 0, 10);
        assertThrows(IOException.class, () -> tis.enableRewind(null), "rewind must be enabled at 0");
        assertThrows(IOException.class, tis::getPath, "no spool from mid-stream");
        tis.mark(100);
        tis.readNBytes(new byte[50], 0, 50);
        tis.reset();
        assertEquals(d[10] & 0xff, tis.read(), "passthrough mark/reset is the buffer's");
        tis.close();
        assertEquals(0, tempFiles());
    }

    @Test
    public void lengthIsMeasuredAtEofWithoutASpill() throws Exception {
        byte[] d = data(5000);
        // lying declared length, cache mode
        Metadata lying = new Metadata();
        lying.set(HttpHeaders.CONTENT_LENGTH, "4000");
        TikaInputStream tis = TikaInputStream.get(new Probe(d, 0), tmp(), lying);
        tis.enableRewind(null);
        assertFalse(tis.hasReliableLength());
        IOUtils.toByteArray(tis);
        assertEquals(5000, tis.getLength());
        assertTrue(tis.hasReliableLength());
        assertEquals(0, tempFiles(), "the cache knows the size; nothing is spooled to measure it");
        tis.close();
        // no declared length, passthrough
        tis = TikaInputStream.get(new Probe(d, 0), tmp(), new Metadata());
        IOUtils.toByteArray(tis);
        assertEquals(5000, tis.getLength());
        assertTrue(tis.hasReliableLength());
        assertEquals(0, tempFiles());
        tis.close();
    }

    @Test
    public void unknownLengthIsRetainedWhenItFits() throws Exception {
        byte[] d = data(500 * 1024);
        AtomicInteger opens = new AtomicInteger();
        TikaInputStream tis = TikaInputStream.get(() -> {
            opens.incrementAndGet();
            return new ByteArrayInputStream(d);
        }, tmp(), new Metadata());
        tis.enableRewind(null);
        assertTrue(tis.tryRetainInMemory(), "under the floor it is held without a length");
        assertArrayEquals(d, IOUtils.toByteArray(tis));
        tis.rewind();
        assertArrayEquals(d, IOUtils.toByteArray(tis));
        assertEquals(1, opens.get(), "digest and parse read the retained bytes");
        assertEquals(d.length, tis.getLength());
        tis.close();
    }

    @Test
    public void skipAndNullTmpFollowTheContract() throws Exception {
        byte[] d = data(1000);
        Probe probe = new Probe(d, 0);
        TikaInputStream tis = TikaInputStream.get(probe, null, new Metadata());
        assertEquals(0, tis.skip(-1));
        assertEquals(0, tis.skip(0));
        assertEquals(10, tis.skip(10));
        assertEquals(d[10] & 0xff, tis.read());
        tis.close();
        assertEquals(1, probe.closes);
    }

    @Test
    public void spilledReplayByteAtATimeAndTrimmedReservation() throws Exception {
        byte[] d = data(3 * MB);
        TemporaryResources tmp = tmp();
        TikaInputStream tis = TikaInputStream.get(new Probe(d, 0), tmp, new Metadata());
        tis.enableRewind(null);   // no budget: spills at 1 MB
        IOUtils.toByteArray(tis);
        tis.rewind();
        for (int i = 0; i < 20000; i++) {
            assertEquals(d[i] & 0xff, tis.read(), "byte " + i + " from the spill window");
        }
        tis.close();

        CacheMemoryBudget budget = new CacheMemoryBudget(64L * MB);
        tis = TikaInputStream.get(new Probe(d, 0), tmp(), new Metadata());
        tis.enableRewind(budget);
        IOUtils.toByteArray(tis);
        assertEquals(2L * MB, budget.getReservedBytes(),
                "after the drain the reservation is the content over the floor, not the doubled capacity");
        tis.close();
        assertEquals(0, budget.getReservedBytes());
    }

    @Test
    public void tmpClosedFirstThenStream() throws Exception {
        CacheMemoryBudget budget = new CacheMemoryBudget(64L * MB);
        byte[] d = data(3 * MB);
        TemporaryResources tmp = tmp();
        TikaInputStream tis = TikaInputStream.get(new Probe(d, 0), tmp, new Metadata());
        tis.enableRewind(budget);
        IOUtils.toByteArray(tis);
        tmp.close();
        assertEquals(0, budget.getReservedBytes());
        assertEquals(0, tempFiles());
        tis.close();
        assertEquals(0, budget.getReservedBytes(), "never negative");
    }
}
