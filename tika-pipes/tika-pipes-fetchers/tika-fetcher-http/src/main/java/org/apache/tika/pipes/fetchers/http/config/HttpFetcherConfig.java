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
package org.apache.tika.pipes.fetchers.http.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.pf4j.Extension;

import org.apache.tika.pipes.fetchers.core.DefaultFetcherConfig;

@Extension
public class HttpFetcherConfig extends DefaultFetcherConfig {
    private Integer maxConnectionsPerRoute = 1000;
    private Integer maxConnections = 2000;
    private Integer requestTimeout = 120000;
    private Integer connectTimeout = 120000;
    private Integer socketTimeout = 120000;
    private Long maxSpoolSize = -1L;
    private Integer maxRedirects = 0;
    private List<String> httpHeaders = new ArrayList<>();
    private Map<String, List<String>> httpRequestHeaders = new LinkedHashMap<>();
    private Long overallTimeout = 120000L;
    private Integer maxErrMsgSize = 10000000;
    private String userAgent;
    private AuthConfig authConfig;
    private ProxyConfig proxyConfig;

    public Integer getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    public HttpFetcherConfig setMaxConnectionsPerRoute(Integer maxConnectionsPerRoute) {
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
        return this;
    }

    public Integer getMaxConnections() {
        return maxConnections;
    }

    public HttpFetcherConfig setMaxConnections(Integer maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }

    public Integer getRequestTimeout() {
        return requestTimeout;
    }

    public HttpFetcherConfig setRequestTimeout(Integer requestTimeout) {
        this.requestTimeout = requestTimeout;
        return this;
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    public HttpFetcherConfig setConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public Integer getSocketTimeout() {
        return socketTimeout;
    }

    public HttpFetcherConfig setSocketTimeout(Integer socketTimeout) {
        this.socketTimeout = socketTimeout;
        return this;
    }

    public Long getMaxSpoolSize() {
        return maxSpoolSize;
    }

    public HttpFetcherConfig setMaxSpoolSize(Long maxSpoolSize) {
        this.maxSpoolSize = maxSpoolSize;
        return this;
    }

    public Integer getMaxRedirects() {
        return maxRedirects;
    }

    public HttpFetcherConfig setMaxRedirects(Integer maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
    }

    public List<String> getHttpHeaders() {
        return httpHeaders;
    }

    public HttpFetcherConfig setHttpHeaders(List<String> httpHeaders) {
        this.httpHeaders = httpHeaders;
        return this;
    }

    public Map<String, List<String>> getHttpRequestHeaders() {
        return httpRequestHeaders;
    }

    public HttpFetcherConfig setHttpRequestHeaders(Map<String, List<String>> httpRequestHeaders) {
        this.httpRequestHeaders = httpRequestHeaders;
        return this;
    }

    public Long getOverallTimeout() {
        return overallTimeout;
    }

    public HttpFetcherConfig setOverallTimeout(Long overallTimeout) {
        this.overallTimeout = overallTimeout;
        return this;
    }

    public Integer getMaxErrMsgSize() {
        return maxErrMsgSize;
    }

    public HttpFetcherConfig setMaxErrMsgSize(Integer maxErrMsgSize) {
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

    public AuthConfig getAuthConfig() {
        return authConfig;
    }

    public HttpFetcherConfig setAuthConfig(AuthConfig authConfig) {
        this.authConfig = authConfig;
        return this;
    }

    public ProxyConfig getProxyConfig() {
        return proxyConfig;
    }

    public HttpFetcherConfig setProxyConfig(ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
        return this;
    }
}
