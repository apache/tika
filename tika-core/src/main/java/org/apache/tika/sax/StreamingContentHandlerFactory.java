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

import java.io.OutputStream;
import java.nio.charset.Charset;

import org.xml.sax.ContentHandler;

/**
 * Extended factory interface for creating ContentHandler instances that write
 * directly to an OutputStream.
 * <p>
 * This interface extends {@link ContentHandlerFactory} to add streaming output
 * capability, primarily used by tika-server's /tika endpoint for streaming
 * responses back to clients.
 *
 * @see ContentHandlerFactory
 * @see BasicContentHandlerFactory
 */
public interface StreamingContentHandlerFactory extends ContentHandlerFactory {

    /**
     * Creates a new ContentHandler that writes output directly to the given OutputStream.
     *
     * @param os      the output stream to write to
     * @param charset the character encoding to use
     * @return a new ContentHandler instance that writes to the stream
     */
    ContentHandler createHandler(OutputStream os, Charset charset);
}
