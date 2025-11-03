package org.apache.tika.pipes.emitter.fs;

import java.io.IOException;

import org.pf4j.Extension;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.api.emitter.EmitterFactory;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.fetcher.FetcherFactory;
import org.apache.tika.plugins.PluginConfig;

@Extension
public class FileSystemEmitterFactory implements EmitterFactory {

    @Override
    public Emitter buildPlugin(PluginConfig pluginConfig) throws IOException, TikaConfigException {
        return FileSystemEmitter.build(pluginConfig);
    }
}
