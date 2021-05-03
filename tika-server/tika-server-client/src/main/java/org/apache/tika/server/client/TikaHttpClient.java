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
package org.apache.tika.server.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaException;

/**
 * Low-level class to handle the http layer.
 */
class TikaHttpClient {

    private static final String EMIT_ENDPOINT = "emit";
    private static final String TIKA_ENDPOINT = "tika";
    private static final String ASYNC_ENDPOINT = "async";
    private static final Logger LOGGER = LoggerFactory.getLogger(TikaHttpClient.class);
    private final HttpHost httpHost;
    private final HttpClient httpClient;
    private final String emitEndPointUrl;
    private final String asyncEndPointUrl;
    private final String tikaUrl;
    private final int maxRetries = 3;
    //if can't make contact with Tika server, max wait time in ms
    private final long maxWaitForTikaMs = 120000;
    //how often to ping /tika (in ms) to see if the server is up and running
    private final long pulseWaitForTikaMs = 1000;

    /**
     * @param baseUrl    url to base endpoint
     * @param httpHost
     * @param httpClient
     */
    private TikaHttpClient(String baseUrl, HttpHost httpHost, HttpClient httpClient) {
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        this.emitEndPointUrl = baseUrl + EMIT_ENDPOINT;
        this.asyncEndPointUrl = baseUrl + ASYNC_ENDPOINT;
        this.tikaUrl = baseUrl + TIKA_ENDPOINT;
        this.httpHost = httpHost;
        this.httpClient = httpClient;
    }

    static TikaHttpClient get(String baseUrl) throws TikaClientConfigException {
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException e) {
            throw new TikaClientConfigException("bad URI", e);
        }
        HttpHost httpHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        //TODO: need to add other configuration stuff? proxy, username, password, timeouts...
        HttpClient client = HttpClients.createDefault();
        return new TikaHttpClient(baseUrl, httpHost, client);
    }

    public TikaEmitterResult postJsonAsync(String jsonRequest) {
        return postJson(asyncEndPointUrl, jsonRequest);
    }

    public TikaEmitterResult postJson(String jsonRequest) {
        return postJson(emitEndPointUrl, jsonRequest);
    }

    private TikaEmitterResult postJson(String endPoint, String jsonRequest) {
        HttpPost post = new HttpPost(endPoint);
        ByteArrayEntity entity = new ByteArrayEntity(jsonRequest.getBytes(StandardCharsets.UTF_8));
        post.setEntity(entity);
        post.setHeader("Content-Type", "application/json");

        int tries = 0;
        long start = System.currentTimeMillis();
        try {
            while (tries++ < maxRetries) {
                HttpResponse response = null;
                try {
                    response = httpClient.execute(httpHost, post);
                } catch (IOException e) {
                    LOGGER.warn("Exception trying to parse", e);
                    waitForServer();
                    continue;
                }
                String msg = "";
                try {
                    msg = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    //swallow
                }
                long elapsed = System.currentTimeMillis() - start;
                TikaEmitterResult.STATUS status = TikaEmitterResult.STATUS.OK;
                if (response.getStatusLine().getStatusCode() != 200) {
                    status = TikaEmitterResult.STATUS.NOT_OK;
                } else {
                    //pull out stacktrace from parse exception?
                }
                return new TikaEmitterResult(status, elapsed, msg);
            }
        } catch (TimeoutWaitingForTikaException e) {
            long elapsed = System.currentTimeMillis() - start;
            return new TikaEmitterResult(TikaEmitterResult.STATUS.TIMED_OUT_WAITING_FOR_TIKA,
                    elapsed, "");
        }
        long elapsed = System.currentTimeMillis() - start;
        return new TikaEmitterResult(TikaEmitterResult.STATUS.EXCEEDED_MAX_RETRIES, elapsed, "");
    }


    private void waitForServer() throws TimeoutWaitingForTikaException {
        long start = System.currentTimeMillis();
        long elapsed = System.currentTimeMillis() - start;
        LOGGER.info("server unreachable; waiting for it to restart");
        while (elapsed < maxWaitForTikaMs) {
            try {
                Thread.sleep(pulseWaitForTikaMs);
            } catch (InterruptedException e) {
                //swallow
            }

            HttpGet get = new HttpGet(tikaUrl);
            try {
                HttpResponse response = httpClient.execute(httpHost, get);
                if (response.getStatusLine().getStatusCode() == 200) {
                    LOGGER.debug("server back up");
                    return;
                }
            } catch (IOException e) {
                elapsed = System.currentTimeMillis() - start;
                LOGGER.debug("waiting for server; failed to reach it: {} ms", elapsed);
            }

            elapsed = System.currentTimeMillis() - start;
        }

        LOGGER.warn("Timeout waiting for tika server {} in {} ms", tikaUrl, elapsed);
        throw new TimeoutWaitingForTikaException("");
    }

    private class TimeoutWaitingForTikaException extends TikaException {
        public TimeoutWaitingForTikaException(String msg) {
            super(msg);
        }
    }
}
