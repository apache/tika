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

import java.util.List;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

import org.apache.tika.digest.CompositeDigester;
import org.apache.tika.digest.DigestDef;
import org.apache.tika.digest.Digester;
import org.apache.tika.digest.Encoder;
import org.apache.tika.digest.InputStreamDigester;

/**
 * Implementation of {@link Digester}
 * that relies on commons.codec.digest.DigestUtils to calculate digest hashes.
 * <p>
 * This digester tries to use the regular mark/reset protocol on the InputStream.
 * However, this wraps an internal BoundedInputStream, and if the InputStream
 * is not fully read, then this will reset the stream and
 * spool the InputStream to disk (via TikaInputStream) and then digest the file.
 */
public class CommonsDigester extends CompositeDigester {

    /**
     * @param markLimit limit for mark/reset; after this limit is hit, the
     *                  stream is reset and spooled to disk
     * @param digests   list of digest definitions (algorithm + encoding pairs)
     */
    public CommonsDigester(int markLimit, List<DigestDef> digests) {
        super(buildDigesters(markLimit, digests));
    }

    /**
     * @param markLimit  limit for mark/reset; after this limit is hit, the
     *                   stream is reset and spooled to disk
     * @param algorithms algorithms to run (uses HEX encoding for all)
     */
    public CommonsDigester(int markLimit, DigestDef.Algorithm... algorithms) {
        super(buildDigesters(markLimit, algorithms));
    }

    private static Digester[] buildDigesters(int markLimit, List<DigestDef> digests) {
        Digester[] digesters = new Digester[digests.size()];
        int i = 0;
        for (DigestDef def : digests) {
            checkSupported(def.getAlgorithm());
            Encoder encoder = getEncoder(def.getEncoding());
            digesters[i++] = new InputStreamDigester(markLimit,
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
            checkSupported(algorithm);
            DigestDef def = new DigestDef(algorithm, DigestDef.Encoding.HEX);
            digesters[i++] = new InputStreamDigester(markLimit,
                    algorithm.getJavaName(),
                    def.getMetadataKey(),
                    encoder);
        }
        return digesters;
    }

    private static void checkSupported(DigestDef.Algorithm algorithm) {
        switch (algorithm) {
            case SHA3_256:
            case SHA3_384:
            case SHA3_512:
                throw new UnsupportedOperationException(
                        "CommonsDigester does not support " + algorithm.name() +
                                ". Use BouncyCastleDigester instead.");
            default:
                // supported
        }
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
            return Hex.encodeHexString(bytes);
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
}
