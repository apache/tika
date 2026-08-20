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
package org.apache.tika.pipes.core.fetcher;

import java.io.Serializable;
import java.util.Arrays;

import org.apache.tika.annotation.TikaComponent;

/**
 * Document bytes carried in the {@code ParseContext} instead of fetched from a source, for
 * callers that already hold the content and would otherwise have to spool it to disk just to
 * hand it to the forked worker.
 * <p>
 * Read by {@link BytesFetcher}, which the tuple selects with fetcher id
 * {@link BytesFetcher#FETCHER_ID}. On the IPC wire this travels as a dedicated raw-binary
 * field of the tuple, bypassing the parse-context config machinery (whose text-JSON round
 * trip would base64 it); it counts against {@code maxIpcPayloadBytes} like any other part
 * of the request.
 */
@TikaComponent(name = "inline-bytes", spi = false)
public class InlineBytes implements Serializable {

    private static final long serialVersionUID = 1L;

    private byte[] bytes;

    public InlineBytes() {
    }

    public InlineBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public int length() {
        return bytes == null ? 0 : bytes.length;
    }

    /**
     * Value equality on the payload. {@code Objects.equals} would compare array identity here,
     * which would silently make two tuples carrying identical content unequal.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return Arrays.equals(bytes, ((InlineBytes) o).bytes);
    }

    /**
     * Length only. {@code FetchEmitTuple.hashCode()} hashes its ParseContext, so hashing the
     * payload itself would walk megabytes on every map insert; unequal-hash-implies-unequal
     * still holds, and collisions fall through to {@link #equals}.
     */
    @Override
    public int hashCode() {
        return length();
    }

    /**
     * Length only -- {@code FetchEmitTuple.toString()} prints its ParseContext, and a debug log
     * of a multi-megabyte payload is a real operational hazard.
     */
    @Override
    public String toString() {
        return "InlineBytes{length=" + length() + "}";
    }
}
