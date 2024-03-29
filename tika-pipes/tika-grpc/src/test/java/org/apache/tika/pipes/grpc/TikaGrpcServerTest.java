/*
 * Copyright 2016 The gRPC Authors
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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.time.Duration;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.apache.tika.CreateFetcherReply;
import org.apache.tika.CreateFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcher;

@ExtendWith(GrpcCleanupExtension.class)
public class TikaGrpcServerTest {

    @Test
    public void testTikaCreateFetcher(Resources resources) throws Exception {
        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();

        // Create a server, add service, start, and register for automatic graceful shutdown.
        Server server = InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(new TikaGrpcServerImpl("tika-config.xml")).build().start();
        resources.register(server, Duration.ofSeconds(10));

        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        resources.register(channel, Duration.ofSeconds(10));
        TikaGrpc.TikaBlockingStub blockingStub = TikaGrpc.newBlockingStub(channel);

        String fetcherId = "fetcherIdHere";
        String targetFolder = new File("target").getAbsolutePath();
        CreateFetcherReply reply = blockingStub.createFetcher(CreateFetcherRequest.newBuilder().setName(fetcherId)
                .setFetcherClass(FileSystemFetcher.class.getName())
                .putParams("basePath", targetFolder).putParams("extractFileSystemMetadata", "true")
                .build());

        assertEquals(fetcherId, reply.getMessage());
    }
}
