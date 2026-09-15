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

import org.apache.tika.parser.ParseContext;

/**
 * A content enricher whose output is the document's text: what the image, audio, or video
 * says, written into the caller's body as content the caller may treat as its own. Other
 * enrichers annotate (captions, embeddings, tags); a caller never substitutes their output
 * for text it already has. Only text recognizers count when a caller such as the PDF
 * parser's AUTO OCR strategy asks whether an engine can stand in for extracted text.
 * <p>
 * With no {@code "text-recognizers"} list, a recognizer on the classpath is found by this
 * interface (see {@link ContentEnrichers#resolve}). Decorators hide it: ask through
 * {@link ContentEnrichers#asTextRecognizer}, not {@code instanceof}.
 *
 * @since Apache Tika 4.1
 */
public interface TextRecognizer extends ContentEnricher {

    /**
     * Whether this configured instance recognizes text for this parse. An OCR engine told
     * to skip OCR recognizes nothing; a VLM answers from its configuration, since only the
     * operator knows whether its prompt transcribes or captions.
     */
    default boolean recognizesText(ParseContext context) {
        return true;
    }
}
