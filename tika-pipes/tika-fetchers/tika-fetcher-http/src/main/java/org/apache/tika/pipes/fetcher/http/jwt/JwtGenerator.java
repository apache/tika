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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public class JwtGenerator {
    JwtCreds jwtCreds;
    public JwtGenerator(JwtCreds jwtCreds) {
        this.jwtCreds = jwtCreds;
    }

    public String jwt() throws JOSEException {
        if (jwtCreds instanceof JwtSecretCreds) {
            return jwtHS256((JwtSecretCreds) jwtCreds);
        } else {
            return jwtRS256((JwtPrivateKeyCreds) jwtCreds);
        }
    }

    String jwtHS256(JwtSecretCreds jwtSecretCreds)
            throws JOSEException {
        JWSSigner signer = new MACSigner(jwtSecretCreds.getSecret());

        JWTClaimsSet claimsSet = getJwtClaimsSet(jwtSecretCreds.getIssuer(),
                jwtSecretCreds.getSubject(), jwtSecretCreds.getExpiresInSeconds());

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    String jwtRS256(JwtPrivateKeyCreds jwtPrivateKeyCreds)
            throws JOSEException {
        JWSSigner signer = new RSASSASigner(jwtPrivateKeyCreds.getPrivateKey());

        JWTClaimsSet claimsSet = getJwtClaimsSet(jwtPrivateKeyCreds.getIssuer(),
                jwtPrivateKeyCreds.getSubject(), jwtPrivateKeyCreds.getExpiresInSeconds());

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);

        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    private JWTClaimsSet getJwtClaimsSet(String issuer, String subject, int expiresInSeconds) {
        return new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .expirationTime(Date.from(Instant.now().plus(expiresInSeconds, ChronoUnit.SECONDS)))
                .build();
    }
}
