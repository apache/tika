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

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.core.EmitStrategy;
import org.apache.tika.pipes.core.EmitStrategyConfig;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.PipesException;
import org.apache.tika.pipes.core.PipesParser;
import org.apache.tika.pipes.core.serialization.JsonFetchEmitTuple;
import org.apache.tika.serialization.ParseContextUtils;

@Path("/pipes")
public class PipesResource {


    private static final Logger LOG = LoggerFactory.getLogger(PipesResource.class);

    private final PipesParser pipesParser;

    public PipesResource(java.nio.file.Path tikaConfig) throws TikaConfigException, IOException {
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfig);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        // Everything must be emitted through the PipesServer (EMIT_ALL strategy)
        if (pipesConfig.getEmitStrategy().getType() != EmitStrategy.EMIT_ALL) {
            if (pipesConfig.getEmitStrategy().getType() != EmitStrategyConfig.DEFAULT_EMIT_STRATEGY) {
                LOG.warn("resetting emit strategy to EMIT_ALL for pipes endpoint");
                pipesConfig.setEmitStrategy(new EmitStrategyConfig(EmitStrategy.EMIT_ALL));
            }
        }
        this.pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfig);
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
        // Resolve friendly-named configs in ParseContext to actual objects
        ParseContextUtils.resolveAll(t.getParseContext(), getClass().getClassLoader());
        return processTuple(t);
    }

    private Map<String, String> processTuple(FetchEmitTuple fetchEmitTuple) throws InterruptedException, PipesException, IOException {

        PipesResult pipesResult = pipesParser.parse(fetchEmitTuple);
        if (pipesResult.isProcessCrash()) {
            return returnProcessCrash(pipesResult.status().toString());
        } else if (!pipesResult.isSuccess()) {
            // Handle fatal errors, initialization failures, and task exceptions
            return returnApplicationError(pipesResult
                    .status()
                    .toString());
        }
        switch (pipesResult.status()) {
            case EMIT_SUCCESS_PARSE_EXCEPTION:
                return parseException(pipesResult.message(), true);
            case PARSE_EXCEPTION_NO_EMIT:
                return parseException(pipesResult.message(), false);
        }
        return returnSuccess();
    }

    private Map<String, String> parseException(String msg, boolean emitted) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "ok");
        statusMap.put("parse_exception", msg);
        statusMap.put("emitted", Boolean.toString(emitted));
        return statusMap;
    }

    private Map<String, String> returnSuccess() {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "ok");
        return statusMap;
    }

    private Map<String, String> returnProcessCrash(String type) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "process_crash");
        statusMap.put("type", type);
        return statusMap;
    }

    private Map<String, String> returnApplicationError(String type) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "application_error");
        statusMap.put("type", type);
        return statusMap;
    }

    public void close() throws IOException {
        pipesParser.close();
    }
}
