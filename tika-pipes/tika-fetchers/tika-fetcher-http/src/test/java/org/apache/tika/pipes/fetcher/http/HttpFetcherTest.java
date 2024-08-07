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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.apache.tika.TikaTest;
import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.pipes.fetcher.config.FetcherConfigContainer;
import org.apache.tika.pipes.fetcher.http.config.HttpFetcherConfig;
import org.apache.tika.pipes.fetcher.http.config.HttpHeaders;
import org.apache.tika.pipes.fetcher.http.jwt.JwtGenerator;

class HttpFetcherTest extends TikaTest {
    private static final String TEST_URL = "wontbecalled";
    private static final String CONTENT = "request content";

    private HttpFetcher httpFetcher;

    private HttpFetcherConfig httpFetcherConfig;

    @BeforeEach
    public void before() throws Exception {
        httpFetcherConfig = new HttpFetcherConfig();
        httpFetcherConfig.setHttpHeaders(new ArrayList<>());
        httpFetcherConfig.setUserAgent("Test app");
        httpFetcherConfig.setConnectTimeout(240_000);
        httpFetcherConfig.setRequestTimeout(240_000);
        httpFetcherConfig.setSocketTimeout(240_000);
        httpFetcherConfig.setMaxConnections(500);
        httpFetcherConfig.setMaxConnectionsPerRoute(20);
        httpFetcherConfig.setMaxRedirects(-1);
        httpFetcherConfig.setMaxErrMsgSize(500_000_000);
        httpFetcherConfig.setOverallTimeout(400_000L);
        httpFetcherConfig.setMaxSpoolSize(-1L);

        httpFetcher = new HttpFetcher();
        final HttpResponse mockResponse = buildMockResponse(HttpStatus.SC_OK, IOUtils.toInputStream(CONTENT, Charset.defaultCharset()));

        mockClientResponse(mockResponse);
    }

    @Test
    public void test2xxResponse() throws TikaException, IOException {
        final Metadata meta = new Metadata();
        meta.set(TikaCoreProperties.RESOURCE_NAME_KEY, "fileName");

        try (final InputStream ignored = httpFetcher.fetch(TEST_URL, meta, new ParseContext())) {
            // HTTP headers added into meta
            assertEquals("200", meta.get("http-header:status-code"));
            assertEquals(TEST_URL, meta.get("http-connection:target-url"));
            // Content size included in meta
            assertEquals("15", meta.get("Content-Length"));

            // Filename passed in should be preserved
            assertEquals("fileName", meta.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        }
    }

    @Test
    public void test4xxResponse() throws Exception {
        // Setup client to respond with 403
        mockClientResponse(buildMockResponse(HttpStatus.SC_FORBIDDEN, null));

        final Metadata meta = new Metadata();
        assertThrows(IOException.class, () -> httpFetcher.fetch(TEST_URL, meta, new ParseContext()));

        // Meta still populated
        assertEquals("403", meta.get("http-header:status-code"));
        assertEquals(TEST_URL, meta.get("http-connection:target-url"));
    }

    @Test
    public void testJwt() throws Exception {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);

        httpFetcher.jwtGenerator = Mockito.mock(JwtGenerator.class);

        final Metadata meta = new Metadata();
        meta.set(TikaCoreProperties.RESOURCE_NAME_KEY, "fileName");

        try (final InputStream ignored = httpFetcher.fetch(TEST_URL, meta, new ParseContext())) {
            // HTTP headers added into meta
            assertEquals("200", meta.get("http-header:status-code"));
            assertEquals(TEST_URL, meta.get("http-connection:target-url"));
            // Content size included in meta
            assertEquals("15", meta.get("Content-Length"));

            // Filename passed in should be preserved
            assertEquals("fileName", meta.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        }

        Mockito
                .verify(httpFetcher.jwtGenerator)
                .jwt();
    }

    @Test
    public void testHttpRequestHeaders() throws Exception {
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        httpFetcher.setHttpClient(httpClient);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        ArgumentCaptor<HttpGet> httpGetArgumentCaptor = ArgumentCaptor.forClass(HttpGet.class);

        when(httpClient.execute(httpGetArgumentCaptor.capture(), any(HttpContext.class))).thenReturn(response);
        when(response.getStatusLine()).thenReturn(new StatusLine() {
            @Override
            public ProtocolVersion getProtocolVersion() {
                return new HttpGet("http://localhost").getProtocolVersion();
            }

            @Override
            public int getStatusCode() {
                return 200;
            }

            @Override
            public String getReasonPhrase() {
                return null;
            }
        });

        when(response.getEntity()).thenReturn(new StringEntity("Hi"));

        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        FetcherConfigContainer fetcherConfigContainer = new FetcherConfigContainer();
        fetcherConfigContainer.setConfigClassName(HttpFetcherConfig.class.getName());
        HttpFetcherConfig additionalHttpFetcherConfig = new HttpFetcherConfig();
        additionalHttpFetcherConfig.setHttpRequestHeaders(new HttpHeaders());
        HashMap<String, Collection<String>> headersMap = new HashMap<>();
        headersMap.put("fromFetchRequestHeader1", List.of("fromFetchRequestValue1"));
        headersMap.put("fromFetchRequestHeader2", List.of("fromFetchRequestValue2", "fromFetchRequestValue3"));
        additionalHttpFetcherConfig.getHttpRequestHeaders().setMap(headersMap);
        fetcherConfigContainer.setJson(new ObjectMapper().writeValueAsString(additionalHttpFetcherConfig));
        parseContext.set(FetcherConfigContainer.class, fetcherConfigContainer);

        httpFetcher.getHttpFetcherConfig().setHttpRequestHeaders(new HttpHeaders());
        HashMap<String, Collection<String>> headersMapFromConfig = new HashMap<>();
        headersMapFromConfig.put("fromFetchConfig1", List.of("fromFetchConfigValue1"));
        headersMapFromConfig.put("fromFetchConfig2", List.of("fromFetchConfigValue2", "fromFetchConfigValue3"));
        httpFetcher.getHttpFetcherConfig().getHttpRequestHeaders().setMap(headersMapFromConfig);

        httpFetcher.fetch("http://localhost", metadata, parseContext);
        HttpGet httpGet = httpGetArgumentCaptor.getValue();
        Assertions.assertEquals("fromFetchRequestValue1", httpGet.getHeaders("fromFetchRequestHeader1")[0].getValue());
        List<String> fromFetchRequestHeader2s = Arrays.stream(httpGet.getHeaders("fromFetchRequestHeader2"))
                .map(Header::getValue)
                .sorted()
                .collect(Collectors.toList());
        Assertions.assertEquals(2, fromFetchRequestHeader2s.size());
        Assertions.assertEquals("fromFetchRequestValue2", fromFetchRequestHeader2s.get(0));
        Assertions.assertEquals("fromFetchRequestValue3", fromFetchRequestHeader2s.get(1));
        // also make sure the headers from the fetcher config level are specified - see src/test/resources/tika-config-http.xml
        Assertions.assertEquals("fromFetchConfigValue1", httpGet.getHeaders("fromFetchConfig1")[0].getValue());
        List<String> fromFetchConfig2s = Arrays.stream(httpGet.getHeaders("fromFetchConfig2"))
                                    .map(Header::getValue)
                                    .sorted()
                                    .collect(Collectors.toList());
        Assertions.assertEquals(2, fromFetchConfig2s.size());
        Assertions.assertEquals("fromFetchConfigValue2", fromFetchConfig2s.get(0));
        Assertions.assertEquals("fromFetchConfigValue3", fromFetchConfig2s.get(1));

        metadata.set(Property.externalText("httpRequestHeaders"), new String[] {" nick1 :   val1", "nick2:   val2"});
        httpFetcher.fetch("http://localhost", metadata, parseContext);
        httpGet = httpGetArgumentCaptor.getValue();
        Assertions.assertEquals("val1", httpGet.getHeaders("nick1")[0].getValue());
        Assertions.assertEquals("val2", httpGet.getHeaders("nick2")[0].getValue());
        // also make sure the headers from the fetcher config level are specified - see src/test/resources/tika-config-http.xml
        Assertions.assertEquals("headerValueFromFetcherConfig", httpGet.getHeaders("headerNameFromFetcherConfig")[0].getValue());
    }

    @Test
    @Disabled("requires network connectivity")
    public void testRedirect() throws Exception {
        String url = "https://t.co/cvfkWAEIxw?amp=1";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Metadata metadata = new Metadata();
        HttpFetcher httpFetcher = (HttpFetcher) getFetcherManager("tika-config-http.xml").getFetcher("http");
        try (InputStream is = httpFetcher.fetch(url, metadata, new ParseContext())) {
            IOUtils.copy(is, bos);
        }
        //debug(metadata);
    }

    @Test
    @Disabled("requires network connectivity")
    public void testRange() throws Exception {
        String url = "https://commoncrawl.s3.amazonaws.com/crawl-data/CC-MAIN-2020-45/segments/1603107869785.9/warc/CC-MAIN-20201020021700-20201020051700-00529.warc.gz";
        long start = 969596307;
        long end = start + 1408 - 1;
        Metadata metadata = new Metadata();
        HttpFetcher httpFetcher = (HttpFetcher) getFetcherManager("tika-config-http.xml").getFetcher("http");
        try (TemporaryResources tmp = new TemporaryResources()) {
            Path tmpPath = tmp.createTempFile(metadata);
            try (InputStream is = httpFetcher.fetch(url, start, end, metadata)) {
                Files.copy(new GZIPInputStream(is), tmpPath, StandardCopyOption.REPLACE_EXISTING);
            }
            assertEquals(2461, Files.size(tmpPath));
        }
    }

    FetcherManager getFetcherManager(String path) throws Exception {
        return FetcherManager.load(Paths.get(HttpFetcherTest.class
                .getResource("/" + path)
                .toURI()));
    }

    private void mockClientResponse(final HttpResponse response) throws Exception {
        httpFetcher = (HttpFetcher) getFetcherManager("tika-config-http.xml").getFetcher("http");

        final HttpClient httpClient = mock(HttpClient.class);
        final HttpClientFactory clientFactory = mock(HttpClientFactory.class);

        when(httpClient.execute(any(HttpUriRequest.class), any(HttpContext.class))).thenReturn(response);
        when(clientFactory.build()).thenReturn(httpClient);
        when(clientFactory.copy()).thenReturn(clientFactory);

        httpFetcher.setHttpClientFactory(clientFactory);
        httpFetcher.setHttpFetcherConfig(httpFetcherConfig);
        httpFetcher.initialize(Collections.emptyMap());
    }

    private static HttpResponse buildMockResponse(final int statusCode, final InputStream is) throws IOException {
        final HttpResponse response = mock(HttpResponse.class);
        final StatusLine status = mock(StatusLine.class);
        final HttpEntity entity = mock(HttpEntity.class);

        when(status.getStatusCode()).thenReturn(statusCode);
        when(entity.getContent()).thenReturn(is);
        when(response.getStatusLine()).thenReturn(status);
        when(response.getEntity()).thenReturn(entity);

        return response;
    }
}
