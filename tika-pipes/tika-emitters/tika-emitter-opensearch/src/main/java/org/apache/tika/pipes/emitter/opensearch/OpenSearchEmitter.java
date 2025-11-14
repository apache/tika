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
package org.apache.tika.pipes.emitter.opensearch;

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.client.TikaClientException;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.AbstractEmitter;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.pipes.core.emitter.TikaEmitterException;
import org.apache.tika.plugins.PluginConfig;


public class OpenSearchEmitter extends AbstractEmitter {




    public static String DEFAULT_EMBEDDED_FILE_FIELD_NAME = "embedded";
    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchEmitter.class);

    public static OpenSearchEmitter build(PluginConfig pluginConfig) throws TikaConfigException, IOException {
        OpenSearchEmitterConfig config = OpenSearchEmitterConfig.load(pluginConfig.jsonConfig());
        return new OpenSearchEmitter(pluginConfig, config);
    }

    private OpenSearchClient openSearchClient;
    private final HttpClientFactory httpClientFactory;
    private final OpenSearchEmitterConfig config;
    public OpenSearchEmitter(PluginConfig pluginConfig, OpenSearchEmitterConfig config) throws IOException, TikaConfigException {
        super(pluginConfig);
        this.config = config;
        httpClientFactory = new HttpClientFactory();
        configure();
    }

    @Override
    public void emit(List<? extends EmitData> emitData) throws IOException, TikaEmitterException {
        if (emitData == null || emitData.isEmpty()) {
            LOG.debug("metadataList is null or empty");
            return;
        }

        try {
            LOG.debug("about to emit {} docs", emitData.size());
            openSearchClient.emitDocuments(emitData);
            LOG.info("successfully emitted {} docs", emitData.size());
        } catch (TikaClientException e) {
            LOG.warn("problem emitting docs", e);
            throw new TikaEmitterException(e.getMessage());
        }
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext)
            throws IOException, TikaEmitterException {
        if (metadataList == null || metadataList.size() == 0) {
            LOG.debug("metadataList is null or empty");
            return;
        }
        try {
            LOG.warn("about to emit one doc {}", metadataList.size());
            openSearchClient.emitDocument(emitKey, metadataList);
            LOG.info("successfully emitted one doc");
        } catch (TikaClientException e) {
            LOG.warn("problem emitting doc", e);
            throw new TikaEmitterException("failed to add document", e);
        }
    }


    private void configure() throws TikaConfigException {
        mustNotBeEmpty("openSearchUrl", config.openSearchUrl());
        mustNotBeEmpty("idField", config.idField());
        HttpClientConfig http = config.httpClientConfig();
        httpClientFactory.setUserName(http.userName());
        httpClientFactory.setPassword(http.password());
        /*
            turn these back on as necessary
        httpClientFactory.setSocketTimeout(http.socketTimeout());
        httpClientFactory.setConnectTimeout(http.connectionTimeout());
        httpClientFactory.setAuthScheme(http.authScheme());
        httpClientFactory.setProxyHost(http.proxyHost());
        httpClientFactory.setProxyPort(http.proxyPort());

         */
        openSearchClient = new OpenSearchClient(config, httpClientFactory.build());
    }

}
