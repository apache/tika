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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.grpc.v2.ParseBytesReply;
import org.apache.tika.grpc.v2.ParseBytesRequest;
import org.apache.tika.grpc.v2.SourceOrigin;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.core.config.ConfigStore;
import org.apache.tika.serialization.config.JsonConfigHelper;

/**
 * ParseBytes without a gRPC server or a forked pipes worker: the first tests mock
 * {@link TikaGrpcServerImpl}, the spool tests stub its pipes call.
 */
class TikaGrpcV2ParseBytesUnitTest {

    private TikaGrpcServerImpl v1;
    private TikaGrpcV2ServerImpl v2;

    @BeforeEach
    void setUp() {
        v1 = Mockito.mock(TikaGrpcServerImpl.class);
        Mockito.when(v1.denyPerRequestConfig(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(false);
        // An unstubbed mock returns null, which parseBytes treats as a refused request.
        Mockito.when(v1.buildRequestParseContext(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any())).thenReturn(new ParseContext());
        v2 = new TikaGrpcV2ServerImpl(v1);
    }

    static final class RecordingObserver implements StreamObserver<ParseBytesReply> {
        final List<ParseBytesReply> replies = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();
        int completions = 0;

        @Override
        public void onNext(ParseBytesReply reply) {
            replies.add(reply);
        }

        @Override
        public void onError(Throwable t) {
            errors.add(t);
        }

        @Override
        public void onCompleted() {
            completions++;
        }
    }

    private static Metadata htmlMetadata() {
        Metadata primary = new Metadata();
        primary.set(HttpHeaders.CONTENT_TYPE, "text/html");
        return primary;
    }

    private TikaGrpcServerImpl.ParseBytesOutcome outcome() {
        return new TikaGrpcServerImpl.ParseBytesOutcome(
                htmlMetadata(), "PARSE_SUCCESS", 5L);
    }

    private static InputStream bytes(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static ParseBytesRequest.Builder requestWithContent() {
        return ParseBytesRequest.newBuilder()
                .setContent(ByteString.copyFromUtf8("<html><body>u</body></html>"));
    }


    /** The correlation id comes back unchanged, whitespace included. */
    @Test
    void whitespaceCorrelationEchoesVerbatim() throws Exception {
        Mockito.when(v1.runParseBytes(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any()))
                .thenReturn(outcome());
        RecordingObserver observer = new RecordingObserver();

        v2.parseBytes(requestWithContent().setCorrelationId("  ").build(), observer);

        assertEquals(1, observer.replies.size(), "exactly one reply expected");
        assertEquals("  ", observer.replies.get(0).getCorrelationId(),
                "the reply carries the correlation id unchanged");
        assertEquals("  ", observer.replies.get(0).getDocument().getId(),
                "Document.id is the correlation id unchanged");
    }

    /** origin carries the caller's URIs and truncation flag, and the size of the content. */
    @Test
    void originCarriesTheCallersProvenanceAndTheByteCount() throws Exception {
        Mockito.when(v1.runParseBytes(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any()))
                .thenReturn(outcome());
        RecordingObserver observer = new RecordingObserver();
        ParseBytesRequest request = requestWithContent()
                .setSourceUri("http://h/a")
                .setEffectiveUri("http://h/b")
                .setBaseUri("http://h/")
                .setTruncated(true)
                .build();

        v2.parseBytes(request, observer);

        assertEquals(1, observer.replies.size());
        SourceOrigin origin = observer.replies.get(0).getDocument().getOrigin();
        assertEquals(request.getContent().size(), origin.getByteSize(),
                "byte_size is the size of the content sent");
        assertEquals("http://h/a", origin.getSourceUri());
        assertEquals("http://h/b", origin.getEffectiveUri());
        assertEquals("http://h/", origin.getBaseUri());
        assertTrue(origin.getTruncated(), "truncated comes back as sent");
    }

    /** An interrupted parse ends the call with UNAVAILABLE and no Document. */
    @Test
    void anInterruptedParseEndsTheCall() throws Exception {
        Mockito.when(v1.runParseBytes(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any()))
                .thenReturn(null);
        RecordingObserver observer = new RecordingObserver();

        v2.parseBytes(requestWithContent().build(), observer);

        assertTrue(observer.replies.isEmpty(),
                "no Document without a parse result");
        assertEquals(0, observer.completions, "the call must not complete normally");
        assertEquals(1, observer.errors.size(), "the call must end with one error");
        assertEquals(Status.Code.UNAVAILABLE,
                Status.fromThrowable(observer.errors.get(0)).getCode());
    }

    /** runParseBytes checks the declared size, so four bytes declared as 64 MiB + 1 are refused. */
    @Test
    void contentAboveTheLimitIsRefusedBeforeItIsStaged(@TempDir Path tmp)
            throws Exception {
        TikaGrpcServerImpl real = realImpl(tmp);
        try {
            assertThrows(TikaGrpcServerImpl.ParseBytesTooLargeException.class, () ->
                    real.runParseBytes(bytes("<x/>"),
                            TikaGrpcConfig.DEFAULT_PARSE_BYTES_MAX_CONTENT_BYTES + 1L,
                            "a.html", null));

            try (var files = Files.list(real.spool.directory())) {
                assertTrue(files.findAny().isEmpty(),
                        "a rejected request must not have created a spool file");
            }
        } finally {
            real.postShutdown();
        }
    }

    @Test
    void theContentLimitComesFromTheConfig(@TempDir Path tmp) throws Exception {
        TikaGrpcServerImpl real = realImpl(tmp, 1024L);
        try {
            assertThrows(TikaGrpcServerImpl.ParseBytesTooLargeException.class, () ->
                    real.runParseBytes(bytes("<x/>"), 1025L, "a.html", null),
                    "content above the configured limit must be refused");

            try (var files = Files.list(real.spool.directory())) {
                assertTrue(files.findAny().isEmpty(),
                        "a rejected request must not have created a spool file");
            }
        } finally {
            real.postShutdown();
        }
    }

    @Test
    void oversizeIsReportedAsResourceExhausted() throws Exception {
        Mockito.when(v1.runParseBytes(Mockito.any(), Mockito.anyLong(), Mockito.any(),
                        Mockito.any()))
                .thenThrow(new TikaGrpcServerImpl.ParseBytesTooLargeException(
                        "content exceeds the ParseBytes limit of 1 bytes"));
        RecordingObserver observer = new RecordingObserver();

        v2.parseBytes(requestWithContent().build(), observer);

        assertTrue(observer.replies.isEmpty(), "a refused request gets no reply");
        assertEquals(1, observer.errors.size(), "the call must end with one error");
        assertEquals(Status.Code.RESOURCE_EXHAUSTED,
                Status.fromThrowable(observer.errors.get(0)).getCode());
    }

    // The spool tests use a real TikaGrpcServerImpl. No forked pipes worker starts.

    private static TikaGrpcServerImpl realImpl(Path tmp) throws Exception {
        return realImpl(tmp, null);
    }

    private static TikaGrpcServerImpl realImpl(Path tmp, Long parseBytesMaxContentBytes)
            throws Exception {
        return realImpl(tmp, parseBytesMaxContentBytes, null);
    }

    /** With {@code sharedStore}, the impl uses a "file" config store at that path. */
    private static TikaGrpcServerImpl realImpl(Path tmp, Long parseBytesMaxContentBytes,
                                               Path sharedStore) throws Exception {
        Path config = tmp.resolve("unit-config.json");
        Map<String, Object> replacements = new HashMap<>();
        replacements.put("JAVA_PATH", Paths.get(System.getProperty("java.home"), "bin", "java"));
        replacements.put("FETCHER_BASE_PATH", tmp.toAbsolutePath());
        replacements.put("PLUGIN_ROOTS", Paths.get("target").toAbsolutePath().resolve("plugins"));
        JsonConfigHelper.writeConfigFromResource("/tika-pipes-test-config.json",
                TikaGrpcServerTest.class, replacements, config);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(config.toFile());
        // Every payload in these tests is larger, so it goes through a spool file.
        ((ObjectNode) root.get("pipes")).put("maxInlineBytes", 1);
        if (sharedStore != null) {
            ((ObjectNode) root.get("pipes")).put("configStoreType", "file");
            // Jackson escapes the backslashes of a Windows path.
            ((ObjectNode) root.get("pipes")).put("configStoreParams", mapper.createObjectNode()
                    .put("path", sharedStore.toAbsolutePath().toString()).toString());
        }
        if (parseBytesMaxContentBytes != null) {
            ObjectNode grpc = root.has("grpc") && root.get("grpc").isObject()
                    ? (ObjectNode) root.get("grpc")
                    : root.putObject("grpc");
            grpc.put("parseBytesMaxContentBytes", parseBytesMaxContentBytes);
        }
        Files.writeString(config,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        return new TikaGrpcServerImpl(config.toAbsolutePath().toString());
    }

    @Test
    void theSpoolFileIsDeletedWhenTheParseFails(@TempDir Path tmp) throws Exception {
        TikaGrpcServerImpl real = realImpl(tmp);
        try {
            TikaGrpcServerImpl spied = Mockito.spy(real);
            Mockito.doThrow(new RuntimeException("worker unavailable"))
                    .when(spied).executeTuple(Mockito.any(), Mockito.anyLong());

            assertThrows(RuntimeException.class, () ->
                    spied.runParseBytes(bytes("<x/>"), 4, "a.html", null));

            assertSpooled(spied);
            try (var files = Files.list(spied.spool.directory())) {
                assertTrue(files.findAny().isEmpty(),
                        "a failed parse must not leave a spool file behind");
            }
        } finally {
            real.postShutdown();
        }
    }

    @Test
    void theSpoolFileIsDeletedWhenTheParseIsInterrupted(@TempDir Path tmp) throws Exception {
        TikaGrpcServerImpl real = realImpl(tmp);
        try {
            TikaGrpcServerImpl spied = Mockito.spy(real);
            Mockito.doThrow(new InterruptedException())
                    .when(spied).executeTuple(Mockito.any(), Mockito.anyLong());

            assertNull(spied.runParseBytes(bytes("<x/>"), 4, "a.html", null));
            assertTrue(Thread.interrupted(), "the interrupt flag must be set again");

            assertSpooled(spied);
            try (var files = Files.list(spied.spool.directory())) {
                assertTrue(files.findAny().isEmpty(),
                        "an interrupted parse must not leave a spool file behind");
            }
        } finally {
            real.postShutdown();
        }
    }

    /** The stubbed pipes call received the spool fetcher, so the bytes went to a file. */
    private static void assertSpooled(TikaGrpcServerImpl spied) throws Exception {
        ArgumentCaptor<FetchEmitTuple> tuple = ArgumentCaptor.forClass(FetchEmitTuple.class);
        Mockito.verify(spied).executeTuple(tuple.capture(), Mockito.anyLong());
        assertEquals(spied.spool.fetcherId(),
                tuple.getValue().getFetchKey().getFetcherId(),
                "the bytes must have gone through a spool file");
    }

    /** shutdown() keeps the spool directory for requests still running; postShutdown() deletes it. */
    @Test
    void theSpoolDirectoryOutlivesShutdownAndPostShutdownDeletesIt(@TempDir Path tmp) throws Exception {
        TikaGrpcServerImpl real = realImpl(tmp);
        Path dir = real.spool.directory();
        try {
            real.shutdown();
            assertTrue(Files.isDirectory(dir),
                    "shutdown() must keep the spool directory");
        } finally {
            real.postShutdown();
        }
        assertFalse(Files.exists(dir), "postShutdown() must delete the spool directory");
    }

    /**
     * postShutdown() removes the spool fetcher from the config store before it closes the
     * embedded Ignite node, and closes the node even when the removal fails.
     */
    @Test
    void postShutdownClosesIgniteLast(@TempDir Path tmp) throws Exception {
        TikaGrpcServerImpl real = realImpl(tmp);
        ConfigStore store = Mockito.spy(real.configStore);
        Mockito.doThrow(new IllegalStateException("store unavailable"))
                .when(store).remove(Mockito.anyString());
        real.configStore = store;
        AutoCloseable ignite = Mockito.mock(AutoCloseable.class);
        real.igniteStoreServer = ignite;

        real.postShutdown();

        InOrder order = Mockito.inOrder(store, ignite);
        order.verify(store).remove(real.spool.fetcherId());
        order.verify(ignite).close();
    }

    /** A constructor that throws deletes the spool directory it created. */
    @Test
    void failedConstructionLeavesNoSpoolDirectory(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("ignite-without-the-module.json");
        Files.writeString(config,
                "{\"pipes\":{\"configStoreType\":\"ignite\",\"configStoreParams\":\"{}\"}}");
        Path tmpdir = Paths.get(System.getProperty("java.io.tmpdir"));
        long before = spoolDirectories(tmpdir);

        assertThrows(TikaConfigException.class,
                () -> new TikaGrpcServerImpl(config.toAbsolutePath().toString()));

        assertEquals(before, spoolDirectories(tmpdir),
                "the failed constructor must delete its spool directory");
    }

    /**
     * Two servers on one "file" config store each read their own spool directory, and each
     * removes its own fetcher from the store when it shuts down.
     */
    @Test
    void twoServersOnOneStoreKeepTheirOwnSpool(@TempDir Path tmp) throws Exception {
        Path store = tmp.resolve("shared-config-store.json");
        TikaGrpcServerImpl first = realImpl(Files.createDirectories(tmp.resolve("first")), null, store);
        TikaGrpcServerImpl second = realImpl(Files.createDirectories(tmp.resolve("second")), null, store);
        try {
            assertEquals(first.spool.directory().toString(), spoolBasePath(first),
                    "the first server must read its own spool directory");
            assertEquals(second.spool.directory().toString(), spoolBasePath(second));
            assertNotEquals(first.spool.fetcherId(), second.spool.fetcherId(),
                    "each server must have its own spool fetcher");
        } finally {
            first.postShutdown();
        }
        try {
            assertFalse(Files.readString(store).contains(first.spool.fetcherId()),
                    "the first server must remove its fetcher from the store");
            assertTrue(Files.readString(store).contains(second.spool.fetcherId()),
                    "the second server's fetcher must stay in the store");
        } finally {
            second.postShutdown();
        }
        assertFalse(Files.readString(store).contains(second.spool.fetcherId()));
    }

    /**
     * A "file" store re-reads its file on lookups but not on remove, and remove writes this
     * JVM's copy back. Unlike the test above, nothing looks the store up before the first
     * server shuts down, so its copy predates the second server; removing its entry must not
     * remove the second server's.
     */
    @Test
    void aDepartingServerLeavesTheOthersEntriesAlone(@TempDir Path tmp) throws Exception {
        Path store = tmp.resolve("shared-config-store.json");
        TikaGrpcServerImpl first = realImpl(Files.createDirectories(tmp.resolve("first")), null, store);
        TikaGrpcServerImpl second = realImpl(Files.createDirectories(tmp.resolve("second")), null, store);
        try {
            first.postShutdown();
            assertTrue(Files.readString(store).contains(second.spool.fetcherId()),
                    "the second server's fetcher must stay in the store");
        } finally {
            second.postShutdown();
        }
    }

    private static String spoolBasePath(TikaGrpcServerImpl impl) throws Exception {
        String json = impl.fetcherManager.getFetcher(impl.spool.fetcherId())
                .getExtensionConfig().json();
        return new ObjectMapper().readTree(json).get("basePath").asText();
    }

    private static long spoolDirectories(Path tmpdir) throws IOException {
        try (var entries = Files.list(tmpdir)) {
            return entries.filter(p -> p.getFileName().toString().startsWith("tika-grpc-parse-bytes"))
                    .count();
        }
    }
}
