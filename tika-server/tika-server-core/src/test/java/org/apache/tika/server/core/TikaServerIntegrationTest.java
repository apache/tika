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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Permission;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

public class TikaServerIntegrationTest extends IntegrationTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(TikaServerIntegrationTest.class);


    @Test
    public void testBasic() throws Exception {

        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-maxFiles", "100",
                                "-p", INTEGRATION_TEST_PORT,
                                "-tmpFilePrefix", "basic-"
                        });
            }
        };
        serverThread.start();
        try {
            testBaseline();
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
                                "-JXmx256m",
                                "-p", INTEGRATION_TEST_PORT,
                                "-pingPulseMillis", "100",
                                "-tmpFilePrefix", "tika-server-oom"

                        });
            }
        };
        serverThread.start();
        awaitServerStartup();

        Response response = null;
        try {
            response = WebClient
                    .create(endPoint + META_PATH)
                    .accept("application/json")
                    .put(ClassLoader
                            .getSystemResourceAsStream(TEST_OOM));
        } catch (Exception e) {
            //oom may or may not cause an exception depending
            //on the timing
        }
        //give some time for the server to crash/terminate itself
        Thread.sleep(2000);
        try {
            testBaseline();
        } finally {
            serverThread.interrupt();
        }
    }

    @Test
    public void testSameServerIdAfterOOM() throws Exception {

        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-JXmx256m",
                                "-p", INTEGRATION_TEST_PORT,
                                "-pingPulseMillis", "100",
                                "-status",
                                "-tmpFilePrefix", "tika-server-oom"

                        });
            }
        };
        serverThread.start();
        awaitServerStartup();
        String serverId = getServerId();
        Response response = null;
        try {
            response = WebClient
                    .create(endPoint + META_PATH)
                    .accept("application/json")
                    .put(ClassLoader
                            .getSystemResourceAsStream(TEST_OOM));
        } catch (Exception e) {
            //oom may or may not cause an exception depending
            //on the timing
        }
        //give some time for the server to crash/terminate itself
        Thread.sleep(2000);
        try {
            testBaseline();
            assertEquals(serverId, getServerId());
            assertEquals(1, getNumRestarts());
        } finally {
            serverThread.interrupt();
        }
    }

    @Test
    public void testSameDeclaredServerIdAfterOOM() throws Exception {
        String serverId = "qwertyuiop";
        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-JXmx256m",
                                "-p", INTEGRATION_TEST_PORT,
                                "-pingPulseMillis", "100",
                                "-status",
                                "-id",
                                serverId,
                                "-tmpFilePrefix", "tika-server-oom"

                        });
            }
        };
        serverThread.start();
        awaitServerStartup();
        Response response = null;
        try {
            response = WebClient
                    .create(endPoint + META_PATH)
                    .accept("application/json")
                    .put(ClassLoader
                            .getSystemResourceAsStream(TEST_OOM));
        } catch (Exception e) {
            //oom may or may not cause an exception depending
            //on the timing
        }
        //give some time for the server to crash/terminate itself
        Thread.sleep(2000);
        try {
            testBaseline();
            assertEquals(serverId, getServerId());
        } finally {
            serverThread.interrupt();
        }
    }

    private String getServerId() throws Exception {
        Response response = WebClient
                .create(endPoint + STATUS_PATH)
                .accept("application/json")
                .get();
        String jsonString =
                CXFTestBase.getStringFromInputStream((InputStream) response.getEntity());
        JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
        return root.get("server_id").getAsJsonPrimitive().getAsString();
    }

    private int getNumRestarts() throws Exception {
        Response response = WebClient
                .create(endPoint + STATUS_PATH)
                .accept("application/json")
                .get();
        String jsonString =
                CXFTestBase.getStringFromInputStream((InputStream) response.getEntity());
        JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
        return root.get("num_restarts").getAsJsonPrimitive().getAsInt();
    }

    @Test
    public void testSystemExit() throws Exception {

        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-p", INTEGRATION_TEST_PORT,
                                "-tmpFilePrefix", "tika-server-systemexit"

                        });
            }
        };
        serverThread.start();
        awaitServerStartup();
        Response response = null;
        try {
            response = WebClient
                    .create(endPoint + META_PATH)
                    .accept("application/json")
                    .put(ClassLoader
                            .getSystemResourceAsStream(TEST_SYSTEM_EXIT));
        } catch (Exception e) {
            //sys exit causes catchable problems for the client
        }
        //give some time for the server to crash/terminate itself
        Thread.sleep(2000);
        try {
            testBaseline();
        } finally {
            serverThread.interrupt();
        }
    }

    @Test
    public void testTimeoutOk() throws Exception {
        //test that there's enough time for this file.
        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-p", INTEGRATION_TEST_PORT,
                                "-taskTimeoutMillis", "10000", "-taskPulseMillis", "500",
                                "-pingPulseMillis", "500",
                                "-tmpFilePrefix", "tika-server-timeoutok"

                        });
            }
        };
        serverThread.start();
        awaitServerStartup();
        Response response = null;
        try {
            response = WebClient
                    .create(endPoint + META_PATH)
                    .accept("application/json")
                    .put(ClassLoader
                            .getSystemResourceAsStream(TEST_HEAVY_HANG_SHORT));
        } catch (Exception e) {
            //potential exception depending on timing
        }
        try {
            testBaseline();
        } finally {
            serverThread.interrupt();
        }
    }

    @Test(timeout = 60000)
    public void testTimeout() throws Exception {

        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-p", INTEGRATION_TEST_PORT,
                                "-taskTimeoutMillis", "10000", "-taskPulseMillis", "100",
                                "-pingPulseMillis", "100",
                                "-tmpFilePrefix", "tika-server-timeout"

                        });
            }
        };
        serverThread.start();
        awaitServerStartup();
        Response response = null;
        try {
            response = WebClient
                    .create(endPoint + META_PATH)
                    .accept("application/json")
                    .put(ClassLoader
                            .getSystemResourceAsStream(TEST_HEAVY_HANG));
        } catch (Exception e) {
            //catchable exception when server shuts down.
        }
        try {
            testBaseline();
        } finally {
            serverThread.interrupt();
        }
    }

    @Test
    public void testBadJVMArgs() throws Exception {
        final AtomicInteger i = new AtomicInteger();
        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-JXms20m", "-JXmx10m",
                                "-p", INTEGRATION_TEST_PORT,
                                "-tmpFilePrefix", "tika-server-badargs"

                        });
            }
        };
        serverThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                i.set(((MyExitException) e).getStatus());
            }
        });
        serverThread.start();
        serverThread.join(30000);

        assertEquals(-1, i.get());
    }

    @Test
    public void testStdErrOutBasic() throws Exception {
        final AtomicInteger i = new AtomicInteger();
        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-p", INTEGRATION_TEST_PORT,
                                "-taskTimeoutMillis", "60000", "-taskPulseMillis", "500",
                                "-pingPulseMillis", "100",
                                "-tmpFilePrefix", "tika-server-stderr"

                        });
            }
        };
        serverThread.start();
        try {
            awaitServerStartup();

            Response response = WebClient
                    .create(endPoint + META_PATH)
                    .accept("application/json")
                    .put(ClassLoader
                            .getSystemResourceAsStream(TEST_STDOUT_STDERR));
            Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
            List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
            assertEquals(1, metadataList.size());
            assertContains("quick brown fox", metadataList.get(0).get("X-TIKA:content"));
            testBaseline();
        } finally {
            serverThread.interrupt();
        }
    }

    @Test
    @Ignore("This works, but prints too much junk to the console.  Figure out how to gobble/redirect.")
    public void testStaticStdErrOutBasic() throws Exception {
        final AtomicInteger i = new AtomicInteger();
        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-p", INTEGRATION_TEST_PORT,
                                "-taskTimeoutMillis", "60000", "-taskPulseMillis", "500",
                                "-pingPulseMillis", "100"
                        });
            }
        };
        serverThread.start();
        try {
            awaitServerStartup();

            Response response = WebClient
                    .create(endPoint + META_PATH)
                    .accept("application/json")
                    .put(ClassLoader
                            .getSystemResourceAsStream(TEST_STATIC_STDOUT_STDERR));
            Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
            List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
            assertEquals(1, metadataList.size());
            assertContains("quick brown fox", metadataList.get(0).get("X-TIKA:content"));
            testBaseline();
        } finally {
            serverThread.interrupt();
        }
    }


    @Test
    public void testStdErrOutLogging() throws Exception {
        final AtomicInteger i = new AtomicInteger();
        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-p", INTEGRATION_TEST_PORT,
                                "-taskTimeoutMillis", "10000", "-taskPulseMillis", "500",
                                "-pingPulseMillis", "100", "-maxRestarts", "0",
                                "-JDlog4j.configuration=file:" + LOG_FILE.toAbsolutePath(),
                                "-tmpFilePrefix", "tika-server-stderrlogging"
                        });
            }
        };
        serverThread.start();
        awaitServerStartup();

        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_STDOUT_STDERR));
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
    public void testEmitterSysExit() throws Exception {

    }

    private void awaitServerStartup() throws Exception {
        Instant started = Instant.now();
        long elapsed = Duration.between(started, Instant.now()).toMillis();
        WebClient client = WebClient.create(endPoint + "/tika").accept("text/plain");
        while (elapsed < MAX_WAIT_MS) {
            try {
                Response response = client.get();
                if (response.getStatus() == 200) {
                    elapsed = Duration.between(started, Instant.now()).toMillis();
                    LOG.info("client observes server successfully started after " +
                            elapsed + " ms");
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

    @Test
    @Ignore("turn this into a real test")
    public void testMaxFiles() throws Exception {
        //this isn't a real regression test yet.
        //Can watch logs at least for confirmation of behavior
        //TODO: convert to real test
        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-maxFiles", "10",
                                "-taskTimeoutMillis", "10000", "-taskPulseMillis", "500",
                                "-p", INTEGRATION_TEST_PORT
                        });
            }
        };
        serverThread.start();
        awaitServerStartup();
        Random r = new Random();
        for (int i = 0; i < 100; i++) {
            System.out.println("FILE # " + i);
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
                System.out.println("about to process: " + file);
                response = WebClient
                        .create(endPoint + META_PATH)
                        .accept("application/json")
                        .put(ClassLoader
                                .getSystemResourceAsStream(file));
            } catch (Exception e) {
                ex = true;
            }

            if (ex || response.getStatus() != 200) {
                System.out.println("restarting");
                i--;
                awaitServerStartup();
                System.out.println("done awaiting");
                continue;
            }
            if (file.equals(TEST_HELLO_WORLD)) {
                Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
                List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
                assertEquals(1, metadataList.size());
                assertEquals("Nikolai Lobachevsky", metadataList.get(0).get("author"));
                assertContains("hello world", metadataList.get(0).get("X-TIKA:content"));
            }
            //assertEquals("a38e6c7b38541af87148dee9634cb811", metadataList.get(10).get("X-TIKA:digest:MD5"));
        }
        serverThread.interrupt();

    }

    private void testBaseline() throws Exception {
        int maxTries = 3;
        int tries = 0;
        while (++tries < maxTries) {
            awaitServerStartup();
            Response response = null;

            try {
                response = WebClient
                        .create(endPoint + META_PATH)
                        .accept("application/json")
                        .put(ClassLoader
                                .getSystemResourceAsStream(TEST_HELLO_WORLD));
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
        }
    }
}
