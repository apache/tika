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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.List;

import jakarta.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.configuration.jsse.TLSParameterJaxBUtils;
import org.apache.cxf.configuration.security.KeyManagersType;
import org.apache.cxf.configuration.security.KeyStoreType;
import org.apache.cxf.configuration.security.TrustManagersType;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.utils.ProcessUtils;

public class TikaServerIntegrationTest extends IntegrationTestBase {

    private static Path TLS_KEYS;

    @TempDir
    private static Path TLS_CONFIG;

    private static Path TIKA_TLS_ONE_WAY_CONFIG;
    private static Path TIKA_TLS_TWO_WAY_CONFIG;

    @BeforeAll
    public static void setUpSSL() throws Exception {
        TLS_KEYS = Paths.get(TikaServerIntegrationTest.class
                .getResource("/ssl-keys")
                .toURI());

        String json = IOUtils.resourceToString("/configs/tika-config-server-tls-two-way-template.json", UTF_8);
        json = json.replace("{SSL_KEYS}", TLS_KEYS
                .toAbsolutePath()
                .toString());
        json = json.replace("\\", "/");

        TIKA_TLS_TWO_WAY_CONFIG = Files.createTempFile(TLS_CONFIG, "tika-config-tls-", ".json");
        Files.write(TIKA_TLS_TWO_WAY_CONFIG, json.getBytes(UTF_8));

        json = IOUtils.resourceToString("/configs/tika-config-server-tls-one-way-template.json", UTF_8);
        json = json.replace("{SSL_KEYS}", TLS_KEYS
                .toAbsolutePath()
                .toString());

        json = json.replace("\\", "/");
        TIKA_TLS_ONE_WAY_CONFIG = Files.createTempFile(TLS_CONFIG, "tika-config-tls-", ".json");
        Files.write(TIKA_TLS_ONE_WAY_CONFIG, json.getBytes(UTF_8));

    }

    private String getSSL(String file) {
        try {
            return Paths
                    .get(TikaServerIntegrationTest.class
                            .getResource("/ssl-keys/" + file)
                            .toURI())
                    .toAbsolutePath()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    public void test1WayTLS() throws Exception {
        startProcess(new String[]{"-config", ProcessUtils.escapeCommandLine(TIKA_TLS_ONE_WAY_CONFIG
                .toAbsolutePath()
                .toString())});

        String httpsEndpoint = "https://localhost:" + INTEGRATION_TEST_PORT;
        WebClient webClient = WebClient.create(httpsEndpoint);
        configure1WayTLS(webClient);
        awaitServerStartup(webClient);
        assertRmetaOverTls(webClient);
        webClient.close();

        //now test no tls config
        try {
            WebClient.create(httpsEndpoint).get();
            fail("bad, bad, bad. this should have failed!");
        } catch (Exception e) {
            assertContains("javax.net.ssl.SSLHandshakeException", e.getMessage());
        }
    }

    @Test
    public void test2WayTLS() throws Exception {
        startProcess(new String[]{"-config", ProcessUtils.escapeCommandLine(TIKA_TLS_TWO_WAY_CONFIG
                .toAbsolutePath()
                .toString())});

        String httpsEndpoint = "https://localhost:" + INTEGRATION_TEST_PORT;
        WebClient webClient = WebClient.create(httpsEndpoint);
        configure2WayTLS(webClient);
        awaitServerStartup(webClient);
        assertRmetaOverTls(webClient);
        webClient.close();

        //now test that no tls config fails
        try {
            WebClient.create(httpsEndpoint).get();
            fail("bad, bad, bad. this should have failed!");
        } catch (Exception e) {
            assertContains("javax.net.ssl.SSLHandshakeException", e.getMessage());
        }

        //now test that 1 way fails
        webClient = WebClient.create(httpsEndpoint);
        configure1WayTLS(webClient);
        try {
            webClient.get();
            fail("bad, bad, bad. this should have failed!");
        } catch (Exception e) {
            //the messages vary too much between operating systems and
            //java versions to make a reliable assertion
        }
    }

    /** A TLS server forks too; a tlsConfig branch that leaves /rmeta unwired must fail here. */
    private void assertRmetaOverTls(WebClient webClient) throws Exception {
        Response response = webClient.path(RMETA_PATH).accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));
        assertEquals(200, response.getStatus());
        List<Metadata> metadataList = JsonMetadataList.fromJson(
                new InputStreamReader((InputStream) response.getEntity(), UTF_8));
        assertEquals(1, metadataList.size());
        assertEquals("Nikolai Lobachevsky", metadataList.get(0).get("author"));
        assertContains("hello world", metadataList.get(0).get("tk:content"));
    }

    private void configure2WayTLS(WebClient webClient) throws GeneralSecurityException, IOException {
        HTTPConduit conduit = WebClient
                .getConfig(webClient)
                .getHttpConduit();
        KeyStoreType keystore = new KeyStoreType();
        keystore.setType("PKCS12");
        keystore.setPassword("tika-secret");
        keystore.setFile(getSSL("tika-client-keystore.p12"));
        KeyManagersType kmt = new KeyManagersType();
        kmt.setKeyStore(keystore);
        kmt.setKeyPassword("tika-secret");
        TLSClientParameters parameters = new TLSClientParameters();
        parameters.setKeyManagers(TLSParameterJaxBUtils.getKeyManagers(kmt));

        KeyStoreType trustKeyStore = new KeyStoreType();
        trustKeyStore.setType("PKCS12");
        trustKeyStore.setPassword("tika-secret");
        trustKeyStore.setFile(getSSL("tika-client-truststore.p12"));

        TrustManagersType tmt = new TrustManagersType();
        tmt.setKeyStore(trustKeyStore);
        parameters.setTrustManagers(TLSParameterJaxBUtils.getTrustManagers(tmt, true));

        conduit.setTlsClientParameters(parameters);

    }

    private void configure1WayTLS(WebClient webClient) throws GeneralSecurityException, IOException {
        HTTPConduit conduit = WebClient
                .getConfig(webClient)
                .getHttpConduit();
        TLSClientParameters parameters = new TLSClientParameters();

        KeyStoreType trustKeyStore = new KeyStoreType();
        trustKeyStore.setType("PKCS12");
        trustKeyStore.setPassword("tika-secret");
        trustKeyStore.setFile(getSSL("tika-client-truststore.p12"));

        TrustManagersType tmt = new TrustManagersType();
        tmt.setKeyStore(trustKeyStore);
        parameters.setTrustManagers(TLSParameterJaxBUtils.getTrustManagers(tmt, true));
        conduit.setTlsClientParameters(parameters);
    }
}
