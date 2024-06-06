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
import org.apache.tika.parser.ParseContext;
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
    private final Metadata metadata;
    private final ParseContext parseContext;
    private final ON_PARSE_EXCEPTION onParseException;

    private EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig;

    public FetchEmitTuple(String id, FetchKey fetchKey, EmitKey emitKey) {
        this(id, fetchKey, emitKey, new Metadata());
    }
    public FetchEmitTuple(String id, FetchKey fetchKey, EmitKey emitKey, Metadata metadata) {
        this(id, fetchKey, emitKey, metadata, new ParseContext());
    }

    public FetchEmitTuple(String id, FetchKey fetchKey, EmitKey emitKey, Metadata metadata, ParseContext parseContext) {
        this(id, fetchKey, emitKey, metadata, parseContext, ON_PARSE_EXCEPTION.EMIT);
    }

    public FetchEmitTuple(String id, FetchKey fetchKey, EmitKey emitKey, Metadata metadata, ParseContext parseContext,
                          ON_PARSE_EXCEPTION onParseException) {
        this.id = id;
        this.fetchKey = fetchKey;
        this.emitKey = emitKey;
        this.metadata = metadata;
        this.parseContext = parseContext;
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

    public ParseContext getParseContext() {
        return parseContext;
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
                Objects.equals(parseContext, that.parseContext) && onParseException == that.onParseException &&
                Objects.equals(embeddedDocumentBytesConfig, that.embeddedDocumentBytesConfig);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(fetchKey);
        result = 31 * result + Objects.hashCode(emitKey);
        result = 31 * result + Objects.hashCode(metadata);
        result = 31 * result + Objects.hashCode(parseContext);
        result = 31 * result + Objects.hashCode(onParseException);
        result = 31 * result + Objects.hashCode(embeddedDocumentBytesConfig);
        return result;
    }

    @Override
    public String toString() {
        return "FetchEmitTuple{" + "id='" + id + '\'' + ", fetchKey=" + fetchKey + ", emitKey=" + emitKey +
                ", metadata=" + metadata + ", parseContext=" + parseContext +
                ", onParseException=" + onParseException + ", embeddedDocumentBytesConfig=" + embeddedDocumentBytesConfig + '}';
    }
}
