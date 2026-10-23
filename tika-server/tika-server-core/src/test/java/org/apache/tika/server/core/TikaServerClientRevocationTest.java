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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.utils.ProcessUtils;

/**
 * 2-way TLS with client certificates issued by a CA, whose CRL this test serves. Fixtures and
 * how to regenerate them: {@code ssl-keys/revocation/generate.sh}.
 */
public class TikaServerClientRevocationTest extends IntegrationTestBase {

    // fixed: the fixtures' CRL distribution point names it
    private static final int CRL_PORT = 18181;

    private Path pki;
    private ServerSocket crlServer;
    private final AtomicInteger caCrlFetches = new AtomicInteger();
    private final AtomicInteger forgedCrlFetches = new AtomicInteger();

    @BeforeEach
    public void startCrlServer() throws Exception {
        pki = Paths.get(getClass().getResource("/ssl-keys/revocation").toURI()).toAbsolutePath();
        byte[] crl = Files.readAllBytes(pki.resolve("ca.crl"));
        crlServer = new ServerSocket();
        try {
            crlServer.bind(new InetSocketAddress("127.0.0.1", CRL_PORT));
        } catch (BindException e) {
            crlServer.close();
            abort("port " + CRL_PORT + ", which the fixtures' CRL distribution point names, is in use");
        }
        Thread t = new Thread(() -> serveCrls(crl), "crl-server");
        t.setDaemon(true);
        t.start();
    }

    @AfterEach
    public void stopCrlServer() throws IOException {
        if (crlServer != null) {
            crlServer.close();
        }
    }

    /** Minimal HTTP/1.0: GET /ca.crl answers the CRL, anything else 404. */
    private void serveCrls(byte[] crl) {
        while (!crlServer.isClosed()) {
            try (Socket s = crlServer.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), US_ASCII));
                String requestLine = String.valueOf(in.readLine());
                OutputStream out = s.getOutputStream();
                if (requestLine.startsWith("GET /ca.crl ")) {
                    caCrlFetches.incrementAndGet();
                    out.write(("HTTP/1.0 200 OK\r\nContent-Type: application/pkix-crl\r\nContent-Length: "
                            + crl.length + "\r\n\r\n").getBytes(US_ASCII));
                    out.write(crl);
                } else {
                    if (requestLine.startsWith("GET /forged.crl ")) {
                        forgedCrlFetches.incrementAndGet();
                    }
                    out.write("HTTP/1.0 404 Not Found\r\nContent-Length: 0\r\n\r\n".getBytes(US_ASCII));
                }
                out.flush();
            } catch (IOException e) {
                // closed by stopCrlServer, or a client hung up
            }
        }
    }

    @Test
    public void revocationIsCheckedByDefault() throws Exception {
        start(null);
        assertTrue(accepted("good.p12"), "a CA-issued certificate the CRL does not list");
        assertFalse(accepted("revoked.p12"), "a certificate the CRL revokes");
        assertFalse(accepted("no-cdp.p12"), "a certificate with no revocation source fails closed");
        assertTrue(caCrlFetches.get() > 0);
        assertFalse(accepted("forged.p12"));
        assertEquals(0, forgedCrlFetches.get(), "an unverified certificate must not make the server fetch its CRL");
    }

    @Test
    public void revocationCanBeTurnedOff() throws Exception {
        start("\"checkRevocation\": false");
        assertTrue(accepted("no-cdp.p12"));
        assertTrue(accepted("revoked.p12"), "with the check off, revocation is not consulted");
        assertFalse(accepted("forged.p12"));
        assertEquals(0, caCrlFetches.get() + forgedCrlFetches.get());
    }

    private void start(String extraTlsConfig) throws Exception {
        Path keys = Paths.get(getClass().getResource("/ssl-keys").toURI()).toAbsolutePath();
        String json = IOUtils.resourceToString("/configs/tika-config-server-tls-two-way-template.json", UTF_8)
                .replace("{SSL_KEYS}/tika-server-truststore.p12", "{SSL_KEYS}/revocation/ca-truststore.p12")
                .replace("{SSL_KEYS}", keys.toString())
                .replace("\\", "/");
        if (extraTlsConfig != null) {
            json = json.replace("\"tlsConfig\": {", "\"tlsConfig\": {" + extraTlsConfig + ",");
        }
        Path config = Files.createTempFile(TEMP_WORKING_DIR, "tika-config-tls-", ".json");
        Files.writeString(config, json, UTF_8);
        startProcess(new String[]{"-config", ProcessUtils.escapeCommandLine(config.toAbsolutePath().toString())});
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

    /** Whether the server answers an HTTP request over 2-way TLS with this client certificate. */
    private boolean accepted(String clientKeyStore) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(pki.resolve(clientKeyStore))) {
            ks.load(in, "tika-secret".toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "tika-secret".toCharArray());
        // trusts any server certificate: these tests are about the server checking the client
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
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), new TrustManager[]{trustAll}, null);
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket("localhost", integrationTestPort)) {
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            out.write("GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(US_ASCII));
            out.flush();
            // with TLS 1.3 the server's verdict on the client certificate arrives after the handshake
            String response = new String(socket.getInputStream().readAllBytes(), US_ASCII);
            return response.startsWith("HTTP/1.1 ");
        } catch (IOException e) {
            return false;
        }
    }
}
