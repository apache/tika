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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JwtGeneratorTest {
    @Test
    void jwtSecret() throws Exception {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String jwt = new JwtGenerator(new JwtSecretCreds(randomBytes, "nick", "subject",
                120)).jwt();
        SignedJWT signedJWT = SignedJWT.parse(jwt);
        JWSVerifier verifier = new MACVerifier(randomBytes);
        Assertions.assertTrue(signedJWT.verify(verifier));
    }

    @Test
    void jwtPrivateKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        String jwt = new JwtGenerator(new JwtPrivateKeyCreds(keyPair.getPrivate(), "nick",
                "subject", 120)).jwt();
        JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) keyPair.getPublic());
        SignedJWT signedJWT = SignedJWT.parse(jwt);
        Assertions.assertTrue(signedJWT.verify(verifier));
    }
}
