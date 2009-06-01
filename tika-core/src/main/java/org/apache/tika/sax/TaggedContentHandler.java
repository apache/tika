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
package org.apache.tika.sax;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A content handler decorator that tags potential exceptions so that the
 * handler that caused the exception can easily be identified. This is
 * done by using the {@link TaggedSAXException} class to wrap all thrown
 * {@link SAXException}s. See below for an example of using this class.
 * <pre>
 * TaggedContentHandler handler = new TaggedContentHandler(...);
 * try {
 *     // Processing that may throw an SAXException either from this handler
 *     // or from some other XML parsing activity
 *     processXML(handler);
 * } catch (SAXException e) {
 *     if (handler.isCauseOf(e)) {
 *         // The exception was caused by this handler.
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
 * TaggedContentHandler handler = new TaggedContentHandler(...);
 * try {
 *     processXML(handler);
 * } catch (SAXException e) {
 *     stream.throwIfCauseOf(e);
 *     // ... or process the exception that was caused by something else
 * }
 * </pre>
 *
 * @see TaggedSAXException
 */
public class TaggedContentHandler extends ContentHandlerDecorator {

    /**
     * Creates a tagging decorator for the given content handler.
     *
     * @param proxy content handler to be decorated
     */
    public TaggedContentHandler(ContentHandler proxy) {
        super(proxy);
    }

    /**
     * Tests if the given exception was caused by this handler.
     *
     * @param exception an exception
     * @return <code>true</code> if the exception was thrown by this handler,
     *         <code>false</code> otherwise
     */
    public boolean isCauseOf(SAXException exception) {
        if (exception instanceof TaggedSAXException) {
            TaggedSAXException tagged = (TaggedSAXException) exception;
            return this == tagged.getTag();
        } else {
            return false;
        }
    }

    /**
     * Re-throws the original exception thrown by this handler. This method
     * first checks whether the given exception is a {@link TaggedSAXException}
     * wrapper created by this decorator, and then unwraps and throws the
     * original wrapped exception. Returns normally if the exception was
     * not thrown by this handler.
     *
     * @param exception an exception
     * @throws SAXException original exception, if any, thrown by this handler
     */
    public void throwIfCauseOf(Exception exception) throws SAXException {
        if (exception instanceof TaggedSAXException) {
            TaggedSAXException tagged = (TaggedSAXException) exception;
            if (this == tagged.getTag()) {
                throw tagged.getCause();
            }
        }
    }

    /**
     * Tags any {@link SAXException}s thrown, wrapping and re-throwing.
     * 
     * @param e The SAXException thrown
     * @throws SAXException if an XML error occurs
     */
    @Override
    protected void handleException(SAXException e) throws SAXException {
        throw new TaggedSAXException(e, this);
    }

}
