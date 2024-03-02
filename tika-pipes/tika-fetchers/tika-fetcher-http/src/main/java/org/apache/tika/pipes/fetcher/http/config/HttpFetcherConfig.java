package org.apache.tika.pipes.fetcher.http.config;

import java.util.List;

import org.apache.tika.pipes.fetcher.config.AbstractConfig;

public class HttpFetcherConfig extends AbstractConfig {
    private String userName;
    private String password;
    private String ntDomain;
    private String authScheme;
    private String proxyHost;
    private int proxyPort;
    private int connectTimeout;
    private int requestTimeout;
    private int socketTimeout;
    private int maxConnections;
    int maxConnectionsPerRoute;
    private long maxSpoolSize;
    private int maxRedirects;
    private List<String> headers;
    private long overallTimeout;
    private int maxErrMsgSize;
    private String userAgent;

    public String getUserName() {
        return userName;
    }

    public HttpFetcherConfig setUserName(String userName) {
        this.userName = userName;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public HttpFetcherConfig setPassword(String password) {
        this.password = password;
        return this;
    }

    public String getNtDomain() {
        return ntDomain;
    }

    public HttpFetcherConfig setNtDomain(String ntDomain) {
        this.ntDomain = ntDomain;
        return this;
    }

    public String getAuthScheme() {
        return authScheme;
    }

    public HttpFetcherConfig setAuthScheme(String authScheme) {
        this.authScheme = authScheme;
        return this;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public HttpFetcherConfig setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
        return this;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public HttpFetcherConfig setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
        return this;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public HttpFetcherConfig setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public HttpFetcherConfig setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
        return this;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public HttpFetcherConfig setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
        return this;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public HttpFetcherConfig setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }

    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    public HttpFetcherConfig setMaxConnectionsPerRoute(int maxConnectionsPerRoute) {
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
        return this;
    }

    public long getMaxSpoolSize() {
        return maxSpoolSize;
    }

    public HttpFetcherConfig setMaxSpoolSize(long maxSpoolSize) {
        this.maxSpoolSize = maxSpoolSize;
        return this;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }

    public HttpFetcherConfig setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public HttpFetcherConfig setHeaders(List<String> headers) {
        this.headers = headers;
        return this;
    }

    public long getOverallTimeout() {
        return overallTimeout;
    }

    public HttpFetcherConfig setOverallTimeout(long overallTimeout) {
        this.overallTimeout = overallTimeout;
        return this;
    }

    public int getMaxErrMsgSize() {
        return maxErrMsgSize;
    }

    public HttpFetcherConfig setMaxErrMsgSize(int maxErrMsgSize) {
        this.maxErrMsgSize = maxErrMsgSize;
        return this;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public HttpFetcherConfig setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }
}
