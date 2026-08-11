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
import java.util.Objects;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.parser.ParseContext;

/**
 * Configuration for the two-tier task timeout system.
 * <p>
 * <ul>
 *   <li>{@code totalTaskTimeoutMillis} — bounds entire task wall-clock time, including
 *       any embedded documents it recurses into
 *       (default: 3,600,000 ms = 1 hour)</li>
 *   <li>{@code progressTimeoutMillis} — bounds time since the last progress update;
 *       catches infinite loops and hung processes (default: 120,000 ms = 2 minutes)</li>
 * </ul>
 * <p>
 * These compose with any per-parser timeout via {@link ParseTimeout#budgetFor(long)}: a
 * parser's own timeout is honored, but no operation gets more than what remains of
 * {@code totalTaskTimeoutMillis}. A bounded external call reports its own progress, so a
 * legitimately long call (e.g. a multi-minute external process) doesn't need
 * {@code progressTimeoutMillis} raised to accommodate it — see
 * {@link org.apache.tika.utils.ProcessUtils#execute}.
 * <p>
 * Example configuration:
 * <pre>
 * {
 *   "parse-context": {
 *     "timeout-limits": {
 *       "totalTaskTimeoutMillis": 3600000,
 *       "progressTimeoutMillis": 60000
 *     }
 *   }
 * }
 * </pre>
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(spi = false)
public class TimeoutLimits implements Serializable {

    private static final long serialVersionUID = 2L;

    public static final long DEFAULT_TOTAL_TASK_TIMEOUT_MILLIS = 3_600_000L;
    public static final long DEFAULT_PROGRESS_TIMEOUT_MILLIS = 120_000L;

    private long totalTaskTimeoutMillis = DEFAULT_TOTAL_TASK_TIMEOUT_MILLIS;
    private long progressTimeoutMillis = DEFAULT_PROGRESS_TIMEOUT_MILLIS;
    private boolean throwOnDeadline = false;

    /**
     * No-arg constructor for Jackson deserialization.
     */
    public TimeoutLimits() {
    }

    /**
     * Constructor with both timeout parameters.
     *
     * @param totalTaskTimeoutMillis maximum wall-clock time for a task
     * @param progressTimeoutMillis  maximum time between progress updates
     */
    public TimeoutLimits(long totalTaskTimeoutMillis, long progressTimeoutMillis) {
        this.totalTaskTimeoutMillis = totalTaskTimeoutMillis;
        this.progressTimeoutMillis = progressTimeoutMillis;
    }

    /**
     * Gets the maximum wall-clock time in milliseconds for a parse task.
     *
     * @return total task timeout in milliseconds
     */
    public long getTotalTaskTimeoutMillis() {
        return totalTaskTimeoutMillis;
    }

    /**
     * Sets the maximum wall-clock time in milliseconds for a parse task.
     *
     * @param totalTaskTimeoutMillis total task timeout in milliseconds
     */
    public void setTotalTaskTimeoutMillis(long totalTaskTimeoutMillis) {
        this.totalTaskTimeoutMillis = totalTaskTimeoutMillis;
    }

    /**
     * Gets the maximum time in milliseconds between progress updates before
     * the task is considered stalled.
     *
     * @return progress timeout in milliseconds
     */
    public long getProgressTimeoutMillis() {
        return progressTimeoutMillis;
    }

    /**
     * Sets the maximum time in milliseconds between progress updates before
     * the task is considered stalled.
     *
     * @param progressTimeoutMillis progress timeout in milliseconds
     */
    public void setProgressTimeoutMillis(long progressTimeoutMillis) {
        this.progressTimeoutMillis = progressTimeoutMillis;
    }

    /**
     * Whether to throw when the task's total timeout is exhausted mid-parse, instead of
     * skipping remaining embedded documents and returning content extracted so far.
     * Default: {@code false}.
     */
    public boolean isThrowOnDeadline() {
        return throwOnDeadline;
    }

    public void setThrowOnDeadline(boolean throwOnDeadline) {
        this.throwOnDeadline = throwOnDeadline;
    }

    /**
     * Helper method to get TimeoutLimits from ParseContext with defaults.
     *
     * @param context the ParseContext (may be null)
     * @return the TimeoutLimits from context, or a new instance with defaults if not found
     */
    public static TimeoutLimits get(ParseContext context) {
        if (context == null) {
            return new TimeoutLimits();
        }
        TimeoutLimits limits = context.get(TimeoutLimits.class);
        return limits != null ? limits : new TimeoutLimits();
    }

    @Override
    public String toString() {
        return "TimeoutLimits{" +
                "totalTaskTimeoutMillis=" + totalTaskTimeoutMillis +
                ", progressTimeoutMillis=" + progressTimeoutMillis +
                ", throwOnDeadline=" + throwOnDeadline +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TimeoutLimits that = (TimeoutLimits) o;
        return totalTaskTimeoutMillis == that.totalTaskTimeoutMillis &&
                progressTimeoutMillis == that.progressTimeoutMillis &&
                throwOnDeadline == that.throwOnDeadline;
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalTaskTimeoutMillis, progressTimeoutMillis, throwOnDeadline);
    }
}
