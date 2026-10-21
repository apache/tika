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
package org.apache.tika.parser.enricher;

import org.apache.tika.parser.inference.Engine;

/**
 * A {@link org.apache.tika.parser.Parser} a container parser <em>invokes</em> on bytes it
 * has already parsed (OCR on an image, an embedding of a rendered page) rather than one the
 * composite dispatches to; its supported types are the types it can enrich. An enricher the
 * classpath supplied is never dispatched to. Only one that {@link #parsesDocuments()} may be
 * named under {@code "parsers"}, where it also parses the types no other parser there claims.
 * See {@link ContentEnrichers#get}.
 * <p>
 * An enricher is an {@link Engine}: configured once under {@code "engines"}, a
 * {@code "text-recognizers"} entry names it.
 *
 * @since Apache Tika 4.1
 */
public interface ContentEnricher extends Engine {

    /**
     * Whether this engine is also a document parser in its own right (a VLM that reads a
     * whole PDF), and so may be named under {@code "parsers"}. An OCR engine is not.
     *
     * @since Apache Tika 4.2
     */
    default boolean parsesDocuments() {
        return false;
    }
}
