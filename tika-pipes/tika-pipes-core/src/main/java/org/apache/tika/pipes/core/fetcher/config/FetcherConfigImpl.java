package org.apache.tika.pipes.core.fetcher.config;

import org.apache.tika.pipes.api.fetcher.FetcherConfig;

public class FetcherConfigImpl implements FetcherConfig {

    private String plugId;
    private String configJson;

    public FetcherConfigImpl(String plugId, String configJson) {
        this.plugId = plugId;
        this.configJson = configJson;
    }
    @Override
    public String getPluginId() {
        return plugId;
    }

    @Override
    public FetcherConfig setPluginId(String pluginId) {
        this.plugId = pluginId;
        return this;
    }

    @Override
    public String getConfigJson() {
        return configJson;
    }

    @Override
    public FetcherConfig setConfigJson(String configJson) {
        this.configJson = configJson;
        return this;
    }
}
