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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.jupiter.api.Test;

/**
 * A {@code server} field set with {@code TIKA_SERVER_SETTING_<FIELD>} or {@code --serverSetting}
 * instead of the config file, over the file's own value.
 */
public class ServerSettingsIntegrationTest extends IntegrationTestBase {

    private static final String LIMIT_ENV =
            TikaServerConfig.SETTINGS_ENV_PREFIX + "MAX_REQUEST_SIZE_BYTES";

    @Test
    public void testEnvironmentVariable() throws Exception {
        startProcess(new String[]{"-config", getConfig("tika-config-server-basic.json")},
                Map.of(LIMIT_ENV, "10"));
        assertLimitIs10();
    }

    @Test
    public void testServerSettingOption() throws Exception {
        startProcess(new String[]{"-config", getConfig("tika-config-server-basic.json"),
                "--" + TikaServerConfig.SETTING_OPTION, "maxRequestSizeBytes=10"},
                Map.of(LIMIT_ENV, "1000000"));
        assertLimitIs10();
    }

    @Test
    public void testUnknownFieldFailsStartup() throws Exception {
        startProcess(new String[]{"-config", getConfig("tika-config-server-basic.json")},
                Map.of(TikaServerConfig.SETTINGS_ENV_PREFIX + "NO_SUCH_FIELD", "1"));
        assertTrue(process.waitFor(MAX_WAIT_MS, TimeUnit.MILLISECONDS), "server should exit");
        assertNotEquals(0, process.exitValue());
    }

    private void assertLimitIs10() throws Exception {
        awaitServerStartup();
        Response response = WebClient
                .create(endPoint + RMETA_PATH)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));
        assertEquals(413, response.getStatus());
        String body = response.readEntity(String.class);
        assertContains("(10 bytes)", body);
        assertContains(LIMIT_ENV, body);
    }
}
