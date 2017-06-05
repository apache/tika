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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;

import org.apache.commons.codec.binary.Base32;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.digest.CompositeDigester;
import org.apache.tika.parser.digest.InputStreamDigester;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;

/**
 * Digester that relies on BouncyCastle for MessageDigest implementations.
 *
 */
public class BouncyCastleDigester extends CompositeDigester {

    /**
     * Include a string representing the comma-separated algorithms to run: e.g. "md5,sha1".
     * If you want base 32 encoding instead of hexadecimal, add ":32" to the algorithm, e.g. "md5,sha1:32"
     * <p/>
     * Will throw an IllegalArgumentException if an algorithm isn't supported
     * @param markLimit
     * @param algorithmString
     */
    public BouncyCastleDigester(int markLimit, String algorithmString) {
        super(buildDigesters(markLimit, algorithmString));
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
            digesters[i++] = new BCInputStreamDigester(markLimit, parts[0], encoder);
        }
        return digesters;
    }

    private static class HexEncoder implements DigestingParser.Encoder {
        @Override
        public String encode(byte[] bytes) {
            return Hex.toHexString(bytes);
        }
    }

    private static class Base32Encoder implements DigestingParser.Encoder {
        @Override
        public String encode(byte[] bytes) {
            return new Base32().encodeToString(bytes);
        }
    }

    private static class BCInputStreamDigester extends InputStreamDigester {

        public BCInputStreamDigester(int markLimit, String algorithm, DigestingParser.Encoder encoder) {
            super(markLimit, algorithm, encoder);
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
