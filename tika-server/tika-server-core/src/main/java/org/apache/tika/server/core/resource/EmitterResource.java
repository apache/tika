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

package org.apache.tika.server.core.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonFetchEmitTuple;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.StringUtils;

@Path("/emit")
public class EmitterResource {

    /**
     * key that is safe to pass through http header.
     * The user _must_ specify this for the fsemitter if calling 'put'
     */
    public static final String EMIT_KEY_FOR_HTTP_HEADER = "emit-key";
    private static final String EMITTER_PARAM = "emitter";
    private static final String HANDLER_PARAM = "type";
    private static final String FETCHER_NAME_ABBREV = "fn";
    private static final String FETCH_KEY_ABBREV = "fk";
    private static final String EMIT_KEY_ABBREV = "ek";

    private static final Logger LOG = LoggerFactory.getLogger(EmitterResource.class);

    private final FetcherManager fetcherManager;
    private final EmitterManager emitterManager;

    public EmitterResource(FetcherManager fetcherManager, EmitterManager emitterManager) {
        this.fetcherManager = fetcherManager;
        this.emitterManager = emitterManager;
    }

    static EmitKey calcEmitKey(FetchEmitTuple t) {
        //use fetch key if emitter key is not specified
        //TODO: clean this up?
        EmitKey emitKey = t.getEmitKey();
        if (StringUtils.isBlank(emitKey.getEmitKey())) {
            emitKey = new EmitKey(emitKey.getEmitterName(), t.getFetchKey().getFetchKey());
        }
        return emitKey;
    }

    /**
     * @param is              input stream is ignored in 'get'
     * @param httpHeaders
     * @param info
     * @param emitterName
     * @param fetcherName     specify the fetcherName in the url's query section
     * @param fetchKey        specify the fetch key in the url's query section
     * @param handlerTypeName text, html, xml, body, ignore; default is text
     * @return
     * @throws Exception
     */
    @GET
    @Produces("application/json")
    @Path("{" + EMITTER_PARAM + " : (\\w+)?}")
    public Map<String, String> getRmeta(InputStream is, @Context HttpHeaders httpHeaders,
                                        @Context UriInfo info,
                                        @PathParam(EMITTER_PARAM) String emitterName,
                                        @QueryParam(FETCHER_NAME_ABBREV) String fetcherName,
                                        @QueryParam(FETCH_KEY_ABBREV) String fetchKey,
                                        @QueryParam(EMIT_KEY_ABBREV) String emitKey,
                                        @QueryParam(HANDLER_PARAM) String handlerTypeName)
            throws Exception {
        Metadata metadata = new Metadata();
        Fetcher fetcher = fetcherManager.getFetcher(fetcherName);
        List<Metadata> metadataList;
        try (InputStream fetchedIs = fetcher.fetch(fetchKey, metadata)) {
            HandlerConfig handlerConfig = RecursiveMetadataResource
                    .buildHandlerConfig(httpHeaders.getRequestHeaders(), handlerTypeName);
            metadataList = RecursiveMetadataResource
                    .parseMetadata(fetchedIs, metadata, httpHeaders.getRequestHeaders(), info,
                            handlerConfig);
        }
        emitKey = StringUtils.isBlank(emitKey) ? fetchKey : emitKey;
        return emit(new EmitKey(emitterName, emitKey), metadataList);
    }


    /**
     * The user puts the raw bytes of the file and specifies the emitter
     * as elsewhere.  This will not trigger a fetcher.  If you want a
     * fetcher, use the get or post options.
     * <p>
     * The extracted text content is stored with the key
     * {@link TikaCoreProperties#TIKA_CONTENT}
     * <p>
     * Must specify an emitter in the path, e.g. /emit/solr
     * <p>
     * Optionally, may specify handler, e.g. /emit/solr/xml
     *
     * @param info      uri info
     * @param fullParam which emitter to use; emitters must be configured in
     *                  the TikaConfig file.
     * @return InputStream that can be deserialized as a list of {@link Metadata} objects
     * @throws Exception
     */
    @PUT
    @Produces("application/json")
    @Path("{" + EMITTER_PARAM + " : (\\w+(/(text|body|xml|ignore))?)}")
    public Map<String, String> putRmeta(InputStream is, @Context HttpHeaders httpHeaders,
                                        @Context UriInfo info,
                                        @PathParam(EMITTER_PARAM) String fullParam)
            throws Exception {

        Matcher m = Pattern.compile("(\\w+)(?:/(\\w+))?").matcher(fullParam);
        String emitterName = fullParam;
        String handlerTypeName = "text";
        if (m.find()) {
            emitterName = m.group(1);
            if (m.groupCount() > 1) {
                handlerTypeName = m.group(2);
            }
        }
        Metadata metadata = new Metadata();
        String emitKey = httpHeaders.getHeaderString(EMIT_KEY_FOR_HTTP_HEADER);
        HandlerConfig handlerConfig = RecursiveMetadataResource
                .buildHandlerConfig(httpHeaders.getRequestHeaders(), handlerTypeName);
        List<Metadata> metadataList = RecursiveMetadataResource
                .parseMetadata(is, metadata, httpHeaders.getRequestHeaders(), info, handlerConfig);
        return emit(new EmitKey(emitterName, emitKey), metadataList);
    }

    /**
     * The client posts a json request.  At a minimum, this must be a
     * json object that contains an emitter and a fetcherString key with
     * the key to fetch the inputStream. Optionally, it may contain a metadata
     * object that will be used to populate the metadata key for pass
     * through of metadata from the client. It may also include a handler config.
     * <p>
     * The extracted text content is stored with the key
     * {@link TikaCoreProperties#TIKA_CONTENT}
     * <p>
     * Must specify a fetcherString and an emitter in the posted json.
     *
     * @param info uri info
     * @return InputStream that can be deserialized as a list of {@link Metadata} objects
     * @throws Exception
     */
    @POST
    @Produces("application/json")
    public Map<String, String> postRmeta(InputStream is, @Context HttpHeaders httpHeaders,
                                         @Context UriInfo info) throws Exception {
        FetchEmitTuple t = null;
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            t = JsonFetchEmitTuple.fromJson(reader);
        }
        Metadata metadata = new Metadata();

        List<Metadata> metadataList = null;
        try (InputStream stream = fetcherManager
                .getFetcher(t.getFetchKey().getFetcherName())
                .fetch(t.getFetchKey().getFetchKey(), metadata)) {

            metadataList = RecursiveMetadataResource
                    .parseMetadata(stream, metadata, httpHeaders.getRequestHeaders(), info,
                            t.getHandlerConfig());
        } catch (Error error) {
            return returnError(t.getEmitKey().getEmitterName(), error);
        }

        boolean shouldEmit = checkParseException(t, metadataList);
        if (!shouldEmit) {
            return skip(t, metadataList);
        }

        injectUserMetadata(t.getMetadata(), metadataList.get(0));

        for (String n : metadataList.get(0).names()) {
            LOG.debug("post parse/pre emit metadata {}: {}", n, metadataList.get(0).get(n));
        }
        return emit(calcEmitKey(t), metadataList);
    }

    private Map<String, String> skip(FetchEmitTuple t, List<Metadata> metadataList) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "ok");
        statusMap.put("emitter", t.getEmitKey().getEmitterName());
        statusMap.put("emitKey", t.getEmitKey().getEmitKey());
        String msg = metadataList.get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION);
        statusMap.put("parse_exception", msg);
        return statusMap;
    }

    private boolean checkParseException(FetchEmitTuple t, List<Metadata> metadataList) {
        if (metadataList == null || metadataList.size() < 1) {
            return false;
        }
        boolean shouldEmit = true;
        String stack = metadataList.get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION);
        if (stack != null) {
            if (t.getOnParseException() == FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP) {
                shouldEmit = false;
            }
            LOG.warn("fetchKey ({}) caught container parse exception ({})",
                    t.getFetchKey().getFetchKey(), stack);
        }

        for (int i = 1; i < metadataList.size(); i++) {
            Metadata m = metadataList.get(i);
            String embeddedStack = m.get(TikaCoreProperties.EMBEDDED_EXCEPTION);
            if (embeddedStack != null) {
                LOG.warn("fetchKey ({}) caught embedded parse exception ({})",
                        t.getFetchKey().getFetchKey(), embeddedStack);
            }
        }

        return shouldEmit;
    }


    private void injectUserMetadata(Metadata userMetadata, Metadata metadata) {
        for (String n : userMetadata.names()) {
            metadata.set(n, null);
            for (String v : userMetadata.getValues(n)) {
                metadata.add(n, v);
            }
        }
    }

    private Map<String, String> returnError(String emitterName, Error error) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "parse_error");
        statusMap.put("emitter", emitterName);
        String msg = ExceptionUtils.getStackTrace(error);
        statusMap.put("parse_error", msg);
        return statusMap;
    }

    private Map<String, String> emit(EmitKey emitKey, List<Metadata> metadataList)
            throws TikaException {
        Emitter emitter = emitterManager.getEmitter(emitKey.getEmitterName());
        String status = "ok";
        String exceptionMsg = "";
        try {
            emitter.emit(emitKey.getEmitKey(), metadataList);
        } catch (IOException | TikaEmitterException e) {
            LOG.warn("problem emitting (" + emitKey.getEmitterName() + ")", e);
            status = "emitter_exception";
            exceptionMsg = ExceptionUtils.getStackTrace(e);
        }
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", status);
        statusMap.put("emitter", emitKey.getEmitterName());
        if (exceptionMsg.length() > 0) {
            statusMap.put("emitter_exception", exceptionMsg);
        }
        String parseStackTrace = metadataList.get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION);
        if (parseStackTrace != null) {
            statusMap.put("parse_exception", parseStackTrace);
        }
        return statusMap;
    }

}
