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
package org.apache.tika.digest;

/**
 * Factory interface for creating Digester instances.
 * Implementations should be annotated with {@code @TikaComponent} and
 * provide bean properties for configuration (e.g., digests).
 * <p>
 * Configure this factory in the "parse-context" section of tika-config.json.
 * The factory is loaded into the ParseContext and used by AutoDetectParser
 * during parsing to compute digests.
 * <p>
 * Example JSON configuration:
 * <pre>
 * {
 *   "parse-context": {
 *     "commons-digester-factory": {
 *       "digests": [
 *         { "algorithm": "MD5" },
 *         { "algorithm": "SHA256", "encoding": "BASE32" }
 *       ],
 *       "skipContainerDocumentDigest": true
 *     }
 *   }
 * }
 * </pre>
 * <p>
 * When using TikaLoader, call {@code loader.loadParseContext()} to get a
 * ParseContext with the DigesterFactory already set.
 *
 * @see DigestDef
 */
public interface DigesterFactory {
    /**
     * Build a new Digester instance using the factory's configured properties.
     *
     * @return a new Digester instance
     */
    Digester build();

    /**
     * Returns whether to skip digesting for container (top-level) documents.
     * When true, only embedded documents (depth &gt; 0) will be digested.
     * <p>
     * Default implementation returns false (digest everything).
     *
     * @return true if container documents should be skipped, false otherwise
     */
    default boolean isSkipContainerDocumentDigest() {
        return false;
    }
}
