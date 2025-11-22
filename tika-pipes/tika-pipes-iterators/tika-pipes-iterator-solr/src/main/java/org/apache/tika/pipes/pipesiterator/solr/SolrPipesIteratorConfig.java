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
package org.apache.tika.pipes.pipesiterator.solr;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorBaseConfig;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorConfig;

public class SolrPipesIteratorConfig implements PipesIteratorConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static SolrPipesIteratorConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json,
                    SolrPipesIteratorConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse SolrPipesIteratorConfig from JSON", e);
        }
    }

    private String solrCollection;
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
    private String userName;
    private String password;
    private String authScheme;
    private String proxyHost;
    private int proxyPort = 0;
    private PipesIteratorBaseConfig baseConfig = null;

    public String getSolrCollection() {
        return solrCollection;
    }

    public List<String> getSolrUrls() {
        return solrUrls;
    }

    public List<String> getSolrZkHosts() {
        return solrZkHosts;
    }

    public String getSolrZkChroot() {
        return solrZkChroot;
    }

    public List<String> getFilters() {
        return filters;
    }

    public String getIdField() {
        return idField;
    }

    public String getParsingIdField() {
        return parsingIdField;
    }

    public String getFailCountField() {
        return failCountField;
    }

    public String getSizeFieldName() {
        return sizeFieldName;
    }

    public List<String> getAdditionalFields() {
        return additionalFields;
    }

    public int getRows() {
        return rows;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getAuthScheme() {
        return authScheme;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    @Override
    public PipesIteratorBaseConfig getBaseConfig() {
        return baseConfig;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof SolrPipesIteratorConfig that)) {
            return false;
        }

        return rows == that.rows &&
                connectionTimeout == that.connectionTimeout &&
                socketTimeout == that.socketTimeout &&
                proxyPort == that.proxyPort &&
                Objects.equals(solrCollection, that.solrCollection) &&
                Objects.equals(solrUrls, that.solrUrls) &&
                Objects.equals(solrZkHosts, that.solrZkHosts) &&
                Objects.equals(solrZkChroot, that.solrZkChroot) &&
                Objects.equals(filters, that.filters) &&
                Objects.equals(idField, that.idField) &&
                Objects.equals(parsingIdField, that.parsingIdField) &&
                Objects.equals(failCountField, that.failCountField) &&
                Objects.equals(sizeFieldName, that.sizeFieldName) &&
                Objects.equals(additionalFields, that.additionalFields) &&
                Objects.equals(userName, that.userName) &&
                Objects.equals(password, that.password) &&
                Objects.equals(authScheme, that.authScheme) &&
                Objects.equals(proxyHost, that.proxyHost) &&
                Objects.equals(baseConfig, that.baseConfig);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(solrCollection);
        result = 31 * result + Objects.hashCode(solrUrls);
        result = 31 * result + Objects.hashCode(solrZkHosts);
        result = 31 * result + Objects.hashCode(solrZkChroot);
        result = 31 * result + Objects.hashCode(filters);
        result = 31 * result + Objects.hashCode(idField);
        result = 31 * result + Objects.hashCode(parsingIdField);
        result = 31 * result + Objects.hashCode(failCountField);
        result = 31 * result + Objects.hashCode(sizeFieldName);
        result = 31 * result + Objects.hashCode(additionalFields);
        result = 31 * result + rows;
        result = 31 * result + connectionTimeout;
        result = 31 * result + socketTimeout;
        result = 31 * result + Objects.hashCode(userName);
        result = 31 * result + Objects.hashCode(password);
        result = 31 * result + Objects.hashCode(authScheme);
        result = 31 * result + Objects.hashCode(proxyHost);
        result = 31 * result + proxyPort;
        result = 31 * result + Objects.hashCode(baseConfig);
        return result;
    }
}
