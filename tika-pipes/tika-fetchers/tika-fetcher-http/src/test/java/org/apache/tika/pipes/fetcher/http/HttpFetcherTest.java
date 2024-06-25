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
import java.util.Collections;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;
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
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.pipes.fetcher.http.config.AdditionalHttpHeaders;
import org.apache.tika.pipes.fetcher.http.jwt.JwtGenerator;

public class HttpFetcherTest extends TikaTest {

    private static final String TEST_URL = "wontbecalled";
    private static final String CONTENT = "request content";

    private HttpFetcher httpFetcher;

    @BeforeEach
    public void before() throws Exception {
        final HttpResponse mockResponse = buildMockResponse(HttpStatus.SC_OK,
                IOUtils.toInputStream(CONTENT, Charset.defaultCharset()));

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
        AdditionalHttpHeaders additionalHttpHeaders = new AdditionalHttpHeaders();
        additionalHttpHeaders
                .getHeaders()
                .put("nick1", "val1");
        additionalHttpHeaders
                .getHeaders()
                .put("nick2", "val2");
        parseContext.set(AdditionalHttpHeaders.class, additionalHttpHeaders);
        httpFetcher.fetch("http://localhost", metadata, parseContext);
        HttpGet httpGet = httpGetArgumentCaptor.getValue();
        Assertions.assertEquals("val1", httpGet.getHeaders("nick1")[0].getValue());
        Assertions.assertEquals("val2", httpGet.getHeaders("nick2")[0].getValue());
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
