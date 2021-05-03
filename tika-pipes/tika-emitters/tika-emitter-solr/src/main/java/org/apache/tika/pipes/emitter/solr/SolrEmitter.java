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

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.client.HttpClientUtil;
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

public class SolrEmitter extends AbstractEmitter implements Initializable {

    private static final String ATTACHMENTS = "attachments";
    private static final String UPDATE_PATH = "/update";
    private static final Logger LOG = LoggerFactory.getLogger(SolrEmitter.class);
    //one day this will be allowed or can be configured?
    private final boolean gzipJson = false;
    private AttachmentStrategy attachmentStrategy = AttachmentStrategy.PARENT_CHILD;
    private String url;
    private String contentField = "content";
    private String idField = "id";
    private int commitWithin = 100;
    private HttpClientFactory httpClientFactory;
    private HttpClient httpClient;
    public SolrEmitter() throws TikaConfigException {
        httpClientFactory = new HttpClientFactory();
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList)
            throws IOException, TikaEmitterException {

        if (metadataList == null || metadataList.size() == 0) {
            LOG.warn("metadataList is null or empty");
            return;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Writer writer = gzipJson ? new BufferedWriter(
                new OutputStreamWriter(new GZIPOutputStream(bos), StandardCharsets.UTF_8)) :
                new BufferedWriter(new OutputStreamWriter(bos, StandardCharsets.UTF_8));
        try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
            jsonGenerator.writeStartArray();
            jsonify(jsonGenerator, emitKey, metadataList);
            jsonGenerator.writeEndArray();
        }
        LOG.debug("emitting json ({})", new String(bos.toByteArray(), StandardCharsets.UTF_8));
        try {
            HttpClientUtil
                    .postJson(httpClient, url + UPDATE_PATH +
                                    "?commitWithin=" + getCommitWithin(),
                            bos.toByteArray(), gzipJson);
        } catch (TikaClientException e) {
            throw new TikaEmitterException("can't post", e);
        }
    }

    @Override
    public void emit(List<? extends EmitData> batch) throws IOException, TikaEmitterException {
        if (batch == null || batch.size() == 0) {
            LOG.warn("batch is null or empty");
            return;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Writer writer = gzipJson ? new BufferedWriter(
                new OutputStreamWriter(new GZIPOutputStream(bos), StandardCharsets.UTF_8)) :
                new BufferedWriter(new OutputStreamWriter(bos, StandardCharsets.UTF_8));
        try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
            jsonGenerator.writeStartArray();
            for (EmitData d : batch) {
                jsonify(jsonGenerator, d.getEmitKey().getEmitKey(), d.getMetadataList());
            }
            jsonGenerator.writeEndArray();
        }
        LOG.debug("emitting json ({})", new String(bos.toByteArray(), StandardCharsets.UTF_8));
        try {
            HttpClientUtil
                    .postJson(httpClient, url + UPDATE_PATH +
                                    "?commitWithin=" + getCommitWithin(),
                            bos.toByteArray(), gzipJson);
        } catch (TikaClientException e) {
            throw new TikaEmitterException("can't post", e);
        }
    }

    private void jsonify(JsonGenerator jsonGenerator, String emitKey,
                         List<Metadata> metadataList)
            throws IOException {
        metadataList.get(0).set(idField, emitKey);
        if (attachmentStrategy == AttachmentStrategy.SKIP || metadataList.size() == 1) {
            jsonify(metadataList.get(0), jsonGenerator);
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
            jsonify(parent, jsonGenerator);
        } else if (attachmentStrategy == AttachmentStrategy.PARENT_CHILD) {
            jsonify(metadataList.get(0), jsonGenerator, false);
            jsonGenerator.writeArrayFieldStart(ATTACHMENTS);

            for (int i = 1; i < metadataList.size(); i++) {
                Metadata m = metadataList.get(i);
                m.set(idField, UUID.randomUUID().toString());
                jsonify(m, jsonGenerator);
            }
            jsonGenerator.writeEndArray();
            jsonGenerator.writeEndObject();
        } else {
            throw new IllegalArgumentException(
                    "I don't yet support this attachment strategy: " + attachmentStrategy);
        }
    }

    private void jsonify(Metadata metadata, JsonGenerator jsonGenerator, boolean writeEndObject)
            throws IOException {
        jsonGenerator.writeStartObject();
        for (String n : metadata.names()) {
            String[] vals = metadata.getValues(n);
            if (vals.length == 0) {
                continue;
            } else if (vals.length == 1) {
                jsonGenerator.writeStringField(n, vals[0]);
            } else if (vals.length > 1) {
                jsonGenerator.writeArrayFieldStart(n);
                for (String val : vals) {
                    jsonGenerator.writeString(val);
                }
                jsonGenerator.writeEndArray();
            }
        }
        if (writeEndObject) {
            jsonGenerator.writeEndObject();
        }
    }

    private void jsonify(Metadata metadata, JsonGenerator jsonGenerator) throws IOException {
        jsonify(metadata, jsonGenerator, true);
    }

    /**
     * Options: "skip", "concatenate-content", "parent-child". Default is "parent-child".
     * If set to "skip", this will index only the main file and ignore all info
     * in the attachments.  If set to "concatenate", this will concatenate the
     * content extracted from the attachments into the main document and
     * then index the main document with the concatenated content _and_ the
     * main document's metadata (metadata from attachments will be thrown away).
     * If set to "parent-child", this will index the attachments as children
     * of the parent document via Solr's parent-child relationship.
     *
     * @param attachmentStrategy
     */
    @Field
    public void setAttachmentStrategy(String attachmentStrategy) {
        if (attachmentStrategy.equals("skip")) {
            this.attachmentStrategy = AttachmentStrategy.SKIP;
        } else if (attachmentStrategy.equals("concatenate-content")) {
            this.attachmentStrategy = AttachmentStrategy.CONCATENATE_CONTENT;
        } else if (attachmentStrategy.equals("parent-child")) {
            this.attachmentStrategy = AttachmentStrategy.PARENT_CHILD;
        } else {
            throw new IllegalArgumentException("Expected 'skip', 'concatenate-content' or " +
                    "'parent-child'. I regret I do not recognize: " + attachmentStrategy);
        }
    }

    /**
     * Specify the url for Solr
     *
     * @param url
     */
    @Field
    public void setUrl(String url) {
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.url = url;
    }

    public String getContentField() {
        return contentField;
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

    //TODO -- add other httpclient configurations
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
        //TODO: build the client here?
        httpClient = httpClientFactory.build();
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {

    }

    enum AttachmentStrategy {
        SKIP, CONCATENATE_CONTENT, PARENT_CHILD,
        //anything else?
    }

}
