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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.client.TikaClientException;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.utils.StringUtils;


public class OpenSearchEmitter extends AbstractEmitter implements Initializable {


    public enum AttachmentStrategy {
        SEPARATE_DOCUMENTS, PARENT_CHILD,
        //anything else?
    }

    public enum UpdateStrategy {
        OVERWRITE, UPSERT
        //others?
    }

    public static String DEFAULT_EMBEDDED_FILE_FIELD_NAME = "embedded";
    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchEmitter.class);
    private AttachmentStrategy attachmentStrategy = AttachmentStrategy.PARENT_CHILD;

    private UpdateStrategy updateStrategy = UpdateStrategy.OVERWRITE;
    private String openSearchUrl = null;
    private String idField = "_id";
    private int commitWithin = 1000;
    private OpenSearchClient openSearchClient;
    private final HttpClientFactory httpClientFactory;
    private String embeddedFileFieldName = DEFAULT_EMBEDDED_FILE_FIELD_NAME;
    private String pipeline = null;

    public OpenSearchEmitter() throws TikaConfigException {
        httpClientFactory = new HttpClientFactory();
    }

    @Override
    public void emit(List<? extends EmitData> emitData) throws IOException, TikaEmitterException {
        if (emitData == null || emitData.size() == 0) {
            LOG.debug("metadataList is null or empty");
            return;
        }
        try {
            LOG.debug("about to emit {} docs", emitData.size());
            openSearchClient.emitDocuments(emitData, Optional.ofNullable(pipeline));
            LOG.info("successfully emitted {} docs", emitData.size());
        } catch (TikaClientException e) {
            LOG.warn("problem emitting docs", e);
            throw new TikaEmitterException(e.getMessage());
        }
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList)
            throws IOException, TikaEmitterException {
        if (metadataList == null || metadataList.size() == 0) {
            LOG.debug("metadataList is null or empty");
            return;
        }
        try {
            LOG.debug("about to emit one doc");
            openSearchClient.emitDocument(emitKey, metadataList, Optional.ofNullable(pipeline));
            LOG.info("successfully emitted one doc");
        } catch (TikaClientException e) {
            LOG.warn("problem emitting doc", e);
            throw new TikaEmitterException("failed to add document", e);
        }
    }


    /**
     * Options: SEPARATE_DOCUMENTS, PARENT_CHILD. Default is "SEPARATE_DOCUMENTS".
     * All embedded documents are treated as independent documents.
     * PARENT_CHILD requires a schema to be set up for the relationship type;
     * all embedded objects (no matter how deeply nested) will have a single
     * parent of the main container document.
     *
     * If you want to concatenate the content of embedded files and ignore
     * the metadata of embedded files, set
     * {@link org.apache.tika.pipes.HandlerConfig}'s parseMode to
     * {@link org.apache.tika.pipes.HandlerConfig.PARSE_MODE#CONCATENATE}
     * in your {@link org.apache.tika.pipes.FetchEmitTuple} or in the
     * &lt;parseMode&gt; element in your {@link org.apache.tika.pipes.pipesiterator.PipesIterator}
     * configuration.
     */
    @Field
    public void setAttachmentStrategy(String attachmentStrategy) {
        this.attachmentStrategy = AttachmentStrategy.valueOf(attachmentStrategy);
    }


    @Field
    public void setConnectionTimeout(int connectionTimeout) {
        httpClientFactory.setConnectTimeout(connectionTimeout);
    }

    @Field
    public void setSocketTimeout(int socketTimeout) {
        httpClientFactory.setSocketTimeout(socketTimeout);
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

    //this is the full url, including the collection, e.g. https://localhost:9200/my-collection
    @Field
    public void setOpenSearchUrl(String openSearchUrl) {
        this.openSearchUrl = openSearchUrl;
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

    public void setUpdateStrategy(UpdateStrategy updateStrategy) {
        this.updateStrategy = updateStrategy;
    }

    public void setUpdateStrategy(String strategy) throws TikaConfigException {
        switch (strategy.toLowerCase(Locale.US)) {
            case "overwrite" :
                setUpdateStrategy(UpdateStrategy.OVERWRITE);
                break;
            case "upsert" :
                setUpdateStrategy(UpdateStrategy.UPSERT);
                break;
            default :
                throw new TikaConfigException("'overwrite' and 'upsert' are the two options so " +
                        "far. I regret I don't understand: " + strategy);
        }
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
        if (StringUtils.isBlank(openSearchUrl)) {
            throw new TikaConfigException("Must specify an open search url!");
        } else {
            openSearchClient =
                    new OpenSearchClient(openSearchUrl,
                            httpClientFactory.build(), attachmentStrategy, updateStrategy,
                            embeddedFileFieldName);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        mustNotBeEmpty("openSearchUrl", this.openSearchUrl);
        mustNotBeEmpty("idField", this.idField);
    }

}
