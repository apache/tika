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
package org.apache.tika.parser.digestutils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.util.List;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;

import org.apache.tika.digest.CompositeDigester;
import org.apache.tika.digest.DigestDef;
import org.apache.tika.digest.Digester;
import org.apache.tika.digest.Encoder;
import org.apache.tika.digest.InputStreamDigester;

/**
 * Digester that relies on BouncyCastle for MessageDigest implementations.
 * <p>
 * BouncyCastle supports additional algorithms beyond the standard Java ones,
 * such as SHA3-256, SHA3-384, SHA3-512.
 */
public class BouncyCastleDigester extends CompositeDigester {

    /**
     * @param markLimit limit for mark/reset; after this limit is hit, the
     *                  stream is reset and spooled to disk
     * @param digests   list of digest definitions (algorithm + encoding pairs)
     */
    public BouncyCastleDigester(int markLimit, List<DigestDef> digests) {
        super(buildDigesters(markLimit, digests));
    }

    /**
     * Convenience constructor using Algorithm enum with HEX encoding.
     *
     * @param markLimit  limit for mark/reset; after this limit is hit, the
     *                   stream is reset and spooled to disk
     * @param algorithms algorithms to run (uses HEX encoding for all)
     */
    public BouncyCastleDigester(int markLimit, DigestDef.Algorithm... algorithms) {
        super(buildDigesters(markLimit, algorithms));
    }

    private static Digester[] buildDigesters(int markLimit, List<DigestDef> digests) {
        Digester[] digesters = new Digester[digests.size()];
        int i = 0;
        for (DigestDef def : digests) {
            Encoder encoder = getEncoder(def.getEncoding());
            digesters[i++] = new BCInputStreamDigester(markLimit,
                    def.getAlgorithm().getJavaName(),
                    def.getMetadataKey(),
                    encoder);
        }
        return digesters;
    }

    private static Digester[] buildDigesters(int markLimit, DigestDef.Algorithm[] algorithms) {
        Digester[] digesters = new Digester[algorithms.length];
        Encoder encoder = getEncoder(DigestDef.Encoding.HEX);
        int i = 0;
        for (DigestDef.Algorithm algorithm : algorithms) {
            DigestDef def = new DigestDef(algorithm, DigestDef.Encoding.HEX);
            digesters[i++] = new BCInputStreamDigester(markLimit,
                    algorithm.getJavaName(),
                    def.getMetadataKey(),
                    encoder);
        }
        return digesters;
    }

    private static Encoder getEncoder(DigestDef.Encoding encoding) {
        switch (encoding) {
            case HEX:
                return new HexEncoder();
            case BASE32:
                return new Base32Encoder();
            case BASE64:
                return new Base64Encoder();
            default:
                return new HexEncoder();
        }
    }

    private static class HexEncoder implements Encoder {
        @Override
        public String encode(byte[] bytes) {
            return Hex.toHexString(bytes);
        }
    }

    private static class Base32Encoder implements Encoder {
        @Override
        public String encode(byte[] bytes) {
            return new Base32().encodeToString(bytes);
        }
    }

    private static class Base64Encoder implements Encoder {
        @Override
        public String encode(byte[] bytes) {
            return new Base64().encodeToString(bytes);
        }
    }

    private static class BCInputStreamDigester extends InputStreamDigester {

        public BCInputStreamDigester(int markLimit, String algorithm, String algorithmKeyName,
                                     Encoder encoder) {
            super(markLimit, algorithm, algorithmKeyName, encoder);
            try {
                MessageDigest.getInstance(algorithm, getProvider());
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        protected Provider getProvider() {
            return new BouncyCastleProvider();
        }
    }
}
