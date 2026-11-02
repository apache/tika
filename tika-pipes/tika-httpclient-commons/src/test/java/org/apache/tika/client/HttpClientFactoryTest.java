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
package org.apache.tika.client;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class HttpClientFactoryTest {

    private static final char[] PASSWORD = "changeit".toCharArray();

    private static SSLServerSocket server;
    private static Thread acceptor;

    @BeforeAll
    public static void startSelfSignedServer(@TempDir Path tmp) throws Exception {
        Path keystore = tmp.resolve("self-signed.p12");
        Process keytool = new ProcessBuilder(
                Paths.get(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "self", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=localhost", "-validity", "2", "-storetype", "PKCS12",
                "-keystore", keystore.toString(), "-storepass", new String(PASSWORD),
                "-keypass", new String(PASSWORD)).inheritIO().start();
        assertTrue(keytool.waitFor(60, TimeUnit.SECONDS) && keytool.exitValue() == 0,
                "keytool failed");

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = Files.newInputStream(keystore)) {
            ks.load(is, PASSWORD);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, PASSWORD);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        server = (SSLServerSocket) ctx.getServerSocketFactory()
                .createServerSocket(0, 10, InetAddress.getLoopbackAddress());
        acceptor = new Thread(() -> {
            while (!server.isClosed()) {
                try (SSLSocket s = (SSLSocket) server.accept()) {
                    s.startHandshake();
                    InputStream in = s.getInputStream();
                    // read the request head
                    int c;
                    int matched = 0;
                    while (matched < 4 && (c = in.read()) != -1) {
                        matched = (c == (matched % 2 == 0 ? '\r' : '\n')) ? matched + 1 : 0;
                    }
                    OutputStream out = s.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok")
                            .getBytes(US_ASCII));
                    out.flush();
                } catch (Exception e) {
                    // handshake refused by a verifying client, or the server closed
                }
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @AfterAll
    public static void stopServer() throws Exception {
        server.close();
        acceptor.join(5000);
    }

    @Test
    public void testDefaultDoesNotVerify() throws Exception {
        HttpClientFactory factory = new HttpClientFactory();
        assertFalse(factory.isVerifySsl());
        assertEquals("ok", get(factory.build()));
    }

    @Test
    public void testVerifySslRejectsUntrustedCertificate() throws Exception {
        HttpClientFactory factory = new HttpClientFactory();
        factory.setVerifySsl(true);
        assertThrows(SSLException.class, () -> get(factory.build()));
        assertThrows(SSLException.class, () -> get(factory.copy().build()));
    }

    private static String get(HttpClient client) throws Exception {
        HttpResponse response = client.execute(
                new HttpGet("https://localhost:" + server.getLocalPort() + "/"));
        return EntityUtils.toString(response.getEntity(), US_ASCII);
    }
}
