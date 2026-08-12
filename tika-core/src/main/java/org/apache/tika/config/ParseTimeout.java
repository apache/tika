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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.parser.ParseContext;

/**
 * Runtime timeout state for a parse task, shared with any embedded documents it recurses into.
 * <p>
 * One instance per top-level task, created from {@link TimeoutLimits} and looked up from the
 * {@link ParseContext} at every nesting depth, so a budget request from an embedded document
 * (e.g. OCR on an image inside a zip inside a PDF) draws from the same remaining time as the
 * top-level task -- no per-depth bookkeeping needed.
 * <p>
 * All public accessors are relative to the task (elapsed/remaining/since-last-progress), not
 * anchored to wall-clock time -- internally this is backed by {@link System#nanoTime()}, so
 * a system clock adjustment mid-task doesn't affect it.
 * <p>
 * Runtime-only state (not Serializable); never sent over the wire.
 * <p>
 * Two responsibilities:
 * <ul>
 *   <li>{@link #budgetFor(long)} -- caps a requested budget at whatever remains of the task:
 *       {@code min(requested, remaining)}.</li>
 *   <li>{@link #checkpoint()} -- records progress. Bounded waits (e.g.
 *       {@link org.apache.tika.utils.ProcessUtils#execute}) checkpoint periodically so a long
 *       but legitimate external call isn't mistaken for a hang by the stall detector.</li>
 * </ul>
 *
 * @since Apache Tika 4.0
 */
public class ParseTimeout {

    private static final Logger LOG = LoggerFactory.getLogger(ParseTimeout.class);

    private final long startNanos;
    // Long.MAX_VALUE means unbounded. Kept in millis (not converted to a nanos deadline) so
    // remainingMillis() only ever subtracts a small elapsed value from it -- never adds to it --
    // which sidesteps overflow without needing a special case.
    private final long totalTimeoutMillis;
    private final long progressTimeoutMillis;
    private final AtomicLong lastProgressNanos;

    // Each fires at most once per task, not once per embedded document/operation.
    private final AtomicBoolean warnedNonPositiveRequest = new AtomicBoolean(false);
    private final AtomicBoolean warnedSubSecondRequest = new AtomicBoolean(false);
    private final AtomicBoolean warnedRequestExceedsTotal = new AtomicBoolean(false);

    private ParseTimeout(long startNanos, long totalTimeoutMillis, long progressTimeoutMillis) {
        this.startNanos = startNanos;
        this.totalTimeoutMillis = totalTimeoutMillis;
        this.progressTimeoutMillis = progressTimeoutMillis;
        this.lastProgressNanos = new AtomicLong(startNanos);
    }

    /**
     * Starts a new timeout window anchored to now, using the total and progress
     * timeouts from the given limits.
     * <p>
     * Rejects negative totals/progress (no coherent "less than no time"). Zero is
     * accepted (e.g. a task resuming with none of its budget left). A progress timeout
     * at or above a positive total is accepted but logged, since the stall detector
     * could then never fire before the total deadline.
     *
     * @throws IllegalArgumentException if either limit is negative
     */
    public static ParseTimeout start(TimeoutLimits limits) {
        long total = limits.getTotalTaskTimeoutMillis();
        long progress = limits.getProgressTimeoutMillis();
        if (total < 0) {
            throw new IllegalArgumentException("totalTaskTimeoutMillis must not be negative, was " + total);
        }
        if (progress < 0) {
            throw new IllegalArgumentException("progressTimeoutMillis must not be negative, was " + progress);
        }
        if (total > 0 && progress >= total) {
            LOG.warn("progressTimeoutMillis ({}) >= totalTaskTimeoutMillis ({}) -- the stall " +
                    "detector can never fire before the total deadline does", progress, total);
        }
        if (total > 0 && total < 1000) {
            LOG.warn("totalTaskTimeoutMillis ({}) is under one second -- this is often a " +
                    "seconds-vs-milliseconds mistake in the configuration", total);
        }
        if (progress > 0 && progress < 1000) {
            LOG.warn("progressTimeoutMillis ({}) is under one second -- this is often a " +
                    "seconds-vs-milliseconds mistake in the configuration", progress);
        }
        return new ParseTimeout(System.nanoTime(), total, progress);
    }

    /**
     * Returns the ParseTimeout installed in the given context, creating and installing
     * one (from {@link TimeoutLimits#get(ParseContext)}) if absent. Idempotent: the same
     * instance is reused for every call with the same context, including nested
     * embedded-document calls.
     *
     * @param context the ParseContext, may be null
     * @return the task's ParseTimeout, or a detached default if context is null
     */
    public static ParseTimeout getOrCreate(ParseContext context) {
        if (context == null) {
            return start(new TimeoutLimits());
        }
        ParseTimeout timeout = context.get(ParseTimeout.class);
        if (timeout == null) {
            timeout = start(TimeoutLimits.get(context));
            context.set(ParseTimeout.class, timeout);
        }
        return timeout;
    }

    /**
     * Records a checkpoint on the ParseTimeout in the given context, if present. Unlike
     * {@link #getOrCreate(ParseContext)}, this does not install one -- a checkpoint from
     * code outside any tracked task is simply a no-op.
     *
     * @param context the ParseContext, may be null
     */
    public static void checkpoint(ParseContext context) {
        if (context == null) {
            return;
        }
        ParseTimeout timeout = context.get(ParseTimeout.class);
        if (timeout != null) {
            timeout.checkpoint();
        }
    }

    /**
     * The single composition rule for nested timeouts: a requested budget is never
     * granted more time than remains for the whole task.
     * <p>
     * Also the chokepoint for misconfiguration diagnostics -- every per-parser timeout
     * flows through here, so validation lives once instead of per config class:
     * <ul>
     *   <li>a non-positive request is treated as "unset" (falls back to remaining task
     *       time) instead of granting zero, which would fail instantly with no useful
     *       diagnostic;</li>
     *   <li>a request under one second is logged -- usually a seconds-vs-milliseconds
     *       mistake;</li>
     *   <li>a request larger than the task's original total is logged, since it can never
     *       be granted in full even at the task's start (unlike the ordinary case of being
     *       clipped by elapsed time, which is not logged).</li>
     * </ul>
     * Each logs at most once per task.
     *
     * @return {@code min(requestedMillis, remainingMillis())}, or just {@code remainingMillis()}
     * if {@code requestedMillis} was non-positive
     */
    public long budgetFor(long requestedMillis) {
        if (requestedMillis <= 0) {
            if (!warnedNonPositiveRequest.getAndSet(true)) {
                LOG.warn("non-positive timeout requested ({}ms) -- treating as unset and using " +
                        "whatever remains of the task's total timeout instead", requestedMillis);
            }
            return remainingMillis();
        }
        if (requestedMillis < 1000 && !warnedSubSecondRequest.getAndSet(true)) {
            LOG.warn("a requested timeout of {}ms is under one second -- this is often a " +
                    "seconds-vs-milliseconds mistake in the caller's configuration", requestedMillis);
        }
        long total = getTotalTimeoutMillis();
        if (total != Long.MAX_VALUE && requestedMillis > total && !warnedRequestExceedsTotal.getAndSet(true)) {
            LOG.warn("a requested timeout of {}ms exceeds totalTaskTimeoutMillis ({}ms) -- it can " +
                    "never be granted in full; raise totalTaskTimeoutMillis or lower this timeout",
                    requestedMillis, total);
        }
        return Math.min(requestedMillis, remainingMillis());
    }

    /**
     * @return the task's original total timeout in milliseconds, or {@code Long.MAX_VALUE}
     * if unbounded -- unlike {@link #remainingMillis()}, this does not shrink over time
     */
    public long getTotalTimeoutMillis() {
        return totalTimeoutMillis;
    }

    /**
     * @return milliseconds elapsed since the task started
     */
    public long elapsedMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * @return milliseconds remaining before the task's total timeout, never negative
     */
    public long remainingMillis() {
        if (totalTimeoutMillis == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, totalTimeoutMillis - elapsedMillis());
    }

    /**
     * Records that progress happened. Never throws — cooperative cancellation
     * on an exhausted deadline happens at embedded-document boundaries (see
     * {@code ParseRecord}), not here.
     */
    public void checkpoint() {
        lastProgressNanos.set(System.nanoTime());
    }

    /**
     * @return milliseconds elapsed since the last checkpoint
     */
    public long millisSinceLastProgress() {
        return (System.nanoTime() - lastProgressNanos.get()) / 1_000_000L;
    }

    /**
     * @return the configured progress (stall-detection) timeout in milliseconds
     */
    public long getProgressTimeoutMillis() {
        return progressTimeoutMillis;
    }
}
