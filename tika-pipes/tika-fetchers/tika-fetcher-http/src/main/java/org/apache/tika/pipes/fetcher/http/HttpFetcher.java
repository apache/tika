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
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import com.nimbusds.jose.JOSEException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.http.ConnectionClosedException;
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
import org.apache.http.impl.conn.ConnectionShutdownException;
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
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetcher.RangeFetcher;
import org.apache.tika.pipes.fetcher.http.config.AdditionalHttpHeaders;
import org.apache.tika.pipes.fetcher.http.config.HttpFetcherConfig;
import org.apache.tika.pipes.fetcher.http.jwt.JwtGenerator;
import org.apache.tika.pipes.fetcher.http.jwt.JwtPrivateKeyCreds;
import org.apache.tika.pipes.fetcher.http.jwt.JwtSecretCreds;
import org.apache.tika.utils.StringUtils;

/**
 * Based on Apache httpclient
 */
public class HttpFetcher extends AbstractFetcher implements Initializable, RangeFetcher {
    public HttpFetcher() {

    }

    private HttpFetcherConfig httpFetcherConfig = new HttpFetcherConfig();
    private HttpClientFactory httpClientFactory = new HttpClientFactory();

    public HttpFetcher(HttpFetcherConfig httpFetcherConfig) {
        this.httpFetcherConfig = httpFetcherConfig;
    }

    public static String HTTP_HEADER_PREFIX = "http-header:";

    public static String HTTP_FETCH_PREFIX = "http-connection:";

    /**
     * http status code
     */
    public static Property HTTP_STATUS_CODE = Property.externalInteger(HTTP_HEADER_PREFIX + "status-code");
    /**
     * Number of redirects
     */
    public static Property HTTP_NUM_REDIRECTS = Property.externalInteger(HTTP_FETCH_PREFIX + "num-redirects");

    /**
     * If there were redirects, this captures the final URL visited
     */
    public static Property HTTP_TARGET_URL = Property.externalText(HTTP_FETCH_PREFIX + "target-url");

    public static Property HTTP_TARGET_IP_ADDRESS = Property.externalText(HTTP_FETCH_PREFIX + "target-ip-address");

    public static Property HTTP_FETCH_TRUNCATED = Property.externalBoolean(HTTP_FETCH_PREFIX + "fetch-truncated");

    public static Property HTTP_CONTENT_ENCODING = Property.externalText(HTTP_HEADER_PREFIX + "content-encoding");

    public static Property HTTP_CONTENT_TYPE = Property.externalText(HTTP_HEADER_PREFIX + "content-type");

    private static String USER_AGENT = "User-Agent";

    Logger LOG = LoggerFactory.getLogger(HttpFetcher.class);

    private HttpClient httpClient;
    //back-off client that disables compression
    private HttpClient noCompressHttpClient;

    JwtGenerator jwtGenerator;

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext) throws IOException, TikaException {
        HttpGet get = new HttpGet(fetchKey);
        RequestConfig requestConfig = RequestConfig
                .custom()
                .setMaxRedirects(httpFetcherConfig.getMaxRedirects())
                .setRedirectsEnabled(httpFetcherConfig.getMaxRedirects() > 0)
                .build();
        get.setConfig(requestConfig);
        putAdditionalHeadersOnRequest(parseContext, get);
        return execute(get, metadata, httpClient, true);
    }

    @Override
    public InputStream fetch(String fetchKey, long startRange, long endRange, Metadata metadata,
                             ParseContext parseContext) throws IOException, TikaException {
        HttpGet get = new HttpGet(fetchKey);
        putAdditionalHeadersOnRequest(parseContext, get);

        get.setHeader("Range", "bytes=" + startRange + "-" + endRange);
        return execute(get, metadata, httpClient, true);
    }

    private void putAdditionalHeadersOnRequest(ParseContext parseContext, HttpGet httpGet) throws TikaException {
        if (!StringUtils.isBlank(httpFetcherConfig.getUserAgent())) {
            httpGet.setHeader(USER_AGENT, httpFetcherConfig.getUserAgent());
        }
        AdditionalHttpHeaders additionalHttpHeaders = parseContext.get(AdditionalHttpHeaders.class);
        if (additionalHttpHeaders != null) {
            additionalHttpHeaders
                    .getHeaders()
                    .forEach(httpGet::setHeader);
        }
        if (jwtGenerator != null) {
            try {
                httpGet.setHeader("Authorization", "Bearer " + jwtGenerator.jwt());
            } catch (JOSEException e) {
                throw new TikaException("Could not generate JWT", e);
            }
        }
    }

    private static void placeHeaderOnGetRequest(HttpGet get, String httpRequestHeader) {
        int idxOfEquals = httpRequestHeader.indexOf(':');
        if (idxOfEquals == -1) {
            return;
        }
        String headerKey = httpRequestHeader
                .substring(0, idxOfEquals)
                .trim();
        String headerValue = httpRequestHeader
                .substring(idxOfEquals + 1)
                .trim();
        get.setHeader(headerKey, headerValue);
    }

    private InputStream execute(HttpGet get, Metadata metadata, HttpClient client, boolean retryOnBadLength) throws IOException {
        HttpClientContext context = HttpClientContext.create();
        HttpResponse response = null;
        final AtomicBoolean timeout = new AtomicBoolean(false);
        Timer timer = null;
        long overallTimeout = httpFetcherConfig.getOverallTimeout() == null ? -1 : httpFetcherConfig.getOverallTimeout();
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
            response = client.execute(get, context);

            updateMetadata(get
                    .getURI()
                    .toString(), response, context, metadata);

            int code = response
                    .getStatusLine()
                    .getStatusCode();
            LOG.info("Fetch id {} status code {}", get.getURI(), code);
            if (code < 200 || code > 299) {
                throw new IOException("bad status code: " + code + " :: " + responseToString(response));
            }
            try (InputStream is = response
                    .getEntity()
                    .getContent()) {
                return spool(is, metadata);
            }
        } catch (ConnectionClosedException e) {

            if (retryOnBadLength && e.getMessage() != null && e
                    .getMessage()
                    .contains("Premature " + "end of " + "Content-Length delimited message")) {
                //one trigger for this is if the server sends the uncompressed length
                //and then compresses the stream. See HTTPCLIENT-2176
                LOG.warn("premature end of content-length delimited message; retrying with " + "content compression" +
                        " disabled for {}", get.getURI());
                return execute(get, metadata, noCompressHttpClient, false);
            }
            throw e;
        } catch (IOException e) {
            if (timeout.get()) {
                throw new TikaTimeoutException("Overall timeout after " + overallTimeout + "ms");
            } else {
                throw e;
            }
        } finally {
            if (timer != null) {
                timer.cancel();
                timer.purge();
            }
            if (response != null) {
                //make sure you've consumed the entity
                EntityUtils.consumeQuietly(response.getEntity());
            }
            if (response instanceof CloseableHttpResponse) {
                ((CloseableHttpResponse) response).close();
            }
        }
    }

    private InputStream spool(InputStream content, Metadata metadata) throws IOException {
        long start = System.currentTimeMillis();
        TemporaryResources tmp = new TemporaryResources();
        Path tmpFile = tmp.createTempFile(metadata);
        if (httpFetcherConfig.getMaxSpoolSize() < 0) {
            Files.copy(content, tmpFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            try (OutputStream os = Files.newOutputStream(tmpFile)) {
                long totalRead = IOUtils.copyLarge(content, os, 0, httpFetcherConfig.getMaxSpoolSize());
                if (totalRead == httpFetcherConfig.getMaxSpoolSize() && content.read() != -1) {
                    metadata.set(HTTP_FETCH_TRUNCATED, "true");
                }
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        LOG.debug("took {} ms to copy to local tmp file", elapsed);
        return TikaInputStream.get(tmpFile, metadata, tmp);
    }

    private void updateMetadata(String url, HttpResponse response, HttpClientContext context, Metadata metadata) {
        if (response == null) {
            return;
        }

        if (response.getStatusLine() != null) {
            metadata.set(HTTP_STATUS_CODE, response
                    .getStatusLine()
                    .getStatusCode());
        }

        HttpEntity entity = response.getEntity();
        if (entity != null && entity.getContentEncoding() != null) {
            metadata.set(HTTP_CONTENT_ENCODING, entity
                    .getContentEncoding()
                    .getValue());
        }
        if (entity != null && entity.getContentType() != null) {
            metadata.set(HTTP_CONTENT_TYPE, entity
                    .getContentType()
                    .getValue());
        }

        //load headers
        if (httpFetcherConfig.getHttpHeaders() != null) {
            for (String h : httpFetcherConfig.getHttpHeaders()) {
                Header[] headers = response.getHeaders(h);
                if (headers != null && headers.length > 0) {
                    String name = HTTP_HEADER_PREFIX + h;
                    for (Header header : headers) {
                        metadata.add(name, header.getValue());
                    }
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
                //there were some rare NPEs in this part of the codebase
                //during development.
                URI uri = uriList.get(uriList.size() - 1);
                if (uri != null) {
                    URL u = uri.toURL();
                    metadata.set(HTTP_TARGET_URL, u.toString());
                    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, u.getFile());
                }
            } catch (MalformedURLException e) {
                //swallow
            }
        }
        HttpConnection connection = context.getConnection();
        if (connection instanceof HttpInetConnection) {
            try {
                InetAddress inetAddress = ((HttpInetConnection) connection).getRemoteAddress();
                if (inetAddress != null) {
                    metadata.set(HTTP_TARGET_IP_ADDRESS, inetAddress.getHostAddress());
                }
            } catch (ConnectionShutdownException e) {
                LOG.warn("connection shutdown while trying to get target URL: " + url);
            }
        }
    }

    private String responseToString(HttpResponse response) {
        if (response.getEntity() == null) {
            return "";
        }
        try (InputStream is = response
                .getEntity()
                .getContent()) {
            UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream
                    .builder()
                    .get();
            IOUtils.copyLarge(is, bos, 0, httpFetcherConfig.getMaxErrMsgSize());
            return bos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("IOException trying to read error message", e);
            return "";
        } catch (NullPointerException e) {
            return "";
        } finally {
            EntityUtils.consumeQuietly(response.getEntity());
        }

    }

    @Field
    public void setUserName(String userName) {
        httpFetcherConfig.setUserName(userName);
    }

    @Field
    public void setPassword(String password) {
        httpFetcherConfig.setPassword(password);
    }

    @Field
    public void setNtDomain(String domain) {
        httpFetcherConfig.setNtDomain(domain);
    }

    @Field
    public void setAuthScheme(String authScheme) {
        httpFetcherConfig.setAuthScheme(authScheme);
    }

    @Field
    public void setProxyHost(String proxyHost) {
        httpFetcherConfig.setProxyHost(proxyHost);
    }

    @Field
    public void setProxyPort(int proxyPort) {
        httpFetcherConfig.setProxyPort(proxyPort);
    }

    @Field
    public void setConnectTimeout(int connectTimeout) {
        httpFetcherConfig.setConnectTimeout(connectTimeout);
    }

    @Field
    public void setRequestTimeout(int requestTimeout) {
        httpFetcherConfig.setRequestTimeout(requestTimeout);
    }

    @Field
    public void setSocketTimeout(int socketTimeout) {
        httpFetcherConfig.setSocketTimeout(socketTimeout);
    }

    @Field
    public void setMaxConnections(int maxConnections) {
        httpFetcherConfig.setMaxConnections(maxConnections);
    }

    @Field
    public void setMaxConnectionsPerRoute(int maxConnectionsPerRoute) {
        httpFetcherConfig.setMaxConnectionsPerRoute(maxConnectionsPerRoute);
    }

    /**
     * Set the maximum number of bytes to spool to a temp file.
     * If this value is <code>-1</code>, the full stream will be spooled to a temp file
     * <p>
     * Default size is -1.
     *
     * @param maxSpoolSize
     */
    @Field
    public void setMaxSpoolSize(long maxSpoolSize) {
        httpFetcherConfig.setMaxSpoolSize(maxSpoolSize);
    }

    @Field
    public void setMaxRedirects(int maxRedirects) {
        httpFetcherConfig.setMaxRedirects(maxRedirects);
    }

    /**
     * Which http request headers should we send in the http fetch requests.
     *
     * @param headers The headers to add to the HTTP GET requests.
     */
    @Field
    public void setHttpRequestHeaders(List<String> headers) {
        httpFetcherConfig.setHttpRequestHeaders(new ArrayList<>());
        if (headers != null) {
            httpFetcherConfig
                    .getHttpRequestHeaders()
                    .addAll(headers);
        }
    }

    /**
     * Which http headers should we capture in the metadata.
     * Keys will be prepended with {@link HttpFetcher#HTTP_HEADER_PREFIX}
     *
     * @param headers
     */
    @Field
    public void setHttpHeaders(List<String> headers) {
        httpFetcherConfig.setHttpHeaders(new ArrayList<>());
        if (headers != null) {
            httpFetcherConfig
                    .getHttpHeaders()
                    .addAll(headers);
        }
    }

    /**
     * This sets an overall timeout on the request.  If a server is super slow
     * or the file is very long, the other timeouts might not be triggered.
     *
     * @param overallTimeout
     */
    @Field
    public void setOverallTimeout(long overallTimeout) {
        httpFetcherConfig.setOverallTimeout(overallTimeout);
    }

    @Field
    public void setMaxErrMsgSize(int maxErrMsgSize) {
        httpFetcherConfig.setMaxErrMsgSize(maxErrMsgSize);
    }

    /**
     * When making the request, what User-Agent is sent in the request.
     * By default httpclient adds e.g. "Apache-HttpClient/4.5.13 (Java/x.y.z)"
     *
     * @param userAgent
     */
    @Field
    public void setUserAgent(String userAgent) {
        httpFetcherConfig.setUserAgent(userAgent);
    }

    @Field
    public void setJwtIssuer(String jwtIssuer) {
        httpFetcherConfig.setJwtIssuer(jwtIssuer);
    }

    @Field
    public void setJwtSubject(String jwtSubject) {
        httpFetcherConfig.setJwtSubject(jwtSubject);
    }

    @Field
    public void setJwtExpiresInSeconds(int jwtExpiresInSeconds) {
        httpFetcherConfig.setJwtExpiresInSeconds(jwtExpiresInSeconds);
    }

    @Field
    public void setJwtSecret(String jwtSecret) {
        httpFetcherConfig.setJwtSecret(jwtSecret);
    }

    @Field
    public void setJwtPrivateKeyBase64(String jwtPrivateKeyBase64) {
        httpFetcherConfig.setJwtPrivateKeyBase64(jwtPrivateKeyBase64);
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        if (httpFetcherConfig.getSocketTimeout() != null) {
            httpClientFactory.setSocketTimeout(httpFetcherConfig.getSocketTimeout());
        }
        if (httpFetcherConfig.getRequestTimeout() != null) {
            httpClientFactory.setRequestTimeout(httpFetcherConfig.getRequestTimeout());
        }
        if (httpFetcherConfig.getConnectTimeout() != null) {
            httpClientFactory.setSocketTimeout(httpFetcherConfig.getConnectTimeout());
        }
        if (httpFetcherConfig.getMaxConnections() != null) {
            httpClientFactory.setMaxConnections(httpFetcherConfig.getMaxConnections());
        }
        if (httpFetcherConfig.getMaxConnectionsPerRoute() != null) {
            httpClientFactory.setMaxConnectionsPerRoute(httpFetcherConfig.getMaxConnectionsPerRoute());
        }
        if (!StringUtils.isBlank(httpFetcherConfig.getAuthScheme())) {
            httpClientFactory.setUserName(httpFetcherConfig.getUserName());
            httpClientFactory.setPassword(httpFetcherConfig.getPassword());
            httpClientFactory.setAuthScheme(httpFetcherConfig.getAuthScheme());
            if (httpFetcherConfig.getNtDomain() != null) {
                httpClientFactory.setNtDomain(httpFetcherConfig.getNtDomain());
            }
        }
        if (!StringUtils.isBlank(httpFetcherConfig.getProxyHost())) {
            httpClientFactory.setProxyHost(httpFetcherConfig.getProxyHost());
            httpClientFactory.setProxyPort(httpFetcherConfig.getProxyPort());
        }
        httpClient = httpClientFactory.build();
        HttpClientFactory cp = httpClientFactory.copy();
        cp.setDisableContentCompression(true);
        noCompressHttpClient = cp.build();

        if (!StringUtils.isBlank(httpFetcherConfig.getJwtPrivateKeyBase64())) {
            PrivateKey key = JwtPrivateKeyCreds.convertBase64ToPrivateKey(httpFetcherConfig.getJwtPrivateKeyBase64());
            jwtGenerator = new JwtGenerator(new JwtPrivateKeyCreds(key, httpFetcherConfig.getJwtIssuer(),
                    httpFetcherConfig.getJwtSubject(), httpFetcherConfig.getJwtExpiresInSeconds()));
        } else if (!StringUtils.isBlank(httpFetcherConfig.getJwtSecret())) {
            jwtGenerator = new JwtGenerator(new JwtSecretCreds(httpFetcherConfig
                    .getJwtSecret()
                    .getBytes(StandardCharsets.UTF_8), httpFetcherConfig.getJwtIssuer(), httpFetcherConfig.getJwtSubject(), httpFetcherConfig.getJwtExpiresInSeconds()));
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {
        if (!StringUtils.isBlank(httpFetcherConfig.getJwtSecret()) && !StringUtils.isBlank(httpFetcherConfig.getJwtPrivateKeyBase64())) {
            throw new TikaConfigException("Both JWT secret and JWT private key base 64 were " + "specified. Only one or the other is supported");
        }
    }

    public void setHttpClientFactory(HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public HttpFetcherConfig getHttpFetcherConfig() {
        return httpFetcherConfig;
    }

    public void setHttpFetcherConfig(HttpFetcherConfig httpFetcherConfig) {
        this.httpFetcherConfig = httpFetcherConfig;
    }
}
