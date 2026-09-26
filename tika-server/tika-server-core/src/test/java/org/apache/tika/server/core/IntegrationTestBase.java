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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.utils.ProcessUtils;

// PER_CLASS so subclasses' state (notably TEMP_WORKING_DIR below) is isolated
// per test class instead of shared via one static field on this common base --
// a prerequisite for running subclasses' test classes concurrently.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IntegrationTestBase extends TikaTest {

    static final String TEST_HELLO_WORLD = "test-documents/mock/hello_world.xml";
    static final String TEST_OOM = "test-documents/mock/fake_oom.xml";
    static final String TEST_SYSTEM_EXIT = "test-documents/mock/system_exit.xml";
    static final String TEST_HEAVY_HANG = "test-documents/mock/heavy_hang_30000.xml";
    static final String TEST_STDOUT_STDERR = "test-documents/mock/testStdOutErr.xml";
    static final String RMETA_PATH = "/rmeta";
    static final String STATUS_PATH = "/status";

    static final long MAX_WAIT_MS = 60000;
    private static final Logger LOG = LoggerFactory.getLogger(IntegrationTestBase.class);

    // Instance (not static) so each test method -- JUnit5 creates a fresh test
    // instance per @Test method by default -- gets its own port. These used to be
    // `static final`, computed once for the whole JVM fork and shared by every
    // subclass; harmless serially, but a guaranteed bind collision if two
    // subclasses' tests ever ran concurrently in the same fork.
    final int integrationTestPort = TestPortAllocator.findFreePort();
    final String INTEGRATION_TEST_PORT = String.valueOf(integrationTestPort);
    protected final String endPoint = "http://localhost:" + INTEGRATION_TEST_PORT;

    @TempDir
    Path TEMP_WORKING_DIR;
    protected Process process = null;
    private boolean classScoped = false;

    @BeforeAll
    public void setUp() throws Exception {
        LogUtils.setLoggerClass(NullWebClientLogger.class);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (!classScoped) {
            stopProcess();
        }
    }

    @AfterAll
    public void tearDownClass() throws Exception {
        if (classScoped) {
            stopProcess();
        }
    }

    private void stopProcess() throws Exception {
        if (process != null) {
            LOG.info("Trying graceful shutdown; supported? {}",
                    process.toHandle().supportsNormalTermination());
            // Try graceful shutdown first (SIGTERM) to allow shutdown hooks to run
            process.destroy();
            boolean exited = process.waitFor(5, TimeUnit.SECONDS);
            LOG.info("Graceful shutdown succeeded: {}", exited);
            if (!exited) {
                // Fall back to forceful shutdown (SIGKILL)
                LOG.warn("Fall back to forceful shutdown");
                process.destroyForcibly();
                process.waitFor(30, TimeUnit.SECONDS);
            }
            if (process.isAlive()) {
                throw new RuntimeException("process still alive!");
            }
            // DIAGNOSTIC (TIKA-4740): on Windows, Process.destroy() is the same
            // as destroyForcibly() (supportsNormalTermination() is false), so
            // tika-server's JVM shutdown hooks never run. That means
            // PipesParser.close() never fires, and the forked PipesServer
            // children become orphans. They eventually self-terminate when
            // their socket to the parent breaks, but the timing is racy --
            // if @TempDir cleanup runs before they exit, they're still
            // holding the redirect log files open and the test fails.
            //
            // We give the OS a moment for the kill to propagate, then list
            // any PipesServer JVMs still alive. Anything that shows up here
            // is an orphan and explains downstream @TempDir cleanup failures.
            logOrphanPipesServers();
        }
    }

    private static void logOrphanPipesServers() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        long count = ProcessHandle.allProcesses()
                .filter(p -> p.info().commandLine()
                        .map(c -> c.contains("org.apache.tika.pipes.core.server.PipesServer"))
                        .orElse(false))
                .peek(p -> LOG.warn(
                        "ORPHAN PipesServer alive after tika-server exit: pid={} cmd={}",
                        p.pid(), p.info().commandLine().orElse("?")))
                .count();
        LOG.info("post-teardown orphan PipesServer count: {}", count);
    }

    /**
     * One server for every test in the class, stopped after the last one. Call from a
     * non-static {@code @BeforeAll}; the instance {@code @TempDir} isn't injected yet there,
     * so the caller supplies the working dir.
     */
    public void startClassProcess(String[] extraArgs, Path workingDir) throws Exception {
        classScoped = true;
        process = start(extraArgs, workingDir);
        awaitServerStartup();
    }

    public void startProcess(String[] extraArgs) throws IOException {
        process = start(extraArgs, TEMP_WORKING_DIR);
    }

    private Process start(String[] extraArgs, Path workingDir) throws IOException {
        String[] base = new String[]{"java",
                "-Djava.io.tmpdir=" + workingDir.toAbsolutePath(), // make sure we're using subdir cleaned up by JUnit
                "-cp", System.getProperty("java.class.path"), "org.apache.tika.server.core.TikaServerCli",
                "-p", INTEGRATION_TEST_PORT};
        List<String> args = new ArrayList<>(Arrays.asList(base));
        args.addAll(Arrays.asList(extraArgs));
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.inheritIO();
//        pb.redirectInput(Files.createTempFile(STREAMS_DIR, "tika-stream-out", ".log").toFile());
        //      pb.redirectError(Files.createTempFile(STREAMS_DIR,
        //      "tika-stream-err", ".log").toFile());
        return pb.start();
    }

    static String getConfig(String configName) {
        try {
            return ProcessUtils.escapeCommandLine(Paths
                    .get(IntegrationTestBase.class
                            .getResource("/configs/" + configName)
                            .toURI())
                    .toAbsolutePath()
                    .toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /** Parses hello_world through /rmeta, retrying while the server or its worker warms up. */
    void testBaseline() throws Exception {
        int maxTries = 3;
        int tries = 0;
        while (++tries < maxTries) {
            awaitServerStartup();
            Response response;
            try {
                response = WebClient
                        .create(endPoint + RMETA_PATH)
                        .accept("application/json")
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
            assertContains("hello world", metadataList.get(0).get("tk:content"));
            return;
        }
        fail("should have completed within 3 tries");
    }

    void awaitServerStartup() throws Exception {
        WebClient client = WebClient
                .create(endPoint + "/")
                .accept("text/html");
        awaitServerStartup(client);

    }

    void awaitServerStartup(WebClient client) throws Exception {
        Instant started = Instant.now();
        long elapsed = Duration
                .between(started, Instant.now())
                .toMillis();
        while (elapsed < MAX_WAIT_MS) {
            try {
                Response response = client.get();
                if (response.getStatus() == 200) {
                    elapsed = Duration
                            .between(started, Instant.now())
                            .toMillis();
                    LOG.info("client observes server successfully started after " + elapsed + " ms");
                    return;
                }
                LOG.debug("tika test client failed to connect to server with status: {}", response.getStatus());

            } catch (jakarta.ws.rs.ProcessingException e) {
                LOG.debug("tika test client failed to connect to server", e);
            }

            Thread.sleep(1000);
            elapsed = Duration
                    .between(started, Instant.now())
                    .toMillis();
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
