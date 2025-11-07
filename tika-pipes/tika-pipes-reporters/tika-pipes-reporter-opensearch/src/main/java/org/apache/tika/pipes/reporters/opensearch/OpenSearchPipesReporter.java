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
package org.apache.tika.pipes.reporters.opensearch;

import java.io.IOException;

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
import org.apache.tika.pipes.reporters.PipesReporterBase;
import org.apache.tika.plugins.PluginConfig;
import org.apache.tika.utils.StringUtils;

/**
 * As of the 2.5.0 release, this is ALPHA version.  There may be breaking changes
 * in the future.
 */
public class OpenSearchPipesReporter extends PipesReporterBase {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchPipesReporter.class);

    public static String DEFAULT_PARSE_TIME_KEY = "parse_time_ms";
    public static String DEFAULT_PARSE_STATUS_KEY = "parse_status";
    public static String DEFAULT_EXIT_VALUE_KEY = "exit_value";



    public static OpenSearchPipesReporter build(PluginConfig pluginConfig) throws TikaConfigException, IOException {
        OpenSearchReporterConfig config = OpenSearchReporterConfig.load(pluginConfig.jsonConfig());
        return new OpenSearchPipesReporter(pluginConfig, config);
    }

    private OpenSearchClient openSearchClient;
    private HttpClientFactory httpClientFactory = new HttpClientFactory();

    //TODO -- move these into the config and make then configurable it anyone needs these
    private String parseTimeKey = DEFAULT_PARSE_TIME_KEY;

    private String parseStatusKey = DEFAULT_PARSE_STATUS_KEY;

    private String exitValueKey = DEFAULT_EXIT_VALUE_KEY;

    private final OpenSearchReporterConfig config;
    public OpenSearchPipesReporter(PluginConfig pluginConfig, OpenSearchReporterConfig config) throws TikaConfigException {
        super(pluginConfig, config.includes(), config.excludes());
        this.config = config;
        init();
    }


    @Override
    public void report(FetchEmitTuple t, PipesResult result, long elapsed) {
        if (! accept(result.status())) {
            return;
        }

        Metadata metadata = new Metadata();
        metadata.set(parseStatusKey, result.status().name());
        metadata.set(parseTimeKey, Long.toString(elapsed));
        if (result.emitData() != null && result.emitData().getMetadataList() != null &&
                result.emitData().getMetadataList().size() > 0) {
            Metadata m = result.emitData().getMetadataList().get(0);
            if (m.get(ExternalProcess.EXIT_VALUE) != null) {
                metadata.set(exitValueKey, m.get(ExternalProcess.EXIT_VALUE));
            }
        }
        //TODO -- we're not currently doing anything with the message
        try {
            if (config.includeRouting()) {
                openSearchClient.emitDocument(t.getEmitKey().getEmitKey(),
                        t.getEmitKey().getEmitKey(), metadata);
            } else {
                openSearchClient.emitDocument(t.getEmitKey().getEmitKey(),
                        null, metadata);

            }
        } catch (IOException | TikaClientException e) {
            LOG.warn("failed to report status for '" +
                    t.getId() + "'", e);
        }
    }

    @Override
    public void report(TotalCountResult totalCountResult) {
        //
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

    public void init() throws TikaConfigException {
        HttpClientConfig http = config.httpClientConfig();
        httpClientFactory.setUserName(http.userName());
        httpClientFactory.setPassword(http.password());
        /*
            turn these back on as necessary
        httpClientFactory.setSocketTimeout(http.socketTimeout());
        httpClientFactory.setConnectTimeout(http.connectionTimeout());
        httpClientFactory.setAuthScheme(http.authScheme());
        httpClientFactory.setProxyHost(http.proxyHost());
        httpClientFactory.setProxyPort(http.proxyPort());

         */
        parseStatusKey = StringUtils.isBlank(config.keyPrefix()) ? parseStatusKey : config.keyPrefix() + parseStatusKey;
        parseTimeKey = StringUtils.isBlank(config.keyPrefix()) ? parseTimeKey : config.keyPrefix() + parseTimeKey;
        if (StringUtils.isBlank(config.openSearchUrl())) {
            throw new TikaConfigException("Must specify an open search url!");
        } else {
            openSearchClient =
                    new OpenSearchClient(config.openSearchUrl(),
                            httpClientFactory.build());
        }
    }

    @Override
    public void close() throws IOException {

    }
}
