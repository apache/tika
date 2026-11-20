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
package org.apache.tika.config;

import java.io.Serializable;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TempLimitReachedException;
import org.apache.tika.io.TempBytesBudget;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.parser.ParseContext;

/**
 * Limit on the temp files Tika writes for one task.
 * <p>
 * {@code maxTempBytes} bounds the bytes held at one time in the temp files Tika itself
 * writes for a task: spooled streams, parsers' own temp files, a document a fetcher
 * downloads to temp, and the output of external programs once they have written it.
 * Reaching it fails the write with a
 * {@link org.apache.tika.exception.TempLimitReachedException}, which fails the task.
 * Temp files written inside a library are not counted. Default 50 GiB; {@code -1} for
 * no limit; {@code 0} and anything below {@code -1} are rejected.
 * <p>
 * Applied by tika-pipes, which turns it into one {@link TempBytesBudget} per task. Parsing
 * in-process, nothing is limited unless the caller puts a budget in the context.
 * <pre>
 * {
 *   "parse-context": {
 *     "temp-limits": {
 *       "maxTempBytes": 10737418240
 *     }
 *   }
 * }
 * </pre>
 *
 * @since Apache Tika 4.3
 */
@TikaComponent(spi = false)
public class TempLimits implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final long UNLIMITED = -1;

    public static final long DEFAULT_MAX_TEMP_BYTES = 50L * 1024 * 1024 * 1024;

    private long maxTempBytes = DEFAULT_MAX_TEMP_BYTES;

    public TempLimits() {
    }

    public TempLimits(long maxTempBytes) {
        setMaxTempBytes(maxTempBytes);
    }

    public long getMaxTempBytes() {
        return maxTempBytes;
    }

    public void setMaxTempBytes(long maxTempBytes) {
        if (maxTempBytes == 0 || maxTempBytes < UNLIMITED) {
            throw new IllegalArgumentException(
                    "maxTempBytes must be positive, or -1 for no limit: " + maxTempBytes);
        }
        this.maxTempBytes = maxTempBytes;
    }

    /** The context's limits, or the defaults. */
    public static TempLimits get(ParseContext context) {
        TempLimits limits = context == null ? null : context.get(TempLimits.class);
        return limits != null ? limits : new TempLimits();
    }

    /** A budget for one task, or {@code null} for no limit. */
    public TempBytesBudget newBudget() {
        return maxTempBytes < 0 ? null : new TempBytesBudget(maxTempBytes);
    }

    /**
     * Puts {@code tis} under the context's {@link TempBytesBudget}, if it has one. Call it
     * wherever a stream enters a parse. Nothing here creates the budget: a pipes fork
     * seeds one per task from these limits, and a caller parsing in-process sets its own.
     *
     * @throws TempLimitReachedException if the task has already reached its limit
     */
    public static void apply(TikaInputStream tis, ParseContext context)
            throws TempLimitReachedException {
        if (tis != null && context != null) {
            tis.setTempBudget(context.get(TempBytesBudget.class));
        }
        throwIfReached(context);
    }

    /** True once the context's budget has refused a write. */
    public static boolean isReached(ParseContext context) {
        TempBytesBudget budget = context == null ? null : context.get(TempBytesBudget.class);
        return budget != null && budget.isLimitReached();
    }

    /**
     * Fails a task that has reached its limit, whatever became of the exception that
     * reported it first.
     */
    public static void throwIfReached(ParseContext context) throws TempLimitReachedException {
        if (isReached(context)) {
            throw new TempLimitReachedException(context.get(TempBytesBudget.class).getMaxBytes());
        }
    }

    /** As {@link #apply(TikaInputStream, ParseContext)}, for temp files a parser writes itself. */
    public static void apply(TemporaryResources tmp, ParseContext context) {
        if (tmp != null && context != null) {
            tmp.setTempBudget(context.get(TempBytesBudget.class));
        }
    }

    /** This, or a copy no looser than {@code ceiling}. */
    public TempLimits tightenedTo(TempLimits ceiling) {
        if (ceiling.maxTempBytes < 0
                || (maxTempBytes >= 0 && maxTempBytes <= ceiling.maxTempBytes)) {
            return this;
        }
        return new TempLimits(ceiling.maxTempBytes);
    }

    @Override
    public String toString() {
        return "TempLimits{maxTempBytes=" + maxTempBytes + '}';
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TempLimits && ((TempLimits) o).maxTempBytes == maxTempBytes;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(maxTempBytes);
    }
}
