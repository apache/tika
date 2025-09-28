package org.apache.tika.pipes.fetchers.core;

import java.io.Serializable;

import org.pf4j.ExtensionPoint;

public interface FetcherConfig extends Serializable, ExtensionPoint {
    String getPluginId();
    FetcherConfig setPluginId(String pluginId);
    String getFetcherId();
    FetcherConfig setFetcherId(String fetcherId);
    String getConfigJson();
    FetcherConfig setConfigJson(String config);
}
