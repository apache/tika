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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/emit")
public class EmitterResource {

    private static final String EMITTER_PARAM = "emitter";
    private static final String FETCHER_NAME_ABBREV = "fn";
    private static final String FETCH_KEY_ABBREV = "fk";
    private static final String EMIT_KEY_ABBREV = "ek";

    /**
     * key that is safe to pass through http header.
     * The user _must_ specify this for the fsemitter if calling 'put'
     */
    public static final String EMIT_KEY_FOR_HTTP_HEADER = "emit-key";
    private static final Logger LOG = LoggerFactory.getLogger(EmitterResource.class);


    /**
     * @param is          input stream is ignored in 'get'
     * @param httpHeaders
     * @param info
     * @param emitterName
     * @param fetcherName specify the fetcherName in the url's query section
     * @param fetchKey    specify the fetch key in the url's query section
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
                                        @QueryParam(EMIT_KEY_ABBREV) String emitKey) throws Exception {
        Metadata metadata = new Metadata();
        Fetcher fetcher = TikaResource.getConfig().getFetcherManager().getFetcher(fetcherName);
        List<Metadata> metadataList;
        try (InputStream fetchedIs = fetcher.fetch(fetchKey, metadata)) {
            metadataList =
                    RecursiveMetadataResource.parseMetadata(fetchedIs,
                            metadata,
                            httpHeaders.getRequestHeaders(), info, "text");
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
     *
     * @param info        uri info
     * @param emitterName which emitter to use; emitters must be configured in
     *                    the TikaConfig file.
     * @return InputStream that can be deserialized as a list of {@link Metadata} objects
     * @throws Exception
     */
    @PUT
    @Produces("application/json")
    @Path("{" + EMITTER_PARAM + " : (\\w+)?}")
    public Map<String, String> putRmeta(InputStream is,
                                        @Context HttpHeaders httpHeaders,
                                        @Context UriInfo info,
                                        @PathParam(EMITTER_PARAM) String emitterName
    ) throws Exception {

        Metadata metadata = new Metadata();
        String emitKey = httpHeaders.getHeaderString(EMIT_KEY_FOR_HTTP_HEADER);
        List<Metadata> metadataList =
                RecursiveMetadataResource.parseMetadata(is,
                        metadata,
                        httpHeaders.getRequestHeaders(), info, "text");
        return emit(new EmitKey(emitterName, emitKey), metadataList);
    }

    /**
     * The client posts a json request.  At a minimum, this must be a
     * json object that contains an emitter and a fetcherString key with
     * the key to fetch the inputStream. Optionally, it may contain a metadata
     * object that will be used to populate the metadata key for pass
     * through of metadata from the client.
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
    public Map<String, String> postRmeta(InputStream is,
                                         @Context HttpHeaders httpHeaders,
                                         @Context UriInfo info
    ) throws Exception {

        JsonFactory jfactory = new JsonFactory();
        JsonParser jParser = jfactory.createParser(is);
        FetchEmitTuple t = deserializeTuple(jParser);
        Metadata metadata = new Metadata();


        List<Metadata> metadataList = null;
        try (InputStream stream =
                     TikaResource.getConfig().getFetcherManager()
                             .getFetcher(t.getFetchKey().getFetcherName())
                             .fetch(t.getFetchKey().getKey(), metadata)) {

            metadataList = RecursiveMetadataResource.parseMetadata(
                    stream,
                    metadata,
                    httpHeaders.getRequestHeaders(), info, "text");
        } catch (Error error) {
            return returnError(t.getEmitKey().getEmitterName(), error);
        }

        injectUserMetadata(t.getMetadata(), metadataList.get(0));

        for (String n : metadataList.get(0).names()) {
            LOG.debug("post parse/pre emit metadata {}: {}",
                    n, metadataList.get(0).get(n));
        }
        return emit(t.getEmitKey(), metadataList);
    }

    private FetchEmitTuple deserializeTuple(JsonParser jParser) throws IOException {
        String fetcherName = null;
        String fetchKey = null;
        String emitterName = null;
        String emitKey = null;

        Metadata metadata = null;
        while (jParser.nextToken() != JsonToken.END_OBJECT) {
            String currentName = jParser.getCurrentName();
            if ("fetcherName".equals(currentName)) {
                fetcherName = currentName;
            } else if ("fetchKey".equals(currentName)) {
                fetchKey = currentName;
            } else if ("emitterName".equals(currentName)) {
                emitterName = currentName;
            } else if ("emitKey".equals(currentName)) {
                emitKey = currentName;
            } else if ("metadata".equals(currentName)) {
                metadata = deserializeMetadata(jParser);
            }
        }
        return new FetchEmitTuple(new FetchKey(fetcherName, fetchKey),
                new EmitKey(emitterName, emitKey), metadata);
    }

    private Metadata deserializeMetadata(JsonParser jParser) throws IOException {
        Metadata metadata = new Metadata();

        while (jParser.nextToken() != JsonToken.END_OBJECT) {
            String key = jParser.getCurrentName();
            JsonToken token = jParser.nextToken();
            if (jParser.isExpectedStartArrayToken()) {
                List<String> vals = new ArrayList<>();
                while (jParser.nextToken() != JsonToken.END_ARRAY) {
                    metadata.add(key, jParser.getText());
                }
            } else {
                metadata.set(key, token.asString());
            }
        }
        return metadata;

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

    private Map<String, String> emit(EmitKey emitKey, List<Metadata> metadataList) throws TikaException {
        Emitter emitter = TikaResource.getConfig().getEmitterManager().getEmitter(emitKey.getEmitterName());
        String status = "ok";
        String exceptionMsg = "";
        try {
            emitter.emit(emitKey.getKey(), metadataList);
        } catch (IOException | TikaEmitterException e) {
            LOG.warn("problem with emitting", e);
            status = "emitter_exception";
            exceptionMsg = ExceptionUtils.getStackTrace(e);
        }
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", status);
        statusMap.put("emitter", emitKey.getEmitterName());
        if (exceptionMsg.length() > 0) {
            statusMap.put("emitter_exception", exceptionMsg);
        }
        String parseStackTrace = metadataList.get(0).get(
                TikaCoreProperties.CONTAINER_EXCEPTION);
        if (parseStackTrace != null) {
            statusMap.put("parse_exception", parseStackTrace);
        }
        return statusMap;
    }

}
