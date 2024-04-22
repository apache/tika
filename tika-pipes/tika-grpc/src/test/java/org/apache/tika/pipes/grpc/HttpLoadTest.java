package org.apache.tika.pipes.grpc;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.PathResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.fetcher.http.HttpFetcher;

class HttpLoadTest {
    static File tikaConfigXmlTemplate =
            Paths.get("src", "test", "resources", "tika-pipes-test-config.xml").toFile();
    static File tikaConfigXml = new File("target", "tika-config-" + UUID.randomUUID() + ".xml");

    static TikaGrpcServer grpcServer;
    static int grpcPort;
    static String httpServerUrl;

    static TikaGrpc.TikaBlockingStub blockingStub;
    String httpFetcherId = "httpFetcherIdHere";

    List<String> files = Arrays.asList("014760.docx", "017091.docx", "017097.docx", "018367.docx");

    static int findAvailablePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    static Server httpServer;
    static int httpServerPort;

    @BeforeAll
    static void setUpHttpServer() throws Exception {
        // Specify the folder from which files will be served
        httpServerPort = findAvailablePort();
        httpServer = new Server(httpServerPort);

        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirAllowed(true);
        resourceHandler.setBaseResource(new PathResource(Paths.get("src", "test", "resources",
                "test-files")));
        httpServer.setHandler(resourceHandler);
        grpcServer.start();

        httpServerUrl = InetAddress.getLocalHost().getHostAddress() + ":" + httpServerPort;
    }

    @BeforeAll
    static void setUpGrpcServer() throws Exception {
        setupTikaGrpcServer();
    }

    private static void setupTikaGrpcServer() throws Exception {
        grpcPort = findAvailablePort();
        System.getProperty("server.port", String.valueOf(grpcPort));

        FileUtils.copyFile(tikaConfigXmlTemplate, tikaConfigXml);
        TikaGrpcServer.setTikaConfigPath(tikaConfigXml.getAbsolutePath());
        grpcServer = new TikaGrpcServer();
        grpcServer.start();

        String target = InetAddress.getLocalHost().getHostAddress() + ":" + grpcPort;

        ManagedChannel channel =
                Grpc.newChannelBuilder(target, InsecureChannelCredentials.create()).build();

        blockingStub = TikaGrpc.newBlockingStub(channel);
    }

    @AfterAll
    static void stopHttpServer() throws Exception {
        httpServer.stop();
    }

    @AfterAll
    static void stopGrpcServer() throws Exception {
        grpcServer.stop();
    }

    @BeforeEach
    void createHttpFetcher() {
        SaveFetcherRequest saveFetcherRequest = SaveFetcherRequest.newBuilder()
                .setFetcherId(httpFetcherId)
                .setFetcherClass(HttpFetcher.class.getName())
                .build();
        SaveFetcherReply saveFetcherReply = blockingStub.saveFetcher(saveFetcherRequest);
        Assertions.assertEquals(saveFetcherReply.getFetcherId(), httpFetcherId);
    }

    @Test
    void testHttpFetchScenario() throws Exception {
        for (String file : files) {
            FetchAndParseRequest fetchAndParseRequest = FetchAndParseRequest.newBuilder()
                    .setFetchKey(httpServerUrl + "/" + file).build();

        }
    }

    // Method to send an HTTP GET request and return the response code
    private int sendHttpRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        return connection.getResponseCode();
    }
}
