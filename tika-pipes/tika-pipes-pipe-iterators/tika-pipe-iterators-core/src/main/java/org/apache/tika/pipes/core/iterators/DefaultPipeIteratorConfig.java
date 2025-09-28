package org.apache.tika.pipes.core.iterators;

import org.apache.ignite.cache.query.annotations.QuerySqlField;

public class DefaultPipeIteratorConfig implements PipeIteratorConfig {
    @QuerySqlField(index = true)
    private String pluginId;
    @QuerySqlField(index = true)
    private String pipeIteratorId;
    private String configJson;

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public DefaultPipeIteratorConfig setPluginId(String pluginId) {
        this.pluginId = pluginId;
        return this;
    }

    @Override
    public String getPipeIteratorId() {
        return pipeIteratorId;
    }

    @Override
    public DefaultPipeIteratorConfig setPipeIteratorId(String pipeIteratorId) {
        this.pipeIteratorId = pipeIteratorId;
        return this;
    }

    @Override
    public String getConfigJson() {
        return configJson;
    }

    @Override
    public DefaultPipeIteratorConfig setConfigJson(String configJson) {
        this.configJson = configJson;
        return this;
    }
}
