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
package org.apache.tika.exception;

/**
 * Thrown when a single operation (external process, HTTP call, pool borrow, etc.)
 * exceeds its allotted timeout.
 * <p>
 * Checked, not a {@link RuntimeException}: expected to be caught at the nearest
 * embedded-document boundary (see {@code ParsingEmbeddedDocumentExtractor}), recorded,
 * and parsing of remaining siblings continued. This differs from the task's total
 * deadline being exhausted, which does not throw at all -- see {@code ParseRecord}'s
 * {@code taskDeadlineReached} handling.
 * <p>
 * When the caller got its budget via {@code ParseTimeout.budgetFor(long)}, use
 * {@link #TikaTimeoutException(String, long, long)} so the message states both the
 * requested and granted budget: {@code granted == requested} means the operation's own
 * timeout was binding; {@code granted < requested} means the task's total timeout was
 * binding and raising the operation's timeout won't help.
 *
 * @since Apache Tika 4.0
 */
public class TikaTimeoutException extends TikaException {

    private static final long UNKNOWN = -1;

    private final long requestedMillis;
    private final long grantedMillis;

    public TikaTimeoutException(String message) {
        this(message, UNKNOWN, UNKNOWN);
    }

    /**
     * @param requestedMillis the timeout the caller's own configuration asked for
     * @param grantedMillis   the budget actually granted, e.g. by {@code ParseTimeout.budgetFor}
     */
    public TikaTimeoutException(String message, long requestedMillis, long grantedMillis) {
        super(buildMessage(message, requestedMillis, grantedMillis));
        this.requestedMillis = requestedMillis;
        this.grantedMillis = grantedMillis;
    }

    private static String buildMessage(String message, long requestedMillis, long grantedMillis) {
        if (requestedMillis == UNKNOWN || grantedMillis == UNKNOWN) {
            return message;
        }
        String clippedBy = grantedMillis < requestedMillis ? " (task remaining)" : " -- budget exhausted";
        return message + ": requested=" + requestedMillis + "ms, granted=" + grantedMillis + "ms" + clippedBy;
    }

    /**
     * @return the timeout the caller's own configuration requested, or {@code -1} if
     * this exception was not constructed with that information
     */
    public long getRequestedMillis() {
        return requestedMillis;
    }

    /**
     * @return the budget actually granted, or {@code -1} if this exception was not
     * constructed with that information
     */
    public long getGrantedMillis() {
        return grantedMillis;
    }

    /**
     * @return true if the granted budget was clipped below the requested timeout by the
     * task's remaining time (the task's total timeout was binding, not the operation's
     * own). False -- including when no requested/granted info was recorded -- means the
     * operation's own timeout was binding.
     */
    public boolean isClippedByRemaining() {
        return requestedMillis != UNKNOWN && grantedMillis != UNKNOWN && grantedMillis < requestedMillis;
    }
}
