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
package org.apache.tika.pipes.fetcher.atlassianjwt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
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
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.fetcher.FetcherConfig;
import org.apache.tika.pipes.fetcher.atlassianjwt.config.AtlassianJwtFetcherConfig;
import org.apache.tika.utils.StringUtils;

@Extension
@Slf4j
public class AtlassianJwtFetcher implements Fetcher {
    private final HttpClientFactory httpClientFactory = new HttpClientFactory();
    public static String HTTP_HEADER_PREFIX = "http-header:";
    public static String HTTP_FETCH_PREFIX = "http-connection:";

    public static Property HTTP_STATUS_CODE = Property.externalInteger(HTTP_HEADER_PREFIX + "status-code");
    public static Property HTTP_NUM_REDIRECTS = Property.externalInteger(HTTP_FETCH_PREFIX + "num-redirects");
    public static Property HTTP_TARGET_URL = Property.externalText(HTTP_FETCH_PREFIX + "target-url");
    public static Property HTTP_TARGET_IP_ADDRESS = Property.externalText(HTTP_FETCH_PREFIX + "target-ip-address");
    public static Property HTTP_FETCH_TRUNCATED = Property.externalBoolean(HTTP_FETCH_PREFIX + "fetch-truncated");
    public static Property HTTP_CONTENT_ENCODING = Property.externalText(HTTP_HEADER_PREFIX + "content-encoding");
    public static Property HTTP_CONTENT_TYPE = Property.externalText(HTTP_HEADER_PREFIX + "content-type");

    private static final String USER_AGENT = "User-Agent";

    private HttpClient httpClient;
    private HttpClient noCompressHttpClient;
    private AtlassianJwtGenerator jwtGenerator;
    private boolean isInit = false;

    @Override
    public InputStream fetch(FetcherConfig fetcherConfig, String fetchKey, Map<String, Object> fetchMetadata, Map<String, Object> responseMetadata) {
        try {
            AtlassianJwtFetcherConfig atlassianJwtFetcherConfig = (AtlassianJwtFetcherConfig) fetcherConfig;
            initIfNeeded(atlassianJwtFetcherConfig);
            HttpGet get = new HttpGet(fetchKey);
            RequestConfig requestConfig = RequestConfig
                    .custom()
                    .setMaxRedirects(atlassianJwtFetcherConfig.getMaxRedirects())
                    .setRedirectsEnabled(atlassianJwtFetcherConfig.getMaxRedirects() > 0)
                    .build();
            get.setConfig(requestConfig);
            putAdditionalHeadersOnRequest(atlassianJwtFetcherConfig, get, fetchKey);
            return execute(get, atlassianJwtFetcherConfig, fetchMetadata, httpClient, true);
        } catch (TikaException | IOException | JOSEException | URISyntaxException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private void putAdditionalHeadersOnRequest(AtlassianJwtFetcherConfig atlassianJwtFetcherConfig, HttpGet httpGet, String url) 
            throws TikaException, JOSEException, URISyntaxException, NoSuchAlgorithmException {

        if (!StringUtils.isBlank(atlassianJwtFetcherConfig.getUserAgent())) {
            httpGet.setHeader(USER_AGENT, atlassianJwtFetcherConfig.getUserAgent());
        }
        if (atlassianJwtFetcherConfig.getHttpRequestHeaders() != null) {
            atlassianJwtFetcherConfig.getHttpRequestHeaders()
                    .forEach((header, values) -> {
                        for (String value : values) {
                            httpGet.addHeader(header, value);
                        }
                    });
        }
        if (jwtGenerator != null) {
            String jwt = jwtGenerator.generateJwt("GET", url);
            httpGet.setHeader("Authorization", "JWT " + jwt);
        } else {
            log.warn("No JWT generator available - authorization header not set");
        }
    }

    private InputStream execute(HttpGet get, AtlassianJwtFetcherConfig atlassianJwtFetcherConfig,
                                Map<String, Object> fetchMetadata, HttpClient client,
                                boolean retryOnBadLength) throws IOException {
        HttpClientContext context = HttpClientContext.create();
        HttpResponse response = null;
        final AtomicBoolean timeout = new AtomicBoolean(false);
        Timer timer = null;
        long overallTimeout = atlassianJwtFetcherConfig.getOverallTimeout() == null ? -1 : atlassianJwtFetcherConfig.getOverallTimeout();
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

            updateMetadata(get.getURI().toString(), response, context, fetchMetadata, atlassianJwtFetcherConfig);

            int code = response.getStatusLine().getStatusCode();
            log.info("Fetch id {} status code {}", get.getURI(), code);
            if (code < 200 || code > 299) {
                throw new IOException("bad status code: " + code + " :: " + responseToString(atlassianJwtFetcherConfig, response));
            }
            try (InputStream is = response.getEntity().getContent()) {
                return spool(atlassianJwtFetcherConfig, is, fetchMetadata);
            }
        } catch (ConnectionClosedException e) {
            if (retryOnBadLength && e.getMessage() != null && e.getMessage().contains("Premature end of Content-Length delimited message")) {
                log.warn("premature end of content-length delimited message; retrying with content compression disabled for {}", get.getURI());
                return execute(get, atlassianJwtFetcherConfig, fetchMetadata, noCompressHttpClient, false);
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
                EntityUtils.consumeQuietly(response.getEntity());
            }
            if (response instanceof CloseableHttpResponse) {
                ((CloseableHttpResponse) response).close();
            }
        }
    }

    private InputStream spool(AtlassianJwtFetcherConfig atlassianJwtFetcherConfig, InputStream content, Map<String, Object> fetchMetadata) throws IOException {
        long start = System.currentTimeMillis();
        TemporaryResources tmp = new TemporaryResources();
        Path tmpFile = tmp.createTempFile();
        if (atlassianJwtFetcherConfig.getMaxSpoolSize() < 0) {
            Files.copy(content, tmpFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            try (OutputStream os = Files.newOutputStream(tmpFile)) {
                long totalRead = IOUtils.copyLarge(content, os, 0, atlassianJwtFetcherConfig.getMaxSpoolSize());
                if (totalRead == atlassianJwtFetcherConfig.getMaxSpoolSize() && content.read() != -1) {
                    fetchMetadata.put(HTTP_FETCH_TRUNCATED.getName(), "true");
                }
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        log.debug("took {} ms to copy to local tmp file", elapsed);
        return TikaInputStream.get(tmpFile);
    }

    private void updateMetadata(String url, HttpResponse response, HttpClientContext context,
                               Map<String, Object> fetchMetadata,
                               AtlassianJwtFetcherConfig atlassianJwtFetcherConfig) {
        if (response == null) {
            return;
        }

        if (response.getStatusLine() != null) {
            fetchMetadata.put(HTTP_STATUS_CODE.getName(), response.getStatusLine().getStatusCode());
        }

        HttpEntity entity = response.getEntity();
        if (entity != null && entity.getContentEncoding() != null) {
            fetchMetadata.put(HTTP_CONTENT_ENCODING.getName(), entity.getContentEncoding().getValue());
        }
        if (entity != null && entity.getContentType() != null) {
            fetchMetadata.put(HTTP_CONTENT_TYPE.getName(), entity.getContentType().getValue());
        }

        if (atlassianJwtFetcherConfig.getHttpHeaders() != null) {
            for (String h : atlassianJwtFetcherConfig.getHttpHeaders()) {
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
                URI uri = uriList.get(uriList.size() - 1);
                if (uri != null) {
                    URL u = uri.toURL();
                    fetchMetadata.put(HTTP_TARGET_URL.getName(), u.toString());
                    fetchMetadata.put(TikaCoreProperties.RESOURCE_NAME_KEY, u.getFile());
                }
            } catch (MalformedURLException e) {
                // swallow
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

    private String responseToString(AtlassianJwtFetcherConfig atlassianJwtFetcherConfig, HttpResponse response) {
        if (response.getEntity() == null) {
            return "";
        }
        try (InputStream is = response.getEntity().getContent()) {
            UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().get();
            IOUtils.copyLarge(is, bos, 0, atlassianJwtFetcherConfig.getMaxErrMsgSize());
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

    public void initIfNeeded(AtlassianJwtFetcherConfig atlassianJwtFetcherConfig) throws TikaConfigException {
        if (isInit) {
            return;
        }

        log.info("AtlassianJwtFetcher initialization:");
        log.info("Shared Secret: {}", atlassianJwtFetcherConfig.getSharedSecret() != null ? "[PRESENT]" : "[MISSING]");
        log.info("Issuer: {}", atlassianJwtFetcherConfig.getIssuer());
        log.info("Subject: {}", atlassianJwtFetcherConfig.getSubject());
        log.info("JWT Expires In Seconds: {}", atlassianJwtFetcherConfig.getJwtExpiresInSeconds());

        checkInitialization(atlassianJwtFetcherConfig);

        if (atlassianJwtFetcherConfig.getSocketTimeout() != null) {
            httpClientFactory.setSocketTimeout(atlassianJwtFetcherConfig.getSocketTimeout());
        }
        if (atlassianJwtFetcherConfig.getRequestTimeout() != null) {
            httpClientFactory.setRequestTimeout(atlassianJwtFetcherConfig.getRequestTimeout());
        }
        if (atlassianJwtFetcherConfig.getConnectTimeout() != null) {
            httpClientFactory.setSocketTimeout(atlassianJwtFetcherConfig.getConnectTimeout());
        }
        if (atlassianJwtFetcherConfig.getMaxConnections() != null) {
            httpClientFactory.setMaxConnections(atlassianJwtFetcherConfig.getMaxConnections());
        }
        if (atlassianJwtFetcherConfig.getMaxConnectionsPerRoute() != null) {
            httpClientFactory.setMaxConnectionsPerRoute(atlassianJwtFetcherConfig.getMaxConnectionsPerRoute());
        }

        httpClient = httpClientFactory.build();
        HttpClientFactory cp = httpClientFactory.copy();
        cp.setDisableContentCompression(true);
        noCompressHttpClient = cp.build();

        if (!StringUtils.isBlank(atlassianJwtFetcherConfig.getSharedSecret()) && 
            !StringUtils.isBlank(atlassianJwtFetcherConfig.getIssuer())) {
            jwtGenerator = new AtlassianJwtGenerator(
                atlassianJwtFetcherConfig.getSharedSecret(),
                atlassianJwtFetcherConfig.getIssuer(),
                atlassianJwtFetcherConfig.getSubject(),
                atlassianJwtFetcherConfig.getJwtExpiresInSeconds()
            );
        } else {
            log.warn("JWT generator not created. missing required configuration");
        }

        isInit = true;
    }

    public void checkInitialization(AtlassianJwtFetcherConfig atlassianJwtFetcherConfig) throws TikaConfigException {
        if (StringUtils.isBlank(atlassianJwtFetcherConfig.getSharedSecret())) {
            throw new TikaConfigException("Atlassian JWT Fetcher requires a shared secret");
        }
        if (StringUtils.isBlank(atlassianJwtFetcherConfig.getIssuer())) {
            throw new TikaConfigException("Atlassian JWT Fetcher requires an issuer");
        }
    }
}
