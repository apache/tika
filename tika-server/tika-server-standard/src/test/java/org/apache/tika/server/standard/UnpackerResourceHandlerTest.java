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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

/**
 * /unpack has no format segment, so the config part is the only place a handler is named --
 * and it reaches the RMETA list written to metadata.json.
 */
public class UnpackerResourceHandlerTest extends CXFTestBase {

    private static final String TEST_DOC = "test-documents/test_recursive_embedded.docx";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FRICTIONLESS =
            "\"unpack-config\": {\"outputFormat\": \"FRICTIONLESS\", \"outputMode\": \"ZIPPED\", "
                    + "\"includeFullMetadata\": true}";

    private Path unpackTempDir;

    @Override
    protected boolean isAllowPerRequestConfig() {
        return true;
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
    protected InputStream getTikaConfigInputStream() throws IOException {
        return this.getClass().getResourceAsStream("/configs/tika-config-unpacker.json");
    }

    @Override
    protected InputStream getPipesConfigInputStream() throws IOException {
        unpackTempDir = Files.createTempDirectory("tika-unpack-probe-");
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
    public void testMetadataJsonDefaultsToMarkdown() throws Exception {
        String json = metadataJson("{" + FRICTIONLESS + "}");
        assertFalse(json.contains("<html xmlns="), "default handler should not be XHTML");
    }

    @Test
    public void testConfigPartSelectsTheMetadataJsonHandler() throws Exception {
        String json = metadataJson("{" + FRICTIONLESS
                + ", \"basic-content-handler-factory\": {\"type\": \"XML\"}}");
        assertTrue(json.contains("<html xmlns="),
                "config part should select the handler for metadata.json");
    }

    /** /unpack/all/<handler> used to 200 and silently ignore the segment. */
    @Test
    public void testHandlerSegmentIsHonoredNotSwallowed() throws Exception {
        assertEquals(0, sidecarsWithContent(putAll("/unpack/all/ignore")),
                "ignore in the path must suppress tk:content");
        assertTrue(sidecarsWithContent(putAll("/unpack/all")) > 0, "baseline carries tk:content");
    }

    /** Plain /unpack carries no metadata for a handler to render, so it takes no segment. */
    @Test
    public void testNoHandlerSegmentOnPlainUnpack() throws Exception {
        assertEquals(404, WebClient.create(endPoint + "/unpack/xml")
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC)).getStatus());
    }

    @Test
    public void testUnrecognizedHandlerSegmentIsBadRequest() throws Exception {
        assertEquals(400, WebClient.create(endPoint + "/unpack/all/somethingOrOther")
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC)).getStatus());
    }

    private List<byte[]> putAll(String path) throws Exception {
        Response response = WebClient.create(endPoint + path)
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        assertEquals(200, response.getStatus());
        return sidecarsOf(response);
    }

    /** includeMetadataInZip writes a sidecar per extracted file; it carries tk:content too. */
    @Test
    public void testPerFileSidecarsFollowTheConfigPartHandler() throws Exception {
        assertTrue(perFileSidecar("{\"unpack-config\": {\"includeMetadataInZip\": true}}")
                .contains("\"tk:content-handler-type\":\"MARKDOWN\""));

        String xml = perFileSidecar("{\"unpack-config\": {\"includeMetadataInZip\": true}, "
                + "\"basic-content-handler-factory\": {\"type\": \"XML\"}}");
        assertTrue(xml.contains("\"tk:content-handler-type\":\"XML\""), xml);
        assertTrue(xml.contains("<html xmlns="), xml);
    }

    /** IGNORE is the opt-out: sidecars keep their metadata but carry no extracted text. */
    @Test
    public void testIgnoreHandlerLeavesSidecarsWithoutContent() throws Exception {
        String withText = "{\"unpack-config\": {\"includeMetadataInZip\": true}}";
        String ignore = "{\"unpack-config\": {\"includeMetadataInZip\": true}, "
                + "\"basic-content-handler-factory\": {\"type\": \"IGNORE\"}}";

        assertTrue(sidecarsWithContent(withText) > 0, "baseline should carry tk:content");
        assertEquals(0, sidecarsWithContent(ignore), "IGNORE should suppress tk:content");
        assertTrue(sidecarCount(ignore) > 0, "IGNORE must not drop the sidecars themselves");
    }

    private int sidecarsWithContent(String configJson) throws Exception {
        return sidecarsWithContent(sidecars(configJson));
    }

    private int sidecarsWithContent(List<byte[]> sidecars) throws Exception {
        int count = 0;
        for (byte[] sidecar : sidecars) {
            if (new String(sidecar, StandardCharsets.UTF_8).contains("\"tk:content\"")) {
                count++;
            }
        }
        return count;
    }

    private int sidecarCount(String configJson) throws Exception {
        return sidecars(configJson).size();
    }

    private List<byte[]> sidecars(String configJson) throws Exception {
        Response response = post("/unpack", configJson);
        assertEquals(200, response.getStatus());
        return sidecarsOf(response);
    }

    private List<byte[]> sidecarsOf(Response response) throws Exception {
        Map<String, byte[]> entries = readZipArchiveBytes((InputStream) response.getEntity());
        List<byte[]> out = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().endsWith(".metadata.json")) {
                out.add(e.getValue());
            }
        }
        return out;
    }

    private String perFileSidecar(String configJson) throws Exception {
        Response response = post("/unpack", configJson);
        assertEquals(200, response.getStatus());
        Map<String, byte[]> entries = readZipArchiveBytes((InputStream) response.getEntity());
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().endsWith(".metadata.json")) {
                String json = new String(e.getValue(), StandardCharsets.UTF_8);
                if (json.contains("\"tk:content\"")) {
                    return json;
                }
            }
        }
        throw new AssertionError("no per-file sidecar carrying tk:content");
    }

    private String metadataJson(String configJson) throws Exception {
        Response response = post("/unpack", configJson);
        assertEquals(200, response.getStatus());
        Map<String, byte[]> entries = readZipArchiveBytes((InputStream) response.getEntity());
        byte[] md = entries.get("metadata.json");
        assertNotNull(md, "includeFullMetadata should write metadata.json");
        return new String(md, StandardCharsets.UTF_8);
    }

    private Response post(String path, String configJson) {
        ContentDisposition cd = new ContentDisposition(
                "form-data; name=\"file\"; filename=\"test_recursive_embedded.docx\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream(TEST_DOC), cd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));
        return WebClient.create(endPoint + path)
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
    }
}
