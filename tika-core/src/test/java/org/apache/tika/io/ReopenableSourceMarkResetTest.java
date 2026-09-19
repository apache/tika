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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;

/**
 * A mark/reset within the read limit must be served from the open stream, never by re-opening
 * the source: re-opening a zip entry inflates it from the start, and a reader that marks and
 * resets per record (POI on a metafile's bitmaps) made that quadratic.
 */
public class ReopenableSourceMarkResetTest {

    private static byte[] data(int size) {
        byte[] d = new byte[size];
        for (int i = 0; i < size; i++) {
            d[i] = (byte) (i * 7 + 3);
        }
        return d;
    }

    @Test
    public void resetWithinLimitDoesNotReopen() throws Exception {
        byte[] data = data(64 * 1024);
        AtomicInteger opens = new AtomicInteger();
        try (TikaInputStream tis = TikaInputStream.get(() -> {
            opens.incrementAndGet();
            return new ByteArrayInputStream(data);
        }, new TemporaryResources(), new Metadata())) {
            byte[] buf = new byte[100];
            assertEquals(100, tis.readNBytes(buf, 0, 100));
            assertEquals(1, opens.get());

            for (int i = 0; i < 500; i++) {
                tis.mark(1000);
                assertEquals(400, tis.readNBytes(buf.length > 400 ? buf : new byte[400], 0, 400));
                tis.reset();
            }
            assertEquals(1, opens.get(), "500 resets within the limit must not re-open");
            assertEquals(data[100] & 0xff, tis.read(), "positioned at the mark after reset");

            tis.mark(100);
            // far past the limit and the stream's buffer: the mark is gone, the source re-opens
            tis.readNBytes(new byte[32 * 1024], 0, 32 * 1024);
            tis.reset();
            assertEquals(2, opens.get(), "past the limit the source re-opens, once");
            assertEquals(data[101] & 0xff, tis.read(), "and lands on the mark");
        }
    }

    @Test
    public void hugeReadlimitDoesNotBufferWithoutBound() throws Exception {
        byte[] data = data(4 * 1024 * 1024);
        AtomicInteger opens = new AtomicInteger();
        try (TikaInputStream tis = TikaInputStream.get(() -> {
            opens.incrementAndGet();
            return new ByteArrayInputStream(data);
        }, new TemporaryResources(), new Metadata())) {
            assertEquals(data[0] & 0xff, tis.read());
            tis.mark(Integer.MAX_VALUE);
            tis.readNBytes(new byte[3 * 1024 * 1024], 0, 3 * 1024 * 1024);
            tis.reset();
            assertEquals(2, opens.get(), "beyond the 1 MB cap a reset re-opens rather than buffer");
            assertEquals(data[1] & 0xff, tis.read());
        }
    }

    @Test
    public void markBeforeAnyReadStillResets() throws Exception {
        byte[] data = data(1024);
        try (TikaInputStream tis = TikaInputStream.get(() -> new ByteArrayInputStream(data),
                new TemporaryResources(), new Metadata())) {
            tis.mark(10);
            assertEquals(data[0] & 0xff, tis.read());
            tis.reset();
            assertEquals(data[0] & 0xff, tis.read());
        }
    }
}
