package org.apache.tika.pipes.grpc;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import org.apache.tika.CreateFetcherReply;
import org.apache.tika.CreateFetcherRequest;
import org.apache.tika.DeleteFetcherReply;
import org.apache.tika.DeleteFetcherRequest;
import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.GetFetcherReply;
import org.apache.tika.GetFetcherRequest;
import org.apache.tika.ListFetchersReply;
import org.apache.tika.ListFetchersRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.UpdateFetcherReply;
import org.apache.tika.UpdateFetcherRequest;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.PipesClient;
import org.apache.tika.pipes.PipesConfig;
import org.apache.tika.pipes.PipesResult;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.config.AbstractConfig;

class TikaGrpcServerImpl extends TikaGrpc.TikaImplBase {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /**
     * FetcherID is key, The pair is the Fetcher object and the Metadata
     */
    Map<String, AbstractFetcher> fetchers = Collections.synchronizedMap(new HashMap<>());
    Map<String, AbstractConfig> fetcherConfigs = Collections.synchronizedMap(new HashMap<>());
    PipesConfig pipesConfig = PipesConfig.load(Paths.get("tika-config.xml"));
    PipesClient pipesClient = new PipesClient(pipesConfig);

    String tikaConfigPath;

    TikaGrpcServerImpl(String tikaConfigPath)
            throws TikaConfigException, IOException, ParserConfigurationException,
            TransformerException, SAXException {
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
        for (var fetcherEntry : fetchers.entrySet()) {
            AbstractFetcher fetcherObject = fetcherEntry.getValue();
            Map<String, Object> fetcherConfigParams =
                    OBJECT_MAPPER.convertValue(fetcherConfigs.get(fetcherEntry.getKey()), new TypeReference<>() {
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
            configElm.setTextContent(Objects.toString(configParam.getValue()));
        }
    }

    @SuppressWarnings("raw")
    @Override
    public void createFetcher(CreateFetcherRequest request,
                              StreamObserver<CreateFetcherReply> responseObserver) {
        CreateFetcherReply reply =
                CreateFetcherReply.newBuilder().setMessage(request.getName()).build();
        Map<String, Param> tikaParamsMap = createTikaParamMap(request.getParamsMap());
        try {
            createFetcher(request.getName(), request.getFetcherClass(), request.getParamsMap(),
                    tikaParamsMap);
            updateTikaConfig();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    private void createFetcher(String name, String fetcherClassName, Map<String, String> paramsMap,
                               Map<String, Param> tikaParamsMap) {
        try {
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
            fetchers.put(name, abstractFetcher);
            fetcherConfigs.put(name, configObject);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (TikaConfigException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Param> createTikaParamMap(Map<String, String> paramsMap) {
        Map<String, Param> tikaParamsMap = new HashMap<>();
        for (Map.Entry<String, String> entry : paramsMap.entrySet()) {
            tikaParamsMap.put(entry.getKey(), new Param<>(entry.getKey(), entry.getValue()));
        }
        return tikaParamsMap;
    }

    @Override
    public void fetchAndParse(FetchAndParseRequest request,
                              StreamObserver<FetchAndParseReply> responseObserver) {
        AbstractFetcher fetcher = fetchers.get(request.getFetcherName());
        if (fetcher == null) {
            throw new RuntimeException(
                    "Could not find fetcher with name " + request.getFetcherName());
        }
        Metadata tikaMetadata = new Metadata();
        for (Map.Entry<String, String> entry : request.getMetadataMap().entrySet()) {
            tikaMetadata.add(entry.getKey(), entry.getValue());
        }
        try {
            PipesResult pipesResult = pipesClient.process(new FetchEmitTuple(request.getFetchKey(),
                    new FetchKey(fetcher.getName(), request.getFetchKey()), new EmitKey(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
            for (Metadata metadata : pipesResult.getEmitData().getMetadataList()) {
                FetchAndParseReply.Builder fetchReplyBuilder =
                        FetchAndParseReply.newBuilder().setFetchKey(request.getFetchKey());
                for (String name : metadata.names()) {
                    String value = metadata.get(name);
                    if (value != null) {
                        fetchReplyBuilder.putFields(name, value);
                    }
                }
                responseObserver.onNext(fetchReplyBuilder.build());
            }
            responseObserver.onCompleted();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void updateFetcher(UpdateFetcherRequest request,
                              StreamObserver<UpdateFetcherReply> responseObserver) {
        UpdateFetcherReply reply =
                UpdateFetcherReply.newBuilder().setMessage(request.getName()).build();
        Map<String, Param> tikaParamsMap = createTikaParamMap(request.getParamsMap());
        try {
            deleteFetcher(request.getName());
            createFetcher(request.getName(), request.getFetcherClass(), request.getParamsMap(),
                    tikaParamsMap);
            updateTikaConfig();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void getFetcher(GetFetcherRequest request,
                           StreamObserver<GetFetcherReply> responseObserver) {
        GetFetcherReply.Builder getFetcherReply = GetFetcherReply.newBuilder();
        AbstractConfig abstractConfig = fetcherConfigs.get(request.getName());
        Map<String, Object> paramMap =
                OBJECT_MAPPER.convertValue(abstractConfig, new TypeReference<>() {
                });
        paramMap.forEach((k, v) -> getFetcherReply.putParams(Objects.toString(k), Objects.toString(v)));
        responseObserver.onNext(getFetcherReply.build());
        responseObserver.onCompleted();
    }

    @Override
    public void listFetchers(ListFetchersRequest request,
                             StreamObserver<ListFetchersReply> responseObserver) {
        ListFetchersReply.Builder listFetchersReplyBuilder = ListFetchersReply.newBuilder();
        for (Map.Entry<String, AbstractConfig> fetcherConfig : fetcherConfigs.entrySet()) {
            GetFetcherReply.Builder replyBuilder = createFetcherReply(fetcherConfig);
            listFetchersReplyBuilder.addGetFetcherReply(replyBuilder.build());
        }
        responseObserver.onNext(listFetchersReplyBuilder.build());
        responseObserver.onCompleted();
    }

    private GetFetcherReply.Builder createFetcherReply(Map.Entry<String, AbstractConfig> fetcherConfig) {
        AbstractFetcher abstractFetcher = fetchers.get(fetcherConfig.getKey());
        AbstractConfig abstractConfig = fetcherConfigs.get(fetcherConfig.getKey());
        GetFetcherReply.Builder replyBuilder = GetFetcherReply.newBuilder()
                .setFetcherClass(abstractFetcher.getClass().getName())
                .setName(abstractFetcher.getName());
        Map<String, Object> paramMap =
                OBJECT_MAPPER.convertValue(abstractConfig, new TypeReference<>() {
                });
        paramMap.forEach((k, v) -> replyBuilder.putParams(Objects.toString(k), Objects.toString(v)));
        return replyBuilder;
    }

    @Override
    public void deleteFetcher(DeleteFetcherRequest request,
                              StreamObserver<DeleteFetcherReply> responseObserver) {
        deleteFetcher(request.getName());
        try {
            updateTikaConfig();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteFetcher(String name) {
        fetcherConfigs.remove(name);
        fetchers.remove(name);
    }
}