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
 * This is used in {@link org.apache.tika.parser.AutoDetectParserConfig} to
 * configure digesting in the AutoDetectParser.
 * <p>
 * Example JSON configuration:
 * <pre>
 * "auto-detect-parser": {
 *   "digesterFactory": {
 *     "commons-digester-factory": {
 *       "digests": [
 *         { "algorithm": "MD5" },
 *         { "algorithm": "SHA256", "encoding": "BASE32" }
 *       ]
 *     }
 *   }
 * }
 * </pre>
 *
 * @see DigestDef
 * @see DigestAlgorithm
 * @see DigestEncoding
 */
public interface DigesterFactory {
    /**
     * Build a new Digester instance using the factory's configured properties.
     *
     * @return a new Digester instance
     */
    Digester build();
}
