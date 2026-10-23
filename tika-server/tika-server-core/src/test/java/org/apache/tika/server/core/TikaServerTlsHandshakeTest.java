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
package org.apache.tika.server.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.utils.ProcessUtils;

/**
 * Handshakes against a running server. Protocol and cipher policy is the JVM's
 * ({@code jdk.tls.server.*}); these pin that it reaches CXF and Jetty unchanged.
 */
public class TikaServerTlsHandshakeTest extends IntegrationTestBase {

    /** The replacement for the removed includedProtocols: the JVM's own property. */
    @Test
    public void jvmServerProtocolsPropertyIsHonoured() throws Exception {
        start("-Djdk.tls.server.protocols=TLSv1.3");
        assertEquals("TLSv1.3", handshake(new String[]{"TLSv1.3"}, null).getProtocol());
        assertThrows(SSLHandshakeException.class, () -> handshake(new String[]{"TLSv1.2"}, null));
    }

    /** The replacement for the removed includedCipherSuites. */
    @Test
    public void jvmServerCipherSuitesPropertyIsHonoured() throws Exception {
        start("-Djdk.tls.server.cipherSuites=TLS_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
        assertEquals("TLS_AES_128_GCM_SHA256", handshake(new String[]{"TLSv1.3"}, null).getCipherSuite());
        assertThrows(SSLHandshakeException.class,
                () -> handshake(new String[]{"TLSv1.3"}, new String[]{"TLS_AES_256_GCM_SHA384"}));
    }

    @Test
    public void defaultsRefuseWeakSuites() throws Exception {
        assumeTrue(Arrays.asList(TlsConfigTest.jvmEnabled().getCipherSuites()).contains(TlsConfigTest.CBC_SHA_SUITE));
        start(null);
        assertEquals("TLSv1.3", handshake(null, null).getProtocol());
        assertThrows(SSLHandshakeException.class,
                () -> handshake(new String[]{"TLSv1.2"}, new String[]{TlsConfigTest.CBC_SHA_SUITE}));
    }

    private void start(String jvmOptions) throws Exception {
        Path keys = Paths.get(getClass().getResource("/ssl-keys").toURI()).toAbsolutePath();
        String json = IOUtils.resourceToString("/configs/tika-config-server-tls-one-way-template.json", UTF_8)
                .replace("{SSL_KEYS}", keys.toString())
                .replace("\\", "/");
        Path config = Files.createTempFile(TEMP_WORKING_DIR, "tika-config-tls-", ".json");
        Files.writeString(config, json, UTF_8);
        startProcess(new String[]{"-config", ProcessUtils.escapeCommandLine(config.toAbsolutePath().toString())},
                jvmOptions == null ? Map.of() : Map.of("JAVA_TOOL_OPTIONS", jvmOptions));
        long deadline = System.currentTimeMillis() + MAX_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("server exited with " + process.exitValue());
            }
            try (Socket ignored = new Socket("localhost", integrationTestPort)) {
                return;
            } catch (ConnectException e) {
                Thread.sleep(200);
            }
        }
        throw new TimeoutException("server did not accept TLS connections within " + MAX_WAIT_MS + " ms");
    }

    /** Trusts any server certificate: these tests are about negotiation, not authentication. */
    private SSLSession handshake(String[] protocols, String[] cipherSuites) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        X509TrustManager trustAll = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        context.init(null, new TrustManager[]{trustAll}, null);
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket("localhost", integrationTestPort)) {
            socket.setSoTimeout(10000);
            if (protocols != null) {
                socket.setEnabledProtocols(protocols);
            }
            if (cipherSuites != null) {
                socket.setEnabledCipherSuites(cipherSuites);
            }
            socket.startHandshake();
            return socket.getSession();
        } catch (SSLHandshakeException e) {
            throw e;
        } catch (IOException e) {
            throw new SSLHandshakeException(e.toString());
        }
    }
}
