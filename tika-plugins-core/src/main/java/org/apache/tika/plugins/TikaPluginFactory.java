package org.apache.tika.plugins;

import java.io.IOException;

import org.pf4j.ExtensionPoint;

import org.apache.tika.exception.TikaConfigException;

public interface TikaPluginFactory<T extends TikaPlugin> extends ExtensionPoint {

    T buildPlugin(PluginConfig pluginConfig) throws IOException, TikaConfigException;
}
