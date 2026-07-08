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
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.apache.tika.DeleteFetcherReply;
import org.apache.tika.DeleteFetcherRequest;
import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.GetFetcherConfigJsonSchemaReply;
import org.apache.tika.GetFetcherConfigJsonSchemaRequest;
import org.apache.tika.GetFetcherReply;
import org.apache.tika.GetFetcherRequest;
import org.apache.tika.ListFetchersReply;
import org.apache.tika.ListFetchersRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.PipesClient;
import org.apache.tika.pipes.PipesConfig;
import org.apache.tika.pipes.PipesResult;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.config.AbstractConfig;
import org.apache.tika.pipes.fetcher.config.FetcherConfigContainer;
import org.apache.tika.utils.XMLReaderUtils;

class TikaGrpcServerImpl extends TikaGrpc.TikaImplBase {
    private static final Logger LOG = LoggerFactory.getLogger(TikaGrpcServerImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static {
        //TODO with Jackson 3.0 we'll have to use MapperBuilder
        OBJECT_MAPPER.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }
    public static final JsonSchemaGenerator JSON_SCHEMA_GENERATOR = new JsonSchemaGenerator(OBJECT_MAPPER);

    /**
     * FetcherID is key, The pair is the Fetcher object and the Metadata
     */
    PipesConfig pipesConfig;
    PipesClient pipesClient;
    ExpiringFetcherStore expiringFetcherStore;

    String tikaConfigPath;

    //deny-by-default grpc capabilities, read from the <grpc> section of the tika-config
    private final TikaGrpcConfig tikaGrpcConfig;

    TikaGrpcServerImpl(String tikaConfigPath)
            throws TikaConfigException, IOException, ParserConfigurationException,
            TransformerException, SAXException {
        File tikaConfigFile = new File(tikaConfigPath);
        if (!tikaConfigFile.canWrite()) {
            File tmpTikaConfigFile = Files.createTempFile("configCopy", tikaConfigFile.getName()).toFile();
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
        this.tikaGrpcConfig = TikaGrpcConfig.load(Paths.get(tikaConfigPath));
        //load any fetchers declared in the <fetchers> section into the store before we rewrite the
        //config below; otherwise updateTikaConfig() would wipe them. These are the trusted,
        //operator-declared fetchers and are loaded regardless of allowComponentModifications.
        loadFetchersFromConfig();
        try {
            updateTikaConfig();
        } catch (TikaException e) {
            throw new TikaConfigException("Problem updating tikaConfig", e);
        }
    }

    private void updateTikaConfig() throws ParserConfigurationException, IOException, SAXException, TransformerException, TikaException {
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
        try (Writer writer = new FileWriter(tikaConfigPath, StandardCharsets.UTF_8))
        {
            StreamResult result = new StreamResult(writer);

            TransformerFactory transformerFactory = XMLReaderUtils.getTransformerFactory();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.transform(source, result);
        }
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

    /**
     * Loads fetchers declared in the {@code <fetchers>} section of the tika-config into the store
     * at startup. These are trusted, operator-declared fetchers (the secure way to provide
     * fetchers when {@code allowComponentModifications} is false) and are marked permanent so the
     * stale-fetcher job never expires them. The reverse of {@link #populateFetcherConfigs}.
     */
    private void loadFetchersFromConfig()
            throws ParserConfigurationException, IOException, SAXException, TikaConfigException {
        Document tikaConfigDoc =
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(tikaConfigPath);
        NodeList fetchersElements = tikaConfigDoc.getElementsByTagName("fetchers");
        if (fetchersElements.getLength() == 0) {
            return;
        }
        Element fetchersElement = (Element) fetchersElements.item(0);
        NodeList fetcherNodes = fetchersElement.getElementsByTagName("fetcher");
        for (int i = 0; i < fetcherNodes.getLength(); i++) {
            Element fetcherEl = (Element) fetcherNodes.item(i);
            String fetcherClass = fetcherEl.getAttribute("class");
            if (StringUtils.isBlank(fetcherClass)) {
                throw new TikaConfigException("<fetcher> in <fetchers> must have a 'class' attribute");
            }
            String name = null;
            Map<String, Object> params = new LinkedHashMap<>();
            NodeList children = fetcherEl.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                String localName = child.getNodeName();
                if ("name".equals(localName)) {
                    name = child.getTextContent();
                } else {
                    params.put(localName, readParamValue((Element) child));
                }
            }
            if (StringUtils.isBlank(name)) {
                throw new TikaConfigException("<fetcher> in <fetchers> must have a <name>");
            }
            saveFetcher(name, fetcherClass, params, createTikaParamMap(params), true);
            LOG.info("Loaded fetcher '{}' ({}) from config", name, fetcherClass);
        }
    }

    /**
     * Reads a single config element written by {@link #populateFetcherConfigs}: an element with
     * child elements is read back as a {@link List} of their text contents; otherwise the element's
     * text content is returned.
     */
    private static Object readParamValue(Element element) {
        List<String> list = new ArrayList<>();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                list.add(child.getTextContent());
            }
        }
        return list.isEmpty() ? element.getTextContent() : list;
    }

    /**
     * @return true (and sends {@code PERMISSION_DENIED} to the client) if this request carries
     * per-request configuration that is not allowed. Note: we use {@code onError} rather than
     * throwing so the client receives {@code PERMISSION_DENIED} rather than {@code UNKNOWN}.
     */
    private boolean denyPerRequestConfig(FetchAndParseRequest request,
                                         StreamObserver<?> responseObserver) {
        if (StringUtils.isBlank(request.getAdditionalFetchConfigJson())
                || tikaGrpcConfig.isAllowPerRequestConfig()) {
            return false;
        }
        responseObserver.onError(io.grpc.Status.PERMISSION_DENIED
                .withDescription("Per-request configuration (additional_fetch_config_json) is " +
                        "disabled. Set 'allowPerRequestConfig' to true in the <grpc> section of " +
                        "the tika-config to enable it.")
                .asRuntimeException());
        return true;
    }

    /**
     * @return true (and sends {@code PERMISSION_DENIED} to the client) if runtime component
     * modifications (SaveFetcher/DeleteFetcher) are not allowed.
     */
    private boolean denyComponentModifications(StreamObserver<?> responseObserver) {
        if (tikaGrpcConfig.isAllowComponentModifications()) {
            return false;
        }
        responseObserver.onError(io.grpc.Status.PERMISSION_DENIED
                .withDescription("Runtime component modifications (SaveFetcher/DeleteFetcher) are " +
                        "disabled. Set 'allowComponentModifications' to true in the <grpc> section " +
                        "of the tika-config to enable them. Fetchers may instead be declared in the " +
                        "<fetchers> section of the tika-config.")
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
            ParseContext parseContext = new ParseContext();
            String additionalFetchConfigJson = request.getAdditionalFetchConfigJson();
            if (StringUtils.isNotBlank(additionalFetchConfigJson)) {
                // The fetch and parse has the option to specify additional configuration
                AbstractConfig abstractConfig = expiringFetcherStore
                        .getFetcherConfigs()
                        .get(fetcher.getName());
                parseContext.set(FetcherConfigContainer.class, new FetcherConfigContainer()
                        .setConfigClassName(abstractConfig
                                .getClass().getName())
                        .setJson(additionalFetchConfigJson));
            }
            PipesResult pipesResult = pipesClient.process(new FetchEmitTuple(request.getFetchKey(),
                    new FetchKey(fetcher.getName(), request.getFetchKey()), new EmitKey(), tikaMetadata, parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
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
        if (denyComponentModifications(responseObserver)) {
            return;
        }
        SaveFetcherReply reply =
                SaveFetcherReply.newBuilder().setFetcherId(request.getFetcherId()).build();
        try {
            Map<String, Object> fetcherConfigMap = OBJECT_MAPPER.readValue(request.getFetcherConfigJson(), new TypeReference<>() {});
            Map<String, Param> tikaParamsMap = createTikaParamMap(fetcherConfigMap);
            saveFetcher(request.getFetcherId(), request.getFetcherClass(), fetcherConfigMap, tikaParamsMap, false);
            updateTikaConfig();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    private void saveFetcher(String name, String fetcherClassName, Map<String, Object> paramsMap, Map<String, Param> tikaParamsMap, boolean permanent) {
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
            expiringFetcherStore.createFetcher(abstractFetcher, configObject, permanent);
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
        if (denyComponentModifications(responseObserver)) {
            return;
        }
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
            JsonSchema jsonSchema = JSON_SCHEMA_GENERATOR.generateSchema(Class.forName(request.getFetcherClass()));
            builder.setFetcherConfigJsonSchema(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema));
        } catch (ClassNotFoundException | JsonProcessingException e) {
            throw new RuntimeException("Could not create json schema for " + request.getFetcherClass(), e);
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    private boolean deleteFetcher(String fetcherName) {
        return expiringFetcherStore.deleteFetcher(fetcherName);
    }
}
