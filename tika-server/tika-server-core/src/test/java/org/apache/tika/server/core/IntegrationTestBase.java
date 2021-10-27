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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.TikaTest;

public class IntegrationTestBase extends TikaTest {

    static final String TEST_HELLO_WORLD = "test-documents/mock/hello_world.xml";
    static final String TEST_OOM = "test-documents/mock/fake_oom.xml";
    static final String TEST_SYSTEM_EXIT = "test-documents/mock/system_exit.xml";
    static final String TEST_HEAVY_HANG = "test-documents/mock/heavy_hang_30000.xml";
    static final String TEST_HEAVY_HANG_SHORT = "test-documents/mock/heavy_hang_100.xml";
    static final String TEST_STDOUT_STDERR = "test-documents/mock/testStdOutErr.xml";
    static final String TEST_STATIC_STDOUT_STDERR = "test-documents/mock/testStaticStdOutErr.xml";
    static final String RMETA_PATH = "/rmeta";
    static final String STATUS_PATH = "/status";

    static final long MAX_WAIT_MS = 60000;
    //running into conflicts on 9998 with the CXFTestBase tests
    //TODO: figure out why?!
    static final String INTEGRATION_TEST_PORT = "9999";
    protected static final String endPoint = "http://localhost:" + INTEGRATION_TEST_PORT;
    private static final Logger LOG = LoggerFactory.getLogger(IntegrationTestBase.class);
    static Path LOG_FILE;
    static Path STREAMS_DIR;
    private SecurityManager existingSecurityManager = null;
    protected Process process = null;

    @BeforeAll
    public static void staticSetup() throws Exception {
        LogUtils.setLoggerClass(NullWebClientLogger.class);
        LOG_FILE = Files.createTempFile("tika-server-integration", ".xml");
        Files.copy(
                TikaServerIntegrationTest.class.getResourceAsStream("/logging/log4j2_forked.xml"),
                LOG_FILE, StandardCopyOption.REPLACE_EXISTING);
        STREAMS_DIR = Files.createTempDirectory("tika-server-integration");
    }

    @AfterAll
    public static void staticTearDown() throws Exception {
        Files.delete(LOG_FILE);
        FileUtils.deleteDirectory(STREAMS_DIR.toFile());
    }

    @BeforeEach
    public void setUp() throws Exception {
        existingSecurityManager = System.getSecurityManager();
/*        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkExit(int status) {
                super.checkExit(status);
                throw new MyExitException(status);
            }
            @Override
            public void checkPermission(Permission perm) {
                // all ok
            }
            @Override
            public void checkPermission(Permission perm, Object context) {
                // all ok
            }
        });*/
    }

    @AfterEach
    public void tearDown() throws Exception {
        System.setSecurityManager(existingSecurityManager);
        if (process != null) {
            process.destroyForcibly();
            process.waitFor(30, TimeUnit.SECONDS);
            if (process.isAlive()) {
                throw new RuntimeException("process still alive!");
            }
        }
    }

    public void startProcess(String[] extraArgs) throws IOException {
        String[] base = new String[]{"java", "-cp", System.getProperty("java.class.path"),
                "org.apache.tika.server.core.TikaServerCli",};
        List<String> args = new ArrayList<>(Arrays.asList(base));
        args.addAll(Arrays.asList(extraArgs));
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.inheritIO();
//        pb.redirectInput(Files.createTempFile(STREAMS_DIR, "tika-stream-out", ".log").toFile());
        //      pb.redirectError(Files.createTempFile(STREAMS_DIR,
        //      "tika-stream-err", ".log").toFile());
        process = pb.start();
    }

    void awaitServerStartup() throws Exception {
        Instant started = Instant.now();
        long elapsed = Duration.between(started, Instant.now()).toMillis();
        WebClient client = WebClient.create(endPoint + "/").accept("text/html");
        while (elapsed < MAX_WAIT_MS) {
            try {
                Response response = client.get();
                if (response.getStatus() == 200) {
                    elapsed = Duration.between(started, Instant.now()).toMillis();
                    LOG.info(
                            "client observes server successfully started after " + elapsed + " ms");
                    return;
                }
                LOG.debug("tika test client failed to connect to server with status: {}",
                        response.getStatus());

            } catch (javax.ws.rs.ProcessingException e) {
                LOG.debug("tika test client failed to connect to server", e);
            }

            Thread.sleep(1000);
            elapsed = Duration.between(started, Instant.now()).toMillis();
        }
        throw new TimeoutException("couldn't connect to server after " + elapsed + " ms");
    }

    static class MyExitException extends RuntimeException {
        private final int status;

        MyExitException(int status) {
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

}
