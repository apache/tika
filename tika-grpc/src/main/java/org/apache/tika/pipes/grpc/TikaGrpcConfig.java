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
 *   <li>{@link #isAllowComponentModifications()} lets a client change which
 *   fetchers and iterators the server has at all.</li>
 * </ul>
 */
public class TikaGrpcConfig {

    private boolean allowPerRequestConfig = false;

    private boolean allowComponentModifications = false;

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
     * Whether clients may add, modify, or delete fetchers and pipes iterators at
     * runtime (the SaveFetcher, DeleteFetcher, SavePipesIterator and
     * DeletePipesIterator RPCs).
     * <p>
     * This is dangerous because it changes what the server can reach for all
     * subsequent requests and clients (for example, adding a fetcher that
     * escapes a configured base path or points at an internal host). Defaults
     * to {@code false}; when {@code false}, those RPCs are rejected.
     *
     * @return true if runtime component modifications are permitted
     */
    public boolean isAllowComponentModifications() {
        return allowComponentModifications;
    }

    public void setAllowComponentModifications(boolean allowComponentModifications) {
        this.allowComponentModifications = allowComponentModifications;
    }
}
