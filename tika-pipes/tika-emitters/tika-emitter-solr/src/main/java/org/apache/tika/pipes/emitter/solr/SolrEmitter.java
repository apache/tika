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

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.LBHttpSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.TikaEmitterException;


public class SolrEmitter extends AbstractEmitter implements Initializable {

    public static String DEFAULT_EMBEDDED_FILE_FIELD_NAME = "embedded";
    private static final Logger LOG = LoggerFactory.getLogger(SolrEmitter.class);
    private final HttpClientFactory httpClientFactory;
    private AttachmentStrategy attachmentStrategy = AttachmentStrategy.PARENT_CHILD;
    private UpdateStrategy updateStrategy = UpdateStrategy.ADD;
    private String solrCollection;
    /**
     * You can specify solrUrls, or you can specify solrZkHosts and use use zookeeper to determine the solr server urls.
     */
    private List<String> solrUrls;
    private List<String> solrZkHosts;
    private String solrZkChroot;
    private String idField = "id";
    private int commitWithin = 1000;
    private int connectionTimeout = 10000;
    private int socketTimeout = 60000;
    private SolrClient solrClient;
    private String embeddedFileFieldName = DEFAULT_EMBEDDED_FILE_FIELD_NAME;

    public SolrEmitter() throws TikaConfigException {
        httpClientFactory = new HttpClientFactory();
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext)
            throws IOException, TikaEmitterException {
        if (metadataList == null || metadataList.size() == 0) {
            LOG.warn("metadataList is null or empty");
            return;
        }
        List<SolrInputDocument> docsToUpdate = new ArrayList<>();
        addMetadataAsSolrInputDocuments(emitKey, metadataList, docsToUpdate);
        emitSolrBatch(docsToUpdate);
    }

    private void addMetadataAsSolrInputDocuments(String emitKey, List<Metadata> metadataList,
                                                 List<SolrInputDocument> docsToUpdate)
            throws IOException, TikaEmitterException {
        SolrInputDocument solrInputDocument = new SolrInputDocument();
        solrInputDocument.setField(idField, emitKey);
        if (updateStrategy == UpdateStrategy.UPDATE_MUST_EXIST) {
            solrInputDocument.setField("_version_", 1);
        } else if (updateStrategy == UpdateStrategy.UPDATE_MUST_NOT_EXIST) {
            solrInputDocument.setField("_version_", -1);
        }
        if (metadataList.size() == 1) {
            addMetadataToSolrInputDocument(metadataList.get(0), solrInputDocument, updateStrategy);
            docsToUpdate.add(solrInputDocument);
        } else if (attachmentStrategy == AttachmentStrategy.PARENT_CHILD) {
            addMetadataToSolrInputDocument(metadataList.get(0), solrInputDocument, updateStrategy);
            List<SolrInputDocument> children = new ArrayList<>();
            for (int i = 1; i < metadataList.size(); i++) {
                SolrInputDocument childSolrInputDocument = new SolrInputDocument();
                Metadata m = metadataList.get(i);
                childSolrInputDocument
                        .setField(idField, emitKey + "-" + UUID.randomUUID().toString());
                addMetadataToSolrInputDocument(m, childSolrInputDocument, updateStrategy);
                children.add(childSolrInputDocument);
            }
            solrInputDocument.setField(embeddedFileFieldName, children);
            docsToUpdate.add(solrInputDocument);
        } else if (attachmentStrategy == AttachmentStrategy.SEPARATE_DOCUMENTS) {
            addMetadataToSolrInputDocument(metadataList.get(0), solrInputDocument, updateStrategy);
            docsToUpdate.add(solrInputDocument);
            for (int i = 1; i < metadataList.size(); i++) {
                SolrInputDocument childSolrInputDocument = new SolrInputDocument();
                Metadata m = metadataList.get(i);
                childSolrInputDocument.setField(idField,
                        solrInputDocument.get(idField).getValue() + "-" + UUID.randomUUID().toString());
                addMetadataToSolrInputDocument(m, childSolrInputDocument, updateStrategy);
                docsToUpdate.add(childSolrInputDocument);
            }
        } else {
            throw new IllegalArgumentException(
                    "I don't yet support this attachment strategy: " + attachmentStrategy);
        }
    }

    @Override
    public void emit(List<? extends EmitData> batch) throws IOException, TikaEmitterException {
        if (batch == null || batch.size() == 0) {
            LOG.warn("batch is null or empty");
            return;
        }
        List<SolrInputDocument> docsToUpdate = new ArrayList<>();
        for (EmitData d : batch) {
            addMetadataAsSolrInputDocuments(d.getEmitKey().getEmitKey(), d.getMetadataList(),
                    docsToUpdate);
        }
        emitSolrBatch(docsToUpdate);
    }

    private void emitSolrBatch(List<SolrInputDocument> docsToUpdate)
            throws IOException, TikaEmitterException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Emitting solr doc batch: {}", docsToUpdate);
        }
        if (!docsToUpdate.isEmpty()) {
            try {
                UpdateRequest req = new UpdateRequest();
                req.add(docsToUpdate);
                req.setCommitWithin(commitWithin);
                req.setParam("failOnVersionConflicts", "false");
                UpdateResponse updateResponse = req.process(solrClient, solrCollection);
                LOG.debug("update response: " + updateResponse);
                if (updateResponse.getStatus() != 0) {
                    throw new TikaEmitterException("Bad status: " + updateResponse);
                }
            } catch (Exception e) {
                throw new TikaEmitterException("Could not add batch to solr", e);
            }
        }
    }

    private void addMetadataToSolrInputDocument(Metadata metadata,
                                                SolrInputDocument solrInputDocument,
                                                UpdateStrategy updateStrategy) {
        for (String n : metadata.names()) {
            String[] vals = metadata.getValues(n);
            if (vals.length == 0) {
                continue;
            } else if (vals.length == 1) {
                if (updateStrategy == UpdateStrategy.ADD) {
                    solrInputDocument.setField(n, vals[0]);
                } else {
                    solrInputDocument.setField(n, new HashMap<String, String>() {
                        {
                            put("set", vals[0]);
                        }
                    });
                }
            } else if (vals.length > 1) {
                if (updateStrategy == UpdateStrategy.ADD) {
                    solrInputDocument.setField(n, vals);
                } else {
                    solrInputDocument.setField(n, new HashMap<String, String[]>() {
                        {
                            put("set", vals);
                        }
                    });
                }
            }
        }
    }

    /**
     * Options: SKIP, CONCATENATE_CONTENT, PARENT_CHILD. Default is "PARENT_CHILD".
     * If set to "SKIP", this will index only the main file and ignore all info
     * in the attachments.  If set to "CONCATENATE_CONTENT", this will concatenate the
     * content extracted from the attachments into the main document and
     * then index the main document with the concatenated content _and_ the
     * main document's metadata (metadata from attachments will be thrown away).
     * If set to "PARENT_CHILD", this will index the attachments as children
     * of the parent document via Solr's parent-child relationship.
     */
    @Field
    public void setAttachmentStrategy(String attachmentStrategy) {
        this.attachmentStrategy = AttachmentStrategy.valueOf(attachmentStrategy);
    }

    @Field
    public void setUpdateStrategy(String updateStrategy) {
        this.updateStrategy = UpdateStrategy.valueOf(updateStrategy);
    }

    @Field
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    @Field
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public int getCommitWithin() {
        return commitWithin;
    }

    @Field
    public void setCommitWithin(int commitWithin) {
        this.commitWithin = commitWithin;
    }

    /**
     * Specify the field in the first Metadata that should be
     * used as the id field for the document.
     *
     * @param idField
     */
    @Field
    public void setIdField(String idField) {
        this.idField = idField;
    }

    @Field
    public void setSolrCollection(String solrCollection) {
        this.solrCollection = solrCollection;
    }

    @Field
    public void setSolrUrls(List<String> solrUrls) {
        this.solrUrls = solrUrls;
    }

    @Field
    public void setSolrZkHosts(List<String> solrZkHosts) {
        this.solrZkHosts = solrZkHosts;
    }

    @Field
    public void setSolrZkChroot(String solrZkChroot) {
        this.solrZkChroot = solrZkChroot;
    }

    //TODO -- add other httpclient configurations??
    @Field
    public void setUserName(String userName) {
        httpClientFactory.setUserName(userName);
    }

    @Field
    public void setPassword(String password) {
        httpClientFactory.setPassword(password);
    }

    @Field
    public void setAuthScheme(String authScheme) {
        httpClientFactory.setAuthScheme(authScheme);
    }

    @Field
    public void setProxyHost(String proxyHost) {
        httpClientFactory.setProxyHost(proxyHost);
    }

    @Field
    public void setProxyPort(int proxyPort) {
        httpClientFactory.setProxyPort(proxyPort);
    }

    /**
     * If using the {@link AttachmentStrategy#PARENT_CHILD}, this is the field name
     * used to store the child documents.  Note that we artificially flatten all embedded
     * documents, no matter how nested in the container document, into direct children
     * of the root document.
     *
     * @param embeddedFileFieldName
     */
    @Field
    public void setEmbeddedFileFieldName(String embeddedFileFieldName) {
        this.embeddedFileFieldName = embeddedFileFieldName;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        if (solrUrls == null || solrUrls.isEmpty()) {
            solrClient = new CloudSolrClient.Builder(solrZkHosts, Optional.ofNullable(solrZkChroot))
                    .withConnectionTimeout(connectionTimeout).withSocketTimeout(socketTimeout)
                    .withHttpClient(httpClientFactory.build()).build();
        } else {
            solrClient = new LBHttpSolrClient.Builder().withConnectionTimeout(connectionTimeout)
                    .withSocketTimeout(socketTimeout).withHttpClient(httpClientFactory.build())
                    .withBaseSolrUrls(solrUrls.toArray(new String[]{})).build();
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        mustNotBeEmpty("solrCollection", this.solrCollection);
        mustNotBeEmpty("urlFieldName", this.idField);
        if ((this.solrUrls == null || this.solrUrls.isEmpty()) &&
                (this.solrZkHosts == null || this.solrZkHosts.isEmpty())) {
            throw new IllegalArgumentException(
                    "expected either param solrUrls or param solrZkHosts, but neither was specified");
        }
        if (this.solrUrls != null && !this.solrUrls.isEmpty() && this.solrZkHosts != null &&
                !this.solrZkHosts.isEmpty()) {
            throw new IllegalArgumentException(
                    "expected either param solrUrls or param solrZkHosts, but both were specified");
        }
    }

    public enum AttachmentStrategy {
        SEPARATE_DOCUMENTS, PARENT_CHILD,
        //anything else?
    }

    public enum UpdateStrategy {
        ADD, UPDATE_MUST_EXIST, UPDATE_MUST_NOT_EXIST,
    }
}
