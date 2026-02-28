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
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import com.google.rpc.Status;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.DeleteFetcherReply;
import org.apache.tika.DeleteFetcherRequest;
import org.apache.tika.DeletePipesIteratorReply;
import org.apache.tika.DeletePipesIteratorRequest;
import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.GetFetcherConfigJsonSchemaReply;
import org.apache.tika.GetFetcherConfigJsonSchemaRequest;
import org.apache.tika.GetFetcherReply;
import org.apache.tika.GetFetcherRequest;
import org.apache.tika.GetPipesIteratorReply;
import org.apache.tika.GetPipesIteratorRequest;
import org.apache.tika.ListFetchersReply;
import org.apache.tika.ListFetchersRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.SavePipesIteratorReply;
import org.apache.tika.SavePipesIteratorRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.core.PipesClient;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.config.ConfigStore;
import org.apache.tika.pipes.core.config.ConfigStoreFactory;
import org.apache.tika.pipes.core.fetcher.FetcherManager;
import org.apache.tika.pipes.ignite.server.IgniteStoreServer;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.plugins.TikaPluginManager;

class TikaGrpcServerImpl extends TikaGrpc.TikaImplBase {
    private static final Logger LOG = LoggerFactory.getLogger(TikaGrpcServerImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .build();
    public static final JsonSchemaGenerator JSON_SCHEMA_GENERATOR = new JsonSchemaGenerator(OBJECT_MAPPER);

    private static final String PIPES_ITERATOR_PREFIX = "pipesIterator:";

    PipesConfig pipesConfig;
    PipesClient pipesClient;
    FetcherManager fetcherManager;
    ConfigStore configStore;
    Path tikaConfigPath;
    PluginManager pluginManager;
    private IgniteStoreServer igniteStoreServer;

    TikaGrpcServerImpl(String tikaConfigPath) throws TikaConfigException, IOException {
        this(tikaConfigPath, null);
    }

    TikaGrpcServerImpl(String tikaConfigPath, String pluginRootsOverride) throws TikaConfigException, IOException {
        File tikaConfigFile = new File(tikaConfigPath);
        if (!tikaConfigFile.exists()) {
            throw new TikaConfigException("Tika config file does not exist: " + tikaConfigPath);
        }

        Path configPath = tikaConfigFile.toPath();
        this.tikaConfigPath = configPath;

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(configPath);

        // Load PipesConfig directly from root level (not from "parse-context")
        pipesConfig = tikaJsonConfig.deserialize("pipes", PipesConfig.class);
        if (pipesConfig == null) {
            pipesConfig = new PipesConfig();
        }

        pipesClient = new PipesClient(pipesConfig, configPath);
        
        try {
            if (pluginRootsOverride != null && !pluginRootsOverride.trim().isEmpty()) {
                // Use command-line plugin roots
                pluginManager = TikaPluginManager.loadFromPaths(pluginRootsOverride);
            } else {
                // Use plugin roots from config file
                pluginManager = TikaPluginManager.load(tikaJsonConfig);
            }
            pluginManager.loadPlugins();
            pluginManager.startPlugins();
        } catch (TikaConfigException e) {
            LOG.warn("Could not load plugin manager, using default: {}", e.getMessage());
            pluginManager = new org.pf4j.DefaultPluginManager();
        }

        this.configStore = createConfigStore();

        fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig, true, this.configStore);
    }

    private ConfigStore createConfigStore() throws TikaConfigException {
        String configStoreType = pipesConfig.getConfigStoreType();
        String configStoreParams = pipesConfig.getConfigStoreParams();
        ExtensionConfig storeConfig = new ExtensionConfig(
            configStoreType, configStoreType, configStoreParams);

        // If using Ignite, start the embedded server first
        if ("ignite".equalsIgnoreCase(configStoreType)) {
            startIgniteServer(storeConfig);
        }

        return ConfigStoreFactory.createConfigStore(
                pluginManager,
                configStoreType,
                storeConfig);
    }
    
    private void startIgniteServer(ExtensionConfig config) {
        try {
            LOG.info("Starting embedded Ignite server for ConfigStore");

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode params = mapper.readTree(config.json());

            String tableName = params.has("tableName") ? params.get("tableName").asText() :
                               params.has("cacheName") ? params.get("cacheName").asText() : "tika_config_store";
            String instanceName = params.has("igniteInstanceName") ? params.get("igniteInstanceName").asText() : "TikaIgniteServer";

            igniteStoreServer = new IgniteStoreServer(tableName, instanceName);
            igniteStoreServer.start();

            LOG.info("Embedded Ignite server started successfully");
        } catch (Exception e) {
            LOG.error("Failed to start embedded Ignite server", e);
            throw new RuntimeException("Failed to start Ignite server", e);
        }
    }

    @Override
    public void fetchAndParseServerSideStreaming(FetchAndParseRequest request,
                                                 StreamObserver<FetchAndParseReply> responseObserver) {
        fetchAndParseImpl(request, responseObserver);
    }

    @Override
    public StreamObserver<FetchAndParseRequest> fetchAndParseBiDirectionalStreaming(
            StreamObserver<FetchAndParseReply> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(FetchAndParseRequest fetchAndParseRequest) {
                fetchAndParseImpl(fetchAndParseRequest, responseObserver);
            }

            @Override
            public void onError(Throwable throwable) {
                LOG.error("Parse error occurred", throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void fetchAndParse(FetchAndParseRequest request,
                              StreamObserver<FetchAndParseReply> responseObserver) {
        fetchAndParseImpl(request, responseObserver);
        responseObserver.onCompleted();
    }

    private void fetchAndParseImpl(FetchAndParseRequest request,
                                   StreamObserver<FetchAndParseReply> responseObserver) {
        Fetcher fetcher;
        try {
            fetcher = fetcherManager.getFetcher(request.getFetcherId());
        } catch (TikaException | IOException e) {
            throw new RuntimeException("Could not find fetcher with name " + request.getFetcherId(), e);
        }

        Metadata tikaMetadata = new Metadata();
        try {
            ParseContext parseContext = new ParseContext();
            String additionalFetchConfigJson = request.getAdditionalFetchConfigJson();
            if (StringUtils.isNotBlank(additionalFetchConfigJson)) {
                parseContext.setJsonConfig(request.getFetcherId(), additionalFetchConfigJson);
            }
            PipesResult pipesResult = pipesClient.process(new FetchEmitTuple(request.getFetchKey(), new FetchKey(fetcher.getExtensionConfig().id(), request.getFetchKey()),
                    new EmitKey(), tikaMetadata, parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
            FetchAndParseReply.Builder fetchReplyBuilder =
                    FetchAndParseReply.newBuilder()
                                      .setFetchKey(request.getFetchKey())
                            .setStatus(pipesResult.status().name());
            if (pipesResult.status().equals(PipesResult.RESULT_STATUS.FETCH_EXCEPTION)) {
                fetchReplyBuilder.setErrorMessage(pipesResult.message());
            }
            if (pipesResult.emitData() != null && pipesResult.emitData().getMetadataList() != null) {
                for (Metadata metadata : pipesResult.emitData().getMetadataList()) {
                    for (String name : metadata.names()) {
                        String value = metadata.get(name);
                        if (value != null) {
                            fetchReplyBuilder.putFields(name, value);
                        }
                    }
                }
            }
            responseObserver.onNext(fetchReplyBuilder.build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("raw")
    @Override
    public void saveFetcher(SaveFetcherRequest request,
                              StreamObserver<SaveFetcherReply> responseObserver) {
        SaveFetcherReply reply =
                SaveFetcherReply.newBuilder().setFetcherId(request.getFetcherId()).build();
        try {
            String factoryName = findFactoryNameForClass(request.getFetcherClass());
            ExtensionConfig config = new ExtensionConfig(request.getFetcherId(), factoryName, request.getFetcherConfigJson());
            
            // Save to fetcher manager (updates ConfigStore which is shared with PipesServer)
            fetcherManager.saveFetcher(config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    private String findFactoryNameForClass(String className) throws TikaConfigException {
        var factories = pluginManager.getExtensions(org.apache.tika.pipes.api.fetcher.FetcherFactory.class);
        LOG.debug("Looking for factory that produces class: {}", className);
        LOG.debug("Found {} factories", factories.size());
        for (var factory : factories) {
            LOG.debug("Checking factory: {} (name={})", factory.getClass().getName(), factory.getName());
            try {
                // Use a permissive config that should allow most factories to create an instance
                // FileSystemFetcher requires basePath or allowAbsolutePaths
                String tempJson = "{\"allowAbsolutePaths\": true}";
                ExtensionConfig tempConfig = new ExtensionConfig("temp", factory.getName(), tempJson);
                Fetcher fetcher = factory.buildExtension(tempConfig);
                LOG.debug("Factory {} produced: {}", factory.getName(), fetcher.getClass().getName());
                if (fetcher.getClass().getName().equals(className)) {
                    return factory.getName();
                }
            } catch (Exception e) {
                LOG.debug("Could not build fetcher for factory: {} - {}", factory.getName(), e.getMessage());
            }
        }
        throw new TikaConfigException("Could not find factory for class: " + className);
    }
    static Status notFoundStatus(String fetcherId) {
        return Status.newBuilder()
                .setCode(io.grpc.Status.Code.NOT_FOUND.value())
                .setMessage("Could not find fetcher with id:" + fetcherId)
                .build();
    }

    @Override
    public void getFetcher(GetFetcherRequest request,
                           StreamObserver<GetFetcherReply> responseObserver) {
        GetFetcherReply.Builder getFetcherReply = GetFetcherReply.newBuilder();
        try {
            Fetcher fetcher = fetcherManager.getFetcher(request.getFetcherId());
            ExtensionConfig config = fetcher.getExtensionConfig();

            getFetcherReply.setFetcherId(config.id());
            // Return the class name instead of the factory name for backward compatibility
            getFetcherReply.setFetcherClass(fetcher.getClass().getName());

            Map<String, Object> paramMap = OBJECT_MAPPER.readValue(config.json(), new TypeReference<>() {
            });
            paramMap.forEach((k, v) -> getFetcherReply.putParams(Objects.toString(k), Objects.toString(v)));

            responseObserver.onNext(getFetcherReply.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(StatusProto.toStatusException(notFoundStatus(request.getFetcherId())));
        }
    }

    @Override
    public void listFetchers(ListFetchersRequest request,
                             StreamObserver<ListFetchersReply> responseObserver) {
        ListFetchersReply.Builder listFetchersReplyBuilder = ListFetchersReply.newBuilder();
        for (String fetcherId : fetcherManager.getSupported()) {
            try {
                Fetcher fetcher = fetcherManager.getFetcher(fetcherId);
                ExtensionConfig config = fetcher.getExtensionConfig();

                GetFetcherReply.Builder replyBuilder = GetFetcherReply.newBuilder().setFetcherId(config.id()).setFetcherClass(fetcher.getClass().getName());

                Map<String, Object> paramMap = OBJECT_MAPPER.readValue(config.json(), new TypeReference<>() {
                });
                paramMap.forEach((k, v) -> replyBuilder.putParams(Objects.toString(k), Objects.toString(v)));

                listFetchersReplyBuilder.addGetFetcherReplies(replyBuilder.build());
            } catch (Exception e) {
                LOG.error("Error listing fetcher: {}", fetcherId, e);
            }
        }
        responseObserver.onNext(listFetchersReplyBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void deleteFetcher(DeleteFetcherRequest request,
                              StreamObserver<DeleteFetcherReply> responseObserver) {
        boolean successfulDelete = deleteFetcher(request.getFetcherId());
        responseObserver.onNext(DeleteFetcherReply.newBuilder().setSuccess(successfulDelete).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getFetcherConfigJsonSchema(GetFetcherConfigJsonSchemaRequest request, StreamObserver<GetFetcherConfigJsonSchemaReply> responseObserver) {
        GetFetcherConfigJsonSchemaReply.Builder builder = GetFetcherConfigJsonSchemaReply.newBuilder();
        try {
            JsonSchema jsonSchema = JSON_SCHEMA_GENERATOR.generateSchema(Class.forName(request.getFetcherClass()));
            builder.setFetcherConfigJsonSchema(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema));
        } catch (ClassNotFoundException | JsonProcessingException e) {
            throw new RuntimeException("Could not create json schema for " + request.getFetcherClass(), e);
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    private boolean deleteFetcher(String id) {
        try {
            // Delete from fetcher manager (updates ConfigStore which is shared with PipesServer)
            fetcherManager.deleteFetcher(id);
            LOG.info("Successfully deleted fetcher: {}", id);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to delete fetcher: {}", id, e);
            return false;
        }
    }
    
    // ========== PipesIterator RPC Methods ==========
    
    @Override
    public void savePipesIterator(SavePipesIteratorRequest request,
                                  StreamObserver<SavePipesIteratorReply> responseObserver) {
        try {
            String iteratorId = request.getIteratorId();
            String iteratorClass = request.getIteratorClass();
            String iteratorConfigJson = request.getIteratorConfigJson();

            LOG.info("Saving pipes iterator: id={}, class={}", iteratorId, iteratorClass);

            ExtensionConfig config = new ExtensionConfig(iteratorId, iteratorClass, iteratorConfigJson);

            // Save directly to ConfigStore (shared with PipesServer)
            configStore.put(PIPES_ITERATOR_PREFIX + iteratorId, config);

            SavePipesIteratorReply reply = SavePipesIteratorReply.newBuilder()
                    .setMessage("Pipes iterator saved successfully")
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();

            LOG.info("Successfully saved pipes iterator: {}", iteratorId);

        } catch (Exception e) {
            LOG.error("Failed to save pipes iterator", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to save pipes iterator: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }
    
    @Override
    public void getPipesIterator(GetPipesIteratorRequest request,
                                 StreamObserver<GetPipesIteratorReply> responseObserver) {
        try {
            String iteratorId = request.getIteratorId();
            LOG.info("Getting pipes iterator: {}", iteratorId);

            // Get directly from ConfigStore (shared with PipesServer)
            ExtensionConfig config = configStore.get(PIPES_ITERATOR_PREFIX + iteratorId);

            if (config == null) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Pipes iterator not found: " + iteratorId)
                        .asRuntimeException());
                return;
            }

            GetPipesIteratorReply reply = GetPipesIteratorReply.newBuilder()
                    .setIteratorId(config.id())
                    .setIteratorClass(config.name())
                    .setIteratorConfigJson(config.json())
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();

            LOG.info("Successfully retrieved pipes iterator: {}", iteratorId);

        } catch (Exception e) {
            LOG.error("Failed to get pipes iterator", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get pipes iterator: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }
    
    @Override
    public void deletePipesIterator(DeletePipesIteratorRequest request,
                                    StreamObserver<DeletePipesIteratorReply> responseObserver) {
        try {
            String iteratorId = request.getIteratorId();
            LOG.info("Deleting pipes iterator: {}", iteratorId);

            // Delete directly from ConfigStore (shared with PipesServer)
            configStore.remove(PIPES_ITERATOR_PREFIX + iteratorId);

            DeletePipesIteratorReply reply = DeletePipesIteratorReply.newBuilder()
                    .setMessage("Pipes iterator deleted successfully")
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();

            LOG.info("Successfully deleted pipes iterator: {}", iteratorId);

        } catch (Exception e) {
            LOG.error("Failed to delete pipes iterator", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to delete pipes iterator: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    /**
     * Releases resources, including the embedded Ignite server if one was started.
     */
    public void shutdown() {
        if (igniteStoreServer != null) {
            LOG.info("Shutting down embedded Ignite server");
            try {
                igniteStoreServer.close();
                igniteStoreServer = null;
            } catch (Exception e) {
                LOG.error("Error shutting down Ignite server", e);
            }
        }
    }
}
