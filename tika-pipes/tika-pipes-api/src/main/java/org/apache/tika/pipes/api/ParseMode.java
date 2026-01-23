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

/**
 * Controls how embedded documents are handled during parsing.
 * <p>
 * This can be set as a default in PipesConfig (loaded from tika-config.json)
 * or overridden per-file via ParseContext.
 */
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
    NO_PARSE;

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
                            "Must be one of: RMETA, CONCATENATE, NO_PARSE");
        }
    }
}
