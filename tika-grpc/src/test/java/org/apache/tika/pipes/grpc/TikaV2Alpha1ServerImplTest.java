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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.InsecureServerCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.apache.tika.grpc.v2alpha1.Cardinality;
import org.apache.tika.grpc.v2alpha1.FetchAndParseBidiRequest;
import org.apache.tika.grpc.v2alpha1.FetchAndParseBidiResponse;
import org.apache.tika.grpc.v2alpha1.FetchAndParseRequest;
import org.apache.tika.grpc.v2alpha1.FetchAndParseResponse;
import org.apache.tika.grpc.v2alpha1.FetchAndParseStreamRequest;
import org.apache.tika.grpc.v2alpha1.ParsedDocument;
import org.apache.tika.grpc.v2alpha1.TikaServiceGrpc;
import org.apache.tika.grpc.v2alpha1.ValueType;
import org.apache.tika.grpc.v2alpha1.Values;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.serialization.config.JsonConfigHelper;

@ExtendWith(GrpcCleanupExtension.class)
public class TikaV2Alpha1ServerImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // a file-system fetcher rooted at target/, from tika-pipes-test-config.json
    private static final String FETCHER_ID = "nick1.is.cool.super-fs";
    private static final String DOC_DIR = "v2alpha1-" + UUID.randomUUID();
    private static final String ZIP_KEY = DOC_DIR + "/two-texts.zip";
    private static final String BIG_KEY = DOC_DIR + "/big.txt";

    static Path tikaConfig = Paths.get("target", "tika-config-v2alpha1-" + UUID.randomUUID() + ".json");
    static Path tikaConfigUnlocked =
            Paths.get("target", "tika-config-v2alpha1-unlocked-" + UUID.randomUUID() + ".json");

    private final List<TikaGrpcServerImpl> services = new ArrayList<>();

    @BeforeAll
    static void init() throws Exception {
        Path target = Paths.get("target").toAbsolutePath();
        Map<String, Object> replacements = new HashMap<>();
        replacements.put("JAVA_PATH", Paths.get(System.getProperty("java.home"), "bin", "java"));
        replacements.put("FETCHER_BASE_PATH", target);
        replacements.put("PLUGIN_ROOTS", target.resolve("plugins"));
        JsonConfigHelper.writeConfigFromResource("/tika-pipes-test-config.json",
                TikaV2Alpha1ServerImplTest.class, replacements, tikaConfig);

        ObjectNode root = (ObjectNode) OBJECT_MAPPER.readTree(tikaConfig.toFile());
        root.set("grpc", OBJECT_MAPPER.createObjectNode().put("allowPerRequestConfig", true));
        Files.writeString(tikaConfigUnlocked, OBJECT_MAPPER.writeValueAsString(root),
                StandardCharsets.UTF_8);

        Path docs = Files.createDirectories(target.resolve(DOC_DIR));
        try (OutputStream os = Files.newOutputStream(docs.resolve("two-texts.zip"));
                ZipOutputStream zip = new ZipOutputStream(os)) {
            for (String name : List.of("alpha", "beta")) {
                ZipEntry entry = new ZipEntry(name + ".txt");
                entry.setTime(1_600_000_000_000L);
                zip.putNextEntry(entry);
                zip.write((name + "-marker text").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        // ~5 MB of text: over gRPC's default 4 MB client limit once extracted
        String line = "the quick brown fox jumps over the lazy dog 0123456789\n";
        Files.writeString(docs.resolve("big.txt"), line.repeat(5 * 1024 * 1024 / line.length() + 1),
                StandardCharsets.UTF_8);
    }

    @AfterAll
    static void clean() throws Exception {
        Files.deleteIfExists(tikaConfig);
        Files.deleteIfExists(tikaConfigUnlocked);
        FileUtils.deleteDirectory(Paths.get("target").resolve(DOC_DIR).toFile());
    }

    @AfterEach
    void closeServices() throws Exception {
        for (TikaGrpcServerImpl service : services) {
            service.shutdown();
            service.postShutdown();
        }
        services.clear();
    }

    private ManagedChannel start(Resources resources, Path config) throws Exception {
        String name = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(newService(config)).build().start();
        resources.register(server, Duration.ofSeconds(10));
        ManagedChannel channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        resources.register(channel, Duration.ofSeconds(10));
        return channel;
    }

    // in-process transport hands messages over as objects and never checks their size
    private ManagedChannel startOverNetwork(Resources resources, Path config) throws Exception {
        Server server = Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                .addService(newService(config)).build().start();
        resources.register(server, Duration.ofSeconds(10));
        ManagedChannel channel = Grpc.newChannelBuilderForAddress("localhost", server.getPort(),
                InsecureChannelCredentials.create()).build();
        resources.register(channel, Duration.ofSeconds(10));
        return channel;
    }

    private TikaV2Alpha1ServerImpl newService(Path config) throws Exception {
        TikaGrpcServerImpl v1 = new TikaGrpcServerImpl(config.toAbsolutePath().toString());
        services.add(v1);
        return new TikaV2Alpha1ServerImpl(v1.runtime);
    }

    private static FetchAndParseRequest request(String fetcherId, String fetchKey) {
        return FetchAndParseRequest.newBuilder().setFetcherId(fetcherId).setFetchKey(fetchKey).build();
    }

    @Test
    public void unaryReturnsTheContainerAndEachEmbeddedDocument(Resources resources) throws Exception {
        FetchAndParseResponse response = TikaServiceGrpc.newBlockingStub(start(resources, tikaConfig))
                .fetchAndParse(request(FETCHER_ID, ZIP_KEY));

        assertEquals("PARSE_SUCCESS", response.getPipesStatus(), response.getMessage());
        assertEquals(3, response.getDocumentsCount());
        ParsedDocument container = response.getDocuments(0);
        assertEquals("", container.getEmbeddedPath());
        for (int i = 1; i < 3; i++) {
            ParsedDocument embedded = response.getDocuments(i);
            assertTrue(embedded.getEmbeddedPath().startsWith("/"), embedded.getEmbeddedPath());
            assertFalse(embedded.containsMetadata("tk:content"), "content is not repeated in metadata");
        }
        assertTrue(response.getDocuments(1).getContent().contains("alpha-marker"));
        assertTrue(response.getDocuments(2).getContent().contains("beta-marker"));

        // hints from the registry: INTEGER, DATE, a BAG of text, and an unhinted key
        ParsedDocument alpha = response.getDocuments(1);
        assertEquals(ValueType.VALUE_TYPE_INTEGER, alpha.getMetadataOrThrow("tk:embedded-depth").getType());
        assertEquals(ValueType.VALUE_TYPE_DATE, alpha.getMetadataOrThrow("fs:modified").getType());
        Values parsedBy = alpha.getMetadataOrThrow("tk:parsed-by");
        assertEquals(ValueType.VALUE_TYPE_UNSPECIFIED, parsedBy.getType());
        assertEquals(Cardinality.CARDINALITY_BAG, parsedBy.getCardinality());
        assertTrue(parsedBy.getValuesCount() >= 1);
    }

    @Test
    public void streamSendsTheSameDocumentsOnePerMessage(Resources resources) throws Exception {
        ManagedChannel channel = start(resources, tikaConfig);
        List<ParsedDocument> unary = TikaServiceGrpc.newBlockingStub(channel)
                .fetchAndParse(request(FETCHER_ID, ZIP_KEY)).getDocumentsList();
        List<ParsedDocument> streamed = new ArrayList<>();
        TikaServiceGrpc.newBlockingStub(channel).fetchAndParseStream(FetchAndParseStreamRequest
                .newBuilder().setFetcherId(FETCHER_ID).setFetchKey(ZIP_KEY).build())
                .forEachRemaining(r -> streamed.add(r.getDocument()));

        assertEquals(unary.size(), streamed.size());
        for (int i = 0; i < unary.size(); i++) {
            assertEquals(unary.get(i).getEmbeddedPath(), streamed.get(i).getEmbeddedPath());
            assertEquals(unary.get(i).getContent(), streamed.get(i).getContent());
        }
    }

    @Test
    public void anUnknownFetcherIsNotFound(Resources resources) throws Exception {
        StatusRuntimeException e = assertThrows(
                StatusRuntimeException.class, () -> TikaServiceGrpc
                        .newBlockingStub(start(resources, tikaConfig))
                        .fetchAndParse(request("no-such-fetcher", ZIP_KEY)));
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    @Test
    public void aMissingFileIsADocumentOutcomeWithAMessage(Resources resources) throws Exception {
        FetchAndParseResponse response = TikaServiceGrpc.newBlockingStub(start(resources, tikaConfig))
                .fetchAndParse(request(FETCHER_ID, DOC_DIR + "/does-not-exist.txt"));
        assertEquals("FETCH_EXCEPTION", response.getPipesStatus());
        assertFalse(response.getMessage().isEmpty());
    }

    @Test
    public void perRequestConfigIsDeniedUnlessAllowed(Resources resources) throws Exception {
        FetchAndParseRequest withConfig = request(FETCHER_ID, ZIP_KEY).toBuilder()
                .setParseContextJson("{}").build();
        StatusRuntimeException e = assertThrows(
                StatusRuntimeException.class, () -> TikaServiceGrpc
                        .newBlockingStub(start(resources, tikaConfig)).fetchAndParse(withConfig));
        assertEquals(Status.Code.PERMISSION_DENIED, e.getStatus().getCode());
    }

    @Test
    public void bidiAnswersEveryRequestAndReportsFailuresInBand(Resources resources) throws Exception {
        ManagedChannel channel = start(resources, tikaConfig);
        Recording responses = new Recording();
        StreamObserver<FetchAndParseBidiRequest> requests =
                TikaServiceGrpc.newStub(channel).fetchAndParseBidi(responses);
        requests.onNext(bidi("1", FETCHER_ID, ZIP_KEY, ""));
        requests.onNext(bidi("2", "no-such-fetcher", ZIP_KEY, ""));
        // denied per request on a locked server, not for the whole stream
        requests.onNext(bidi("3", FETCHER_ID, ZIP_KEY, "{}"));
        requests.onNext(bidi("4", FETCHER_ID, ZIP_KEY, ""));
        requests.onCompleted();
        assertTrue(responses.done.await(60, TimeUnit.SECONDS));

        assertTrue(responses.errors.isEmpty(), String.valueOf(responses.errors));
        assertEquals(4, responses.responses.size());
        Map<String, FetchAndParseBidiResponse> byId = new HashMap<>();
        responses.responses.forEach(r -> byId.put(r.getRequestId(), r));
        assertEquals(3, byId.get("1").getResult().getDocumentsCount());
        assertEquals(Status.Code.NOT_FOUND.value(), byId.get("2").getError().getCode());
        assertEquals(Status.Code.PERMISSION_DENIED.value(), byId.get("3").getError().getCode());
        assertEquals(3, byId.get("4").getResult().getDocumentsCount());
    }

    @Test
    public void aReplyOverFourMegabytesNeedsARaisedClientLimit(Resources resources) throws Exception {
        ManagedChannel channel = startOverNetwork(resources, tikaConfig);
        StatusRuntimeException e = assertThrows(
                StatusRuntimeException.class, () -> TikaServiceGrpc.newBlockingStub(channel)
                        .fetchAndParse(request(FETCHER_ID, BIG_KEY)));
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, e.getStatus().getCode());

        FetchAndParseResponse response = TikaServiceGrpc.newBlockingStub(channel)
                .withMaxInboundMessageSize(64 * 1024 * 1024)
                .fetchAndParse(request(FETCHER_ID, BIG_KEY));
        assertTrue(response.getDocuments(0).getContent().length() > 4 * 1024 * 1024);
    }

    @Test
    public void hintsComeFromTheRegistry() {
        Metadata metadata = new Metadata();
        metadata.add("pdf:chars-per-page", "10");
        metadata.add("pdf:chars-per-page", "20");
        metadata.set("pdf:encrypted", "false");
        metadata.set("x-not-registered", "anything");
        ParsedDocument document = new TikaV2Alpha1ServerImpl(null).toDocument(metadata);

        Values chars = document.getMetadataOrThrow("pdf:chars-per-page");
        assertEquals(List.of("10", "20"), chars.getValuesList());
        assertEquals(ValueType.VALUE_TYPE_INTEGER, chars.getType());
        assertEquals(Cardinality.CARDINALITY_SEQ, chars.getCardinality());
        assertEquals(ValueType.VALUE_TYPE_BOOLEAN, document.getMetadataOrThrow("pdf:encrypted").getType());
        Values unknown = document.getMetadataOrThrow("x-not-registered");
        assertEquals(ValueType.VALUE_TYPE_UNSPECIFIED, unknown.getType());
        assertEquals(Cardinality.CARDINALITY_UNSPECIFIED, unknown.getCardinality());
    }

    private static FetchAndParseBidiRequest bidi(String id, String fetcherId, String fetchKey,
                                                 String parseContextJson) {
        return FetchAndParseBidiRequest.newBuilder().setRequestId(id).setFetcherId(fetcherId)
                .setFetchKey(fetchKey).setParseContextJson(parseContextJson).build();
    }

    private static final class Recording implements StreamObserver<FetchAndParseBidiResponse> {
        private final List<FetchAndParseBidiResponse> responses =
                Collections.synchronizedList(new ArrayList<>());
        private final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch done = new CountDownLatch(1);

        @Override
        public void onNext(FetchAndParseBidiResponse response) {
            responses.add(response);
        }

        @Override
        public void onError(Throwable t) {
            errors.add(t);
            done.countDown();
        }

        @Override
        public void onCompleted() {
            done.countDown();
        }
    }
}
