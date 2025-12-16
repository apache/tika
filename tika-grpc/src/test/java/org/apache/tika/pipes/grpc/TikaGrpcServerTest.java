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
import com.google.common.collect.ImmutableMap;
import io.grpc.ManagedChannel;
import io.grpc.Server;
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
import org.apache.tika.GetFetcherReply;
import org.apache.tika.GetFetcherRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.config.JsonConfigHelper;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcher;

@ExtendWith(GrpcCleanupExtension.class)
public class TikaGrpcServerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(TikaGrpcServerTest.class);
    public static final int NUM_TEST_DOCS = 2;
    static Path tikaConfig = Paths.get("target", "tika-config-" + UUID.randomUUID() + ".json");


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
    }

    @AfterAll
    static void clean() {
        try {
            Files.deleteIfExists(tikaConfig);
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
                .addService(new TikaGrpcServerImpl(tikaConfig.toAbsolutePath().toString()))
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
                    .setFetcherClass(FileSystemFetcher.class.getName())
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
                    .setFetcherClass(FileSystemFetcher.class.getName())
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
            assertEquals(FileSystemFetcher.class.getName(), getFetcherReply.getFetcherClass());
        }

        // delete fetchers - note: delete is not currently supported
        for (int i = 0; i < NUM_FETCHERS_TO_CREATE; ++i) {
            String fetcherId = createFetcherId(i);
            DeleteFetcherReply deleteFetcherReply = blockingStub.deleteFetcher(DeleteFetcherRequest
                    .newBuilder()
                    .setFetcherId(fetcherId)
                    .build());
            // Delete is not supported, so this will return false
            Assertions.assertFalse(deleteFetcherReply.getSuccess());
        }
    }

    @NotNull
    private static String createFetcherId(int i) {
        return "nick" + i + ":is:cool:super/" + FileSystemFetcher.class;
    }

    @Test
    public void testBiStream(Resources resources) throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        Server server = InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(new TikaGrpcServerImpl(tikaConfig.toAbsolutePath().toString()))
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
                .setFetcherClass(FileSystemFetcher.class.getName())
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
                LOG.debug("Fetched {} with metadata {}", fetchAndParseReply.getFetchKey(), fetchAndParseReply.getFieldsMap());
                if (PipesResult.RESULT_STATUS.FETCH_EXCEPTION.name().equals(fetchAndParseReply.getStatus())) {
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
                LOG.info("Success: {} - status: {}", success.getFetchKey(), success.getStatus());
            }
            for (FetchAndParseReply error : errors) {
                LOG.info("Error: {} - status: {}", error.getFetchKey(), error.getStatus());
            }
            
            assertEquals(NUM_TEST_DOCS, successes.size());
            assertEquals(1, errors.size());
            assertTrue(finished.get());
        } finally {
            FileUtils.deleteDirectory(testDocumentFolder);
        }
    }
}
