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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.config.ConfigBase;
import org.apache.tika.exception.TikaConfigException;

/**
 * grpc-specific configuration, read from the {@code <grpc>} section of the tika-config xml (the
 * same {@link ConfigBase} mechanism {@code TikaServerConfig} uses for its {@code <server>}
 * section).
 * <p>
 * tika-grpc binds to all interfaces with no application-level authentication, so both capabilities
 * below are <em>denied by default</em>. An operator must explicitly opt in (after restricting
 * network access and/or enabling TLS - see the {@code --secure} family of options on
 * {@link TikaGrpcServer}). This mirrors the 4.x {@code TikaGrpcConfig} / {@code grpc} config
 * section.
 *
 * <pre>{@code
 * <properties>
 *   <grpc>
 *     <allowPerRequestConfig>true</allowPerRequestConfig>
 *     <allowComponentModifications>true</allowComponentModifications>
 *   </grpc>
 * </properties>
 * }</pre>
 */
public class TikaGrpcConfig extends ConfigBase {

    /**
     * When false, a {@code FetchAndParse} request that carries
     * {@code additional_fetch_config_json} is rejected with {@code PERMISSION_DENIED}.
     */
    private boolean allowPerRequestConfig = false;

    /**
     * When false, the runtime component-mutation RPCs ({@code SaveFetcher}, {@code DeleteFetcher})
     * are rejected with {@code PERMISSION_DENIED}. Fetchers declared in the {@code <fetchers>}
     * section of the tika-config are still loaded at startup regardless of this flag.
     */
    private boolean allowComponentModifications = false;

    /**
     * Loads the {@code <grpc>} section from the tika-config at the given path. If there is no
     * {@code <grpc>} section, the locked-down defaults (every capability denied) are returned.
     */
    public static TikaGrpcConfig load(Path tikaConfigPath) throws IOException, TikaConfigException {
        TikaGrpcConfig config = new TikaGrpcConfig();
        try (InputStream is = Files.newInputStream(tikaConfigPath)) {
            config.configure("grpc", is);
        }
        return config;
    }

    public boolean isAllowPerRequestConfig() {
        return allowPerRequestConfig;
    }

    public void setAllowPerRequestConfig(boolean allowPerRequestConfig) {
        this.allowPerRequestConfig = allowPerRequestConfig;
    }

    public boolean isAllowComponentModifications() {
        return allowComponentModifications;
    }

    public void setAllowComponentModifications(boolean allowComponentModifications) {
        this.allowComponentModifications = allowComponentModifications;
    }
}
