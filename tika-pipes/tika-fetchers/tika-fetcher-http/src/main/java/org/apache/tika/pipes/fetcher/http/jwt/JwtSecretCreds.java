package org.apache.tika.pipes.fetcher.http.jwt;

public class JwtSecretCreds extends JwtCreds {
    private final byte[] secret;
    public JwtSecretCreds(byte[] secret, String issuer, String subject, int expiresInSeconds) {
        super(issuer, subject, expiresInSeconds);
        this.secret = secret;
    }

    public byte[] getSecret() {
        return secret;
    }

}
