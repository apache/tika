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


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonFetchEmitTuple;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.apache.tika.utils.ProcessUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TikaServerEmitterIntegrationTest extends IntegrationTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(TikaServerEmitterIntegrationTest.class);

    private static Path TMP_DIR;
    private static Path TMP_OUTPUT_DIR;
    private static Path TIKA_CONFIG;
    private static Path TIKA_CONFIG_TIMEOUT;

    private static final String EMITTER_NAME = "fse";
    private static final String FETCHER_NAME = "fsf";

    private static String[] FILES = new String[]{
            "hello_world.xml",
            "heavy_hang_30000.xml", "fake_oom.xml", "system_exit.xml",
            "null_pointer.xml"
    };

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        TMP_DIR = Files.createTempDirectory("tika-emitter-test-");
        Path inputDir = TMP_DIR.resolve("input");
        TMP_OUTPUT_DIR = TMP_DIR.resolve("output");
        Files.createDirectories(inputDir);
        Files.createDirectories(TMP_OUTPUT_DIR);

        for (String mockFile : FILES) {
            Files.copy(TikaEmitterTest.class.getResourceAsStream(
                    "/test-documents/mock/" + mockFile),
                    inputDir.resolve(mockFile));
        }
        TIKA_CONFIG = TMP_DIR.resolve("tika-config.xml");
        TIKA_CONFIG_TIMEOUT = TMP_DIR.resolve("tika-config-timeout.xml");

        String xml1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<properties>" +
                "<fetchers>" +
                "<fetcher class=\"org.apache.tika.pipes.fetcher.FileSystemFetcher\">" +
                "<params>" +
                "<param name=\"name\" type=\"string\">" + FETCHER_NAME + "</param>" +
                "<param name=\"basePath\" type=\"string\">" + inputDir.toAbsolutePath() + "</param>" +
                "</params>" +
                "</fetcher>" +
                "</fetchers>" +
                "<emitters>" +
                "<emitter class=\"org.apache.tika.pipes.emitter.fs.FileSystemEmitter\">" +
                "<params>" +
                "<param name=\"name\" type=\"string\">" + EMITTER_NAME + "</param>" +

                "<param name=\"basePath\" type=\"string\">" + TMP_OUTPUT_DIR.toAbsolutePath() + "</param>" +
                "</params>" +
                "</emitter>" +
                "</emitters>" +
                "<server>" +
                "<enableUnsecureFeatures>true</enableUnsecureFeatures>" +
                "<port>9999</port>"+
                "<endpoints>"+
                "<endpoint>emit</endpoint>"+
                "<endpoint>status</endpoint>"+
                "</endpoints>";
        String xml2 = "</server>"+
                "</properties>";

        String tikaConfigXML = xml1+xml2;

        FileUtils.write(TIKA_CONFIG.toFile(), tikaConfigXML, UTF_8);

        String tikaConfigTimeoutXML = xml1+
                "<taskTimeoutMillis>10000</taskTimeoutMillis>"+xml2;
        FileUtils.write(TIKA_CONFIG_TIMEOUT.toFile(), tikaConfigTimeoutXML, UTF_8);

    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        FileUtils.deleteDirectory(TMP_DIR.toFile());
    }

    @After
    public void tear() throws Exception {
        Thread.sleep(500);
    }

    @Before
    public void setUpEachTest() throws Exception {

        for (String problemFile : FILES) {
            Path targ = TMP_OUTPUT_DIR.resolve(problemFile + ".json");

            if (Files.exists(targ)) {
                Files.delete(targ);
                assertFalse(Files.isRegularFile(targ));
            }
        }
    }

    @Test
    public void testBasic() throws Exception {
        Process p = null;
        try {
            p = startProcess( new String[]{  "-config",
                    ProcessUtils.escapeCommandLine(TIKA_CONFIG.toAbsolutePath().toString())});
            JsonNode node = testOne("hello_world.xml", true);
            assertEquals("ok", node.get("status").asText());
        } catch (Exception e) {
            fail("shouldn't have an exception" + e.getMessage());
        } finally {
            if (p != null) {
                p.destroyForcibly();
            }
        }
    }

    @Test
    public void testNPEDefault() throws Exception {

        Process p = null;
        try {
            p = startProcess( new String[]{  "-config",
                    ProcessUtils.escapeCommandLine(TIKA_CONFIG.toAbsolutePath().toString())});
            JsonNode node = testOne("null_pointer.xml", true);
            assertEquals("ok", node.get("status").asText());
            assertContains("java.lang.NullPointerException",
                    node.get("parse_exception").asText());
        } catch (Exception e) {
            fail("shouldn't have an exception" + e.getMessage());
        } finally {
            if (p != null) {
                p.destroyForcibly();
            }
        }
    }

    @Test
    public void testNPESkip() throws Exception {

        Process p = null;
        try {
            p = startProcess( new String[]{  "-config",
                    ProcessUtils.escapeCommandLine(TIKA_CONFIG.toAbsolutePath().toString())});
            JsonNode node = testOne("null_pointer.xml", false,
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);
            assertEquals("ok", node.get("status").asText());
            assertContains("java.lang.NullPointerException",
                    node.get("parse_exception").asText());
        } catch (Exception e) {
            fail("shouldn't have an exception" + e.getMessage());
        } finally {
            if (p != null) {
                p.destroyForcibly();
            }
        }
    }

    @Test(expected = ProcessingException.class)
    public void testSystemExit() throws Exception {
        Process p = null;
        try {
            p = startProcess( new String[]{  "-config",
                    ProcessUtils.escapeCommandLine(TIKA_CONFIG.toAbsolutePath().toString())});
            testOne("system_exit.xml", false);
        } finally {
            if (p != null) {
                p.destroyForcibly();
            }
        }
    }

    @Test
    public void testOOM() throws Exception {

        Process p = null;
        try {
            p = startProcess( new String[]{  "-config",
                    ProcessUtils.escapeCommandLine(TIKA_CONFIG.toAbsolutePath().toString())});
            JsonNode response = testOne("fake_oom.xml", false);
            assertContains("oom message", response.get("parse_error").asText());
        } catch (ProcessingException e) {
            //depending on timing, there may be a connection exception --
            // TODO add more of a delay to server shutdown to ensure message is sent
            // before shutdown.
        } finally {
            if (p != null) {
                p.destroyForcibly();
            }
        }
    }

    @Test(expected = ProcessingException.class)
    public void testTimeout() throws Exception {

        Process p = null;
        try {
            p = startProcess( new String[]{  "-config",
                    ProcessUtils.escapeCommandLine(TIKA_CONFIG_TIMEOUT.toAbsolutePath().toString())});
            JsonNode response = testOne("heavy_hang_30000.xml", false);
        } finally {
            if (p != null) {
                p.destroyForcibly();
            }
        }
    }


    private JsonNode testOne(String fileName, boolean shouldFileExist) throws Exception {
        return testOne(fileName, shouldFileExist, FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
    }

    private JsonNode testOne(String fileName, boolean shouldFileExist, FetchEmitTuple.ON_PARSE_EXCEPTION onParseException) throws Exception {

        awaitServerStartup();
        Response response = WebClient
                .create(endPoint + "/emit")
                .accept("application/json")
                .post(getJsonString(fileName, onParseException));
        if (response.getStatus() == 200) {
            Path targFile = TMP_OUTPUT_DIR.resolve(fileName + ".json");
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
        FetchEmitTuple t = new FetchEmitTuple(
                new FetchKey(FETCHER_NAME, fileName),
                new EmitKey(EMITTER_NAME, ""),
                new Metadata(), onParseException
        );
        return JsonFetchEmitTuple.toJson(t);
    }
}
