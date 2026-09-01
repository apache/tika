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
 * How much detail Tika reports when an exception is turned into a string: the
 * {@code tk:exception:*} metadata values, tika-server error bodies and pipes result messages.
 * <p>
 * Stack traces and exception messages can carry file paths, hostnames and fragments of the
 * document being parsed. Operators who expose Tika to untrusted callers can reduce that with
 * {@link Level} and bound the size with {@code maxLength}.
 * <ul>
 *   <li>{@link Level#FULL} - the complete stack trace (default)</li>
 *   <li>{@link Level#MESSAGE_REDACTED} - the complete stack trace with every exception message
 *   removed; class names, frames and the cause chain are kept</li>
 *   <li>{@link Level#REDACTED} - exception class names only (the cause chain, no frames,
 *   no messages)</li>
 * </ul>
 * {@code maxLength} caps the formatted string: a longer value is cut to {@code maxLength}
 * characters and a {@code ...[truncated]} marker is appended (-1 = unlimited).
 * <p>
 * Loaded from the {@code parse-context} section of the config and deliberately not settable
 * per request: a caller could otherwise turn redaction back off.
 *
 * @since Apache Tika 4.1
 */
@TikaComponent(name = "exception-reporting", spi = false)
public class ExceptionReporting implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Level {
        REDACTED, MESSAGE_REDACTED, FULL
    }

    public static final int UNLIMITED = -1;

    private Level level = Level.FULL;
    private int maxLength = UNLIMITED;

    public ExceptionReporting() {
    }

    public ExceptionReporting(Level level, int maxLength) {
        setLevel(level);
        setMaxLength(maxLength);
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        //0 is rejected rather than treated as "report nothing": it would emit only the marker
        if (maxLength < UNLIMITED || maxLength == 0) {
            throw new IllegalArgumentException(
                    "maxLength must be positive or -1 for unlimited, was " + maxLength);
        }
        this.maxLength = maxLength;
    }

    /**
     * @return the context's policy, or a new instance with the defaults
     */
    public static ExceptionReporting get(ParseContext context) {
        if (context == null) {
            return new ExceptionReporting();
        }
        ExceptionReporting reporting = context.get(ExceptionReporting.class);
        return reporting != null ? reporting : new ExceptionReporting();
    }

    @Override
    public String toString() {
        return "ExceptionReporting{level=" + level + ", maxLength=" + maxLength + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExceptionReporting that = (ExceptionReporting) o;
        return maxLength == that.maxLength && level == that.level;
    }

    @Override
    public int hashCode() {
        return 31 * level.hashCode() + maxLength;
    }
}
