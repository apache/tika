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

import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import org.apache.tika.CreateFetcherReply;
import org.apache.tika.CreateFetcherRequest;
import org.apache.tika.TikaGrpc;

@RunWith(JUnit4.class)
public class TikaGrpcServerTest {
    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @Test
    public void greeterImpl_replyMessage() throws Exception {
        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();

        // Create a server, add service, start, and register for automatic graceful shutdown.
        grpcCleanup.register(InProcessServerBuilder.forName(serverName).directExecutor()
                .addService(new TikaGrpcServerImpl("tika-config.xml")).build().start());

        TikaGrpc.TikaBlockingStub blockingStub = TikaGrpc.newBlockingStub(
                // Create a client channel and register for automatic graceful shutdown.
                grpcCleanup.register(
                        InProcessChannelBuilder.forName(serverName).directExecutor().build()));


        String testName = "test name";
        CreateFetcherReply reply = blockingStub.createFetcher(
                CreateFetcherRequest.newBuilder().setName(testName).build());

        assertEquals(testName, reply.getMessage());
    }
}
