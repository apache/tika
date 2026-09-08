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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.apache.tika.serialization.config.JsonConfigHelper;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.UnpackerResource;
import org.apache.tika.server.core.writer.ZipWriter;

/** The catalog preset {@code thumbnails} unpacks exactly one raster thumbnail (TIKA-4856). */
public class ThumbnailPresetTest extends CXFTestBase {

    private static final String PRESET_PATH = "/unpack/preset/thumbnails";
    private static final String UNPACK_CONFIG_TEMPLATE = "/configs/cxf-unpack-test-template.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Path unpackTempDir;

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(UnpackerResource.class);
        sf.setResourceProvider(UnpackerResource.class,
                new SingletonResourceProvider(new UnpackerResource(tikaResource)));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new ZipWriter());
        providers.add(new TikaServerParseExceptionMapper());
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getTikaConfigInputStream() {
        return getClass().getResourceAsStream("/configs/tika-config-thumbnail-preset.json");
    }

    @Override
    protected InputStream getPipesConfigInputStream() throws IOException {
        unpackTempDir = Files.createTempDirectory("tika-unpack-test-");
        Map<String, Object> replacements = new HashMap<>();
        replacements.put("UNPACK_EMITTER_BASE_PATH", unpackTempDir.toAbsolutePath().toString());
        replacements.put("PLUGINS_PATHS",
                Paths.get("target/plugins").toAbsolutePath().toString().replace("\\", "/"));
        replacements.put("TIMEOUT_MILLIS", 60000L);
        ObjectNode config = (ObjectNode) JsonConfigHelper.loadFromResource(
                UNPACK_CONFIG_TEMPLATE, CXFTestBase.class, replacements);
        // the worker resolves the preset name from its own config
        config.putObject("presets").put("thumbnails", true);
        return new ByteArrayInputStream(MAPPER.writeValueAsString(config).getBytes(UTF_8));
    }

    @Override
    protected Path getUnpackEmitterBasePath() {
        return unpackTempDir;
    }

    @ParameterizedTest
    @CsvSource({
            "testDOCX_Thumbnail.docx, png", // EMF thumbnail, rasterized by the metafile renderer
            "testPDFTwoTextBoxes.pdf, png", // first page rendering
            "testMP3_twoCovers.mp3, png"    // two stored covers; the first one wins
    })
    public void testExactlyOneThumbnail(String file, String format) throws Exception {
        Map<String, byte[]> entries = unpack(PRESET_PATH, file);
        assertEquals(1, entries.size(), file + " -> " + entries.keySet());
        Map.Entry<String, byte[]> entry = entries.entrySet().iterator().next();
        assertTrue(entry.getKey().endsWith("." + format), entry.getKey());
        assertEquals(format, imageFormat(entry.getValue()), entry.getKey());
    }

    @Test
    public void testPlainUnpackKeepsBothCovers() throws Exception {
        // the preset's selector, not the parser, is what drops the second cover
        assertEquals(2, unpack("/unpack", "testMP3_twoCovers.mp3").size());
    }

    private Map<String, byte[]> unpack(String path, String file) throws IOException {
        Response response = WebClient.create(endPoint + path)
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/" + file));
        assertEquals(200, response.getStatus(), file);
        return readZipArchiveBytes((InputStream) response.getEntity());
    }

    private static String imageFormat(byte[] bytes) {
        if (bytes.length > 4 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G') {
            return "png";
        }
        if (bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "jpg";
        }
        return "unknown";
    }
}
