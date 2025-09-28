package org.apache.tika.pipes.fetchers.http.config;

public class AuthConfig {
    private String userName;
    private String password;
    private String ntDomain;
    private String authScheme;
    private JwtConfig jwtConfig;

    public String getUserName() {
        return userName;
    }

    public AuthConfig setUserName(String userName) {
        this.userName = userName;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public AuthConfig setPassword(String password) {
        this.password = password;
        return this;
    }

    public String getNtDomain() {
        return ntDomain;
    }

    public AuthConfig setNtDomain(String ntDomain) {
        this.ntDomain = ntDomain;
        return this;
    }

    public String getAuthScheme() {
        return authScheme;
    }

    public AuthConfig setAuthScheme(String authScheme) {
        this.authScheme = authScheme;
        return this;
    }

    public JwtConfig getJwtConfig() {
        return jwtConfig;
    }

    public AuthConfig setJwtConfig(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        return this;
    }
}
