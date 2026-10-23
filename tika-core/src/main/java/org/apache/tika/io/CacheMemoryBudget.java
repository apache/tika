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
 * A shared, bounded memory budget for in-memory stream caching.
 * <p>
 * When present, stream caches hold content in memory past the per-object threshold by
 * reserving buffer <em>capacity</em> against this budget, spilling to a temp file when a
 * reservation fails. Content up to the per-object threshold is not accounted here. Place a
 * single, process-wide instance in the {@link org.apache.tika.parser.ParseContext}; it is
 * bridged to the IO layer via {@link TikaInputStream#enableRewind(CacheMemoryBudget)}.
 * <p>
 * Thread-safe; a single instance may be shared across concurrent parses.
 */
public final class CacheMemoryBudget {

    private final long maxBytes;
    private final AtomicLong reserved = new AtomicLong();

    /**
     * @param maxBytes maximum total bytes that may be held in memory across all caches sharing
     *                 this budget; must be positive (to disable budgeting, pass no budget at all)
     */
    public CacheMemoryBudget(long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be > 0: " + maxBytes);
        }
        this.maxBytes = maxBytes;
    }

    /**
     * Attempts to reserve {@code n} bytes. All-or-nothing: either the full amount is reserved
     * (return {@code n}) or nothing is (return {@code 0}, signalling the caller to spill).
     *
     * @param n bytes requested
     * @return {@code n} if reserved, else {@code 0}
     */
    public long tryReserve(long n) {
        if (n <= 0) {
            return 0;
        }
        while (true) {
            long cur = reserved.get();
            if (cur + n > maxBytes) {
                return 0;
            }
            if (reserved.compareAndSet(cur, cur + n)) {
                return n;
            }
        }
    }

    /**
     * Releases {@code n} previously-reserved bytes back to the budget.
     */
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
