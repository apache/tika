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
package org.apache.tika.pipes.iterator.solr;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.LBHttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.CursorMarkParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorBaseConfig;
import org.apache.tika.pipes.pipesiterator.PipesIteratorBase;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.utils.StringUtils;

/**
 * Iterates through results from a Solr query.
 */
public class SolrPipesIterator extends PipesIteratorBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(SolrPipesIterator.class);

    private SolrPipesIteratorConfig config;
    private HttpClientFactory httpClientFactory;

    private SolrPipesIterator(ExtensionConfig pluginConfig) {
        super(pluginConfig);
    }

    public static SolrPipesIterator build(ExtensionConfig extensionConfig) throws IOException, TikaConfigException {
        SolrPipesIterator iterator = new SolrPipesIterator(extensionConfig);
        iterator.configure();
        return iterator;
    }

    private void configure() throws IOException, TikaConfigException {
        config = SolrPipesIteratorConfig.load(pluginConfig.jsonConfig());

        // Validation
        if (StringUtils.isBlank(config.getSolrCollection())) {
            throw new TikaConfigException("solrCollection must not be empty");
        }
        if (StringUtils.isBlank(config.getIdField())) {
            throw new TikaConfigException("idField must not be empty");
        }
        if (StringUtils.isBlank(config.getParsingIdField())) {
            throw new TikaConfigException("parsingIdField must not be empty");
        }
        if (StringUtils.isBlank(config.getFailCountField())) {
            throw new TikaConfigException("failCountField must not be empty");
        }
        if (StringUtils.isBlank(config.getSizeFieldName())) {
            throw new TikaConfigException("sizeFieldName must not be empty");
        }
        List<String> solrUrls = config.getSolrUrls() != null ? config.getSolrUrls() : Collections.emptyList();
        List<String> solrZkHosts = config.getSolrZkHosts() != null ? config.getSolrZkHosts() : Collections.emptyList();
        if (solrUrls.isEmpty() && solrZkHosts.isEmpty()) {
            throw new TikaConfigException("expected either param solrUrls or param solrZkHosts, but neither was specified");
        }
        if (!solrUrls.isEmpty() && !solrZkHosts.isEmpty()) {
            throw new TikaConfigException("expected either param solrUrls or param solrZkHosts, but both were specified");
        }

        // Initialize HTTP client factory
        httpClientFactory = new HttpClientFactory();
        if (!StringUtils.isBlank(config.getUserName())) {
            httpClientFactory.setUserName(config.getUserName());
        }
        if (!StringUtils.isBlank(config.getPassword())) {
            httpClientFactory.setPassword(config.getPassword());
        }
        if (!StringUtils.isBlank(config.getAuthScheme())) {
            httpClientFactory.setAuthScheme(config.getAuthScheme());
        }
        if (!StringUtils.isBlank(config.getProxyHost())) {
            httpClientFactory.setProxyHost(config.getProxyHost());
        }
        if (config.getProxyPort() > 0) {
            httpClientFactory.setProxyPort(config.getProxyPort());
        }
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        PipesIteratorBaseConfig baseConfig = config.getBaseConfig();
        String fetcherId = baseConfig.fetcherId();
        String emitterId = baseConfig.emitterId();

        try (SolrClient solrClient = createSolrClient()) {
            int fileCount = 0;

            SolrQuery query = new SolrQuery();
            query.set("q", "*:*");
            query.setRows(config.getRows());

            Set<String> allFields = new HashSet<>();
            allFields.add("id");
            allFields.add(config.getIdField());
            allFields.add(config.getParsingIdField());
            allFields.add(config.getFailCountField());
            allFields.add(config.getSizeFieldName());
            List<String> additionalFields = config.getAdditionalFields() != null ? config.getAdditionalFields() : Collections.emptyList();
            allFields.addAll(additionalFields);

            query.setFields(allFields.toArray(new String[]{}));
            query.setSort(SolrQuery.SortClause.asc(config.getParsingIdField()));
            query.addSort(SolrQuery.SortClause.asc("id"));
            List<String> filters = config.getFilters() != null ? config.getFilters() : Collections.emptyList();
            query.setFilterQueries(filters.toArray(new String[]{}));

            HandlerConfig handlerConfig = baseConfig.handlerConfig();

            String cursorMark = CursorMarkParams.CURSOR_MARK_START;
            boolean done = false;
            while (!done) {
                query.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
                QueryResponse qr = solrClient.query(config.getSolrCollection(), query);
                long totalToFetch = qr
                        .getResults()
                        .getNumFound();
                String nextCursorMark = qr.getNextCursorMark();
                LOGGER.info("Query to fetch files to parse collection={}, q={}, onCount={}, totalCount={}", config.getSolrCollection(), query, fileCount, totalToFetch);
                for (SolrDocument sd : qr.getResults()) {
                    ++fileCount;
                    String fetchKey = (String) sd.getFieldValue(config.getIdField());
                    String emitKey = (String) sd.getFieldValue(config.getIdField());
                    Metadata metadata = new Metadata();
                    for (String nextField : allFields) {
                        metadata.add(nextField, (String) sd.getFieldValue(nextField));
                    }
                    LOGGER.info("iterator doc: {}, idField={}, fetchKey={}", sd, config.getIdField(), fetchKey);
                    ParseContext parseContext = new ParseContext();
                    parseContext.set(HandlerConfig.class, handlerConfig);
                    tryToAdd(new FetchEmitTuple(fetchKey, new FetchKey(fetcherId, fetchKey), new EmitKey(emitterId, emitKey), new Metadata(), parseContext,
                            baseConfig.onParseException()));
                }
                if (cursorMark.equals(nextCursorMark)) {
                    done = true;
                }
                cursorMark = nextCursorMark;
            }
        } catch (SolrServerException | TikaConfigException e) {
            LOGGER.error("Could not iterate through solr", e);
        }
    }

    private SolrClient createSolrClient() throws TikaConfigException {
        List<String> solrUrls = config.getSolrUrls() != null ? config.getSolrUrls() : Collections.emptyList();
        List<String> solrZkHosts = config.getSolrZkHosts() != null ? config.getSolrZkHosts() : Collections.emptyList();

        if (solrUrls.isEmpty()) {
            //TODO -- there's more that we need to pass through, including ssl etc.
            Http2SolrClient.Builder http2SolrClientBuilder = new Http2SolrClient.Builder();
            if (!StringUtils.isBlank(httpClientFactory.getUserName())) {
                http2SolrClientBuilder.withBasicAuthCredentials(httpClientFactory.getUserName(), httpClientFactory.getPassword());
            }
            http2SolrClientBuilder
                    .withRequestTimeout(httpClientFactory.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .withConnectionTimeout(config.getConnectionTimeout(), TimeUnit.MILLISECONDS);


            Http2SolrClient http2SolrClient = http2SolrClientBuilder.build();
            return new CloudSolrClient.Builder(solrZkHosts, Optional.ofNullable(config.getSolrZkChroot()))
                    .withHttpClient(http2SolrClient)
                    .build();

        }
        return new LBHttpSolrClient.Builder()
                .withConnectionTimeout(config.getConnectionTimeout())
                .withSocketTimeout(config.getSocketTimeout())
                .withHttpClient(httpClientFactory.build())
                .withBaseSolrUrls(solrUrls.toArray(new String[]{}))
                .build();
    }
}
