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
package org.apache.tika.pipes.reporter.es;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.client.TikaClientException;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.ExternalProcess;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.pipesiterator.TotalCountResult;
import org.apache.tika.pipes.emitter.es.HttpClientConfig;
import org.apache.tika.pipes.emitter.es.JsonResponse;
import org.apache.tika.pipes.reporters.PipesReporterBase;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.utils.StringUtils;

public class ESPipesReporter extends PipesReporterBase {

    private static final Logger LOG = LoggerFactory.getLogger(ESPipesReporter.class);

    public static final String DEFAULT_PARSE_TIME_KEY = "parse_time_ms";
    public static final String DEFAULT_PARSE_STATUS_KEY = "parse_status";
    public static final String DEFAULT_EXIT_VALUE_KEY = "exit_value";

    private final ESReporterConfig config;
    private HttpClient httpClient;
    private String parseTimeKey = DEFAULT_PARSE_TIME_KEY;
    private String parseStatusKey = DEFAULT_PARSE_STATUS_KEY;
    private String exitValueKey = DEFAULT_EXIT_VALUE_KEY;

    public static ESPipesReporter build(ExtensionConfig pluginConfig)
            throws TikaConfigException, IOException {
        ESReporterConfig config = ESReporterConfig.load(pluginConfig.json());
        return new ESPipesReporter(pluginConfig, config);
    }

    public ESPipesReporter(ExtensionConfig pluginConfig, ESReporterConfig config)
            throws TikaConfigException {
        super(pluginConfig, config.includes(), config.excludes());
        this.config = config;
        init();
    }

    private void init() throws TikaConfigException {
        if (StringUtils.isBlank(config.esUrl())) {
            throw new TikaConfigException("Must specify an esUrl!");
        }
        HttpClientFactory httpClientFactory = new HttpClientFactory();
        HttpClientConfig http = config.httpClientConfig();
        if (http != null) {
            httpClientFactory.setUserName(http.userName());
            httpClientFactory.setPassword(http.password());
            if (http.socketTimeout() > 0) {
                httpClientFactory.setSocketTimeout(http.socketTimeout());
            }
            if (http.connectionTimeout() > 0) {
                httpClientFactory.setConnectTimeout(http.connectionTimeout());
            }
            if (http.authScheme() != null) {
                httpClientFactory.setAuthScheme(http.authScheme());
            }
            if (http.proxyHost() != null) {
                httpClientFactory.setProxyHost(http.proxyHost());
                httpClientFactory.setProxyPort(http.proxyPort());
            }
            httpClientFactory.setVerifySsl(http.verifySsl());
        }
        httpClient = httpClientFactory.build();

        parseStatusKey = StringUtils.isBlank(config.keyPrefix())
                ? parseStatusKey : config.keyPrefix() + parseStatusKey;
        parseTimeKey = StringUtils.isBlank(config.keyPrefix())
                ? parseTimeKey : config.keyPrefix() + parseTimeKey;
        exitValueKey = StringUtils.isBlank(config.keyPrefix())
                ? exitValueKey : config.keyPrefix() + exitValueKey;
    }

    @Override
    public void report(FetchEmitTuple t, PipesResult result, long elapsed) {
        if (!accept(result.status())) {
            return;
        }
        Metadata metadata = new Metadata();
        metadata.set(parseStatusKey, result.status().name());
        metadata.set(parseTimeKey, Long.toString(elapsed));
        if (result.emitData() != null && result.emitData().getMetadataList() != null &&
                !result.emitData().getMetadataList().isEmpty()) {
            Metadata m = result.emitData().getMetadataList().get(0);
            if (m.get(ExternalProcess.EXIT_VALUE) != null) {
                metadata.set(exitValueKey, m.get(ExternalProcess.EXIT_VALUE));
            }
        }
        try {
            String routing = config.includeRouting()
                    ? t.getEmitKey().getEmitKey() : null;
            emitDocument(t.getEmitKey().getEmitKey(), routing, metadata);
        } catch (IOException | TikaClientException e) {
            LOG.warn("failed to report status for '{}'", t.getId(), e);
        }
    }

    private void emitDocument(String emitKey, String routing, Metadata metadata)
            throws IOException, TikaClientException {
        StringWriter writer = new StringWriter();
        writeBulkHeader(emitKey, routing, writer);
        writer.append("\n");
        writeDoc(metadata, writer);
        writer.append("\n");

        String requestUrl = config.esUrl() + "/_bulk";
        JsonResponse response = postJson(requestUrl, writer.toString());
        if (response.getStatus() != 200) {
            throw new TikaClientException(response.getMsg());
        }
        JsonNode errorNode = response.getJson().get("errors");
        if (errorNode != null && errorNode.asText().equals("true")) {
            throw new TikaClientException(response.getJson().toString());
        }
    }

    private void writeBulkHeader(String id, String routing, StringWriter writer)
            throws IOException {
        try (JsonGenerator jg = new JsonFactory().createGenerator(writer)) {
            jg.writeStartObject();
            jg.writeObjectFieldStart("update");
            jg.writeStringField("_id", id);
            if (!StringUtils.isEmpty(routing)) {
                jg.writeStringField("routing", routing);
            }
            jg.writeNumberField("retry_on_conflict", 3);
            jg.writeEndObject();
            jg.writeEndObject();
        }
    }

    private void writeDoc(Metadata metadata, StringWriter writer) throws IOException {
        try (JsonGenerator jg = new JsonFactory().createGenerator(writer)) {
            jg.writeStartObject();
            jg.writeObjectFieldStart("doc");
            for (String n : metadata.names()) {
                String[] vals = metadata.getValues(n);
                if (vals.length == 1) {
                    jg.writeStringField(n, vals[0]);
                } else {
                    jg.writeArrayFieldStart(n);
                    for (String v : vals) {
                        jg.writeString(v);
                    }
                    jg.writeEndArray();
                }
            }
            jg.writeEndObject();
            jg.writeBooleanField("doc_as_upsert", true);
            jg.writeEndObject();
        }
    }

    private JsonResponse postJson(String url, String json) throws IOException {
        HttpPost httpRequest = new HttpPost(url);
        httpRequest.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
        httpRequest.setHeader("Accept", "application/json");
        httpRequest.setHeader("Content-type", "application/json; charset=utf-8");
        if (!StringUtils.isEmpty(config.apiKey())) {
            httpRequest.setHeader("Authorization", "ApiKey " + config.apiKey());
        }
        HttpResponse response = null;
        try {
            response = httpClient.execute(httpRequest);
            int status = response.getStatusLine().getStatusCode();
            if (status == 200) {
                try (Reader reader = new BufferedReader(
                        new InputStreamReader(
                                response.getEntity().getContent(),
                                StandardCharsets.UTF_8))) {
                    JsonNode node = new ObjectMapper().readTree(reader);
                    return new JsonResponse(200, node);
                }
            } else {
                return new JsonResponse(status,
                        new String(EntityUtils.toByteArray(response.getEntity()),
                                StandardCharsets.UTF_8));
            }
        } finally {
            if (response instanceof CloseableHttpResponse) {
                ((CloseableHttpResponse) response).close();
            }
            httpRequest.releaseConnection();
        }
    }

    @Override
    public void report(TotalCountResult totalCountResult) {
        // not supported
    }

    @Override
    public boolean supportsTotalCount() {
        return false;
    }

    @Override
    public void error(Throwable t) {
        LOG.error("crashed", t);
    }

    @Override
    public void error(String msg) {
        LOG.error("crashed {}", msg);
    }

    @Override
    public void close() throws IOException {
        // nothing to close
    }
}
