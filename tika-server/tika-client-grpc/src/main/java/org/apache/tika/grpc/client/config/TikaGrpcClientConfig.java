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
package org.apache.tika.grpc.client.config;

/**
 * Configuration class for TikaGrpcClient.
 *
 * This class holds all the configuration parameters needed to connect to a Tika gRPC server.
 * Use the builder pattern to create instances with custom settings.
 *
 * Example usage:
 * <pre>
 * TikaGrpcClientConfig config = TikaGrpcClientConfig.builder()
 *     .host("tika-server.example.com")
 *     .port(9090)
 *     .tlsEnabled(true)
 *     .maxInboundMessageSize(16 * 1024 * 1024) // 16MB
 *     .connectionTimeoutSeconds(30)
 *     .build();
 * </pre>
 */
public class TikaGrpcClientConfig {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 9090;
    private static final boolean DEFAULT_TLS_ENABLED = false;
    private static final int DEFAULT_MAX_INBOUND_MESSAGE_SIZE = 4 * 1024 * 1024; // 4MB
    private static final int DEFAULT_CONNECTION_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_KEEP_ALIVE_TIME_SECONDS = 30;
    private static final int DEFAULT_KEEP_ALIVE_TIMEOUT_SECONDS = 5;

    private final String host;
    private final int port;
    private final boolean tlsEnabled;
    private final int maxInboundMessageSize;
    private final int connectionTimeoutSeconds;
    private final int keepAliveTimeSeconds;
    private final int keepAliveTimeoutSeconds;

    private TikaGrpcClientConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.tlsEnabled = builder.tlsEnabled;
        this.maxInboundMessageSize = builder.maxInboundMessageSize;
        this.connectionTimeoutSeconds = builder.connectionTimeoutSeconds;
        this.keepAliveTimeSeconds = builder.keepAliveTimeSeconds;
        this.keepAliveTimeoutSeconds = builder.keepAliveTimeoutSeconds;
    }

    /**
     * Creates a default configuration (localhost:9090, no TLS).
     *
     * @return default configuration
     */
    public static TikaGrpcClientConfig createDefault() {
        return new Builder().build();
    }

    /**
     * Creates a new builder for TikaGrpcClientConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public int getMaxInboundMessageSize() {
        return maxInboundMessageSize;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public int getKeepAliveTimeSeconds() {
        return keepAliveTimeSeconds;
    }

    public int getKeepAliveTimeoutSeconds() {
        return keepAliveTimeoutSeconds;
    }

    @Override
    public String toString() {
        return "TikaGrpcClientConfig{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", tlsEnabled=" + tlsEnabled +
                ", maxInboundMessageSize=" + maxInboundMessageSize +
                ", connectionTimeoutSeconds=" + connectionTimeoutSeconds +
                ", keepAliveTimeSeconds=" + keepAliveTimeSeconds +
                ", keepAliveTimeoutSeconds=" + keepAliveTimeoutSeconds +
                '}';
    }

    /**
     * Builder for TikaGrpcClientConfig.
     */
    public static class Builder {
        private String host = DEFAULT_HOST;
        private int port = DEFAULT_PORT;
        private boolean tlsEnabled = DEFAULT_TLS_ENABLED;
        private int maxInboundMessageSize = DEFAULT_MAX_INBOUND_MESSAGE_SIZE;
        private int connectionTimeoutSeconds = DEFAULT_CONNECTION_TIMEOUT_SECONDS;
        private int keepAliveTimeSeconds = DEFAULT_KEEP_ALIVE_TIME_SECONDS;
        private int keepAliveTimeoutSeconds = DEFAULT_KEEP_ALIVE_TIMEOUT_SECONDS;

        private Builder() {}

        /**
         * Sets the host name or IP address of the Tika gRPC server.
         *
         * @param host the server host (default: "localhost")
         * @return this builder
         */
        public Builder host(String host) {
            if (host == null || host.trim().isEmpty()) {
                throw new IllegalArgumentException("Host cannot be null or empty");
            }
            this.host = host.trim();
            return this;
        }

        /**
         * Sets the port of the Tika gRPC server.
         *
         * @param port the server port (default: 9090)
         * @return this builder
         */
        public Builder port(int port) {
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            this.port = port;
            return this;
        }

        /**
         * Enables or disables TLS encryption.
         *
         * @param tlsEnabled whether to use TLS (default: false)
         * @return this builder
         */
        public Builder tlsEnabled(boolean tlsEnabled) {
            this.tlsEnabled = tlsEnabled;
            return this;
        }

        /**
         * Sets the maximum inbound message size.
         *
         * @param maxInboundMessageSize the maximum message size in bytes (default: 4MB)
         * @return this builder
         */
        public Builder maxInboundMessageSize(int maxInboundMessageSize) {
            if (maxInboundMessageSize <= 0) {
                throw new IllegalArgumentException("Max inbound message size must be positive");
            }
            this.maxInboundMessageSize = maxInboundMessageSize;
            return this;
        }

        /**
         * Sets the connection timeout.
         *
         * @param connectionTimeoutSeconds the connection timeout in seconds (default: 30)
         * @return this builder
         */
        public Builder connectionTimeoutSeconds(int connectionTimeoutSeconds) {
            if (connectionTimeoutSeconds <= 0) {
                throw new IllegalArgumentException("Connection timeout must be positive");
            }
            this.connectionTimeoutSeconds = connectionTimeoutSeconds;
            return this;
        }

        /**
         * Sets the keep-alive time.
         *
         * @param keepAliveTimeSeconds the keep-alive time in seconds (default: 30)
         * @return this builder
         */
        public Builder keepAliveTimeSeconds(int keepAliveTimeSeconds) {
            if (keepAliveTimeSeconds <= 0) {
                throw new IllegalArgumentException("Keep-alive time must be positive");
            }
            this.keepAliveTimeSeconds = keepAliveTimeSeconds;
            return this;
        }

        /**
         * Sets the keep-alive timeout.
         *
         * @param keepAliveTimeoutSeconds the keep-alive timeout in seconds (default: 5)
         * @return this builder
         */
        public Builder keepAliveTimeoutSeconds(int keepAliveTimeoutSeconds) {
            if (keepAliveTimeoutSeconds <= 0) {
                throw new IllegalArgumentException("Keep-alive timeout must be positive");
            }
            this.keepAliveTimeoutSeconds = keepAliveTimeoutSeconds;
            return this;
        }

        /**
         * Builds the configuration object.
         *
         * @return a new TikaGrpcClientConfig instance
         */
        public TikaGrpcClientConfig build() {
            return new TikaGrpcClientConfig(this);
        }
    }
}
