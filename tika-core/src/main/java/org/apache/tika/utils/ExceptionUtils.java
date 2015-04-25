/**
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


import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;

public class ExceptionUtils {

    private final static Pattern MSG_PATTERN = Pattern.compile(":[^\r\n]+");

    /**
     * Simple util to get stack trace.
     * <p>
     * This will unwrap a TikaException and return the cause if not null
     * <p>
     * NOTE: If your stacktraces are truncated, make sure to start your jvm
     * with: -XX:-OmitStackTraceInFastThrow
     *
     * @param t throwable
     * @return
     * @throws IOException
     */
    public static String getFilteredStackTrace(Throwable t) {
        Throwable cause = t;
        if ((t instanceof TikaException) &&
                t.getCause() != null) {
            cause = t.getCause();
        }
        return getStackTrace(cause);
    }

    /**
     * Get the full stacktrace as a string
     * @param t
     * @return
     */
    public static String getStackTrace(Throwable t) {
        Writer result = new StringWriter();
        PrintWriter writer = new PrintWriter(result);
        t.printStackTrace(writer);
        try {
            writer.flush();
            result.flush();
            writer.close();
            result.close();
        } catch (IOException e) {
            //swallow
        }
        return result.toString();
    }

    /**
     * Utility method to trim the message from a stack trace
     * string.
     * <p>
     * E.g. <code>java.lang.IllegalStateException: Potential loop detected </code>
     * will be trimmed to <code>java.lang.IllegalStateException</code>
     * @param trace string view of stack trace
     * @return trimmed stack trace
     */
    public static String trimMessage(String trace) {
        Matcher msgMatcher = MSG_PATTERN.matcher(trace);
        if (msgMatcher.find()) {
            return msgMatcher.replaceFirst("");
        }
        return trace;
    }
}
