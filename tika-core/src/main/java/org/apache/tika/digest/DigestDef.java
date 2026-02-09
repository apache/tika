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

import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Defines a digest algorithm with its output encoding.
 * <p>
 * Example JSON configuration:
 * <pre>
 * {
 *   "digests": [
 *     { "algorithm": "MD5" },
 *     { "algorithm": "SHA256", "encoding": "BASE32" }
 *   ]
 * }
 * </pre>
 * <p>
 * The metadata key format is:
 * <ul>
 *   <li>{@code X-TIKA:digest:MD5} - for HEX encoding (default)</li>
 *   <li>{@code X-TIKA:digest:SHA256:BASE32} - for non-default encodings</li>
 * </ul>
 */
public class DigestDef {

    /**
     * Supported digest algorithms.
     * <p>
     * Note: SHA3 algorithms require BouncyCastle provider (use BouncyCastleDigester).
     * CommonsDigester only supports MD2, MD5, SHA1, SHA256, SHA384, SHA512.
     */
    public enum Algorithm {
        MD2("MD2"),
        MD5("MD5"),
        SHA1("SHA-1"),
        SHA256("SHA-256"),
        SHA384("SHA-384"),
        SHA512("SHA-512"),
        SHA3_256("SHA3-256"),
        SHA3_384("SHA3-384"),
        SHA3_512("SHA3-512");

        private final String javaName;

        Algorithm(String javaName) {
            this.javaName = javaName;
        }

        /**
         * Returns the Java Security name for this algorithm
         * (for use with MessageDigest.getInstance()).
         */
        public String getJavaName() {
            return javaName;
        }
    }

    /**
     * Supported digest output encodings.
     */
    public enum Encoding {
        HEX,
        BASE32,
        BASE64
    }

    private Algorithm algorithm;
    private Encoding encoding = Encoding.HEX;

    public DigestDef() {
    }

    public DigestDef(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    public DigestDef(Algorithm algorithm, Encoding encoding) {
        this.algorithm = algorithm;
        this.encoding = encoding;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    public Encoding getEncoding() {
        return encoding;
    }

    public void setEncoding(Encoding encoding) {
        this.encoding = encoding;
    }

    /**
     * Returns the metadata key for storing this digest value.
     * <p>
     * For HEX encoding (the default), returns {@code X-TIKA:digest:ALGORITHM}.
     * For other encodings, returns {@code X-TIKA:digest:ALGORITHM:ENCODING}.
     *
     * @return the metadata key
     */
    public String metadataKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(TikaCoreProperties.TIKA_META_PREFIX);
        sb.append("digest");
        sb.append(TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER);
        sb.append(algorithm.name());
        if (encoding != Encoding.HEX) {
            sb.append(":");
            sb.append(encoding.name());
        }
        return sb.toString();
    }
}
