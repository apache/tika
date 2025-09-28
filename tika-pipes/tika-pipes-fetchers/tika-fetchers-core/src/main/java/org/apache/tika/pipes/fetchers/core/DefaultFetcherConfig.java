package org.apache.tika.pipes.fetchers.core;

import org.apache.ignite.cache.query.annotations.QuerySqlField;

public class DefaultFetcherConfig implements FetcherConfig {
    @QuerySqlField(index = true)
    private String pluginId;
    @QuerySqlField(index = true)
    private String fetcherId;

    private String configJson;

    public String getPluginId() {
        return pluginId;
    }

    public DefaultFetcherConfig setPluginId(String pluginId) {
        this.pluginId = pluginId;
        return this;
    }

    public String getFetcherId() {
        return fetcherId;
    }

    public DefaultFetcherConfig setFetcherId(String fetcherId) {
        this.fetcherId = fetcherId;
        return this;
    }

    public String getConfigJson() {
        return configJson;
    }

    public DefaultFetcherConfig setConfigJson(String configJson) {
        this.configJson = configJson;
        return this;
    }
}
