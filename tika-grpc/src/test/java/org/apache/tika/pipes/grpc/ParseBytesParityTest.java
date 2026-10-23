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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.grpc.v2.Document;
import org.apache.tika.grpc.v2.FetchAndParseRequest;
import org.apache.tika.grpc.v2.MetadataField;
import org.apache.tika.grpc.v2.ParseBytesRequest;
import org.apache.tika.grpc.v2.ParseStatus;
import org.apache.tika.grpc.v2.TikaV2ServiceGrpc;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.serialization.config.JsonConfigHelper;

/**
 * The same bytes parsed with FetchAndParse, from a file on disk, and with ParseBytes give
 * the same {@link Document}. Both requests use the same id and the same resource name, the
 * fetcher adds no file-system metadata, and a SHA-256 digester gives both replies a digest.
 *
 * <p>The fields that change on every call are checked and then removed before the
 * comparison: {@code parsed_at}, {@code fetch_parse_time_ms} and
 * {@code tk:parse-time-millis}. The fetch side may also return a source path, which is
 * removed from that side only. The other metadata fields are sorted by key, because their
 * order is not fixed.
 */
public class ParseBytesParityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long TIMING_SENTINEL = 0L;

    private static final String HTML =
            "<html><head><title>Parity</title>"
                    + "<meta name=\"author\" content=\"Jane Doe\"/></head>"
                    + "<body>same bytes both ways</body></html>";

    @TempDir
    static Path tempDir;

    private static Path config;

    @BeforeAll
    static void init() throws Exception {
        Path base = tempDir.resolve("tika-config-parity-base.json");
        Map<String, Object> replacements = new HashMap<>();
        replacements.put("JAVA_PATH", Paths.get(System.getProperty("java.home"), "bin", "java"));
        replacements.put("FETCHER_BASE_PATH", tempDir.toAbsolutePath());
        replacements.put("PLUGIN_ROOTS", Paths.get("target").toAbsolutePath().resolve("plugins"));
        JsonConfigHelper.writeConfigFromResource("/tika-pipes-test-config.json",
                ParseBytesParityTest.class, replacements, base);

        // The fetcher adds no file-system metadata, and the digester gives both replies a
        // digest to compare.
        ObjectNode root = (ObjectNode) OBJECT_MAPPER.readTree(base.toFile());
        ((ObjectNode) root.get("fetchers")).putObject("parity-fetcher")
                .putObject("file-system-fetcher")
                .put("basePath", tempDir.toAbsolutePath().toString())
                .put("extractFileSystemMetadata", false);
        ObjectNode parseCtx = root.has("parse-context") && root.get("parse-context").isObject()
                ? (ObjectNode) root.get("parse-context")
                : root.putObject("parse-context");
        ObjectNode digester = parseCtx.putObject("commons-digester-factory");
        digester.putArray("digests").addObject().put("algorithm", "SHA256");
        digester.put("skipContainerDocumentDigest", false);

        config = tempDir.resolve("tika-config-parity.json");
        FileUtils.write(config.toFile(),
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8);
    }

    @Test
    public void sameBytesEitherWayYieldTheSameDocument() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        TikaGrpcServerImpl serviceImpl =
                new TikaGrpcServerImpl(config.toAbsolutePath().toString());
        Server server = InProcessServerBuilder.forName(serverName).directExecutor()
                .addService(serviceImpl)
                .addService(new TikaGrpcV2ServerImpl(serviceImpl))
                .build().start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor().build();
        TikaV2ServiceGrpc.TikaV2ServiceBlockingStub v2 = TikaV2ServiceGrpc.newBlockingStub(channel);

        String folder = "parity";
        File dir = tempDir.resolve(folder).toFile();
        try {
            FileUtils.forceMkdir(dir);
            FileUtils.writeStringToFile(new File(dir, "parity.html"), HTML,
                    StandardCharsets.UTF_8);
            String fetchKey = folder + "/parity.html";

            Document viaFetch = v2.fetchAndParse(FetchAndParseRequest.newBuilder()
                    .setFetcherId("parity-fetcher")
                    .setFetchKey(fetchKey)
                    .build()).getDocument();
            Document viaBytes = v2.parseBytes(ParseBytesRequest.newBuilder()
                    .setContent(ByteString.copyFromUtf8(HTML))
                    .setResourceName("parity.html")
                    .setCorrelationId(fetchKey)
                    .build()).getDocument();

            assertEquals(ParseStatus.Status.STATUS_SUCCESS, viaFetch.getStatus().getStatus(),
                    "the fetch path must parse the fixture");
            assertEquals(ParseStatus.Status.STATUS_SUCCESS, viaBytes.getStatus().getStatus(),
                    "the bytes path must parse the fixture");

            // The per-call fields, before canonical() removes them.
            assertTrue(viaFetch.hasParsedAt(), "fetch reply must carry parsed_at");
            assertTrue(viaBytes.hasParsedAt(), "bytes reply must carry parsed_at");
            assertTrue(viaFetch.getStatus().getFetchParseTimeMs() >= 0,
                    "fetch timing must be non-negative");
            assertTrue(viaBytes.getStatus().getFetchParseTimeMs() >= 0,
                    "bytes timing must be non-negative");

            // Two empty digests would also compare equal below.
            assertFalse(viaFetch.getOrigin().getSha256().isEmpty(),
                    "fetch reply must carry a SHA-256 digest");
            assertFalse(viaBytes.getOrigin().getSha256().isEmpty(),
                    "bytes reply must carry a SHA-256 digest");

            // canonical() removes the fetch side's source path only.
            String sourcePathKey = TikaCoreProperties.SOURCE_PATH.getName();
            assertEquals(0, count(viaBytes, sourcePathKey),
                    "ParseBytes must not carry a source path");

            String parseTimeKey = TikaCoreProperties.PARSE_TIME_MILLIS.getName();
            assertEquals(count(viaFetch, parseTimeKey), count(viaBytes, parseTimeKey),
                    "tk:parse-time-millis must be recorded on both paths or neither");
            assertNonNegativeNumbers(viaFetch, parseTimeKey, "fetch");
            assertNonNegativeNumbers(viaBytes, parseTimeKey, "bytes");

            assertEquals(canonical(viaFetch, true), canonical(viaBytes, false),
                    "the same bytes must give the same Document, apart from the per-call "
                            + "fields");
        } finally {
            // Stopping the gRPC server does not release the impl; postShutdown() does.
            channel.shutdownNow();
            channel.awaitTermination(10, TimeUnit.SECONDS);
            server.shutdownNow();
            server.awaitTermination(10, TimeUnit.SECONDS);
            serviceImpl.postShutdown();
        }
    }

    /**
     * Removes the fields that change on every call: parsed_at, both timings and, when
     * {@code dropSourcePath} is set, the source path. Sorts the other metadata fields by
     * key; fields with the same key keep their order.
     */
    private static Document canonical(Document document, boolean dropSourcePath) {
        Document.Builder builder = document.toBuilder();
        builder.clearParsedAt();
        builder.getStatusBuilder().setFetchParseTimeMs(TIMING_SENTINEL);
        String sourcePathKey = TikaCoreProperties.SOURCE_PATH.getName();
        String parseTimeKey = TikaCoreProperties.PARSE_TIME_MILLIS.getName();
        List<MetadataField> remaining = new ArrayList<>();
        for (MetadataField field : builder.getExtraList()) {
            if (field.getKey().equals(parseTimeKey)
                    || (dropSourcePath && field.getKey().equals(sourcePathKey))) {
                continue;
            }
            remaining.add(field);
        }
        remaining.sort(Comparator.comparing(MetadataField::getKey));
        builder.clearExtra();
        builder.addAllExtra(remaining);
        return builder.build();
    }

    private static int count(Document document, String key) {
        int occurrences = 0;
        for (MetadataField field : document.getExtraList()) {
            if (field.getKey().equals(key)) {
                occurrences++;
            }
        }
        return occurrences;
    }

    private static void assertNonNegativeNumbers(Document document, String key, String side) {
        for (MetadataField field : document.getExtraList()) {
            if (!field.getKey().equals(key)) {
                continue;
            }
            assertTrue(field.getValue().hasStrings(),
                    key + " must be an untyped text value on the " + side + " side");
            assertFalse(field.getValue().getStrings().getValuesList().isEmpty(),
                    key + " must carry at least one value on the " + side + " side");
            for (String value : field.getValue().getStrings().getValuesList()) {
                assertTrue(Long.parseLong(value) >= 0,
                        key + " must be non-negative on the " + side + " side, was " + value);
            }
        }
    }

    /**
     * With {@code pipes.maxInlineBytes} below the content size, the bytes go through a
     * spool file. The Document is the same as with inline bytes, digest included, and
     * carries no trace of the spool file.
     */
    @Test
    public void spooledAndInlinePathsYieldTheSameDocument() throws Exception {
        // The fixture is larger than 16 bytes, so it goes through a spool file.
        ObjectNode root = (ObjectNode) OBJECT_MAPPER.readTree(config.toFile());
        ObjectNode pipes = (ObjectNode) root.get("pipes");
        pipes.put("maxInlineBytes", 16);
        Path tinyInlineConfig = tempDir.resolve("tika-config-parity-spool.json");
        FileUtils.write(tinyInlineConfig.toFile(),
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8);

        String serverName = InProcessServerBuilder.generateName();
        TikaGrpcServerImpl spooling =
                new TikaGrpcServerImpl(tinyInlineConfig.toAbsolutePath().toString());
        Server server = InProcessServerBuilder.forName(serverName).directExecutor()
                .addService(spooling)
                .addService(new TikaGrpcV2ServerImpl(spooling))
                .build().start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor().build();
        TikaV2ServiceGrpc.TikaV2ServiceBlockingStub v2 = TikaV2ServiceGrpc.newBlockingStub(channel);

        String inlineServerName = InProcessServerBuilder.generateName();
        TikaGrpcServerImpl inlining =
                new TikaGrpcServerImpl(config.toAbsolutePath().toString());
        Server inlineServer = InProcessServerBuilder.forName(inlineServerName).directExecutor()
                .addService(inlining)
                .addService(new TikaGrpcV2ServerImpl(inlining))
                .build().start();
        ManagedChannel inlineChannel = InProcessChannelBuilder.forName(inlineServerName)
                .directExecutor().build();
        TikaV2ServiceGrpc.TikaV2ServiceBlockingStub inlineV2 = TikaV2ServiceGrpc.newBlockingStub(inlineChannel);
        try {
            ParseBytesRequest request = ParseBytesRequest.newBuilder()
                    .setContent(ByteString.copyFromUtf8(HTML))
                    .setResourceName("parity.html")
                    .setCorrelationId("spool-parity")
                    .build();
            Document spooled = v2.parseBytes(request).getDocument();
            Document inline = inlineV2.parseBytes(request).getDocument();

            assertEquals(ParseStatus.Status.STATUS_SUCCESS, spooled.getStatus().getStatus(),
                    "the spooled path must parse the fixture");
            assertEquals(ParseStatus.Status.STATUS_SUCCESS, inline.getStatus().getStatus(),
                    "the inline path must parse the fixture");
            assertFalse(spooled.getOrigin().getSha256().isEmpty(),
                    "spooled reply must carry a SHA-256 digest");
            assertEquals(inline.getOrigin().getSha256(), spooled.getOrigin().getSha256(),
                    "both routes hash the same bytes");
            String sourcePathKey = TikaCoreProperties.SOURCE_PATH.getName();
            assertEquals(0, count(spooled, sourcePathKey),
                    "the spool file must not surface as a source path");
            assertEquals(0, countValuesContaining(spooled, "parse-bytes-"),
                    "no field may carry the spool filename");
            assertEquals(canonical(inline, false), canonical(spooled, false),
                    "the spool file must not change the Document");
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(10, TimeUnit.SECONDS);
            server.shutdownNow();
            server.awaitTermination(10, TimeUnit.SECONDS);
            inlineChannel.shutdownNow();
            inlineChannel.awaitTermination(10, TimeUnit.SECONDS);
            inlineServer.shutdownNow();
            inlineServer.awaitTermination(10, TimeUnit.SECONDS);
            spooling.postShutdown();
            inlining.postShutdown();
        }
    }

    /**
     * Counts the extra fields whose value contains {@code fragment}, plus the filename and
     * the id when they contain it.
     */
    private static int countValuesContaining(Document document, String fragment) {
        int hits = 0;
        for (var field : document.getExtraList()) {
            hits += field.getValue().toString().contains(fragment) ? 1 : 0;
        }
        if (document.getOrigin().getFilename().contains(fragment)) {
            hits++;
        }
        if (document.getId().contains(fragment)) {
            hits++;
        }
        return hits;
    }
}
