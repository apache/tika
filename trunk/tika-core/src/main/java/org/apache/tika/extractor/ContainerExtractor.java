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
package org.apache.tika.extractor;

import java.io.IOException;
import java.io.Serializable;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;

/**
 * Tika container extractor interface.
 * Container Extractors provide access to the embedded
 *  resources within container formats such as .zip and .doc 
 */
public interface ContainerExtractor extends Serializable {
    /**
     * Is this Container Extractor able to process the
     *  supplied container?
     * @since Apache Tika 0.8
     */
    boolean isSupported(TikaInputStream input) throws IOException;

    /**
     * Processes a container file, and extracts all the embedded
     * resources from within it.
     * <p>
     * The {@link EmbeddedResourceHandler} you supply will
     * be called for each embedded resource in the container. It is
     * up to you whether you process the contents of the resource or not. 
     * <p>
     * The given document stream is consumed but not closed by this method.
     * The responsibility to close the stream remains on the caller.
     * <p>
     * If required, nested containers (such as a .docx within a .zip)
     * can automatically be recursed into, and processed inline. If
     * no recurseExtractor is given, the nested containers will be
     * treated as with any other embedded resources.
     *
     * @since Apache Tika 0.8
     * @param stream the document stream (input)
     * @param recurseExtractor the extractor to use on any embedded containers 
     * @param handler handler for the embedded files (output)
     * @throws IOException if the document stream could not be read
     * @throws TikaException if the container could not be parsed
     */
    void extract(
            TikaInputStream stream, ContainerExtractor recurseExtractor,
            EmbeddedResourceHandler handler)
            throws IOException, TikaException;
}
