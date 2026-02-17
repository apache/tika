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
package org.apache.tika.sax;

import java.io.Serializable;

import org.xml.sax.ContentHandler;

/**
 * Factory interface for creating ContentHandler instances.
 * <p>
 * This is the base interface used by tika-pipes, RecursiveParserWrapper, and other
 * components that need to create content handlers for in-memory content extraction.
 * <p>
 * For streaming output to an OutputStream, see {@link StreamingContentHandlerFactory}.
 *
 * @see StreamingContentHandlerFactory
 * @see BasicContentHandlerFactory
 */
public interface ContentHandlerFactory extends Serializable {

    /**
     * Creates a new ContentHandler for extracting content.
     *
     * @return a new ContentHandler instance
     */
    ContentHandler createHandler();

    /**
     * Returns the name of the handler type produced by this factory
     * (e.g. {@code TEXT}, {@code MARKDOWN}, {@code HTML}, {@code XML}).
     * <p>
     * This value is written to
     * {@link org.apache.tika.metadata.TikaCoreProperties#TIKA_CONTENT_HANDLER_TYPE}
     * so that downstream components (such as the inference pipeline) can
     * determine what format {@code tika:content} is in without guessing.
     *
     * @return handler type name, never {@code null}
     */
    default String getHandlerTypeName() {
        return "UNKNOWN";
    }
}
