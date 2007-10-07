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
package org.apache.tika.parser;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.config.Content;
import org.apache.tika.exception.TikaException;

/**
 * Tika parser interface
 */
public interface Parser {

    /**
     * Parses a document from the given input stream and returns the
     * extracted full text content of the document. Fills in selected
     * metadata information in the given set of {@link Content} instances.
     * <p>
     * The given stream is consumed but not closed by this method.
     * The responsibility to close the stream remains on the caller.
     *
     * @param stream the document to be parsed
     * @param contents set of metadata information to extract
     * @return full text content of the document
     * @throws IOException if the document could not be read
     * @throws TikaException if the document could not be parsed
     */
    String parse(InputStream stream, Iterable<Content> contents)
            throws IOException, TikaException;

}
