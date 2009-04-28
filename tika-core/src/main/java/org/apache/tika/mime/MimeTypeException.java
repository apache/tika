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
package org.apache.tika.mime;

/**
 * A class to encapsulate MimeType related exceptions.
 * 
 * 
 */
public class MimeTypeException extends Exception {

    /**
     * Constructs a MimeTypeException with no specified detail message.
     */
    public MimeTypeException() {
        super();
    }

    /**
     * Constructs a MimeTypeException with the specified detail message.
     * 
     * @param msg
     *            the detail message.
     */
    public MimeTypeException(String msg) {
        super(msg);
    }

    /**
     * Constructs a MimeTypeException with the specified cause.
     * 
     * @param t
     *            the cause.
     */
    public MimeTypeException(Throwable t) {
        super(t);
    }
}
