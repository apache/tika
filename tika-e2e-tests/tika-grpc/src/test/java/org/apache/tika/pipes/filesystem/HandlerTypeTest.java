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
package org.apache.tika.pipes.filesystem;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.grpc.v1.Document;
import org.apache.tika.grpc.v1.FormatCategory;
import org.apache.tika.grpc.v1.MetadataField;
import org.apache.tika.grpc.v1.MetadataValue;
import org.apache.tika.grpc.v1.ParseStatus;
import org.apache.tika.pipes.ExternalTestBase;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcherConfig;

/**
 * End-to-end tests against a live tika-grpc server (real gRPC wire serialization, real
 * forked PipesServer, real fetcher plumbing).
 *
 * Covers per-request ParseContext configuration via
 * FetchAndParseRequest.parse_context_json (e.g.
 * {"basic-content-handler-factory": {"type": "HTML"}}), and the typed
 * FetchAndParseReply.document contract: DocumentMetadata typed fields, the tagged
 * `extra` tail (typed where Tika declares a type), the structured `blocks` tree,
 * `format_category`, and `embedded` recursion for container formats.
 *
 * Uses the Ignite ConfigStore so that fetchers registered via saveFetcher are visible
 * to both the gRPC server JVM and the forked PipesServer JVM.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("E2ETest")
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "exec:exec classpath exceeds Windows CreateProcess command-line length limit")
class HandlerTypeTest {

    private static final Logger LOG = LoggerFactory.getLogger(HandlerTypeTest.class);
    private static final File TEST_FOLDER = ExternalTestBase.TEST_FOLDER;
    private static final int GRPC_PORT = Integer.parseInt(System.getProperty("tika.e2e.grpcPort", "50052"));

    private static Process localGrpcProcess;

    @BeforeAll
    void setup() throws Exception {
        try {
            killProcessOnPort(GRPC_PORT);
            killProcessOnPort(3344);
            killProcessOnPort(10800);
        } catch (Exception e) {
            LOG.debug("No orphaned processes to clean up: {}", e.getMessage());
        }

        ExternalTestBase.copyTestFixtures();
        startLocalGrpcServer();
    }

    @AfterAll
    void teardown() {
        if (localGrpcProcess != null) {
            LOG.info("Stopping local gRPC server and child processes");
            localGrpcProcess.destroy();
            try {
                if (!localGrpcProcess.waitFor(10, TimeUnit.SECONDS)) {
                    localGrpcProcess.destroyForcibly();
                    localGrpcProcess.waitFor(5, TimeUnit.SECONDS);
                }
                Thread.sleep(2000);
                killProcessOnPort(GRPC_PORT);
                killProcessOnPort(3344);
                killProcessOnPort(10800);
            } catch (Exception e) {
                LOG.debug("Error during teardown: {}", e.getMessage());
            }
            LOG.info("Local gRPC server stopped");
        }
    }

    private static void startLocalGrpcServer() throws Exception {
        LOG.info("Starting local tika-grpc server with Ignite config for HandlerType test");

        Path currentDir = Path.of("").toAbsolutePath();
        Path tikaRootDir = currentDir;
        while (tikaRootDir != null &&
               !(Files.exists(tikaRootDir.resolve("tika-grpc")) &&
                 Files.exists(tikaRootDir.resolve("tika-e2e-tests")))) {
            tikaRootDir = tikaRootDir.getParent();
        }
        if (tikaRootDir == null) {
            throw new IllegalStateException("Cannot find tika root directory. Current dir: " + currentDir);
        }

        Path tikaGrpcDir = tikaRootDir.resolve("tika-grpc");
        Path configFile = Path.of("src/test/resources/tika-config-ignite-handlertype.json").toAbsolutePath();
        if (!Files.exists(configFile)) {
            throw new IllegalStateException("Config file not found: " + configFile);
        }

        LOG.info("tika-grpc dir: {}", tikaGrpcDir);
        LOG.info("Config file: {}", configFile);

        String javaHome = System.getProperty("java.home");
        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        String javaCmd = javaHome + (isWindows ? "\\bin\\java.exe" : "/bin/java");
        String mvnCmd = tikaRootDir.resolve(isWindows ? "mvnw.cmd" : "mvnw").toString();

        ProcessBuilder pb = new ProcessBuilder(
            mvnCmd,
            "exec:exec",
            "-Dexec.executable=" + javaCmd,
            "-Dexec.args=" +
                "--add-opens=java.base/java.lang=ALL-UNNAMED " +
                "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED " +
                "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED " +
                "--add-opens=java.base/java.io=ALL-UNNAMED " +
                "--add-opens=java.base/java.nio=ALL-UNNAMED " +
                "--add-opens=java.base/java.math=ALL-UNNAMED " +
                "--add-opens=java.base/java.util=ALL-UNNAMED " +
                "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED " +
                "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED " +
                "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED " +
                "--add-opens=java.base/java.time=ALL-UNNAMED " +
                "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED " +
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED " +
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED " +
                "--add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED " +
                "--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED " +
                "-Dio.netty.tryReflectionSetAccessible=true " +
                "-Dignite.work.dir=\"" + tikaGrpcDir.resolve("target/ignite-work-handlertype") + "\" " +
                "-classpath %classpath " +
                "org.apache.tika.pipes.grpc.TikaGrpcServer " +
                "-c \"" + configFile + "\" " +
                "-p " + GRPC_PORT
        );

        pb.directory(tikaGrpcDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);

        localGrpcProcess = pb.start();

        final boolean[] igniteStarted = {false};
        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(localGrpcProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.info("tika-grpc: {}", line);
                    if (line.contains("Ignite server started") ||
                        (line.contains("Table") && line.contains("created successfully")) ||
                        line.contains("Server started, listening on")) {
                        synchronized (igniteStarted) {
                            igniteStarted[0] = true;
                            igniteStarted.notifyAll();
                        }
                    }
                }
            } catch (IOException e) {
                LOG.error("Error reading server output", e);
            }
        });
        logThread.setDaemon(true);
        logThread.start();

        try {
            Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(180))
                .pollInterval(java.time.Duration.ofSeconds(2))
                .until(() -> {
                    synchronized (igniteStarted) {
                        if (!igniteStarted[0]) {
                            return false;
                        }
                    }
                    ManagedChannel testChannel = ManagedChannelBuilder
                        .forAddress("localhost", GRPC_PORT)
                        .usePlaintext()
                        .build();
                    try {
                        io.grpc.health.v1.HealthGrpc.HealthBlockingStub healthStub =
                            io.grpc.health.v1.HealthGrpc.newBlockingStub(testChannel)
                                .withDeadlineAfter(2, TimeUnit.SECONDS);
                        io.grpc.health.v1.HealthCheckResponse response = healthStub.check(
                            io.grpc.health.v1.HealthCheckRequest.getDefaultInstance());
                        return response.getStatus() ==
                            io.grpc.health.v1.HealthCheckResponse.ServingStatus.SERVING;
                    } catch (io.grpc.StatusRuntimeException e) {
                        if (e.getStatus().getCode() == io.grpc.Status.Code.UNIMPLEMENTED) {
                            return true;
                        }
                        return false;
                    } catch (Exception e) {
                        return false;
                    } finally {
                        testChannel.shutdown();
                        testChannel.awaitTermination(1, TimeUnit.SECONDS);
                    }
                });
        } catch (org.awaitility.core.ConditionTimeoutException e) {
            if (localGrpcProcess.isAlive()) {
                localGrpcProcess.destroyForcibly();
            }
            throw new RuntimeException("tika-grpc server with Ignite failed to start within timeout", e);
        }

        LOG.info("HandlerType test server ready on port {}", GRPC_PORT);
    }

    private ManagedChannel getManagedChannel() {
        return ManagedChannelBuilder
            .forAddress("localhost", GRPC_PORT)
            .usePlaintext()
            .maxInboundMessageSize(160 * 1024 * 1024)
            .build();
    }

    private static void killProcessOnPort(int port) throws IOException, InterruptedException {
        ProcessBuilder findPb = new ProcessBuilder("lsof", "-ti", ":" + port);
        findPb.redirectErrorStream(true);
        Process findProcess = findPb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(findProcess.getInputStream(), StandardCharsets.UTF_8))) {
            String pidStr = reader.readLine();
            if (pidStr != null && !pidStr.trim().isEmpty()) {
                long pid = Long.parseLong(pidStr.trim());
                long myPid = ProcessHandle.current().pid();
                if (pid == myPid || isParentProcess(pid)) {
                    return;
                }
                String cmdLine = ProcessHandle.of(pid)
                    .flatMap(h -> h.info().commandLine())
                    .orElse("");
                if (!cmdLine.contains("tika") && !cmdLine.contains("TikaGrpc") && !cmdLine.contains("ignite")) {
                    LOG.debug("Skipping kill of PID {} on port {} — not a tika/ignite process", pid, port);
                    return;
                }
                LOG.info("Killing tika/ignite process {} on port {}", pid, port);
                new ProcessBuilder("kill", String.valueOf(pid)).start().waitFor(2, TimeUnit.SECONDS);
                Thread.sleep(1000);
                new ProcessBuilder("kill", "-9", String.valueOf(pid)).start().waitFor(2, TimeUnit.SECONDS);
            }
        }
        findProcess.waitFor(2, TimeUnit.SECONDS);
    }

    private static boolean isParentProcess(long pid) {
        try {
            ProcessHandle current = ProcessHandle.current();
            while (current.parent().isPresent()) {
                current = current.parent().get();
                if (current.pid() == pid) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.debug("Error checking parent process", e);
        }
        return false;
    }

    private void registerFileSystemFetcher(TikaGrpc.TikaBlockingStub blockingStub, String fetcherId)
            throws Exception {
        FileSystemFetcherConfig config = new FileSystemFetcherConfig();
        config.setBasePath(TEST_FOLDER.getAbsolutePath());

        SaveFetcherReply saveReply = blockingStub.saveFetcher(SaveFetcherRequest.newBuilder()
                .setFetcherId(fetcherId)
                .setFetcherType("file-system-fetcher")
                .setFetcherConfigJson(ExternalTestBase.OBJECT_MAPPER.writeValueAsString(config))
                .build());
        LOG.info("Fetcher created: {}", saveReply.getFetcherId());
    }

    private static MetadataField findExtra(Document document, String key) {
        return document.getExtraList().stream()
                .filter(field -> field.getKey().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "expected document.extra to contain key '" + key + "', got: "
                                + document.getExtraList().stream().map(MetadataField::getKey).toList()));
    }

    @Test
    void testParseContextJson() throws Exception {
        String fetcherId = "handlerTypeFetcher";
        ManagedChannel channel = getManagedChannel();
        try {
            TikaGrpc.TikaBlockingStub blockingStub = TikaGrpc.newBlockingStub(channel);
            registerFileSystemFetcher(blockingStub, fetcherId);

            // Parse sample.html requesting HTML output. render_markdown carries the
            // handler's flat output in Document.markdown so it can be asserted directly.
            FetchAndParseReply htmlReply = blockingStub.fetchAndParse(FetchAndParseRequest.newBuilder()
                    .setFetcherId(fetcherId)
                    .setFetchKey("sample.html")
                    .setParseContextJson("{\"basic-content-handler-factory\": {\"type\": \"HTML\"}}")
                    .setRenderMarkdown(true)
                    .build());

            LOG.info("HTML parse status: {}", htmlReply.getDocument().getStatus().getPipesStatus());
            Assertions.assertEquals("PARSE_SUCCESS", htmlReply.getDocument().getStatus().getPipesStatus(),
                    "Parse should succeed with HTML handler type");

            String htmlContent = htmlReply.getDocument().getMarkdown();
            Assertions.assertNotNull(htmlContent, "Content should be present in HTML response");
            LOG.info("HTML content (first 200 chars): {}", htmlContent.substring(0, Math.min(200, htmlContent.length())));
            Assertions.assertTrue(
                    htmlContent.contains("<html") || htmlContent.contains("<body") || htmlContent.contains("<p"),
                    "HTML handler should produce HTML markup, got: " + htmlContent);

            // Parse the same file requesting plain text — expect no HTML tags
            FetchAndParseReply textReply = blockingStub.fetchAndParse(FetchAndParseRequest.newBuilder()
                    .setFetcherId(fetcherId)
                    .setFetchKey("sample.html")
                    .setParseContextJson("{\"basic-content-handler-factory\": {\"type\": \"TEXT\"}}")
                    .setRenderMarkdown(true)
                    .build());

            LOG.info("Text parse status: {}", textReply.getDocument().getStatus().getPipesStatus());
            Assertions.assertEquals("PARSE_SUCCESS", textReply.getDocument().getStatus().getPipesStatus(),
                    "Parse should succeed with TEXT handler type");

            String textContent = textReply.getDocument().getMarkdown();
            Assertions.assertNotNull(textContent, "Content should be present in text response");
            LOG.info("Text content (first 200 chars): {}", textContent.substring(0, Math.min(200, textContent.length())));
            Assertions.assertFalse(
                    textContent.contains("<html") || textContent.contains("<body"),
                    "TEXT handler should not produce HTML tags, got: " + textContent);

            Assertions.assertNotEquals(htmlContent, textContent,
                    "HTML and TEXT outputs should differ for the same document");

        } finally {
            shutdownChannel(channel);
        }
    }

    /**
     * Exercises the typed Document contract end-to-end for a PDF: typed
     * DocumentMetadata fields, the tagged `extra` tail (a boolean-typed key and a
     * string-typed key), the structured block tree, format_category, and ParseStatus --
     * all through the live server and real gRPC wire serialization, not the in-process
     * mapper tests.
     */
    @Test
    void typedDocumentContractOverLiveServerPdf() throws Exception {
        String fetcherId = "typedContractPdfFetcher";
        ManagedChannel channel = getManagedChannel();
        try {
            TikaGrpc.TikaBlockingStub blockingStub = TikaGrpc.newBlockingStub(channel);
            registerFileSystemFetcher(blockingStub, fetcherId);

            FetchAndParseReply reply = blockingStub.fetchAndParse(FetchAndParseRequest.newBuilder()
                    .setFetcherId(fetcherId)
                    .setFetchKey("testPDF.pdf")
                    .setRenderMarkdown(true)
                    .build());

            Assertions.assertEquals("PARSE_SUCCESS", reply.getDocument().getStatus().getPipesStatus());
            Assertions.assertTrue(reply.hasDocument(), "reply should carry a typed Document");
            Document document = reply.getDocument();

            // envelope
            Assertions.assertEquals("application/pdf", document.getContentType());
            Assertions.assertEquals(FormatCategory.FORMAT_CATEGORY_PDF, document.getFormatCategory());
            Assertions.assertEquals(ParseStatus.Status.SUCCESS, document.getStatus().getStatus());

            // typed DocumentMetadata fields
            Assertions.assertEquals("Apache Tika - Apache Tika", document.getMetadata().getTitle());
            Assertions.assertEquals(java.util.List.of("Bertrand Delacr\u00e9taz"),
                    document.getMetadata().getAuthorsList());
            Assertions.assertEquals(1, document.getMetadata().getPageCount());
            Assertions.assertTrue(document.getMetadata().hasCreated(),
                    "created date should map to a typed Timestamp");

            // content: the canonical block tree, plus the flat rendering this request
            // opted into with render_markdown
            Assertions.assertFalse(document.getBlocksList().isEmpty(),
                    "structured block tree should be populated");
            Assertions.assertTrue(document.getMarkdown().contains("Tika"),
                    "requested markdown rendering should contain extracted text");

            // tagged tail: pdf:encrypted is declared boolean by Tika, so it must arrive
            // typed as a boolean over the wire, not as a string
            MetadataValue encrypted = findExtra(document, "pdf:encrypted").getValue();
            Assertions.assertEquals(MetadataValue.ValueCase.BOOLEAN, encrypted.getValueCase(),
                    "pdf:encrypted has a declared boolean type and must be tagged as one");
            Assertions.assertFalse(encrypted.getBoolean(), "testPDF.pdf is not encrypted");

            // tagged tail: a text-typed key stays a string, value intact
            MetadataValue creatorTool = findExtra(document, "xmp:CreatorTool").getValue();
            Assertions.assertEquals(MetadataValue.ValueCase.STRINGS, creatorTool.getValueCase());
            Assertions.assertEquals(java.util.List.of("Firefox"),
                    creatorTool.getStrings().getValuesList());
        } finally {
            shutdownChannel(channel);
        }
    }

    /**
     * Same contract for HTML through the live server: the typed title and a tagged tail
     * key, proving the format-specific transformer ran server-side.
     */
    @Test
    void typedDocumentContractOverLiveServerHtml() throws Exception {
        String fetcherId = "typedContractHtmlFetcher";
        ManagedChannel channel = getManagedChannel();
        try {
            TikaGrpc.TikaBlockingStub blockingStub = TikaGrpc.newBlockingStub(channel);
            registerFileSystemFetcher(blockingStub, fetcherId);

            FetchAndParseReply reply = blockingStub.fetchAndParse(FetchAndParseRequest.newBuilder()
                    .setFetcherId(fetcherId)
                    .setFetchKey("sample.html")
                    .build());

            Assertions.assertEquals("PARSE_SUCCESS", reply.getDocument().getStatus().getPipesStatus());
            Document document = reply.getDocument();

            Assertions.assertEquals(FormatCategory.FORMAT_CATEGORY_HTML, document.getFormatCategory());
            Assertions.assertEquals("Sample E2E Test Document", document.getMetadata().getTitle(),
                    "html <title> should map to the typed title field");
            // this request did not set render_markdown, so the reply carries the content
            // once: the canonical block tree, with the flat rendering left empty
            Assertions.assertFalse(document.getBlocksList().isEmpty());
            Assertions.assertTrue(document.getMarkdown().isEmpty(),
                    "markdown rendering is opt-in and was not requested");

            // tagged tail still carries the HTML-specific keys (encoding, etc.)
            MetadataValue encoding = findExtra(document, "Content-Encoding").getValue();
            Assertions.assertEquals(MetadataValue.ValueCase.STRINGS, encoding.getValueCase());
            Assertions.assertFalse(encoding.getStrings().getValuesList().isEmpty());
        } finally {
            shutdownChannel(channel);
        }
    }

    /**
     * Container formats must recurse: an Office document with embedded resources should
     * come back with each embedded child as its own fully typed Document, through the
     * live server.
     */
    @Test
    void embeddedDocumentsRecurseOverLiveServer() throws Exception {
        String fetcherId = "embeddedDocsFetcher";
        ManagedChannel channel = getManagedChannel();
        try {
            TikaGrpc.TikaBlockingStub blockingStub = TikaGrpc.newBlockingStub(channel);
            registerFileSystemFetcher(blockingStub, fetcherId);

            FetchAndParseReply reply = blockingStub.fetchAndParse(FetchAndParseRequest.newBuilder()
                    .setFetcherId(fetcherId)
                    .setFetchKey("test_recursive_embedded.docx")
                    .build());

            Assertions.assertEquals("PARSE_SUCCESS", reply.getDocument().getStatus().getPipesStatus());
            Document document = reply.getDocument();

            Assertions.assertEquals(FormatCategory.FORMAT_CATEGORY_OFFICE, document.getFormatCategory());
            // the container's own typed metadata (docProps/core.xml carries dcterms dates)
            Assertions.assertTrue(document.getMetadata().hasCreated());
            Assertions.assertTrue(document.getMetadata().hasModified());

            Assertions.assertTrue(document.getEmbeddedCount() > 0,
                    "container format should recurse into embedded children");
            for (Document child : document.getEmbeddedList()) {
                Assertions.assertFalse(child.getContentType().isEmpty(),
                        "every embedded child should carry a detected content type");
            }
            Assertions.assertTrue(
                    document.getEmbeddedList().stream()
                            .anyMatch(child -> !child.getOrigin().getFilename().isEmpty()),
                    "embedded children should carry their resource names in origin.filename");
        } finally {
            shutdownChannel(channel);
        }
    }

    private static void shutdownChannel(ManagedChannel channel) {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
