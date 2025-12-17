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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.serialization.JsonFetchEmitTuple;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.utils.ProcessUtils;

public class TikaServerPipesIntegrationTest extends IntegrationTestBase {

    private static Path TEMP_OUTPUT_DIR;
    private static Path TIKA_CONFIG;
    private static Path TIKA_CONFIG_TIMEOUT;
    private static String[] FILES = new String[]{"hello_world.xml", "heavy_hang_30000.xml", "fake_oom.xml", "system_exit.xml", "null_pointer.xml"};

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        Path inputDir = TEMP_WORKING_DIR.resolve("input");
        TEMP_OUTPUT_DIR = TEMP_WORKING_DIR.resolve("output");
        Files.createDirectories(inputDir);
        Files.createDirectories(TEMP_OUTPUT_DIR);

        for (String mockFile : FILES) {
            Files.copy(TikaPipesTest.class.getResourceAsStream("/test-documents/mock/" + mockFile), inputDir.resolve(mockFile));
        }
        TIKA_CONFIG = TEMP_WORKING_DIR.resolve("tika-config.json");
        TIKA_CONFIG_TIMEOUT = TEMP_WORKING_DIR.resolve("tika-config-timeout.json");
        CXFTestBase.createPluginsConfig(TIKA_CONFIG, inputDir, TEMP_OUTPUT_DIR, null, 5000L);
        CXFTestBase.createPluginsConfig(TIKA_CONFIG_TIMEOUT, inputDir, TEMP_OUTPUT_DIR, null, 500L);

    }

    @AfterEach
    public void tear() throws Exception {
        Thread.sleep(500);
    }

    @BeforeEach
    public void setUpEachTest() throws Exception {

        for (String problemFile : FILES) {
            Path targ = TEMP_OUTPUT_DIR.resolve(problemFile + ".json");

            if (Files.exists(targ)) {
                Files.delete(targ);
                assertFalse(Files.isRegularFile(targ));
            }
        }
    }

    @Test
    public void testBasic() throws Exception {
        startProcess(new String[]{
                "-config", ProcessUtils.escapeCommandLine(TIKA_CONFIG
                .toAbsolutePath()
                .toString())});
        JsonNode node = testOne("hello_world.xml", true);
        assertEquals("ok", node
                .get("status")
                .asText());

    }

    @Test
    public void testNPEDefault() throws Exception {

        startProcess(new String[]{
                "-config", ProcessUtils.escapeCommandLine(TIKA_CONFIG
                .toAbsolutePath()
                .toString())});
        JsonNode node = testOne("null_pointer.xml", true);
        assertEquals("ok", node
                .get("status")
                .asText());
        assertContains("java.lang.NullPointerException", node
                .get("parse_exception")
                .asText());
    }

    @Test
    public void testNPESkip() throws Exception {

        startProcess(new String[]{
                "-config", ProcessUtils.escapeCommandLine(TIKA_CONFIG
                .toAbsolutePath()
                .toString())});
        JsonNode node = testOne("null_pointer.xml", false, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);
        assertEquals("ok", node
                .get("status")
                .asText());
        assertContains("java.lang.NullPointerException", node
                .get("parse_exception")
                .asText());
    }

    @Test
    public void testSystemExit() throws Exception {
        startProcess(new String[]{
                "-config", ProcessUtils.escapeCommandLine(TIKA_CONFIG
                .toAbsolutePath()
                .toString())});
        JsonNode node = testOne("system_exit.xml", false);
        assertEquals("process_crash", node
                .get("status")
                .asText());
        assertContains("UNSPECIFIED_CRASH", node
                .get("type")
                .asText());
    }

    @Test
    public void testOOM() throws Exception {

        try {
            startProcess(new String[]{
                    "-config", ProcessUtils.escapeCommandLine(TIKA_CONFIG
                    .toAbsolutePath()
                    .toString())});
            JsonNode node = testOne("fake_oom.xml", false);
            assertEquals("process_crash", node
                    .get("status")
                    .asText());
            assertContains("OOM", node
                    .get("type")
                    .asText());
        } catch (ProcessingException e) {
            //depending on timing, there may be a connection exception --
            // TODO add more of a delay to server shutdown to ensure message is sent
            // before shutdown.
        }
    }

    @Test
    public void testTimeout() throws Exception {
        startProcess(new String[]{
                "-config", ProcessUtils.escapeCommandLine(TIKA_CONFIG_TIMEOUT
                .toAbsolutePath()
                .toString())});
        JsonNode node = testOne("heavy_hang_30000.xml", false);
        assertEquals("process_crash", node
                .get("status")
                .asText());
        assertContains("TIMEOUT", node
                .get("type")
                .asText());
    }

    @Test
    public void testPerRequestTimeout() throws Exception {
        // Start server with 5000ms timeout (TIKA_CONFIG)
        // but send a request with 100ms per-request timeout
        // This should timeout after 100ms, not 5000ms
        startProcess(new String[]{
                "-config", ProcessUtils.escapeCommandLine(TIKA_CONFIG
                .toAbsolutePath()
                .toString())});
        JsonNode node = testOneWithPerRequestTimeout("heavy_hang_30000.xml", 100);
        assertEquals("process_crash", node
                .get("status")
                .asText());
        assertContains("TIMEOUT", node
                .get("type")
                .asText());
    }

    private JsonNode testOneWithPerRequestTimeout(String fileName, long timeoutMillis) throws Exception {
        awaitServerStartup();
        Response response = WebClient
                .create(endPoint + "/pipes")
                .accept("application/json")
                .post(getJsonStringWithTimeout(fileName, timeoutMillis));
        if (response.getStatus() == 200) {
            Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
            return new ObjectMapper().readTree(reader);
        }
        return null;
    }

    private String getJsonStringWithTimeout(String fileName, long timeoutMillis) throws IOException {
        ParseContext parseContext = new ParseContext();
        parseContext.set(ContentHandlerFactory.class,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        parseContext.set(ParseMode.class, ParseMode.RMETA);
        parseContext.addConfig("tika-task-timeout", "{\"timeoutMillis\":" + timeoutMillis + "}");

        FetchEmitTuple t = new FetchEmitTuple(fileName,
                new FetchKey(CXFTestBase.FETCHER_ID, fileName),
                new EmitKey(CXFTestBase.EMITTER_JSON_ID, ""),
                new Metadata(),
                parseContext,
                FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
        return JsonFetchEmitTuple.toJson(t);
    }

    private JsonNode testOne(String fileName, boolean shouldFileExist) throws Exception {
        return testOne(fileName, shouldFileExist, FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
    }

    private JsonNode testOne(String fileName, boolean shouldFileExist, FetchEmitTuple.ON_PARSE_EXCEPTION onParseException) throws Exception {

        awaitServerStartup();
        Response response = WebClient
                .create(endPoint + "/pipes")
                .accept("application/json")
                .post(getJsonString(fileName, onParseException));
        if (response.getStatus() == 200) {
            Path targFile = TEMP_OUTPUT_DIR.resolve(fileName + ".json");
            if (shouldFileExist) {
                assertTrue(Files.size(targFile) > 1);
            } else {
                assertFalse(Files.isRegularFile(targFile));
            }
            Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
            return new ObjectMapper().readTree(reader);
        }
        return null;
    }

    private String getJsonString(String fileName, FetchEmitTuple.ON_PARSE_EXCEPTION onParseException) throws IOException {
        ParseContext parseContext = new ParseContext();
        parseContext.set(ContentHandlerFactory.class,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        parseContext.set(ParseMode.class, ParseMode.RMETA);
        FetchEmitTuple t = new FetchEmitTuple(fileName, new FetchKey(CXFTestBase.FETCHER_ID, fileName),
                new EmitKey(CXFTestBase.EMITTER_JSON_ID, ""), new Metadata(), parseContext, onParseException);
        return JsonFetchEmitTuple.toJson(t);
    }
}
