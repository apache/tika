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
 * deletes its files, so a container's entries spooled one after another each see the
 * whole budget.
 * <p>
 * It covers only temp files written through that method. A library or an external
 * program that writes its own temp files is not counted.
 * <p>
 * Thread-safe.
 *
 * @since Apache Tika 4.3
 */
public final class TempBytesBudget {

    private final long maxBytes;
    private final AtomicLong reserved = new AtomicLong();

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
    public boolean tryReserve(long n) {
        if (n <= 0) {
            return true;
        }
        while (true) {
            long cur = reserved.get();
            if (n > maxBytes - cur) {
                return false;
            }
            if (reserved.compareAndSet(cur, cur + n)) {
                return true;
            }
        }
    }

    public void release(long n) {
        if (n > 0) {
            reserved.addAndGet(-n);
        }
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public long getReservedBytes() {
        return reserved.get();
    }
}
