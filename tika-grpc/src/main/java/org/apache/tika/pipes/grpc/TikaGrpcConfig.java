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

    private boolean allowPerRequestConfig = false;

    private boolean allowComponentManagement = false;

    private boolean cancelRunningParse = false;

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
     * What a v2 call that the client cancels, or whose deadline expires, does to a parse
     * that is already running on a pipes worker. A call still waiting for a worker always
     * stops waiting at once, whatever this says.
     * <p>
     * {@code false} (the default): the running parse finishes, bounded by the pipes
     * timeouts, and its result is dropped. The worker stays up for the next request.
     * <p>
     * {@code true}: the parse is interrupted and the pipes client abandons the worker's
     * connection. That frees the worker sooner, but in the default per-client server mode
     * ({@code pipes.useSharedServer: false}) it also restarts the worker JVM, so clients
     * whose deadlines expire under load, or that cancel on purpose, cost a JVM start each.
     * The pipes client sees the interrupt between its waits on the worker, about once a
     * second while the worker sends heartbeats; a worker that has stopped responding
     * altogether still holds the parse until {@code pipes.socketTimeoutMillis}.
     *
     * @return true if a cancel interrupts a parse already running on a worker
     */
    public boolean isCancelRunningParse() {
        return cancelRunningParse;
    }

    public void setCancelRunningParse(boolean cancelRunningParse) {
        this.cancelRunningParse = cancelRunningParse;
    }
}
