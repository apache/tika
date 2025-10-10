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
package org.apache.tika.grpc.client;

import org.apache.tika.grpc.client.config.TikaGrpcClientConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TikaGrpcClient configuration and basic functionality.
 */
public class TikaGrpcClientTest {

    @Test
    public void testDefaultConfig() {
        TikaGrpcClientConfig config = TikaGrpcClientConfig.createDefault();

        assertEquals("localhost", config.getHost());
        assertEquals(9090, config.getPort());
        assertFalse(config.isTlsEnabled());
        assertEquals(4 * 1024 * 1024, config.getMaxInboundMessageSize());
        assertEquals(30, config.getConnectionTimeoutSeconds());
    }

    @Test
    public void testCustomConfig() {
        TikaGrpcClientConfig config = TikaGrpcClientConfig.builder()
            .host("example.com")
            .port(8080)
            .tlsEnabled(true)
            .maxInboundMessageSize(8 * 1024 * 1024)
            .connectionTimeoutSeconds(60)
            .build();

        assertEquals("example.com", config.getHost());
        assertEquals(8080, config.getPort());
        assertTrue(config.isTlsEnabled());
        assertEquals(8 * 1024 * 1024, config.getMaxInboundMessageSize());
        assertEquals(60, config.getConnectionTimeoutSeconds());
    }

    @Test
    public void testConfigValidation() {
        // Test invalid host
        assertThrows(IllegalArgumentException.class, () -> {
            TikaGrpcClientConfig.builder().host(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            TikaGrpcClientConfig.builder().host("");
        });

        // Test invalid port
        assertThrows(IllegalArgumentException.class, () -> {
            TikaGrpcClientConfig.builder().port(0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            TikaGrpcClientConfig.builder().port(65536);
        });

        // Test invalid message size
        assertThrows(IllegalArgumentException.class, () -> {
            TikaGrpcClientConfig.builder().maxInboundMessageSize(-1);
        });

        // Test invalid timeout
        assertThrows(IllegalArgumentException.class, () -> {
            TikaGrpcClientConfig.builder().connectionTimeoutSeconds(-1);
        });
    }

    @Test
    public void testClientCreation() {
        // Test that client can be created (though it won't connect without a server)
        TikaGrpcClientConfig config = TikaGrpcClientConfig.createDefault();

        assertDoesNotThrow(() -> {
            try (TikaGrpcClient client = new TikaGrpcClient(config)) {
                assertNotNull(client);
                assertNotNull(client.getConfig());
                assertEquals("localhost", client.getConfig().getHost());
                assertEquals(9090, client.getConfig().getPort());
            }
        });
    }

    @Test
    public void testCreateDefaultClient() {
        assertDoesNotThrow(() -> {
            try (TikaGrpcClient client = TikaGrpcClient.createDefault()) {
                assertNotNull(client);
                assertEquals("localhost", client.getConfig().getHost());
                assertEquals(9090, client.getConfig().getPort());
            }
        });
    }
}
