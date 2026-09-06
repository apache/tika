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
package org.apache.tika.pipes.core.serialization;

import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.core.fetcher.InlineBytes;

/**
 * The NEW_REQUEST envelope on the parent-to-child IPC: the tuple, plus the optional inline
 * document payload beside it. Bytes are data, not tuple state -- a {@link FetchEmitTuple} and
 * its ParseContext are never serialized with content in them ({@code FetchEmitTupleSerializer}
 * refuses loudly if an {@link InlineBytes} slips through).
 * <p>
 * {@link #of} lifts the payload out of the caller's context on the parent;
 * {@link #applyTo} plants it into the worker's merged context on the child, where
 * {@link org.apache.tika.pipes.core.fetcher.BytesFetcher} reads it.
 */
public final class PipesRequest {

    /** Wire name of the payload field; not a FetchEmitTuple field. */
    static final String INLINE_BYTES = "inlineBytes";

    static final String TUPLE = "tuple";

    private final FetchEmitTuple tuple;
    private final byte[] inlineBytes;

    PipesRequest(FetchEmitTuple tuple, byte[] inlineBytes) {
        this.tuple = tuple;
        this.inlineBytes = inlineBytes;
    }

    /**
     * Wraps {@code t} for the wire, lifting any {@link InlineBytes} out of its ParseContext.
     * The caller's live context is never mutated; the stripped copy exists only for
     * serialization.
     */
    public static PipesRequest of(FetchEmitTuple t) {
        ParseContext ctx = t.getParseContext();
        InlineBytes inline = ctx == null ? null : ctx.get(InlineBytes.class);
        if (inline == null) {
            return new PipesRequest(t, null);
        }
        ParseContext copy = new ParseContext();
        copy.copyFrom(ctx);
        copy.set(InlineBytes.class, null);
        FetchEmitTuple stripped = new FetchEmitTuple(t.getId(), t.getFetchKey(), t.getEmitKey(),
                t.getMetadata(), copy, t.getOnParseException(), t.getPresetName());
        return new PipesRequest(stripped, inline.getBytes());
    }

    /** Plants the payload into the worker's context for BytesFetcher; no-op without one. */
    public void applyTo(ParseContext mergedContext) {
        if (inlineBytes != null) {
            mergedContext.set(InlineBytes.class, new InlineBytes(inlineBytes));
        }
    }

    public FetchEmitTuple getTuple() {
        return tuple;
    }

    public byte[] getInlineBytes() {
        return inlineBytes;
    }
}
