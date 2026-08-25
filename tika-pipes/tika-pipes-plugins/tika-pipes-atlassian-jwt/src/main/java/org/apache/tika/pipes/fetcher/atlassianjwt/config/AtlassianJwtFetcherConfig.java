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
package org.apache.tika.pipes.fetcher.atlassianjwt.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.plugins.PluginJson;

public class AtlassianJwtFetcherConfig {

    public static AtlassianJwtFetcherConfig load(final String json)
            throws TikaConfigException {
        return PluginJson.read(json, AtlassianJwtFetcherConfig.class);
    }

    private Integer maxConnectionsPerRoute = 1000;
    private Integer maxConnections = 2000;
    private Integer requestTimeoutMillis = 120000;
    private Integer connectTimeoutMillis = 120000;
    private Integer socketTimeoutMillis = 120000;
    private Long maxSpoolSize = -1L;
    private Integer maxRedirects = 0;
    /**
     * Verify server certificates and hostnames; false accepts any cert from any host
     * (the opt-out for self-signed internal certs).
     */
    private boolean verifySsl = true;
    private List<String> httpHeaders = new ArrayList<>();
    private Map<String, List<String>> httpRequestHeaders = new LinkedHashMap<>();
    private Long overallTimeoutMillis = 120000L;
    private Integer maxErrMsgSize = 10000000;
    private String userAgent;

    private String sharedSecret;
    private String issuer;
    private String subject;
    private Integer jwtExpiresInSeconds = 3600;

    public Integer getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    public AtlassianJwtFetcherConfig setMaxConnectionsPerRoute(Integer maxConnectionsPerRoute) {
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
        return this;
    }

    public Integer getMaxConnections() {
        return maxConnections;
    }

    public AtlassianJwtFetcherConfig setMaxConnections(Integer maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }

    public Integer getRequestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    public AtlassianJwtFetcherConfig setRequestTimeoutMillis(Integer requestTimeoutMillis) {
        this.requestTimeoutMillis = requestTimeoutMillis;
        return this;
    }

    public Integer getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public AtlassianJwtFetcherConfig setConnectTimeoutMillis(Integer connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        return this;
    }

    public Integer getSocketTimeoutMillis() {
        return socketTimeoutMillis;
    }

    public AtlassianJwtFetcherConfig setSocketTimeoutMillis(Integer socketTimeoutMillis) {
        this.socketTimeoutMillis = socketTimeoutMillis;
        return this;
    }

    public Long getMaxSpoolSize() {
        return maxSpoolSize;
    }

    public AtlassianJwtFetcherConfig setMaxSpoolSize(Long maxSpoolSize) {
        this.maxSpoolSize = maxSpoolSize;
        return this;
    }

    public Integer getMaxRedirects() {
        return maxRedirects;
    }

    public AtlassianJwtFetcherConfig setMaxRedirects(Integer maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
    }

    public boolean isVerifySsl() {
        return verifySsl;
    }

    public AtlassianJwtFetcherConfig setVerifySsl(boolean verifySsl) {
        this.verifySsl = verifySsl;
        return this;
    }

    public List<String> getHttpHeaders() {
        return httpHeaders;
    }

    public AtlassianJwtFetcherConfig setHttpHeaders(List<String> httpHeaders) {
        this.httpHeaders = httpHeaders;
        return this;
    }

    public Map<String, List<String>> getHttpRequestHeaders() {
        return httpRequestHeaders;
    }

    public AtlassianJwtFetcherConfig setHttpRequestHeaders(
            Map<String, List<String>> httpRequestHeaders) {
        this.httpRequestHeaders = httpRequestHeaders;
        return this;
    }

    public Long getOverallTimeoutMillis() {
        return overallTimeoutMillis;
    }

    public AtlassianJwtFetcherConfig setOverallTimeoutMillis(Long overallTimeoutMillis) {
        this.overallTimeoutMillis = overallTimeoutMillis;
        return this;
    }

    public Integer getMaxErrMsgSize() {
        return maxErrMsgSize;
    }

    public AtlassianJwtFetcherConfig setMaxErrMsgSize(Integer maxErrMsgSize) {
        this.maxErrMsgSize = maxErrMsgSize;
        return this;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public AtlassianJwtFetcherConfig setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public AtlassianJwtFetcherConfig setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
        return this;
    }

    public String getIssuer() {
        return issuer;
    }

    public AtlassianJwtFetcherConfig setIssuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    public String getSubject() {
        return subject;
    }

    public AtlassianJwtFetcherConfig setSubject(String subject) {
        this.subject = subject;
        return this;
    }

    public Integer getJwtExpiresInSeconds() {
        return jwtExpiresInSeconds;
    }

    public AtlassianJwtFetcherConfig setJwtExpiresInSeconds(Integer jwtExpiresInSeconds) {
        this.jwtExpiresInSeconds = jwtExpiresInSeconds;
        return this;
    }
}
