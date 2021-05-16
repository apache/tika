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
import org.apache.solr.common.SolrInputDocument;
import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SolrEmitter extends AbstractEmitter implements Initializable {

    public enum AttachmentStrategy {
        SKIP,
        CONCATENATE_CONTENT,
        PARENT_CHILD,
        //anything else?
    }

    public enum UpdateStrategy {
        ADD,
        UPDATE_MUST_EXIST,
        UPDATE_MUST_NOT_EXIST,
    }

    private static final Logger LOG = LoggerFactory.getLogger(SolrEmitter.class);

    private AttachmentStrategy attachmentStrategy = AttachmentStrategy.PARENT_CHILD;
    private UpdateStrategy updateStrategy = UpdateStrategy.ADD;
    private String solrCollection;
    /**
     * You can specify solrUrls, or you can specify solrZkHosts and use use zookeeper to determine the solr server urls.
     */
    private List<String> solrUrls;
    private List<String> solrZkHosts;
    private String solrZkChroot;
    private String contentField = "content";
    private String idField = "id";
    private int commitWithin = 1000;
    private int connectionTimeout = 10000;
    private int socketTimeout = 60000;
    private final HttpClientFactory httpClientFactory;
    private SolrClient solrClient;

    public SolrEmitter() throws TikaConfigException {
        httpClientFactory = new HttpClientFactory();
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList) throws IOException,
            TikaEmitterException {
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
        if (attachmentStrategy == AttachmentStrategy.SKIP ||
                metadataList.size() == 1) {
            addMetadataToSolrInputDocument(metadataList.get(0), solrInputDocument, updateStrategy);
        } else if (attachmentStrategy == AttachmentStrategy.CONCATENATE_CONTENT) {
            //this only handles text for now, not xhtml
            StringBuilder sb = new StringBuilder();
            for (Metadata metadata : metadataList) {
                String content = metadata.get(getContentField());
                if (content != null) {
                    sb.append(content).append("\n");
                }
            }
            Metadata parent = metadataList.get(0);
            parent.set(getContentField(), sb.toString());
            addMetadataToSolrInputDocument(parent, solrInputDocument, updateStrategy);
        } else if (attachmentStrategy == AttachmentStrategy.PARENT_CHILD) {
            addMetadataToSolrInputDocument(metadataList.get(0), solrInputDocument, updateStrategy);
            for (int i = 1; i < metadataList.size(); i++) {
                SolrInputDocument childSolrInputDocument = new SolrInputDocument();
                Metadata m = metadataList.get(i);
                childSolrInputDocument.setField(idField, UUID.randomUUID().toString());
                addMetadataToSolrInputDocument(m, childSolrInputDocument, updateStrategy);
            }
        } else {
            throw new IllegalArgumentException("I don't yet support this attachment strategy: "
                    + attachmentStrategy);
        }
        docsToUpdate.add(solrInputDocument);
    }

    @Override
    public void emit(List<? extends EmitData> batch) throws IOException, TikaEmitterException {
        if (batch == null || batch.size() == 0) {
            LOG.warn("batch is null or empty");
            return;
        }
        List<SolrInputDocument> docsToUpdate = new ArrayList<>();
        for (EmitData d : batch) {
            addMetadataAsSolrInputDocuments(d.getEmitKey().getEmitKey(), d.getMetadataList(), docsToUpdate);
        }
        emitSolrBatch(docsToUpdate);
    }

    private void emitSolrBatch(List<SolrInputDocument> docsToUpdate) throws IOException, TikaEmitterException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Emitting solr doc batch: {}", docsToUpdate);
        }
        if (!docsToUpdate.isEmpty()) {
            try {
                UpdateRequest req = new UpdateRequest();
                req.add(docsToUpdate);
                req.setCommitWithin(commitWithin);
                req.setParam("failOnVersionConflicts", "false");
                req.process(solrClient, solrCollection);
            } catch (Exception e) {
                throw new TikaEmitterException("Could not add batch to solr", e);
            }
        }
    }

    private void addMetadataToSolrInputDocument(Metadata metadata, SolrInputDocument solrInputDocument,
                                                UpdateStrategy updateStrategy) {
        for (String n : metadata.names()) {
            String[] vals = metadata.getValues(n);
            if (vals.length == 0) {
                continue;
            } else if (vals.length == 1) {
                if (updateStrategy == UpdateStrategy.ADD) {
                    solrInputDocument.setField(n, vals[0]);
                } else {
                    solrInputDocument.setField(n, new HashMap<String, String>() {{
                        put("set", vals[0]);
                    }});
                }
            } else if (vals.length > 1) {
                if (updateStrategy == UpdateStrategy.ADD) {
                    solrInputDocument.setField(n, vals);
                } else {
                    solrInputDocument.setField(n, new HashMap<String, String[]>() {{
                        put("set", vals);
                    }});
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

    /**
     * This is the field _after_ metadata mappings have been applied
     * that contains the "content" for each metadata object.
     * <p>
     * This is the field that is used if {@link #attachmentStrategy}
     * is {@link AttachmentStrategy#CONCATENATE_CONTENT}.
     *
     * @param contentField
     */
    @Field
    public void setContentField(String contentField) {
        this.contentField = contentField;
    }

    public String getContentField() {
        return contentField;
    }

    @Field
    public void setCommitWithin(int commitWithin) {
        this.commitWithin = commitWithin;
    }

    public int getCommitWithin() {
        return commitWithin;
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

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        if (solrUrls == null || solrUrls.isEmpty()) {
            solrClient = new CloudSolrClient.Builder(solrZkHosts, Optional.ofNullable(solrZkChroot))
                    .withConnectionTimeout(connectionTimeout)
                    .withSocketTimeout(socketTimeout)
                    .withHttpClient(httpClientFactory.build())
                    .build();
        } else {
            solrClient = new LBHttpSolrClient.Builder()
                    .withConnectionTimeout(connectionTimeout)
                    .withSocketTimeout(socketTimeout)
                    .withHttpClient(httpClientFactory.build())
                    .withBaseSolrUrls(solrUrls.toArray(new String[] {})).build();
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {
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
}
