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
package org.apache.tika.pipes.emitter.qdrant;

import java.io.IOException;
import java.util.List;

import org.apache.http.client.HttpClient;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.client.TikaClientException;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.AbstractEmitter;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.plugins.ExtensionConfig;

/** Writes the vectors in {@code tk:chunks} to a Qdrant collection. */
public class QdrantEmitter extends AbstractEmitter {

    private final QdrantClient client;

    public static QdrantEmitter build(ExtensionConfig pluginConfig)
            throws TikaConfigException, IOException {
        return new QdrantEmitter(pluginConfig, QdrantEmitterConfig.load(pluginConfig.json()));
    }

    private QdrantEmitter(ExtensionConfig pluginConfig, QdrantEmitterConfig config)
            throws IOException, TikaConfigException {
        super(pluginConfig);
        this.client = new QdrantClient(config, buildHttpClient(config.httpClientConfig()));
    }

    @Override
    public void emit(List<? extends EmitData> emitData) throws IOException {
        if (emitData == null || emitData.isEmpty()) {
            return;
        }
        try {
            client.emitDocuments(emitData);
        } catch (TikaClientException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext)
            throws IOException {
        try {
            client.emitDocument(emitKey, metadataList);
        } catch (TikaClientException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private static HttpClient buildHttpClient(HttpClientConfig http) throws TikaConfigException {
        HttpClientFactory factory = new HttpClientFactory();
        if (http != null) {
            factory.setUserName(http.userName());
            factory.setPassword(http.password());
            if (http.socketTimeoutMillis() > 0) {
                factory.setSocketTimeoutMillis(http.socketTimeoutMillis());
            }
            if (http.connectionTimeoutMillis() > 0) {
                factory.setConnectTimeoutMillis(http.connectionTimeoutMillis());
            }
            if (http.authScheme() != null) {
                factory.setAuthScheme(http.authScheme());
            }
            if (http.proxyHost() != null) {
                factory.setProxyHost(http.proxyHost());
                factory.setProxyPort(http.proxyPort());
            }
            factory.setVerifySsl(http.verifySslOrDefault());
        }
        return factory.build();
    }
}
