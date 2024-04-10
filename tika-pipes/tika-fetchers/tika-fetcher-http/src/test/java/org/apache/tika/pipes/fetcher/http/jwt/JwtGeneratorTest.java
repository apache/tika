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
