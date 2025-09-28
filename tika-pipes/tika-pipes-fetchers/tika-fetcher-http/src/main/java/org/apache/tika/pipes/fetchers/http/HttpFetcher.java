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
package org.apache.tika.pipes.fetchers.http;


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
import lombok.extern.slf4j.Slf4j;
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
import org.pf4j.Extension;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaTimeoutException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.fetchers.core.Fetcher;
import org.apache.tika.pipes.fetchers.core.FetcherConfig;
import org.apache.tika.pipes.fetchers.http.config.AuthConfig;
import org.apache.tika.pipes.fetchers.http.config.HttpFetcherConfig;
import org.apache.tika.pipes.fetchers.http.config.JwtConfig;
import org.apache.tika.pipes.fetchers.http.config.ProxyConfig;
import org.apache.tika.pipes.fetchers.http.jwt.JwtGenerator;
import org.apache.tika.pipes.fetchers.http.jwt.JwtPrivateKeyCreds;
import org.apache.tika.pipes.fetchers.http.jwt.JwtSecretCreds;
import org.apache.tika.utils.StringUtils;

/**
 * Based on Apache httpclient
 */
@Extension
@Slf4j
public class HttpFetcher implements Fetcher {
    private final HttpClientFactory httpClientFactory = new HttpClientFactory();
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

    private static final String USER_AGENT = "User-Agent";

    private HttpClient httpClient;
    private HttpClient noCompressHttpClient; // back-off client used disabling compression
    private JwtGenerator jwtGenerator;
    private boolean isInit = false;

    @Override
    public InputStream fetch(FetcherConfig fetcherConfig, String fetchKey, Map<String, Object> fetchMetadata, Map<String, Object> responseMetadata) {
        try {
            HttpFetcherConfig httpFetcherConfig = (HttpFetcherConfig) fetcherConfig;
            initIfNeeded(httpFetcherConfig);
            HttpGet get = new HttpGet(fetchKey);
            RequestConfig requestConfig = RequestConfig
                    .custom()
                    .setMaxRedirects(httpFetcherConfig.getMaxRedirects())
                    .setRedirectsEnabled(httpFetcherConfig.getMaxRedirects() > 0)
                    .build();
            get.setConfig(requestConfig);
            putAdditionalHeadersOnRequest(httpFetcherConfig, get);
            return execute(get, httpFetcherConfig, fetchMetadata, httpClient, true);
        } catch (TikaException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void putAdditionalHeadersOnRequest(HttpFetcherConfig httpFetcherConfig, HttpGet httpGet) throws TikaException {
        if (!StringUtils.isBlank(httpFetcherConfig.getUserAgent())) {
            httpGet.setHeader(USER_AGENT, httpFetcherConfig.getUserAgent());
        }
        if (httpFetcherConfig.getHttpRequestHeaders() != null) {
            httpFetcherConfig.getHttpRequestHeaders()
                    .forEach((header, values) -> {
                        for (String value : values) {
                            httpGet.addHeader(header, value);
                        }
                    });
        }
        if (jwtGenerator != null) {
            try {
                httpGet.setHeader("Authorization", "Bearer " + jwtGenerator.jwt());
            } catch (JOSEException e) {
                throw new TikaException("Could not generate JWT", e);
            }
        }
    }

    private InputStream execute(HttpGet get, HttpFetcherConfig httpFetcherConfig, Map<String, Object> fetchMetadata, HttpClient client, boolean retryOnBadLength) throws IOException {
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
                    .toString(), response, context, fetchMetadata, httpFetcherConfig);

            int code = response
                    .getStatusLine()
                    .getStatusCode();
            log.info("Fetch id {} status code {}", get.getURI(), code);
            if (code < 200 || code > 299) {
                throw new IOException("bad status code: " + code + " :: " + responseToString(httpFetcherConfig, response));
            }
            try (InputStream is = response
                    .getEntity()
                    .getContent()) {
                return spool(httpFetcherConfig, is, fetchMetadata);
            }
        } catch (ConnectionClosedException e) {

            if (retryOnBadLength && e.getMessage() != null && e
                    .getMessage()
                    .contains("Premature " + "end of " + "Content-Length delimited message")) {
                //one trigger for this is if the server sends the uncompressed length
                //and then compresses the stream. See HTTPCLIENT-2176
                log.warn("premature end of content-length delimited message; retrying with " + "content compression" +
                        " disabled for {}", get.getURI());
                return execute(get, httpFetcherConfig, fetchMetadata, noCompressHttpClient, false);
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

    private InputStream spool(HttpFetcherConfig httpFetcherConfig, InputStream content, Map<String, Object> fetchMetadata) throws IOException {
        long start = System.currentTimeMillis();
        TemporaryResources tmp = new TemporaryResources();
        Path tmpFile = tmp.createTempFile();
        if (httpFetcherConfig.getMaxSpoolSize() < 0) {
            Files.copy(content, tmpFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            try (OutputStream os = Files.newOutputStream(tmpFile)) {
                long totalRead = IOUtils.copyLarge(content, os, 0, httpFetcherConfig.getMaxSpoolSize());
                if (totalRead == httpFetcherConfig.getMaxSpoolSize() && content.read() != -1) {
                    fetchMetadata.put(HTTP_FETCH_TRUNCATED.getName(), "true");
                }
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        log.debug("took {} ms to copy to local tmp file", elapsed);
        return TikaInputStream.get(tmpFile);
    }

    private void updateMetadata(String url, HttpResponse response, HttpClientContext context, Map<String, Object> fetchMetadata, HttpFetcherConfig httpFetcherConfig) {
        if (response == null) {
            return;
        }

        if (response.getStatusLine() != null) {
            fetchMetadata.put(HTTP_STATUS_CODE.getName(), response
                    .getStatusLine()
                    .getStatusCode());
        }

        HttpEntity entity = response.getEntity();
        if (entity != null && entity.getContentEncoding() != null) {
            fetchMetadata.put(HTTP_CONTENT_ENCODING.getName(), entity
                    .getContentEncoding()
                    .getValue());
        }
        if (entity != null && entity.getContentType() != null) {
            fetchMetadata.put(HTTP_CONTENT_TYPE.getName(), entity
                    .getContentType()
                    .getValue());
        }

        //load headers
        if (httpFetcherConfig.getHttpHeaders() != null) {
            for (String h : httpFetcherConfig.getHttpHeaders()) {
                Header[] headers = response.getHeaders(h);
                if (headers != null && headers.length > 0) {
                    String name = HTTP_HEADER_PREFIX + h;
                    List<String> headerList = new ArrayList<>();
                    fetchMetadata.put(name, headerList);
                    for (Header header : headers) {
                        headerList.add(header.getValue());
                    }
                    fetchMetadata.put(name, headerList);
                }
            }
        }
        List<URI> uriList = context.getRedirectLocations();
        if (uriList == null) {
            fetchMetadata.put(HTTP_NUM_REDIRECTS.getName(), 0);
            fetchMetadata.put(HTTP_TARGET_URL.getName(), url);
        } else {
            fetchMetadata.put(HTTP_NUM_REDIRECTS.getName(), uriList.size());
            try {
                //there were some rare NPEs in this part of the codebase
                //during development.
                URI uri = uriList.get(uriList.size() - 1);
                if (uri != null) {
                    URL u = uri.toURL();
                    fetchMetadata.put(HTTP_TARGET_URL.getName(), u.toString());
                    fetchMetadata.put(TikaCoreProperties.RESOURCE_NAME_KEY, u.getFile());
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
                    fetchMetadata.put(HTTP_TARGET_IP_ADDRESS.getName(), inetAddress.getHostAddress());
                }
            } catch (ConnectionShutdownException e) {
                log.warn("connection shutdown while trying to get target URL: " + url);
            }
        }
    }

    private String responseToString(HttpFetcherConfig httpFetcherConfig, HttpResponse response) {
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
            log.warn("IOException trying to read error message", e);
            return "";
        } catch (NullPointerException e) {
            return "";
        } finally {
            EntityUtils.consumeQuietly(response.getEntity());
        }
    }

    public void initIfNeeded(HttpFetcherConfig httpFetcherConfig) throws TikaConfigException {
        if (isInit) {
            return;
        }
        if (httpFetcherConfig.getAuthConfig() == null) {
            httpFetcherConfig.setAuthConfig(new AuthConfig());
        }
        if (httpFetcherConfig.getAuthConfig().getJwtConfig() == null) {
            httpFetcherConfig.getAuthConfig().setJwtConfig(new JwtConfig());
        }
        if (httpFetcherConfig.getProxyConfig() == null) {
            httpFetcherConfig.setProxyConfig(new ProxyConfig());
        }
        checkInitialization(httpFetcherConfig);
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
        if (!StringUtils.isBlank(httpFetcherConfig.getAuthConfig().getAuthScheme())) {
            httpClientFactory.setUserName(httpFetcherConfig.getAuthConfig().getUserName());
            httpClientFactory.setPassword(httpFetcherConfig.getAuthConfig().getPassword());
            httpClientFactory.setAuthScheme(httpFetcherConfig.getAuthConfig().getAuthScheme());
            if (httpFetcherConfig.getAuthConfig().getNtDomain() != null) {
                httpClientFactory.setNtDomain(httpFetcherConfig.getAuthConfig().getNtDomain());
            }
        }
        if (!StringUtils.isBlank(httpFetcherConfig.getProxyConfig().getProxyHost())) {
            httpClientFactory.setProxyHost(httpFetcherConfig.getProxyConfig().getProxyHost());
            httpClientFactory.setProxyPort(httpFetcherConfig.getProxyConfig().getProxyPort());
        }
        httpClient = httpClientFactory.build();
        HttpClientFactory cp = httpClientFactory.copy();
        cp.setDisableContentCompression(true);
        noCompressHttpClient = cp.build();

        JwtConfig jwtConfig = httpFetcherConfig
                .getAuthConfig()
                .getJwtConfig();
        if (!StringUtils.isBlank(jwtConfig.getJwtPrivateKeyBase64())) {
            PrivateKey key = JwtPrivateKeyCreds.convertBase64ToPrivateKey(jwtConfig.getJwtPrivateKeyBase64());
            jwtGenerator = new JwtGenerator(new JwtPrivateKeyCreds(key, jwtConfig.getJwtIssuer(),
                    jwtConfig.getJwtSubject(), jwtConfig.getJwtExpiresInSeconds()));
        } else if (!StringUtils.isBlank(jwtConfig.getJwtSecret())) {
            jwtGenerator = new JwtGenerator(new JwtSecretCreds(jwtConfig
                    .getJwtSecret()
                    .getBytes(StandardCharsets.UTF_8), jwtConfig.getJwtIssuer(), jwtConfig.getJwtSubject(), jwtConfig.getJwtExpiresInSeconds()));
        }
        isInit = true;
    }

    public void checkInitialization(HttpFetcherConfig httpFetcherConfig) throws TikaConfigException {
        if (!StringUtils.isBlank(httpFetcherConfig.getAuthConfig().getJwtConfig().getJwtSecret()) && !StringUtils.isBlank(httpFetcherConfig.getAuthConfig().getJwtConfig().getJwtPrivateKeyBase64())) {
            throw new TikaConfigException("Both JWT secret and JWT private key base 64 were " + "specified. Only one or the other is supported");
        }
    }
}
