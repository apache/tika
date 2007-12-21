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
package org.apache.tika.exception;

/**
 * IOException subclass with the {@link Throwable} constructor that's
 * missing before Java 6.
 */
public class CauseIOException extends java.io.IOException {

    /**
     * Creates an IOException with the given message.
     *
     * @param message exception message
     */
    public CauseIOException(String message) {
        super(message);
    }

    /**
     * Creates an IOException with the given message and root cause.
     * <p>
     * This constructor was not added in the underlying
     * {@link java.io.IOException) class until Java 6.
     * This is a convenience method which uses the
     * {@link #IOException(String)} constructor and calls the
     * {@link #initCause(Throwable)} method to set the root cause.
     *
     * @param message exception message
     * @param cause root cause
     */
    public CauseIOException(String message, Throwable cause) {
        this(message);
        initCause(cause);
    }

}
