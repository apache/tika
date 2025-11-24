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
package org.apache.tika.pipes.fetcher.http.jwt;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import org.apache.tika.exception.TikaConfigException;

public class JwtPrivateKeyCreds extends JwtCreds {
    private final PrivateKey privateKey;
    public JwtPrivateKeyCreds(PrivateKey privateKey, String issuer, String subject,
                              int expiresInSeconds) {
        super(issuer, subject, expiresInSeconds);
        this.privateKey = privateKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public static String convertPrivateKeyToBase64(PrivateKey privateKey) {
        // Get the encoded form of the private key
        byte[] privateKeyEncoded = privateKey.getEncoded();
        // Encode the byte array using Base64
        return Base64.getEncoder().encodeToString(privateKeyEncoded);
    }

    public static PrivateKey convertBase64ToPrivateKey(String privateKeyBase64)
            throws TikaConfigException {
        try {
            byte[] privateKeyEncoded = Base64.getDecoder().decode(privateKeyBase64);

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyEncoded);
            return keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new TikaConfigException("Could not convert private key base64 to PrivateKey", e);
        }
    }
}
