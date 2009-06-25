/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.io;

import java.io.IOException;

/**
 * An {@link IOException} wrapper that tags the wrapped exception with
 * a given object reference. Both the tag and the wrapped original exception
 * can be used to determine further processing when this exception is caught.
 */
public class TaggedIOException extends IOExceptionWithCause {

    /**
     * The object reference used to tag the exception.
     */
    private final Object tag;

    /**
     * Creates a tagged wrapper for the given exception.
     *
     * @param original the exception to be tagged
     * @param tag tag object
     */
    public TaggedIOException(IOException original, Object tag) {
        super(original.getMessage(), original);
        this.tag = tag;
    }

    /**
     * Returns the object reference used as the tag this exception.
     *
     * @return tag object
     */
    public Object getTag() {
        return tag;
    }

    /**
     * Returns the wrapped exception. The only difference to the overridden
     * {@link Throwable#getCause()} method is the narrower return type.
     *
     * @return wrapped exception
     */
    @Override
    public IOException getCause() {
        return (IOException) super.getCause();
    }

}
