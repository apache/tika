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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;

/**
 * A tlsConfig block means TLS. One that cannot work as written must refuse to load; it must
 * never fall back to plain HTTP.
 */
public class TlsConfigTest {

    private static String key(String file) {
        return Paths.get("src", "test", "resources", "ssl-keys", file).toString();
    }

    private static TlsConfig keyStoreOnly() {
        TlsConfig c = new TlsConfig();
        c.setKeyStoreType("PKCS12");
        c.setKeyStorePassword("tika-secret");
        c.setKeyStoreFile(key("tika-server-keystore.p12"));
        return c;
    }

    private static void withTrustStore(TlsConfig c) {
        c.setTrustStoreType("PKCS12");
        c.setTrustStorePassword("tika-secret");
        c.setTrustStoreFile(key("tika-server-truststore.p12"));
    }

    static final String CBC_SHA_SUITE = "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA";

    static SSLParameters jvmEnabled() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        return context.getDefaultSSLParameters();
    }

    private static String refused(TlsConfig c) {
        return assertThrows(TikaConfigException.class, c::checkInitialization).getMessage();
    }

    @Test
    public void emptyBlockRefuses() {
        assertTrue(refused(new TlsConfig()).contains("keyStoreType"));
    }

    @Test
    public void keyStoreOnlyLoads() {
        assertDoesNotThrow(keyStoreOnly()::checkInitialization);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void deprecatedActiveTrueIsIgnored() {
        TlsConfig c = keyStoreOnly();
        c.setActive(true);
        assertDoesNotThrow(c::checkInitialization);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void deprecatedActiveFalseRefuses() {
        TlsConfig c = keyStoreOnly();
        c.setActive(false);
        assertTrue(refused(c).contains("remove the block"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void clientAuthWantedRefuses() {
        TlsConfig c = keyStoreOnly();
        withTrustStore(c);
        c.setClientAuthenticationWanted(true);
        assertTrue(refused(c).contains("clientAuthenticationRequired"));
    }

    /** 4.1 configs and the old docs carry "clientAuthenticationWanted": false; it asks for nothing. */
    @Test
    @SuppressWarnings("deprecation")
    public void clientAuthWantedFalseIsIgnored() {
        TlsConfig c = keyStoreOnly();
        c.setClientAuthenticationWanted(false);
        assertDoesNotThrow(c::checkInitialization);
    }

    @Test
    public void clientAuthRequiredWithoutTrustStoreRefuses() {
        TlsConfig c = keyStoreOnly();
        c.setClientAuthenticationRequired(true);
        assertTrue(refused(c).contains("no trust store"));
    }

    @Test
    public void clientAuthRequiredWithTrustStoreLoads() {
        TlsConfig c = keyStoreOnly();
        withTrustStore(c);
        c.setClientAuthenticationRequired(true);
        assertDoesNotThrow(c::checkInitialization);
    }

    /** Any value, even an empty list: the key never worked as written through 4.1. */
    @Test
    @SuppressWarnings("deprecation")
    public void removedProtocolAndCipherKeysRefuse() {
        TlsConfig c = keyStoreOnly();
        c.setIncludedProtocols(List.of("TLSv1.3"));
        assertTrue(refused(c).contains("tlsConfig.includedProtocols -> -Djdk.tls.server.protocols"));

        c = keyStoreOnly();
        c.setExcludedProtocols(List.of());
        assertTrue(refused(c).contains("tlsConfig.excludedProtocols"));

        c = keyStoreOnly();
        c.setIncludedCipherSuites(null);
        c.setExcludedCipherSuites(List.of(".*CBC.*"));
        String msg = refused(c);
        assertTrue(msg.contains("tlsConfig.includedCipherSuites -> -Djdk.tls.server.cipherSuites"), msg);
        assertTrue(msg.contains("tlsConfig.excludedCipherSuites"), msg);
    }
}
