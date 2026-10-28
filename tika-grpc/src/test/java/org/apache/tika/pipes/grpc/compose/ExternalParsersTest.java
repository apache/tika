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
package org.apache.tika.pipes.grpc.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.grpc.Context;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.InsecureServerCredentials;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.TlsChannelCredentials;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.grpc.adapter.AdapterContribution;
import org.apache.tika.grpc.adapter.AdapterRequest;
import org.apache.tika.grpc.adapter.ContributionLimits;
import org.apache.tika.grpc.mapper.AdapterMerge;
import org.apache.tika.grpc.v2.Document;
import org.apache.tika.grpc.v2.EmbeddedDocument;
import org.apache.tika.grpc.v2.ExtensionResult;
import org.apache.tika.grpc.v2.MetadataField;
import org.apache.tika.grpc.v2.MetadataValue;
import org.apache.tika.grpc.v2.ParseStatus;
import org.apache.tika.grpc.v2.SourceOrigin;
import org.apache.tika.grpc.v2.StringValues;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.pipes.grpc.TikaGrpcConfig;
import org.apache.tika.pipes.grpc.compose.records.ListRecordsRequest;
import org.apache.tika.pipes.grpc.compose.records.ListRecordsResponse;

/**
 * WARC through the external parser adapters (TIKA-4771): the test adapter
 * {@link RecordListAdapter}, found on the classpath by its service file, calls an in-process
 * {@link FakeRecordList} with its own generated stubs, and the records come back as embedded
 * Documents in Tika's own types.
 */
class ExternalParsersTest {

    /**
     * The defaults leave room for the record headers of a WARC at the default ParseBytes
     * content limit (256 MiB), and the builder's and the merge's defaults agree.
     */
    @Test
    void theDefaultLimitsFitALargeWarc() {
        long mib = 1024L * 1024L;
        ExternalParserConfig entry = new ExternalParserConfig();
        assertEquals(100_000, entry.getMaxChildren());
        assertEquals(64 * mib, entry.getMaxEmbeddedBytes());
        assertEquals(128 * mib, entry.getMaxReplyBytes());
        assertEquals(96 * mib, new TikaGrpcConfig().getExternalParsersMaxAddedBytes());
        assertEquals(96 * mib, ExternalParsers.DEFAULT_MAX_ADDED_BYTES);

        assertEquals(AdapterMerge.Limits.DEFAULTS.maxChildren(),
                ContributionLimits.DEFAULTS.maxChildren());
        assertEquals(AdapterMerge.Limits.DEFAULTS.maxEmbeddedBytes(),
                ContributionLimits.DEFAULTS.maxBytes());
        assertEquals(AdapterMerge.Limits.DEFAULTS.maxKeysPerDocument(),
                ContributionLimits.DEFAULTS.maxKeysPerDocument());
        assertTrue(ExternalParsers.DEFAULT_MAX_ADDED_BYTES >= entry.getMaxEmbeddedBytes(),
                "one parser's embedded documents fit the shared budget");
    }

    private static final ByteString WARC = FakeRecordList.SAMPLE;

    private final List<AutoCloseable> closeables = new ArrayList<>();

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() throws Exception {
        for (AutoCloseable closeable : closeables) {
            closeable.close();
        }
    }

    private String startRecordList(FakeRecordList fake) throws Exception {
        String name = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(name)
                .addService(fake)
                .build()
                .start();
        closeables.add(() -> server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS));
        return name;
    }

    private ExternalParsers open(List<ExternalParserConfig> configs) {
        return open(configs, ExternalParserAdapters.load(getClass().getClassLoader()));
    }

    private ExternalParsers open(List<ExternalParserConfig> configs,
                                 ExternalParserAdapters adapters) {
        ExternalParsers parsers = ExternalParsers.open(configs, adapters,
                config -> InProcessChannelBuilder.forName(config.getTarget()).build());
        closeables.add(0, parsers);
        return parsers;
    }

    private static ExternalParserConfig records(String target) {
        ExternalParserConfig config = new ExternalParserConfig();
        config.setProducerId("records");
        config.setTarget(target);
        config.setPlaintext(true);
        config.setTimeoutMs(5000);
        return config;
    }

    private static Document tikaDocument(String contentType) {
        return Document.newBuilder()
                .setId("crawl-1")
                .setContentType(contentType)
                .setStatus(ParseStatus.newBuilder()
                        .setStatus(ParseStatus.Status.STATUS_SUCCESS)
                        .addParsersUsed("org.apache.tika.parser.warc.WARCParser"))
                .setOrigin(SourceOrigin.newBuilder().setSha256("abc").setByteSize(WARC.size()))
                .build();
    }

    private static Document compose(ExternalParsers parsers, Document core, ByteString bytes) {
        return parsers.compose(core, new Metadata(), bytes, "crawl.warc");
    }

    static MetadataValue extra(Document document, String key) {
        return extra(document.getExtraList(), key);
    }

    static MetadataValue extra(EmbeddedDocument document, String key) {
        return extra(document.getExtraList(), key);
    }

    static MetadataValue extra(List<MetadataField> fields, String key) {
        for (MetadataField field : fields) {
            if (field.getKey().equals(key)) {
                return field.getValue();
            }
        }
        return null;
    }

    @Test
    void theTestAdapterIsFoundOnTheClasspath() {
        ExternalParserAdapters adapters = ExternalParserAdapters.load(getClass().getClassLoader());

        assertTrue(adapters.get("records") instanceof RecordListAdapter, adapters.all().toString());
    }

    @Test
    void warcRecordsBecomeTypedEmbeddedDocuments() throws Exception {
        FakeRecordList fake = new FakeRecordList();
        ExternalParsers parsers = open(List.of(records(startRecordList(fake))));
        Document core = tikaDocument("application/warc+gz");

        Document composed = compose(parsers, core, WARC);

        // Tika's own fields stay; the parent only gains FastWARC's own key.
        assertEquals(core.getStatus(), composed.getStatus());
        assertEquals(core.getOrigin(), composed.getOrigin());
        assertEquals(core.getContentType(), composed.getContentType());
        assertEquals(core.getMetadata(), composed.getMetadata());
        assertEquals(2, extra(composed, "warc:record-count").getIntegers().getValues(0));

        assertEquals(1, composed.getExtensionsCount());
        ExtensionResult slot = composed.getExtensions(0);
        assertEquals("records", slot.getProducerId());
        assertEquals(ExtensionResult.Status.STATUS_OK, slot.getStatus(),
                "warnings=" + slot.getWarningsList());
        assertFalse(slot.hasPayload(), "the raw reply is only kept on request");
        assertEquals(2, slot.getEmbeddedReported());
        assertEquals(2, slot.getEmbeddedKept());
        assertFalse(slot.getTruncated());

        assertEquals(2, composed.getEmbeddedCount());
        EmbeddedDocument info = composed.getEmbedded(0);
        assertEquals("/record-1", info.getPath());
        assertEquals("records", info.getProducerId());
        assertEquals("warcinfo", extra(info, "warc:WARC-Type").getStrings().getValues(0));

        EmbeddedDocument record = composed.getEmbedded(1);
        assertEquals("/record-2", record.getPath());
        assertEquals("text/html; charset=UTF-8", record.getContentType());
        assertEquals("<urn:uuid:00000000-0000-0000-0000-000000000002>",
                record.getOrigin().getFilename());
        assertEquals(Instant.parse("2026-10-28T08:00:01Z").getEpochSecond(),
                record.getMetadata().getCreated().getSeconds(), "WARC-Date is dcterms:created");
        assertEquals("https://example.com/",
                extra(record, "warc:WARC-Target-URI").getStrings().getValues(0));
        assertEquals("200", extra(record, "warc:http:status").getStrings().getValues(0));
        assertEquals("OK", extra(record, "warc:http:status:reason").getStrings().getValues(0));
        assertEquals("text/html; charset=UTF-8",
                extra(record, "warc:http:Content-Type").getStrings().getValues(0));
        assertTrue(extra(record, "warc:stream-pos").getIntegers().getValues(0) > 0);

        ListRecordsRequest sent = fake.lastRequest.get();
        assertEquals(WARC, sent.getArchive());
        assertFalse(sent.getStrict());
    }

    @Test
    void optionsReachTheRequestAndTheRawReplyCanBeKept() throws Exception {
        FakeRecordList fake = new FakeRecordList();
        ExternalParserConfig config = records(startRecordList(fake));
        config.setOptions(new ObjectMapper().readTree("{\"strict\": true}"));
        config.setKeepRawReply(true);
        ExternalParsers parsers = open(List.of(config));

        ExtensionResult slot = compose(parsers, tikaDocument("application/warc"), WARC)
                .getExtensions(0);

        assertTrue(fake.lastRequest.get().getStrict());
        assertEquals("type.googleapis.com/tika.test.v1.ListRecordsResponse",
                slot.getPayload().getTypeUrl());
        assertEquals(2, slot.getPayload().unpack(ListRecordsResponse.class).getRecordsCount());
    }

    @Test
    void contentTypeParametersDoNotMatter() throws Exception {
        ExternalParsers parsers = open(List.of(records(startRecordList(new FakeRecordList()))));

        Document composed = compose(parsers, tikaDocument("application/warc; version=1.1"), WARC);

        assertEquals(ExtensionResult.Status.STATUS_OK, composed.getExtensions(0).getStatus());
    }

    @Test
    void otherContentTypesAreNotSent() throws Exception {
        FakeRecordList fake = new FakeRecordList();
        ExternalParsers parsers = open(List.of(records(startRecordList(fake))));
        Document core = tikaDocument("text/html");

        assertSame(core, compose(parsers, core, WARC));
        assertEquals(0, fake.calls.get());
    }

    /** A FastWARC error leaves Tika's Document intact, with a failed slot saying why. */
    @Test
    void aFailingCallLeavesAFailedSlot() throws Exception {
        FakeRecordList fake = new FakeRecordList();
        fake.failWith = Status.RESOURCE_EXHAUSTED.withDescription("decoded past 16 MiB");
        ExternalParsers parsers = open(List.of(records(startRecordList(fake))));
        Document core = tikaDocument("application/warc");

        Document composed = compose(parsers, core, WARC);

        assertEquals(core, composed.toBuilder().clearExtensions().build());
        ExtensionResult result = composed.getExtensions(0);
        assertEquals(ExtensionResult.Status.STATUS_FAILED, result.getStatus());
        assertTrue(result.getWarnings(0).contains("RESOURCE_EXHAUSTED"), result.getWarnings(0));
        assertTrue(result.getWarnings(0).contains("decoded past 16 MiB"), result.getWarnings(0));

        fake.failWith = null;
        assertEquals(ExtensionResult.Status.STATUS_OK,
                compose(parsers, core, WARC).getExtensions(0).getStatus());
    }

    @Test
    void anUnreachableParserLeavesAFailedSlot() {
        ExternalParsers parsers = open(List.of(records("nothing-listens-here")));

        ExtensionResult result = compose(parsers, tikaDocument("application/warc"), WARC)
                .getExtensions(0);

        assertEquals(ExtensionResult.Status.STATUS_FAILED, result.getStatus());
        assertEquals(1, result.getWarningsCount());
    }

    /**
     * FastWARC stopping at bytes it cannot read is a partial result: the records it read
     * before them are kept, and the slot is STATUS_PARTIAL with the reason.
     */
    @Test
    void anUnrecoverableRecordErrorIsPartialAndKeepsTheRecordsBeforeIt() throws Exception {
        ExternalParsers parsers = open(List.of(records(startRecordList(new FakeRecordList()))));
        ByteString broken = WARC.concat(ByteString.copyFromUtf8("garbage\r\n"));

        Document composed = compose(parsers, tikaDocument("application/warc"), broken);

        ExtensionResult slot = composed.getExtensions(0);
        assertEquals(ExtensionResult.Status.STATUS_PARTIAL, slot.getStatus());
        assertTrue(slot.getWarnings(0).contains("not a WARC record"),
                slot.getWarningsList().toString());
        assertEquals(2, composed.getEmbeddedCount());
        assertEquals(1, extra(composed, "warc:record-count") == null ? 0 : 1,
                "a partial contribution's parent keys are merged too");
    }

    /** A failed contribution merges nothing: no children and no parent keys. */
    @Test
    void aFailedContributionMergesNothing() throws Exception {
        ExternalParserConfig config = records(startRecordList(new FakeRecordList()));
        config.setKeepRawReply(true);
        ExternalParsers parsers = open(List.of(config),
                ExternalParserAdapters.of(new RecordListAdapter() {
                    @Override
                    public AdapterContribution map(ListRecordsResponse reply,
                                                   AdapterRequest request) {
                        return request.contribution()
                                .parentKey("warc:record-count", "2")
                                .addChild("/record-1", new Metadata())
                                .fail("the archive is not readable")
                                .build();
                    }
                }));
        Document core = tikaDocument("application/warc");

        Document composed = compose(parsers, core, WARC);

        assertEquals(core, composed.toBuilder().clearExtensions().build());
        ExtensionResult slot = composed.getExtensions(0);
        assertEquals(ExtensionResult.Status.STATUS_FAILED, slot.getStatus());
        assertEquals("the archive is not readable", slot.getWarnings(0));
        assertFalse(slot.hasPayload(), "a failed slot never carries the raw reply");
    }

    private ExternalParsers openWithMaxAddedBytes(ExternalParserConfig config, long maxAdded) {
        ExternalParsers parsers = ExternalParsers.open(List.of(config),
                ExternalParserAdapters.load(getClass().getClassLoader()),
                c -> InProcessChannelBuilder.forName(c.getTarget()).build(), maxAdded);
        closeables.add(0, parsers);
        return parsers;
    }

    /**
     * What the parsers add is kept under its limit: the raw reply goes first, then embedded
     * documents from the end, and the slot says it is truncated.
     */
    @Test
    void whatTheParsersAddIsKeptUnderItsLimit() throws Exception {
        ExternalParserConfig config = records(startRecordList(new FakeRecordList()));
        config.setKeepRawReply(true);
        Document core = tikaDocument("application/warc");
        Document full = compose(open(List.of(config)), core, WARC);
        long oneRecordLess = full.toBuilder()
                .setExtensions(0, full.getExtensions(0).toBuilder().clearPayload())
                .removeEmbedded(1).build().getSerializedSize()
                - core.getSerializedSize() + 200;

        Document composed = compose(openWithMaxAddedBytes(config, oneRecordLess), core, WARC);

        assertTrue(composed.toBuilder().clearExtensions().build().getSerializedSize()
                - core.getSerializedSize() <= oneRecordLess, "the slots are not counted");
        assertEquals(1, composed.getEmbeddedCount());
        ExtensionResult slot = composed.getExtensions(0);
        assertFalse(slot.hasPayload());
        assertTrue(slot.getTruncated());
        assertEquals(2, slot.getEmbeddedReported());
        assertEquals(1, slot.getEmbeddedKept());
    }

    /** Tika's own Document does not count against the parsers' limit and is never cut. */
    @Test
    void aLargeTikaDocumentDoesNotCostTheParsersTheirRecords() throws Exception {
        ExternalParserConfig config = records(startRecordList(new FakeRecordList()));
        Document core = tikaDocument("application/warc").toBuilder()
                .addExtra(MetadataField.newBuilder().setKey("x:big").setValue(
                        MetadataValue.newBuilder().setStrings(
                                StringValues.newBuilder().addValues("x".repeat(2_000_000)))))
                .build();

        Document composed = compose(openWithMaxAddedBytes(config, 1_000_000), core, WARC);

        assertEquals(2, composed.getEmbeddedCount());
        assertFalse(composed.getExtensions(0).getTruncated());
        assertEquals(core.getExtra(0), composed.getExtra(0));
    }

    /** An adapter that returns no contribution fails its slot; Tika's Document stays. */
    @Test
    void aNullContributionLeavesTikasDocumentUnchanged() throws Exception {
        ExternalParsers parsers = open(List.of(records(startRecordList(new FakeRecordList()))),
                ExternalParserAdapters.of(new RecordListAdapter() {
                    @Override
                    public AdapterContribution map(ListRecordsResponse reply,
                                                   AdapterRequest request) {
                        return null;
                    }
                }));
        Document core = tikaDocument("application/warc");

        Document composed = compose(parsers, core, WARC);

        assertEquals(core, composed.toBuilder().clearExtensions().build());
        ExtensionResult slot = composed.getExtensions(0);
        assertEquals(ExtensionResult.Status.STATUS_FAILED, slot.getStatus());
        assertTrue(slot.getWarnings(0).contains("no contribution"), slot.getWarnings(0));
    }

    /** A contribution built with limits of the adapter's own choosing is refused. */
    @Test
    void aContributionWithOtherLimitsIsRefused() throws Exception {
        ExternalParsers parsers = open(List.of(records(startRecordList(new FakeRecordList()))),
                ExternalParserAdapters.of(new RecordListAdapter() {
                    @Override
                    public AdapterContribution map(ListRecordsResponse reply,
                                                   AdapterRequest request) {
                        AdapterRequest roomier = new AdapterRequest(request.content(),
                                request.tikaMetadata(), request.resourceName(),
                                request.contentType(), request.options(),
                                new ContributionLimits(1_000_000, 100_000, 1_000_000,
                                        Long.MAX_VALUE, 1_000_000, 1_000_000));
                        return super.map(reply, roomier);
                    }
                }));
        Document core = tikaDocument("application/warc");

        Document composed = compose(parsers, core, WARC);

        assertEquals(core, composed.toBuilder().clearExtensions().build());
        assertTrue(composed.getExtensions(0).getWarnings(0).contains("other limits"),
                composed.getExtensions(0).getWarnings(0));
    }

    /**
     * The calls run in the caller's gRPC Context: its deadline and its cancellation reach
     * the parser's call, which fails its slot at once instead of waiting for timeoutMs.
     */
    @Test
    void theCallersDeadlineAndCancelReachTheParser() throws Exception {
        FakeRecordList fake = new FakeRecordList();
        CountDownLatch release = new CountDownLatch(1);
        fake.onCall = () -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        ExternalParserConfig config = records(startRecordList(fake));
        config.setTimeoutMs(30_000);
        ExternalParsers parsers = open(List.of(config));
        Document core = tikaDocument("application/warc");
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        closeables.add(scheduler::shutdownNow);
        closeables.add(0, release::countDown);

        long start = System.nanoTime();
        Context.CancellableContext deadline = Context.current()
                .withDeadlineAfter(300, TimeUnit.MILLISECONDS, scheduler);
        ExtensionResult timedOut = deadline.call(() -> compose(parsers, core, WARC))
                .getExtensions(0);
        deadline.cancel(null);
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5));
        assertEquals(ExtensionResult.Status.STATUS_FAILED, timedOut.getStatus());
        assertTrue(timedOut.getWarnings(0).contains("DEADLINE_EXCEEDED"),
                timedOut.getWarnings(0));

        Context.CancellableContext cancellable = Context.current().withCancellation();
        scheduler.schedule(() -> cancellable.cancel(null), 300, TimeUnit.MILLISECONDS);
        ExtensionResult cancelled = cancellable.call(() -> compose(parsers, core, WARC))
                .getExtensions(0);
        assertEquals(ExtensionResult.Status.STATUS_FAILED, cancelled.getStatus());
        assertTrue(cancelled.getWarnings(0).contains("CANCELLED"), cancelled.getWarnings(0));
    }

    /** Failure text is one line of at most 1024 characters, as elsewhere in v2. */
    @Test
    void failureTextIsBounded() throws Exception {
        ExternalParsers parsers = open(List.of(records(startRecordList(new FakeRecordList()))),
                ExternalParserAdapters.of(new RecordListAdapter() {
                    @Override
                    public AdapterContribution map(ListRecordsResponse reply,
                                                   AdapterRequest request) {
                        throw new IllegalStateException("x".repeat(5000) + "\nat secret.Frame");
                    }
                }));

        String warning = compose(parsers, tikaDocument("application/warc"), WARC)
                .getExtensions(0).getWarnings(0);

        assertEquals(1024, warning.length());
        assertFalse(warning.contains("secret"));
    }

    @Test
    void theChannelUsesTlsUnlessTheEntrySaysPlaintext() {
        ExternalParserConfig config = records("x");
        assertTrue(ExternalParsers.credentials(config) instanceof InsecureChannelCredentials);

        config.setPlaintext(false);
        assertTrue(ExternalParsers.credentials(config) instanceof TlsChannelCredentials,
                "TLS with the JVM's trust store by default");

        config.setTls(new ExternalParserConfig.Tls());
        config.getTls().setCaFile(tempDir.resolve("missing-ca.pem").toString());
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> ExternalParsers.credentials(config));
        assertTrue(missing.getMessage().contains("TLS files"), missing.getMessage());

        config.setPlaintext(true);
        assertThrows(IllegalArgumentException.class, () -> open(List.of(config)),
                "plaintext and tls together");

        ExternalParserConfig halfMutual = records("x");
        halfMutual.setPlaintext(false);
        halfMutual.setTls(new ExternalParserConfig.Tls());
        halfMutual.getTls().setCertFile("client.pem");
        assertThrows(IllegalArgumentException.class, () -> open(List.of(halfMutual)),
                "a client certificate without its key");
    }

    @Test
    void badOptionsStopTheServerAtStartup() throws Exception {
        ExternalParserConfig config = records("x");
        config.setOptions(new ObjectMapper().readTree("{\"strct\": true}"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> open(List.of(config)));
        assertTrue(e.getMessage().contains("bad options"), e.getMessage());
    }

    @Test
    void adapterDeclarationsAreCheckedAtStartup() {
        RecordListAdapter real = new RecordListAdapter();
        assertThrows(IllegalArgumentException.class, () -> open(List.of(records("x")),
                ExternalParserAdapters.of(new RecordListAdapter() {
                    @Override
                    public MethodDescriptor<ListRecordsRequest, ListRecordsResponse> method() {
                        return real.method().toBuilder()
                                .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                                .build();
                    }
                })), "a streaming method");
        assertThrows(IllegalArgumentException.class, () -> open(List.of(records("x")),
                ExternalParserAdapters.of(new RecordListAdapter() {
                    @Override
                    public Set<MediaType> supportedTypes() {
                        return Set.of();
                    }
                })), "no content types");

        ExternalParserConfig http = records("y");
        http.setProducerId("warc-http");
        IllegalArgumentException overlap = assertThrows(IllegalArgumentException.class,
                () -> open(List.of(records("x"), http), ExternalParserAdapters.of(real,
                        new RecordListAdapter() {
                            @Override
                            public String producerId() {
                                return "warc-http";
                            }

                            @Override
                            public String keyPrefix() {
                                return "warc:http:";
                            }
                        })));
        assertTrue(overlap.getMessage().contains("overlapping"), overlap.getMessage());
    }

    @Test
    void maxChildrenCapsTheEmbeddedDocuments() throws Exception {
        ExternalParserConfig config = records(startRecordList(new FakeRecordList()));
        config.setMaxChildren(1);
        ExternalParsers parsers = open(List.of(config));

        Document composed = compose(parsers, tikaDocument("application/warc"), WARC);

        assertEquals(1, composed.getEmbeddedCount());
        ExtensionResult slot = composed.getExtensions(0);
        assertEquals(ExtensionResult.Status.STATUS_OK, slot.getStatus());
        assertTrue(slot.getTruncated(), "a cut list is marked truncated");
        assertEquals(2, slot.getEmbeddedReported());
        assertEquals(1, slot.getEmbeddedKept());
        assertTrue(slot.getWarningsList().stream().anyMatch(w -> w.contains("maxChildren")),
                slot.getWarningsList().toString());
    }

    /** An adapter that throws: its slot fails and Tika's Document is unchanged. */
    @Test
    void anAdapterThatThrowsLeavesTikasDocumentUnchanged() throws Exception {
        ExternalParsers parsers = open(List.of(records(startRecordList(new FakeRecordList()))),
                ExternalParserAdapters.of(new RecordListAdapter() {
                    @Override
                    public AdapterContribution map(ListRecordsResponse reply,
                                                   AdapterRequest request) {
                        throw new IllegalStateException("mapping bug");
                    }
                }));
        Document core = tikaDocument("application/warc");

        Document composed = compose(parsers, core, WARC);

        assertEquals(core, composed.toBuilder().clearExtensions().build());
        ExtensionResult slot = composed.getExtensions(0);
        assertEquals(ExtensionResult.Status.STATUS_FAILED, slot.getStatus());
        assertTrue(slot.getWarnings(0).contains("mapping bug"), slot.getWarnings(0));
    }

    /**
     * Whatever an adapter writes, the server adds only its prefixed parent keys and its
     * children: a parent key outside the prefix never reaches the Document.
     */
    @Test
    void anAdapterCannotWriteTikasParentFields() throws Exception {
        ExternalParsers parsers = open(List.of(records(startRecordList(new FakeRecordList()))),
                ExternalParserAdapters.of(new RecordListAdapter() {
                    @Override
                    public AdapterContribution map(ListRecordsResponse reply,
                                                   AdapterRequest request) {
                        return request.contribution()
                                .parentKey("dc:title", "Hijacked")
                                .parentKey("Content-Type", "text/plain")
                                .parentKey("warc:ok", "yes")
                                .build();
                    }
                }));
        Document core = tikaDocument("application/warc");

        Document composed = compose(parsers, core, WARC);

        assertEquals(core.getMetadata(), composed.getMetadata());
        assertEquals(core.getContentType(), composed.getContentType());
        assertEquals(1, composed.getExtraCount());
        assertEquals("yes", extra(composed, "warc:ok").getStrings().getValues(0));
        assertTrue(composed.getExtensions(0).getWarningsList()
                .contains("dropped 2 parent keys outside warc:"),
                composed.getExtensions(0).getWarningsList().toString());
    }

    @Test
    void anAdapterWithTikasOwnPrefixIsRefused() {
        for (String prefix : List.of("tk:", "dc:", "Content-", "X-TIKA:", "pdf:", "xmpMM:")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> open(List.of(records("x")), ExternalParserAdapters.of(
                            new RecordListAdapter() {
                                @Override
                                public String keyPrefix() {
                                    return prefix;
                                }
                            })), prefix);
            assertTrue(e.getMessage().contains("Tika's own"), e.getMessage());
        }
    }

    /**
     * A reply over the message limit fails its slot, not the call: on a real Netty server and
     * the production channel, since the in-process transport does not apply the limit.
     */
    @Test
    void aReplyOverTheMessageLimitLeavesAFailedSlot() throws Exception {
        Server server = Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                .addService(new FakeRecordList())
                .build()
                .start();
        closeables.add(() -> server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS));
        int oneReply = FakeRecordList.parse(WARC).getSerializedSize();
        ExternalParserConfig config = records("localhost:" + server.getPort());
        config.setMaxReplyBytes(2 * oneReply);
        ExternalParsers parsers = ExternalParsers.open(List.of(config),
                ExternalParsers.DEFAULT_MAX_ADDED_BYTES);
        closeables.add(0, parsers);
        Document core = tikaDocument("application/warc");

        ExtensionResult small = compose(parsers, core, WARC).getExtensions(0);
        assertEquals(ExtensionResult.Status.STATUS_OK, small.getStatus(),
                "warnings=" + small.getWarningsList());

        ByteString many = WARC;
        for (int i = 0; i < 4; i++) {
            many = many.concat(many);
        }
        Document composed = compose(parsers, core, many);
        assertEquals(core, composed.toBuilder().clearExtensions().build());
        ExtensionResult big = composed.getExtensions(0);
        assertEquals(ExtensionResult.Status.STATUS_FAILED, big.getStatus());
        assertTrue(big.getWarnings(0).contains("RESOURCE_EXHAUSTED"), big.getWarnings(0));
    }

    @Test
    void documentsOverTheLimitAreSkipped() throws Exception {
        FakeRecordList fake = new FakeRecordList();
        ExternalParserConfig config = records(startRecordList(fake));
        config.setMaxContentBytes((long) WARC.size() - 1);
        ExternalParsers parsers = open(List.of(config));

        ExtensionResult result = compose(parsers, tikaDocument("application/warc"), WARC)
                .getExtensions(0);

        assertEquals(ExtensionResult.Status.STATUS_SKIPPED, result.getStatus());
        assertEquals(0, fake.calls.get());
    }

    @Test
    void incompleteDuplicateOrUnknownEntriesAreRefused() {
        ExternalParserConfig noTarget = records(" ");
        assertThrows(IllegalArgumentException.class, () -> open(List.of(noTarget)));

        assertThrows(IllegalArgumentException.class,
                () -> open(List.of(records("x"), records("y"))));

        ExternalParserConfig noAdapter = records("x");
        noAdapter.setProducerId("calamine");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> open(List.of(noAdapter)));
        assertTrue(e.getMessage().contains("no adapter jar"), e.getMessage());
        assertTrue(e.getMessage().contains("records"), "names the adapters found");
    }

    @Test
    void loadsFromTheGrpcSection() throws Exception {
        Path file = tempDir.resolve("tika-config.json");
        Files.writeString(file, """
                {"grpc": {"externalParsersMaxAddedBytes": 1048576, "externalParsers": [{
                  "producerId": "records",
                  "target": "localhost:50060",
                  "tls": {"caFile": "/etc/tika/ca.pem", "certFile": "c.pem", "keyFile": "k.pem"},
                  "timeoutMs": 30000,
                  "maxContentBytes": 16777216,
                  "maxChildren": 500,
                  "keepRawReply": true,
                  "maxReplyBytes": 1000000,
                  "options": {"strict": true}
                }]}}
                """, StandardCharsets.UTF_8);

        TikaGrpcConfig grpc = TikaGrpcConfig.load(TikaJsonConfig.load(file));
        List<ExternalParserConfig> configs = grpc.getExternalParsers();

        assertEquals(1048576L, grpc.getExternalParsersMaxAddedBytes());
        assertEquals(1, configs.size());
        ExternalParserConfig config = configs.get(0);
        assertFalse(config.isPlaintext());
        assertEquals("/etc/tika/ca.pem", config.getTls().getCaFile());
        assertEquals("c.pem", config.getTls().getCertFile());
        assertEquals("k.pem", config.getTls().getKeyFile());
        assertEquals("records", config.getProducerId());
        assertEquals("localhost:50060", config.getTarget());
        assertEquals(30000, config.getTimeoutMs());
        assertEquals(16777216L, config.getMaxContentBytes());
        assertEquals(500, config.getMaxChildren());
        assertTrue(config.isKeepRawReply());
        assertEquals(1000000, config.getMaxReplyBytes());
        assertEquals("{\"strict\":true}", config.getOptions().toString());
    }

    /** An entry with a key tika-grpc does not know fails to load, rather than being ignored. */
    @Test
    void unknownEntryKeysAreRefused() throws Exception {
        Path file = tempDir.resolve("tika-config.json");
        Files.writeString(file, """
                {"grpc": {"externalParsers": [{
                  "producerId": "records",
                  "target": "localhost:50060",
                  "method": "tika.test.v1.RecordListService/ListRecords"
                }]}}
                """, StandardCharsets.UTF_8);

        assertThrows(Exception.class, () -> TikaGrpcConfig.load(TikaJsonConfig.load(file)));
    }

    /**
     * An adapter that throws an Error (a missing class in its jar, a stack overflow) costs its
     * slot, not the call: Tika's Document comes back unchanged.
     */
    @Test
    void anAdapterThatThrowsAnErrorLeavesTikasDocumentUnchanged() throws Exception {
        for (Error error : List.of(new NoClassDefFoundError("com/example/Missing"),
                new StackOverflowError(), new AssertionError("bug"))) {
            ExternalParsers parsers = open(List.of(records(startRecordList(new FakeRecordList()))),
                    ExternalParserAdapters.of(new RecordListAdapter() {
                        @Override
                        public AdapterContribution map(ListRecordsResponse reply,
                                                       AdapterRequest request) {
                            throw error;
                        }
                    }));
            Document core = tikaDocument("application/warc");

            Document composed = compose(parsers, core, WARC);

            assertEquals(core, composed.toBuilder().clearExtensions().build(), error.toString());
            assertEquals(ExtensionResult.Status.STATUS_FAILED,
                    composed.getExtensions(0).getStatus());
        }
    }

    /** OutOfMemoryError is not swallowed: the server must see it. */
    @Test
    void outOfMemoryIsRethrown() throws Exception {
        ExternalParsers parsers = open(List.of(records(startRecordList(new FakeRecordList()))),
                ExternalParserAdapters.of(new RecordListAdapter() {
                    @Override
                    public AdapterContribution map(ListRecordsResponse reply,
                                                   AdapterRequest request) {
                        throw new OutOfMemoryError("test");
                    }
                }));
        assertThrows(OutOfMemoryError.class,
                () -> compose(parsers, tikaDocument("application/warc"), WARC));
    }

    /** A cancel that arrives while the adapter maps the reply merges nothing. */
    @Test
    void aCancelDuringMapMergesNothing() throws Exception {
        Context.CancellableContext cancellable = Context.current().withCancellation();
        ExternalParsers parsers = open(List.of(records(startRecordList(new FakeRecordList()))),
                ExternalParserAdapters.of(new RecordListAdapter() {
                    @Override
                    public AdapterContribution map(ListRecordsResponse reply,
                                                   AdapterRequest request) {
                        cancellable.cancel(null);
                        return super.map(reply, request);
                    }
                }));
        Document core = tikaDocument("application/warc");

        Document composed = cancellable.call(() -> compose(parsers, core, WARC));

        assertEquals(core, composed.toBuilder().clearExtensions().build());
        assertEquals(ExtensionResult.Status.STATUS_FAILED, composed.getExtensions(0).getStatus());
        assertTrue(composed.getExtensions(0).getWarnings(0).contains("cancelled"),
                composed.getExtensions(0).getWarnings(0));
    }

    /** A call already cancelled calls no parser at all. */
    @Test
    void aCancelledCallCallsNoParser() throws Exception {
        FakeRecordList fake = new FakeRecordList();
        ExternalParsers parsers = open(List.of(records(startRecordList(fake))));
        Context.CancellableContext cancelled = Context.current().withCancellation();
        cancelled.cancel(null);

        Document composed = cancelled.call(() ->
                compose(parsers, tikaDocument("application/warc"), WARC));

        assertEquals(0, fake.calls.get());
        assertEquals(ExtensionResult.Status.STATUS_FAILED, composed.getExtensions(0).getStatus());
    }

    /**
     * An adapter that changes its options object changes nothing for the next request: each
     * request gets its own copy.
     */
    @Test
    void anAdapterCannotChangeItsOptionsForLaterRequests() throws Exception {
        ExternalParserConfig config = records(startRecordList(new FakeRecordList()));
        config.setOptions(new ObjectMapper().readTree("{\"strict\": false}"));
        List<String> seen = new ArrayList<>();
        ExternalParsers parsers = open(List.of(config),
                ExternalParserAdapters.of(new RecordListAdapter() {
                    @Override
                    public ListRecordsRequest buildRequest(AdapterRequest request) {
                        seen.add(request.options().toString());
                        ((com.fasterxml.jackson.databind.node.ObjectNode) request.options())
                                .put("strict", true).put("injected", 1);
                        return super.buildRequest(request);
                    }
                }));

        compose(parsers, tikaDocument("application/warc"), WARC);
        compose(parsers, tikaDocument("application/warc"), WARC);

        assertEquals(List.of("{\"strict\":false}", "{\"strict\":false}"), seen);
    }

    /**
     * Two parsers share one maxAddedBytes budget: the second only gets what the first left,
     * and the reply stays within the budget plus the slots.
     */
    @Test
    void parsersShareOneAddedBytesBudget() throws Exception {
        ExternalParserConfig first = records(startRecordList(new FakeRecordList()));
        ExternalParserConfig second = records(startRecordList(new FakeRecordList()));
        second.setProducerId("records2");
        RecordListAdapter other = new RecordListAdapter() {
            @Override
            public String producerId() {
                return "records2";
            }

            @Override
            public String keyPrefix() {
                return "rec2:";
            }
        };
        Document core = tikaDocument("application/warc");
        Document alone = compose(openWith(List.of(first), Long.MAX_VALUE,
                new RecordListAdapter()), core, WARC);
        long oneParser = alone.toBuilder().clearExtensions().build().getSerializedSize()
                - core.getSerializedSize();

        ExternalParsers both = openWith(List.of(first, second), oneParser,
                new RecordListAdapter(), other);
        Document composed = compose(both, core, WARC);

        long added = composed.toBuilder().clearExtensions().build().getSerializedSize()
                - core.getSerializedSize();
        assertTrue(added <= oneParser, "added " + added);
        assertEquals(2, composed.getEmbeddedCount(), "the first parser's records fit");
        ExtensionResult secondSlot = composed.getExtensions(1);
        assertEquals("records2", secondSlot.getProducerId());
        assertTrue(secondSlot.getTruncated(), secondSlot.toString());
        assertEquals(0, secondSlot.getEmbeddedKept());
    }

    /** Parent keys past the budget are dropped too, as a last resort, and the slot says so. */
    @Test
    void parentKeysOverTheBudgetAreDropped() throws Exception {
        ExternalParserConfig config = records(startRecordList(new FakeRecordList()));
        ExternalParsers parsers = openWith(List.of(config), 64, new RecordListAdapter() {
            @Override
            public AdapterContribution map(ListRecordsResponse reply, AdapterRequest request) {
                return request.contribution().parentKey("warc:big", "x".repeat(4000)).build();
            }
        });
        Document core = tikaDocument("application/warc");

        Document composed = compose(parsers, core, WARC);

        assertEquals(core.getExtraList(), composed.getExtraList());
        assertTrue(composed.getExtensions(0).getWarningsList().stream()
                .anyMatch(w -> w.contains("parent keys dropped")),
                composed.getExtensions(0).getWarningsList().toString());
    }

    private ExternalParsers openWith(List<ExternalParserConfig> configs, long maxAdded,
                                     RecordListAdapter... adapters) {
        ExternalParsers parsers = ExternalParsers.open(configs,
                ExternalParserAdapters.of(adapters),
                c -> InProcessChannelBuilder.forName(c.getTarget()).build(), maxAdded);
        closeables.add(0, parsers);
        return parsers;
    }

    /** Prefixes are compared ignoring case: WARC: and warc:http: overlap. */
    @Test
    void prefixesThatDifferOnlyInCaseOverlap() throws Exception {
        ExternalParserConfig first = records("x");
        ExternalParserConfig second = records("y");
        second.setProducerId("records2");
        RecordListAdapter upper = new RecordListAdapter() {
            @Override
            public String producerId() {
                return "records2";
            }

            @Override
            public String keyPrefix() {
                return "WARC:http:";
            }
        };
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> openWith(List.of(first, second), 1024, new RecordListAdapter(), upper));
        assertTrue(e.getMessage().contains("overlapping"), e.getMessage());
    }

    /** A broken or duplicated adapter jar that no entry names does not stop the server. */
    @Test
    void anUnconfiguredBrokenAdapterIsIgnored() throws Exception {
        RecordListAdapter broken = new RecordListAdapter() {
            @Override
            public String producerId() {
                throw new NoClassDefFoundError("com/example/Missing");
            }
        };
        RecordListAdapter dupA = new RecordListAdapter() {
            @Override
            public String producerId() {
                return "dup";
            }
        };
        RecordListAdapter dupB = new RecordListAdapter() {
            @Override
            public String producerId() {
                return "dup";
            }
        };
        ExternalParserAdapters adapters =
                ExternalParserAdapters.of(broken, dupA, dupB, new RecordListAdapter());

        ExternalParsers parsers = open(List.of(records(startRecordList(new FakeRecordList()))),
                adapters);
        assertFalse(parsers.isEmpty());

        ExternalParserConfig dup = records("x");
        dup.setProducerId("dup");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> open(List.of(dup), adapters));
        assertTrue(e.getMessage().contains("two external parser adapters"), e.getMessage());
        ExternalParserConfig missing = records("x");
        missing.setProducerId("missing");
        e = assertThrows(IllegalArgumentException.class, () -> open(List.of(missing), adapters));
        assertTrue(e.getMessage().contains("failed to load"), e.getMessage());
    }

    @Test
    void producerIdsMustBePlainLowerCaseNames() {
        for (String bad : List.of("Records", "re cords", "-records", "records/1", "x".repeat(65))) {
            ExternalParserConfig config = records("x");
            config.setProducerId(bad);
            assertThrows(IllegalArgumentException.class, config::validate, bad);
        }
    }

    /** A target the gRPC channel cannot use stops the server at startup, not at first call. */
    @Test
    void anUnusableTargetIsRefusedAtStartup() {
        ExternalParserConfig config = records("nosuchscheme://host:1/x");
        assertThrows(IllegalArgumentException.class,
                () -> ExternalParsers.open(List.of(config), 1024));
    }

    /** Routing is by base type only; similar and blank types are not sent. */
    @Test
    void routingNegatives() throws Exception {
        FakeRecordList fake = new FakeRecordList();
        ExternalParsers parsers = open(List.of(records(startRecordList(fake))));
        for (String type : List.of("", "application/warcx", "application/x-warc", "text/plain",
                "application/gzip", "not a type")) {
            Document core = tikaDocument(type);
            assertSame(core, compose(parsers, core, WARC), type);
        }
        assertEquals(0, fake.calls.get());
    }

    @Test
    void noExternalParsersByDefault() throws Exception {
        Path file = tempDir.resolve("tika-config.json");
        Files.writeString(file, "{\"grpc\": {}}", StandardCharsets.UTF_8);

        assertTrue(TikaGrpcConfig.load(TikaJsonConfig.load(file)).getExternalParsers().isEmpty());
        assertTrue(ExternalParsers.open(List.of(),
                ExternalParsers.DEFAULT_MAX_ADDED_BYTES).isEmpty());
    }
}
