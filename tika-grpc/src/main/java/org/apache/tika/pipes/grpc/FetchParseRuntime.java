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

import java.io.IOException;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.grpc.Status;
import io.grpc.StatusException;
import org.apache.commons.lang3.StringUtils;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.core.Lease;
import org.apache.tika.pipes.core.PipesException;
import org.apache.tika.pipes.core.PipesParser;
import org.apache.tika.pipes.core.fetcher.FetcherManager;
import org.apache.tika.serialization.ComponentNameResolver;
import org.apache.tika.serialization.ParseContextUtils;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;

/**
 * The fetch-and-parse path every gRPC service version shares: the per-request config gates, fetcher
 * lookup and the pipes call. Holds no state of its own; the v1 server builds and closes what it
 * uses.
 */
final class FetchParseRuntime {

    // duplicate keys in parse_context_json are refused, not last-one-wins
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private final TikaGrpcConfig tikaGrpcConfig;
    private final FetcherManager fetcherManager;
    private final PipesParser pipesParser;

    FetchParseRuntime(TikaGrpcConfig tikaGrpcConfig, FetcherManager fetcherManager,
                      PipesParser pipesParser) {
        this.tikaGrpcConfig = tikaGrpcConfig;
        this.fetcherManager = fetcherManager;
        this.pipesParser = pipesParser;
    }

    /** {@code PERMISSION_DENIED} if the request carries per-request config the operator has not
     * allowed, else {@code null}. */
    Status perRequestConfigDenial(String additionalFetchConfigJson, String parseContextJson) {
        boolean hasPerRequestConfig = StringUtils.isNotBlank(additionalFetchConfigJson)
                || StringUtils.isNotBlank(parseContextJson);
        if (!hasPerRequestConfig || tikaGrpcConfig.isAllowPerRequestConfig()) {
            return null;
        }
        return Status.PERMISSION_DENIED
                .withDescription("Per-request configuration is disabled. Set "
                        + "'allowPerRequestConfig' to true in the 'grpc' section of your "
                        + "tika-config to allow additional_fetch_config_json / "
                        + "parse_context_json. Understand the security implications first.");
    }

    /**
     * Builds the per-request {@link ParseContext} from {@code parse_context_json}, or throws
     * {@code INVALID_ARGUMENT}.
     * <p>
     * The request is untrusted wire input, so it is deserialized restricted and resolved here, as
     * the tika-server resources do. Left to the fork, the same refusal (of
     * {@code exception-reporting}, say) is a deserialization failure that exits it: a JVM restart
     * per bad request, and a crash status with no reason for the caller. The trade-off, accepted
     * deliberately: a component registered only on the fork's classpath is refused here too --
     * fail-fast validation runs against this JVM's registries. Serialization still forwards the
     * caller's original jsonConfigs, not the instances resolution materializes.
     */
    ParseContext requestParseContext(String fetcherId, String parseContextJson,
                                     String additionalFetchConfigJson) throws StatusException {
        ParseContext parseContext = new ParseContext();
        if (StringUtils.isNotBlank(parseContextJson)) {
            try {
                parseContext = ParseContextDeserializer.readParseContext(
                        JSON.readTree(parseContextJson), true);
                ParseContextUtils.resolveAll(parseContext, getClass().getClassLoader());
            } catch (IOException | TikaConfigException e) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("Invalid parse_context_json: " + e.getMessage())
                        .asException();
            }
        }
        if (StringUtils.isNotBlank(additionalFetchConfigJson)) {
            // The fork reads this jsonConfig by the fetcher's registered component name
            // (e.g. "http-fetcher"). An unregistered key fails the fork's resolveAll and a
            // wire-blocked one is refused by its restricted tuple deserialization -- both
            // exit the worker. Enforce here: a 400, not a JVM restart per request.
            var info = ComponentNameResolver.getComponentInfo(fetcherId);
            if (info.isEmpty() || ComponentNameResolver.isWireBlocked(
                    ComponentNameResolver.determineContextKey(info.get()))) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("additional_fetch_config_json requires fetcher_id to "
                                + "be a registered, wire-allowed component name; got '"
                                + fetcherId + "'")
                        .asException();
            }
            parseContext.setJsonConfig(fetcherId, additionalFetchConfigJson);
        }
        return parseContext;
    }

    /** The registered id of {@code fetcherId}. */
    String resolveFetcherId(String fetcherId) throws TikaException, IOException {
        try (Lease<Fetcher> lease = fetcherManager.getFetcher(fetcherId)) {
            return lease.get().getExtensionConfig().id();
        }
    }

    /** One fetch and parse through pipes, emitting nothing. */
    PipesResult fetchAndParse(String resolvedFetcherId, String fetchKey, ParseContext parseContext)
            throws IOException, PipesException, InterruptedException {
        return pipesParser.parse(new FetchEmitTuple(fetchKey, new FetchKey(resolvedFetcherId, fetchKey),
                new EmitKey(), new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
    }
}
