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
import java.util.concurrent.TimeUnit;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerCredentials;
import io.grpc.TlsServerCredentials;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server that manages startup/shutdown of the GRPC Tika server.
 */
public class TikaGrpcServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TikaGrpcServer.class);
    private Server server;
    @Parameter(names = {"-p", "--port"}, description = "The grpc server port", help = true, required = true)
    private Integer port;

    @Parameter(names = {"-t", "--tika-config"}, description = "The grpc server port", help = true, required = true)
    private File tikaConfigXml;

    @Parameter(names = {"-s", "--secure"}, description = "Enable credentials required to access this grpc server")
    private boolean secure;

    @Parameter(names = {"--cert-chain"}, description = "Certificate chain file. Example: server1.pem See: https://github.com/grpc/grpc-java/tree/b3ffb5078df361d7460786e134db7b5c00939246/examples/example-tls")
    private File certChain;

    @Parameter(names = {"--private-key"}, description = "Private key store. Example: server1.key See: https://github.com/grpc/grpc-java/tree/b3ffb5078df361d7460786e134db7b5c00939246/examples/example-tls")
    private File privateKey;

    @Parameter(names = {"--private-key-password"}, description = "Private key password, if needed")
    private String privateKeyPassword;

    @Parameter(names = {"--trust-cert-collection"}, description = "The trust certificate collection (root certs). Example: ca.pem See: https://github.com/grpc/grpc-java/tree/b3ffb5078df361d7460786e134db7b5c00939246/examples/example-tls")
    private File trustCertCollection;

    @Parameter(names = {"--client-auth-required"}, description = "Is Mutual TLS required?")
    private boolean clientAuthRequired;

    @Parameter(names = {"-h", "-H", "--help"}, description = "Display help menu")
    private boolean help;

    public void start() throws Exception {
        ServerCredentials creds;
        if (secure) {
            TlsServerCredentials.Builder channelCredBuilder = TlsServerCredentials.newBuilder();
            channelCredBuilder.keyManager(certChain, privateKey, privateKeyPassword);
            if (trustCertCollection != null && trustCertCollection.exists()) {
                channelCredBuilder.trustManager(trustCertCollection);
                if (clientAuthRequired) {
                    channelCredBuilder.clientAuth(TlsServerCredentials.ClientAuth.REQUIRE);
                }
            }
            creds = channelCredBuilder.build();
        } else {
            creds = InsecureServerCredentials.create();
        }
        server = Grpc.newServerBuilderForPort(port, creds)
                     .addService(new TikaGrpcServerImpl(tikaConfigXml.getAbsolutePath()))
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
        TikaGrpcServer server = new TikaGrpcServer();
        JCommander commander = JCommander
                .newBuilder()
                .addObject(server)
                .build();

        commander.parse(args);

        if (server.help) {
            commander.usage();
            return;
        }

        server.start();
        server.blockUntilShutdown();
    }

    public TikaGrpcServer setTikaConfigXml(File tikaConfigXml) {
        this.tikaConfigXml = tikaConfigXml;
        return this;
    }

    public TikaGrpcServer setServer(Server server) {
        this.server = server;
        return this;
    }

    public TikaGrpcServer setPort(Integer port) {
        this.port = port;
        return this;
    }

    public TikaGrpcServer setSecure(boolean secure) {
        this.secure = secure;
        return this;
    }

    public TikaGrpcServer setCertChain(File certChain) {
        this.certChain = certChain;
        return this;
    }

    public TikaGrpcServer setPrivateKey(File privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public TikaGrpcServer setPrivateKeyPassword(String privateKeyPassword) {
        this.privateKeyPassword = privateKeyPassword;
        return this;
    }

    public TikaGrpcServer setTrustCertCollection(File trustCertCollection) {
        this.trustCertCollection = trustCertCollection;
        return this;
    }

    public TikaGrpcServer setClientAuthRequired(boolean clientAuthRequired) {
        this.clientAuthRequired = clientAuthRequired;
        return this;
    }
}
