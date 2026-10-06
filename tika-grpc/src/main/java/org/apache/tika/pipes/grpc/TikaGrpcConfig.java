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
package org.apache.tika.pipes.grpc;

import java.io.IOException;

import org.apache.tika.config.loader.TikaJsonConfig;

/**
 * Configuration for security-sensitive tika-grpc features, loaded from the
 * {@code "grpc"} section of the tika-config JSON.
 * <p>
 * Both flags default to {@code false} so an out-of-the-box server is locked
 * down: clients may only fetch-and-parse using the fetchers and pipes iterators
 * the operator declared in the config file, using the server's own parse
 * configuration. The capabilities below are dangerous and must be opted into
 * explicitly:
 * <ul>
 *   <li>{@link #isAllowPerRequestConfig()} lets a client reconfigure any
 *   pipeline component for a single request.</li>
 *   <li>{@link #isAllowComponentManagement()} lets a client change which
 *   fetchers and iterators the server has, and read their stored configs.</li>
 * </ul>
 */
public class TikaGrpcConfig {

    /** The ParseBytes content limit when {@code parseBytesMaxContentBytes} is not set. */
    public static final long DEFAULT_PARSE_BYTES_MAX_CONTENT_BYTES = 64L * 1024 * 1024;

    private boolean allowPerRequestConfig = false;

    private boolean allowComponentManagement = false;

    private Integer maxInboundMessageBytes = null;

    private Long parseBytesMaxContentBytes = null;

    /**
     * Loads {@link TikaGrpcConfig} from the {@code "grpc"} section of the JSON
     * configuration, or returns a locked-down default instance (all flags
     * {@code false}) if no {@code "grpc"} section is present.
     *
     * @param tikaJsonConfig the JSON configuration to load from
     * @return the loaded config, or a default (locked-down) instance
     * @throws IOException if deserialization fails
     */
    public static TikaGrpcConfig load(TikaJsonConfig tikaJsonConfig) throws IOException {
        TikaGrpcConfig config = tikaJsonConfig.deserialize("grpc", TikaGrpcConfig.class);
        if (config == null) {
            config = new TikaGrpcConfig();
        }
        return config;
    }

    /**
     * Whether clients may attach per-request configuration to FetchAndParse
     * requests (the {@code additional_fetch_config_json} and
     * {@code parse_context_json} fields), overriding the server's defaults for
     * that single request.
     * <p>
     * This is dangerous because the supplied JSON can reconfigure <em>any</em>
     * pipeline component (fetcher, parser/handler, timeout limits, ...), not
     * just parse behavior. Defaults to {@code false}; when {@code false}, a
     * request carrying either field is rejected.
     *
     * @return true if per-request configuration is permitted
     */
    public boolean isAllowPerRequestConfig() {
        return allowPerRequestConfig;
    }

    public void setAllowPerRequestConfig(boolean allowPerRequestConfig) {
        this.allowPerRequestConfig = allowPerRequestConfig;
    }

    /**
     * Whether clients may manage fetchers and pipes iterators at runtime: add,
     * modify, or delete them (the SaveFetcher, DeleteFetcher, SavePipesIterator
     * and DeletePipesIterator RPCs), and read their stored configuration back
     * (the GetFetcher, ListFetchers and GetPipesIterator RPCs).
     * <p>
     * This is dangerous on two counts. Writes change what the server can reach
     * for all subsequent requests and clients (for example, adding a fetcher
     * that escapes a configured base path or points at an internal host). Reads
     * return stored component configs verbatim, which may include secrets
     * (passwords, access keys, tokens). Defaults to {@code false}; when
     * {@code false}, the mutating RPCs are rejected and the read RPCs return
     * only component identity (id and class), never the config.
     *
     * @return true if runtime component management is permitted
     */
    public boolean isAllowComponentManagement() {
        return allowComponentManagement;
    }

    public void setAllowComponentManagement(boolean allowComponentManagement) {
        this.allowComponentManagement = allowComponentManagement;
    }

    /**
     * The largest message the server accepts on any RPC, in bytes, passed to gRPC's
     * {@code maxInboundMessageSize}. When it is not set, gRPC's default of 4 MiB applies.
     * <p>
     * gRPC reads a whole unary request into memory before the handler runs, so each
     * request in flight takes at least this much heap.
     *
     * @return the configured limit in bytes, or {@code null} if not set
     */
    public Integer getMaxInboundMessageBytes() {
        return maxInboundMessageBytes;
    }

    /**
     * @param maxInboundMessageBytes positive limit in bytes, or {@code null} for gRPC's
     *                               default
     * @throws IllegalArgumentException if the value is present and not positive
     */
    public void setMaxInboundMessageBytes(Integer maxInboundMessageBytes) {
        if (maxInboundMessageBytes != null && maxInboundMessageBytes <= 0) {
            throw new IllegalArgumentException(
                    "maxInboundMessageBytes must be positive, got: " + maxInboundMessageBytes);
        }
        this.maxInboundMessageBytes = maxInboundMessageBytes;
    }

    /**
     * The largest {@code content} a ParseBytes request may carry, in bytes. gRPC applies
     * {@link #getMaxInboundMessageBytes()} to the whole request first, so a limit above
     * that one has no effect.
     *
     * @return the configured limit in bytes, or {@code null} if not set
     */
    public Long getParseBytesMaxContentBytes() {
        return parseBytesMaxContentBytes;
    }

    /**
     * @param parseBytesMaxContentBytes positive limit in bytes, or {@code null} to use
     *                                  {@link #DEFAULT_PARSE_BYTES_MAX_CONTENT_BYTES}
     * @throws IllegalArgumentException if the value is present and not positive
     */
    public void setParseBytesMaxContentBytes(Long parseBytesMaxContentBytes) {
        if (parseBytesMaxContentBytes != null && parseBytesMaxContentBytes <= 0) {
            throw new IllegalArgumentException(
                    "parseBytesMaxContentBytes must be positive, got: "
                            + parseBytesMaxContentBytes);
        }
        this.parseBytesMaxContentBytes = parseBytesMaxContentBytes;
    }

    /**
     * @return the configured {@link #getParseBytesMaxContentBytes()}, or
     *         {@link #DEFAULT_PARSE_BYTES_MAX_CONTENT_BYTES} when it is not set
     */
    public long effectiveParseBytesMaxContentBytes() {
        return parseBytesMaxContentBytes != null
                ? parseBytesMaxContentBytes
                : DEFAULT_PARSE_BYTES_MAX_CONTENT_BYTES;
    }
}
