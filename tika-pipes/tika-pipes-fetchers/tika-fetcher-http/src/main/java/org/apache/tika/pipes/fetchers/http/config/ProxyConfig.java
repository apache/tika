package org.apache.tika.pipes.fetchers.http.config;

public class ProxyConfig {
    private String proxyHost;
    private Integer proxyPort;

    public String getProxyHost() {
        return proxyHost;
    }

    public ProxyConfig setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
        return this;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public ProxyConfig setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
        return this;
    }
}
