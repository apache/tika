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

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.tika.TikaTest;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

public class TikaServerIntegrationTest extends TikaTest {

    private static final String TEST_RECURSIVE_DOC = "test_recursive_embedded.docx";
    private static final String TEST_OOM = "mock/real_oom.xml";
    private static final String TEST_SYSTEM_EXIT = "mock/system_exit.xml";
    private static final String TEST_HEAVY_HANG = "mock/heavy_hang_30000.xml";
    private static final String TEST_HEAVY_HANG_SHORT = "mock/heavy_hang_100.xml";
    private static final String META_PATH = "/rmeta";

    //running into conflicts on 9998 with the CXFTestBase tests
    //TODO: figure out why?!
    private static final String INTEGRATION_TEST_PORT = "9999";

    protected static final String endPoint =
            "http://localhost:" + INTEGRATION_TEST_PORT;

    @Test
    public void testBasic() throws Exception {

        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-spawnChild",
                                "-p", INTEGRATION_TEST_PORT
                        });
            }
        };
        serverThread.start();
        awaitServerStartup();

        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        assertEquals("Microsoft Office Word", metadataList.get(0).get(OfficeOpenXMLExtended.APPLICATION));
        assertContains("plundered our seas", metadataList.get(6).get("X-TIKA:content"));

        //assertEquals("a38e6c7b38541af87148dee9634cb811", metadataList.get(10).get("X-TIKA:digest:MD5"));

        serverThread.interrupt();


    }

    @Test
    public void testOOM() throws Exception {

        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-spawnChild", "-JXmx512m",
                                "-p", INTEGRATION_TEST_PORT
                        });
            }
        };
        serverThread.start();
        awaitServerStartup();
        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_OOM));
        //give some time for the server to crash/kill itself
        Thread.sleep(2000);
        awaitServerStartup();

        response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        assertEquals("Microsoft Office Word", metadataList.get(0).get(OfficeOpenXMLExtended.APPLICATION));
        assertContains("plundered our seas", metadataList.get(6).get("X-TIKA:content"));

        serverThread.interrupt();
    }

    @Test
    public void testSystemExit() throws Exception {

        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-spawnChild",
                                "-p", INTEGRATION_TEST_PORT
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
        //give some time for the server to crash/kill itself
        Thread.sleep(2000);

        awaitServerStartup();

        response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        assertEquals("Microsoft Office Word", metadataList.get(0).get(OfficeOpenXMLExtended.APPLICATION));
        assertContains("plundered our seas", metadataList.get(6).get("X-TIKA:content"));

        serverThread.interrupt();


    }

    @Test
    public void testTimeoutOk() throws Exception {
        //test that there's enough time for this file.
        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-spawnChild", "-p", INTEGRATION_TEST_PORT,
                                "-taskTimeoutMillis", "10000", "-taskPulseMillis", "500",
                                "-pingPulseMillis", "500"
                        });
            }
        };
        serverThread.start();
        awaitServerStartup();
        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_HEAVY_HANG_SHORT));
        awaitServerStartup();

        response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        assertEquals("Microsoft Office Word", metadataList.get(0).get(OfficeOpenXMLExtended.APPLICATION));
        assertContains("plundered our seas", metadataList.get(6).get("X-TIKA:content"));

        serverThread.interrupt();


    }

    @Test
    public void testTimeout() throws Exception {

        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(
                        new String[]{
                                "-spawnChild", "-p", INTEGRATION_TEST_PORT,
                                "-taskTimeoutMillis", "10000", "-taskPulseMillis", "500",
                                "-pingPulseMillis", "500"
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
        awaitServerStartup();

        response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        assertEquals("Microsoft Office Word", metadataList.get(0).get(OfficeOpenXMLExtended.APPLICATION));
        assertContains("plundered our seas", metadataList.get(6).get("X-TIKA:content"));

        serverThread.interrupt();


    }

    private void awaitServerStartup() throws Exception {

        Instant started = Instant.now();
        long elapsed = Duration.between(started, Instant.now()).toMillis();
        while (elapsed < 30000) {
            try {
                Response response = WebClient
                        .create(endPoint + "/tika")
                        .accept("text/plain")
                        .get();
                if (response.getStatus() == 200) {
                    return;
                }
            } catch (javax.ws.rs.ProcessingException e) {
            }
            Thread.sleep(1000);
            elapsed = Duration.between(started, Instant.now()).toMillis();
        }

    }
}
