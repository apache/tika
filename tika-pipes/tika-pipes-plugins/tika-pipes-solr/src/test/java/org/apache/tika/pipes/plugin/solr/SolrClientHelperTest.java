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
package org.apache.tika.pipes.plugin.solr;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.apache.solr.client.solrj.jetty.HttpJettySolrClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.exception.TikaConfigException;

class SolrClientHelperTest {

    private HttpJettySolrClient.Builder builder;
    private HttpClientFactory factory;

    @BeforeEach
    void setUp() {
        builder = mock(HttpJettySolrClient.Builder.class, Answers.RETURNS_SELF);
        factory = new HttpClientFactory();
    }

    // ── auth: basic credentials applied ─────────────────────────────────────

    @Test
    void basicAuthApplied_whenUsernameAndPasswordSet() throws TikaConfigException {
        factory.setUserName("alice");
        factory.setPassword("secret");

        SolrClientHelper.applyAuthAndProxy(builder, factory);

        verify(builder).withBasicAuthCredentials("alice", "secret");
    }

    @Test
    void basicAuthApplied_whenSchemeExplicitlyBasic() throws TikaConfigException {
        factory.setUserName("alice");
        factory.setPassword("secret");
        factory.setAuthScheme("basic");

        SolrClientHelper.applyAuthAndProxy(builder, factory);

        verify(builder).withBasicAuthCredentials("alice", "secret");
    }

    @Test
    void basicAuthApplied_whenSchemeCaseInsensitive() throws TikaConfigException {
        factory.setUserName("alice");
        factory.setPassword("secret");
        factory.setAuthScheme("BASIC");

        SolrClientHelper.applyAuthAndProxy(builder, factory);

        verify(builder).withBasicAuthCredentials("alice", "secret");
    }

    // ── auth: non-basic scheme rejected when credentials present ─────────────

    @Test
    void nonBasicSchemeThrows_whenUsernameSet() {
        factory.setUserName("alice");
        factory.setPassword("secret");
        factory.setAuthScheme("ntlm");

        assertThrows(TikaConfigException.class,
                () -> SolrClientHelper.applyAuthAndProxy(builder, factory));
    }

    @Test
    void nonBasicSchemeThrows_whenSchemeIsDigest() {
        factory.setUserName("alice");
        factory.setPassword("secret");
        factory.setAuthScheme("digest");

        assertThrows(TikaConfigException.class,
                () -> SolrClientHelper.applyAuthAndProxy(builder, factory));
    }

    // ── auth: no credentials when username is blank ──────────────────────────

    @Test
    void noAuthApplied_whenUsernameBlank() throws TikaConfigException {
        SolrClientHelper.applyAuthAndProxy(builder, factory);

        verify(builder, never()).withBasicAuthCredentials(any(), any());
    }

    @Test
    void noExceptionAndNoAuth_whenNonBasicSchemeButNoUsername() {
        // authScheme mismatch is only enforced when credentials are present;
        // without a username there is nothing to authenticate, matching the
        // behaviour of the original Http2SolrClient code on main.
        factory.setAuthScheme("ntlm");

        assertDoesNotThrow(() -> SolrClientHelper.applyAuthAndProxy(builder, factory));
    }

    // ── proxy: applied when host and positive port are set ──────────────────

    @Test
    void proxyApplied_whenHostAndPortSet() throws TikaConfigException {
        factory.setProxyHost("proxy.example.com");
        factory.setProxyPort(8080);

        SolrClientHelper.applyAuthAndProxy(builder, factory);

        verify(builder).withProxyConfiguration("proxy.example.com", 8080, false, false);
    }

    @Test
    void proxyNotApplied_whenHostBlank() throws TikaConfigException {
        factory.setProxyPort(8080);

        SolrClientHelper.applyAuthAndProxy(builder, factory);

        verify(builder, never()).withProxyConfiguration(any(), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    void proxyNotApplied_whenPortZero() throws TikaConfigException {
        factory.setProxyHost("proxy.example.com");
        // proxyPort defaults to 0 — do not set it

        SolrClientHelper.applyAuthAndProxy(builder, factory);

        verify(builder, never()).withProxyConfiguration(any(), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    void proxyNotApplied_whenPortNegative() throws TikaConfigException {
        factory.setProxyHost("proxy.example.com");
        factory.setProxyPort(-1);

        SolrClientHelper.applyAuthAndProxy(builder, factory);

        verify(builder, never()).withProxyConfiguration(any(), anyInt(), anyBoolean(), anyBoolean());
    }

    // ── combined: auth + proxy together ──────────────────────────────────────

    @Test
    void authAndProxyBothApplied_whenAllConfigured() throws TikaConfigException {
        factory.setUserName("alice");
        factory.setPassword("secret");
        factory.setProxyHost("proxy.example.com");
        factory.setProxyPort(3128);

        SolrClientHelper.applyAuthAndProxy(builder, factory);

        verify(builder).withBasicAuthCredentials("alice", "secret");
        verify(builder).withProxyConfiguration("proxy.example.com", 3128, false, false);
    }
}
