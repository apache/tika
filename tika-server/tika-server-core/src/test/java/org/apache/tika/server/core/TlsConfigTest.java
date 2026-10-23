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

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;

/**
 * A tlsConfig block means TLS. One that cannot work as written must refuse to load; it must
 * never fall back to plain HTTP or to the JVM's TLS defaults.
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
    public void clientAuthWantedWithoutTrustStoreRefuses() {
        TlsConfig c = keyStoreOnly();
        c.setClientAuthenticationWanted(true);
        assertTrue(refused(c).contains("clientAuthenticationWanted"));
    }

    @Test
    public void clientAuthWantedWithTrustStoreLoads() {
        TlsConfig c = keyStoreOnly();
        withTrustStore(c);
        c.setClientAuthenticationWanted(true);
        assertDoesNotThrow(c::checkInitialization);
    }

    @Test
    public void unsupportedIncludedProtocolRefuses() {
        TlsConfig c = keyStoreOnly();
        c.setIncludedProtocols(List.of("TLSv9.9"));
        assertTrue(refused(c).contains("TLSv9.9"));
    }

    @Test
    public void excludingEveryProtocolRefuses() {
        TlsConfig c = keyStoreOnly();
        c.setExcludedProtocols(List.of("TLSv1.2", "TLSv1.3"));
        assertTrue(refused(c).contains("no TLS protocol"));
    }

    @Test
    public void excludingAnUnsupportedProtocolIsFine() {
        TlsConfig c = keyStoreOnly();
        c.setExcludedProtocols(List.of("SSLv2Hello-from-another-jvm"));
        assertDoesNotThrow(c::checkInitialization);
    }

    @Test
    public void tls13OnlyLoads() {
        TlsConfig c = keyStoreOnly();
        c.setIncludedProtocols(List.of("TLSv1.3"));
        assertDoesNotThrow(c::checkInitialization);
    }

    @Test
    public void unmatchedIncludedCipherRefuses() {
        TlsConfig c = keyStoreOnly();
        c.setIncludedCipherSuites(List.of("TLS_AES_256_GCM_SHA384", "TLS_NOPE_WITH_NOPE"));
        assertTrue(refused(c).contains("TLS_NOPE_WITH_NOPE"));
    }

    @Test
    public void excludingEveryCipherRefuses() {
        TlsConfig c = keyStoreOnly();
        c.setExcludedCipherSuites(List.of(".*"));
        assertTrue(refused(c).contains("no cipher suite"));
    }

    @Test
    public void excludingAnAbsentCipherFamilyIsFine() {
        TlsConfig c = keyStoreOnly();
        c.setExcludedCipherSuites(List.of(".*RC4.*", ".*NULL.*"));
        assertDoesNotThrow(c::checkInitialization);
    }

    @Test
    public void cipherAllowlistAndDenylistLoad() {
        TlsConfig c = keyStoreOnly();
        c.setIncludedCipherSuites(List.of("TLS_AES_256_GCM_SHA384", "TLS_ECDHE_.*_GCM_.*"));
        c.setExcludedCipherSuites(List.of(".*CBC.*"));
        assertDoesNotThrow(c::checkInitialization);
    }

    @Test
    public void invalidCipherRegexRefuses() {
        TlsConfig c = keyStoreOnly();
        c.setIncludedCipherSuites(List.of("TLS_("));
        assertTrue(refused(c).contains("regular expression"));
    }
}
