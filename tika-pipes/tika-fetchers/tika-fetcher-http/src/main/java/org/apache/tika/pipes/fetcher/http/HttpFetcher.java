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


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaTimeoutException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetcher.RangeFetcher;

/**
 * Based on Apache httpclient
 */
public class HttpFetcher extends AbstractFetcher implements Initializable, RangeFetcher {

    public static String HTTP_HEADER_PREFIX = "http-header:";

    public static String HTTP_FETCH_PREFIX = "http-connection:";

    /**
     * http status code
     */
    public static Property HTTP_STATUS_CODE =
            Property.externalInteger(HTTP_HEADER_PREFIX + "status-code");
    /**
     * Number of redirects
     */
    public static Property HTTP_NUM_REDIRECTS =
            Property.externalInteger(HTTP_FETCH_PREFIX + "num-redirects");

    /**
     * If there were redirects, this captures the final URL visited
     */
    public static Property HTTP_TARGET_URL =
            Property.externalText(HTTP_FETCH_PREFIX + "target-url");

    public static Property HTTP_TARGET_IP_ADDRESS =
            Property.externalText(HTTP_FETCH_PREFIX + "target-ip-address");

    public static Property HTTP_FETCH_TRUNCATED =
            Property.externalBoolean(HTTP_FETCH_PREFIX + "fetch-truncated");

    public static Property HTTP_CONTENT_ENCODING =
            Property.externalText(HTTP_HEADER_PREFIX + "content-encoding");

    public static Property HTTP_CONTENT_TYPE =
            Property.externalText(HTTP_HEADER_PREFIX + "content-type");


    Logger LOG = LoggerFactory.getLogger(HttpFetcher.class);
    private HttpClientFactory httpClientFactory;
    private HttpClient httpClient;

    private int maxRedirects = 10;
    //overall timeout in milliseconds
    private long overallTimeout = -1;

    private long maxSpoolSize = -1;

    //max string length to read from a result if the
    //status code was not in the 200 range
    private int maxErrMsgSize = 10000;

    //httpHeaders to capture in the metadata
    private Set<String> httpHeaders = new HashSet<>();

    public HttpFetcher() throws TikaConfigException {
        httpClientFactory = new HttpClientFactory();
    }

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata) throws IOException, TikaException {
        HttpGet get = new HttpGet(fetchKey);
        RequestConfig requestConfig =
                RequestConfig.custom()
                        .setMaxRedirects(maxRedirects)
                        .setRedirectsEnabled(true).build();
        get.setConfig(requestConfig);
        return get(get, metadata);
    }

    @Override
    public InputStream fetch(String fetchKey, long startRange, long endRange, Metadata metadata)
            throws IOException, TikaException {
        HttpGet get = new HttpGet(fetchKey);
        get.setHeader("Range", "bytes=" + startRange + "-" + endRange);
        return get(get, metadata);
    }

    private InputStream get(HttpGet get, Metadata metadata) throws IOException, TikaException {
        HttpClientContext context = HttpClientContext.create();
        HttpResponse response = null;
        final AtomicBoolean timeout = new AtomicBoolean(false);
        Timer timer = null;
        try {
            if (overallTimeout > -1) {
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        timeout.set(true);
                        if (get != null) {
                            get.abort();
                        }

                    }
                };
                timer = new Timer(false);
                timer.schedule(task, overallTimeout);
            }
            response = httpClient.execute(get, context);

            updateMetadata(get.getURI().toString(), response, context, metadata);

            int code = response.getStatusLine().getStatusCode();
            if (code < 200 || code > 299) {
                throw new IOException("bad status code: " + code + " :: " +
                        responseToString(response));
            }
            try (InputStream is = response.getEntity().getContent()) {
                return spool(is, metadata);
            }
        } catch (IOException e) {
            if (timeout.get() == true) {
                throw new TikaTimeoutException("Overall timeout after " + overallTimeout + "ms");
            } else {
                throw e;
            }
        } finally {
            if (timer != null) {
                timer.cancel();
                timer.purge();
            }
            if (response != null && response instanceof CloseableHttpResponse) {
                ((CloseableHttpResponse) response).close();
            }
        }
    }

    private InputStream spool(InputStream content, Metadata metadata) throws IOException {
        long start = System.currentTimeMillis();
        TemporaryResources tmp = new TemporaryResources();
        Path tmpFile = tmp.createTempFile();
        if (maxSpoolSize < 0) {
            Files.copy(content, tmpFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            try (OutputStream os = Files.newOutputStream(tmpFile)) {
                long totalRead = IOUtils.copyLarge(content, os, 0, maxSpoolSize);
                if (totalRead == maxSpoolSize && content.read() != -1) {
                    metadata.set(HTTP_FETCH_TRUNCATED, "true");
                }
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        LOG.debug("took {} ms to copy to local tmp file", elapsed);
        return TikaInputStream.get(tmpFile, metadata, tmp);
    }

    private void updateMetadata(String url, HttpResponse response, HttpClientContext context,
                                Metadata metadata) {

        metadata.set(HTTP_STATUS_CODE, response.getStatusLine().getStatusCode());
        HttpEntity entity = response.getEntity();
        if (entity.getContentEncoding() != null) {
            metadata.set(HTTP_CONTENT_ENCODING, entity.getContentEncoding().getValue());
        }
        if (entity.getContentType() != null) {
            metadata.set(HTTP_CONTENT_TYPE, entity.getContentType().getValue());
        }

        //load headers
        for (String h : httpHeaders) {
            Header[] headers = response.getHeaders(h);
            if (headers != null && headers.length > 0) {
                String name = HTTP_HEADER_PREFIX + h;
                for (Header header : headers) {
                    metadata.add(name, header.getValue());
                }
            }
        }
        List<URI> uriList = context.getRedirectLocations();
        if (uriList == null) {
            metadata.set(HTTP_NUM_REDIRECTS, 0);
            metadata.set(HTTP_TARGET_URL, url);
        } else {
            metadata.set(HTTP_NUM_REDIRECTS, uriList.size());
            try {
                metadata.set(HTTP_TARGET_URL, uriList.get(uriList.size() - 1).toURL().toString());
            } catch (MalformedURLException e) {
                //swallow
            }
        }
        HttpConnection connection = context.getConnection();
        if (connection instanceof HttpInetConnection) {
            InetAddress inetAddress = ((HttpInetConnection)connection).getRemoteAddress();
            if (inetAddress != null) {
                metadata.set(HTTP_TARGET_IP_ADDRESS, inetAddress.getHostAddress());
            }
        }

    }

    private String responseToString(HttpResponse response) {
        try (InputStream is = response.getEntity().getContent()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copyLarge(is, bos, 0, maxErrMsgSize);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("IOexception trying to read error message", e);
            return "";
        } finally {
            try {
                EntityUtils.consume(response.getEntity());
            } catch (IOException e) {
                //swallow
            }
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

    /**
     * Set the maximum number of bytes to spool to a temp file.
     * If this value is <code>-1</code>, the full stream will be spooled to a temp file
     *
     * Default size is -1.
     *
     * @param maxSpoolSize
     */
    @Field
    public void setMaxSpoolSize(long maxSpoolSize) {
        this.maxSpoolSize = maxSpoolSize;
    }

    @Field
    public void setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }

    /**
     * Which http headers should we capture in the metadata.
     * Keys will be prepended with {@link HttpFetcher#HTTP_HEADER_PREFIX}
     *
     * @param headers
     */
    @Field
    public void setHttpHeaders(List<String> headers) {
        this.httpHeaders.clear();
        this.httpHeaders.addAll(headers);
    }

    /**
     * This sets an overall timeout on the request.  If a server is super slow
     * or the file is very long, the other timeouts might not be triggered.
     *
     * @param overallTimeout
     */
    @Field
    public void setOverallTimeout(long overallTimeout) {
        this.overallTimeout = overallTimeout;
    }

    @Field
    public void setMaxErrMsgSize(int maxErrMsgSize) {
        this.maxErrMsgSize = maxErrMsgSize;
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
