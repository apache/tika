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


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.cxf.jaxrs.client.WebClient;
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
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TikaServerEmitterIntegrationTest extends IntegrationTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(TikaServerEmitterIntegrationTest.class);

    private static Path TMP_DIR;
    private static Path TMP_OUTPUT_DIR;
    private static String TIKA_CONFIG_XML;
    private static Path TIKA_CONFIG;

    private static final String EMITTER_NAME = "fse";
    private static final String FETCHER_NAME = "fsf";

    private static String[] FILES = new String[] {
            "hello_world.xml",
            "heavy_hang_30000.xml", "real_oom.xml", "system_exit.xml"
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
                    "/test-documents/mock/"+mockFile),
                    inputDir.resolve(mockFile));
        }
        TIKA_CONFIG = TMP_DIR.resolve("tika-config.xml");

        TIKA_CONFIG_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
                "<properties>"+
                "<fetchers>"+
                "<fetcher class=\"org.apache.tika.pipes.fetcher.FileSystemFetcher\">"+
                "<params>"+
                "<param name=\"name\" type=\"string\">"+FETCHER_NAME+"</param>"+
                "<param name=\"basePath\" type=\"string\">"+inputDir.toAbsolutePath()+"</param>"+
                "</params>"+
                "</fetcher>"+
                "</fetchers>"+
                "<emitters>"+
                "<emitter class=\"org.apache.tika.pipes.emitter.fs.FileSystemEmitter\">"+
                "<params>"+
                "<param name=\"name\" type=\"string\">"+EMITTER_NAME+"</param>"+

                "<param name=\"basePath\" type=\"string\">"+ TMP_OUTPUT_DIR.toAbsolutePath()+"</param>"+
                "</params>"+
                "</emitter>"+
                "</emitters>"+
                "</properties>";

        FileUtils.write(TIKA_CONFIG.toFile(), TIKA_CONFIG_XML, UTF_8);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        FileUtils.deleteDirectory(TMP_DIR.toFile());
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

        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-enableUnsecureFeatures",
                                "-maxFiles", "2000",
                                "-p", INTEGRATION_TEST_PORT,
                                "-tmpFilePrefix", "basic-",
                                "-config", TIKA_CONFIG.toAbsolutePath().toString()
                        });
            }
        };
        serverThread.start();
        try {
            testOne("hello_world.xml", true);
        } finally {
            serverThread.interrupt();
        }
    }

    @Test(expected = ProcessingException.class)
    public void testSystemExit() throws Exception {

        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-enableUnsecureFeatures",
                                "-maxFiles", "2000",
                                "-p", INTEGRATION_TEST_PORT,
                                "-tmpFilePrefix", "basic-",
                                "-config", TIKA_CONFIG.toAbsolutePath().toString()
                        });
            }
        };
        serverThread.start();
        try {
            testOne("system_exit.xml", false);
        } finally {
            serverThread.interrupt();
        }
    }

    @Test
    public void testOOM() throws Exception {

        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-enableUnsecureFeatures",
                                "-JXmx128m",
                                "-maxFiles", "2000",
                                "-p", INTEGRATION_TEST_PORT,
                                "-tmpFilePrefix", "basic-",
                                "-config", TIKA_CONFIG.toAbsolutePath().toString()
                        });
            }
        };
        serverThread.start();
        try {
            JsonNode response = testOne("real_oom.xml", false);
            assertContains("heap space", response.get("parse_error").asText());
        } finally {
            serverThread.interrupt();
        }
    }

    @Test(expected = ProcessingException.class)
    public void testTimeout() throws Exception {

        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-enableUnsecureFeatures",
                                "-JXmx128m",
                                "-taskTimeoutMillis", "2000", "-taskPulseMillis", "100",
                                "-p", INTEGRATION_TEST_PORT,
                                "-tmpFilePrefix", "basic-",
                                "-config", TIKA_CONFIG.toAbsolutePath().toString()
                        });
            }
        };
        serverThread.start();
        try {
            JsonNode response = testOne("heavy_hang_30000.xml", false);
            assertContains("heap space", response.get("parse_error").asText());
        } finally {
            serverThread.interrupt();
        }
    }

    private void awaitServerStartup() throws Exception {
        Instant started = Instant.now();
        long elapsed = Duration.between(started, Instant.now()).toMillis();
        WebClient client = WebClient.create(endPoint+"/tika").accept("text/plain");
        while (elapsed < MAX_WAIT_MS) {
            try {
                Response response = client.get();
                if (response.getStatus() == 200) {
                    elapsed = Duration.between(started, Instant.now()).toMillis();
                    LOG.info("client observes server successfully started after " +
                            elapsed+ " ms");
                    return;
                }
                LOG.debug("tika test client failed to connect to server with status: {}", response.getStatus());

            } catch (javax.ws.rs.ProcessingException e) {
                LOG.debug("tika test client failed to connect to server", e);
            }

            Thread.sleep(100);
            elapsed = Duration.between(started, Instant.now()).toMillis();
        }
        throw new TimeoutException("couldn't connect to server after " +
                elapsed + " ms");
    }

    private JsonNode testOne(String fileName, boolean shouldFileExist) throws Exception {
        awaitServerStartup();
        Response response = WebClient
                .create(endPoint + "/emit")
                .accept("application/json")
                .post(getJsonString(fileName));
        if (shouldFileExist) {
            Path targFile = TMP_OUTPUT_DIR.resolve(fileName + ".json");
            assertTrue(Files.size(targFile) > 1);
        }
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        return new ObjectMapper().readTree(reader);
    }

    private String getJsonString(String fileName) throws IOException {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = new JsonFactory().createGenerator(writer)) {
            generator.writeStartObject();
            generator.writeStringField("fetcher", FETCHER_NAME);
            generator.writeStringField("fetchKey", fileName);
            generator.writeStringField("emitter", EMITTER_NAME);
        }
        return writer.toString();
    }
}
