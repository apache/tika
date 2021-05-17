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
package org.apache.tika.pipes.solrtest;

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.LBHttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.CursorMarkParams;
import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.pipesiterator.PipesIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iterates through results from a Solr query.
 */
public class SolrPipesIterator extends PipesIterator implements Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SolrPipesIterator.class);

    private String solrCollection;
    /**
     * You can specify solrUrls, or you can specify solrZkHosts and use use zookeeper to determine the solr server urls.
     */
    private List<String> solrUrls = Collections.emptyList();
    private List<String> solrZkHosts = Collections.emptyList();
    private String solrZkChroot;
    private List<String> filters = Collections.emptyList();
    private String idField;
    private String parsingIdField;
    private String failCountField;
    private String sizeFieldName;
    private List<String> additionalFields = Collections.emptyList();
    private int rows = 5000;
    private int connectionTimeout = 10000;
    private int socketTimeout = 60000;

    private final HttpClientFactory httpClientFactory;

    public SolrPipesIterator() throws TikaConfigException {
        httpClientFactory = new HttpClientFactory();
    }

    @Field
    public void setSolrZkHosts(List<String> solrZkHosts) {
        this.solrZkHosts = solrZkHosts;
    }

    @Field
    public void setSolrZkChroot(String solrZkChroot) {
        this.solrZkChroot = solrZkChroot;
    }

    @Field
    public void setSolrCollection(String solrCollection) {
        this.solrCollection = solrCollection;
    }

    @Field
    public void setSolrUrls(List<String> solrUrls) {
        this.solrUrls = solrUrls;
    }

    @Field
    public void setFilters(List<String> filters) {
        this.filters = filters;
    }

    @Field
    public void setAdditionalFields(List<String> additionalFields) {
        this.additionalFields = additionalFields;
    }

    @Field
    public void setIdField(String idField) {
        this.idField = idField;
    }

    @Field
    public void setParsingIdField(String parsingIdField) {
        this.parsingIdField = parsingIdField;
    }

    @Field
    public void setFailCountField(String failCountField) {
        this.failCountField = failCountField;
    }

    @Field
    public void setSizeFieldName(String sizeFieldName) {
        this.sizeFieldName = sizeFieldName;
    }

    @Field
    public void setRows(int rows) {
        this.rows = rows;
    }

    @Field
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    @Field
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    //TODO -- add other httpclient configurations??
    @Field
    public void setUserName(String userName) {
        httpClientFactory.setUserName(userName);
    }

    @Field
    public void setPassword(String password) {
        httpClientFactory.setPassword(password);
    }

    @Field
    public void setAuthScheme(String authScheme) {
        httpClientFactory.setAuthScheme(authScheme);
    }

    @Field
    public void setProxyHost(String proxyHost) {
        httpClientFactory.setProxyHost(proxyHost);
    }

    @Field
    public void setProxyPort(int proxyPort) {
        httpClientFactory.setProxyPort(proxyPort);
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        String fetcherName = getFetcherName();
        String emitterName = getEmitterName();

        try (SolrClient solrClient = createSolrClient()) {
            int fileCount = 0;

            SolrQuery query = new SolrQuery();
            query.set("q", "*:*");
            query.setRows(rows);

            Set<String> allFields = new HashSet<>();
            allFields.add("id");
            allFields.add(idField);
            allFields.add(parsingIdField);
            allFields.add(failCountField);
            allFields.add(sizeFieldName);
            allFields.addAll(additionalFields);

            query.setFields(allFields.toArray(new String[]{}));
            query.setSort(SolrQuery.SortClause.asc(parsingIdField));
            query.addSort(SolrQuery.SortClause.asc("id"));
            query.setFilterQueries(filters.toArray(new String[]{}));

            HandlerConfig handlerConfig = getHandlerConfig();

            String cursorMark = CursorMarkParams.CURSOR_MARK_START;
            boolean done = false;
            while (!done) {
                query.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
                QueryResponse qr = solrClient.query(solrCollection, query);
                long totalToFetch = qr.getResults().getNumFound();
                String nextCursorMark = qr.getNextCursorMark();
                LOGGER.info("Query to fetch files to parse collection={}, q={}, onCount={}, totalCount={}", solrCollection, query, fileCount, totalToFetch);
                for (SolrDocument sd : qr.getResults()) {
                    ++fileCount;
                    String fetchKey = (String) sd.getFieldValue(idField);
                    String emitKey = (String) sd.getFieldValue(idField);
                    Metadata metadata = new Metadata();
                    for (String nextField : allFields) {
                        metadata.add(nextField, (String) sd.getFieldValue(nextField));
                    }
                    LOGGER.info("iterator doc: {}, idField={}, fetchKey={}", sd, idField, fetchKey);
                    tryToAdd(new FetchEmitTuple(fetchKey,
                            new FetchKey(fetcherName, fetchKey),
                            new EmitKey(emitterName, emitKey),
                            new Metadata(),
                            handlerConfig,
                            getOnParseException()));
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
        if (solrUrls == null || solrUrls.isEmpty()) {
            return new CloudSolrClient.Builder(solrZkHosts, Optional.ofNullable(solrZkChroot))
                    .withHttpClient(httpClientFactory.build())
                    .withConnectionTimeout(connectionTimeout)
                    .withSocketTimeout(socketTimeout)
                    .build();
        }
        return new LBHttpSolrClient.Builder()
                .withConnectionTimeout(connectionTimeout)
                .withSocketTimeout(socketTimeout)
                .withHttpClient(httpClientFactory.build())
                .withBaseSolrUrls(solrUrls.toArray(new String[]{})).build();
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        super.checkInitialization(problemHandler);
        mustNotBeEmpty("solrCollection", this.solrCollection);
        mustNotBeEmpty("urlFieldName", this.idField);
        mustNotBeEmpty("parsingIdField", this.parsingIdField);
        mustNotBeEmpty("failCountField", this.failCountField);
        mustNotBeEmpty("sizeFieldName", this.sizeFieldName);
        if ((this.solrUrls == null || this.solrUrls.isEmpty()) && (this.solrZkHosts == null || this.solrZkHosts.isEmpty())) {
            throw new IllegalArgumentException("expected either param solrUrls or param solrZkHosts, but neither was specified");
        }
        if (this.solrUrls != null && !this.solrUrls.isEmpty() && this.solrZkHosts != null && !this.solrZkHosts.isEmpty()) {
            throw new IllegalArgumentException("expected either param solrUrls or param solrZkHosts, but both were specified");
        }
    }
}
