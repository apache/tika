package org.apache.tika.pipes.api.fetcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.tika.exception.TikaConfigException;

public abstract class AbstractFetcher implements Fetcher {

    private final String pluginId;
    public AbstractFetcher() throws IOException {
        Properties properties = new Properties();
        try (InputStream is = this.getClass().getResourceAsStream("/plugin.properties")) {
            properties.load(is);
        }
        pluginId = (String) properties.get("plugin.id");

    }

    @Override
    public String getPluginId() {
        return pluginId;
    }

    protected void checkPluginId(String pluginId) throws TikaConfigException {
        if (! getPluginId().equals(pluginId)) {
            throw new TikaConfigException("Plugin id mismatch: " + getPluginId() + " <> " + pluginId);
        }
    }


}
