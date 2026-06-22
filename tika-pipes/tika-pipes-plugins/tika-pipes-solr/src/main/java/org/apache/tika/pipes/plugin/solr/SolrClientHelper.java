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

import java.util.List;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.LBSolrClient;
import org.apache.solr.client.solrj.jetty.HttpJettySolrClient;
import org.apache.solr.client.solrj.jetty.LBJettySolrClient;
import org.apache.solr.client.solrj.jetty.SSLConfig;
import org.apache.solr.common.util.IOUtils;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.StringUtils;

public final class SolrClientHelper {

    // SolrJ signature is withProxyConfiguration(host, port, isSocks4, isSecure). Tika only ever
    // configured a plain HTTP proxy (HttpClientFactory.addProxy used new HttpHost(host, port)).
    private static final boolean PROXY_IS_SOCKS4 = false;
    private static final boolean PROXY_IS_SECURE = false;

    private SolrClientHelper() {}

    /**
     * Applies authentication, proxy and SSL settings from {@code factory} to a
     * {@link HttpJettySolrClient.Builder}.
     * <ul>
     *   <li>Auth: when a username is configured, only the basic scheme is supported; any other
     *   scheme throws {@link TikaConfigException}. When no username is set, auth is skipped
     *   regardless of the configured scheme (matching the original Http2SolrClient code).</li>
     *   <li>Proxy: configured only when both a non-blank host and a positive port are present.</li>
     *   <li>SSL: verified by default. When {@code factory.isVerifySsl()} is false, all certificates
     *   are trusted and hostname verification is disabled (opt-in; preserves the pre-Jetty-12
     *   HttpClientFactory behaviour for self-signed Solr endpoints).</li>
     * </ul>
     */
    public static void applyClientSettings(HttpJettySolrClient.Builder builder,
                                           HttpClientFactory factory) throws TikaConfigException {
        if (!StringUtils.isBlank(factory.getUserName())) {
            if (!"basic".equalsIgnoreCase(factory.getAuthScheme())) {
                throw new TikaConfigException(
                        "Only 'basic' auth scheme is supported by HttpJettySolrClient; got: '"
                                + factory.getAuthScheme() + "'");
            }
            builder.withBasicAuthCredentials(factory.getUserName(), factory.getPassword());
        }
        if (!StringUtils.isBlank(factory.getProxyHost()) && factory.getProxyPort() > 0) {
            builder.withProxyConfiguration(factory.getProxyHost(), factory.getProxyPort(),
                    PROXY_IS_SOCKS4, PROXY_IS_SECURE);
        }
        if (!factory.isVerifySsl()) {
            builder.withSSLConfig(trustAllSslConfig());
        }
    }

    /**
     * An {@link SSLConfig} whose client context factory trusts all certificates and skips hostname
     * verification. Stock {@link SSLConfig} can only point at a keystore/truststore, so we override
     * {@link SSLConfig#createClientContextFactory()} to mirror the old accept-all behaviour.
     */
    private static SSLConfig trustAllSslConfig() {
        return new SSLConfig(true, false, null, null, null, null) {
            @Override
            public SslContextFactory.Client createClientContextFactory() {
                SslContextFactory.Client clientFactory = new SslContextFactory.Client();
                clientFactory.setTrustAll(true);
                clientFactory.setEndpointIdentificationAlgorithm(null);
                return clientFactory;
            }
        };
    }

    /**
     * Builds a direct-URL (load-balanced) Solr client from a configured builder and the list of
     * base Solr URLs.
     * <p>
     * The returned client closes the underlying {@link HttpJettySolrClient} when it is closed.
     * The stock {@link LBJettySolrClient} does not own its delegate client (see
     * {@link LBSolrClient#close()}), so without this the Jetty thread pool and selectors created
     * by the delegate would leak every time a caller closes the load-balanced client.
     */
    public static SolrClient buildLbClient(HttpJettySolrClient.Builder builder, List<String> solrUrls) {
        return buildLbClient(builder.build(), solrUrls);
    }

    // package-private overload that takes an already-built (or mocked) delegate, for testing
    static SolrClient buildLbClient(HttpJettySolrClient httpClient, List<String> solrUrls) {
        LBSolrClient.Endpoint[] endpoints = solrUrls.stream()
                .map(LBSolrClient.Endpoint::new)
                .toArray(LBSolrClient.Endpoint[]::new);
        return new ClosingLBJettySolrClient(new LBJettySolrClient.Builder(httpClient, endpoints),
                httpClient);
    }

    /**
     * An {@link LBJettySolrClient} that also closes the delegate {@link HttpJettySolrClient} it
     * was built with, which the stock client does not do.
     */
    private static final class ClosingLBJettySolrClient extends LBJettySolrClient {

        private final HttpJettySolrClient httpClient;

        private ClosingLBJettySolrClient(LBJettySolrClient.Builder builder,
                                         HttpJettySolrClient httpClient) {
            super(builder);
            this.httpClient = httpClient;
        }

        @Override
        public void close() {
            try {
                super.close();
            } finally {
                IOUtils.closeQuietly(httpClient);
            }
        }
    }
}
