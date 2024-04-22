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

import java.util.concurrent.TimeUnit;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server that manages startup/shutdown of the GRPC Tika server.
 */
public class TikaGrpcServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TikaGrpcServer.class);
    private Server server;
    private static String tikaConfigPath;

    public void start() throws Exception {
        /* The port on which the server should run */
        int port = Integer.parseInt(System.getProperty("server.port", "50051"));
        server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                .addService(new TikaGrpcServerImpl(tikaConfigPath))
                .addService(ProtoReflectionService.newInstance()) // Enable reflection
                .build()
                .start();
        LOGGER.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            try {
                TikaGrpcServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** server shut down");
        }));
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: TikaGrpcServer {path-to-tika-config-xml-file}");
            System.exit(1);
        }
        tikaConfigPath = args[0];
        TikaGrpcServer server = new TikaGrpcServer();
        server.start();
        server.blockUntilShutdown();
    }

    public static void setTikaConfigPath(String tikaConfigPath) {
        TikaGrpcServer.tikaConfigPath = tikaConfigPath;
    }
}
