package org.apache.tika.plugins;

public class AbstractTikaPlugin implements TikaPlugin {

    protected final PluginConfig pluginConfig;

    public AbstractTikaPlugin(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }
}
