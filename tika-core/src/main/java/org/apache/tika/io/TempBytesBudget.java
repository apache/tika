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

import java.util.concurrent.atomic.AtomicLong;

/**
 * A bound on the bytes Tika holds in temp files at one time on behalf of one task: the
 * disk counterpart of {@link CacheMemoryBudget}. Place one instance per task in the
 * {@link org.apache.tika.parser.ParseContext}; {@link TemporaryResources} charges what it
 * writes through {@link TemporaryResources#newOutputStream} and gives it back when it
 * deletes the file, at its close at the latest, so a container's entries spooled one
 * after another each see what the container itself is not holding.
 * <p>
 * It covers only temp files written through that method, and files something else wrote
 * that are reported afterwards through {@link TemporaryResources#chargeTempFile}, as Tika
 * does for the output of the external programs it runs. Temp files written inside a
 * library are not counted.
 * <p>
 * Reaching the limit is final: the task fails, so once a write has been refused every
 * later one is too, and {@link #isLimitReached()} stays true.
 * <p>
 * Thread-safe.
 *
 * @since Apache Tika 4.3
 */
public final class TempBytesBudget {

    private final long maxBytes;
    private final AtomicLong reserved = new AtomicLong();
    private volatile boolean limitReached;

    /**
     * @param maxBytes most bytes that may be held in temp files at once; must be positive
     *                 (for no limit, use no budget)
     */
    public TempBytesBudget(long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be > 0: " + maxBytes);
        }
        this.maxBytes = maxBytes;
    }

    /** All or nothing. */
    boolean tryReserve(long n) {
        if (limitReached) {
            return false;
        }
        if (n <= 0) {
            return true;
        }
        while (true) {
            long cur = reserved.get();
            if (n > maxBytes - cur) {
                limitReached = true;
                return false;
            }
            if (reserved.compareAndSet(cur, cur + n)) {
                return true;
            }
        }
    }

    void release(long n) {
        if (n > 0) {
            reserved.addAndGet(-n);
        }
    }

    /** True once a write has been refused; never reset. */
    public boolean isLimitReached() {
        return limitReached;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public long getReservedBytes() {
        return reserved.get();
    }
}
