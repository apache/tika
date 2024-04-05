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
    public static String jwt(JwtCreds jwtCreds) throws JOSEException {
        if (jwtCreds instanceof JwtSecretCreds) {
            return jwtHS256((JwtSecretCreds) jwtCreds);
        } else {
            return jwtRS256((JwtPrivateKeyCreds) jwtCreds);
        }
    }

    public static String jwtHS256(JwtSecretCreds jwtSecretCreds)
            throws JOSEException {
        JWSSigner signer = new MACSigner(jwtSecretCreds.getSecret());

        JWTClaimsSet claimsSet = getJwtClaimsSet(jwtSecretCreds.getIssuer(),
                jwtSecretCreds.getSubject(), jwtSecretCreds.getExpiresInSeconds());

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    public static String jwtRS256(JwtPrivateKeyCreds jwtPrivateKeyCreds)
            throws JOSEException {
        JWSSigner signer = new RSASSASigner(jwtPrivateKeyCreds.getPrivateKey());

        JWTClaimsSet claimsSet = getJwtClaimsSet(jwtPrivateKeyCreds.getIssuer(),
                jwtPrivateKeyCreds.getSubject(), jwtPrivateKeyCreds.getExpiresInSeconds());

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);

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
}
