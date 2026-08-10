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
 *   <li>{@code totalTaskTimeoutMillis} — bounds entire task wall-clock time
 *       (default: 3,600,000 ms = 1 hour)</li>
 *   <li>{@code progressTimeoutMillis} — bounds time since the last progress update;
 *       catches infinite loops and hung processes (default: 120,000 ms = 2 minutes)</li>
 * </ul>
 * <p>
 * Parsers that never call {@link TikaProgressTracker#update()} effectively get
 * {@code progressTimeoutMillis} as their total timeout (same as the old single-timeout
 * behavior). Parsers that <em>do</em> update progress can run up to
 * {@code totalTaskTimeoutMillis}.
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

    /**
     * Also caps how long a single external process may run (see
     * {@link #getProcessTimeoutMillis(ParseContext, long)}), so this must not be
     * shorter than the per-process timeouts the bundled process-spawning parsers
     * default to, or those defaults become unreachable.
     */
    public static final long DEFAULT_PROGRESS_TIMEOUT_MILLIS = 120_000L;

    private long totalTaskTimeoutMillis = DEFAULT_TOTAL_TASK_TIMEOUT_MILLIS;
    private long progressTimeoutMillis = DEFAULT_PROGRESS_TIMEOUT_MILLIS;

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

    /**
     * Returns the per-process timeout to use for external process execution.
     * <p>
     * External processes must not outlive the progress watchdog: a parser only
     * reports progress once its process has finished, so a process allowed to run
     * past {@code progressTimeoutMillis} would be killed as a hang. This caps the
     * caller's timeout at {@code progressTimeoutMillis - 100}, leaving the monitoring
     * loop a small window to observe the process exit first.
     * <p>
     * The cap is a ceiling, not a replacement: a caller asking for less than the cap
     * keeps its own shorter value. Falls back to {@code defaultMs} when no
     * TimeoutLimits is in the context.
     *
     * @param context   the ParseContext (may be null)
     * @param defaultMs the caller's configured timeout; also used if no TimeoutLimits
     *                  is in the context
     * @return timeout in milliseconds for external process execution
     */
    public static long getProcessTimeoutMillis(ParseContext context, long defaultMs) {
        if (context == null) {
            return defaultMs;
        }
        TimeoutLimits limits = context.get(TimeoutLimits.class);
        if (limits == null) {
            return defaultMs;
        }
        return Math.max(0, Math.min(defaultMs, limits.progressTimeoutMillis - 100));
    }

    @Override
    public String toString() {
        return "TimeoutLimits{" +
                "totalTaskTimeoutMillis=" + totalTaskTimeoutMillis +
                ", progressTimeoutMillis=" + progressTimeoutMillis +
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
                progressTimeoutMillis == that.progressTimeoutMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalTaskTimeoutMillis, progressTimeoutMillis);
    }
}
