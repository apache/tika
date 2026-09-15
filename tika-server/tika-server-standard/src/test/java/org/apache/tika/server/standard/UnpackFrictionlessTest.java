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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.serialization.config.JsonConfigHelper;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.UnpackerResource;
import org.apache.tika.server.core.writer.TarWriter;
import org.apache.tika.server.core.writer.ZipWriter;

/** /unpack under a server-level Frictionless config, with no per-request config. */
public class UnpackFrictionlessTest extends CXFTestBase {

    private static final String TEST_DOC = "test-documents/test_recursive_embedded.docx";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SERVER_CONFIG = """
            {
              "parsers": [ { "default-parser": {} } ],
              "parse-context": {
                "unpack-config": { "outputFormat": "FRICTIONLESS", "outputMode": "ZIPPED" }
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
        unpackTempDir = Files.createTempDirectory("tika-unpack-fd-");
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
    public void testServerConfigMakesUnpackFrictionless() throws Exception {
        Map<String, byte[]> entries = unpack("/unpack");
        assertTrue(entries.containsKey("datapackage.json"), entries.keySet().toString());
        assertTrue(entries.keySet().stream().anyMatch(k -> k.startsWith("unpacked/")),
                entries.keySet().toString());
    }

    /** A Frictionless package carries metadata.json by default; /all adds only the original. */
    @Test
    public void testPlainUnpackHasMetadataJsonByDefault() throws Exception {
        Map<String, byte[]> entries = unpack("/unpack");
        assertTrue(entries.containsKey("metadata.json"), entries.keySet().toString());
        assertFalse(entries.keySet().stream().anyMatch(k -> k.startsWith("unpacked/0.")),
                "plain /unpack must not pack the container's bytes: " + entries.keySet());
    }

    /** DIRECTORY from a request contradicts the one-body transport: 400, not a silent pin. */
    @Test
    public void testDirectoryModeInRequestIs400() throws Exception {
        ContentDisposition cd = new ContentDisposition(
                "form-data; name=\"file\"; filename=\"test_recursive_embedded.docx\"");
        Attachment fileAtt = new Attachment("file", ClassLoader.getSystemResourceAsStream(TEST_DOC), cd);
        Attachment configAtt = new Attachment("config", "application/json", new ByteArrayInputStream(
                "{\"unpack-config\": {\"outputMode\": \"DIRECTORY\"}}".getBytes(StandardCharsets.UTF_8)));
        Response response = WebClient.create(endPoint + "/unpack")
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testUnpackAllCarriesMetadataAndTheOriginal() throws Exception {
        Map<String, byte[]> entries = unpack("/unpack/all");
        assertTrue(entries.containsKey("metadata.json"), entries.keySet().toString());
        assertTrue(entries.keySet().stream().anyMatch(k -> k.startsWith("unpacked/0.")),
                "includeOriginal should add the container: " + entries.keySet());
    }

    /** The RMETA list's row 0 is the container -- its own metadata and its own text. */
    @Test
    public void testMetadataJsonCarriesTheContainerRow() throws Exception {
        Map<String, byte[]> entries = unpack("/unpack");
        JsonNode rows = MAPPER.readTree(entries.get("metadata.json"));
        assertTrue(rows.isArray() && rows.size() > 1, "expected an RMETA list, got: " + rows);

        JsonNode container = rows.get(0);
        assertEquals("0", container.path("tk:embedded-depth").asText(),
                "row 0 should be the container: " + container);
        assertTrue(container.hasNonNull("tk:content"),
                "the container row should carry its own extracted text: " + container);
        assertTrue(container.hasNonNull("dcterms:created"),
                "the container row should carry its own metadata: " + container);
    }

    /** Every row can be joined to its file: id, tree position and logical path all travel. */
    @Test
    public void testRowsCarryEmbeddedPathData() throws Exception {
        Map<String, byte[]> entries = unpack("/unpack/all");
        JsonNode rows = MAPPER.readTree(entries.get("metadata.json"));
        JsonNode embedded = rows.get(1);
        for (String key : new String[]{"tk:embedded-id", "tk:embedded-id-path",
                "tk:embedded-resource-path", "tk:resource-name"}) {
            assertTrue(embedded.hasNonNull(key), key + " missing from: " + embedded);
        }
        String id = embedded.get("tk:embedded-id").asText();
        assertTrue(entries.keySet().stream().anyMatch(k -> k.startsWith("unpacked/" + id + ".")),
                "row's tk:embedded-id should name a packed file: " + entries.keySet());
    }

    private Map<String, byte[]> unpack(String path) throws Exception {
        Response response = WebClient.create(endPoint + path)
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        assertEquals(200, response.getStatus());
        return readZipArchiveBytes((InputStream) response.getEntity());
    }
}
