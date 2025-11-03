package org.apache.tika.pipes.api;

import java.io.IOException;

import org.pf4j.ExtensionPoint;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.plugins.PluginConfig;
import org.apache.tika.plugins.TikaPlugin;

public interface TikaPluginFactory<T extends TikaPlugin> extends ExtensionPoint {

    T buildPlugin(PluginConfig pluginConfig) throws IOException, TikaConfigException;
}
