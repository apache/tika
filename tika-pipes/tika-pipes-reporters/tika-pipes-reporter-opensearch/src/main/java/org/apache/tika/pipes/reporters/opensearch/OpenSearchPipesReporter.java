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

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.client.TikaClientException;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.ExternalProcess;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.PipesReporter;
import org.apache.tika.pipes.PipesResult;
import org.apache.tika.utils.StringUtils;

/**
 * As of the 2.5.0 release, this is ALPHA version.  There may be breaking changes
 * in the future.
 */
public class OpenSearchPipesReporter extends PipesReporter implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchPipesReporter.class);

    public static String DEFAULT_PARSE_TIME_KEY = "parse_time_ms";
    public static String DEFAULT_PARSE_STATUS_KEY = "parse_status";
    public static String DEFAULT_EXIT_VALUE_KEY = "exit_value";

    private OpenSearchClient openSearchClient;
    private String openSearchUrl;
    private HttpClientFactory httpClientFactory = new HttpClientFactory();

    private Set<String> includeStatus = new HashSet<>();

    private Set<String> excludeStatus = new HashSet<>();

    private String parseTimeKey = DEFAULT_PARSE_TIME_KEY;

    private String parseStatusKey = DEFAULT_PARSE_STATUS_KEY;

    private String exitValueKey = DEFAULT_EXIT_VALUE_KEY;

    private boolean includeRouting = false;


    @Override
    public void report(FetchEmitTuple t, PipesResult result, long elapsed) {
        if (! shouldReport(result)) {
            return;
        }

        Metadata metadata = new Metadata();
        metadata.set(parseStatusKey, result.getStatus().name());
        metadata.set(parseTimeKey, Long.toString(elapsed));
        if (result.getEmitData() != null && result.getEmitData().getMetadataList() != null &&
                result.getEmitData().getMetadataList().size() > 0) {
            Metadata m = result.getEmitData().getMetadataList().get(0);
            if (m.get(ExternalProcess.EXIT_VALUE) != null) {
                metadata.set(exitValueKey, m.get(ExternalProcess.EXIT_VALUE));
            }
        }
        //TODO -- we're not currently doing anything with the message
        try {
            if (includeRouting) {
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
    public void error(Throwable t) {
        LOG.error("crashed", t);
    }

    @Override
    public void error(String msg) {
        LOG.error("crashed {}", msg);
    }

    private boolean shouldReport(PipesResult result) {
        if (includeStatus.size() > 0) {
            if (includeStatus.contains(result.getStatus().name())) {
                return true;
            }
            return false;
        }
        if (excludeStatus.size() > 0 && excludeStatus.contains(result.getStatus().name())) {
            return false;
        }
        return true;
    }

    @Field
    public void setConnectionTimeout(int connectionTimeout) {
        httpClientFactory.setConnectTimeout(connectionTimeout);
    }

    @Field
    public void setSocketTimeout(int socketTimeout) {
        httpClientFactory.setSocketTimeout(socketTimeout);
    }

    //this is the full url, including the collection, e.g. https://localhost:9200/my-collection
    @Field
    public void setOpenSearchUrl(String openSearchUrl) {
        this.openSearchUrl = openSearchUrl;
    }

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

    @Field
    public void setIncludeStatuses(List<String> statusList) {
        includeStatus.addAll(statusList);
    }

    @Field
    public void setExcludeStatuses(List<String> statusList) {
        excludeStatus.addAll(statusList);
    }

    @Field
    public void setIncludeRouting(boolean includeRouting) {
        this.includeRouting = includeRouting;
    }
    /**
     * This prefixes the keys before sending them to OpenSearch.
     * For example, "pdfinfo_", would have this reporter sending
     * "pdfinfo_status" and "pdfinfo_parse_time" to OpenSearch.
     * @param keyPrefix
     */
    @Field
    public void setKeyPrefix(String keyPrefix) {
        this.parseStatusKey = keyPrefix + DEFAULT_PARSE_STATUS_KEY;
        this.parseTimeKey = keyPrefix + DEFAULT_PARSE_TIME_KEY;
        this.exitValueKey = keyPrefix + DEFAULT_EXIT_VALUE_KEY;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        if (StringUtils.isBlank(openSearchUrl)) {
            throw new TikaConfigException("Must specify an open search url!");
        } else {
            openSearchClient =
                    new OpenSearchClient(openSearchUrl,
                            httpClientFactory.build());
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        mustNotBeEmpty("openSearchUrl", this.openSearchUrl);
        for (String status : includeStatus) {
            if (excludeStatus.contains(status)) {
                throw new TikaConfigException("Can't have a status in both include and exclude: " +
                        status);
            }
        }
        Set<String> statuses = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (PipesResult.STATUS status : PipesResult.STATUS.values()) {
            statuses.add(status.name());
            i++;
            if (i > 1) {
                sb.append(", ");
            }
            sb.append(status.name());
        }
        for (String include : includeStatus) {
            if (! statuses.contains(include)) {
                throw new TikaConfigException("I regret I don't recognize '" +
                        include + "' in the include list. " +
                        "I recognize: " + sb.toString());
            }
        }
        for (String exclude : excludeStatus) {
            if (! statuses.contains(exclude)) {
                throw new TikaConfigException("I regret I don't recognize '" +
                        exclude + "' in the exclude list. " +
                        "I recognize: " + sb.toString());
            }
        }
    }
}
