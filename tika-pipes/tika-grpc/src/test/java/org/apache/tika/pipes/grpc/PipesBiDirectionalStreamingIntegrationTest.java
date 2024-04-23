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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.apache.commons.io.FileUtils;
import org.awaitility.Awaitility;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.PathResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.fetcher.http.HttpFetcher;

/**
 * This test will start an HTTP server using jetty.
 * Then it will start Tika Pipes Grpc service.
 * Then it will, using a bidirectional stream of data, send urls to the
 * HTTP fetcher whilst simultaneously receiving parsed output as they parse.
 */
class PipesBiDirectionalStreamingIntegrationTest {
    static final Logger LOGGER = LoggerFactory.getLogger(PipesBiDirectionalStreamingIntegrationTest.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static File tikaConfigXmlTemplate = Paths
            .get("src", "test", "resources", "tika-pipes-test-config.xml")
            .toFile();
    static File tikaConfigXml = new File("target", "tika-config-" + UUID.randomUUID() + ".xml");
    static TikaGrpcServer grpcServer;
    static int grpcPort;
    static String httpServerUrl;
    static TikaGrpc.TikaBlockingStub tikaBlockingStub;
    static TikaGrpc.TikaStub tikaStub;
    static Server httpServer;
    static int httpServerPort;
    String httpFetcherId = "httpFetcherIdHere";
    List<String> files = Arrays.asList("014760.docx", "017091.docx", "017097.docx", "018367.docx");

    static int findAvailablePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    @BeforeAll
    static void setUpHttpServer() throws Exception {
        // Specify the folder from which files will be served
        httpServerPort = findAvailablePort();
        httpServer = new Server(httpServerPort);

        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirAllowed(true);
        resourceHandler.setBaseResource(new PathResource(Paths.get("src", "test", "resources", "test-files")));
        httpServer.setHandler(resourceHandler);
        httpServer.start();

        httpServerUrl = "http://" + InetAddress
                .getByName("localhost")
                .getHostAddress() + ":" + httpServerPort;
    }

    @BeforeAll
    static void setUpGrpcServer() throws Exception {
        grpcPort = findAvailablePort();
        FileUtils.copyFile(tikaConfigXmlTemplate, tikaConfigXml);
        grpcServer = new TikaGrpcServer();
        grpcServer.setTikaConfigXml(tikaConfigXml);
        grpcServer.setPort(grpcPort);
        grpcServer.start();

        String target = InetAddress
                .getByName("localhost")
                .getHostAddress() + ":" + grpcPort;

        ManagedChannel channel = Grpc
                .newChannelBuilder(target, InsecureChannelCredentials.create())
                .build();

        tikaBlockingStub = TikaGrpc.newBlockingStub(channel);
        tikaStub = TikaGrpc.newStub(channel);
    }

    @AfterAll
    static void stopHttpServer() throws Exception {
        if (httpServer != null) {
            httpServer.stop();
        }
    }

    @AfterAll
    static void stopGrpcServer() throws Exception {
        if (grpcServer != null) {
            grpcServer.stop();
        }
    }

    @BeforeEach
    void createHttpFetcher() throws Exception {
        SaveFetcherRequest saveFetcherRequest = SaveFetcherRequest
                .newBuilder()
                .setFetcherId(httpFetcherId)
                .setFetcherClass(HttpFetcher.class.getName())
                .setFetcherConfigJson(OBJECT_MAPPER.writeValueAsString(ImmutableMap
                        .builder()
                        .put("requestTimeout", 30_000)
                        .put("socketTimeout", 30_000)
                        .put("connectTimeout", 20_000)
                        .put("maxConnectionsPerRoute", 200)
                        .put("maxRedirects", 0)
                        .put("maxSpoolSize", -1)
                        .put("overallTimeout", 50_000)
                        .build()))
                .build();
        SaveFetcherReply saveFetcherReply = tikaBlockingStub.saveFetcher(saveFetcherRequest);
        Assertions.assertEquals(saveFetcherReply.getFetcherId(), httpFetcherId);
    }

    @Test
    void testHttpFetchScenario() throws Exception {
        AtomicInteger numParsed = new AtomicInteger();
        Map<String, Map<String, String>> result = Collections.synchronizedMap(new HashMap<>());
        StreamObserver<FetchAndParseReply> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(FetchAndParseReply fetchAndParseReply) {
                LOGGER.info("Parsed: {}", fetchAndParseReply.getFetchKey());
                numParsed.incrementAndGet();
                result.put(fetchAndParseReply.getFetchKey(), fetchAndParseReply.getFieldsMap());
            }

            @Override
            public void onError(Throwable throwable) {
                LOGGER.error("Error occurred", throwable);
            }

            @Override
            public void onCompleted() {
                LOGGER.info("Completed fetching.");
            }
        };
        StreamObserver<FetchAndParseRequest> request = tikaStub.fetchAndParseBiDirectionalStreaming(responseObserver);
        for (String file : files) {
            request.onNext(FetchAndParseRequest
                    .newBuilder()
                    .setFetcherId(httpFetcherId)
                    .setFetchKey(httpServerUrl + "/" + file)
                    .build());
        }
        request.onCompleted();

        Awaitility.await().atMost(Duration.ofSeconds(600)).until(() -> result.size() == files.size());

        Assertions.assertEquals(files.size(), numParsed.get());
    }
}
