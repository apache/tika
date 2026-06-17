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

import org.apache.solr.client.solrj.jetty.HttpJettySolrClient;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.StringUtils;

public final class SolrClientHelper {

    private SolrClientHelper() {}

    /**
     * Applies authentication and proxy settings to a {@link HttpJettySolrClient.Builder}.
     * Only basic auth is supported; a non-basic auth scheme will throw {@link TikaConfigException}.
     * Proxy is only configured when both host and a positive port are present on the factory.
     */
    public static void applyAuthAndProxy(HttpJettySolrClient.Builder builder,
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
            builder.withProxyConfiguration(factory.getProxyHost(), factory.getProxyPort(), false, false);
        }
    }
}
