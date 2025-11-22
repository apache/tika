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
package org.apache.tika.pipes.emitter.solr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.LBHttpSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.AbstractEmitter;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.utils.StringUtils;

/**
 * Emitter to write parsed documents to Apache Solr.
 *
 * <p>Example JSON configuration:</p>
 * <pre>
 * {
 *   "emitters": {
 *     "solr-emitter": {
 *       "my-solr": {
 *         "solrCollection": "my-collection",
 *         "solrUrls": ["http://localhost:8983/solr"],
 *         "idField": "id",
 *         "commitWithin": 1000,
 *         "attachmentStrategy": "PARENT_CHILD",
 *         "updateStrategy": "ADD"
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
public class SolrEmitter extends AbstractEmitter {

    public static final String DEFAULT_EMBEDDED_FILE_FIELD_NAME = "embedded";
    private static final Logger LOG = LoggerFactory.getLogger(SolrEmitter.class);

    private final SolrEmitterConfig config;
    private final SolrClient solrClient;
    private final SolrEmitterConfig.AttachmentStrategy attachmentStrategy;
    private final SolrEmitterConfig.UpdateStrategy updateStrategy;

    public static SolrEmitter build(ExtensionConfig extensionConfig) throws TikaConfigException, IOException {
        SolrEmitterConfig config = SolrEmitterConfig.load(extensionConfig.jsonConfig());
        config.validate();
        SolrClient solrClient = buildSolrClient(config);
        return new SolrEmitter(extensionConfig, config, solrClient);
    }

    private SolrEmitter(ExtensionConfig extensionConfig, SolrEmitterConfig config, SolrClient solrClient) throws IOException {
        super(extensionConfig);
        this.config = config;
        this.solrClient = solrClient;
        this.attachmentStrategy = config.getAttachmentStrategyEnum();
        this.updateStrategy = config.getUpdateStrategyEnum();
    }

    private static SolrClient buildSolrClient(SolrEmitterConfig config) throws TikaConfigException {
        HttpClientFactory httpClientFactory = new HttpClientFactory();
        if (!StringUtils.isBlank(config.userName())) {
            httpClientFactory.setUserName(config.userName());
        }
        if (!StringUtils.isBlank(config.password())) {
            httpClientFactory.setPassword(config.password());
        }
        if (!StringUtils.isBlank(config.authScheme())) {
            httpClientFactory.setAuthScheme(config.authScheme());
        }
        if (!StringUtils.isBlank(config.proxyHost())) {
            httpClientFactory.setProxyHost(config.proxyHost());
        }
        if (config.proxyPort() != null && config.proxyPort() > 0) {
            httpClientFactory.setProxyPort(config.proxyPort());
        }

        if (config.solrUrls() == null || config.solrUrls().isEmpty()) {
            // Use ZooKeeper-based CloudSolrClient
            Http2SolrClient.Builder http2SolrClientBuilder = new Http2SolrClient.Builder();
            if (!StringUtils.isBlank(httpClientFactory.getUserName())) {
                http2SolrClientBuilder.withBasicAuthCredentials(httpClientFactory.getUserName(), httpClientFactory.getPassword());
            }
            http2SolrClientBuilder
                    .withRequestTimeout(httpClientFactory.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .withConnectionTimeout(config.getConnectionTimeoutOrDefault(), TimeUnit.MILLISECONDS);

            Http2SolrClient http2SolrClient = http2SolrClientBuilder.build();
            return new CloudSolrClient.Builder(config.solrZkHosts(), Optional.ofNullable(config.solrZkChroot()))
                    .withHttpClient(http2SolrClient)
                    .build();
        } else {
            // Use direct URL-based LBHttpSolrClient
            return new LBHttpSolrClient.Builder()
                    .withConnectionTimeout(config.getConnectionTimeoutOrDefault(), TimeUnit.MILLISECONDS)
                    .withSocketTimeout(config.getSocketTimeoutOrDefault(), TimeUnit.MILLISECONDS)
                    .withHttpClient(httpClientFactory.build())
                    .withBaseEndpoints(config.solrUrls().toArray(new String[]{}))
                    .build();
        }
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext) throws IOException {
        if (metadataList == null || metadataList.isEmpty()) {
            LOG.warn("metadataList is null or empty");
            return;
        }
        List<SolrInputDocument> docsToUpdate = new ArrayList<>();
        addMetadataAsSolrInputDocuments(emitKey, metadataList, docsToUpdate);
        emitSolrBatch(docsToUpdate);
    }

    @Override
    public void emit(List<? extends EmitData> batch) throws IOException {
        if (batch == null || batch.isEmpty()) {
            LOG.warn("batch is null or empty");
            return;
        }
        List<SolrInputDocument> docsToUpdate = new ArrayList<>();
        for (EmitData d : batch) {
            addMetadataAsSolrInputDocuments(d.getEmitKey(), d.getMetadataList(), docsToUpdate);
        }
        emitSolrBatch(docsToUpdate);
    }

    private void addMetadataAsSolrInputDocuments(String emitKey, List<Metadata> metadataList,
                                                 List<SolrInputDocument> docsToUpdate) throws IOException {
        String idField = config.getIdFieldOrDefault();
        SolrInputDocument solrInputDocument = new SolrInputDocument();
        solrInputDocument.setField(idField, emitKey);

        if (updateStrategy == SolrEmitterConfig.UpdateStrategy.UPDATE_MUST_EXIST) {
            solrInputDocument.setField("_version_", 1);
        } else if (updateStrategy == SolrEmitterConfig.UpdateStrategy.UPDATE_MUST_NOT_EXIST) {
            solrInputDocument.setField("_version_", -1);
        }

        if (metadataList.size() == 1) {
            addMetadataToSolrInputDocument(metadataList.get(0), solrInputDocument);
            docsToUpdate.add(solrInputDocument);
        } else if (attachmentStrategy == SolrEmitterConfig.AttachmentStrategy.PARENT_CHILD) {
            addMetadataToSolrInputDocument(metadataList.get(0), solrInputDocument);
            List<SolrInputDocument> children = new ArrayList<>();
            for (int i = 1; i < metadataList.size(); i++) {
                SolrInputDocument childSolrInputDocument = new SolrInputDocument();
                Metadata m = metadataList.get(i);
                childSolrInputDocument.setField(idField, emitKey + "-" + UUID.randomUUID());
                addMetadataToSolrInputDocument(m, childSolrInputDocument);
                children.add(childSolrInputDocument);
            }
            solrInputDocument.setField(config.getEmbeddedFileFieldNameOrDefault(), children);
            docsToUpdate.add(solrInputDocument);
        } else if (attachmentStrategy == SolrEmitterConfig.AttachmentStrategy.SEPARATE_DOCUMENTS) {
            addMetadataToSolrInputDocument(metadataList.get(0), solrInputDocument);
            docsToUpdate.add(solrInputDocument);
            for (int i = 1; i < metadataList.size(); i++) {
                SolrInputDocument childSolrInputDocument = new SolrInputDocument();
                Metadata m = metadataList.get(i);
                childSolrInputDocument.setField(idField,
                        solrInputDocument.get(idField).getValue() + "-" + UUID.randomUUID());
                addMetadataToSolrInputDocument(m, childSolrInputDocument);
                docsToUpdate.add(childSolrInputDocument);
            }
        } else {
            throw new IOException("Unsupported attachment strategy: " + attachmentStrategy);
        }
    }

    private void emitSolrBatch(List<SolrInputDocument> docsToUpdate) throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Emitting solr doc batch: {}", docsToUpdate);
        }
        if (!docsToUpdate.isEmpty()) {
            try {
                UpdateRequest req = new UpdateRequest();
                req.add(docsToUpdate);
                req.setCommitWithin(config.getCommitWithinOrDefault());
                req.setParam("failOnVersionConflicts", "false");
                UpdateResponse updateResponse = req.process(solrClient, config.solrCollection());
                LOG.debug("update response: {}", updateResponse);
                if (updateResponse.getStatus() != 0) {
                    throw new IOException("Bad status: " + updateResponse);
                }
            } catch (Exception e) {
                throw new IOException("Could not add batch to solr", e);
            }
        }
    }

    private void addMetadataToSolrInputDocument(Metadata metadata, SolrInputDocument solrInputDocument) {
        for (String n : metadata.names()) {
            String[] vals = metadata.getValues(n);
            if (vals.length == 0) {
                continue;
            } else if (vals.length == 1) {
                if (updateStrategy == SolrEmitterConfig.UpdateStrategy.ADD) {
                    solrInputDocument.setField(n, vals[0]);
                } else {
                    solrInputDocument.setField(n,
                            java.util.Collections.singletonMap("set", vals[0]));
                }
            } else {
                if (updateStrategy == SolrEmitterConfig.UpdateStrategy.ADD) {
                    solrInputDocument.setField(n, vals);
                } else {
                    solrInputDocument.setField(n,
                            java.util.Collections.singletonMap("set", vals));
                }
            }
        }
    }
}
