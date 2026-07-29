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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;

import org.apache.cxf.configuration.jsse.TLSServerParameters;
import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;

/**
 * Exercises TikaServerProcess.getTlsParams() directly via reflection, independent of
 * TikaServerConfig.load(), to confirm its own TLS config checks hold on their own.
 */
class TikaServerProcessTlsGuardTest {

    private static TLSServerParameters invokeGetTlsParams(TlsConfig tlsConfig) throws Throwable {
        try {
            Method m = TikaServerProcess.class.getDeclaredMethod("getTlsParams", TlsConfig.class);
            m.setAccessible(true);
            return (TLSServerParameters) m.invoke(null, tlsConfig);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static TlsConfig validKeyStoreOnlyConfig() {
        TlsConfig tlsConfig = new TlsConfig();
        tlsConfig.setActive(true);
        tlsConfig.setKeyStoreType("PKCS12");
        tlsConfig.setKeyStorePassword("tika-secret");
        tlsConfig.setKeyStoreFile(Paths
                .get("src", "test", "resources", "ssl-keys", "tika-server-keystore.p12")
                .toString());
        return tlsConfig;
    }

    @Test
    void getTlsParamsRefusesClientAuthRequiredWithoutTrustStore() throws Throwable {
        TlsConfig tlsConfig = validKeyStoreOnlyConfig();
        tlsConfig.setClientAuthenticationRequired(true);
        // trust store intentionally left unset

        TikaConfigException ex = assertThrows(TikaConfigException.class,
                () -> invokeGetTlsParams(tlsConfig));
        assertTrue(ex.getMessage().contains("no trust store"));
    }

    @Test
    void getTlsParamsAllowsClientAuthRequiredWithTrustStore() throws Throwable {
        TlsConfig tlsConfig = validKeyStoreOnlyConfig();
        tlsConfig.setTrustStoreType("PKCS12");
        tlsConfig.setTrustStorePassword("tika-secret");
        tlsConfig.setTrustStoreFile(Paths
                .get("src", "test", "resources", "ssl-keys", "tika-server-truststore.p12")
                .toString());
        tlsConfig.setClientAuthenticationRequired(true);

        TLSServerParameters params = invokeGetTlsParams(tlsConfig);
        assertTrue(params.getClientAuthentication().isRequired());
    }
}
