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

import org.apache.tika.parser.ParseContext;

/**
 * Configuration for task timeout limits.
 * <p>
 * This controls the maximum time allowed for a parse operation:
 * <ul>
 *   <li>{@code taskTimeoutMillis} - maximum time in milliseconds for a parse task (default: 60000)</li>
 * </ul>
 * <p>
 * <b>Timeout behavior:</b> When the timeout is reached, the parse task is killed and a
 * {@code TikaTimeoutException} is thrown. There is no silent truncation option for timeouts.
 * <p>
 * <b>Warning:</b> If {@code taskTimeoutMillis} is shorter than parser-specific timeouts
 * (e.g., TesseractOCRConfig.timeoutSeconds, ExternalParserConfig.timeoutMs), the task will
 * be killed before the parser completes. Common conflicts to watch for:
 * <ul>
 *   <li>TesseractOCRConfig.timeoutSeconds default: 120s (120000ms)</li>
 *   <li>ExternalParserConfig.timeoutMs default: 60s (60000ms)</li>
 *   <li>StringsConfig.timeoutSeconds default: 120s (120000ms)</li>
 * </ul>
 * <p>
 * Example configuration:
 * <pre>
 * {
 *   "other-configs": {
 *     "timeout-limits": {
 *       "taskTimeoutMillis": 120000
 *     }
 *   }
 * }
 * </pre>
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(spi = false)
public class TimeoutLimits implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final long DEFAULT_TASK_TIMEOUT_MILLIS = 60_000;

    private long taskTimeoutMillis = DEFAULT_TASK_TIMEOUT_MILLIS;

    /**
     * No-arg constructor for Jackson deserialization.
     */
    public TimeoutLimits() {
    }

    /**
     * Constructor with timeout parameter.
     *
     * @param taskTimeoutMillis maximum time in milliseconds for a parse task
     */
    public TimeoutLimits(long taskTimeoutMillis) {
        this.taskTimeoutMillis = taskTimeoutMillis;
    }

    /**
     * Gets the maximum time in milliseconds for a parse task.
     *
     * @return task timeout in milliseconds
     */
    public long getTaskTimeoutMillis() {
        return taskTimeoutMillis;
    }

    /**
     * Sets the maximum time in milliseconds for a parse task.
     *
     * @param taskTimeoutMillis task timeout in milliseconds
     */
    public void setTaskTimeoutMillis(long taskTimeoutMillis) {
        this.taskTimeoutMillis = taskTimeoutMillis;
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
                "taskTimeoutMillis=" + taskTimeoutMillis +
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
        return taskTimeoutMillis == that.taskTimeoutMillis;
    }

    @Override
    public int hashCode() {
        return (int) (taskTimeoutMillis ^ (taskTimeoutMillis >>> 32));
    }
}
