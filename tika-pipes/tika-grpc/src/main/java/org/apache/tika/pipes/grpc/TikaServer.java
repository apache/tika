/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import org.apache.tika.CreateFetcherReply;
import org.apache.tika.CreateFetcherRequest;
import org.apache.tika.FetchReply;
import org.apache.tika.FetchRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.PipesClient;
import org.apache.tika.pipes.PipesConfig;
import org.apache.tika.pipes.PipesResult;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcher;

/**
 * Server that manages startup/shutdown of a server.
 */
public class TikaServer {
    private static final Logger logger = Logger.getLogger(TikaServer.class.getName());
    private Server server;

    private static String tikaConfigPath;

    private void start() throws Exception {
        /* The port on which the server should run */
        int port = 50051;
        server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                .addService(new TikaServerImpl()).build().start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            try {
                TikaServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** server shut down");
        }));
    }

    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) throws Exception {
        tikaConfigPath = args[0];
        final TikaServer server = new TikaServer();
        server.start();
        server.blockUntilShutdown();
    }

    static class TikaServerImpl extends TikaGrpc.TikaImplBase {
        Map<String, AbstractFetcher> fetchers = Collections.synchronizedMap(new HashMap<>());
        PipesConfig pipesConfig = PipesConfig.load(Paths.get("tika-config.xml"));
        PipesClient pipesClient = new PipesClient(pipesConfig);

        TikaServerImpl() throws TikaConfigException, IOException {
        }

        private void updateTikaConfig()
                throws ParserConfigurationException, IOException, SAXException,
                TransformerException {
            Document tikaConfigDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(tikaConfigPath);
            Element fetchersElement = (Element) tikaConfigDoc.getElementsByTagName("fetchers").item(0);
            for (int i = 0; i < fetchersElement.getChildNodes().getLength(); ++i) {
                fetchersElement.removeChild(fetchersElement.getChildNodes().item(i));
            }
            for (Map.Entry<String, AbstractFetcher> fetcherEntry : fetchers.entrySet()) {
                Element fetcher = tikaConfigDoc.createElement("fetcher");
                fetcher.setAttribute("class", fetcherEntry.getValue().getClass().getName());
                if (fetcherEntry.getValue() instanceof FileSystemFetcher) {
                    FileSystemFetcher fileSystemFetcher = (FileSystemFetcher) fetcherEntry.getValue();
                    Element fetcherName = tikaConfigDoc.createElement("name");
                    fetcherName.setTextContent(fileSystemFetcher.getName());
                    fetcher.appendChild(fetcherName);
                    Element basePath = tikaConfigDoc.createElement("basePath");
                    fetcher.appendChild(basePath);
                    basePath.setTextContent(fileSystemFetcher.getBasePath().toAbsolutePath().toString());
                }
                fetchersElement.appendChild(fetcher);
            }
            DOMSource source = new DOMSource(tikaConfigDoc);
            FileWriter writer = new FileWriter(tikaConfigPath, StandardCharsets.UTF_8);
            StreamResult result = new StreamResult(writer);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.transform(source, result);
        }

        @Override
        public void createFetcher(CreateFetcherRequest request,
                                  StreamObserver<CreateFetcherReply> responseObserver) {
            CreateFetcherReply reply =
                    CreateFetcherReply.newBuilder().setMessage(request.getName()).build();
            if (FileSystemFetcher.class.getName().equals(request.getFetcherClass())) {
                FileSystemFetcher fileSystemFetcher = new FileSystemFetcher();
                fileSystemFetcher.setName(request.getName());
                fileSystemFetcher.setBasePath(request.getParamsOrDefault("basePath", "."));
                fileSystemFetcher.setExtractFileSystemMetadata(Boolean.parseBoolean(request.getParamsOrDefault("extractFileSystemMetadata", "false")));
                Map<String, String> paramsMap = request.getParamsMap();
                Map<String, Param> tikaParamsMap = new HashMap<>();
                for (Map.Entry<String, String> entry : paramsMap.entrySet()) {
                    tikaParamsMap.put(entry.getKey(),
                            new Param<>(entry.getKey(), entry.getValue()));
                }
                try {
                    fileSystemFetcher.initialize(tikaParamsMap);
                } catch (TikaConfigException e) {
                    throw new RuntimeException(e);
                }
                fetchers.put(request.getName(), fileSystemFetcher);
            }
            try {
                updateTikaConfig();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void fetch(FetchRequest request, StreamObserver<FetchReply> responseObserver) {
            AbstractFetcher fetcher = fetchers.get(request.getFetcherName());
            if (fetcher == null) {
                throw new RuntimeException("Could not find fetcher with name " + request.getFetcherName());
            }
            Metadata tikaMetadata = new Metadata();
            for (Map.Entry<String, String> entry : request.getMetadataMap().entrySet()) {
                tikaMetadata.add(entry.getKey(), entry.getValue());
            }
            try {
                PipesResult pipesResult = pipesClient.process(new FetchEmitTuple(request.getFetchKey(),
                        new FetchKey(fetcher.getName(), request.getFetchKey()), new EmitKey(), FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
                for (Metadata metadata : pipesResult.getEmitData().getMetadataList()) {
                    FetchReply.Builder fetchReplyBuilder = FetchReply.newBuilder()
                            .setFetchKey(request.getFetchKey());
                    for (String name : metadata.names()) {
                        String value = metadata.get(name);
                        if (value != null) {
                            fetchReplyBuilder.putFields(name, value);
                        }
                    }
                    responseObserver.onNext(fetchReplyBuilder.build());
                }
                responseObserver.onCompleted();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
