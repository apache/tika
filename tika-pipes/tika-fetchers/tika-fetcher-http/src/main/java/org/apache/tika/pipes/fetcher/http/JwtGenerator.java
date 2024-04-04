package org.apache.tika.pipes.fetcher.http;

import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
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
    public static void main(String[] args) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        System.out.println(jwt(randomBytes, "nick", "subject", 120));
        System.out.println(jwt(keyPairGenerator.generateKeyPair().getPrivate(), "nick", "subject", 120));
    }

    public static String jwt(byte[] secret, String issuer, String subject,
                             int expiresInSeconds)
            throws JOSEException {
        JWSSigner signer = new MACSigner(secret);

        JWTClaimsSet claimsSet = getJwtClaimsSet(issuer, subject, expiresInSeconds);

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    private static JWTClaimsSet getJwtClaimsSet(String issuer, String subject, int expiresInSeconds) {
        return new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .expirationTime(Date.from(Instant.now().plus(expiresInSeconds, ChronoUnit.SECONDS)))
                .build();
    }

    public static String jwt(PrivateKey privateKey, String issuer, String subject,
                             int expiresInSeconds)
            throws JOSEException {
        JWSSigner signer = new RSASSASigner(privateKey);

        JWTClaimsSet claimsSet = getJwtClaimsSet(issuer, subject, expiresInSeconds);

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);

        signedJWT.sign(signer);

        return signedJWT.serialize();
    }
}
