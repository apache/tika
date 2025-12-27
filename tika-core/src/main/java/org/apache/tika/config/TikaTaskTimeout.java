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
 * Configuration class for specifying parse task timeout.
 * <p>
 * This is a config POJO (not a component like Parser/Detector), so it uses
 * standard Jackson format rather than compact component format:
 * <pre>
 * {
 *   "parse-context": {
 *     "tika-task-timeout": {
 *       "timeoutMillis": 30000
 *     }
 *   }
 * }
 * </pre>
 */
@TikaComponent(spi = false)
public class TikaTaskTimeout implements Serializable {

    private long timeoutMillis;

    /**
     * No-arg constructor for Jackson deserialization.
     */
    public TikaTaskTimeout() {
    }

    /**
     * Constructor with timeout value.
     *
     * @param timeoutMillis timeout in milliseconds
     */
    public TikaTaskTimeout(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public static long getTimeoutMillis(ParseContext context, long defaultTimeoutMillis) {
        if (context == null) {
            return defaultTimeoutMillis;
        }
        TikaTaskTimeout tikaTaskTimeout = context.get(TikaTaskTimeout.class);
        if (tikaTaskTimeout == null) {
            return defaultTimeoutMillis;
        }
        return tikaTaskTimeout.getTimeoutMillis();
    }
}
