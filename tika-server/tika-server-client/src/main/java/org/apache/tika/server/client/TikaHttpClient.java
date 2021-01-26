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

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * Low-level class to handle the http layer.
 */
class TikaHttpClient {

    private static final String ENDPOINT = "emit";
    private static final String TIKA_ENDPOINT = "tika";
    private static final Logger LOGGER = LoggerFactory.getLogger(TikaHttpClient.class);
    private final HttpHost httpHost;
    private final HttpClient httpClient;
    private final String endPointUrl;
    private final String tikaUrl;
    private int maxRetries = 3;
    //if can't make contact with Tika server, max wait time in ms
    private long maxWaitForTikaMs = 120000;
    //how often to ping /tika (in ms) to see if the server is up and running
    private long pulseWaitForTikaMs = 1000;

    static TikaHttpClient get(String baseUrl) throws TikaClientConfigException {
        String endPointUrl = baseUrl.endsWith("/") ? baseUrl+ENDPOINT : baseUrl+"/"+ENDPOINT;
        String tikaUrl = baseUrl.endsWith("/") ? baseUrl+TIKA_ENDPOINT : baseUrl+"/"+TIKA_ENDPOINT;
        URI uri;
        try {
            uri = new URI(endPointUrl);
        } catch (URISyntaxException e) {
            throw new TikaClientConfigException("bad URI", e);
        }
        HttpHost httpHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        //TODO: need to add other configuration stuff? proxy, username, password, timeouts...
        HttpClient client = HttpClients.createDefault();
        return new TikaHttpClient(endPointUrl, tikaUrl, httpHost, client);
    }

    /**
     *
     * @param endPointUrl full url to the tika-server including endpoint
     * @param tikaUrl url to /tika endpoint to use to check on server status
     * @param httpHost
     * @param httpClient
     */
    private TikaHttpClient(String endPointUrl, String tikaUrl, HttpHost httpHost, HttpClient httpClient) {
        this.endPointUrl = endPointUrl;
        this.tikaUrl = tikaUrl;
        this.httpHost = httpHost;
        this.httpClient = httpClient;
    }


    public TikaEmitterResult postJson(String jsonRequest) {
        System.out.println("NED:"+endPointUrl);
        HttpPost post = new HttpPost(endPointUrl);
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
            return new TikaEmitterResult(
                    TikaEmitterResult.STATUS.TIMED_OUT_WAITING_FOR_TIKA,
                    elapsed, ""
            );
        }
        long elapsed = System.currentTimeMillis() - start;
        return new TikaEmitterResult(
                TikaEmitterResult.STATUS.EXCEEDED_MAX_RETRIES,
                elapsed, ""
        );
    }


    private void waitForServer() throws TimeoutWaitingForTikaException {
        long start = System.currentTimeMillis();
        long elapsed = System.currentTimeMillis() - start;
        LOGGER.info("server unreachable; waiting for it to restart");
        while (elapsed < maxWaitForTikaMs) {
            try {
                Thread.sleep(pulseWaitForTikaMs);
            } catch (InterruptedException e) {

            }

            HttpGet get = new HttpGet(tikaUrl);
            try {
                HttpResponse response = httpClient.execute(httpHost, get);
                if (response.getStatusLine().getStatusCode() == 200) {
                    LOGGER.debug("server back up");
                    return;
                }
            } catch (IOException e) {
                elapsed = System.currentTimeMillis()-start;
                LOGGER.debug("waiting for server; failed to reach it: {} ms",
                        elapsed);
            }

            elapsed = System.currentTimeMillis()-start;
        }

        LOGGER.warn("Timeout waiting for tika server {} in {} ms", tikaUrl,
                elapsed);
        throw new TimeoutWaitingForTikaException("");
    }

    private class TimeoutWaitingForTikaException extends TikaException {
        public TimeoutWaitingForTikaException(String msg) {
            super(msg);
        }
    }
}