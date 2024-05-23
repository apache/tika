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
package org.apache.tika.pipes;

import java.io.Serializable;
import java.util.Objects;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.pipes.fetcher.FetchKey;

public class FetchEmitTuple implements Serializable {

    public static final ON_PARSE_EXCEPTION DEFAULT_ON_PARSE_EXCEPTION = ON_PARSE_EXCEPTION.EMIT;

    public enum ON_PARSE_EXCEPTION {
        SKIP, EMIT
    }

    private final String id;
    private final FetchKey fetchKey;
    private EmitKey emitKey;
    private final Metadata userMetadata;
    private final ON_PARSE_EXCEPTION onParseException;
    private HandlerConfig handlerConfig;
    private final Metadata fetchRequestMetadata;

    private EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig;

    public FetchEmitTuple(String id, FetchKey fetchKey, EmitKey emitKey) {
        this(id, fetchKey, emitKey, new Metadata(), HandlerConfig.DEFAULT_HANDLER_CONFIG, DEFAULT_ON_PARSE_EXCEPTION);
    }

    public FetchEmitTuple(String id, FetchKey fetchKey, EmitKey emitKey, ON_PARSE_EXCEPTION onParseException) {
        this(id, fetchKey, emitKey, new Metadata(), HandlerConfig.DEFAULT_HANDLER_CONFIG, onParseException);
    }

    public FetchEmitTuple(String id, FetchKey fetchKey, EmitKey emitKey, Metadata userMetadata) {
        this(id, fetchKey, emitKey, userMetadata, HandlerConfig.DEFAULT_HANDLER_CONFIG, DEFAULT_ON_PARSE_EXCEPTION);
    }

    public FetchEmitTuple(String id, FetchKey fetchKey, EmitKey emitKey, Metadata userMetadata, HandlerConfig handlerConfig,
                          ON_PARSE_EXCEPTION onParseException) {
        this(id, fetchKey, emitKey, userMetadata, handlerConfig, onParseException, EmbeddedDocumentBytesConfig.SKIP);
    }


    public FetchEmitTuple(String id, FetchKey fetchKey, EmitKey emitKey, Metadata userMetadata, HandlerConfig handlerConfig,
                          ON_PARSE_EXCEPTION onParseException, EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig) {
        this(id, fetchKey, emitKey, userMetadata, handlerConfig, onParseException, embeddedDocumentBytesConfig, new Metadata());
    }

    public FetchEmitTuple(String id, FetchKey fetchKey, EmitKey emitKey, Metadata userMetadata, HandlerConfig handlerConfig,
                          ON_PARSE_EXCEPTION onParseException, EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig, Metadata fetchRequestMetadata) {
        this.id = id;
        this.fetchKey = fetchKey;
        this.emitKey = emitKey;
        this.userMetadata = userMetadata;
        this.handlerConfig = handlerConfig;
        this.onParseException = onParseException;
        this.embeddedDocumentBytesConfig = embeddedDocumentBytesConfig;
        this.fetchRequestMetadata = fetchRequestMetadata;
    }

    public String getId() {
        return id;
    }

    public FetchKey getFetchKey() {
        return fetchKey;
    }

    public EmitKey getEmitKey() {
        return emitKey;
    }

    public Metadata getUserMetadata() {
        return userMetadata;
    }

    public ON_PARSE_EXCEPTION getOnParseException() {
        return onParseException;
    }

    public void setEmitKey(EmitKey emitKey) {
        this.emitKey = emitKey;
    }

    public void setHandlerConfig(HandlerConfig handlerConfig) {
        this.handlerConfig = handlerConfig;
    }

    public HandlerConfig getHandlerConfig() {
        return handlerConfig == null ? HandlerConfig.DEFAULT_HANDLER_CONFIG : handlerConfig;
    }

    public EmbeddedDocumentBytesConfig getEmbeddedDocumentBytesConfig() {
        return embeddedDocumentBytesConfig;
    }

    public Metadata getFetchRequestMetadata() {
        return fetchRequestMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FetchEmitTuple that = (FetchEmitTuple) o;
        return Objects.equals(id, that.id) && Objects.equals(fetchKey, that.fetchKey) &&
                Objects.equals(emitKey, that.emitKey) &&
                Objects.equals(userMetadata, that.userMetadata) &&
                onParseException == that.onParseException &&
                Objects.equals(handlerConfig, that.handlerConfig) &&
                Objects.equals(fetchRequestMetadata, that.fetchRequestMetadata) &&
                Objects.equals(embeddedDocumentBytesConfig, that.embeddedDocumentBytesConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fetchKey, emitKey, userMetadata,
                onParseException, handlerConfig, fetchRequestMetadata, embeddedDocumentBytesConfig);
    }

    @Override
    public String toString() {
        return "FetchEmitTuple{" + "id='" + id + '\'' + ", fetchKey=" + fetchKey + ", emitKey=" +
                emitKey + ", userMetadata=" + userMetadata + ", onParseException=" + onParseException +
                ", handlerConfig=" + handlerConfig + ", fetchRequestMetadata=" + fetchRequestMetadata +
                ", embeddedDocumentBytesConfig=" + embeddedDocumentBytesConfig + '}';
    }
}
