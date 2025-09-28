package org.apache.tika.pipes.grpc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import grpcstarter.server.GrpcService;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.pf4j.PluginManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

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
import org.apache.tika.Metadata;
import org.apache.tika.RunPipeJobReply;
import org.apache.tika.RunPipeJobRequest;
import org.apache.tika.SaveEmitterReply;
import org.apache.tika.SaveEmitterRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.SavePipeIteratorReply;
import org.apache.tika.SavePipeIteratorRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.Value;
import org.apache.tika.ValueList;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.core.emitter.DefaultEmitterConfig;
import org.apache.tika.pipes.core.emitter.EmitOutput;
import org.apache.tika.pipes.core.emitter.Emitter;
import org.apache.tika.pipes.core.emitter.EmitterConfig;
import org.apache.tika.pipes.core.exception.TikaPipesException;
import org.apache.tika.pipes.core.exception.TikaServerParseException;
import org.apache.tika.pipes.core.iterators.DefaultPipeIteratorConfig;
import org.apache.tika.pipes.core.iterators.PipeInput;
import org.apache.tika.pipes.core.iterators.PipeIterator;
import org.apache.tika.pipes.core.iterators.PipeIteratorConfig;
import org.apache.tika.pipes.core.parser.ParseService;
import org.apache.tika.pipes.fetchers.core.DefaultFetcherConfig;
import org.apache.tika.pipes.fetchers.core.Fetcher;
import org.apache.tika.pipes.fetchers.core.FetcherConfig;
import org.apache.tika.pipes.job.JobStatus;
import org.apache.tika.pipes.model.FetchAndParseStatus;
import org.apache.tika.pipes.repo.EmitterRepository;
import org.apache.tika.pipes.repo.FetcherRepository;
import org.apache.tika.pipes.repo.JobStatusRepository;
import org.apache.tika.pipes.repo.PipeIteratorRepository;

@Service
@Slf4j
@GrpcService
public class TikaGrpcService extends TikaGrpc.TikaImplBase {
    private final ExecutorService executorService = Executors.newCachedThreadPool(new TikaRunnerThreadFactory());
    public static final TypeReference<Map<String, Object>> MAP_STRING_OBJ_TYPE_REF = new TypeReference<>() {
    };
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JsonSchemaGenerator jsonSchemaGenerator;

    @Autowired
    private FetcherRepository fetcherRepository;

    @Autowired
    private EmitterRepository emitterRepository;

    @Autowired
    private PipeIteratorRepository pipeIteratorRepository;

    @Autowired
    private JobStatusRepository jobStatusRepository;

    @Autowired
    private Environment environment;

    @Autowired
    private ParseService parseService;

    @Autowired
    private PluginManager pluginManager;

    private Fetcher getFetcher(String pluginId) {
        return pluginManager
                .getExtensions(Fetcher.class, pluginId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new TikaPipesException("Could not find Fetcher extension for plugin " + pluginId));
    }

    private DefaultFetcherConfig newFetcherConfig(String pluginId, String fetcherId) {
        DefaultFetcherConfig fetcherConfig = new DefaultFetcherConfig();
        fetcherConfig.setPluginId(pluginId);
        fetcherConfig.setFetcherId(fetcherId);
        try {
            fetcherConfig.setConfigJson(objectMapper.writeValueAsString(getFetcherConfig(pluginId)));
        } catch (JsonProcessingException e) {
            throw new TikaPipesException("Could not parse JSON config for pluginid=" + pluginId + ", fetcherId=" + fetcherId, e);
        }
        return fetcherConfig;
    }

    private FetcherConfig getFetcherConfig(String pluginId) {
        return pluginManager
                .getExtensions(FetcherConfig.class, pluginId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new TikaPipesException("Could not find FetcherConfig extension for plugin " + pluginId));
    }

    private Emitter getEmitter(String pluginId) {
        return pluginManager
                .getExtensions(Emitter.class, pluginId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new TikaPipesException("Could not find Emitter extension for plugin " + pluginId));
    }

    private EmitterConfig getEmitterConfig(String pluginId) {
        return pluginManager
                .getExtensions(EmitterConfig.class, pluginId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new TikaPipesException("Could not find EmitterConfig extension for plugin " + pluginId));
    }

    private DefaultEmitterConfig newEmitterConfig(String pluginId, String emitterId) {
        DefaultEmitterConfig emitterConfig = new DefaultEmitterConfig();
        emitterConfig.setPluginId(pluginId);
        emitterConfig.setEmitterId(emitterId);
        try {
            emitterConfig.setConfigJson(objectMapper.writeValueAsString(getEmitterConfig(pluginId)));
        } catch (JsonProcessingException e) {
            throw new TikaPipesException("Could not parse JSON config for pluginid=" + pluginId + ", emitterId=" + emitterId, e);
        }
        return emitterConfig;
    }

    private PipeIterator getPipeIterator(String pluginId) {
        return pluginManager
                .getExtensions(PipeIterator.class, pluginId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new TikaPipesException("Could not find PipeIterator extension for plugin " + pluginId));
    }

    private PipeIteratorConfig getPipeIteratorConfig(String pluginId) {
        return pluginManager
                .getExtensions(PipeIteratorConfig.class, pluginId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new TikaPipesException("Could not find PipeIteratorConfig extension for plugin " + pluginId));
    }

    private DefaultPipeIteratorConfig newPipeIteratorConfig(String pluginId, String pipeIteratorId) {
        DefaultPipeIteratorConfig pipeIteratorConfig = new DefaultPipeIteratorConfig();
        pipeIteratorConfig.setPluginId(pluginId);
        pipeIteratorConfig.setPipeIteratorId(pipeIteratorId);
        try {
            pipeIteratorConfig.setConfigJson(objectMapper.writeValueAsString(getPipeIteratorConfig(pluginId)));
        } catch (JsonProcessingException e) {
            throw new TikaPipesException("Could not parse JSON config for pluginid=" + pluginId + ", pipeIteratorId=" + pipeIteratorId, e);
        }
        return pipeIteratorConfig;
    }

    @Override
    public void saveFetcher(SaveFetcherRequest request, StreamObserver<SaveFetcherReply> plainResponseObserver) {
        ServerCallStreamObserver<SaveFetcherReply> responseObserver =
                (ServerCallStreamObserver<SaveFetcherReply>) plainResponseObserver;
        responseObserver.setCompression("gzip");
        try {
            FetcherConfig fetcherConfig = newFetcherConfig(request.getPluginId(), request.getFetcherId());
            fetcherConfig.setFetcherId(request.getFetcherId())
                    .setPluginId(request.getPluginId())
                    .setConfigJson(request.getFetcherConfigJson());
            fetcherRepository.save(fetcherConfig.getFetcherId(), newFetcherConfig(request));
            responseObserver.onNext(SaveFetcherReply
                    .newBuilder()
                    .setFetcherId(request.getFetcherId())
                    .build());
        } catch (IOException e) {
            responseObserver.onError(Status.INTERNAL.withDescription("Could not save - " + ExceptionUtils.getStackTrace(e)).withCause(e).asException());
        }
        responseObserver.onCompleted();
    }

    private DefaultFetcherConfig newFetcherConfig(SaveFetcherRequest request) throws JsonProcessingException {
        return new DefaultFetcherConfig()
                .setFetcherId(request.getFetcherId())
                .setPluginId(request.getPluginId())
                .setConfigJson(request.getFetcherConfigJson());
    }

    @Override
    public void getFetcher(GetFetcherRequest request, StreamObserver<GetFetcherReply> plainResponseObserver) {
        ServerCallStreamObserver<GetFetcherReply> responseObserver =
                (ServerCallStreamObserver<GetFetcherReply>) plainResponseObserver;
        responseObserver.setCompression("gzip");

        DefaultFetcherConfig fetcherConfig = fetcherRepository.findByFetcherId(request.getFetcherId());
        responseObserver.onNext(GetFetcherReply
                .newBuilder()
                .setFetcherId(request.getFetcherId())
                .setPluginId(fetcherConfig.getPluginId())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void listFetchers(ListFetchersRequest request, StreamObserver<ListFetchersReply> plainResponseObserver) {
        ServerCallStreamObserver<ListFetchersReply> responseObserver =
                (ServerCallStreamObserver<ListFetchersReply>) plainResponseObserver;
        responseObserver.setCompression("gzip");
        ListFetchersReply.Builder builder = ListFetchersReply.newBuilder();
        fetcherRepository
                .findAll()
                .forEach(fetcherConfig -> builder.addGetFetcherReplies(GetFetcherReply
                        .newBuilder()
                        .setFetcherId(fetcherConfig.getFetcherId())
                        .setPluginId(fetcherConfig.getPluginId())
                        .build()));
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void deleteFetcher(DeleteFetcherRequest request, StreamObserver<DeleteFetcherReply> plainResponseObserver) {
        ServerCallStreamObserver<DeleteFetcherReply> responseObserver =
                (ServerCallStreamObserver<DeleteFetcherReply>) plainResponseObserver;
        responseObserver.setCompression("gzip");
        boolean exists = fetcherRepository.findByFetcherId(request.getFetcherId()) != null;
        if (exists) {
            fetcherRepository.deleteByFetcherId(request.getFetcherId());
        }
        responseObserver.onNext(DeleteFetcherReply
                .newBuilder()
                .setSuccess(exists)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void fetchAndParse(FetchAndParseRequest request, StreamObserver<FetchAndParseReply> plainResponseObserver) {
        ServerCallStreamObserver<FetchAndParseReply> responseObserver =
                (ServerCallStreamObserver<FetchAndParseReply>) plainResponseObserver;
        responseObserver.setCompression("gzip");
        try {
            fetchAndParseImpl(request, responseObserver);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Could not fetch and parse - " + ExceptionUtils.getStackTrace(e)).withCause(e).asException());
        }
    }

    private void fetchAndParseImpl(FetchAndParseRequest request, StreamObserver<FetchAndParseReply> responseObserver) throws IOException {
        DefaultFetcherConfig fetcherConfig = fetcherRepository.findByFetcherId(request.getFetcherId());
        if (fetcherConfig == null) {
            throw new IOException("Could not find fetcher with ID " + request.getFetcherId());
        }
        Fetcher fetcher = getFetcher(fetcherConfig.getPluginId());
        HashMap<String, Object> responseMetadata = new HashMap<>();
        // The fetcherRepository returns objects in the gprc server's classloader
        // But the fetcher.fetch will be done within the pf4j plugin.
        // If you send DefaultFetcherConfig and try to cast to the respective config you'll get a class loading error.
        // To get past this, get the correct class from the plugin manager, and convert to it.
        FetcherConfig fetcherConfigFromPluginManager = objectMapper.readValue(fetcherConfig.getConfigJson(), getFetcherConfigClassFromPluginManager(fetcherConfig));
        Map<String, Object> fetchMetadata = objectMapper.readValue(StringUtils.defaultIfBlank(request.getFetchMetadataJson(), "{}"), MAP_STRING_OBJ_TYPE_REF);
        InputStream inputStream = fetcher.fetch(fetcherConfigFromPluginManager, request.getFetchKey(), fetchMetadata, responseMetadata);
        FetchAndParseReply.Builder builder = FetchAndParseReply.newBuilder();
        builder.setFetchKey(request.getFetchKey());

        Map<String, Object> addedMetadata = objectMapper.readValue(StringUtils.defaultIfBlank(request.getAddedMetadataJson(), "{}"), MAP_STRING_OBJ_TYPE_REF);

        ParseContext parseContext;
        if (StringUtils.isNotBlank(request.getParseContextJson())) {
            parseContext = objectMapper.readValue(request.getParseContextJson(), ParseContext.class);
        } else {
            parseContext = new ParseContext();
        }
        try {
            log.info("Beginning parse for fetchKey={} with fetcherId={}", request.getFetchKey(), request.getFetcherId());
            for (Map<String, Object> metadata : parseService.parseDocument(inputStream, parseContext)) {
                Metadata.Builder metadataBuilder = Metadata.newBuilder();
                putMetadataFields(metadata, metadataBuilder);
                putMetadataFields(addedMetadata, metadataBuilder);
                builder.addMetadata(metadataBuilder.build());
            }
            builder.setStatus(FetchAndParseStatus.FETCH_AND_PARSE_SUCCESS.name());
            log.info("Successful parse for fetchKey={} with fetcherId={}", request.getFetchKey(), request.getFetcherId());
        } catch (TikaServerParseException | TikaException e) {
            log.info("Failed parse for fetchKey={} with fetcherId={} with message={}", request.getFetchKey(), request.getFetcherId(), e.getMessage());
            builder.setStatus(FetchAndParseStatus.FETCH_AND_PARSE_EXCEPTION.name());
            builder.setErrorMessage(ExceptionUtils.getRootCauseMessage(e));
        }
        responseObserver.onNext(builder.build());
    }

    private void putMetadataFields(Map<String, Object> metadata, Metadata.Builder metadataBuilder) throws JsonProcessingException {
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            ValueList.Builder valueListBuilder = ValueList.newBuilder();
            buildValueList(entry.getValue(), valueListBuilder);
            metadataBuilder.putFields(entry.getKey(), valueListBuilder.build());
        }
    }

    private void buildValueList(Object entryValue, ValueList.Builder valueListBuilder) throws JsonProcessingException {
        Value.Builder valueBuilder = Value.newBuilder();
        if (entryValue instanceof String) {
            valueBuilder.setStringValue((String) entryValue);
        } else if (entryValue instanceof Integer) {
            valueBuilder.setIntValue((Integer) entryValue);
        } else if (entryValue instanceof Long) {
            valueBuilder.setIntValue((Long) entryValue);
        } else if (entryValue instanceof Short) {
            valueBuilder.setIntValue((Short) entryValue);
        } else if (entryValue instanceof Double) {
            valueBuilder.setDoubleValue((Double) entryValue);
        } else if (entryValue instanceof Float) {
            valueBuilder.setDoubleValue((Float) entryValue);
        } else if (entryValue instanceof Boolean) {
            valueBuilder.setBoolValue((Boolean) entryValue);
        } else if (!(entryValue instanceof Object[])) {
            valueBuilder.setStringValue((String.valueOf(entryValue)));
        }
        if (entryValue instanceof Object[] arrayOfObj) {
            for (Object o : arrayOfObj) {
                buildValueList(o, valueListBuilder);
            }
        } else {
            valueListBuilder.addValues(valueBuilder.build());
        }
    }

    private Class<? extends FetcherConfig> getFetcherConfigClassFromPluginManager(DefaultFetcherConfig fetcherConfig) {
        return pluginManager
                .getExtensionClasses(FetcherConfig.class, fetcherConfig.getPluginId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Could not find a fetcher class for " + fetcherConfig.getFetcherId()));
    }

    private Class<? extends PipeIteratorConfig> getPipeIteratorConfigClassFromPluginManager(DefaultPipeIteratorConfig pipeIteratorConfig) {
        return pluginManager
                .getExtensionClasses(PipeIteratorConfig.class, pipeIteratorConfig.getPluginId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Could not find a pipe iterator class for " + pipeIteratorConfig.getPipeIteratorId()));
    }

    private Class<? extends EmitterConfig> getEmitterConfigClassFromPluginManager(DefaultEmitterConfig emitterConfig) {
        return pluginManager
                .getExtensionClasses(EmitterConfig.class, emitterConfig.getPluginId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Could not find a emitter class for " + emitterConfig.getEmitterId()));
    }

    @Override
    public void fetchAndParseServerSideStreaming(FetchAndParseRequest request, StreamObserver<FetchAndParseReply> plainResponseObserver) {
        ServerCallStreamObserver<FetchAndParseReply> responseObserver =
                (ServerCallStreamObserver<FetchAndParseReply>) plainResponseObserver;
        responseObserver.setCompression("gzip");
        try {
            fetchAndParseImpl(request, responseObserver);
            responseObserver.onCompleted();
        } catch (IOException e) {
            responseObserver.onError(Status.INTERNAL.withDescription("Could not fetch and parse - " + ExceptionUtils.getStackTrace(e)).withCause(e).asException());
        }
    }

    @Override
    public StreamObserver<FetchAndParseRequest> fetchAndParseBiDirectionalStreaming(StreamObserver<FetchAndParseReply> plainResponseObserver) {
        ServerCallStreamObserver<FetchAndParseReply> responseObserver =
                (ServerCallStreamObserver<FetchAndParseReply>) plainResponseObserver;
        responseObserver.setCompression("gzip");
        return fetchAndParseAsync(responseObserver);
    }

    private StreamObserver<FetchAndParseRequest> fetchAndParseAsync(StreamObserver<FetchAndParseReply> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(FetchAndParseRequest fetchAndParseRequest) {
                try {
                    fetchAndParseImpl(fetchAndParseRequest, responseObserver);
                } catch (Exception e) {
                    responseObserver.onError(Status.NOT_FOUND.withDescription("Could not handle next fetch and parse request " + fetchAndParseRequest + " - " + ExceptionUtils.getStackTrace(e)).withCause(e).asRuntimeException());
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Parse error occurred", throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void getFetcherConfigJsonSchema(GetFetcherConfigJsonSchemaRequest request, StreamObserver<GetFetcherConfigJsonSchemaReply> plainResponseObserver) {
        ServerCallStreamObserver<GetFetcherConfigJsonSchemaReply> responseObserver =
                (ServerCallStreamObserver<GetFetcherConfigJsonSchemaReply>) plainResponseObserver;
        responseObserver.setCompression("gzip");
        GetFetcherConfigJsonSchemaReply.Builder builder = GetFetcherConfigJsonSchemaReply.newBuilder();
        try {
            FetcherConfig fetcherConfig = getFetcherConfig(request.getPluginId());
            if (fetcherConfig == null) {
                responseObserver.onError(Status.NOT_FOUND.withDescription("Could not find fetcher config for " + request.getPluginId()).asException());
            } else {
                JsonSchema jsonSchema = jsonSchemaGenerator.generateSchema(fetcherConfig.getClass());
                builder.setFetcherConfigJsonSchema(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema));
            }
        } catch (JsonProcessingException e) {
            responseObserver.onError(Status.INTERNAL.withDescription("Could not process config - json issues " + request.getPluginId() + " - " + e).asException());
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void saveEmitter(SaveEmitterRequest request, StreamObserver<SaveEmitterReply> plainResponseObserver) {
        ServerCallStreamObserver<SaveEmitterReply> responseObserver =
                (ServerCallStreamObserver<SaveEmitterReply>) plainResponseObserver;
        responseObserver.setCompression("gzip");
        DefaultEmitterConfig emitterConfig = newEmitterConfig(request.getPluginId(), request.getEmitterId());
        emitterConfig.setEmitterId(request.getEmitterId())
                     .setPluginId(request.getPluginId())
                     .setConfigJson(request.getEmitterConfigJson());
        emitterRepository.save(emitterConfig.getEmitterId(), emitterConfig);
        responseObserver.onNext(SaveEmitterReply.newBuilder().setEmitterId(request.getEmitterId()).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getEmitter(GetEmitterRequest request, StreamObserver<GetEmitterReply> responseObserver) {
        EmitterConfig emitterConfig = emitterRepository.findByEmitterId(request.getEmitterId());
        responseObserver.onNext(GetEmitterReply.newBuilder()
                                               .setEmitterId(request.getEmitterId())
                                               .setPluginId(emitterConfig.getPluginId())
                                               .build());
        responseObserver.onCompleted();
    }

    @Override
    public void listEmitters(ListEmittersRequest request, StreamObserver<ListEmittersReply> plainResponseObserver) {
        ServerCallStreamObserver<ListEmittersReply> responseObserver =
                (ServerCallStreamObserver<ListEmittersReply>) plainResponseObserver;
        responseObserver.setCompression("gzip");
        ListEmittersReply.Builder builder = ListEmittersReply.newBuilder();
        emitterRepository.findAll().forEach(emitterConfig -> builder.addGetEmitterReplies(GetEmitterReply.newBuilder()
                                                                                                         .setEmitterId(emitterConfig.getEmitterId())
                                                                                                         .setPluginId(emitterConfig.getPluginId())
                                                                                                         .build()));
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void deleteEmitter(DeleteEmitterRequest request, StreamObserver<DeleteEmitterReply> plainResponseObserver) {
        ServerCallStreamObserver<DeleteEmitterReply> responseObserver =
                (ServerCallStreamObserver<DeleteEmitterReply>) plainResponseObserver;
        responseObserver.setCompression("gzip");
        boolean exists = emitterRepository.findByEmitterId(request.getEmitterId()) != null;
        if (exists) {
            emitterRepository.deleteByEmitterId(request.getEmitterId());
        }
        responseObserver.onNext(DeleteEmitterReply.newBuilder().setSuccess(exists).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getEmitterConfigJsonSchema(GetEmitterConfigJsonSchemaRequest request, StreamObserver<GetEmitterConfigJsonSchemaReply> plainResponseObserver) {
        ServerCallStreamObserver<GetEmitterConfigJsonSchemaReply> responseObserver =
                (ServerCallStreamObserver<GetEmitterConfigJsonSchemaReply>) plainResponseObserver;
        responseObserver.setCompression("gzip");
        GetEmitterConfigJsonSchemaReply.Builder builder = GetEmitterConfigJsonSchemaReply.newBuilder();
        try {
            EmitterConfig emitterConfig = getEmitterConfig(request.getPluginId());
            if (emitterConfig == null) {
                responseObserver.onError(Status.NOT_FOUND.withDescription("Could not find emitter config for " + request.getPluginId()).asException());
            } else {
                JsonSchema jsonSchema = jsonSchemaGenerator.generateSchema(emitterConfig.getClass());
                builder.setEmitterConfigJsonSchema(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema));
            }
        } catch (JsonProcessingException e) {
            responseObserver.onError(Status.INTERNAL.withDescription("Could not process config - json issues " + request.getPluginId() + " - " + e).asException());
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void savePipeIterator(SavePipeIteratorRequest request, StreamObserver<SavePipeIteratorReply> responseObserver) {
        DefaultPipeIteratorConfig pipeIteratorConfig = newPipeIteratorConfig(request.getPluginId(), request.getPipeIteratorId());
        pipeIteratorConfig.setPipeIteratorId(request.getPipeIteratorId())
                          .setPluginId(request.getPluginId())
                          .setConfigJson(request.getPipeIteratorConfigJson());
        pipeIteratorRepository.save(pipeIteratorConfig.getPipeIteratorId(), pipeIteratorConfig);
        responseObserver.onNext(SavePipeIteratorReply.newBuilder().setPipeIteratorId(request.getPipeIteratorId()).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getPipeIterator(GetPipeIteratorRequest request, StreamObserver<GetPipeIteratorReply> responseObserver) {
        PipeIteratorConfig pipeIteratorConfig = pipeIteratorRepository.findByPipeIteratorId(request.getPipeIteratorId());
        responseObserver.onNext(GetPipeIteratorReply.newBuilder()
                                                    .setPipeIteratorId(request.getPipeIteratorId())
                                                    .setPluginId(pipeIteratorConfig.getPluginId())
                                                    .build());
        responseObserver.onCompleted();
    }

    @Override
    public void listPipeIterators(ListPipeIteratorsRequest request, StreamObserver<ListPipeIteratorsReply> responseObserver) {
        ListPipeIteratorsReply.Builder builder = ListPipeIteratorsReply.newBuilder();
        pipeIteratorRepository.findAll().forEach(pipeIteratorConfig -> builder.addGetPipeIteratorReplies(GetPipeIteratorReply.newBuilder()
                                                                                                                             .setPipeIteratorId(pipeIteratorConfig.getPipeIteratorId())
                                                                                                                             .setPluginId(pipeIteratorConfig.getPluginId())
                                                                                                                             .build()));
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void deletePipeIterator(DeletePipeIteratorRequest request, StreamObserver<DeletePipeIteratorReply> responseObserver) {
        boolean exists = pipeIteratorRepository.findByPipeIteratorId(request.getPipeIteratorId()) != null;
        if (exists) {
            pipeIteratorRepository.deleteByPipeIteratorId(request.getPipeIteratorId());
        }
        responseObserver.onNext(DeletePipeIteratorReply.newBuilder().setSuccess(exists).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getPipeIteratorConfigJsonSchema(GetPipeIteratorConfigJsonSchemaRequest request, StreamObserver<GetPipeIteratorConfigJsonSchemaReply> plainResponseObserver) {
        ServerCallStreamObserver<GetPipeIteratorConfigJsonSchemaReply> responseObserver =
                (ServerCallStreamObserver<GetPipeIteratorConfigJsonSchemaReply>) plainResponseObserver;
        responseObserver.setCompression("gzip");
        GetPipeIteratorConfigJsonSchemaReply.Builder builder = GetPipeIteratorConfigJsonSchemaReply.newBuilder();
        try {
            PipeIteratorConfig pipeIteratorConfig = getPipeIteratorConfig(request.getPluginId());
            if (pipeIteratorConfig == null) {
                responseObserver.onError(Status.NOT_FOUND.withDescription("Could not find pipe iterator config for " + request.getPluginId()).asException());
            } else {
                JsonSchema jsonSchema = jsonSchemaGenerator.generateSchema(pipeIteratorConfig.getClass());
                builder.setPipeIteratorConfigJsonSchema(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema));
            }
        } catch (JsonProcessingException e) {
            responseObserver.onError(Status.INTERNAL.withDescription("Could not process config - json issues " + request.getPluginId() + " - " + e).asException());
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void runPipeJob(RunPipeJobRequest request,
                           StreamObserver<RunPipeJobReply> responseObserver) {
        String jobId = UUID.randomUUID().toString();
        updateJobStatus(jobId, true, false, false);

        executorService.submit(() -> runPipeJobImpl(request, responseObserver, jobId));

        responseObserver.onNext(RunPipeJobReply.newBuilder().setPipeJobId(jobId).build());
        responseObserver.onCompleted();
    }

    private void updateJobStatus(String jobId, boolean isRunning, boolean hasError, boolean isCompleted) {
        jobStatusRepository.save(jobId, JobStatus.builder()
                .jobId(jobId)
                .running(isRunning)
                .hasError(hasError)
                .completed(isCompleted)
                .build());
    }

    private void runPipeJobImpl(RunPipeJobRequest request, StreamObserver<RunPipeJobReply> responseObserver, String jobId) {
        try {
            DefaultPipeIteratorConfig pipeIteratorConfig = pipeIteratorRepository.findByPipeIteratorId(request.getPipeIteratorId());
            PipeIteratorConfig pipeIteratorConfigFromPluginManager = objectMapper.readValue(pipeIteratorConfig.getConfigJson(), getPipeIteratorConfigClassFromPluginManager(pipeIteratorConfig));
            PipeIterator pipeIterator = getPipeIterator(pipeIteratorConfig.getPluginId());
            pipeIterator.init(pipeIteratorConfigFromPluginManager);
            DefaultEmitterConfig emitterConfig = emitterRepository.findByEmitterId(request.getEmitterId());
            EmitterConfig emitterConfigFromPluginManager = objectMapper.readValue(emitterConfig.getConfigJson(), getEmitterConfigClassFromPluginManager(emitterConfig));
            Emitter emitter = getEmitter(emitterConfig.getPluginId());
            emitter.init(emitterConfigFromPluginManager);
            CountDownLatch countDownLatch = new CountDownLatch(1);
            StreamObserver<FetchAndParseRequest> requestStreamObserver = fetchAndParseAsync(new StreamObserver<>() {
                        @Override
                        public void onNext(FetchAndParseReply fetchAndParseReply) {
                            try {
                                List<Map<String, List<Object>>> listOfMetadata = listOfMetadataToListOfMap(fetchAndParseReply);
                                emitter.emit(List.of(EmitOutput.builder()
                                        .fetchKey(fetchAndParseReply.getFetchKey())
                                        .metadata(listOfMetadata)
                                        .build()));
                            } catch (IOException e) {
                                log.error("Error emitting fetch key {}",
                                        fetchAndParseReply.getFetchKey(), e);
                                updateJobStatus(jobId, true, false, false);
                                responseObserver.onError(Status.INTERNAL.withDescription("Could not handle next fetchAndParseReply " + fetchAndParseReply + " - " + ExceptionUtils.getStackTrace(e)).withCause(e).asException());
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            log.error("Error streaming fetch and parse replies", throwable);
                            countDownLatch.countDown();
                        }

                        @Override
                        public void onCompleted() {
                            log.info("Finished streaming fetch and parse replies");
                            countDownLatch.countDown();
                        }
                    });

            while (pipeIterator.hasNext()) {
                List<PipeInput> pipeInputs = pipeIterator.next();

                for (PipeInput pipeInput : pipeInputs) {
                    requestStreamObserver.onNext(FetchAndParseRequest.newBuilder()
                            .setFetcherId(request.getFetcherId())
                            .setFetchKey(pipeInput.getFetchKey())
                            .setFetchMetadataJson(objectMapper.writeValueAsString(pipeInput.getMetadata()))
                            .setAddedMetadataJson("{}")
                            .build());
                }
            }
            requestStreamObserver.onCompleted();
            try {
                if (!countDownLatch.await(request.getJobCompletionTimeoutSeconds(), TimeUnit.SECONDS)) {
                    throw new TikaPipesException("Timed out waiting for job to complete");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            updateJobStatus(jobId, false, false, true);
        } catch (Throwable e) {
            log.error("Exception running pipe job", e);
            updateJobStatus(jobId, false, true, true);
        }
    }

    @NotNull
    private static List<Map<String, List<Object>>> listOfMetadataToListOfMap(FetchAndParseReply fetchAndParseReply) {
        List<Map<String, List<Object>>> listOfMetadata = new ArrayList<>();
        for (Metadata metadata : fetchAndParseReply.getMetadataList()) {
            Map<String, List<Object>> metadataMap = new HashMap<>();
            for (String key : metadata.getFieldsMap().keySet()) {
                ValueList value = metadata.getFieldsMap().get(key);
                List<Value> valuesList = value.getValuesList();
                metadataMap.put(key, new ArrayList<>());
                for (Value val : valuesList) {
                    if (val.hasStringValue()) {
                        metadataMap.get(key).add(val.getStringValue());
                    } else if (val.hasIntValue()) {
                        metadataMap.get(key).add(val.getIntValue());
                    } else if (val.hasDoubleValue()) {
                        metadataMap.get(key).add(val.getDoubleValue());
                    } else if (val.hasBoolValue()) {
                        metadataMap.get(key).add(val.getBoolValue());
                    }
                }
            }
            listOfMetadata.add(metadataMap);
        }
        return listOfMetadata;
    }

    @Override
    public void getPipeJob(GetPipeJobRequest request,
                           StreamObserver<GetPipeJobReply> plainResponseObserver) {
        ServerCallStreamObserver<GetPipeJobReply> responseObserver =
                (ServerCallStreamObserver<GetPipeJobReply>) plainResponseObserver;
        responseObserver.setCompression("gzip");
        JobStatus jobStatus = jobStatusRepository.findByJobId(request.getPipeJobId());
        if (jobStatus == null) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Could not find pipe job id " + request.getPipeJobId()).asException());
            return;
        }

        GetPipeJobReply.Builder replyBuilder =
                GetPipeJobReply.newBuilder().setPipeJobId(jobStatus.getJobId())
                        .setIsRunning(jobStatus.getRunning())
                        .setIsCompleted(jobStatus.getCompleted())
                        .setHasError(jobStatus.getHasError());

        responseObserver.onNext(replyBuilder.build());
        responseObserver.onCompleted();
    }

    private static class TikaRunnerThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(@NotNull Runnable runnable) {
            String namePrefix = "pipes-runner-";
            return new Thread(runnable, namePrefix + threadNumber.getAndIncrement());
        }
    }
}
