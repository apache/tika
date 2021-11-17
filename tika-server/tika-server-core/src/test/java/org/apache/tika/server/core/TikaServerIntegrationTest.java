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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.server.core.config.TimeoutConfig;
import org.apache.tika.utils.ProcessUtils;

public class TikaServerIntegrationTest extends IntegrationTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(TikaServerIntegrationTest.class);

    @Test
    public void testBasic() throws Exception {

        startProcess(new String[]{"-config", getConfig("tika-config-server-basic.xml")});
        testBaseline();
    }

    @Test
    public void testOOM() throws Exception {

        startProcess(new String[]{"-config", getConfig("tika-config-server-basic.xml")});

        awaitServerStartup();

        Response response = null;
        try {
            response = WebClient.create(endPoint + RMETA_PATH).accept("application/json")
                    .put(ClassLoader.getSystemResourceAsStream(TEST_OOM));
        } catch (Exception e) {
            //oom may or may not cause an exception depending
            //on the timing
        }
        //give some time for the server to crash/terminate itself
        Thread.sleep(2000);
        testBaseline();

    }

    @Test
    public void testSameServerIdAfterOOM() throws Exception {

        startProcess(new String[]{"-config", getConfig(
                "tika-config-server-basic.xml")});
        awaitServerStartup();
        String serverId = getServerId();
        Response response = null;
        try {
            response = WebClient.create(endPoint + RMETA_PATH).accept("application/json")
                    .put(ClassLoader.getSystemResourceAsStream(TEST_OOM));
        } catch (Exception e) {
            //oom may or may not cause an exception depending
            //on the timing
        }
        //give some time for the server to crash/terminate itself
        Thread.sleep(2000);
        testBaseline();
        assertEquals(serverId, getServerId());
        assertTrue(getNumRestarts() > 0);
        assertTrue(getNumRestarts() < 3);
    }

    @Test
    public void testMinimumTimeoutInHeader() throws Exception {
        startProcess(new String[]{"-config", getConfig(
                "tika-config-server-basic.xml")});
        awaitServerStartup();

        Response response = WebClient.create(endPoint + RMETA_PATH)
                    .accept("application/json")
                    .header(TimeoutConfig.X_TIKA_TIMEOUT_MILLIS, 1)
                    .put(ClassLoader.getSystemResourceAsStream(TEST_HEAVY_HANG));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(),
                    response.getStatus());
    }

    @Test
    public void testTaskTimeoutHeader() throws Exception {

        startProcess(new String[]{"-config", getConfig(
                "tika-config-server-basic.xml")});
        awaitServerStartup();
        String serverId = getServerId();
        Response response = null;
        try {
            response = WebClient.create(endPoint + RMETA_PATH)
                    .accept("application/json")
                    .header(TimeoutConfig.X_TIKA_TIMEOUT_MILLIS, 100)
                    .put(ClassLoader.getSystemResourceAsStream(TEST_HEAVY_HANG));
        } catch (Exception e) {
            //oom may or may not cause an exception depending
            //on the timing
        }
        //give some time for the server to crash/terminate itself
        Thread.sleep(2000);
        testBaseline();
        assertEquals(serverId, getServerId());
        assertTrue(getNumRestarts() > 0);
        assertTrue(getNumRestarts() < 3);
    }

    @Test
    public void testSameDeclaredServerIdAfterOOM() throws Exception {
        String serverId = "qwertyuiop";
        startProcess(
                new String[]{"-config", getConfig("tika-config-server-basic.xml"), "-id",
                        serverId});
        awaitServerStartup();
        Response response = null;
        try {
            response = WebClient.create(endPoint + RMETA_PATH).accept("application/json")
                    .put(ClassLoader.getSystemResourceAsStream(TEST_OOM));
        } catch (Exception e) {
            //oom may or may not cause an exception depending
            //on the timing
        }
        //give some time for the server to crash/terminate itself
        Thread.sleep(2000);
        testBaseline();
        assertEquals(serverId, getServerId());

    }

    private String getServerId() throws Exception {
        Response response =
                WebClient.create(endPoint + STATUS_PATH).accept("application/json").get();
        String jsonString =
                CXFTestBase.getStringFromInputStream((InputStream) response.getEntity());
        JsonNode root = new ObjectMapper().readTree(jsonString);
        return root.get("server_id").asText();
    }

    private int getNumRestarts() throws Exception {
        Response response =
                WebClient.create(endPoint + STATUS_PATH).accept("application/json").get();
        String jsonString =
                CXFTestBase.getStringFromInputStream((InputStream) response.getEntity());
        JsonNode root = new ObjectMapper().readTree(jsonString);
        return root.get("num_restarts").intValue();
    }

    @Test
    public void testSystemExit() throws Exception {
        startProcess(new String[]{"-config", getConfig("tika-config-server-basic.xml")});

        awaitServerStartup();
        Response response = null;
        try {
            response = WebClient.create(endPoint + RMETA_PATH).accept("application/json")
                    .put(ClassLoader.getSystemResourceAsStream(TEST_SYSTEM_EXIT));
        } catch (Exception e) {
            //sys exit causes catchable problems for the client
        }
        //give some time for the server to crash/terminate itself
        Thread.sleep(2000);

        testBaseline();

    }

    @Test
    public void testTimeoutOk() throws Exception {
        startProcess(
                new String[]{"-config", getConfig("tika-config-server-timeout-10000.xml")});
        awaitServerStartup();
        Response response = null;
        try {
            response = WebClient.create(endPoint + RMETA_PATH).accept("application/json")
                    .put(ClassLoader.getSystemResourceAsStream(TEST_HEAVY_HANG_SHORT));
        } catch (Exception e) {
            //potential exception depending on timing
        }
        testBaseline();

    }

    @Test
    @Timeout(60000)
    public void testTimeout() throws Exception {
        startProcess(
                new String[]{"-config", getConfig("tika-config-server-timeout-10000.xml")});
        awaitServerStartup();
        Response response = null;
        try {
            response = WebClient.create(endPoint + RMETA_PATH).accept("application/json")
                    .put(ClassLoader.getSystemResourceAsStream(TEST_HEAVY_HANG));
        } catch (Exception e) {
            //catchable exception when server shuts down.
        }
        testBaseline();

    }

    @Test
    public void testBadJVMArgs() throws Exception {

        startProcess(
                new String[]{"-config", getConfig("tika-config-server-badjvmargs.xml"),});

        boolean finished = process.waitFor(10000, TimeUnit.MILLISECONDS);
        if (!finished) {
            fail("should have completed by now");
        }
        String os = System.getProperty("os.name");
        if (os.startsWith("Windows")) {
            assertEquals(-1, process.exitValue());
        } else {
            assertEquals(255, process.exitValue());
        }
    }

    private String getConfig(String configName) {
        try {
            return ProcessUtils.escapeCommandLine(Paths.get(TikaServerIntegrationTest.class.
                    getResource("/configs/" + configName).toURI()).toAbsolutePath().toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testStdErrOutBasic() throws Exception {
        startProcess(
                new String[]{"-config", getConfig("tika-config-server-timeout-10000.xml")});
        awaitServerStartup();

        Response response = WebClient.create(endPoint + RMETA_PATH).accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_STDOUT_STDERR));
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(1, metadataList.size());
        assertContains("quick brown fox", metadataList.get(0).get("X-TIKA:content"));
        testBaseline();

    }

    @Test
    @Disabled("This works, but prints too much junk to the console.  " +
            "Figure out how to gobble/redirect.")
    public void testStaticStdErrOutBasic() throws Exception {
        startProcess(
                new String[]{"-config", getConfig("tika-config-server-timeout-10000.xml")});
        awaitServerStartup();

        Response response = WebClient.create(endPoint + RMETA_PATH).accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_STATIC_STDOUT_STDERR));
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(1, metadataList.size());
        assertContains("quick brown fox", metadataList.get(0).get("X-TIKA:content"));
        testBaseline();

    }


    @Disabled("TODO needs to write dynamic config file w logfile location")
    @Test
    public void testStdErrOutLogging() throws Exception {
        final AtomicInteger i = new AtomicInteger();
        Thread serverThread = new Thread(() -> TikaServerCli.main(
                new String[]{
                        "-p", INTEGRATION_TEST_PORT, "-taskTimeoutMillis",
                        "10000", "-taskPulseMillis", "500", "-pingPulseMillis", "100",
                        "-maxRestarts", "0",
                        "-JDlog4j.configuration=file:" + LOG_FILE.toAbsolutePath(),
                        "-tmpFilePrefix", "tika-server-stderrlogging"
                }));
        serverThread.start();
        awaitServerStartup();

        Response response = WebClient.create(endPoint + RMETA_PATH).accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_STDOUT_STDERR));
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(1, metadataList.size());
        assertContains("quick brown fox", metadataList.get(0).get("X-TIKA:content"));

        try {
            testBaseline();
        } finally {
            serverThread.interrupt();
        }
    }


    @Test
    @Disabled("turn this into a real test")
    public void testMaxFiles() throws Exception {
        //this isn't a real regression test yet.
        //Can watch logs at least for confirmation of behavior
        //TODO: convert to real test

        startProcess(
                new String[]{"-config", getConfig("tika-config-server-timeout-10000.xml")});
        awaitServerStartup();


        Random r = new Random();
        for (int i = 0; i < 100; i++) {
            boolean ex = false;
            Response response = null;
            String file = TEST_HELLO_WORLD;
            try {
                if (r.nextFloat() < 0.01) {
                    file = TEST_SYSTEM_EXIT;
                } else if (r.nextFloat() < 0.015) {
                    file = TEST_OOM;
                } else if (r.nextFloat() < 0.02) {
                    file = TEST_HEAVY_HANG;
                }
                response = WebClient.create(endPoint + RMETA_PATH).accept("application/json")
                        .put(ClassLoader.getSystemResourceAsStream(file));
            } catch (Exception e) {
                ex = true;
            }

            if (ex || response.getStatus() != 200) {
                i--;
                awaitServerStartup();
                continue;
            }
            if (file.equals(TEST_HELLO_WORLD)) {
                Reader reader =
                        new InputStreamReader((InputStream) response.getEntity(), UTF_8);
                List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
                assertEquals(1, metadataList.size());
                assertEquals("Nikolai Lobachevsky", metadataList.get(0).get("author"));
                assertContains("hello world", metadataList.get(0).get("X-TIKA:content"));
            }
            //assertEquals("a38e6c7b38541af87148dee9634cb811",
            // metadataList.get(10).get("X-TIKA:digest:MD5"));
        }
    }

    private void testBaseline() throws Exception {
        int maxTries = 3;
        int tries = 0;
        while (++tries < maxTries) {
            awaitServerStartup();
            Response response = null;

            try {
                response = WebClient.create(endPoint + RMETA_PATH).accept("application/json")
                        .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD));
            } catch (ProcessingException e) {
                continue;
            }
            if (response.getStatus() == 503) {
                continue;
            }
            Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
            List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
            assertEquals(1, metadataList.size());
            assertEquals("Nikolai Lobachevsky", metadataList.get(0).get("author"));
            assertContains("hello world", metadataList.get(0).get("X-TIKA:content"));
            return;
        }
        fail("should have completed within 3 tries");
    }
}
