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
import java.util.ArrayList;
import java.util.List;
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
import org.apache.tika.grpc.mapper.DocumentBuilder;
import org.apache.tika.grpc.v1.Document;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.fetcher.FetcherFactory;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorFactory;
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
    TikaGrpcConfig tikaGrpcConfig;
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
        // ParseBytes needs a fetcher the FORKED worker can resolve. Runtime store writes do
        // not reach the worker with the default in-memory config store, so the server hands
        // every component (its own managers and the forked worker alike) an augmented copy
        // of the config with an internal file-system fetcher rooted at a server temp dir.
        this.parseBytesDir = java.nio.file.Files.createTempDirectory("tika-grpc-parse-bytes");
        configPath = augmentWithParseBytesFetcher(configPath, parseBytesDir);
        this.tikaConfigPath = configPath;

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(configPath);

        // Load PipesConfig directly from root level (not from "parse-context")
        pipesConfig = tikaJsonConfig.deserialize("pipes", PipesConfig.class);
        if (pipesConfig == null) {
            pipesConfig = new PipesConfig();
        }

        // Security-sensitive grpc features (per-request config, runtime component
        // modifications) are off unless explicitly enabled in the "grpc" section.
        tikaGrpcConfig = TikaGrpcConfig.load(tikaJsonConfig);

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

        if (pluginManager.getPlugins().isEmpty()) {
            LOG.warn("tika-grpc started with no tika-pipes plugins loaded. "
                    + "Most RPC calls will fail with 'fetcher type unknown' or "
                    + "similar. Place tika-pipes-<plugin>-<version>.zip files in "
                    + "a `plugins/` directory next to tika-grpc.jar (or configure "
                    + "`plugin-roots` in your tika config). Plugin zips are "
                    + "published at https://downloads.apache.org/tika/<version>/.");
        }

        this.configStore = createConfigStore();

        fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig,
                tikaGrpcConfig.isAllowComponentManagement(), this.configStore);
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

    /**
     * If the operator has not opted in to runtime component management, closes
     * the call with {@code PERMISSION_DENIED} and returns {@code true}. Guards the
     * Save/Delete fetcher and pipes-iterator RPCs. The caller must {@code return}
     * immediately when this returns {@code true}.
     * <p>
     * We close the observer here rather than throwing, because a
     * {@link io.grpc.StatusRuntimeException} thrown out of a service method is
     * reported to the client as {@code UNKNOWN}; only {@code onError} transmits
     * the intended status.
     */
    private boolean denyComponentManagement(StreamObserver<?> responseObserver) {
        if (tikaGrpcConfig.isAllowComponentManagement()) {
            return false;
        }
        responseObserver.onError(io.grpc.Status.PERMISSION_DENIED
                .withDescription("Runtime component management is disabled. Set "
                        + "'allowComponentManagement' to true in the 'grpc' section of your "
                        + "tika-config to allow SaveFetcher/DeleteFetcher/SavePipesIterator/"
                        + "DeletePipesIterator. Understand the security implications first.")
                .asRuntimeException());
        return true;
    }

    /**
     * If the request carries per-request configuration
     * ({@code additional_fetch_config_json} or {@code parse_context_json}) but the
     * operator has not opted in, closes the call with {@code PERMISSION_DENIED} and
     * returns {@code true}. A request with no per-request config is always allowed.
     * The caller must {@code return} immediately when this returns {@code true}.
     */
    private boolean denyPerRequestConfig(FetchAndParseRequest request,
                                         StreamObserver<?> responseObserver) {
        boolean hasPerRequestConfig =
                StringUtils.isNotBlank(request.getAdditionalFetchConfigJson())
                        || StringUtils.isNotBlank(request.getParseContextJson());
        if (!hasPerRequestConfig || tikaGrpcConfig.isAllowPerRequestConfig()) {
            return false;
        }
        responseObserver.onError(io.grpc.Status.PERMISSION_DENIED
                .withDescription("Per-request configuration is disabled. Set "
                        + "'allowPerRequestConfig' to true in the 'grpc' section of your "
                        + "tika-config to allow additional_fetch_config_json / "
                        + "parse_context_json. Understand the security implications first.")
                .asRuntimeException());
        return true;
    }

    @Override
    public void fetchAndParseServerSideStreaming(FetchAndParseRequest request,
                                                 StreamObserver<FetchAndParseReply> responseObserver) {
        if (denyPerRequestConfig(request, responseObserver)) {
            return;
        }
        fetchAndParseImpl(request, responseObserver);
    }

    @Override
    public StreamObserver<FetchAndParseRequest> fetchAndParseBiDirectionalStreaming(
            StreamObserver<FetchAndParseReply> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(FetchAndParseRequest fetchAndParseRequest) {
                if (denyPerRequestConfig(fetchAndParseRequest, responseObserver)) {
                    return;
                }
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
        if (denyPerRequestConfig(request, responseObserver)) {
            return;
        }
        fetchAndParseImpl(request, responseObserver);
        responseObserver.onCompleted();
    }

    private static final String PARSE_BYTES_FETCHER_ID = "__tika_grpc_parse_bytes";
    private final java.nio.file.Path parseBytesDir;

    // Writes a sibling temp copy of the config with the internal parse-bytes fetcher added
    // to the `fetchers` section, so it is loaded exactly like a user-defined fetcher.
    private static Path augmentWithParseBytesFetcher(Path configPath, java.nio.file.Path dir)
            throws IOException {
        com.fasterxml.jackson.databind.node.ObjectNode root =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        OBJECT_MAPPER.readTree(configPath.toFile());
        com.fasterxml.jackson.databind.node.ObjectNode fetchers =
                root.has("fetchers") && root.get("fetchers").isObject()
                        ? (com.fasterxml.jackson.databind.node.ObjectNode) root.get("fetchers")
                        : root.putObject("fetchers");
        fetchers.putObject(PARSE_BYTES_FETCHER_ID)
                .putObject("file-system-fetcher")
                .put("basePath", dir.toAbsolutePath().toString());
        Path augmented = java.nio.file.Files.createTempFile("tika-grpc-config", ".json");
        java.nio.file.Files.writeString(augmented,
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        return augmented;
    }

    @Override
    public void parseBytes(org.apache.tika.ParseBytesRequest request,
                           StreamObserver<FetchAndParseReply> responseObserver) {
        if (request.getRequestId().isBlank()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("request_id is required").asRuntimeException());
            return;
        }
        if (request.getContent().isEmpty()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("content is required").asRuntimeException());
            return;
        }
        FetchAndParseRequest internal = FetchAndParseRequest.newBuilder()
                .setFetcherId(PARSE_BYTES_FETCHER_ID)
                .setFetchKey(request.getRequestId())
                .setParseContextJson(request.getParseContextJson())
                .setRenderMarkdown(request.getRenderMarkdown())
                .build();
        if (denyPerRequestConfig(internal, responseObserver)) {
            return;
        }
        java.nio.file.Path file = null;
        try {
            // The forked worker fetches by key relative to the internal fetcher's base path;
            // the caller-visible id stays request_id while the on-disk name stays unique.
            String tempName = java.util.UUID.randomUUID().toString();
            file = parseBytesDir.resolve(tempName);
            java.nio.file.Files.write(file, request.getContent().toByteArray());
            FetchAndParseRequest keyed = internal.toBuilder().setFetchKey(tempName).build();
            fetchAndParseImpl(keyed, new StreamObserver<>() {
                @Override
                public void onNext(FetchAndParseReply reply) {
                    Document document = reply.getDocument().toBuilder()
                            .setId(request.getRequestId()).build();
                    responseObserver.onNext(reply.toBuilder()
                            .setFetchKey(request.getRequestId())
                            .setDocument(document).build());
                }

                @Override
                public void onError(Throwable t) {
                    responseObserver.onError(t);
                }

                @Override
                public void onCompleted() {
                }
            });
            responseObserver.onCompleted();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (file != null) {
                try {
                    java.nio.file.Files.deleteIfExists(file);
                } catch (IOException ignore) {
                    // best effort; the directory is a server temp dir
                }
            }
        }
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
        // Times the whole pipesClient.process() round trip: fetch and parse both happen
        // inside the forked pipes worker, so this is fetch+parse latency, not parse-only.
        long fetchParseStart = System.nanoTime();
        try {
            ParseContext parseContext = new ParseContext();
            String additionalFetchConfigJson = request.getAdditionalFetchConfigJson();
            if (StringUtils.isNotBlank(additionalFetchConfigJson)) {
                parseContext.setJsonConfig(request.getFetcherId(), additionalFetchConfigJson);
            }
            String parseContextJson = request.getParseContextJson();
            if (StringUtils.isNotBlank(parseContextJson)) {
                com.fasterxml.jackson.databind.JsonNode contextNode = OBJECT_MAPPER.readTree(parseContextJson);
                contextNode.fields().forEachRemaining(entry ->
                        parseContext.setJsonConfig(entry.getKey(), entry.getValue().toString()));
            }
            PipesResult pipesResult = pipesClient.process(new FetchEmitTuple(request.getFetchKey(), new FetchKey(fetcher.getExtensionConfig().id(), request.getFetchKey()),
                    new EmitKey(), tikaMetadata, parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
            FetchAndParseReply.Builder fetchReplyBuilder =
                    FetchAndParseReply.newBuilder()
                                      .setFetchKey(request.getFetchKey());
            if (pipesResult.status().equals(PipesResult.RESULT_STATUS.FETCH_EXCEPTION)) {
                fetchReplyBuilder.setErrorMessage(pipesResult.message());
            }
            long fetchParseTimeMs = (System.nanoTime() - fetchParseStart) / 1_000_000L;
            List<Metadata> metadataList = new ArrayList<>();
            if (pipesResult.emitData() != null && pipesResult.emitData().getMetadataList() != null) {
                metadataList.addAll(pipesResult.emitData().getMetadataList());
            }
            // null (not an empty Metadata) when the pipes result carried no metadata, so
            // DocumentBuilder can distinguish "nothing came back" from an empty parse.
            Metadata primary = metadataList.isEmpty() ? null : metadataList.get(0);
            String body = primary == null ? null : primary.get(TikaCoreProperties.TIKA_CONTENT);
            Document document = DocumentBuilder.build(
                    primary,
                    metadataList,
                    body,
                    request.getFetchKey(),
                    pipesResult.status().name(),
                    fetchParseTimeMs,
                    request.getRenderMarkdown());
            fetchReplyBuilder.setDocument(document);
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
        if (denyComponentManagement(responseObserver)) {
            return;
        }
        String fetcherType = request.getFetcherType();
        if (!isRegisteredFetcherType(fetcherType)) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("Unknown fetcher type: '" + fetcherType
                            + "'. Use the short factory name (e.g. 'file-system-fetcher').")
                    .asRuntimeException());
            return;
        }
        SaveFetcherReply reply =
                SaveFetcherReply.newBuilder().setFetcherId(request.getFetcherId()).build();
        try {
            // The fetcher type is the factory short name, used directly as the ConfigStore key
            // (shared with PipesServer) -- no class resolution or construction needed.
            ExtensionConfig config = new ExtensionConfig(request.getFetcherId(), fetcherType, request.getFetcherConfigJson());
            fetcherManager.saveFetcher(config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    private boolean isRegisteredFetcherType(String fetcherType) {
        return findFetcherFactory(fetcherType) != null;
    }

    private FetcherFactory findFetcherFactory(String fetcherType) {
        for (FetcherFactory factory : pluginManager.getExtensions(FetcherFactory.class)) {
            if (factory.getName().equals(fetcherType)) {
                return factory;
            }
        }
        return null;
    }

    private boolean isRegisteredIteratorType(String iteratorType) {
        if (iteratorType == null) {
            return false;
        }
        for (PipesIteratorFactory factory : pluginManager.getExtensions(PipesIteratorFactory.class)) {
            if (factory.getName().equals(iteratorType)) {
                return true;
            }
        }
        return false;
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
            getFetcherReply.setFetcherType(config.name());

            // The config may carry secrets (passwords, access keys, tokens). Only return it once the
            // operator has opted in to runtime component management; identity is always safe.
            if (tikaGrpcConfig.isAllowComponentManagement()) {
                Map<String, Object> paramMap = OBJECT_MAPPER.readValue(config.json(), new TypeReference<>() {
                });
                paramMap.forEach((k, v) -> getFetcherReply.putParams(Objects.toString(k), Objects.toString(v)));
            }

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
        // The config may carry secrets; only include it once component management is enabled.
        boolean includeConfig = tikaGrpcConfig.isAllowComponentManagement();
        for (String fetcherId : fetcherManager.getSupported()) {
            try {
                Fetcher fetcher = fetcherManager.getFetcher(fetcherId);
                ExtensionConfig config = fetcher.getExtensionConfig();

                GetFetcherReply.Builder replyBuilder = GetFetcherReply.newBuilder().setFetcherId(config.id()).setFetcherType(config.name());

                if (includeConfig) {
                    Map<String, Object> paramMap = OBJECT_MAPPER.readValue(config.json(), new TypeReference<>() {
                    });
                    paramMap.forEach((k, v) -> replyBuilder.putParams(Objects.toString(k), Objects.toString(v)));
                }

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
        if (denyComponentManagement(responseObserver)) {
            return;
        }
        boolean successfulDelete = deleteFetcher(request.getFetcherId());
        responseObserver.onNext(DeleteFetcherReply.newBuilder().setSuccess(successfulDelete).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getFetcherConfigJsonSchema(GetFetcherConfigJsonSchemaRequest request, StreamObserver<GetFetcherConfigJsonSchemaReply> responseObserver) {
        GetFetcherConfigJsonSchemaReply.Builder builder = GetFetcherConfigJsonSchemaReply.newBuilder();
        String fetcherType = request.getFetcherType();
        // Only resolve config classes from registered fetcher factories -- never load an arbitrary
        // classpath class on a client's say-so.
        FetcherFactory factory = findFetcherFactory(fetcherType);
        if (factory == null) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("Unknown fetcher type: '" + fetcherType
                            + "'. Use the short factory name (e.g. 'file-system-fetcher').")
                    .asRuntimeException());
            return;
        }
        try {
            JsonSchema jsonSchema = JSON_SCHEMA_GENERATOR.generateSchema(factory.getConfigClass());
            builder.setFetcherConfigJsonSchema(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema));
        } catch (JsonProcessingException e) {
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Could not create json schema for fetcher type " + fetcherType)
                    .withCause(e)
                    .asRuntimeException());
            return;
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
        if (denyComponentManagement(responseObserver)) {
            return;
        }
        try {
            String iteratorId = request.getIteratorId();
            String iteratorType = request.getIteratorType();
            String iteratorConfigJson = request.getIteratorConfigJson();

            // Validate the iterator type up front, mirroring saveFetcher: reject unknown types
            // rather than persisting an unvalidated entry into the shared ConfigStore that would
            // only fail later (or in a co-deployed PipesServer that consumes iterator entries).
            if (!isRegisteredIteratorType(iteratorType)) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Unknown pipes iterator type: '" + iteratorType
                                + "'. Use the short factory name (e.g. 'file-system-pipes-iterator').")
                        .asRuntimeException());
                return;
            }

            LOG.info("Saving pipes iterator: id={}, type={}", iteratorId, iteratorType);

            ExtensionConfig config = new ExtensionConfig(iteratorId, iteratorType, iteratorConfigJson);

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

            GetPipesIteratorReply.Builder reply = GetPipesIteratorReply.newBuilder()
                    .setIteratorId(config.id())
                    .setIteratorType(config.name());
            // The iterator config may carry secrets; only include it once component management is
            // enabled (identity is always safe to return).
            if (tikaGrpcConfig.isAllowComponentManagement()) {
                reply.setIteratorConfigJson(config.json());
            }
            responseObserver.onNext(reply.build());
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
        if (denyComponentManagement(responseObserver)) {
            return;
        }
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
            } catch (Exception e) {
                LOG.error("Error shutting down Ignite server", e);
            } finally {
                igniteStoreServer = null;
            }
        }
    }

    /**
     * Close the pipe client, to be called after TikaGrpcServer has shut down.
     */
    void postShutdown() {
        if (pipesClient != null) {
            LOG.info("Shutting down the pipes client");
            try {
                pipesClient.close();
            } catch (IOException e) {
                LOG.error("Error closing the pipes client", e);
            } finally {
                pipesClient = null;
            }
        }
    }
}
