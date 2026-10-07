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
package org.apache.tika.server.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.serialization.config.JsonConfigHelper;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.UnpackerResource;
import org.apache.tika.server.core.writer.TarWriter;
import org.apache.tika.server.core.writer.ZipWriter;

/** A server-level unpack-config.outputFormat of REGULAR restores the pre-4.2 flat zip. */
public class UnpackRegularConfigTest extends CXFTestBase {

    private static final String TEST_DOC = "test-documents/test_recursive_embedded.docx";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SERVER_CONFIG = """
            {
              "parsers": [ { "default-parser": {} } ],
              "parse-context": {
                "unpack-config": { "outputFormat": "REGULAR" }
              }
            }
            """;

    private Path unpackTempDir;

    @Override
    protected boolean isAllowPerRequestConfig() {
        return true;
    }

    @Override
    protected InputStream getTikaConfigInputStream() throws IOException {
        return new ByteArrayInputStream(SERVER_CONFIG.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(UnpackerResource.class);
        sf.setResourceProvider(UnpackerResource.class,
                new SingletonResourceProvider(new UnpackerResource(tikaResource)));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TarWriter());
        providers.add(new ZipWriter());
        providers.add(new TikaServerParseExceptionMapper());
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getPipesConfigInputStream() throws IOException {
        unpackTempDir = Files.createTempDirectory("tika-unpack-regular-");
        Map<String, Object> replacements = new HashMap<>();
        replacements.put("UNPACK_EMITTER_BASE_PATH", unpackTempDir.toAbsolutePath().toString());
        replacements.put("PLUGINS_PATHS",
                Paths.get("target/plugins").toAbsolutePath().toString().replace("\\", "/"));
        replacements.put("TIMEOUT_MILLIS", 60000L);
        JsonNode config = JsonConfigHelper.loadFromResource(
                "/configs/cxf-unpack-test-template.json", CXFTestBase.class, replacements);
        return new ByteArrayInputStream(
                MAPPER.writeValueAsString(config).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected Path getUnpackEmitterBasePath() {
        return unpackTempDir;
    }

    @Test
    public void testServerConfigRestoresTheFlatLayout() throws Exception {
        Map<String, byte[]> entries = unpack("/unpack");
        assertFalse(entries.containsKey("datapackage.json"), entries.keySet().toString());
        assertFalse(entries.containsKey("metadata.json"), entries.keySet().toString());
        assertTrue(entries.keySet().stream().noneMatch(k -> k.contains("/")),
                "REGULAR puts every file at the zip root: " + entries.keySet());
        assertFalse(entries.isEmpty());
    }

    /** Under REGULAR, /all is where the per-file sidecars and the original come from. */
    @Test
    public void testUnpackAllAddsSidecarsAndTheOriginal() throws Exception {
        Map<String, byte[]> entries = unpack("/unpack/all");
        assertTrue(entries.keySet().stream().anyMatch(k -> k.endsWith(".metadata.json")),
                entries.keySet().toString());
        assertTrue(entries.keySet().stream().anyMatch(k -> k.startsWith("0.")),
                "includeOriginal should add the container at the root: " + entries.keySet());
    }

    private Map<String, byte[]> unpack(String path) throws Exception {
        Response response = WebClient.create(endPoint + path)
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        assertEquals(200, response.getStatus());
        return readZipArchiveBytes((InputStream) response.getEntity());
    }
}
