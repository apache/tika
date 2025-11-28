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
package org.apache.tika.pipes.api;

import java.io.Serializable;
import java.util.Objects;

import org.apache.tika.config.ComponentConfigs;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;

/**
 * Container for fetch/emit/parse instructions.
 * <p>
 * The {@code componentConfigs} contains JSON configuration strings for
 * fetchers, emitters, and other pipeline components, keyed by component name.
 * This is separate from {@code ParseContext} which holds parser-specific
 * configuration objects (HandlerConfig, MetadataFilter, EmbeddedDocumentBytesHandler,
 * PassbackFilter, etc.) used during the actual parsing phase.
 */
public class FetchEmitTuple implements Serializable {

    public static final ON_PARSE_EXCEPTION DEFAULT_ON_PARSE_EXCEPTION = ON_PARSE_EXCEPTION.EMIT;

    public enum ON_PARSE_EXCEPTION {
        SKIP, EMIT
    }

    private final String id;
    private final FetchKey fetchKey;
    private EmitKey emitKey;
    private final Metadata metadata;
    private final ComponentConfigs componentConfigs;
    private final ON_PARSE_EXCEPTION onParseException;

    public FetchEmitTuple(String id, FetchKey fetchKey, EmitKey emitKey) {
        this(id, fetchKey, emitKey, new Metadata());
    }

    public FetchEmitTuple(String id, FetchKey fetchKey, EmitKey emitKey, Metadata metadata) {
        this(id, fetchKey, emitKey, metadata, ComponentConfigs.EMPTY);
    }

    public FetchEmitTuple(String id, FetchKey fetchKey, EmitKey emitKey, Metadata metadata, ComponentConfigs componentConfigs) {
        this(id, fetchKey, emitKey, metadata, componentConfigs, ON_PARSE_EXCEPTION.EMIT);
    }

    public FetchEmitTuple(String id, FetchKey fetchKey, EmitKey emitKey, Metadata metadata, ComponentConfigs componentConfigs,
                          ON_PARSE_EXCEPTION onParseException) {
        this.id = id;
        this.fetchKey = fetchKey;
        this.emitKey = emitKey;
        this.metadata = metadata;
        this.componentConfigs = componentConfigs == null ? ComponentConfigs.EMPTY : componentConfigs;
        this.onParseException = onParseException;
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

    public Metadata getMetadata() {
        return metadata;
    }

    public ComponentConfigs getComponentConfigs() {
        return componentConfigs;
    }

    public void setEmitKey(EmitKey emitKey) {
        this.emitKey = emitKey;
    }

    public ON_PARSE_EXCEPTION getOnParseException() {
        return onParseException;
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
        return Objects.equals(id, that.id) && Objects.equals(fetchKey, that.fetchKey) && Objects.equals(emitKey, that.emitKey)
                && Objects.equals(metadata, that.metadata) &&
                Objects.equals(componentConfigs, that.componentConfigs) && onParseException == that.onParseException;
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(fetchKey);
        result = 31 * result + Objects.hashCode(emitKey);
        result = 31 * result + Objects.hashCode(metadata);
        result = 31 * result + Objects.hashCode(componentConfigs);
        result = 31 * result + Objects.hashCode(onParseException);
        return result;
    }

    @Override
    public String toString() {
        return "FetchEmitTuple{" + "id='" + id + '\'' + ", fetchKey=" + fetchKey + ", emitKey=" + emitKey +
                ", metadata=" + metadata + ", componentConfigs=" + componentConfigs +
                ", onParseException=" + onParseException + '}';
    }
}
