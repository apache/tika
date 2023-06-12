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

import org.xml.sax.SAXException;

public class WriteLimitReachedException extends SAXException {

    //in case of (hopefully impossible) cyclic exception
    private final static int MAX_DEPTH = 100;

    private final int writeLimit;
    public WriteLimitReachedException(int writeLimit) {
        this.writeLimit = writeLimit;
    }

    @Override
    public String getMessage() {
        return "Your document contained more than " + writeLimit
                + " characters, and so your requested limit has been"
                + " reached. To receive the full text of the document,"
                + " increase your limit. (Text up to the limit is"
                + " however available).";
    }
    /**
     * Checks whether the given exception (or any of it's root causes) was
     * thrown by this handler as a signal of reaching the write limit.
     *
     * @param t throwable
     * @return <code>true</code> if the write limit was reached,
     * <code>false</code> otherwise
     * @since Apache Tika 2.0
     */
    public static boolean isWriteLimitReached(Throwable t) {
        return isWriteLimitReached(t, 0);
    }

    private static boolean isWriteLimitReached(Throwable t, int depth) {
        if (t == null) {
            return false;
        }
        if (depth > MAX_DEPTH) {
            return false;
        }
        if (t instanceof WriteLimitReachedException) {
            return true;
        } else {
            return isWriteLimitReached(t.getCause(), depth + 1);
        }
    }

    public static void throwIfWriteLimitReached(Exception ex) throws SAXException {
        throwIfWriteLimitReached(ex, 0);
    }

    private static void throwIfWriteLimitReached(Throwable ex, int depth) throws SAXException {
        if (ex == null) {
            return;
        }
        if (depth > MAX_DEPTH) {
            return;
        }
        if (ex instanceof WriteLimitReachedException) {
            throw (SAXException) ex;
        } else {
            throwIfWriteLimitReached(ex.getCause(), depth + 1);
        }
    }
}
