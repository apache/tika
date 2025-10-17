package org.apache.tika.pipes.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.pipes.api.fetcher.FetcherConfig;
import org.apache.tika.pipes.core.fetcher.config.FetcherConfigImpl;

public class PipesPluginsConfig {

    public static PipesPluginsConfig load(InputStream is) throws IOException {
        JsonNode root = new ObjectMapper().readTree(new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)));
        JsonNode plugins = root.get("pipesPluginsConfig");
        Map<String, FetcherConfig> fetcherMap = new HashMap<>();
        if (plugins.has("fetchers")) {
            JsonNode fetchers = plugins.get("fetchers");
            Iterator<String> it = fetchers.fieldNames();
            while (it.hasNext()) {
                String pluginId = it.next();
                JsonNode fetcherConfig = fetchers.get(pluginId);
                fetcherMap.put(pluginId, new FetcherConfigImpl(pluginId, fetcherConfig.toString()));
            }
        }
        return new PipesPluginsConfig(fetcherMap);
    }

    private final Map<String, FetcherConfig> fetcherMap;

    private PipesPluginsConfig(Map<String, FetcherConfig> fetcherMap) {
        this.fetcherMap = fetcherMap;
    }

    public Optional<FetcherConfig> getFetcherConfig(String pluginId) {
        return Optional.ofNullable(fetcherMap.get(pluginId));
    }
}
