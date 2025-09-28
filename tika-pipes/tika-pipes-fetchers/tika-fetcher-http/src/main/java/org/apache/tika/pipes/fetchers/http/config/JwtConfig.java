package org.apache.tika.pipes.fetchers.http.config;

public class JwtConfig {
    private String jwtIssuer;
    private String jwtSubject;
    private int jwtExpiresInSeconds;
    private String jwtSecret;
    private String jwtPrivateKeyBase64;

    public String getJwtIssuer() {
        return jwtIssuer;
    }

    public JwtConfig setJwtIssuer(String jwtIssuer) {
        this.jwtIssuer = jwtIssuer;
        return this;
    }

    public String getJwtSubject() {
        return jwtSubject;
    }

    public JwtConfig setJwtSubject(String jwtSubject) {
        this.jwtSubject = jwtSubject;
        return this;
    }

    public int getJwtExpiresInSeconds() {
        return jwtExpiresInSeconds;
    }

    public JwtConfig setJwtExpiresInSeconds(int jwtExpiresInSeconds) {
        this.jwtExpiresInSeconds = jwtExpiresInSeconds;
        return this;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public JwtConfig setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
        return this;
    }

    public String getJwtPrivateKeyBase64() {
        return jwtPrivateKeyBase64;
    }

    public JwtConfig setJwtPrivateKeyBase64(String jwtPrivateKeyBase64) {
        this.jwtPrivateKeyBase64 = jwtPrivateKeyBase64;
        return this;
    }
}
