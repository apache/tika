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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.commons.io.FileUtils;
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
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcher;

@ExtendWith(GrpcCleanupExtension.class)
public class TikaGrpcServerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(TikaGrpcServerTest.class);
    public static final int NUM_TEST_DOCS = 50;
    static File tikaConfigXmlTemplate = Paths
            .get("src", "test", "resources", "tika-pipes-test-config.xml")
            .toFile();
    static File tikaConfigXml = new File("target", "tika-config-" + UUID.randomUUID() + ".xml");


    @BeforeAll
    static void init() throws Exception {
        FileUtils.copyFile(tikaConfigXmlTemplate, tikaConfigXml);
    }

    static final int NUM_FETCHERS_TO_CREATE = 10;

    @Test
    public void testFetcherCrud(Resources resources) throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        Server server = InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(new TikaGrpcServerImpl(tikaConfigXml.getAbsolutePath()))
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
            String fetcherId = "fetcherIdHere" + i;
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
            String fetcherId = "fetcherIdHere" + i;
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
            String fetcherId = "fetcherIdHere" + i;
            GetFetcherReply getFetcherReply = blockingStub.getFetcher(GetFetcherRequest
                    .newBuilder()
                    .setFetcherId(fetcherId)
                    .build());
            assertEquals(fetcherId, getFetcherReply.getFetcherId());
            assertEquals(FileSystemFetcher.class.getName(), getFetcherReply.getFetcherClass());
        }

        // delete fetchers
        for (int i = 0; i < NUM_FETCHERS_TO_CREATE; ++i) {
            String fetcherId = "fetcherIdHere" + i;
            DeleteFetcherReply deleteFetcherReply = blockingStub.deleteFetcher(DeleteFetcherRequest
                    .newBuilder()
                    .setFetcherId(fetcherId)
                    .build());
            Assertions.assertTrue(deleteFetcherReply.getSuccess());
            StatusRuntimeException statusRuntimeException = Assertions.assertThrows(StatusRuntimeException.class, () -> blockingStub.getFetcher(GetFetcherRequest
                    .newBuilder()
                    .setFetcherId(fetcherId)
                    .build()));
            Assertions.assertEquals(Status.NOT_FOUND
                    .getCode()
                    .value(), statusRuntimeException
                    .getStatus()
                    .getCode()
                    .value());
        }
    }

    @Test
    public void testBiStream(Resources resources) throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        Server server = InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(new TikaGrpcServerImpl(tikaConfigXml.getAbsolutePath()))
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

        String fetcherId = "fetcherIdHere";
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

        List<FetchAndParseReply> fetchAndParseReplys = Collections.synchronizedList(new ArrayList<>());

        StreamObserver<FetchAndParseReply> replyStreamObserver = new StreamObserver<>() {
            @Override
            public void onNext(FetchAndParseReply fetchAndParseReply) {
                LOG.debug("Fetched {} with metadata {}", fetchAndParseReply.getFetchKey(), fetchAndParseReply.getFieldsMap());
                fetchAndParseReplys.add(fetchAndParseReply);
            }

            @Override
            public void onError(Throwable throwable) {
                LOG.error("Fetched error found", throwable);
            }

            @Override
            public void onCompleted() {
                LOG.info("Stream completed");
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
            requestStreamObserver.onCompleted();

            assertEquals(NUM_TEST_DOCS, fetchAndParseReplys.size());
        } finally {
            FileUtils.deleteDirectory(testDocumentFolder);
        }
    }
}
