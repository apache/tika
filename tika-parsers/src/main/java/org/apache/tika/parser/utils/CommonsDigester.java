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
package org.apache.tika.parser.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.digest.CompositeDigester;
import org.apache.tika.parser.digest.InputStreamDigester;

/**
 * Implementation of {@link org.apache.tika.parser.DigestingParser.Digester}
 * that relies on commons.codec.digest.DigestUtils to calculate digest hashes.
 * <p>
 * This digester tries to use the regular mark/reset protocol on the InputStream.
 * However, this wraps an internal BoundedInputStream, and if the InputStream
 * is not fully read, then this will reset the stream and
 * spool the InputStream to disk (via TikaInputStream) and then digest the file.
 */
public class CommonsDigester extends CompositeDigester {

    public enum DigestAlgorithm {
        //those currently available in commons.digest
        MD2("MD2"),
        MD5("MD5"),
        SHA1("SHA-1"),
        SHA256("SHA-256"),
        SHA384("SHA-384"),
        SHA512("SHA-512");

        private final String javaName;

        DigestAlgorithm(String javaName) {
            this.javaName = javaName;
        }

        String getJavaName() {
            return javaName;
        }
        String getMetadataKey() {
            return TikaCoreProperties.TIKA_META_PREFIX +
                    "digest" + Metadata.NAMESPACE_PREFIX_DELIMITER + this.toString();
        }
    }

    /**
     * Include a string representing the comma-separated algorithms to run: e.g. "md5,sha1".
     * If you want base 32 encoding instead of hexadecimal, add ":32" to the algorithm, e.g. "md5,sha1:32"
     * <p/>
     * Will throw an IllegalArgumentException if an algorithm isn't supported
     * @param markLimit
     * @param algorithmString
     */
    public CommonsDigester(int markLimit, String algorithmString) {
        super(buildDigesters(markLimit, algorithmString));
    }

    /**
     *
     * @param markLimit limit for mark/reset; after this limit is hit, the
     *                  stream is reset and spooled to disk
     * @param algorithms algorithms to run
     * @deprecated use {@link #CommonsDigester(int, String)}
     */
    public CommonsDigester(int markLimit, DigestAlgorithm... algorithms) {
        super(buildDigesters(markLimit, algorithms));
    }

    private static DigestingParser.Digester[] buildDigesters(int markLimit, DigestAlgorithm[] algorithms) {
        DigestingParser.Digester[] digesters = new DigestingParser.Digester[algorithms.length];
        int i = 0;
        for (DigestAlgorithm algorithm : algorithms) {
            digesters[i++] = new InputStreamDigester(markLimit, algorithm.getJavaName(), algorithm.name(),
                    new HexEncoder());
        }
        return digesters;
    }

    /**
     * This returns digest algorithms only.  It does not understand the encoding
     * syntax, e.g. "MD5:32" (base 32 encoding of MD5).  To parse
     * those, see {@link #CommonsDigester(int, String)}.
     *
     * @deprecated use the {@link #CommonsDigester(int, String)} instead
     * @param s comma-delimited (no space) list of algorithms to use: md5,sha256.
     * @return
     *
     */
    @Deprecated
    public static DigestAlgorithm[] parse(String s) {
        assert (s != null);

        List<DigestAlgorithm> ret = new ArrayList<>();
        for (String algoString : s.split(",")) {
            ret.add(getDigestAlgorithm(algoString));
        }
        return ret.toArray(new DigestAlgorithm[ret.size()]);
    }

    private static DigestAlgorithm getDigestAlgorithm(String algoString) {
        String uc = algoString.toUpperCase(Locale.ROOT);
        if (uc.equals(DigestAlgorithm.MD2.toString())) {
            return DigestAlgorithm.MD2;
        } else if (uc.equals(DigestAlgorithm.MD5.toString())) {
            return DigestAlgorithm.MD5;
        } else if (uc.equals(DigestAlgorithm.SHA1.toString())) {
            return DigestAlgorithm.SHA1;
        } else if (uc.equals(DigestAlgorithm.SHA256.toString())) {
            return DigestAlgorithm.SHA256;
        } else if (uc.equals(DigestAlgorithm.SHA384.toString())) {
            return DigestAlgorithm.SHA384;
        } else if (uc.equals(DigestAlgorithm.SHA512.toString())) {
            return DigestAlgorithm.SHA512;
        } else {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (DigestAlgorithm algo : DigestAlgorithm.values()) {
                if (i++ > 0) {
                    sb.append(", ");
                }
                sb.append(algo.toString());
            }
            throw new IllegalArgumentException("Couldn't match " + algoString + " with any of: " + sb.toString());
        }
    }

    private static DigestingParser.Digester[] buildDigesters(int markLimit, String digesterDef) {
        String[] digests = digesterDef.split(",");
        DigestingParser.Digester[] digesters = new DigestingParser.Digester[digests.length];
        int i = 0;
        for (String digest : digests) {
            String[] parts = digest.split(":");
            DigestingParser.Encoder encoder = null;
            if (parts.length > 1) {
                if (parts[1].equals("16")) {
                    encoder = new HexEncoder();
                } else if (parts[1].equals("32")) {
                    encoder = new Base32Encoder();
                } else {
                    throw new IllegalArgumentException("Value must be '16' or '32'");
                }
            } else {
                encoder = new HexEncoder();
            }
            DigestAlgorithm digestAlgorithm = getDigestAlgorithm(parts[0]);
            digesters[i++] = new InputStreamDigester(markLimit, digestAlgorithm.getJavaName(),
                    digestAlgorithm.name(), encoder);
        }
        return digesters;
    }


    private static class HexEncoder implements DigestingParser.Encoder {
        @Override
        public String encode(byte[] bytes) {
            return Hex.encodeHexString(bytes);
        }
    }

    private static class Base32Encoder implements DigestingParser.Encoder {
        @Override
        public String encode(byte[] bytes) {
            return new Base32().encodeToString(bytes);
        }
    }
}
