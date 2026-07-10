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
package org.apache.tika.pipes.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.DeleteFetcherReply;
import org.apache.tika.DeleteFetcherRequest;
import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.GetFetcherConfigJsonSchemaReply;
import org.apache.tika.GetFetcherConfigJsonSchemaRequest;
import org.apache.tika.GetFetcherReply;
import org.apache.tika.GetFetcherRequest;
import org.apache.tika.ListFetchersReply;
import org.apache.tika.ListFetchersRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.SavePipesIteratorReply;
import org.apache.tika.SavePipesIteratorRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcher;
import org.apache.tika.serialization.config.JsonConfigHelper;

@ExtendWith(GrpcCleanupExtension.class)
public class TikaGrpcServerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(TikaGrpcServerTest.class);
    public static final int NUM_TEST_DOCS = 2;
    // Secure-default config: no "grpc" section, so per-request config and runtime
    // component modifications are both disabled. Used by the default-deny tests.
    static Path tikaConfig = Paths.get("target", "tika-config-" + UUID.randomUUID() + ".json");
    // Same config but with the dangerous grpc features explicitly enabled. Used by
    // the CRUD/streaming tests, which save fetchers at runtime.
    static Path tikaConfigUnlocked =
            Paths.get("target", "tika-config-unlocked-" + UUID.randomUUID() + ".json");


    @BeforeAll
    static void init() throws Exception {
        // Build the javaPath from java.home
        String javaHome = System.getProperty("java.home");
        Path javaPath = Paths.get(javaHome, "bin", "java");

        // Set up paths
        Path targetPath = Paths.get("target").toAbsolutePath();
        Path pluginsDir = targetPath.resolve("plugins");

        LOG.info("Setting javaPath to: {}", javaPath);
        LOG.info("java.home is: {}", javaHome);

        // Use JsonConfigHelper to load template and apply replacements
        Map<String, Object> replacements = new HashMap<>();
        replacements.put("JAVA_PATH", javaPath);
        replacements.put("FETCHER_BASE_PATH", targetPath);
        replacements.put("PLUGIN_ROOTS", pluginsDir);

        JsonConfigHelper.writeConfigFromResource("/tika-pipes-test-config.json",
                TikaGrpcServerTest.class, replacements, tikaConfig);

        LOG.debug("Written config to: {}", tikaConfig.toAbsolutePath());

        // Derive an "unlocked" variant that opts in to the dangerous grpc features,
        // for the tests that add/modify fetchers at runtime.
        ObjectNode root = (ObjectNode) OBJECT_MAPPER.readTree(tikaConfig.toFile());
        ObjectNode grpc = OBJECT_MAPPER.createObjectNode();
        grpc.put("allowComponentManagement", true);
        grpc.put("allowPerRequestConfig", true);
        root.set("grpc", grpc);
        FileUtils.write(tikaConfigUnlocked.toFile(),
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8);
    }

    @AfterAll
    static void clean() {
        try {
            Files.deleteIfExists(tikaConfig);
            Files.deleteIfExists(tikaConfigUnlocked);
        } catch (Exception e) {
            LOG.warn("Failed to delete {}", tikaConfig, e);
        }
    }

    static final int NUM_FETCHERS_TO_CREATE = 10;

    @Test
    public void testFetcherCrud(Resources resources) throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        Server server = InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(new TikaGrpcServerImpl(tikaConfigUnlocked.toAbsolutePath().toString()))
                .build()
                .start();
        resources.register(server, Duration.ofSeconds(10));

        ManagedChannel channel = InProcessChannelBuilder
                .forName(serverName)
                .directExecutor()
                .build();
        resources.register(channel, Duration.ofSeconds(10));
        TikaGrpc.TikaBlockingStub blockingStub = TikaGrpc.newBlockingStub(channel);

        String targetFolder = new File("target").getAbsolutePath();
        // create fetchers
        for (int i = 0; i < NUM_FETCHERS_TO_CREATE; ++i) {
            String fetcherId = createFetcherId(i);
            SaveFetcherReply reply = blockingStub.saveFetcher(SaveFetcherRequest
                    .newBuilder()
                    .setFetcherId(fetcherId)
                    .setFetcherType("file-system-fetcher")
                    .setFetcherConfigJson(OBJECT_MAPPER.writeValueAsString(ImmutableMap
                            .builder()
                            .put("basePath", targetFolder)
                            .put("extractFileSystemMetadata", true)
                            .build()))
                    .build());
            assertEquals(fetcherId, reply.getFetcherId());
        }
        // update fetchers
        for (int i = 0; i < NUM_FETCHERS_TO_CREATE; ++i) {
            String fetcherId = createFetcherId(i);
            SaveFetcherReply reply = blockingStub.saveFetcher(SaveFetcherRequest
                    .newBuilder()
                    .setFetcherId(fetcherId)
                    .setFetcherType("file-system-fetcher")
                    .setFetcherConfigJson(OBJECT_MAPPER.writeValueAsString(ImmutableMap
                            .builder()
                            .put("basePath", targetFolder)
                            .put("extractFileSystemMetadata", false)
                            .build()))
                    .build());
            assertEquals(fetcherId, reply.getFetcherId());
            GetFetcherReply getFetcherReply = blockingStub.getFetcher(GetFetcherRequest
                    .newBuilder()
                    .setFetcherId(fetcherId)
                    .build());
            assertEquals("false", getFetcherReply
                    .getParamsMap()
                    .get("extractFileSystemMetadata"));
        }

        // get fetchers
        for (int i = 0; i < NUM_FETCHERS_TO_CREATE; ++i) {
            String fetcherId = createFetcherId(i);
            GetFetcherReply getFetcherReply = blockingStub.getFetcher(GetFetcherRequest
                    .newBuilder()
                    .setFetcherId(fetcherId)
                    .build());
            assertEquals(fetcherId, getFetcherReply.getFetcherId());
            assertEquals("file-system-fetcher", getFetcherReply.getFetcherType());
        }

        // delete fetchers
        for (int i = 0; i < NUM_FETCHERS_TO_CREATE; ++i) {
            String fetcherId = createFetcherId(i);
            DeleteFetcherReply deleteFetcherReply = blockingStub.deleteFetcher(DeleteFetcherRequest
                    .newBuilder()
                    .setFetcherId(fetcherId)
                    .build());
            // Delete is now supported and should succeed
            Assertions.assertTrue(deleteFetcherReply.getSuccess());
        }
    }

    @NotNull
    private static String createFetcherId(int i) {
        return "nick" + i + ":is:cool:super/" + FileSystemFetcher.class;
    }

    @Test
    public void testComponentManagementDeniedByDefault(Resources resources) throws Exception {
        TikaGrpc.TikaBlockingStub blockingStub = startServer(resources, tikaConfig);

        String targetFolder = new File("target").getAbsolutePath();
        StatusRuntimeException saveEx = Assertions.assertThrows(StatusRuntimeException.class, () ->
                blockingStub.saveFetcher(SaveFetcherRequest
                        .newBuilder()
                        .setFetcherId(createFetcherId(0))
                        .setFetcherType("file-system-fetcher")
                        .setFetcherConfigJson(OBJECT_MAPPER.writeValueAsString(ImmutableMap
                                .builder()
                                .put("basePath", targetFolder)
                                .build()))
                        .build()));
        assertEquals(Status.Code.PERMISSION_DENIED, saveEx.getStatus().getCode());

        StatusRuntimeException deleteEx = Assertions.assertThrows(StatusRuntimeException.class, () ->
                blockingStub.deleteFetcher(DeleteFetcherRequest
                        .newBuilder()
                        .setFetcherId(createFetcherId(0))
                        .build()));
        assertEquals(Status.Code.PERMISSION_DENIED, deleteEx.getStatus().getCode());
    }

    @Test
    public void testPerRequestConfigDeniedByDefault(Resources resources) throws Exception {
        TikaGrpc.TikaBlockingStub blockingStub = startServer(resources, tikaConfig);

        // The per-request config gate fires before any fetch, so this never reaches the network.
        StatusRuntimeException ex = Assertions.assertThrows(StatusRuntimeException.class, () ->
                blockingStub.fetchAndParse(FetchAndParseRequest
                        .newBuilder()
                        .setFetcherId("httpFetcherIdHere")
                        .setFetchKey("https://example.com")
                        .setParseContextJson("{\"basic-content-handler-factory\":{\"type\":\"HTML\"}}")
                        .build()));
        assertEquals(Status.Code.PERMISSION_DENIED, ex.getStatus().getCode());
    }

    @Test
    public void testComponentManagementHidesConfigByDefault(Resources resources) throws Exception {
        // With component management off (the default), the read RPCs must return component identity
        // only -- never the stored config, which can carry secrets (passwords, access keys, ...).
        TikaGrpc.TikaBlockingStub blockingStub = startServer(resources, tikaConfig);

        GetFetcherReply reply = blockingStub.getFetcher(GetFetcherRequest
                .newBuilder()
                .setFetcherId(createFetcherId(1))
                .build());
        assertEquals(createFetcherId(1), reply.getFetcherId());
        Assertions.assertFalse(reply.getFetcherType().isEmpty(),
                "identity (type) should still be returned");
        Assertions.assertTrue(reply.getParamsMap().isEmpty(),
                "config params must NOT be returned when component management is off");

        ListFetchersReply listReply =
                blockingStub.listFetchers(ListFetchersRequest.newBuilder().build());
        Assertions.assertFalse(listReply.getGetFetcherRepliesList().isEmpty(),
                "fetchers should still be listed by identity");
        for (GetFetcherReply f : listReply.getGetFetcherRepliesList()) {
            Assertions.assertTrue(f.getParamsMap().isEmpty(),
                    "listFetchers must not leak config for " + f.getFetcherId());
        }
    }

    @Test
    public void testFetcherConfigJsonSchemaRejectsUnknownType(Resources resources)
            throws Exception {
        TikaGrpc.TikaBlockingStub blockingStub = startServer(resources, tikaConfig);
        // The schema RPC only resolves config classes from registered fetcher factories: an
        // unknown type is rejected outright, and no arbitrary class is ever loaded.
        StatusRuntimeException ex = Assertions.assertThrows(StatusRuntimeException.class, () ->
                blockingStub.getFetcherConfigJsonSchema(GetFetcherConfigJsonSchemaRequest
                        .newBuilder()
                        .setFetcherType("no-such-fetcher")
                        .build()));
        assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());

        // A registered fetcher type still yields a schema.
        GetFetcherConfigJsonSchemaReply ok = blockingStub.getFetcherConfigJsonSchema(
                GetFetcherConfigJsonSchemaRequest
                        .newBuilder()
                        .setFetcherType("file-system-fetcher")
                        .build());
        Assertions.assertFalse(ok.getFetcherConfigJsonSchema().isEmpty(),
                "a registered fetcher type should still produce a schema");
    }

    @Test
    public void testSavePipesIteratorDeniedByDefault(Resources resources) throws Exception {
        // savePipesIterator is component management: it must be denied when the grpc
        // feature is off (the secure default), just like saveFetcher.
        TikaGrpc.TikaBlockingStub blockingStub = startServer(resources, tikaConfig);
        StatusRuntimeException ex = Assertions.assertThrows(StatusRuntimeException.class, () ->
                blockingStub.savePipesIterator(SavePipesIteratorRequest
                        .newBuilder()
                        .setIteratorId("it0")
                        .setIteratorType("file-system-pipes-iterator")
                        .setIteratorConfigJson("{}")
                        .build()));
        assertEquals(Status.Code.PERMISSION_DENIED, ex.getStatus().getCode());
    }

    @Test
    public void testSavePipesIteratorValidatesType(Resources resources) throws Exception {
        // Even with component management enabled, an unknown iterator type must be rejected
        // up front (mirroring saveFetcher) rather than poisoning the shared ConfigStore with
        // an unvalidated entry that only fails later.
        TikaGrpc.TikaBlockingStub blockingStub = startServer(resources, tikaConfigUnlocked);

        StatusRuntimeException ex = Assertions.assertThrows(StatusRuntimeException.class, () ->
                blockingStub.savePipesIterator(SavePipesIteratorRequest
                        .newBuilder()
                        .setIteratorId("bogus")
                        .setIteratorType("no-such-iterator")
                        .setIteratorConfigJson("{}")
                        .build()));
        assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());

        // A registered iterator type still saves successfully.
        SavePipesIteratorReply reply = blockingStub.savePipesIterator(SavePipesIteratorRequest
                .newBuilder()
                .setIteratorId("fs-it")
                .setIteratorType("file-system-pipes-iterator")
                .setIteratorConfigJson("{\"basePath\":\"" + new File("target").getAbsolutePath().replace("\\", "\\\\") + "\"}")
                .build());
        assertNotNull(reply.getMessage());
    }

    private static TikaGrpc.TikaBlockingStub startServer(Resources resources, Path config)
            throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(new TikaGrpcServerImpl(config.toAbsolutePath().toString()))
                .build()
                .start();
        resources.register(server, Duration.ofSeconds(10));

        ManagedChannel channel = InProcessChannelBuilder
                .forName(serverName)
                .directExecutor()
                .build();
        resources.register(channel, Duration.ofSeconds(10));
        return TikaGrpc.newBlockingStub(channel);
    }

    @Test
    public void testBiStream(Resources resources) throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        TikaGrpcServerImpl tikaGrpcServerImpl = new TikaGrpcServerImpl(tikaConfigUnlocked.toAbsolutePath().toString());
        Server server = InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(tikaGrpcServerImpl)
                .build()
                .start();
        resources.register(server, Duration.ofSeconds(10));

        ManagedChannel channel = InProcessChannelBuilder
                .forName(serverName)
                .directExecutor()
                .build();
        resources.register(channel, Duration.ofSeconds(10));
        TikaGrpc.TikaBlockingStub blockingStub = TikaGrpc.newBlockingStub(channel);
        TikaGrpc.TikaStub tikaStub = TikaGrpc.newStub(channel);

        String fetcherId = createFetcherId(1);
        String targetFolder = new File("target").getAbsolutePath();
        SaveFetcherReply reply = blockingStub.saveFetcher(SaveFetcherRequest
                .newBuilder()
                .setFetcherId(fetcherId)
                .setFetcherType("file-system-fetcher")
                .setFetcherConfigJson(OBJECT_MAPPER.writeValueAsString(ImmutableMap
                        .builder()
                        .put("basePath", targetFolder)
                        .put("extractFileSystemMetadata", true)
                        .build()))
                .build());

        assertEquals(fetcherId, reply.getFetcherId());

        List<FetchAndParseReply> successes = Collections.synchronizedList(new ArrayList<>());
        List<FetchAndParseReply> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean finished = new AtomicBoolean(false);

        StreamObserver<FetchAndParseReply> replyStreamObserver = new StreamObserver<>() {
            @Override
            public void onNext(FetchAndParseReply fetchAndParseReply) {
                LOG.debug("Fetched {} with document present={}",
                        fetchAndParseReply.getFetchKey(), fetchAndParseReply.hasDocument());
                if (PipesResult.RESULT_STATUS.FETCH_EXCEPTION.name().equals(
                        fetchAndParseReply.getDocument().getStatus().getPipesStatus())) {
                    errors.add(fetchAndParseReply);
                } else {
                    successes.add(fetchAndParseReply);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                fail(throwable);
            }

            @Override
            public void onCompleted() {
                LOG.info("Stream completed");
                finished.set(true);
            }
        };

        StreamObserver<FetchAndParseRequest> requestStreamObserver = tikaStub.fetchAndParseBiDirectionalStreaming(replyStreamObserver);

        File testDocumentFolder = new File("target/" + DateTimeFormatter
                .ofPattern("yyyy_MM_dd_HH_mm_ssSSS", Locale.getDefault())
                .format(LocalDateTime.now(ZoneId.systemDefault())) + "-" + UUID.randomUUID());
        assertTrue(testDocumentFolder.mkdir());
        try {
            for (int i = 0; i < NUM_TEST_DOCS; ++i) {
                File testFile = new File(testDocumentFolder, "test-" + i + ".html");
                FileUtils.writeStringToFile(testFile, "<html><body>test " + i + "</body></html>", StandardCharsets.UTF_8);
            }
            File[] testDocuments = testDocumentFolder.listFiles();
            assertNotNull(testDocuments);
            for (File testDocument : testDocuments) {
                requestStreamObserver.onNext(FetchAndParseRequest
                        .newBuilder()
                        .setFetcherId(fetcherId)
                        .setFetchKey(testDocument.getAbsolutePath())
                        .build());
            }
            // Now test error condition
            requestStreamObserver.onNext(FetchAndParseRequest
                    .newBuilder()
                    .setFetcherId(fetcherId)
                    .setFetchKey("does not exist")
                    .build());
            requestStreamObserver.onCompleted();
            
            // Wait a bit for async processing to complete
            Thread.sleep(1000);
            
            // Log what we got for debugging
            LOG.info("Successes: {}, Errors: {}", successes.size(), errors.size());
            for (FetchAndParseReply success : successes) {
                LOG.info("Success: {} - status: {}", success.getFetchKey(),
                    success.getDocument().getStatus().getPipesStatus());
            }
            for (FetchAndParseReply error : errors) {
                LOG.info("Error: {} - status: {}", error.getFetchKey(),
                    error.getDocument().getStatus().getPipesStatus());
            }
            
            assertEquals(NUM_TEST_DOCS, successes.size());
            assertEquals(1, errors.size());
            assertTrue(finished.get());

            tikaGrpcServerImpl.shutdown();
            server.shutdown();
            tikaGrpcServerImpl.postShutdown();
        } finally {
            FileUtils.deleteDirectory(testDocumentFolder);
        }
    }
}
