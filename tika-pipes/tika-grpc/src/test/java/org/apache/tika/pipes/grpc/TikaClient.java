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

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;

import org.apache.tika.CreateFetcherReply;
import org.apache.tika.CreateFetcherRequest;
import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcher;

public class TikaClient {
  private static final Logger logger = Logger.getLogger(TikaClient.class.getName());

  private final TikaGrpc.TikaBlockingStub blockingStub;

  public TikaClient(Channel channel) {
    // 'channel' here is a Channel, not a ManagedChannel, so it is not this code's responsibility to
    // shut it down.

    // Passing Channels to code makes code easier to test and makes it easier to reuse Channels.
    blockingStub = TikaGrpc.newBlockingStub(channel);
  }

  public void createFetcher(CreateFetcherRequest createFileSystemFetcherRequest) {
    CreateFetcherReply response;
    try {
      response = blockingStub.createFetcher(createFileSystemFetcherRequest);
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
      return;
    }
    logger.info("Create fetcher: " + response.getMessage());
  }

  public void fetchAndParse(FetchAndParseRequest fetchAndParseRequest) {
    FetchAndParseReply fetchReply;
    try {
      fetchReply = blockingStub.fetchAndParse(fetchAndParseRequest);
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
      return;
    }
    logger.info("Fetch reply - tika parsed metadata: " + fetchReply.getFieldsMap());
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Expects one command line argument for the base path to use for the crawl.");
      System.exit(1);
      return;
    }
    String crawlPath = args[0];
    String target = "localhost:50051";
    // Create a communication channel to the server, known as a Channel. Channels are thread-safe
    // and reusable. It is common to create channels at the beginning of your application and reuse
    // them until the application shuts down.
    //
    // For the example we use plaintext insecure credentials to avoid needing TLS certificates. To
    // use TLS, use TlsChannelCredentials instead.
    ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create())
        .build();
    try {
      TikaClient client = new TikaClient(channel);
      String fetcherId = "file-system-fetcher-" + UUID.randomUUID();

      client.createFetcher(CreateFetcherRequest.newBuilder()
              .setName(fetcherId)
              .setFetcherClass(FileSystemFetcher.class.getName())
              .putParams("basePath", crawlPath)
              .putParams("extractFileSystemMetadata", "true")
              .build());

      client.fetchAndParse(FetchAndParseRequest.newBuilder()
                      .setFetcherName(fetcherId)
                      .setFetchKey("000164.pdf")
              .build());


    } finally {
      // ManagedChannels use resources like threads and TCP connections. To prevent leaking these
      // resources the channel should be shut down when it will no longer be used. If it may be used
      // again leave it running.
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}
