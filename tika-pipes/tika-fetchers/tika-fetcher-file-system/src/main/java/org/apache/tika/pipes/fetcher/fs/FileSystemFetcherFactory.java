package org.apache.tika.pipes.fetcher.fs;

import java.io.IOException;

import org.pf4j.Extension;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.fetcher.FetcherFactory;
import org.apache.tika.plugins.PluginConfig;

@Extension
public class FileSystemFetcherFactory implements FetcherFactory {

    @Override
    public Fetcher buildPlugin(PluginConfig pluginConfig) throws IOException, TikaConfigException {
        return FileSystemFetcher.build(pluginConfig);
    }
}
