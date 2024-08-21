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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import com.google.rpc.Status;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import org.apache.tika.DeleteFetcherReply;
import org.apache.tika.DeleteFetcherRequest;
import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.GetFetcherConfigJsonSchemaReply;
import org.apache.tika.GetFetcherConfigJsonSchemaRequest;
import org.apache.tika.GetFetcherReply;
import org.apache.tika.GetFetcherRequest;
import org.apache.tika.ListFetcherPluginsReply;
import org.apache.tika.ListFetcherPluginsRequest;
import org.apache.tika.ListFetchersReply;
import org.apache.tika.ListFetchersRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.PipesClient;
import org.apache.tika.pipes.PipesConfig;
import org.apache.tika.pipes.PipesResult;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.config.FetcherConfig;
import org.apache.tika.pipes.fetcher.config.FetcherConfigContainer;
import org.apache.tika.pipes.grpc.exception.TikaGrpcException;

class TikaGrpcServerImpl extends TikaGrpc.TikaImplBase {
    private static final Logger LOG = LoggerFactory.getLogger(TikaGrpcServerImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static {
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    public static final JsonSchemaGenerator JSON_SCHEMA_GENERATOR = new JsonSchemaGenerator(OBJECT_MAPPER);

    private final PluginManager pluginManager;

    /**
     * FetcherID is key, The pair is the Fetcher object and the Metadata
     */
    PipesConfig pipesConfig;
    PipesClient pipesClient;
    ExpiringFetcherStore expiringFetcherStore;

    String tikaConfigPath;

    TikaGrpcServerImpl(String tikaConfigPath, PluginManager pluginManager) throws TikaConfigException, IOException,
            ParserConfigurationException, TransformerException, SAXException {
        File tikaConfigFile = new File(tikaConfigPath);
        if (!tikaConfigFile.canWrite()) {
            File tmpTikaConfigFile = File.createTempFile("configCopy", tikaConfigFile.getName());
            tmpTikaConfigFile.deleteOnExit();
            LOG.info("Tika config file {} is read-only. Making a temporary copy to {}", tikaConfigFile, tmpTikaConfigFile);
            String tikaConfigFileContents = FileUtils.readFileToString(tikaConfigFile, StandardCharsets.UTF_8);
            FileUtils.writeStringToFile(tmpTikaConfigFile, tikaConfigFileContents, StandardCharsets.UTF_8);
            tikaConfigFile = tmpTikaConfigFile;
            tikaConfigPath = tikaConfigFile.getAbsolutePath();
        }
        pipesConfig = PipesConfig.load(tikaConfigFile.toPath());
        pipesClient = new PipesClient(pipesConfig);

        expiringFetcherStore = new ExpiringFetcherStore(pipesConfig.getStaleFetcherTimeoutSeconds(),
                pipesConfig.getStaleFetcherDelaySeconds());

        this.tikaConfigPath = tikaConfigPath;
        updateTikaConfig();

        this.pluginManager = pluginManager;
    }

    private void updateTikaConfig()
            throws ParserConfigurationException, IOException, SAXException, TransformerException {
        Document tikaConfigDoc =
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(tikaConfigPath);

        Element fetchersElement = (Element) tikaConfigDoc.getElementsByTagName("fetchers").item(0);
        if (fetchersElement == null) {
            fetchersElement = tikaConfigDoc.createElement("fetchers");
            tikaConfigDoc.getDocumentElement().appendChild(fetchersElement);
        }
        for (int i = 0; i < fetchersElement.getChildNodes().getLength(); ++i) {
            fetchersElement.removeChild(fetchersElement.getChildNodes().item(i));
        }
        for (var fetcherConfigEntry : expiringFetcherStore.getFetcherConfigs().entrySet()) {
            Fetcher fetcherObject = getFetcher(fetcherConfigEntry.getValue().getPluginId());
            Map<String, Object> fetcherConfigParams = OBJECT_MAPPER.convertValue(
                    expiringFetcherStore.getFetcherConfigs().get(fetcherConfigEntry.getKey()),
                    new TypeReference<>() {
                    });
            Element fetcher = tikaConfigDoc.createElement("fetcher");
            fetcher.setAttribute("class", fetcherObject.getClass().getName());
            Element pluginIdElm = tikaConfigDoc.createElement("pluginId");
            pluginIdElm.setTextContent(fetcherObject.getPluginId());
            fetcher.appendChild(pluginIdElm);
            populateFetcherConfigs(fetcherConfigParams, tikaConfigDoc, fetcher);
            fetchersElement.appendChild(fetcher);
        }
        DOMSource source = new DOMSource(tikaConfigDoc);
        FileWriter writer = new FileWriter(tikaConfigPath, StandardCharsets.UTF_8);
        StreamResult result = new StreamResult(writer);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.transform(source, result);
    }

    private void populateFetcherConfigs(Map<String, Object> fetcherConfigParams,
                                        Document tikaConfigDoc, Element fetcher) {
        for (var configParam : fetcherConfigParams.entrySet()) {
            Element configElm = tikaConfigDoc.createElement(configParam.getKey());
            fetcher.appendChild(configElm);
            if (configParam.getValue() instanceof List) {
                List configParamVal = (List) configParam.getValue();
                String singularName = configParam.getKey().substring(0, configParam.getKey().length() - 1);
                for (Object configParamObj : configParamVal) {
                    Element childElement = tikaConfigDoc.createElement(singularName);
                    childElement.setTextContent(Objects.toString(configParamObj));
                    configElm.appendChild(childElement);
                }
            } else {
                configElm.setTextContent(Objects.toString(configParam.getValue()));
            }
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
        FetcherConfig fetcherConfig =
                expiringFetcherStore.getFetcherAndLogAccess(request.getFetcherId());
        if (fetcherConfig == null) {
            throw new RuntimeException(
                    "Could not find fetcher with name " + request.getFetcherId());
        }
        Metadata tikaMetadata = new Metadata();
        try {
            ParseContext parseContext = new ParseContext();
            String additionalFetchConfigJson = request.getAdditionalFetchConfigJson();
            if (StringUtils.isNotBlank(additionalFetchConfigJson)) {
                // The fetch and parse has the option to specify additional configuration
                FetcherConfig abstractFetcherConfig = expiringFetcherStore
                        .getFetcherConfigs()
                        .get(request.getFetcherId());
                parseContext.set(FetcherConfigContainer.class, new FetcherConfigContainer()
                        .setConfigClassName(abstractFetcherConfig
                                .getClass().getName())
                        .setJson(additionalFetchConfigJson));
            }
            PipesResult pipesResult = pipesClient.process(new FetchEmitTuple(request.getFetchKey(),
                    new FetchKey(request.getFetcherId(), request.getFetchKey()), new EmitKey(), tikaMetadata, parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
            FetchAndParseReply.Builder fetchReplyBuilder =
                    FetchAndParseReply.newBuilder()
                                      .setFetchKey(request.getFetchKey())
                            .setStatus(pipesResult.getStatus().name());
            if (pipesResult.getStatus().equals(PipesResult.STATUS.FETCH_EXCEPTION)) {
                fetchReplyBuilder.setErrorMessage(pipesResult.getMessage());
            }
            if (pipesResult.getEmitData() != null && pipesResult.getEmitData().getMetadataList() != null) {
                for (Metadata metadata : pipesResult.getEmitData().getMetadataList()) {
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
            Map<String, Object> fetcherConfigMap = OBJECT_MAPPER.readValue(request.getFetcherConfigJson(), new TypeReference<>() {});
            Map<String, Param> tikaParamsMap = createTikaParamMap(fetcherConfigMap);
            saveFetcher(request.getFetcherId(), request.getPluginId(), fetcherConfigMap, tikaParamsMap);
            updateTikaConfig();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    private void saveFetcher(String fetcherId, String pluginId, Map<String, Object> paramsMap, Map<String, Param> tikaParamsMap) {
        try {
            if (paramsMap == null) {
                paramsMap = new LinkedHashMap<>();
            }
            Fetcher fetcher = getFetcher(pluginId);
            Class<? extends Fetcher> fetcherClass = fetcher.getClass();
            String configClassName =
                    fetcherClass.getPackageName() + ".config." + fetcherClass.getSimpleName() +
                            "Config";

            Class<? extends FetcherConfig> configClass =
                    (Class<? extends FetcherConfig>) Class.forName(configClassName, true, fetcher.getClass().getClassLoader());
            FetcherConfig configObject = OBJECT_MAPPER.convertValue(paramsMap, configClass);
            if (Initializable.class.isAssignableFrom(fetcherClass)) {
                Initializable initializable = (Initializable) fetcher;
                initializable.initialize(tikaParamsMap);
            }
            if (expiringFetcherStore.deleteFetcher(fetcherId)) {
                LOG.info("Updating fetcher {}", fetcherId);
            } else {
                LOG.info("Creating new fetcher {}", fetcherId);
            }
            expiringFetcherStore.createFetcher(fetcherId, configObject);
        } catch (ClassNotFoundException | TikaConfigException e) {
            throw new TikaGrpcException("Could not create fetcher", e);
        }
    }

    private static Map<String, Param> createTikaParamMap(Map<String, Object> fetcherConfigMap) {
        Map<String, Param> tikaParamsMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : fetcherConfigMap.entrySet()) {
            if (entry.getValue() != null) {
                tikaParamsMap.put(entry.getKey(), new Param<>(entry.getKey(), entry.getValue()));
            }
        }
        return tikaParamsMap;
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
        FetcherConfig fetcherConfig =
                expiringFetcherStore.getFetcherConfigs().get(request.getFetcherId());
        if (fetcherConfig == null) {
            responseObserver.onError(StatusProto.toStatusException(notFoundStatus(request.getFetcherId())));
            return;
        }
        getFetcherReply.setFetcherId(request.getFetcherId());
        getFetcherReply.setPluginId(fetcherConfig.getPluginId());
        Map<String, Object> paramMap = OBJECT_MAPPER.convertValue(fetcherConfig, new TypeReference<>() {});
        paramMap.forEach(
                (k, v) -> getFetcherReply.putParams(Objects.toString(k), Objects.toString(v)));
        responseObserver.onNext(getFetcherReply.build());
        responseObserver.onCompleted();
    }

    @Override
    public void listFetchers(ListFetchersRequest request,
                             StreamObserver<ListFetchersReply> responseObserver) {
        ListFetchersReply.Builder listFetchersReplyBuilder = ListFetchersReply.newBuilder();
        for (Map.Entry<String, FetcherConfig> fetcherConfig : expiringFetcherStore.getFetcherConfigs()
                                                                                  .entrySet()) {
            GetFetcherReply.Builder replyBuilder = saveFetcherReply(fetcherConfig);
            listFetchersReplyBuilder.addGetFetcherReplies(replyBuilder.build());
        }
        responseObserver.onNext(listFetchersReplyBuilder.build());
        responseObserver.onCompleted();
    }

    private GetFetcherReply.Builder saveFetcherReply(
            Map.Entry<String, FetcherConfig> fetcherConfigEntry) {
        FetcherConfig fetcherConfig = fetcherConfigEntry.getValue();
        GetFetcherReply.Builder replyBuilder =
                GetFetcherReply.newBuilder().setPluginId(fetcherConfig.getPluginId())
                        .setFetcherId(fetcherConfig.getFetcherId());
        loadParamsIntoReply(fetcherConfig, replyBuilder);
        return replyBuilder;
    }

    private static void loadParamsIntoReply(FetcherConfig fetcherConfig,
                                            GetFetcherReply.Builder replyBuilder) {
        Map<String, Object> paramMap =
                OBJECT_MAPPER.convertValue(fetcherConfig, new TypeReference<>() {
                });
        if (paramMap != null) {
            paramMap.forEach(
                    (k, v) -> replyBuilder.putParams(Objects.toString(k), Objects.toString(v)));
        }
    }

    @Override
    public void deleteFetcher(DeleteFetcherRequest request,
                              StreamObserver<DeleteFetcherReply> responseObserver) {
        boolean successfulDelete = deleteFetcher(request.getFetcherId());
        if (successfulDelete) {
            try {
                updateTikaConfig();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        responseObserver.onNext(DeleteFetcherReply.newBuilder().setSuccess(successfulDelete).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getFetcherConfigJsonSchema(GetFetcherConfigJsonSchemaRequest request, StreamObserver<GetFetcherConfigJsonSchemaReply> responseObserver) {
        GetFetcherConfigJsonSchemaReply.Builder builder = GetFetcherConfigJsonSchemaReply.newBuilder();
        try {
            Fetcher fetcher = getFetcher(request.getPluginId());
            JsonSchema jsonSchema = JSON_SCHEMA_GENERATOR.generateSchema(fetcher.getClass());
            builder.setFetcherConfigJsonSchema(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not create json schema for fetcher with plugin ID " + request.getPluginId(), e);
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    private Fetcher getFetcher(String pluginId) {
        return pluginManager.getExtensions(Fetcher.class, pluginId)
                            .stream()
                            .findFirst()
                            .orElseThrow();
    }

    @Override
    public void listFetcherPlugins(ListFetcherPluginsRequest request, StreamObserver<ListFetcherPluginsReply> responseObserver) {
        for (Fetcher fetcher : pluginManager.getExtensions(Fetcher.class)) {
            responseObserver.onNext(ListFetcherPluginsReply.newBuilder()
                                                           .setFetcherPluginId(fetcher.getPluginId())
                                                           .build());
        }

    }

    private boolean deleteFetcher(String fetcherName) {
        return expiringFetcherStore.deleteFetcher(fetcherName);
    }
}
