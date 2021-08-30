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
package org.apache.tika.pipes.fetcher.http;


import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetcher.RangeFetcher;

/**
 * Based on Apache httpclient
 */
public class HttpFetcher extends AbstractFetcher implements Initializable, RangeFetcher {

    Logger LOG = LoggerFactory.getLogger(HttpFetcher.class);
    private HttpClientFactory httpClientFactory;
    private HttpClient httpClient;

    public HttpFetcher() throws TikaConfigException {
        httpClientFactory = new HttpClientFactory();
    }

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata) throws IOException, TikaException {
        HttpGet get = new HttpGet(fetchKey);
        return get(get);
    }

    @Override
    public InputStream fetch(String fetchKey, long startRange, long endRange, Metadata metadata)
            throws IOException, TikaException {
        HttpGet get = new HttpGet(fetchKey);
        get.setHeader("Range", "bytes=" + startRange + "-" + endRange);
        return get(get);
    }

    private InputStream get(HttpGet get) throws IOException, TikaException {
        HttpResponse response = httpClient.execute(get);
        int code = response.getStatusLine().getStatusCode();
        if (code < 200 || code > 299) {
            throw new IOException("bad status code: " + code + " :: " +
                    responseToString(response.getEntity().getContent()));
        }

        //spool to local
        long start = System.currentTimeMillis();
        TikaInputStream tis = TikaInputStream.get(response.getEntity().getContent());
        tis.getPath();
        if (response instanceof CloseableHttpResponse) {
            ((CloseableHttpResponse) response).close();
        }
        long elapsed = System.currentTimeMillis() - start;
        LOG.debug("took {} ms to copy to local tmp file", elapsed);
        return tis;
    }

    private String responseToString(InputStream is) {
        try {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("IOexception trying to read error message", e);
            return "";
        }
    }

    @Field
    public void setUserName(String userName) {
        httpClientFactory.setUserName(userName);
    }

    @Field
    public void setPassword(String password) {
        httpClientFactory.setPassword(password);
    }

    @Field
    public void setNtDomain(String domain) {
        httpClientFactory.setNtDomain(domain);
    }

    @Field
    public void setAuthScheme(String authScheme) {
        httpClientFactory.setAuthScheme(authScheme);
    }

    @Field
    public void setProxyHost(String proxyHost) {
        httpClientFactory.setProxyHost(proxyHost);
    }

    @Field
    public void setProxyPort(int proxyPort) {
        httpClientFactory.setProxyPort(proxyPort);
    }

    @Field
    public void setConnectTimeout(int connectTimeout) {
        httpClientFactory.setConnectTimeout(connectTimeout);
    }

    @Field
    public void setRequestTimeout(int requestTimeout) {
        httpClientFactory.setRequestTimeout(requestTimeout);
    }

    @Field
    public void setSocketTimeout(int socketTimeout) {
        httpClientFactory.setSocketTimeout(socketTimeout);
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        httpClient = httpClientFactory.build();
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {

    }
}
