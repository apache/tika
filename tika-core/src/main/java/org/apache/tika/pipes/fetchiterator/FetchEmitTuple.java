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
package org.apache.tika.pipes.fetchiterator;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;

public class FetchEmitTuple {

    public enum ON_PARSE_EXCEPTION {
        SKIP,
        EMIT
    }
    public static final ON_PARSE_EXCEPTION DEFAULT_ON_PARSE_EXCEPTION = ON_PARSE_EXCEPTION.EMIT;
    private final FetchKey fetchKey;
    private EmitKey emitKey;
    private final Metadata metadata;
    private final ON_PARSE_EXCEPTION onParseException;

    public FetchEmitTuple(FetchKey fetchKey, EmitKey emitKey, Metadata metadata) {
        this(fetchKey, emitKey, metadata, DEFAULT_ON_PARSE_EXCEPTION);
    }
    public FetchEmitTuple(FetchKey fetchKey, EmitKey emitKey, Metadata metadata,
                          ON_PARSE_EXCEPTION onParseException) {
        this.fetchKey = fetchKey;
        this.emitKey = emitKey;
        this.metadata = metadata;
        this.onParseException = onParseException;
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

    public ON_PARSE_EXCEPTION getOnParseException() {
        return onParseException;
    }

    public void setEmitKey(EmitKey emitKey) {
        this.emitKey = emitKey;
    }
    @Override
    public String toString() {
        return "FetchEmitTuple{" +
                "fetchKey=" + fetchKey +
                ", emitKey=" + emitKey +
                ", metadata=" + metadata +
                ", onParseException=" + onParseException +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FetchEmitTuple that = (FetchEmitTuple) o;

        if (fetchKey != null ? !fetchKey.equals(that.fetchKey) : that.fetchKey != null) return false;
        if (emitKey != null ? !emitKey.equals(that.emitKey) : that.emitKey != null) return false;
        if (metadata != null ? !metadata.equals(that.metadata) : that.metadata != null) return false;
        return onParseException == that.onParseException;
    }

    @Override
    public int hashCode() {
        int result = fetchKey != null ? fetchKey.hashCode() : 0;
        result = 31 * result + (emitKey != null ? emitKey.hashCode() : 0);
        result = 31 * result + (metadata != null ? metadata.hashCode() : 0);
        result = 31 * result + (onParseException != null ? onParseException.hashCode() : 0);
        return result;
    }
}
