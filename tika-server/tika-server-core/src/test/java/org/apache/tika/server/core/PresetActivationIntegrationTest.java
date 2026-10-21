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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.serialization.JsonMetadataList;

/**
 * A catalog preset activated with {@code --preset} or {@code TIKA_PRESETS} instead of the
 * config's {@code presets} block, resolved by the front end and the forked worker alike. The
 * catalog entry is the test classpath's {@code catalog-xml-content}.
 */
public class PresetActivationIntegrationTest extends IntegrationTestBase {

    private static final String PRESET = "catalog-xml-content";

    @Test
    public void testPresetOption() throws Exception {
        startProcess(new String[]{"-config", getConfig("tika-config-server-basic.json"),
                "--preset", PRESET});
        assertPresetServesXml();
    }

    @Test
    public void testPresetsEnvironmentVariable() throws Exception {
        startProcess(new String[]{"-config", getConfig("tika-config-server-basic.json")},
                Map.of(TikaServerConfig.PRESETS_ENV, " " + PRESET + ", "));
        assertPresetServesXml();
    }

    @Test
    public void testUnknownPresetFailsStartup() throws Exception {
        startProcess(new String[]{"-config", getConfig("tika-config-server-basic.json"),
                "--preset", "no-such-preset"});
        assertTrue(process.waitFor(MAX_WAIT_MS, TimeUnit.MILLISECONDS), "server should exit");
        assertNotEquals(0, process.exitValue());
    }

    private void assertPresetServesXml() throws Exception {
        awaitServerStartup();
        Response response = WebClient
                .create(endPoint + RMETA_PATH + "/preset/" + PRESET)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));
        assertEquals(200, response.getStatus());
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(1, metadataList.size());
        assertContains("<body><p>hello world</p>",
                metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }
}
