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
package org.apache.tika.grpc.adapter;

import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.Message;
import io.grpc.MethodDescriptor;

import org.apache.tika.mime.MediaType;

/**
 * Turns an external gRPC parser's typed reply into documents for tika-grpc's ParseBytes
 * (TIKA-4771). An adapter jar carries the parser's generated stubs and one implementation of
 * this interface, listed in
 * {@code META-INF/services/org.apache.tika.grpc.adapter.ExternalParserAdapter}; putting the
 * jar on tika-grpc's classpath and naming its {@link #producerId()} under
 * {@code grpc.externalParsers} is the whole install.
 * <p>
 * <b>What an adapter may change.</b> Its {@link #map} returns an {@link AdapterContribution},
 * and tika-grpc decides what of it to use: complete child documents (a WARC's records, say),
 * and keys under the adapter's own {@link #keyPrefix()} on the parent, added only where Tika
 * set none. Tika's own parent fields, status and children are never changed by an adapter.
 * <p>
 * <b>Startup.</b> tika-grpc reads {@link #producerId()}, {@link #supportedTypes()},
 * {@link #method()} and {@link #keyPrefix()} once, checks them (a unary method, at least one
 * type, a prefix no other configured adapter's prefix overlaps) and calls
 * {@link #validateOptions}; any problem stops the server.
 * <p>
 * <b>Trust.</b> Adapters are loaded with {@link java.util.ServiceLoader} into the tika-grpc
 * server's JVM, not into Tika's forked parse workers. An adapter is trusted code: one that
 * hangs, leaks or runs out of memory in {@link #map} does so in the server. tika-grpc bounds
 * what it accepts from an adapter (reply bytes, children, keys and value sizes), but not the
 * adapter's own work.
 * <p>
 * One instance serves every call, from several threads at once, so implementations must be
 * stateless or thread-safe.
 *
 * @param <ReqT>  the parser's request message
 * @param <RespT> the parser's reply message
 */
public interface ExternalParserAdapter<ReqT extends Message, RespT extends Message> {

    /**
     * The adapter's name: the {@code producerId} of its {@code grpc.externalParsers} entry and
     * of its {@code ExtensionResult} slot.
     */
    String producerId();

    /**
     * The media types, without parameters, of the documents this adapter is called for,
     * matched against the content type Tika detected.
     */
    Set<MediaType> supportedTypes();

    /** The unary method to call, from the jar's own generated stubs. */
    MethodDescriptor<ReqT, RespT> method();

    /**
     * The prefix of every key the adapter writes on the parent document, such as
     * {@code warc:}. It must end with {@code :} or {@code -}. Parent keys outside it are
     * dropped.
     */
    String keyPrefix();

    /**
     * Checks the {@code options} object of the adapter's config entry, once, when tika-grpc
     * starts. Throw {@link IllegalArgumentException} for an unknown key or a bad value: the
     * server then does not start, rather than failing every request. Accepts only an empty
     * object by default.
     *
     * @param options the entry's options; an empty object when not set
     */
    default void validateOptions(JsonNode options) {
        if (options.size() > 0) {
            throw new IllegalArgumentException("adapter '" + producerId()
                    + "' takes no options, got: " + options);
        }
    }

    /** Builds the parser's request for one document. */
    ReqT buildRequest(AdapterRequest request);

    /**
     * Turns the parser's reply into a contribution, built with
     * {@link AdapterRequest#contribution()}. If this throws, nothing of it is used and the
     * adapter's slot is {@code STATUS_FAILED}.
     */
    AdapterContribution map(RespT reply, AdapterRequest request);
}
