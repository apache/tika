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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

public class AtlassianJwtFetcherConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static AtlassianJwtFetcherConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json, AtlassianJwtFetcherConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse AtlassianJwtFetcherConfig from JSON", e);
        }
    }

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

    public Integer getRequestTimeout() {
        return requestTimeout;
    }

    public AtlassianJwtFetcherConfig setRequestTimeout(Integer requestTimeout) {
        this.requestTimeout = requestTimeout;
        return this;
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    public AtlassianJwtFetcherConfig setConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public Integer getSocketTimeout() {
        return socketTimeout;
    }

    public AtlassianJwtFetcherConfig setSocketTimeout(Integer socketTimeout) {
        this.socketTimeout = socketTimeout;
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

    public Long getOverallTimeout() {
        return overallTimeout;
    }

    public AtlassianJwtFetcherConfig setOverallTimeout(Long overallTimeout) {
        this.overallTimeout = overallTimeout;
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
