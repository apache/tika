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

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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
import com.google.rpc.Status;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import org.apache.tika.DeleteFetcherReply;
import org.apache.tika.DeleteFetcherRequest;
import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.GetFetcherReply;
import org.apache.tika.GetFetcherRequest;
import org.apache.tika.ListFetchersReply;
import org.apache.tika.ListFetchersRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.Param;
import org.apache.tika.config.TikaConfigSerializer;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.PipesClient;
import org.apache.tika.pipes.PipesConfig;
import org.apache.tika.pipes.PipesResult;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.config.AbstractConfig;

class TikaGrpcServerImpl extends TikaGrpc.TikaImplBase {
    private static final Logger LOG = LoggerFactory.getLogger(TikaConfigSerializer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static {
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * FetcherID is key, The pair is the Fetcher object and the Metadata
     */
    PipesConfig pipesConfig;
    PipesClient pipesClient;
    ExpiringFetcherStore expiringFetcherStore;

    String tikaConfigPath;

    TikaGrpcServerImpl(String tikaConfigPath)
            throws TikaConfigException, IOException, ParserConfigurationException,
            TransformerException, SAXException {
        pipesConfig = PipesConfig.load(Paths.get(tikaConfigPath));
        pipesClient = new PipesClient(pipesConfig);

        expiringFetcherStore = new ExpiringFetcherStore(pipesConfig.getStaleFetcherTimeoutSeconds(),
                pipesConfig.getStaleFetcherDelaySeconds());
        this.tikaConfigPath = tikaConfigPath;
        updateTikaConfig();
    }

    private void updateTikaConfig()
            throws ParserConfigurationException, IOException, SAXException, TransformerException {
        Document tikaConfigDoc =
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(tikaConfigPath);

        Element fetchersElement = (Element) tikaConfigDoc.getElementsByTagName("fetchers").item(0);
        for (int i = 0; i < fetchersElement.getChildNodes().getLength(); ++i) {
            fetchersElement.removeChild(fetchersElement.getChildNodes().item(i));
        }
        for (var fetcherEntry : expiringFetcherStore.getFetchers().entrySet()) {
            AbstractFetcher fetcherObject = fetcherEntry.getValue();
            Map<String, Object> fetcherConfigParams = OBJECT_MAPPER.convertValue(
                    expiringFetcherStore.getFetcherConfigs().get(fetcherEntry.getKey()),
                    new TypeReference<>() {
                    });
            Element fetcher = tikaConfigDoc.createElement("fetcher");
            fetcher.setAttribute("class", fetcherEntry.getValue().getClass().getName());
            Element fetcherName = tikaConfigDoc.createElement("name");
            fetcherName.setTextContent(fetcherObject.getName());
            fetcher.appendChild(fetcherName);
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
        AbstractFetcher fetcher =
                expiringFetcherStore.getFetcherAndLogAccess(request.getFetcherId());
        if (fetcher == null) {
            throw new RuntimeException(
                    "Could not find fetcher with name " + request.getFetcherId());
        }
        Metadata tikaMetadata = new Metadata();
        try {
            String metadataJson = request.getMetadataJson();
            loadMetadata(metadataJson, tikaMetadata);
            PipesResult pipesResult = pipesClient.process(new FetchEmitTuple(request.getFetchKey(),
                    new FetchKey(fetcher.getName(), request.getFetchKey()), new EmitKey(), tikaMetadata,
                    HandlerConfig.DEFAULT_HANDLER_CONFIG, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
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

    private static void loadMetadata(String metadataJson, Metadata tikaMetadata) throws JsonProcessingException {
        Map<String, Object> metadataJsonObject = new HashMap<>();
        if (!StringUtils.isBlank(metadataJson)) {
            try {
                metadataJsonObject = OBJECT_MAPPER.readValue(metadataJson, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                metadataJsonObject = new HashMap<>();
            }
        }
        for (Map.Entry<String, Object> entry : metadataJsonObject.entrySet()) {
            if (entry.getValue() instanceof List) {
                List<Object> list = (List<Object>) entry.getValue();
                tikaMetadata.set(Property.externalText(entry.getKey()), list.stream()
                                                                            .map(String::valueOf)
                                                                            .collect(Collectors.toList())
                                                                            .toArray(new String[] {}));
            } else if (entry.getValue() instanceof String) {
                tikaMetadata.set(Property.externalText(entry.getKey()), (String) entry.getValue());
            } else if (entry.getValue() instanceof Integer) {
                tikaMetadata.set(Property.externalText(entry.getKey()), (Integer) entry.getValue());
            } else if (entry.getValue() instanceof Double) {
                tikaMetadata.set(Property.externalText(entry.getKey()), (Double) entry.getValue());
            } else if (entry.getValue() instanceof Float) {
                tikaMetadata.set(Property.externalText(entry.getKey()), (Float) entry.getValue());
            } else if (entry.getValue() instanceof Boolean) {
                tikaMetadata.set(Property.externalText(entry.getKey()), (Boolean) entry.getValue());
            }
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
            saveFetcher(request.getFetcherId(), request.getFetcherClass(), fetcherConfigMap, tikaParamsMap);
            updateTikaConfig();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    private void saveFetcher(String name, String fetcherClassName, Map<String, Object> paramsMap, Map<String, Param> tikaParamsMap) {
        try {
            if (paramsMap == null) {
                paramsMap = new LinkedHashMap<>();
            }
            Class<? extends AbstractFetcher> fetcherClass =
                    (Class<? extends AbstractFetcher>) Class.forName(fetcherClassName);
            String configClassName =
                    fetcherClass.getPackageName() + ".config." + fetcherClass.getSimpleName() +
                            "Config";
            Class<? extends AbstractConfig> configClass =
                    (Class<? extends AbstractConfig>) Class.forName(configClassName);
            AbstractConfig configObject = OBJECT_MAPPER.convertValue(paramsMap, configClass);
            AbstractFetcher abstractFetcher =
                    fetcherClass.getDeclaredConstructor(configClass).newInstance(configObject);
            abstractFetcher.setName(name);
            if (Initializable.class.isAssignableFrom(fetcherClass)) {
                Initializable initializable = (Initializable) abstractFetcher;
                initializable.initialize(tikaParamsMap);
            }
            if (expiringFetcherStore.deleteFetcher(name)) {
                LOG.info("Updating fetcher {}", name);
            } else {
                LOG.info("Creating new fetcher {}", name);
            }
            expiringFetcherStore.createFetcher(abstractFetcher, configObject);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                 InvocationTargetException | NoSuchMethodException | TikaConfigException e) {
            throw new RuntimeException(e);
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
        AbstractConfig abstractConfig =
                expiringFetcherStore.getFetcherConfigs().get(request.getFetcherId());
        AbstractFetcher abstractFetcher = expiringFetcherStore.getFetchers().get(request.getFetcherId());
        if (abstractFetcher == null || abstractConfig == null) {
            responseObserver.onError(StatusProto.toStatusException(notFoundStatus(request.getFetcherId())));
            return;
        }
        getFetcherReply.setFetcherId(request.getFetcherId());
        getFetcherReply.setFetcherClass(abstractFetcher.getClass().getName());
        Map<String, Object> paramMap = OBJECT_MAPPER.convertValue(abstractConfig, new TypeReference<>() {});
        paramMap.forEach(
                (k, v) -> getFetcherReply.putParams(Objects.toString(k), Objects.toString(v)));
        responseObserver.onNext(getFetcherReply.build());
        responseObserver.onCompleted();
    }

    @Override
    public void listFetchers(ListFetchersRequest request,
                             StreamObserver<ListFetchersReply> responseObserver) {
        ListFetchersReply.Builder listFetchersReplyBuilder = ListFetchersReply.newBuilder();
        for (Map.Entry<String, AbstractConfig> fetcherConfig : expiringFetcherStore.getFetcherConfigs()
                .entrySet()) {
            GetFetcherReply.Builder replyBuilder = saveFetcherReply(fetcherConfig);
            listFetchersReplyBuilder.addGetFetcherReplies(replyBuilder.build());
        }
        responseObserver.onNext(listFetchersReplyBuilder.build());
        responseObserver.onCompleted();
    }

    private GetFetcherReply.Builder saveFetcherReply(
            Map.Entry<String, AbstractConfig> fetcherConfig) {
        AbstractFetcher abstractFetcher =
                expiringFetcherStore.getFetchers().get(fetcherConfig.getKey());
        AbstractConfig abstractConfig =
                expiringFetcherStore.getFetcherConfigs().get(fetcherConfig.getKey());
        GetFetcherReply.Builder replyBuilder =
                GetFetcherReply.newBuilder().setFetcherClass(abstractFetcher.getClass().getName())
                        .setFetcherId(abstractFetcher.getName());
        loadParamsIntoReply(abstractConfig, replyBuilder);
        return replyBuilder;
    }

    private static void loadParamsIntoReply(AbstractConfig abstractConfig,
                                            GetFetcherReply.Builder replyBuilder) {
        Map<String, Object> paramMap =
                OBJECT_MAPPER.convertValue(abstractConfig, new TypeReference<>() {
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

    private boolean deleteFetcher(String fetcherName) {
        return expiringFetcherStore.deleteFetcher(fetcherName);
    }
}
