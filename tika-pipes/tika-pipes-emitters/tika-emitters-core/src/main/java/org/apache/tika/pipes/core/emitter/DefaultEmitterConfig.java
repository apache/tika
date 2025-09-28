package org.apache.tika.pipes.core.emitter;

import org.apache.ignite.cache.query.annotations.QuerySqlField;

public class DefaultEmitterConfig implements EmitterConfig {
    @QuerySqlField(index = true)
    private String pluginId;
    @QuerySqlField(index = true)
    private String emitterId;
    private String configJson;

    public String getPluginId() {
        return pluginId;
    }

    public DefaultEmitterConfig setPluginId(String pluginId) {
        this.pluginId = pluginId;
        return this;
    }

    public String getEmitterId() {
        return emitterId;
    }

    public DefaultEmitterConfig setEmitterId(String emitterId) {
        this.emitterId = emitterId;
        return this;
    }

    public String getConfigJson() {
        return configJson;
    }

    public DefaultEmitterConfig setConfigJson(String configJson) {
        this.configJson = configJson;
        return this;
    }

}
