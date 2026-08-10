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
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
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
    private final PipesConfig pipesConfig;

    /**
     * @param pipesParser shared parser, also used by /tika, /rmeta, and /unpack.
     *                     Lifecycle (construction, shutdown) is owned by whoever
     *                     built it, not by this class.
     * @param pipesConfig the parser's config; read here for the {@code Retry-After} value.
     */
    public PipesResource(PipesParser pipesParser, PipesConfig pipesConfig) {
        this.pipesParser = pipesParser;
        this.pipesConfig = pipesConfig;
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
     * @return a JSON body describing the outcome (status/type, or a parse_exception),
     *         with HTTP status reflecting whether the process succeeded, crashed, or
     *         was unavailable within the configured wait
     * @throws Exception
     */
    @POST
    @Produces("application/json")
    public Response postRmeta(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {
        FetchEmitTuple t = null;
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            t = JsonFetchEmitTuple.fromJson(reader);
        }
        // Resolve friendly-named configs in ParseContext to actual objects
        ParseContextUtils.resolveAll(t.getParseContext(), getClass().getClassLoader());
        return processTuple(t);
    }

    private Response processTuple(FetchEmitTuple fetchEmitTuple) throws InterruptedException, PipesException, IOException {
        // This parser is shared with /tika+/rmeta+/unpack, whose own default is
        // PASSBACK_ALL. /pipes needs the child to emit via the client's configured
        // emitter by default -- set EMIT_ALL explicitly per-request rather than
        // relying on the parser-level default, but don't clobber a caller's own
        // explicit override if they set one.
        ParseContext parseContext = fetchEmitTuple.getParseContext();
        if (parseContext.get(EmitStrategyConfig.class) == null) {
            parseContext.set(EmitStrategyConfig.class, new EmitStrategyConfig(EmitStrategy.EMIT_ALL));
        }
        PipesResult pipesResult = pipesParser.parse(fetchEmitTuple);
        Map<String, String> body;
        if (pipesResult.isProcessCrash()) {
            body = returnProcessCrash(pipesResult.status().toString());
        } else if (!pipesResult.isSuccess()) {
            // Handle fatal errors, initialization failures, and task exceptions
            body = returnApplicationError(pipesResult
                    .status()
                    .toString());
        } else {
            body = switch (pipesResult.status()) {
                case EMIT_SUCCESS_PARSE_EXCEPTION -> parseException(pipesResult.message(), true);
                case PARSE_EXCEPTION_NO_EMIT -> parseException(pipesResult.message(), false);
                default -> returnSuccess();
            };
        }
        // Same status mapping /tika+/rmeta+/unpack use (PipesParsingHelper) -- e.g. 429 for
        // CLIENT_UNAVAILABLE_WITHIN_MS, 503 for TIMEOUT/OOM/UNSPECIFIED_CRASH -- rather than
        // always 200 with the failure only visible in the body.
        return PipesParsingHelper
                .responseBuilder(pipesResult.status(), pipesConfig.getMaxWaitForClientMillis())
                .entity(body)
                .build();
    }

    private Map<String, String> parseException(String msg, boolean emitted) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "ok");
        // 200 response, so trim rather than omit -- same reasoning as redactExceptionDetail.
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
}
