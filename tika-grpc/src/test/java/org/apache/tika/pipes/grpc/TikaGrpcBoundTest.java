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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.InsecureServerCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mockito;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.grpc.v2.ParseBytesRequest;
import org.apache.tika.grpc.v2.ParseBytesResponse;
import org.apache.tika.grpc.v2.TikaV2ServiceGrpc;
import org.apache.tika.pipes.core.config.ConfigStore;
import org.apache.tika.pipes.grpc.proto.FetchAndParseReply;
import org.apache.tika.pipes.grpc.proto.FetchAndParseRequest;
import org.apache.tika.pipes.grpc.proto.TikaGrpc;
import org.apache.tika.serialization.config.JsonConfigHelper;

/**
 * The message size limits and the server lifecycle, on a real Netty server: the in-process
 * transport the other tests use does not apply {@code maxInboundMessageSize}.
 *
 * <p>Payloads stay at a few MiB, and their replies stay small, so only the server's
 * inbound limit can refuse them.
 */
public class TikaGrpcBoundTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Above grpc-java's 4 MiB default. */
    private static final int ABOVE_DEFAULT = 5 * 1024 * 1024;

    /** 100 MiB, the limit in dev-tika-config.json. */
    private static final int DEV_CONFIG_LIMIT = 104857600;

    @TempDir
    static Path tempDir;

    private static Path baseConfig;

    @BeforeAll
    static void init() throws Exception {
        baseConfig = tempDir.resolve("tika-config-bound.json");
        Map<String, Object> replacements = new HashMap<>();
        replacements.put("JAVA_PATH", Paths.get(System.getProperty("java.home"), "bin", "java"));
        replacements.put("FETCHER_BASE_PATH", tempDir.toAbsolutePath());
        replacements.put("PLUGIN_ROOTS", Paths.get("target").toAbsolutePath().resolve("plugins"));
        JsonConfigHelper.writeConfigFromResource("/tika-pipes-test-config.json",
                TikaGrpcBoundTest.class, replacements, baseConfig);
    }

    /** A config with the given inbound limit, or without a {@code grpc} section for null. */
    private static Path configWithLimit(Integer maxInboundMessageBytes) throws Exception {
        return configWithLimits(maxInboundMessageBytes, null);
    }

    /** Like {@link #configWithLimit}, and sets the ParseBytes content limit when not null. */
    private static Path configWithLimits(Integer maxInboundMessageBytes,
                                         Long parseBytesMaxContentBytes) throws Exception {
        ObjectNode root = (ObjectNode) OBJECT_MAPPER.readTree(baseConfig.toFile());
        if (maxInboundMessageBytes != null || parseBytesMaxContentBytes != null) {
            ObjectNode grpc = root.putObject("grpc");
            if (maxInboundMessageBytes != null) {
                grpc.put("maxInboundMessageBytes", maxInboundMessageBytes);
            }
            if (parseBytesMaxContentBytes != null) {
                grpc.put("parseBytesMaxContentBytes", parseBytesMaxContentBytes);
            }
        }
        Path derived = tempDir.resolve("tika-config-bound-"
                + maxInboundMessageBytes + "-" + UUID.randomUUID() + ".json");
        FileUtils.write(derived.toFile(),
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8);
        return derived;
    }

    /** A Netty server, its channel and its impl, released together by {@link #close()}. */
    private static final class RealServer implements AutoCloseable {
        private final TikaGrpcServerImpl serviceImpl;
        private final Server server;
        private final ManagedChannel channel;
        final TikaV2ServiceGrpc.TikaV2ServiceBlockingStub v2;

        RealServer(Path config) throws Exception {
            serviceImpl = new TikaGrpcServerImpl(config.toAbsolutePath().toString());
            // The same helper TikaGrpcServer.start() uses.
            ServerBuilder<?> builder =
                    Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create());
            TikaGrpcServer.applyInboundLimit(builder, serviceImpl.tikaGrpcConfig);
            server = builder
                    .addService(serviceImpl)
                    .addService(new TikaGrpcV2ServerImpl(serviceImpl))
                    .build()
                    .start();
            channel = Grpc.newChannelBuilderForAddress(
                            "localhost", server.getPort(), InsecureChannelCredentials.create())
                    .build();
            v2 = TikaV2ServiceGrpc.newBlockingStub(channel);
        }

        @Override
        public void close() throws Exception {
            channel.shutdownNow();
            channel.awaitTermination(10, TimeUnit.SECONDS);
            server.shutdownNow();
            server.awaitTermination(10, TimeUnit.SECONDS);
            serviceImpl.postShutdown();
        }
    }

    /**
     * An HTML page of {@code totalBytes} whose padding is an HTML comment. The parser drops
     * the comment, so the reply stays small.
     */
    private static ByteString bigInputTinyOutput(int totalBytes) {
        String head = "<html><body>tiny<!--";
        String tail = "--></body></html>";
        int pad = totalBytes - head.length() - tail.length();
        return ByteString.copyFromUtf8(head + "x".repeat(Math.max(0, pad)) + tail);
    }

    @Test
    public void payloadAboveTheGrpcDefaultIsAcceptedWhenTheLimitIsRaised() throws Exception {
        try (RealServer s = new RealServer(configWithLimit(DEV_CONFIG_LIMIT))) {
            ParseBytesResponse reply = s.v2.parseBytes(ParseBytesRequest.newBuilder()
                    .setCorrelationId("big-1")
                    .setContent(bigInputTinyOutput(ABOVE_DEFAULT))
                    .setResourceName("big.html")
                    .build());

            assertTrue(reply.hasDocument(), "a 5 MiB payload must reach the parser");
            assertTrue(reply.getSerializedSize() < 1024 * 1024,
                    "the reply must stay under 1 MiB so that only the server's limit is "
                            + "tested; got " + reply.getSerializedSize() + " bytes");
        }
    }

    @Test
    public void contentExactlyAtTheConfiguredLimitIsAccepted() throws Exception {
        try (RealServer s = new RealServer(
                configWithLimits(DEV_CONFIG_LIMIT, (long) ABOVE_DEFAULT))) {
            ParseBytesResponse reply = s.v2.parseBytes(ParseBytesRequest.newBuilder()
                    .setCorrelationId("at-cap-1")
                    .setContent(bigInputTinyOutput(ABOVE_DEFAULT))
                    .setResourceName("atcap.html")
                    .build());

            assertTrue(reply.hasDocument(),
                    "content of exactly the configured limit must parse");
        }
    }

    /**
     * Without a configured limit the inbound limit follows the content limit: content just
     * over a 2 MiB content limit reaches ParseBytes, which refuses it, and a request well over
     * the derived 3 MiB inbound limit is refused by gRPC.
     */
    @Test
    public void withoutALimitTheInboundLimitFollowsTheContentLimit() throws Exception {
        int contentLimit = 2 * 1024 * 1024;
        try (RealServer s = new RealServer(configWithLimits(null, (long) contentLimit))) {
            StatusRuntimeException overContent = assertThrows(StatusRuntimeException.class,
                    () -> s.v2.parseBytes(ParseBytesRequest.newBuilder()
                            .setContent(bigInputTinyOutput(contentLimit + 1))
                            .build()));
            assertEquals(Status.Code.RESOURCE_EXHAUSTED, overContent.getStatus().getCode());
            assertTrue(overContent.getStatus().getDescription().contains("ParseBytes limit"),
                    "ParseBytes, not gRPC, refuses content between the two limits: "
                            + overContent.getStatus().getDescription());

            StatusRuntimeException overInbound = assertThrows(StatusRuntimeException.class,
                    () -> s.v2.parseBytes(ParseBytesRequest.newBuilder()
                            .setContent(bigInputTinyOutput(ABOVE_DEFAULT))
                            .build()));
            assertEquals(Status.Code.RESOURCE_EXHAUSTED, overInbound.getStatus().getCode());
            assertFalse(String.valueOf(overInbound.getStatus().getDescription())
                    .contains("ParseBytes limit"), "gRPC refuses it first");
        }
        assertEquals(TikaGrpcConfig.DEFAULT_PARSE_BYTES_MAX_CONTENT_BYTES
                        + TikaGrpcConfig.INBOUND_OVERHEAD_BYTES,
                new TikaGrpcConfig().effectiveMaxInboundMessageBytes(),
                "the default inbound limit admits default-limit content");
    }

    /** A 2 MiB request is below gRPC's 4 MiB default, so only a 1 MiB limit refuses it. */
    @Test
    public void aLimitBelowTheGrpcDefaultIsApplied() throws Exception {
        try (RealServer s = new RealServer(configWithLimit(1024 * 1024))) {
            StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, () ->
                    s.v2.parseBytes(ParseBytesRequest.newBuilder()
                            .setContent(bigInputTinyOutput(2 * 1024 * 1024))
                            .build()));

            assertEquals(Status.Code.RESOURCE_EXHAUSTED, thrown.getStatus().getCode());
        }
    }

    /**
     * {@link TikaGrpcServer#start()} applies the limit. The tests above call
     * {@link TikaGrpcServer#applyInboundLimit} themselves.
     */
    @Test
    public void theRealServerAppliesTheLimitOnStart() throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        TikaGrpcServer server = new TikaGrpcServer()
                .setPort(port)
                .setTikaConfig(configWithLimit(1024 * 1024).toFile());
        ManagedChannel channel = null;
        try {
            server.start();
            channel = Grpc.newChannelBuilderForAddress(
                    "localhost", port, InsecureChannelCredentials.create()).build();
            TikaV2ServiceGrpc.TikaV2ServiceBlockingStub v2 = TikaV2ServiceGrpc.newBlockingStub(channel);

            StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, () ->
                    v2.parseBytes(ParseBytesRequest.newBuilder()
                            .setContent(bigInputTinyOutput(2 * 1024 * 1024))
                            .build()));

            assertEquals(Status.Code.RESOURCE_EXHAUSTED, thrown.getStatus().getCode(),
                    "start() must apply the configured limit, not only the helper");
        } finally {
            if (channel != null) {
                channel.shutdownNow();
                channel.awaitTermination(10, TimeUnit.SECONDS);
            }
            server.stop();
        }
    }

    /** Equal limits warn too: the request carries other fields besides the content. */
    @Test
    public void anInboundLimitAtOrBelowTheContentLimitIsLogged() {
        long contentLimit = TikaGrpcConfig.DEFAULT_PARSE_BYTES_MAX_CONTENT_BYTES;
        assertNotNull(TikaGrpcServer.inboundLimitWarning(1024 * 1024, contentLimit),
                "an inbound limit below the content limit");
        assertNotNull(TikaGrpcServer.inboundLimitWarning((int) contentLimit, contentLimit),
                "an inbound limit equal to the content limit");
        assertNull(TikaGrpcServer.inboundLimitWarning(DEV_CONFIG_LIMIT, contentLimit),
                "the dev-tika-config.json limit is above the default content limit");
        assertNull(TikaGrpcServer.inboundLimitWarning(null, contentLimit),
                "no inbound limit configured");
        assertNotNull(TikaGrpcServer.inboundLimitWarning(DEV_CONFIG_LIMIT, DEV_CONFIG_LIMIT * 2L),
                "a configured content limit above the inbound limit");
    }

    /** Both size limits reject non-positive values through the JSON load path. */
    @Test
    public void nonPositiveLimitsAreRefusedAtLoad() throws Exception {
        assertLoadRejects("parseBytesMaxContentBytes", 0);
        assertLoadRejects("parseBytesMaxContentBytes", -1);
        assertLoadRejects("maxInboundMessageBytes", 0);
        assertLoadRejects("maxInboundMessageBytes", -1);
    }

    private static void assertLoadRejects(String key, long value) throws Exception {
        Path file = tempDir.resolve("tika-config-badlimit-" + UUID.randomUUID() + ".json");
        FileUtils.write(file.toFile(),
                "{\"grpc\": {\"" + key + "\": " + value + "}}", StandardCharsets.UTF_8);
        IOException refused = assertThrows(IOException.class,
                () -> TikaGrpcConfig.load(TikaJsonConfig.load(file)),
                key + "=" + value + " must be refused at load");
        Throwable cause = refused;
        boolean fromValidation = false;
        while (cause != null && !fromValidation) {
            fromValidation = String.valueOf(cause.getMessage()).contains("must be positive");
            cause = cause.getCause();
        }
        assertTrue(fromValidation,
                "the refusal must come from the setter's validation, got: " + refused);
    }

    @Test
    public void anInboundLimitTooLargeForTheHeapIsLogged() {
        long mib = 1024L * 1024L;
        assertNotNull(TikaGrpcServer.heapHeadroomWarning(100 * (int) mib, 256 * mib),
                "a 100 MiB limit on a 256 MiB heap");
        assertNull(TikaGrpcServer.heapHeadroomWarning(100 * (int) mib, 1024 * mib),
                "a 100 MiB limit on a 1 GiB heap");
        assertNull(TikaGrpcServer.heapHeadroomWarning(100 * (int) mib, 300 * mib),
                "a heap of exactly three times the limit");
        assertNull(TikaGrpcServer.heapHeadroomWarning(null, 64 * mib),
                "no inbound limit configured");
        assertNull(TikaGrpcServer.heapHeadroomWarning(100 * (int) mib, Long.MAX_VALUE),
                "no maximum heap");
    }

    @Test
    public void bothWarningsAreCollectedTogether() {
        long mib = 1024L * 1024L;
        long contentLimit = TikaGrpcConfig.DEFAULT_PARSE_BYTES_MAX_CONTENT_BYTES;
        assertEquals(2, TikaGrpcServer.startupWarnings((int) contentLimit, contentLimit,
                        100 * mib).size(),
                "an inbound limit equal to the content limit, on a 100 MiB heap");
        assertTrue(TikaGrpcServer.startupWarnings(DEV_CONFIG_LIMIT, contentLimit, 8192 * mib)
                        .isEmpty(),
                "the dev-tika-config.json limit on an 8 GiB heap");
    }

    /** {@link TikaGrpcServer#start()} logs the warnings. */
    @Test
    public void theRealServerLogsTheWarningOnStart() throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        CapturingAppender captured = new CapturingAppender();
        captured.start();
        ctx.getConfiguration().getRootLogger().addAppender(captured, Level.WARN, null);
        ctx.updateLoggers();

        TikaGrpcServer server = new TikaGrpcServer()
                .setPort(port)
                .setTikaConfig(configWithLimit(1024 * 1024).toFile());
        try {
            server.start();
            assertTrue(captured.messages.stream()
                            .anyMatch(m -> m.contains("maxInboundMessageBytes is 1048576")),
                    "start() must log the inbound limit warning; logged: "
                            + captured.messages);
        } finally {
            server.stop();
            ctx.getConfiguration().getRootLogger().removeAppender("capture");
            ctx.updateLoggers();
            captured.stop();
        }
    }

    /** Collects the WARN messages logged during a test. */
    private static final class CapturingAppender extends AbstractAppender {
        private final List<String> messages = Collections.synchronizedList(new ArrayList<>());

        CapturingAppender() {
            super("capture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }
    }

    /** start() builds the impl before it binds the port, and releases it when the bind fails. */
    @Test
    public void aFailedStartReleasesTheImpl() throws Exception {
        Path tmpdir = Paths.get(System.getProperty("java.io.tmpdir"));
        try (ServerSocket taken = new ServerSocket(0)) {
            TikaGrpcServer server = new TikaGrpcServer()
                    .setPort(taken.getLocalPort())
                    .setTikaConfig(configWithLimit(null).toFile());
            long before = spoolDirectories(tmpdir);

            assertThrows(IOException.class, server::start, "the port is taken");

            assertEquals(before, spoolDirectories(tmpdir),
                    "start() failed after building the impl: it must release the spool directory");
        }
    }

    /** start() also releases the impl when gRPC refuses the TLS settings. */
    @Test
    public void aStartRefusedAfterBuildingTheImplReleasesIt(@TempDir Path tmp) throws Exception {
        Path corrupt = tmp.resolve("corrupt-ca.pem");
        Files.writeString(corrupt, "this is not a valid PEM certificate");
        Path certs = Paths.get("src", "test", "resources", "certs");
        TikaGrpcServer server = new TikaGrpcServer()
                .setPort(0)
                .setSecure(true)
                .setCertChain(certs.resolve("server1.pem").toFile())
                .setPrivateKey(certs.resolve("server1.key").toFile())
                .setTrustCertCollection(corrupt.toFile())
                .setClientAuthRequired(true)
                .setTikaConfig(configWithLimit(null).toFile());
        Path tmpdir = Paths.get(System.getProperty("java.io.tmpdir"));
        long before = spoolDirectories(tmpdir);

        assertThrows(Exception.class, server::start, "grpc must refuse the corrupt trust collection");

        assertEquals(before, spoolDirectories(tmpdir),
                "start() failed after building the impl: it must release the spool directory");
    }

    @Test
    public void anInterruptedStopStillReleasesTheImpl() throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        Path doc = tempDir.resolve("stop.txt");
        Files.writeString(doc, "hello");
        Path tmpdir = Paths.get(System.getProperty("java.io.tmpdir"));
        long before = spoolDirectories(tmpdir);
        TikaGrpcServer server = new TikaGrpcServer()
                .setPort(port)
                .setTikaConfig(configWithLimit(null).toFile());
        server.start();
        ManagedChannel channel = Grpc.newChannelBuilderForAddress(
                "localhost", port, InsecureChannelCredentials.create()).build();
        try {
            // An open stream keeps the server running, so stop() waits for it.
            CountDownLatch replied = new CountDownLatch(1);
            StreamObserver<FetchAndParseRequest> stream = TikaGrpc.newStub(channel)
                    .fetchAndParseBiDirectionalStreaming(new StreamObserver<>() {
                        @Override
                        public void onNext(FetchAndParseReply reply) {
                            replied.countDown();
                        }

                        @Override
                        public void onError(Throwable t) {
                        }

                        @Override
                        public void onCompleted() {
                        }
                    });
            stream.onNext(FetchAndParseRequest.newBuilder()
                    .setFetcherId("nick1.is.cool.super-fs")
                    .setFetchKey(doc.getFileName().toString())
                    .build());
            assertTrue(replied.await(60, TimeUnit.SECONDS), "the stream must be open on the server");

            Thread.currentThread().interrupt();
            try {
                assertThrows(InterruptedException.class, server::stop);
            } finally {
                Thread.interrupted();
            }

            assertEquals(before, spoolDirectories(tmpdir),
                    "stop() was interrupted while draining: it must still release the impl");
        } finally {
            channel.shutdownNow();
        }
    }

    /**
     * stop() releases the spool before it closes the embedded Ignite node. The spool fetcher
     * comes from the config file, so it never reaches the store (TIKA-5001) and the release
     * only looks it up.
     */
    @Test
    public void stopClosesIgniteLast() throws Exception {
        TikaGrpcServer server = new TikaGrpcServer()
                .setPort(0)
                .setTikaConfig(configWithLimit(null).toFile());
        server.start();
        TikaGrpcServerImpl impl = server.serviceImpl;
        ConfigStore store = Mockito.spy(impl.configStore);
        impl.configStore = store;
        AutoCloseable ignite = Mockito.mock(AutoCloseable.class);
        impl.igniteStoreServer = ignite;

        server.stop();

        InOrder order = Mockito.inOrder(store, ignite);
        order.verify(store).containsKey(impl.spool.fetcherId());
        order.verify(ignite).close();
        Mockito.verify(store, Mockito.never()).remove(Mockito.anyString());
    }

    private static long spoolDirectories(Path tmpdir) throws IOException {
        try (var entries = Files.list(tmpdir)) {
            return entries.filter(p -> p.getFileName().toString().startsWith("tika-grpc-parse-bytes"))
                    .count();
        }
    }
}
