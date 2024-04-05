package org.apache.tika.pipes.fetcher.http.jwt;

public abstract class JwtCreds {
    private final String issuer;
    private final String subject;
    private final int expiresInSeconds;

    public JwtCreds(String issuer, String subject, int expiresInSeconds) {
        this.issuer = issuer;
        this.subject = subject;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getSubject() {
        return subject;
    }

    public int getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
