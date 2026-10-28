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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.grpc.v2.Document;
import org.apache.tika.grpc.v2.EmbeddedDocument;
import org.apache.tika.grpc.v2.ExtensionResult;
import org.apache.tika.grpc.v2.MetadataField;
import org.apache.tika.grpc.v2.ParseBytesRequest;
import org.apache.tika.grpc.v2.ParseStatus;
import org.apache.tika.grpc.v2.TikaV2ServiceGrpc;
import org.apache.tika.pipes.grpc.compose.ExternalParserAdapters;
import org.apache.tika.pipes.grpc.compose.ExternalParserConfig;
import org.apache.tika.pipes.grpc.compose.ExternalParsers;
import org.apache.tika.pipes.grpc.compose.FakeRecordList;
import org.apache.tika.serialization.config.JsonConfigHelper;

/**
 * A WARC, gzipped or not, sent to ParseBytes on a server with real forked pipes workers
 * comes back as one Document: Tika's own parse, plus one embedded Document per record that
 * the test adapter RecordListAdapter mapped from its parser's reply. The parser is an
 * in-process fake that reads the WARC it gets, gunzipping it first, so its records depend on
 * getting the same bytes Tika parsed.
 */
public class WarcComposeIntegrationTest {

    /** testWARC_multiple.warc(.gz) holds this many WARC records. */
    private static int expectedRecords;

    @TempDir
    static Path tempDir;

    private static Server recordList;
    private static TikaGrpcServerImpl tika;
    private static Server server;
    private static TikaGrpcV2ServerImpl v2Service;
    private static ManagedChannel channel;
    private static TikaV2ServiceGrpc.TikaV2ServiceBlockingStub v2;

    @BeforeAll
    static void start() throws Exception {
        expectedRecords = countRecords(testFile("testWARC_multiple.warc"));
        assertTrue(expectedRecords > 1, "the fixture must hold several records");

        String recordsName = InProcessServerBuilder.generateName();
        recordList = InProcessServerBuilder.forName(recordsName)
                .addService(new FakeRecordList())
                .build()
                .start();

        Path config = tempDir.resolve("tika-config-warc.json");
        Map<String, Object> replacements = new HashMap<>();
        replacements.put("JAVA_PATH", Paths.get(System.getProperty("java.home"), "bin", "java"));
        replacements.put("FETCHER_BASE_PATH", tempDir.toAbsolutePath());
        replacements.put("PLUGIN_ROOTS", Paths.get("target").toAbsolutePath().resolve("plugins"));
        JsonConfigHelper.writeConfigFromResource("/tika-pipes-test-config.json",
                WarcComposeIntegrationTest.class, replacements, config);

        // The same entry an operator writes in grpc.externalParsers; RecordListAdapter is
        // found on the classpath by its service file.
        ExternalParserConfig recordsConfig = new ExternalParserConfig();
        recordsConfig.setProducerId("records");
        recordsConfig.setTarget(recordsName);
        recordsConfig.setPlaintext(true);
        ExternalParsers parsers = ExternalParsers.open(List.of(recordsConfig),
                ExternalParserAdapters.load(WarcComposeIntegrationTest.class.getClassLoader()),
                c -> InProcessChannelBuilder.forName(c.getTarget()).build());
        tika = new TikaGrpcServerImpl(config.toAbsolutePath().toString());
        v2Service = new TikaGrpcV2ServerImpl(tika, parsers);
        String tikaName = InProcessServerBuilder.generateName();
        // Not directExecutor: like the production server, each call gets its own thread.
        server = InProcessServerBuilder.forName(tikaName)
                .addService(tika)
                .addService(v2Service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(tikaName).build();
        v2 = TikaV2ServiceGrpc.newBlockingStub(channel);
    }

    @AfterAll
    static void stop() throws Exception {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(10, TimeUnit.SECONDS);
        }
        if (v2Service != null) {
            v2Service.close();
        }
        if (tika != null) {
            tika.postShutdown();
        }
        if (recordList != null) {
            recordList.shutdownNow().awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void aNamedWarcGzIsParsedByTikaAndTheExternalParser() throws Exception {
        assertWarcDocument(parse("testWARC_multiple.warc.gz", "crawl.warc.gz"),
                "application/warc+gz");
    }

    /** No name and no hint: Tika finds the WARC through the gzip layer by its bytes. */
    @Test
    void anUnnamedWarcGzIsFoundByItsBytes() throws Exception {
        assertWarcDocument(parse("testWARC_multiple.warc.gz", ""), "application/warc+gz");
    }

    /** A name that says only gzip does not hide the WARC inside. */
    @Test
    void aWarcGzNamedAsPlainGzipStillRoutes() throws Exception {
        assertWarcDocument(parse("testWARC_multiple.warc.gz", "crawl.gz"),
                "application/warc+gz");
    }

    @Test
    void anUncompressedWarcRoutesToo() throws Exception {
        assertWarcDocument(parse("testWARC_multiple.warc", "crawl.warc"), "application/warc");
    }

    private static Document parse(String fixture, String resourceName) throws IOException {
        return v2.parseBytes(ParseBytesRequest.newBuilder()
                .setCorrelationId(fixture + "|" + resourceName)
                .setContent(testFile(fixture))
                .setResourceName(resourceName)
                .build()).getDocument();
    }

    private static void assertWarcDocument(Document document, String contentType)
            throws Exception {
        assertEquals(ParseStatus.Status.STATUS_SUCCESS, document.getStatus().getStatus(),
                "Tika's own parse must succeed: " + document.getStatus().getErrorsList());
        assertEquals(contentType, document.getContentType());
        assertFalse(document.getOrigin().getSha256().isEmpty(), "origin.sha256");
        assertFalse(document.getStatus().getParsersUsedList().isEmpty(), "parsers_used");

        assertEquals(1, document.getExtensionsCount(), "one external parser slot");
        ExtensionResult slot = document.getExtensions(0);
        assertEquals("records", slot.getProducerId());
        assertEquals(ExtensionResult.Status.STATUS_OK, slot.getStatus(),
                "warnings=" + slot.getWarningsList());

        // One embedded Document per WARC record, in Tika's own types.
        assertEquals(expectedRecords, document.getEmbeddedCount());
        assertEquals(expectedRecords, slot.getEmbeddedKept());
        assertFalse(slot.getTruncated());
        List<String> targets = new ArrayList<>();
        for (EmbeddedDocument embedded : document.getEmbeddedList()) {
            assertEquals("records", embedded.getProducerId());
            assertTrue(embedded.getMetadata().hasCreated(),
                    "WARC-Date becomes the typed created date: " + embedded.getPath());
            for (MetadataField field : embedded.getExtraList()) {
                if (field.getKey().equals("warc:WARC-Target-URI")) {
                    targets.add(field.getValue().getStrings().getValues(0));
                }
            }
        }
        assertFalse(targets.isEmpty(), "records carry their target URI");
        assertTrue(targets.stream().allMatch(uri -> uri.startsWith("http")), targets.toString());
    }

    private static ByteString testFile(String name) throws IOException {
        return ByteString.copyFrom(Files.readAllBytes(
                Paths.get("src", "test", "resources", "test-files", name)));
    }

    /** Counts WARC record headers, gunzipping first when the bytes are gzip. */
    private static int countRecords(ByteString archive) throws IOException {
        InputStream in = archive.newInput();
        if (archive.size() > 1 && (archive.byteAt(0) & 0xff) == 0x1f
                && (archive.byteAt(1) & 0xff) == 0x8b) {
            in = new GZIPInputStream(in);
        }
        List<String> headers = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("WARC/1.")) {
                    headers.add(line);
                }
            }
        }
        return headers.size();
    }
}
