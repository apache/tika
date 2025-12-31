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
import java.security.NoSuchAlgorithmException;
import java.util.List;
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
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaTimeoutException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.atlassianjwt.config.AtlassianJwtFetcherConfig;
import org.apache.tika.plugins.AbstractTikaExtension;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.utils.StringUtils;

public class AtlassianJwtFetcher extends AbstractTikaExtension implements Fetcher {

    private static final Logger LOG = LoggerFactory.getLogger(AtlassianJwtFetcher.class);

    public static AtlassianJwtFetcher build(ExtensionConfig pluginConfig)
            throws TikaConfigException, IOException {
        AtlassianJwtFetcherConfig config =
                AtlassianJwtFetcherConfig.load(pluginConfig.json());
        AtlassianJwtFetcher fetcher = new AtlassianJwtFetcher(pluginConfig, config);
        fetcher.initialize();
        return fetcher;
    }

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

    private AtlassianJwtFetcherConfig config;
    private HttpClient httpClient;
    private HttpClient noCompressHttpClient;
    private AtlassianJwtGenerator jwtGenerator;

    public AtlassianJwtFetcher(ExtensionConfig pluginConfig,
                              AtlassianJwtFetcherConfig config) {
        super(pluginConfig);
        this.config = config;
    }

    public void initialize() throws IOException, TikaConfigException {
        // Configure HTTP client factory
        if (config.getSocketTimeout() != null) {
            httpClientFactory.setSocketTimeout(config.getSocketTimeout());
        }
        if (config.getRequestTimeout() != null) {
            httpClientFactory.setRequestTimeout(config.getRequestTimeout());
        }
        if (config.getConnectTimeout() != null) {
            httpClientFactory.setConnectTimeout(config.getConnectTimeout());
        }
        if (config.getMaxConnections() != null) {
            httpClientFactory.setMaxConnections(config.getMaxConnections());
        }
        if (config.getMaxConnectionsPerRoute() != null) {
            httpClientFactory.setMaxConnectionsPerRoute(config.getMaxConnectionsPerRoute());
        }

        // Initialize HTTP client
        httpClient = httpClientFactory.build();
        HttpClientFactory cp = httpClientFactory.copy();
        cp.setDisableContentCompression(true);
        noCompressHttpClient = cp.build();

        // Initialize JWT generator if configured
        if (!StringUtils.isBlank(config.getSharedSecret())) {
            jwtGenerator = new AtlassianJwtGenerator(config.getSharedSecret(),
                    config.getIssuer(), config.getSubject(),
                    config.getJwtExpiresInSeconds());
        }
    }

    @Override
    public TikaInputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext)
            throws IOException, TikaException {
        HttpGet get = new HttpGet(fetchKey);
        RequestConfig requestConfig = RequestConfig.custom()
                .setMaxRedirects(config.getMaxRedirects())
                .setRedirectsEnabled(config.getMaxRedirects() > 0).build();
        get.setConfig(requestConfig);
        putAdditionalHeadersOnRequest(get, fetchKey);
        return execute(get, metadata, httpClient, true);
    }

    private void putAdditionalHeadersOnRequest(HttpGet httpGet, String url)
            throws TikaException {
        if (!StringUtils.isBlank(config.getUserAgent())) {
            httpGet.setHeader(USER_AGENT, config.getUserAgent());
        }
        if (config.getHttpRequestHeaders() != null) {
            config.getHttpRequestHeaders().forEach((header, values) -> {
                for (String value : values) {
                    httpGet.addHeader(header, value);
                }
            });
        }
        if (jwtGenerator != null) {
            try {
                String jwt = jwtGenerator.generateJwt("GET", url);
                httpGet.setHeader("Authorization", "JWT " + jwt);
            } catch (JOSEException | URISyntaxException | NoSuchAlgorithmException e) {
                throw new TikaException("Failed to generate JWT token", e);
            }
        } else {
            LOG.warn("No JWT generator available - authorization header not set");
        }
    }

    private TikaInputStream execute(HttpGet get, Metadata metadata, HttpClient client,
                                    boolean retryOnBadLength)
            throws IOException, TikaException {
        HttpClientContext context = HttpClientContext.create();
        HttpResponse response = null;
        final AtomicBoolean timeout = new AtomicBoolean(false);
        Timer timer = null;
        long overallTimeout = config.getOverallTimeout() == null ? -1 : config.getOverallTimeout();
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

            updateMetadata(get.getURI().toString(), response, context, metadata);

            int code = response.getStatusLine().getStatusCode();
            LOG.info("Fetch id {} status code {}", get.getURI(), code);
            if (code < 200 || code > 299) {
                throw new IOException("bad status code: " + code + " :: " + responseToString(response));
            }
            try (InputStream is = response.getEntity().getContent()) {
                return spool(is, metadata);
            }
        } catch (ConnectionClosedException e) {
            if (retryOnBadLength && e.getMessage() != null && e.getMessage().contains("Premature end of Content-Length delimited message")) {
                LOG.warn("premature end of content-length delimited message; retrying with content compression disabled for {}", get.getURI());
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
                EntityUtils.consumeQuietly(response.getEntity());
            }
            if (response instanceof CloseableHttpResponse) {
                ((CloseableHttpResponse) response).close();
            }
        }
    }

    private TikaInputStream spool(InputStream content, Metadata metadata) throws IOException {
        long start = System.currentTimeMillis();
        TemporaryResources tmp = new TemporaryResources();
        Path tmpFile = tmp.createTempFile(metadata);
        if (config.getMaxSpoolSize() < 0) {
            Files.copy(content, tmpFile);
        } else {
            try (OutputStream os = Files.newOutputStream(tmpFile)) {
                long totalRead = IOUtils.copyLarge(content, os, 0, config.getMaxSpoolSize());
                if (totalRead == config.getMaxSpoolSize() && content.read() != -1) {
                    metadata.set(HTTP_FETCH_TRUNCATED, true);
                }
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        LOG.debug("took {} ms to copy to local tmp file", elapsed);
        return TikaInputStream.get(tmpFile);
    }

    private void updateMetadata(String url, HttpResponse response, HttpClientContext context,
                               Metadata metadata) {
        if (response == null) {
            return;
        }

        if (response.getStatusLine() != null) {
            metadata.set(HTTP_STATUS_CODE, response.getStatusLine().getStatusCode());
        }

        HttpEntity entity = response.getEntity();
        if (entity != null && entity.getContentEncoding() != null) {
            metadata.set(HTTP_CONTENT_ENCODING, entity.getContentEncoding().getValue());
        }
        if (entity != null && entity.getContentType() != null) {
            metadata.set(HTTP_CONTENT_TYPE, entity.getContentType().getValue());
        }

        if (config.getHttpHeaders() != null) {
            for (String h : config.getHttpHeaders()) {
                Header[] headers = response.getHeaders(h);
                if (headers != null && headers.length > 0) {
                    for (Header header : headers) {
                        metadata.add(HTTP_HEADER_PREFIX + h, header.getValue());
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
                URI uri = uriList.get(uriList.size() - 1);
                if (uri != null) {
                    URL u = uri.toURL();
                    metadata.set(HTTP_TARGET_URL, u.toString());
                    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, u.getFile());
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
        try (InputStream is = response.getEntity().getContent()) {
            UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().get();
            IOUtils.copyLarge(is, bos, 0, config.getMaxErrMsgSize());
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
}
