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
import java.util.Map;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.PipesConfig;
import org.apache.tika.pipes.PipesException;
import org.apache.tika.pipes.PipesParser;
import org.apache.tika.pipes.PipesResult;
import org.apache.tika.serialization.pipes.JsonFetchEmitTuple;

@Path("/pipes")
public class PipesResource {


    private static final Logger LOG = LoggerFactory.getLogger(PipesResource.class);

    private final PipesParser pipesParser;

    public PipesResource(java.nio.file.Path tikaConfig) throws TikaConfigException, IOException {
        PipesConfig pipesConfig = PipesConfig.load(tikaConfig);
        //this has to be zero. everything must be emitted through the PipesServer
        long maxEmit = pipesConfig.getMaxForEmitBatchBytes();
        if (maxEmit != 0) {
            pipesConfig.setMaxForEmitBatchBytes(0);
            if (maxEmit != PipesConfig.DEFAULT_MAX_FOR_EMIT_BATCH) {
                LOG.warn("resetting max for emit batch to 0");
            }
        }
        this.pipesParser = new PipesParser(pipesConfig);
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
    public Map<String, String> postRmeta(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {
        FetchEmitTuple t = null;
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            t = JsonFetchEmitTuple.fromJson(reader);
        }
        return processTuple(t);
    }

    private Map<String, String> processTuple(FetchEmitTuple fetchEmitTuple) throws InterruptedException, PipesException, IOException {

        PipesResult pipesResult = pipesParser.parse(fetchEmitTuple);
        switch (pipesResult.getStatus()) {
            case CLIENT_UNAVAILABLE_WITHIN_MS:
                throw new IllegalStateException("client not available within " + "allotted amount of time");
            case EMIT_EXCEPTION:
                return returnEmitException(pipesResult.getMessage());
            case PARSE_SUCCESS:
            case PARSE_SUCCESS_WITH_EXCEPTION:
                throw new IllegalArgumentException("Should have emitted in forked process?!");
            case EMIT_SUCCESS:
                return returnSuccess();
            case EMIT_SUCCESS_PARSE_EXCEPTION:
                return parseException(pipesResult.getMessage(), true);
            case PARSE_EXCEPTION_EMIT:
                throw new IllegalArgumentException("Should have tried to emit in forked " + "process?!");
            case PARSE_EXCEPTION_NO_EMIT:
                return parseException(pipesResult.getMessage(), false);
            case TIMEOUT:
                return returnError("timeout");
            case OOM:
                return returnError("oom");
            case UNSPECIFIED_CRASH:
                return returnError("unknown_crash");
            case NO_EMITTER_FOUND: {
                throw new IllegalArgumentException("Couldn't find emitter that matched: " + fetchEmitTuple
                        .getEmitKey()
                        .getEmitterName());
            }
            default:
                throw new IllegalArgumentException("I'm sorry, I don't yet handle a status of " + "this type: " + pipesResult.getStatus());
        }
    }

    private Map<String, String> parseException(String msg, boolean emitted) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "ok");
        statusMap.put("parse_exception", msg);
        statusMap.put("emitted", Boolean.toString(emitted));
        return statusMap;
    }

    private Map<String, String> returnEmitException(String msg) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "emit_exception");
        statusMap.put("message", msg);
        return statusMap;
    }

    private Map<String, String> returnSuccess() {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "ok");
        return statusMap;
    }

    private Map<String, String> returnError(String type) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "parse_error");
        statusMap.put("parse_error", type);
        return statusMap;
    }

    public void close() throws IOException {
        pipesParser.close();
    }
}
