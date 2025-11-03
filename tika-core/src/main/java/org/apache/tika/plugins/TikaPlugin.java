package org.apache.tika.plugins;

/**
 * This is an interface for plugins created by TikaPluginFactory
 */
public interface TikaPlugin {
    PluginConfig getPluginConfig();
}
