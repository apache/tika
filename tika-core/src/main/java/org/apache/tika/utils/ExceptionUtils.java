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
package org.apache.tika.utils;


import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.apache.tika.config.ExceptionReporting;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;

/**
 * Turns a {@link Throwable} into the string Tika reports to callers, under the
 * {@link ExceptionReporting} policy in effect. Every place Tika records or emits an exception
 * as text should go through {@link #format(Throwable, ParseContext)} so that one config
 * setting governs all channels.
 * <p>
 * NOTE: If your stacktraces are truncated, make sure to start your jvm
 * with: -XX:-OmitStackTraceInFastThrow
 */
public class ExceptionUtils {

    private static final int MAX_CAUSE_DEPTH = 64;
    private static final String TRUNCATED = "...[truncated]";

    /**
     * Formats {@code t} under the {@link ExceptionReporting} found in {@code context}
     * (or {@link ExceptionReporting#DEFAULT} if the context is null or has none).
     */
    public static String format(Throwable t, ParseContext context) {
        return format(t, ExceptionReporting.get(context));
    }

    /**
     * Formats {@code t} under {@code reporting}; a null policy means
     * {@link ExceptionReporting#DEFAULT}. For channels that have no ParseContext, such as
     * server error responses and pipes crash messages.
     */
    public static String format(Throwable t, ExceptionReporting reporting) {
        if (reporting == null) {
            reporting = ExceptionReporting.DEFAULT;
        }
        String s;
        switch (reporting.getLevel()) {
            case REDACTED:
                s = redacted(t, false);
                break;
            case MESSAGE_REDACTED:
                s = redacted(t, true);
                break;
            default:
                s = full(t);
        }
        return truncate(s, reporting.getMaxLength());
    }

    /**
     * A bare {@link TikaException} wrapper adds nothing to a report; returns its cause
     * instead. Subclasses and unwrapped throwables are returned as is.
     */
    public static Throwable unwrapTikaException(Throwable t) {
        if (t.getClass().equals(TikaException.class) && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    /**
     * @deprecated use {@link #format(Throwable, ParseContext)} on
     * {@link #unwrapTikaException(Throwable)} so the configured policy applies
     */
    @Deprecated
    public static String getFilteredStackTrace(Throwable t) {
        return format(unwrapTikaException(t), ExceptionReporting.DEFAULT);
    }

    /**
     * @deprecated use {@link #format(Throwable, ParseContext)} so the configured policy applies
     */
    @Deprecated
    public static String getStackTrace(Throwable t) {
        return format(t, ExceptionReporting.DEFAULT);
    }

    private static String full(Throwable t) {
        StringWriter result = new StringWriter();
        try (PrintWriter writer = new PrintWriter(result)) {
            t.printStackTrace(writer);
        }
        return result.toString();
    }

    private static String truncate(String s, int maxLength) {
        if (maxLength < 0 || s.length() <= maxLength) {
            return s;
        }
        int cut = maxLength;
        if (Character.isHighSurrogate(s.charAt(cut - 1))) {
            cut--;
        }
        return s.substring(0, cut) + TRUNCATED;
    }

    /**
     * Mirrors {@link Throwable#printStackTrace} (frame elision, suppressed, causes, cycle
     * guard) but never calls {@code toString()}/{@code getMessage()}; with {@code frames}
     * false only the class-name chain is emitted.
     */
    private static String redacted(Throwable t, boolean frames) {
        StringBuilder sb = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(t);
        sb.append(t.getClass().getName()).append('\n');
        StackTraceElement[] trace = t.getStackTrace();
        if (frames) {
            for (StackTraceElement e : trace) {
                sb.append("\tat ").append(e).append('\n');
            }
        }
        for (Throwable s : t.getSuppressed()) {
            enclosed(s, trace, "Suppressed: ", "\t", seen, sb, frames, 1);
        }
        Throwable cause = t.getCause();
        if (cause != null) {
            enclosed(cause, trace, "Caused by: ", "", seen, sb, frames, 1);
        }
        return sb.toString();
    }

    private static void enclosed(Throwable t, StackTraceElement[] enclosingTrace, String caption,
                                 String prefix, Set<Throwable> seen, StringBuilder sb,
                                 boolean frames, int depth) {
        if (seen.contains(t)) {
            sb.append(prefix).append(caption).append("[CIRCULAR REFERENCE: ")
                    .append(t.getClass().getName()).append("]\n");
            return;
        }
        seen.add(t);
        if (depth > MAX_CAUSE_DEPTH) {
            sb.append(prefix).append("... cause chain truncated\n");
            return;
        }
        sb.append(prefix).append(caption).append(t.getClass().getName()).append('\n');
        StackTraceElement[] trace = t.getStackTrace();
        if (frames) {
            int m = trace.length - 1;
            int n = enclosingTrace.length - 1;
            while (m >= 0 && n >= 0 && trace[m].equals(enclosingTrace[n])) {
                m--;
                n--;
            }
            int framesInCommon = trace.length - 1 - m;
            for (int i = 0; i <= m; i++) {
                sb.append(prefix).append("\tat ").append(trace[i]).append('\n');
            }
            if (framesInCommon != 0) {
                sb.append(prefix).append("\t... ").append(framesInCommon).append(" more\n");
            }
        }
        for (Throwable s : t.getSuppressed()) {
            enclosed(s, trace, "Suppressed: ", prefix + "\t", seen, sb, frames, depth + 1);
        }
        Throwable cause = t.getCause();
        if (cause != null) {
            enclosed(cause, trace, "Caused by: ", prefix, seen, sb, frames, depth + 1);
        }
    }
}
