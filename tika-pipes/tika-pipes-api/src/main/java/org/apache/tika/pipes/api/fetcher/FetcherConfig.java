package org.apache.tika.pipes.api.fetcher;

import java.io.Serializable;

public interface FetcherConfig extends Serializable {

    String getPluginId();
    FetcherConfig setPluginId(String pluginId);
    String getConfigJson();
    FetcherConfig setConfigJson(String config);
}
