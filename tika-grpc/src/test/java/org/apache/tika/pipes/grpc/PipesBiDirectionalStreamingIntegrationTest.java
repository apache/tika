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
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.grpc.stub.StreamObserver;
import org.apache.commons.io.FileUtils;
import org.awaitility.Awaitility;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.PathResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.TikaGrpc;

/**
 * This test will start an HTTP server using jetty.
 * Then it will start Tika Pipes Grpc service.
 * Then it will, using a bidirectional stream of data, send urls to the
 * HTTP fetcher whilst simultaneously receiving parsed output as they parse.
 */
class PipesBiDirectionalStreamingIntegrationTest {
    static final Logger LOGGER = LoggerFactory.getLogger(PipesBiDirectionalStreamingIntegrationTest.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static File tikaConfigTemplate = Paths
            .get("src", "test", "resources", "tika-pipes-test-config.json")
            .toFile();
    static File tikaConfig = new File("target", "tika-config-" + UUID.randomUUID() + ".json");
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
        // TODO when using jetty 12:
        // resourceHandler.setBaseResourceAsString("src/test/resources/test-files")        
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

        // Read the template config
        String configContent = FileUtils.readFileToString(tikaConfigTemplate, StandardCharsets.UTF_8);

        // Parse it as JSON to inject the correct javaPath
        @SuppressWarnings("unchecked")
        Map<String, Object> configMap = OBJECT_MAPPER.readValue(configContent, Map.class);

        // Get or create the pipes section
        @SuppressWarnings("unchecked")
        Map<String, Object> pipesSection = (Map<String, Object>) configMap.get("pipes");
        if (pipesSection == null) {
            pipesSection = new HashMap<>();
            configMap.put("pipes", pipesSection);
        }

        // Set javaPath to the same Java running the test
        String javaHome = System.getProperty("java.home");
        String javaPath = javaHome + File.separator + "bin" + File.separator + "java";
        pipesSection.put("javaPath", javaPath);

        // Write the modified config
        String modifiedConfig = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(configMap);
        FileUtils.writeStringToFile(tikaConfig, modifiedConfig, StandardCharsets.UTF_8);

        grpcServer = new TikaGrpcServer();
        grpcServer.setTikaConfig(tikaConfig);
        grpcServer.setPort(grpcPort);
        grpcServer.setSecure(true);
        grpcServer.setCertChain(Paths.get("src", "test", "resources", "certs", "server1.pem").toFile());
        grpcServer.setPrivateKey(Paths.get("src", "test", "resources", "certs", "server1.key").toFile());
        grpcServer.setTrustCertCollection(Paths.get("src", "test", "resources", "certs", "ca.pem").toFile());
        grpcServer.setClientAuthRequired(true);
        grpcServer.start();

        String target = InetAddress
                .getByName("localhost")
                .getHostAddress() + ":" + grpcPort;

        TlsChannelCredentials.Builder channelCredBuilder = TlsChannelCredentials.newBuilder();
        File clientCertChain = Paths.get("src", "test", "resources", "certs", "client.pem").toFile();
        File clientPrivateKey = Paths.get("src", "test", "resources", "certs", "client.key").toFile();
        channelCredBuilder.keyManager(clientCertChain, clientPrivateKey);
        channelCredBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE.getTrustManagers());

        ManagedChannel channel = Grpc
                .newChannelBuilder(target, channelCredBuilder.build())
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

    @AfterAll
    static void cleanConfig() throws Exception {
        FileUtils.deleteQuietly(tikaConfig);
    }

    @Test
    void testHttpFetchScenario() throws Exception {
        AtomicInteger numParsed = new AtomicInteger();
        AtomicInteger numErrors = new AtomicInteger();
        Map<String, Map<String, String>> result = Collections.synchronizedMap(new HashMap<>());
        List<String> errorMessages = Collections.synchronizedList(new ArrayList<>());
        StreamObserver<FetchAndParseReply> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(FetchAndParseReply fetchAndParseReply) {
                String status = fetchAndParseReply.getStatus();
                LOGGER.info("Parsed: {} with status: {}", fetchAndParseReply.getFetchKey(), status);
                
                // Check if this is an error status
                if (status.contains("EXCEPTION") || status.contains("ERROR")) {
                    numErrors.incrementAndGet();
                    String errorMsg = String.format(Locale.ROOT, "Failed to parse %s - status: %s, message: %s",
                        fetchAndParseReply.getFetchKey(), 
                        status,
                        fetchAndParseReply.getErrorMessage());
                    errorMessages.add(errorMsg);
                    LOGGER.error(errorMsg);
                } else {
                    numParsed.incrementAndGet();
                    result.put(fetchAndParseReply.getFetchKey(), fetchAndParseReply.getFieldsMap());
                }
            }

            @Override
            public void onError(Throwable throwable) {
                LOGGER.error("Error occurred", throwable);
                numErrors.incrementAndGet();
                errorMessages.add("Stream error: " + throwable.getMessage());
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

        Awaitility.await().atMost(Duration.ofSeconds(600)).until(() -> 
            (result.size() + numErrors.get()) >= files.size());

        // Fail the test if there were any errors
        if (numErrors.get() > 0) {
            Assertions.fail("Test failed with " + numErrors.get() + " errors:\n" + 
                String.join("\n", errorMessages));
        }
        
        Assertions.assertEquals(files.size(), numParsed.get());
        Assertions.assertEquals(files.size(), result.size());
    }
}
