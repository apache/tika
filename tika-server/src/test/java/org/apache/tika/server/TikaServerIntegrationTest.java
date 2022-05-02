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
package org.apache.tika.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataList;

import org.junit.Ignore;
import org.junit.Test;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TikaServerIntegrationTest extends IntegrationTestBase {

    @Test
    public void testBasic() throws Exception {
        String[] args = new String[]{
                "-maxFiles", "2000",
                "-taskPulseMillis", "100",
                "-pingPulseMillis", "50",
                "-spawnChild",
                "-p", INTEGRATION_TEST_PORT,
                "-tmpFilePrefix", "basic-"
        };
        startProcess(args);
        testBaseline();
    }

    @Test
    public void testOOM() throws Exception {
        String[] args = new String[]{"-spawnChild", "-JXmx256m",
                "-p", INTEGRATION_TEST_PORT,
                "-taskPulseMillis", "100",
                "-pingPulseMillis", "50",
                "-tmpFilePrefix", "tika-server-oom"};

        startProcess(args);

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
        String[] args = new String[]{ "-spawnChild", "-JXmx256m",
                "-p", INTEGRATION_TEST_PORT,
                "-taskPulseMillis", "100",
                "-pingPulseMillis", "50",
                "--status",
                "-tmpFilePrefix", "tika-server-oom"};
        startProcess(args);
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
    public void testSameDeclaredServerIdAfterOOM() throws Exception {
        String serverId = "qwertyuiop";
        String[] args = new String[]{
                "-spawnChild", "-JXmx256m",
                "-p", INTEGRATION_TEST_PORT,
                "-taskPulseMillis", "100",
                "-pingPulseMillis", "50",
                "--status",
                "--id",
                serverId,
                "-tmpFilePrefix", "tika-server-oom"
        };
        startProcess(args);
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
        String[] args = new String[]{
                "-spawnChild",
                "-p", INTEGRATION_TEST_PORT,
                "-taskPulseMillis", "100",
                "-pingPulseMillis", "50",
                "-tmpFilePrefix", "tika-server-systemexit"
        };
        startProcess(args);
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
        String[] args = new String[]{
                "-spawnChild", "-p", INTEGRATION_TEST_PORT,
                "-taskTimeoutMillis", "10000", "-taskPulseMillis", "100",
                "-pingPulseMillis", "50",
                "-tmpFilePrefix", "tika-server-timeoutok"
        };
        startProcess(args);
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

    @Test(timeout = 60000)
    public void testTimeout() throws Exception {
        String[] args = new String[]{
                "-spawnChild",
                "-p", INTEGRATION_TEST_PORT,
                "-taskTimeoutMillis", "10000", "-taskPulseMillis", "100",
                "-pingPulseMillis", "50",
                "-tmpFilePrefix", "tika-server-timeout"
        };
        startProcess(args);
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
        String[] args = new String[]{
                "-spawnChild", "-JXms20m", "-JXmx10m",
                "-p", INTEGRATION_TEST_PORT,
                "-taskPulseMillis", "100",
                "-pingPulseMillis", "50",
                "-tmpFilePrefix", "tika-server-badargs"};
        startProcess(args);
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

    @Test
    public void testStdErrOutBasic() throws Exception {
        String[] args = new String[]{
                "-spawnChild",
                "-p", INTEGRATION_TEST_PORT,
                "-taskTimeoutMillis", "60000", "-taskPulseMillis", "100",
                "-pingPulseMillis", "50",
                "-tmpFilePrefix", "tika-server-stderr"

        };
        startProcess(args);
        awaitServerStartup();

        Response response = WebClient.create(endPoint + RMETA_PATH).accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_STDOUT_STDERR));
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(1, metadataList.size());
        assertContains("quick brown fox", metadataList.get(0).get("X-TIKA:content"));
        testBaseline();

    }

    @Ignore("TODO: modernize this test so that it works with processes")
    @Test
    public void testStdErrOutLogging() throws Exception {
        String[] args = new String[]{ "-spawnChild",
        "-p", INTEGRATION_TEST_PORT,
                "-taskTimeoutMillis", "10000", "-taskPulseMillis", "100",
                "-pingPulseMillis", "50", "-maxRestarts", "0",
                "-JDlog4j.configurationFile="+ LOG_FILE.toAbsolutePath(),
                "-tmpFilePrefix", "tika-server-stderrlogging"};
        startProcess(args);
        awaitServerStartup();

        Response response = WebClient
                .create(endPoint + RMETA_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_STDOUT_STDERR));
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(1, metadataList.size());
        assertContains("quick brown fox", metadataList.get(0).get("X-TIKA:content"));
        testBaseline();
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
