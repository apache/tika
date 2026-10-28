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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
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

import org.apache.tika.digest.DigestDef;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.grpc.v2.Document;
import org.apache.tika.grpc.v2.ParseBytesRequest;
import org.apache.tika.grpc.v2.ParseBytesResponse;
import org.apache.tika.grpc.v2.ParseStatus;
import org.apache.tika.grpc.v2.SourceOrigin;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
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

    static final class RecordingObserver implements StreamObserver<ParseBytesResponse> {
        final List<ParseBytesResponse> replies = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();
        int completions = 0;

        @Override
        public void onNext(ParseBytesResponse reply) {
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
                htmlMetadata(), "PARSE_SUCCESS", null, 5L);
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
        Mockito.when(v1.parseAdmitted(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
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
        Mockito.when(v1.parseAdmitted(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
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

    /**
     * A parse that failed before producing metadata still says what was sent: the SHA-256
     * of the content and a content type detected from its bytes and resource name.
     */
    @Test
    void aFailedParseStillCarriesTheDigestAndContentType() throws Exception {
        Mockito.when(v1.parseAdmitted(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new TikaGrpcServerImpl.ParseBytesOutcome(
                        null, "PARSE_EXCEPTION_NO_EMIT", "boom", 5L));
        RecordingObserver observer = new RecordingObserver();
        byte[] pdf = "%PDF-1.4\n%garbage".getBytes(StandardCharsets.US_ASCII);

        v2.parseBytes(ParseBytesRequest.newBuilder()
                .setContent(ByteString.copyFrom(pdf))
                .setResourceName("broken.pdf")
                .build(), observer);

        assertEquals(1, observer.replies.size());
        Document document = observer.replies.get(0).getDocument();
        assertEquals(ParseStatus.Status.STATUS_FAILED, document.getStatus().getStatus());
        assertEquals(HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(pdf)),
                document.getOrigin().getSha256(), "sha256 is computed from the content");
        assertEquals("application/pdf", document.getContentType(),
                "content type is detected from the bytes");
    }

    /**
     * The fallback looks inside gzip: a gzipped WARC is application/warc+gz with or without
     * a name, not application/gzip, so it can still be routed as a WARC.
     */
    @Test
    void theFallbackDetectsAGzippedWarcByItsBytes() throws Exception {
        ByteString warcGz = testFile("testWARC_multiple.warc.gz");
        for (String name : new String[]{"", "crawl.warc.gz", "crawl.gz", "crawl.bin"}) {
            assertEquals("application/warc+gz",
                    TikaGrpcV2ServerImpl.detectContentType(warcGz, name), "name=" + name);
        }
        assertEquals("application/warc",
                TikaGrpcV2ServerImpl.detectContentType(testFile("testWARC_multiple.warc"), ""));

        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
            out.write("just some text".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals("application/gzip", TikaGrpcV2ServerImpl.detectContentType(
                ByteString.copyFrom(gz.toByteArray()), "notes.warc.gz"),
                "the bytes win over a .warc.gz name on a gzip that holds no WARC");
    }

    private static ByteString testFile(String name) throws IOException {
        return ByteString.copyFrom(Files.readAllBytes(
                Paths.get("src", "test", "resources", "test-files", name)));
    }

    /** The digest and content type the parse recorded win over the server's fallback. */
    @Test
    void theParsesDigestAndContentTypeAreKept() throws Exception {
        Metadata primary = htmlMetadata();
        primary.set(TikaCoreProperties.digestProperty(
                DigestDef.Algorithm.SHA256.getJavaName()), PIPELINE_DIGEST);
        Mockito.when(v1.parseAdmitted(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new TikaGrpcServerImpl.ParseBytesOutcome(
                        primary, "PARSE_SUCCESS", null, 5L));
        RecordingObserver observer = new RecordingObserver();

        v2.parseBytes(requestWithContent().build(), observer);

        Document document = observer.replies.get(0).getDocument();
        assertEquals("text/html", document.getContentType());
        assertEquals(PIPELINE_DIGEST, document.getOrigin().getSha256());
    }

    private static final String PIPELINE_DIGEST = "ab".repeat(32);

    /**
     * origin.sha256 is lowercase hex SHA-256: a pipeline digest in another form (upper case,
     * base32, another length) is replaced by the server's own.
     */
    @Test
    void aPipelineDigestInAnotherFormIsReplaced() throws Exception {
        for (String recorded : List.of("abc123", "AB".repeat(32), "MFRGGZDFMZTWQ2LK")) {
            Metadata primary = htmlMetadata();
            primary.set(TikaCoreProperties.digestProperty(
                    DigestDef.Algorithm.SHA256.getJavaName()), recorded);
            Mockito.when(v1.parseAdmitted(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(new TikaGrpcServerImpl.ParseBytesOutcome(
                            primary, "PARSE_SUCCESS", null, 5L));
            RecordingObserver observer = new RecordingObserver();
            ParseBytesRequest request = requestWithContent().build();

            v2.parseBytes(request, observer);

            String sha256 = observer.replies.get(0).getDocument().getOrigin().getSha256();
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(request.getContent().toByteArray())), sha256, recorded);
        }
    }

    /**
     * Truncated content: whatever the status, the Document carries the truncation note; only
     * SUCCESS becomes PARTIAL.
     */
    @Test
    void truncationIsNotedForEveryStatus() throws Exception {
        record Case(String pipes, ParseStatus.Status expected) { }
        for (Case c : List.of(
                new Case("PARSE_SUCCESS", ParseStatus.Status.STATUS_PARTIAL),
                new Case("PARSE_SUCCESS_WITH_EXCEPTION", ParseStatus.Status.STATUS_PARTIAL),
                new Case("PARSE_EXCEPTION_NO_EMIT", ParseStatus.Status.STATUS_FAILED),
                new Case("TIMEOUT", ParseStatus.Status.STATUS_FAILED))) {
            Mockito.when(v1.parseAdmitted(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(new TikaGrpcServerImpl.ParseBytesOutcome(
                            htmlMetadata(), c.pipes(), "boom", 5L));
            RecordingObserver observer = new RecordingObserver();

            v2.parseBytes(requestWithContent().setTruncated(true).build(), observer);

            ParseStatus status = observer.replies.get(0).getDocument().getStatus();
            assertEquals(c.expected(), status.getStatus(), c.pipes());
            assertTrue(status.getErrorsList().contains(TikaGrpcV2ServerImpl.TRUNCATED_BY_CALLER),
                    c.pipes() + ": " + status.getErrorsList());
        }
    }

    /** A spool directory the server cannot write to is RESOURCE_EXHAUSTED, not INTERNAL. */
    @Test
    void aFullSpoolIsResourceExhausted() throws Exception {
        Mockito.when(v1.parseAdmitted(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new TikaGrpcServerImpl.ParseBytesSpoolException(
                        new IOException("No space left on device")));
        RecordingObserver observer = new RecordingObserver();

        v2.parseBytes(requestWithContent().build(), observer);

        Status status = Status.fromThrowable(observer.errors.get(0));
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.getCode());
        assertFalse(status.getDescription().contains("No space"), status.getDescription());
    }

    /**
     * The admission permit is held until the reply is sent, and released once, also when
     * the call ends in an error.
     */
    @Test
    void thePermitIsHeldThroughTheReplyAndReleasedOnce() throws Exception {
        Semaphore admission = new Semaphore(1);
        Mockito.when(v1.admitParseBytes(Mockito.anyLong())).thenAnswer(call -> {
            assertTrue(admission.tryAcquire());
            return new TikaGrpcServerImpl.ParseBytesPermit(admission);
        });
        List<Integer> freeAtReply = new ArrayList<>();
        StreamObserver<ParseBytesResponse> observer = new StreamObserver<>() {
            @Override
            public void onNext(ParseBytesResponse value) {
                freeAtReply.add(admission.availablePermits());
            }

            @Override
            public void onError(Throwable t) {
                freeAtReply.add(admission.availablePermits());
            }

            @Override
            public void onCompleted() {
            }
        };
        Mockito.when(v1.parseAdmitted(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(outcome())
                .thenThrow(new RuntimeException("boom"));

        v2.parseBytes(requestWithContent().build(), observer);
        v2.parseBytes(requestWithContent().build(), observer);

        assertEquals(List.of(0, 0), freeAtReply, "held while the reply or error is sent");
        assertEquals(1, admission.availablePermits(), "released exactly once per call");
    }

    /** Many threads closing one permit release it once. */
    @Test
    void aPermitClosedConcurrentlyIsReleasedOnce() throws Exception {
        for (int round = 0; round < 50; round++) {
            Semaphore admission = new Semaphore(0);
            TikaGrpcServerImpl.ParseBytesPermit permit =
                    new TikaGrpcServerImpl.ParseBytesPermit(admission);
            CountDownLatch go = new CountDownLatch(1);
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                Thread t = new Thread(() -> {
                    try {
                        go.await();
                        permit.close();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                t.start();
                threads.add(t);
            }
            go.countDown();
            for (Thread t : threads) {
                t.join();
            }
            assertEquals(1, admission.availablePermits());
        }
    }

    /** Gzip edge cases for the fallback: an inflation bomb, a bare header, an ARC. */
    @Test
    void theGzipFallbackHandlesEdgeCases() throws Exception {
        ByteArrayOutputStream bomb = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bomb)) {
            out.write(new byte[16 * 1024 * 1024]);
        }
        long start = System.nanoTime();
        assertEquals("application/gzip", TikaGrpcV2ServerImpl.detectContentType(
                ByteString.copyFrom(bomb.toByteArray()), "zeros.gz"));
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5),
                "a highly compressed gzip is not inflated in full");

        assertEquals("application/gzip", TikaGrpcV2ServerImpl.detectContentType(
                ByteString.copyFrom(new byte[]{0x1f, (byte) 0x8b}), ""),
                "a gzip header with no body");

        ByteArrayOutputStream arc = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(arc)) {
            out.write("filedesc://crawl.arc 0.0.0.0 20240101000000 text/plain 76\n"
                    .getBytes(StandardCharsets.UTF_8));
        }
        assertEquals("application/arc+gz", TikaGrpcV2ServerImpl.detectContentType(
                ByteString.copyFrom(arc.toByteArray()), ""));
    }

    /** A success on bytes the caller marked truncated is reported as partial. */
    @Test
    void aSuccessOnTruncatedContentIsPartial() throws Exception {
        Mockito.when(v1.parseAdmitted(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(outcome());
        RecordingObserver observer = new RecordingObserver();

        v2.parseBytes(requestWithContent().setTruncated(true).build(), observer);

        ParseStatus status = observer.replies.get(0).getDocument().getStatus();
        assertEquals(ParseStatus.Status.STATUS_PARTIAL, status.getStatus());
        assertEquals("PARSE_SUCCESS", status.getPipesStatus(), "the raw pipes status is kept");
        assertTrue(status.getErrors(0).contains("truncated"), status.getErrorsList().toString());
    }

    /** Without the truncated flag a clean parse stays a success. */
    @Test
    void aSuccessOnCompleteContentStaysSuccess() throws Exception {
        Mockito.when(v1.parseAdmitted(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(outcome());
        RecordingObserver observer = new RecordingObserver();

        v2.parseBytes(requestWithContent().build(), observer);

        ParseStatus status = observer.replies.get(0).getDocument().getStatus();
        assertEquals(ParseStatus.Status.STATUS_SUCCESS, status.getStatus());
        assertEquals(0, status.getErrorsCount());
    }

    /** An interrupted parse ends the call with UNAVAILABLE and no Document. */
    @Test
    void anInterruptedParseEndsTheCall() throws Exception {
        Mockito.when(v1.parseAdmitted(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
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

    /** runParseBytes checks the declared size, so four bytes declared as the default limit + 1 are refused. */
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

    /**
     * Over parseBytesMaxConcurrent, a request is turned away at once, before its bytes are
     * staged, instead of holding them while it waits for a worker.
     */
    @Test
    void aRequestOverTheConcurrencyLimitIsRefusedBeforeItIsStaged(@TempDir Path tmp)
            throws Exception {
        TikaGrpcServerImpl real = realImpl(tmp);
        try {
            assertEquals(2 * real.pipesConfig.getNumClients(),
                    real.parseBytesAdmission.availablePermits(),
                    "the default is twice pipes.numClients");
            real.parseBytesAdmission = new Semaphore(0);

            assertThrows(TikaGrpcServerImpl.ParseBytesBusyException.class, () ->
                    real.runParseBytes(bytes("<x/>"), 4, "a.html", null));

            try (var files = Files.list(real.spool.directory())) {
                assertTrue(files.findAny().isEmpty(),
                        "a refused request must not have created a spool file");
            }
        } finally {
            real.postShutdown();
        }
    }

    @Test
    void aBusyServerAnswersResourceExhausted() throws Exception {
        Mockito.when(v1.admitParseBytes(Mockito.anyLong()))
                .thenThrow(new TikaGrpcServerImpl.ParseBytesBusyException());
        RecordingObserver observer = new RecordingObserver();

        v2.parseBytes(requestWithContent().build(), observer);

        assertEquals(0, observer.completions);
        assertEquals(Status.Code.RESOURCE_EXHAUSTED,
                Status.fromThrowable(observer.errors.get(0)).getCode());
    }

    /** No free pipes worker is RESOURCE_EXHAUSTED for ParseBytes too, not a failed Document. */
    @Test
    void noFreeWorkerAnswersResourceExhausted() throws Exception {
        Mockito.when(v1.parseAdmitted(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new TikaGrpcServerImpl.ParseBytesOutcome(null,
                        "CLIENT_UNAVAILABLE_WITHIN_MS", null, 1L));
        RecordingObserver observer = new RecordingObserver();

        v2.parseBytes(requestWithContent().build(), observer);

        assertTrue(observer.replies.isEmpty());
        assertEquals(Status.Code.RESOURCE_EXHAUSTED,
                Status.fromThrowable(observer.errors.get(0)).getCode());
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
        Mockito.when(v1.admitParseBytes(Mockito.anyLong()))
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
                    .when(spied).executeTuple(Mockito.any(), Mockito.anyLong(), Mockito.any());

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
                    .when(spied).executeTuple(Mockito.any(), Mockito.anyLong(), Mockito.any());

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
        Mockito.verify(spied).executeTuple(tuple.capture(), Mockito.anyLong(), Mockito.any());
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
     * postShutdown() releases the spool before it closes the embedded Ignite node, and closes
     * the node even when the config store fails.
     */
    @Test
    void postShutdownClosesIgniteLast(@TempDir Path tmp) throws Exception {
        TikaGrpcServerImpl real = realImpl(tmp);
        ConfigStore store = Mockito.spy(real.configStore);
        Mockito.doThrow(new IllegalStateException("store unavailable"))
                .when(store).containsKey(Mockito.anyString());
        real.configStore = store;
        AutoCloseable ignite = Mockito.mock(AutoCloseable.class);
        real.igniteStoreServer = ignite;

        real.postShutdown();

        InOrder order = Mockito.inOrder(store, ignite);
        order.verify(store).containsKey(real.spool.fetcherId());
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
     * Two servers on one "file" config store each read their own spool directory. The spool
     * fetchers come from each server's config file, so neither reaches the shared store
     * (TIKA-5001) and one server shutting down cannot touch the other's.
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
            assertFalse(storeHolds(store, first.spool.fetcherId()),
                    "a spool fetcher must stay out of the store");
            assertFalse(storeHolds(store, second.spool.fetcherId()));
        } finally {
            first.postShutdown();
        }
        try {
            assertEquals(second.spool.directory().toString(), spoolBasePath(second),
                    "the second server must keep its spool after the first one leaves");
        } finally {
            second.postShutdown();
        }
    }

    private static boolean storeHolds(Path store, String id) throws IOException {
        return Files.exists(store) && Files.readString(store).contains(id);
    }

    /**
     * The spool fetcher's basePath in the config the server's forks load. Read from the
     * effective config file, not through the fetcher manager, so the test holds no fetcher.
     */
    private static String spoolBasePath(TikaGrpcServerImpl impl) throws Exception {
        JsonNode fetcher = new ObjectMapper().readTree(impl.spool.effectiveConfig().toFile())
                .path("fetchers").path(impl.spool.fetcherId());
        assertFalse(fetcher.isMissingNode(), "the spool fetcher must be in the effective config");
        return fetcher.findValue("basePath").asText();
    }

    private static long spoolDirectories(Path tmpdir) throws IOException {
        try (var entries = Files.list(tmpdir)) {
            return entries.filter(p -> p.getFileName().toString().startsWith("tika-grpc-parse-bytes"))
                    .count();
        }
    }
}
