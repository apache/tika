package org.apache.tika.util;
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

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.tika.exception.TikaException;

/**
 * Unwrap TikaExceptions and other wrappers that we might not care about
 * in downstream analysis.  This is similar to
 * what tika-server does when returning stack traces.
 */
public class TikaExceptionFilter {

    /**
     * Unwrap TikaExceptions and other wrappers that users might not
     * care about in downstream analysis.
     *
     * @param t throwable to filter
     * @return filtered throwable
     */
    public Throwable filter(Throwable t) {
        if (t instanceof TikaException) {
            Throwable cause = t.getCause();
            if (cause != null) {
                return cause;
            }
        }
        return t;
    }

    /**
     * This calls {@link #filter} and then prints the filtered
     * <code>Throwable</code>to a <code>String</code>.
     *
     * @param t throwable
     * @return a filtered version of the StackTrace
     */
    public String getStackTrace(Throwable t) {
        Throwable filtered = filter(t);
        StringWriter stringWriter = new StringWriter();
        PrintWriter w = new PrintWriter(stringWriter);
        filtered.printStackTrace(w);
        w.flush();
        stringWriter.flush();
        return stringWriter.toString();
    }
}
