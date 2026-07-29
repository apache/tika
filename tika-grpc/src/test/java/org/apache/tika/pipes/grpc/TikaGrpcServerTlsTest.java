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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the trust-cert-collection states that {@link TikaGrpcServer#start()} should refuse
 * to start on when {@code --client-auth-required} is set: omitted, nonexistent, unreadable,
 * and invalid/corrupt content, plus {@code --client-auth-required} without {@code --secure}.
 */
class TikaGrpcServerTlsTest {
    private static final File CERT_CHAIN = Paths.get("src", "test", "resources", "certs", "server1.pem").toFile();
    private static final File PRIVATE_KEY = Paths.get("src", "test", "resources", "certs", "server1.key").toFile();
    private static final File VALID_TRUST_COLLECTION = Paths.get("src", "test", "resources", "certs", "ca.pem").toFile();

    @Test
    void clientAuthRequiredWithoutSecureRefusesToStart() {
        TikaGrpcServer server = new TikaGrpcServer()
                .setSecure(false)
                .setClientAuthRequired(true);
        assertThrows(IllegalArgumentException.class, server::start);
    }

    @Test
    void clientAuthRequiredWithOmittedTrustCollectionRefusesToStart() {
        TikaGrpcServer server = new TikaGrpcServer()
                .setSecure(true)
                .setCertChain(CERT_CHAIN)
                .setPrivateKey(PRIVATE_KEY)
                .setClientAuthRequired(true);
        // trustCertCollection intentionally left unset
        assertThrows(IllegalArgumentException.class, server::start);
    }

    @Test
    void clientAuthRequiredWithNonexistentTrustCollectionRefusesToStart() {
        TikaGrpcServer server = new TikaGrpcServer()
                .setSecure(true)
                .setCertChain(CERT_CHAIN)
                .setPrivateKey(PRIVATE_KEY)
                .setTrustCertCollection(new File("does-not-exist-" + System.nanoTime() + ".pem"))
                .setClientAuthRequired(true);
        assertThrows(IllegalArgumentException.class, server::start);
    }

    @Test
    void clientAuthRequiredWithUnreadableTrustCollectionRefusesToStart(@TempDir Path tempDir) throws Exception {
        Path unreadable = tempDir.resolve("unreadable-ca.pem");
        Files.copy(VALID_TRUST_COLLECTION.toPath(), unreadable);
        boolean changed = unreadable.toFile().setReadable(false, false);
        Assumptions.assumeTrue(changed && !unreadable.toFile().canRead(),
                "cannot simulate an unreadable file as the current user (likely running as root)");

        TikaGrpcServer server = new TikaGrpcServer()
                .setSecure(true)
                .setCertChain(CERT_CHAIN)
                .setPrivateKey(PRIVATE_KEY)
                .setTrustCertCollection(unreadable.toFile())
                .setClientAuthRequired(true);
        assertThrows(IllegalArgumentException.class, server::start);
    }

    @Test
    void clientAuthRequiredWithCorruptTrustCollectionRefusesToStart(@TempDir Path tempDir) throws Exception {
        Path corrupt = tempDir.resolve("corrupt-ca.pem");
        Files.write(corrupt, "this is not a valid PEM certificate".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        TikaGrpcServer server = new TikaGrpcServer()
                .setSecure(true)
                .setCertChain(CERT_CHAIN)
                .setPrivateKey(PRIVATE_KEY)
                .setTrustCertCollection(corrupt.toFile())
                .setClientAuthRequired(true);
        // Content validation happens deeper in grpc's TLS credential building, so this
        // surfaces as a propagated exception rather than our explicit IllegalArgumentException.
        assertThrows(Exception.class, server::start);
    }
}
