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

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
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
     * The client posts a single JSON FetchEmitTuple: a fetcher and fetch key
     * for input and an emitter for output; it may also carry a metadata object
     * (passed through to the emitted metadata) and a parse context.
     * <p>
     * The extracted text content is stored with the key
     * {@link TikaCoreProperties#TIKA_CONTENT}
     *
     * @param info uri info
     * @return {@code {"status":<RESULT_STATUS>,"message":...}}, with HTTP status
     *         reflecting whether the process succeeded, crashed, or was
     *         unavailable within the configured wait
     * @throws Exception
     */
    @POST
    @Produces("application/json")
    public Response postRmeta(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {
        FetchEmitTuple t = null;
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            t = JsonFetchEmitTuple.fromJson(reader);
        } catch (IOException e) {
            // A malformed body is the caller's error, not a server fault -- 400 with the reason.
            throw new BadRequestException("Could not parse the /pipes request body: " + e.getMessage(), e);
        }
        // Resolve friendly-named configs in ParseContext to actual objects
        try {
            ParseContextUtils.resolveAll(t.getParseContext(), getClass().getClassLoader());
        } catch (TikaConfigException e) {
            throw new BadRequestException(
                    "Could not resolve the parseContext in the /pipes request body: " + e.getMessage(), e);
        }
        return processTuple(t);
    }

    private Response processTuple(FetchEmitTuple fetchEmitTuple) throws InterruptedException, PipesException, IOException {
        PipesParsingHelper.rejectReservedComponentIds(fetchEmitTuple);
        // This parser is shared with /tika+/rmeta+/unpack, whose own default is
        // PASSBACK_ALL. /pipes needs the child to emit via the client's configured
        // emitter -- set EMIT_ALL explicitly per-request rather than relying on the
        // parser-level default.
        ParseContext parseContext = fetchEmitTuple.getParseContext();
        EmitStrategyConfig callerStrategy = parseContext.get(EmitStrategyConfig.class);
        if (callerStrategy == null) {
            parseContext.set(EmitStrategyConfig.class, new EmitStrategyConfig(EmitStrategy.EMIT_ALL));
        } else if (callerStrategy.getType() != EmitStrategy.EMIT_ALL) {
            // The /pipes response body carries only status+message: a passback strategy
            // would report EMIT_SUCCESS_PASSBACK while the parsed data went nowhere.
            throw new BadRequestException("emit-strategy-config '" + callerStrategy.getType()
                    + "' is not supported on /pipes: the response cannot carry passed-back data."
                    + " Use EMIT_ALL (the default) or parse via /rmeta instead.");
        }
        PipesResult pipesResult = pipesParser.parse(fetchEmitTuple);
        // One body shape for every outcome -- {"status":<RESULT_STATUS>,"message":...} --
        // matching /tika, /rmeta, and /unpack. status is always the real enum, so a parse
        // that threw is never reported as "ok".
        Map<String, String> body = statusBody(pipesResult);
        // Same status mapping /tika+/rmeta+/unpack use (PipesParsingHelper) -- e.g. 429 for
        // CLIENT_UNAVAILABLE_WITHIN_MS, 503 for TIMEOUT/OOM/UNSPECIFIED_CRASH -- rather than
        // always 200 with the failure only visible in the body.
        return PipesParsingHelper
                .responseBuilder(pipesResult.status(), pipesConfig.getMaxWaitForClientMillis())
                .entity(body)
                .build();
    }

    private Map<String, String> statusBody(PipesResult pipesResult) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", pipesResult.status().name());
        String message = pipesResult.message();
        if (message != null && !message.isBlank()) {
            statusMap.put("message", message);
        }
        return statusMap;
    }
}
