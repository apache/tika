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
import java.io.InputStream;

/**
 * An input stream decorator that tags potential exceptions so that the
 * stream that caused the exception can easily be identified. This is
 * done by using the {@link TaggedIOException} class to wrap all thrown
 * {@link IOException}s. See below for an example of using this class.
 * <pre>
 * TaggedInputStream stream = new TaggedInputStream(...);
 * try {
 *     // Processing that may throw an IOException either from this stream
 *     // or from some other IO activity like temporary files, etc.
 *     processStream(stream);
 * } catch (IOException e) {
 *     if (stream.isCauseOf(e)) {
 *         // The exception was caused by this stream.
 *         // Use e.getCause() to get the original exception.
 *     } else {
 *         // The exception was caused by something else.
 *     }
 * }
 * </pre>
 * <p>
 * Alternatively, the {@link #throwIfCauseOf(Exception)} method can be
 * used to let higher levels of code handle the exception caused by this
 * stream while other processing errors are being taken care of at this
 * lower level.
 * <pre>
 * TaggedInputStream stream = new TaggedInputStream(...);
 * try {
 *     processStream(stream);
 * } catch (IOException e) {
 *     stream.throwIfCauseOf(e);
 *     // ... or process the exception that was caused by something else
 * }
 * </pre>
 *
 * @see TaggedIOException
 */
public class TaggedInputStream extends ProxyInputStream {

    /**
     * Creates a tagging decorator for the given input stream.
     *
     * @param proxy input stream to be decorated
     */
    public TaggedInputStream(InputStream proxy) {
        super(proxy);
    }

    /**
     * Tests if the given exception was caused by this stream.
     *
     * @param exception an exception
     * @return <code>true</code> if the exception was thrown by this stream,
     *         <code>false</code> otherwise
     */
    public boolean isCauseOf(IOException exception) {
        if (exception instanceof TaggedIOException) {
            TaggedIOException tagged = (TaggedIOException) exception;
            return this == tagged.getTag();
        } else {
            return false;
        }
    }

    /**
     * Re-throws the original exception thrown by this stream. This method
     * first checks whether the given exception is a {@link TaggedIOException}
     * wrapper created by this decorator, and then unwraps and throws the
     * original wrapped exception. Returns normally if the exception was
     * not thrown by this stream.
     *
     * @param exception an exception
     * @throws IOException original exception, if any, thrown by this stream
     */
    public void throwIfCauseOf(Exception exception) throws IOException {
        if (exception instanceof TaggedIOException) {
            TaggedIOException tagged = (TaggedIOException) exception;
            if (this == tagged.getTag()) {
                throw tagged.getCause();
            }
        }
    }

    /**
     * Tags any IOExceptions thrown, wrapping and re-throwing.
     * 
     * @param e The IOException thrown
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void handleIOException(IOException e) throws IOException {
        throw new TaggedIOException(e, this);
    }

}
