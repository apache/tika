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
 * Runtime exception thrown when an embedded document limit is reached
 * and the configuration specifies that parsing should stop with an exception.
 * <p>
 * This is a runtime exception to avoid polluting the {@code EmbeddedDocumentExtractor}
 * interface with checked exceptions, since most implementations don't need limit checking.
 *
 * @since Apache Tika 3.2
 */
public class EmbeddedLimitReachedException extends RuntimeException {

    public enum LimitType {
        MAX_DEPTH,
        MAX_COUNT,
        /** The task's total timeout was exhausted; see {@code ParseRecord#isTaskDeadlineReached()}. */
        DEADLINE
    }

    private final LimitType limitType;
    private final long limit;

    /**
     * @param limit the configured limit -- for {@link LimitType#DEADLINE} this is
     *              {@code totalTaskTimeoutMillis}, not a count or depth
     */
    public EmbeddedLimitReachedException(LimitType limitType, long limit) {
        super(buildMessage(limitType, limit));
        this.limitType = limitType;
        this.limit = limit;
    }

    private static String buildMessage(LimitType limitType, long limit) {
        switch (limitType) {
            case MAX_DEPTH:
                return "Max embedded depth reached: " + limit;
            case MAX_COUNT:
                return "Max embedded count reached: " + limit;
            case DEADLINE:
                return "Task deadline reached: totalTaskTimeoutMillis=" + limit;
            default:
                return "Embedded limit reached: " + limit;
        }
    }

    public LimitType getLimitType() {
        return limitType;
    }

    /**
     * @return the configured limit, e.g. {@code totalTaskTimeoutMillis} for
     * {@link LimitType#DEADLINE}
     */
    public long getLimit() {
        return limit;
    }
}
