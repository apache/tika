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
package org.apache.tika.pipes.emitter.es;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.client.TikaClientException;
import org.apache.tika.config.ConfigValidator;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.AbstractEmitter;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Emitter that sends documents to an ES-compatible REST API.
 *
 * <p>This emitter does <b>not</b> depend on the ES Java client
 * (which changed to a non-ASL license). It uses plain HTTP via
 * Apache HttpClient to call the {@code _bulk} endpoint directly.
 *
 * <p>Supports:
 * <ul>
 *   <li>API key authentication ({@code Authorization: ApiKey &lt;base64&gt;})</li>
 *   <li>Basic authentication (username/password via httpClientConfig)</li>
 *   <li>OVERWRITE and UPSERT update strategies</li>
 *   <li>SEPARATE_DOCUMENTS and PARENT_CHILD attachment strategies</li>
 * </ul>
 */
public class ESEmitter extends AbstractEmitter {

    public static final String DEFAULT_EMBEDDED_FILE_FIELD_NAME = "embedded";
    private static final Logger LOG = LoggerFactory.getLogger(ESEmitter.class);

    private ESClient esClient;
    private final HttpClientFactory httpClientFactory;
    private final ESEmitterConfig config;

    public static ESEmitter build(ExtensionConfig pluginConfig)
            throws TikaConfigException, IOException {
        ESEmitterConfig config = ESEmitterConfig.load(pluginConfig.json());
        return new ESEmitter(pluginConfig, config);
    }

    public ESEmitter(ExtensionConfig pluginConfig, ESEmitterConfig config)
            throws IOException, TikaConfigException {
        super(pluginConfig);
        this.config = config;
        httpClientFactory = new HttpClientFactory();
        configure();
    }

    @Override
    public void emit(List<? extends EmitData> emitData) throws IOException {
        if (emitData == null || emitData.isEmpty()) {
            LOG.debug("metadataList is null or empty");
            return;
        }
        try {
            LOG.debug("about to emit {} docs", emitData.size());
            esClient.emitDocuments(emitData);
            LOG.info("successfully emitted {} docs", emitData.size());
        } catch (TikaClientException e) {
            LOG.warn("problem emitting docs", e);
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList,
                     ParseContext parseContext) throws IOException {
        if (metadataList == null || metadataList.isEmpty()) {
            LOG.debug("metadataList is null or empty");
            return;
        }
        try {
            LOG.debug("about to emit one doc with {} metadata entries",
                    metadataList.size());
            esClient.emitDocument(emitKey, metadataList);
            LOG.info("successfully emitted one doc");
        } catch (TikaClientException e) {
            LOG.warn("problem emitting doc", e);
            throw new IOException("failed to add document", e);
        }
    }

    private void configure() throws TikaConfigException {
        ConfigValidator.mustNotBeEmpty("esUrl", config.esUrl());
        ConfigValidator.mustNotBeEmpty("idField", config.idField());

        HttpClientConfig http = config.httpClientConfig();
        if (http != null) {
            httpClientFactory.setUserName(http.userName());
            httpClientFactory.setPassword(http.password());
            if (http.socketTimeout() > 0) {
                httpClientFactory.setSocketTimeout(http.socketTimeout());
            }
            if (http.connectionTimeout() > 0) {
                httpClientFactory.setConnectTimeout(http.connectionTimeout());
            }
            if (http.authScheme() != null) {
                httpClientFactory.setAuthScheme(http.authScheme());
            }
            if (http.proxyHost() != null) {
                httpClientFactory.setProxyHost(http.proxyHost());
                httpClientFactory.setProxyPort(http.proxyPort());
            }
            httpClientFactory.setVerifySsl(http.verifySsl());
        }

        esClient = new ESClient(config, httpClientFactory.build());
    }
}
