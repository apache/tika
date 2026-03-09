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
package org.apache.tika.pipes.api;

import java.util.Locale;

import org.apache.tika.config.TikaComponent;

/**
 * Controls how embedded documents are handled during parsing.
 * <p>
 * This can be set as a default in PipesConfig (loaded from tika-config.json)
 * or overridden per-file via ParseContext.
 */
@TikaComponent(name = "parse-mode")
public enum ParseMode {

    /**
     * Each embedded file gets its own metadata object in a list.
     * <p>
     * This is equivalent to the -J option in tika-app and the /rmeta endpoint
     * in tika-server. The result is a list of metadata objects, one for each
     * document (container + all embedded documents).
     */
    RMETA,

    /**
     * Concatenates content from all embedded files into a single document.
     * <p>
     * This is equivalent to the legacy tika-app behavior and the /tika endpoint
     * in tika-server. The result is a single metadata object with concatenated
     * content from all documents.
     */
    CONCATENATE,

    /**
     * Performs digest (if configured) and content type detection only.
     * <p>
     * No parsing occurs - embedded documents are not extracted and no content
     * is returned. Use this mode when you only need file identification
     * (mime type, hash) without text extraction.
     */
    NO_PARSE,

    /**
     * Concatenates content and emits only the raw content string, with no
     * metadata and no JSON wrapper.
     * <p>
     * This mode parses like CONCATENATE (producing a single metadata object with
     * merged content from all embedded documents), but at emit time, emitters
     * write only the value of {@code X-TIKA:content} as a raw string instead of
     * serializing the full metadata list as JSON.
     * <p>
     * This is useful when you want plain text, markdown, or HTML output files
     * without any metadata overhead.
     */
    CONTENT_ONLY,

    /**
     * Extracts embedded document bytes and emits them, with full RMETA metadata.
     * <p>
     * This mode parses like RMETA (returning a metadata object per document) AND
     * automatically extracts and emits embedded document bytes. An emitter is
     * required for the byte extraction.
     * <p>
     * With PASSBACK_ALL emit strategy, embedded bytes are still emitted during
     * parsing, but metadata is passed back to the client instead of being emitted.
     * This is useful when you want bytes written to storage but need metadata
     * returned for further processing (e.g., indexing to a database).
     * <p>
     * This mode simplifies byte extraction by handling all the internal setup
     * (UnpackExtractor, EmittingUnpackHandler) automatically.
     * Users just need to specify the emitter in UnpackConfig or FetchEmitTuple.
     */
    UNPACK;

    /**
     * Parses a string to a ParseMode enum value.
     *
     * @param modeString the string to parse (case-insensitive)
     * @return the corresponding ParseMode
     * @throws IllegalArgumentException if the string doesn't match any mode
     */
    public static ParseMode parse(String modeString) {
        if (modeString == null) {
            throw new IllegalArgumentException("Parse mode cannot be null");
        }
        String normalized = modeString.toUpperCase(Locale.ROOT).trim();
        try {
            return ParseMode.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid parse mode: '" + modeString + "'. " +
                            "Must be one of: RMETA, CONCATENATE, CONTENT_ONLY, NO_PARSE, UNPACK");
        }
    }
}
