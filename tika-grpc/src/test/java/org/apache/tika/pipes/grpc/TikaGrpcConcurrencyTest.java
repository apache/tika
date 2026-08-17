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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.grpc.proto.FetchAndParseReply;
import org.apache.tika.pipes.grpc.proto.FetchAndParseRequest;
import org.apache.tika.pipes.grpc.proto.TikaGrpc;
import org.apache.tika.serialization.config.JsonConfigHelper;

/**
 * Concurrent fetchAndParse against a server built WITHOUT directExecutor(),
 * like the production server: each call runs on its own handler thread, so
 * these tests exercise the pipes layer under real handler concurrency.
 */
@ExtendWith(GrpcCleanupExtension.class)
public class TikaGrpcConcurrencyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // The fetcher must come from the config file: one saved at runtime through
    // saveFetcher is not visible to the forked worker.
    private static final String FETCHER_ID = "nick1.is.cool.super-fs";

    /**
     * All concurrent calls must parse, and each reply must carry its own
     * document. The barrier guarantees the calls overlap; the per-request
     * marker catches replies wired to the wrong request even when every
     * status says success.
     */
    @Test
    public void concurrentCallsAllParseTheirOwnDocument(Resources resources) throws Exception {
        runConcurrentBurst(resources, writeConfig(null, null, null), false);
    }

    /**
     * The same burst with pipes.useSharedServer=true. This change makes shared
     * mode reachable from tika-grpc for the first time (the old single-client
     * constructor always forced per-client mode), so prove the wiring end to
     * end: one shared worker JVM, two connections, four calls.
     */
    @Test
    public void sharedServerModeParsesConcurrently(Resources resources) throws Exception {
        runConcurrentBurst(resources, writeConfig(null, null, Boolean.TRUE), true);
    }

    private void runConcurrentBurst(Resources resources, Path config, boolean expectSharedMode)
            throws Exception {
        int concurrency = 4;
        TikaGrpcServerImpl service = new TikaGrpcServerImpl(config.toAbsolutePath().toString());
        // the burst alone can't tell the modes apart
        assertEquals(expectSharedMode, service.pipesParser.isSharedMode(),
                "pipes.useSharedServer did not take effect");
        List<File> testFiles = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            TikaGrpc.TikaBlockingStub stub = startServer(resources, service);
            warmUp(stub, testFiles);

            CyclicBarrier barrier = new CyclicBarrier(concurrency);
            List<Callable<FetchAndParseReply>> calls = new ArrayList<>();
            List<String> fetchKeys = new ArrayList<>();
            List<String> markers = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                String marker = "tika4815-marker-" + i + "-" + UUID.randomUUID();
                String fetchKey = "tika4815-doc-" + i + "-" + UUID.randomUUID() + ".html";
                writeDoc(testFiles, fetchKey, marker);
                fetchKeys.add(fetchKey);
                markers.add(marker);
                calls.add(() -> {
                    barrier.await(30, TimeUnit.SECONDS);
                    return stub.fetchAndParse(FetchAndParseRequest.newBuilder()
                            .setFetcherId(FETCHER_ID)
                            .setFetchKey(fetchKey)
                            .build());
                });
            }
            List<Future<FetchAndParseReply>> futures =
                    pool.invokeAll(calls, 120, TimeUnit.SECONDS);
            for (int i = 0; i < concurrency; i++) {
                Future<FetchAndParseReply> future = futures.get(i);
                assertFalse(future.isCancelled(),
                        "call " + i + " did not finish within the time budget");
                FetchAndParseReply reply = future.get();
                assertEquals(fetchKeys.get(i), reply.getFetchKey());
                assertEquals(PipesResult.RESULT_STATUS.PARSE_SUCCESS.name(), reply.getStatus(),
                        "call " + i + " must parse; error: " + reply.getErrorMessage());
                String marker = markers.get(i);
                assertTrue(reply.getFieldsMap().values().stream()
                                .anyMatch(v -> v.contains(marker)),
                        "call " + i + " must carry its own document, not another call's");
            }
        } finally {
            pool.shutdownNow();
            service.postShutdown();
            cleanUp(config, testFiles);
        }
    }

    /**
     * With one client and a zero wait, two overlapping calls must split into
     * one parse and one in-band CLIENT_UNAVAILABLE_WITHIN_MS, the same way
     * every other worker outcome already reaches the caller.
     * <p>
     * Deliberately not warmed up: the winning call holds the only client for
     * the whole worker fork, seconds against the loser's zero-wait admission
     * check. A warm worker would shrink that window to one small parse.
     */
    @Test
    public void saturationSurfacesInBand(Resources resources) throws Exception {
        Path config = writeConfig(1, 0L, null);
        TikaGrpcServerImpl service = new TikaGrpcServerImpl(config.toAbsolutePath().toString());
        List<File> testFiles = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            TikaGrpc.TikaBlockingStub stub = startServer(resources, service);

            CyclicBarrier barrier = new CyclicBarrier(2);
            List<Callable<FetchAndParseReply>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String fetchKey = "tika4815-sat-" + i + "-" + UUID.randomUUID() + ".html";
                writeDoc(testFiles, fetchKey, "saturation " + i);
                calls.add(() -> {
                    barrier.await(30, TimeUnit.SECONDS);
                    return stub.fetchAndParse(FetchAndParseRequest.newBuilder()
                            .setFetcherId(FETCHER_ID)
                            .setFetchKey(fetchKey)
                            .build());
                });
            }
            List<String> statuses = new ArrayList<>();
            List<Future<FetchAndParseReply>> futures =
                    pool.invokeAll(calls, 120, TimeUnit.SECONDS);
            for (int i = 0; i < futures.size(); i++) {
                Future<FetchAndParseReply> future = futures.get(i);
                assertFalse(future.isCancelled(),
                        "call " + i + " did not finish within the time budget");
                statuses.add(future.get().getStatus());
            }
            Collections.sort(statuses);
            assertEquals(List.of(
                            PipesResult.RESULT_STATUS.CLIENT_UNAVAILABLE_WITHIN_MS.name(),
                            PipesResult.RESULT_STATUS.PARSE_SUCCESS.name()),
                    statuses);
        } finally {
            pool.shutdownNow();
            service.postShutdown();
            cleanUp(config, testFiles);
        }
    }

    private static TikaGrpc.TikaBlockingStub startServer(Resources resources,
            TikaGrpcServerImpl service) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        // NOTE: no directExecutor() anywhere -- the production server
        // (Grpc.newServerBuilderForPort) also dispatches on a thread pool.
        Server server = InProcessServerBuilder.forName(serverName)
                .addService(service)
                .build()
                .start();
        resources.register(server, Duration.ofSeconds(30));
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).build();
        resources.register(channel, Duration.ofSeconds(30));
        return TikaGrpc.newBlockingStub(channel);
    }

    /**
     * One sequential call first, so the worker is already up and the burst
     * cannot be blamed on cold start.
     */
    private static void warmUp(TikaGrpc.TikaBlockingStub stub, List<File> testFiles)
            throws Exception {
        String fetchKey = "tika4815-warmup-" + UUID.randomUUID() + ".html";
        writeDoc(testFiles, fetchKey, "warmup");
        FetchAndParseReply reply = stub.fetchAndParse(FetchAndParseRequest.newBuilder()
                .setFetcherId(FETCHER_ID)
                .setFetchKey(fetchKey)
                .build());
        assertEquals(PipesResult.RESULT_STATUS.PARSE_SUCCESS.name(), reply.getStatus(),
                "the warmup fixture must parse, or this test proves nothing");
    }

    private static void writeDoc(List<File> testFiles, String fetchKey, String marker)
            throws Exception {
        File doc = new File("target", fetchKey);
        synchronized (testFiles) {
            testFiles.add(doc);
        }
        FileUtils.writeStringToFile(doc,
                "<html><head><title>" + marker + "</title></head><body>" + marker
                        + "</body></html>", StandardCharsets.UTF_8);
    }

    private static Path writeConfig(Integer numClients, Long maxWaitForClientMillis,
            Boolean useSharedServer) throws Exception {
        Path config = Paths.get("target", "tika4815-config-" + UUID.randomUUID() + ".json");
        Map<String, Object> replacements = new HashMap<>();
        replacements.put("JAVA_PATH", Paths.get(System.getProperty("java.home"), "bin", "java"));
        replacements.put("FETCHER_BASE_PATH", Paths.get("target").toAbsolutePath());
        replacements.put("PLUGIN_ROOTS", Paths.get("target").toAbsolutePath().resolve("plugins"));
        JsonConfigHelper.writeConfigFromResource("/tika-pipes-test-config.json",
                TikaGrpcConcurrencyTest.class, replacements, config);
        if (numClients != null || maxWaitForClientMillis != null || useSharedServer != null) {
            ObjectNode root = (ObjectNode) OBJECT_MAPPER.readTree(config.toFile());
            ObjectNode pipes = (ObjectNode) root.get("pipes");
            if (numClients != null) {
                pipes.put("numClients", numClients);
            }
            if (maxWaitForClientMillis != null) {
                pipes.put("maxWaitForClientMillis", maxWaitForClientMillis);
            }
            if (useSharedServer != null) {
                pipes.put("useSharedServer", useSharedServer);
            }
            Files.writeString(config, OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(root), StandardCharsets.UTF_8);
        }
        return config;
    }

    private static void cleanUp(Path config, List<File> testFiles) throws Exception {
        Files.deleteIfExists(config);
        for (File f : testFiles) {
            FileUtils.deleteQuietly(f);
        }
    }
}
