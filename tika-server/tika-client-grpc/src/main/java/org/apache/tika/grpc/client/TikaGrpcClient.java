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
package org.apache.tika.grpc.client;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.DeleteEmitterReply;
import org.apache.tika.DeleteEmitterRequest;
import org.apache.tika.DeleteFetcherReply;
import org.apache.tika.DeleteFetcherRequest;
import org.apache.tika.DeletePipeIteratorReply;
import org.apache.tika.DeletePipeIteratorRequest;
import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.GetEmitterConfigJsonSchemaReply;
import org.apache.tika.GetEmitterConfigJsonSchemaRequest;
import org.apache.tika.GetEmitterReply;
import org.apache.tika.GetEmitterRequest;
import org.apache.tika.GetFetcherConfigJsonSchemaReply;
import org.apache.tika.GetFetcherConfigJsonSchemaRequest;
import org.apache.tika.GetFetcherReply;
import org.apache.tika.GetFetcherRequest;
import org.apache.tika.GetPipeIteratorConfigJsonSchemaReply;
import org.apache.tika.GetPipeIteratorConfigJsonSchemaRequest;
import org.apache.tika.GetPipeIteratorReply;
import org.apache.tika.GetPipeIteratorRequest;
import org.apache.tika.GetPipeJobReply;
import org.apache.tika.GetPipeJobRequest;
import org.apache.tika.ListEmittersReply;
import org.apache.tika.ListEmittersRequest;
import org.apache.tika.ListFetchersReply;
import org.apache.tika.ListFetchersRequest;
import org.apache.tika.ListPipeIteratorsReply;
import org.apache.tika.ListPipeIteratorsRequest;
import org.apache.tika.RunPipeJobReply;
import org.apache.tika.RunPipeJobRequest;
import org.apache.tika.SaveEmitterReply;
import org.apache.tika.SaveEmitterRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.SavePipeIteratorReply;
import org.apache.tika.SavePipeIteratorRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.grpc.client.config.TikaGrpcClientConfig;
import org.apache.tika.grpc.client.exception.TikaGrpcClientException;

/**
 * A high-level client for connecting to Apache Tika gRPC servers.
 *
 * This client provides an easy-to-use interface for interacting with Tika gRPC services,
 * abstracting away the complexity of gRPC communication while providing both synchronous
 * and asynchronous operations.
 *
 * Usage example:
 * <pre>
 * TikaGrpcClientConfig config = TikaGrpcClientConfig.builder()
 *     .host("localhost")
 *     .port(9090)
 *     .build();
 *
 * try (TikaGrpcClient client = new TikaGrpcClient(config)) {
 *     // Save a fetcher configuration
 *     client.saveFetcher("my-fetcher", "file-system-fetcher", "{\"basePath\": \"/tmp\"}");
 *
 *     // Fetch and parse a document
 *     FetchAndParseReply result = client.fetchAndParse("my-fetcher", "document.pdf");
 *     System.out.println("Status: " + result.getStatus());
 * }
 * </pre>
 */
public class TikaGrpcClient implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(TikaGrpcClient.class);

    private final TikaGrpcClientConfig config;
    private final ManagedChannel channel;
    private final TikaGrpc.TikaBlockingStub blockingStub;
    private final TikaGrpc.TikaStub asyncStub;

    private volatile boolean closed = false;

    /**
     * Creates a new TikaGrpcClient with the specified configuration.
     *
     * @param config the client configuration
     * @throws TikaGrpcClientException if the client cannot be initialized
     */
    public TikaGrpcClient(TikaGrpcClientConfig config) throws TikaGrpcClientException {
        this.config = config;

        try {
            ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder
                .forAddress(config.getHost(), config.getPort());

            if (!config.isTlsEnabled()) {
                channelBuilder.usePlaintext();
            }

            // Apply additional channel configurations
            if (config.getMaxInboundMessageSize() > 0) {
                channelBuilder.maxInboundMessageSize(config.getMaxInboundMessageSize());
            }
            if (config.getKeepAliveTimeSeconds() > 0) {
                channelBuilder.keepAliveTime(config.getKeepAliveTimeSeconds(), TimeUnit.SECONDS);
            }
            if (config.getKeepAliveTimeoutSeconds() > 0) {
                channelBuilder.keepAliveTimeout(config.getKeepAliveTimeoutSeconds(), TimeUnit.SECONDS);
            }

            this.channel = channelBuilder.build();
            this.blockingStub = TikaGrpc.newBlockingStub(channel);
            this.asyncStub = TikaGrpc.newStub(channel);

            LOG.info("TikaGrpcClient initialized for {}:{}", config.getHost(), config.getPort());

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to initialize TikaGrpcClient", e);
        }
    }

    /**
     * Creates a TikaGrpcClient with default configuration (localhost:9090).
     *
     * @return a new TikaGrpcClient instance
     * @throws TikaGrpcClientException if the client cannot be initialized
     */
    public static TikaGrpcClient createDefault() throws TikaGrpcClientException {
        return new TikaGrpcClient(TikaGrpcClientConfig.createDefault());
    }

    // Fetcher Operations

    /**
     * Saves a fetcher configuration to the server.
     *
     * @param fetcherId unique identifier for the fetcher
     * @param pluginId the plugin ID of the fetcher class
     * @param fetcherConfigJson JSON configuration for the fetcher
     * @return the fetcher ID that was saved
     * @throws TikaGrpcClientException if the operation fails
     */
    public String saveFetcher(String fetcherId, String pluginId, String fetcherConfigJson)
            throws TikaGrpcClientException {
        checkNotClosed();

        try {
            SaveFetcherRequest request = SaveFetcherRequest.newBuilder()
                .setFetcherId(fetcherId)
                .setPluginId(pluginId)
                .setFetcherConfigJson(fetcherConfigJson)
                .build();

            SaveFetcherReply reply = blockingStub.saveFetcher(request);
            return reply.getFetcherId();

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to save fetcher: " + fetcherId, e);
        }
    }

    /**
     * Retrieves fetcher information from the server.
     *
     * @param fetcherId the ID of the fetcher to retrieve
     * @return fetcher information
     * @throws TikaGrpcClientException if the operation fails
     */
    public GetFetcherReply getFetcher(String fetcherId) throws TikaGrpcClientException {
        checkNotClosed();

        try {
            GetFetcherRequest request = GetFetcherRequest.newBuilder()
                .setFetcherId(fetcherId)
                .build();

            return blockingStub.getFetcher(request);

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to get fetcher: " + fetcherId, e);
        }
    }

    /**
     * Lists all fetchers stored on the server.
     *
     * @param pageNumber the page number (starting from 1)
     * @param pageSize the number of fetchers per page
     * @return list of fetcher information
     * @throws TikaGrpcClientException if the operation fails
     */
    public ListFetchersReply listFetchers(int pageNumber, int pageSize) throws TikaGrpcClientException {
        checkNotClosed();

        try {
            ListFetchersRequest request = ListFetchersRequest.newBuilder()
                .setPageNumber(pageNumber)
                .setNumFetchersPerPage(pageSize)
                .build();

            return blockingStub.listFetchers(request);

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to list fetchers", e);
        }
    }

    /**
     * Deletes a fetcher from the server.
     *
     * @param fetcherId the ID of the fetcher to delete
     * @return true if the deletion was successful
     * @throws TikaGrpcClientException if the operation fails
     */
    public boolean deleteFetcher(String fetcherId) throws TikaGrpcClientException {
        checkNotClosed();

        try {
            DeleteFetcherRequest request = DeleteFetcherRequest.newBuilder()
                .setFetcherId(fetcherId)
                .build();

            DeleteFetcherReply reply = blockingStub.deleteFetcher(request);
            return reply.getSuccess();

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to delete fetcher: " + fetcherId, e);
        }
    }

    /**
     * Gets the JSON schema for a fetcher configuration.
     *
     * @param pluginId the plugin ID of the fetcher
     * @return the JSON schema as a string
     * @throws TikaGrpcClientException if the operation fails
     */
    public String getFetcherConfigJsonSchema(String pluginId) throws TikaGrpcClientException {
        checkNotClosed();

        try {
            GetFetcherConfigJsonSchemaRequest request = GetFetcherConfigJsonSchemaRequest.newBuilder()
                .setPluginId(pluginId)
                .build();

            GetFetcherConfigJsonSchemaReply reply = blockingStub.getFetcherConfigJsonSchema(request);
            return reply.getFetcherConfigJsonSchema();

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to get fetcher config schema for: " + pluginId, e);
        }
    }

    // Parse Operations

    /**
     * Fetches and parses a document using the specified fetcher.
     *
     * @param fetcherId the ID of the fetcher to use
     * @param fetchKey the key/path of the document to fetch
     * @return the parse result containing metadata and status
     * @throws TikaGrpcClientException if the operation fails
     */
    public FetchAndParseReply fetchAndParse(String fetcherId, String fetchKey)
            throws TikaGrpcClientException {
        return fetchAndParse(fetcherId, fetchKey, null, null, null);
    }

    /**
     * Fetches and parses a document with additional configuration.
     *
     * @param fetcherId the ID of the fetcher to use
     * @param fetchKey the key/path of the document to fetch
     * @param fetchMetadataJson additional fetch metadata (optional)
     * @param addedMetadataJson additional metadata to add to the result (optional)
     * @param parseContextJson custom parse context configuration (optional)
     * @return the parse result containing metadata and status
     * @throws TikaGrpcClientException if the operation fails
     */
    public FetchAndParseReply fetchAndParse(String fetcherId, String fetchKey,
                                           String fetchMetadataJson, String addedMetadataJson,
                                           String parseContextJson) throws TikaGrpcClientException {
        checkNotClosed();

        try {
            FetchAndParseRequest.Builder requestBuilder = FetchAndParseRequest.newBuilder()
                .setFetcherId(fetcherId)
                .setFetchKey(fetchKey);

            if (fetchMetadataJson != null) {
                requestBuilder.setFetchMetadataJson(fetchMetadataJson);
            }
            if (addedMetadataJson != null) {
                requestBuilder.setAddedMetadataJson(addedMetadataJson);
            }
            if (parseContextJson != null) {
                requestBuilder.setParseContextJson(parseContextJson);
            }

            return blockingStub.fetchAndParse(requestBuilder.build());

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to fetch and parse: " + fetchKey, e);
        }
    }

    /**
     * Fetches and parses a document asynchronously.
     *
     * @param fetcherId the ID of the fetcher to use
     * @param fetchKey the key/path of the document to fetch
     * @return a CompletableFuture containing the parse result
     */
    public CompletableFuture<FetchAndParseReply> fetchAndParseAsync(String fetcherId, String fetchKey) {
        return fetchAndParseAsync(fetcherId, fetchKey, null, null, null);
    }

    /**
     * Fetches and parses a document asynchronously with additional configuration.
     *
     * @param fetcherId the ID of the fetcher to use
     * @param fetchKey the key/path of the document to fetch
     * @param fetchMetadataJson additional fetch metadata (optional)
     * @param addedMetadataJson additional metadata to add to the result (optional)
     * @param parseContextJson custom parse context configuration (optional)
     * @return a CompletableFuture containing the parse result
     */
    public CompletableFuture<FetchAndParseReply> fetchAndParseAsync(String fetcherId, String fetchKey,
                                                                   String fetchMetadataJson, String addedMetadataJson,
                                                                   String parseContextJson) {
        CompletableFuture<FetchAndParseReply> future = new CompletableFuture<>();

        try {
            checkNotClosed();
        } catch (TikaGrpcClientException e) {
            future.completeExceptionally(e);
            return future;
        }

        try {
            FetchAndParseRequest.Builder requestBuilder = FetchAndParseRequest.newBuilder()
                .setFetcherId(fetcherId)
                .setFetchKey(fetchKey);

            if (fetchMetadataJson != null) {
                requestBuilder.setFetchMetadataJson(fetchMetadataJson);
            }
            if (addedMetadataJson != null) {
                requestBuilder.setAddedMetadataJson(addedMetadataJson);
            }
            if (parseContextJson != null) {
                requestBuilder.setParseContextJson(parseContextJson);
            }

            asyncStub.fetchAndParse(requestBuilder.build(), new StreamObserver<>() {
                @Override
                public void onNext(FetchAndParseReply reply) {
                    future.complete(reply);
                }

                @Override
                public void onError(Throwable t) {
                    future.completeExceptionally(new TikaGrpcClientException("Async fetch and parse failed", t));
                }

                @Override
                public void onCompleted() {
                    // Response already handled in onNext
                }
            });

        } catch (Exception e) {
            future.completeExceptionally(new TikaGrpcClientException("Failed to start async fetch and parse", e));
        }

        return future;
    }

    // Emitter Operations

    /**
     * Saves an emitter configuration to the server.
     *
     * @param emitterId unique identifier for the emitter
     * @param pluginId the plugin ID of the emitter class
     * @param emitterConfigJson JSON configuration for the emitter
     * @return the emitter ID that was saved
     * @throws TikaGrpcClientException if the operation fails
     */
    public String saveEmitter(String emitterId, String pluginId, String emitterConfigJson)
            throws TikaGrpcClientException {
        checkNotClosed();

        try {
            SaveEmitterRequest request = SaveEmitterRequest.newBuilder()
                .setEmitterId(emitterId)
                .setPluginId(pluginId)
                .setEmitterConfigJson(emitterConfigJson)
                .build();

            SaveEmitterReply reply = blockingStub.saveEmitter(request);
            return reply.getEmitterId();

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to save emitter: " + emitterId, e);
        }
    }

    /**
     * Retrieves emitter information from the server.
     *
     * @param emitterId the ID of the emitter to retrieve
     * @return emitter information
     * @throws TikaGrpcClientException if the operation fails
     */
    public GetEmitterReply getEmitter(String emitterId) throws TikaGrpcClientException {
        checkNotClosed();

        try {
            GetEmitterRequest request = GetEmitterRequest.newBuilder()
                .setEmitterId(emitterId)
                .build();

            return blockingStub.getEmitter(request);

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to get emitter: " + emitterId, e);
        }
    }

    /**
     * Lists all emitters stored on the server.
     *
     * @param pageNumber the page number (starting from 1)
     * @param pageSize the number of emitters per page
     * @return list of emitter information
     * @throws TikaGrpcClientException if the operation fails
     */
    public ListEmittersReply listEmitters(int pageNumber, int pageSize) throws TikaGrpcClientException {
        checkNotClosed();

        try {
            ListEmittersRequest request = ListEmittersRequest.newBuilder()
                .setPageNumber(pageNumber)
                .setNumEmittersPerPage(pageSize)
                .build();

            return blockingStub.listEmitters(request);

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to list emitters", e);
        }
    }

    /**
     * Deletes an emitter from the server.
     *
     * @param emitterId the ID of the emitter to delete
     * @return true if the deletion was successful
     * @throws TikaGrpcClientException if the operation fails
     */
    public boolean deleteEmitter(String emitterId) throws TikaGrpcClientException {
        checkNotClosed();

        try {
            DeleteEmitterRequest request = DeleteEmitterRequest.newBuilder()
                .setEmitterId(emitterId)
                .build();

            DeleteEmitterReply reply = blockingStub.deleteEmitter(request);
            return reply.getSuccess();

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to delete emitter: " + emitterId, e);
        }
    }

    /**
     * Gets the JSON schema for an emitter configuration.
     *
     * @param pluginId the plugin ID of the emitter
     * @return the JSON schema as a string
     * @throws TikaGrpcClientException if the operation fails
     */
    public String getEmitterConfigJsonSchema(String pluginId) throws TikaGrpcClientException {
        checkNotClosed();

        try {
            GetEmitterConfigJsonSchemaRequest request = GetEmitterConfigJsonSchemaRequest.newBuilder()
                .setPluginId(pluginId)
                .build();

            GetEmitterConfigJsonSchemaReply reply = blockingStub.getEmitterConfigJsonSchema(request);
            return reply.getEmitterConfigJsonSchema();

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to get emitter config schema for: " + pluginId, e);
        }
    }

    // Pipe Iterator Operations

    /**
     * Saves a pipe iterator configuration to the server.
     *
     * @param pipeIteratorId unique identifier for the pipe iterator
     * @param pluginId the plugin ID of the pipe iterator class
     * @param pipeIteratorConfigJson JSON configuration for the pipe iterator
     * @return the pipe iterator ID that was saved
     * @throws TikaGrpcClientException if the operation fails
     */
    public String savePipeIterator(String pipeIteratorId, String pluginId, String pipeIteratorConfigJson)
            throws TikaGrpcClientException {
        checkNotClosed();

        try {
            SavePipeIteratorRequest request = SavePipeIteratorRequest.newBuilder()
                .setPipeIteratorId(pipeIteratorId)
                .setPluginId(pluginId)
                .setPipeIteratorConfigJson(pipeIteratorConfigJson)
                .build();

            SavePipeIteratorReply reply = blockingStub.savePipeIterator(request);
            return reply.getPipeIteratorId();

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to save pipe iterator: " + pipeIteratorId, e);
        }
    }

    /**
     * Retrieves pipe iterator information from the server.
     *
     * @param pipeIteratorId the ID of the pipe iterator to retrieve
     * @return pipe iterator information
     * @throws TikaGrpcClientException if the operation fails
     */
    public GetPipeIteratorReply getPipeIterator(String pipeIteratorId) throws TikaGrpcClientException {
        checkNotClosed();

        try {
            GetPipeIteratorRequest request = GetPipeIteratorRequest.newBuilder()
                .setPipeIteratorId(pipeIteratorId)
                .build();

            return blockingStub.getPipeIterator(request);

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to get pipe iterator: " + pipeIteratorId, e);
        }
    }

    /**
     * Lists all pipe iterators stored on the server.
     *
     * @param pageNumber the page number (starting from 1)
     * @param pageSize the number of pipe iterators per page
     * @return list of pipe iterator information
     * @throws TikaGrpcClientException if the operation fails
     */
    public ListPipeIteratorsReply listPipeIterators(int pageNumber, int pageSize) throws TikaGrpcClientException {
        checkNotClosed();

        try {
            ListPipeIteratorsRequest request = ListPipeIteratorsRequest.newBuilder()
                .setPageNumber(pageNumber)
                .setNumPipeIteratorsPerPage(pageSize)
                .build();

            return blockingStub.listPipeIterators(request);

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to list pipe iterators", e);
        }
    }

    /**
     * Deletes a pipe iterator from the server.
     *
     * @param pipeIteratorId the ID of the pipe iterator to delete
     * @return true if the deletion was successful
     * @throws TikaGrpcClientException if the operation fails
     */
    public boolean deletePipeIterator(String pipeIteratorId) throws TikaGrpcClientException {
        checkNotClosed();

        try {
            DeletePipeIteratorRequest request = DeletePipeIteratorRequest.newBuilder()
                .setPipeIteratorId(pipeIteratorId)
                .build();

            DeletePipeIteratorReply reply = blockingStub.deletePipeIterator(request);
            return reply.getSuccess();

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to delete pipe iterator: " + pipeIteratorId, e);
        }
    }

    /**
     * Gets the JSON schema for a pipe iterator configuration.
     *
     * @param pluginId the plugin ID of the pipe iterator
     * @return the JSON schema as a string
     * @throws TikaGrpcClientException if the operation fails
     */
    public String getPipeIteratorConfigJsonSchema(String pluginId) throws TikaGrpcClientException {
        checkNotClosed();

        try {
            GetPipeIteratorConfigJsonSchemaRequest request = GetPipeIteratorConfigJsonSchemaRequest.newBuilder()
                .setPluginId(pluginId)
                .build();

            GetPipeIteratorConfigJsonSchemaReply reply = blockingStub.getPipeIteratorConfigJsonSchema(request);
            return reply.getPipeIteratorConfigJsonSchema();

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to get pipe iterator config schema for: " + pluginId, e);
        }
    }

    // Pipe Job Operations

    /**
     * Runs a pipe job and returns the job ID.
     *
     * @param pipeIteratorId the ID of the pipe iterator to use
     * @param fetcherId the ID of the fetcher to use
     * @param emitterId the ID of the emitter to use
     * @param timeoutSeconds hard timeout for job completion
     * @return the job ID
     * @throws TikaGrpcClientException if the operation fails
     */
    public String runPipeJob(String pipeIteratorId, String fetcherId, String emitterId, int timeoutSeconds)
            throws TikaGrpcClientException {
        checkNotClosed();

        try {
            RunPipeJobRequest request = RunPipeJobRequest.newBuilder()
                .setPipeIteratorId(pipeIteratorId)
                .setFetcherId(fetcherId)
                .setEmitterId(emitterId)
                .setJobCompletionTimeoutSeconds(timeoutSeconds)
                .build();

            RunPipeJobReply reply = blockingStub.runPipeJob(request);
            return reply.getPipeJobId();

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to run pipe job", e);
        }
    }

    /**
     * Gets the status of a pipe job.
     *
     * @param pipeJobId the ID of the pipe job
     * @return the pipe job status
     * @throws TikaGrpcClientException if the operation fails
     */
    public GetPipeJobReply getPipeJob(String pipeJobId) throws TikaGrpcClientException {
        checkNotClosed();

        try {
            GetPipeJobRequest request = GetPipeJobRequest.newBuilder()
                .setPipeJobId(pipeJobId)
                .build();

            return blockingStub.getPipeJob(request);

        } catch (Exception e) {
            throw new TikaGrpcClientException("Failed to get pipe job: " + pipeJobId, e);
        }
    }

    // Utility Methods

    /**
     * Checks if the connection to the server is healthy.
     *
     * @return true if the connection is healthy
     */
    public boolean isConnected() {
        if (closed) {
            return false;
        }

        try {
            // Try a simple operation to test connectivity
            listFetchers(1, 1);
            return true;
        } catch (Exception e) {
            LOG.debug("Connection check failed", e);
            return false;
        }
    }

    /**
     * Gets the current client configuration.
     *
     * @return the client configuration
     */
    public TikaGrpcClientConfig getConfig() {
        return config;
    }

    private void checkNotClosed() throws TikaGrpcClientException {
        if (closed) {
            throw new TikaGrpcClientException("Client has been closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (channel != null && !channel.isShutdown()) {
                channel.shutdown();
                try {
                    if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                        LOG.warn("Channel did not terminate within 5 seconds, forcing shutdown");
                        channel.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    LOG.warn("Interrupted while waiting for channel termination", e);
                    channel.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            LOG.info("TikaGrpcClient closed");
        }
    }
}
