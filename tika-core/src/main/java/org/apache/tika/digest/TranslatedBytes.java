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
package org.apache.tika.digest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

import org.apache.tika.io.CacheMemoryBudget;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;

/**
 * Sink for a translated embedded stream: bytes stay in memory while the shared
 * {@link CacheMemoryBudget} (or the per-object default without one) allows, and spill to a
 * temp file owned by {@code tmp} past that, so the common small object is digested without
 * touching disk. Translation can inflate (compressed OLE payloads), so the reservation grows
 * on demand rather than being fixed to the source length.
 */
class TranslatedBytes extends OutputStream {

    private static final long GROW_CHUNK = 1024 * 1024;

    private final TemporaryResources tmp;
    private final CacheMemoryBudget budget;
    private long threshold;
    private long reserved;
    private UnsynchronizedByteArrayOutputStream memory =
            UnsynchronizedByteArrayOutputStream.builder().get();
    private long size;
    private Path spillFile;
    private OutputStream spill;

    /**
     * @param budget    shared budget, or null for a fixed {@code initialThreshold}
     * @param initialThreshold bytes allowed in memory before asking the budget for more
     */
    TranslatedBytes(TemporaryResources tmp, CacheMemoryBudget budget, long initialThreshold) {
        this.tmp = tmp;
        this.budget = budget;
        this.threshold = initialThreshold;
        if (budget != null) {
            reserved = budget.tryReserve(initialThreshold) > 0 ? initialThreshold : 0;
            threshold = reserved;
        }
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (spill == null && size + len > threshold && !grow(size + len)) {
            spillFile = tmp.createTempFile();
            spill = Files.newOutputStream(spillFile);
            memory.writeTo(spill);
            memory = null;
        }
        if (spill != null) {
            spill.write(b, off, len);
        } else {
            memory.write(b, off, len);
        }
        size += len;
    }

    // Extends the in-memory allowance from the budget in whole chunks; false => spill.
    private boolean grow(long needed) {
        if (budget == null) {
            return false;
        }
        while (threshold < needed) {
            if (budget.tryReserve(GROW_CHUNK) == 0) {
                return false;
            }
            reserved += GROW_CHUNK;
            threshold += GROW_CHUNK;
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        if (spill != null) {
            spill.close();
        }
    }

    /** Returns the reservation to the budget; call once the digest is done with the bytes. */
    void release() {
        if (budget != null && reserved > 0) {
            budget.release(reserved);
            reserved = 0;
        }
    }

    boolean isInMemory() {
        return spill == null;
    }

    /** The translated content; the caller closes it. */
    TikaInputStream toTikaInputStream() throws IOException {
        return spill == null ? TikaInputStream.get(memory.toByteArray()) : TikaInputStream.get(spillFile);
    }
}
