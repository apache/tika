package org.apache.tika.pipes.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.core.fetcher.FetcherManager;
import org.apache.tika.plugins.TikaConfigs;
import org.apache.tika.plugins.TikaPluginManager;

public class PluginManagerTest {

    @Test
    public void testBasic(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaConfigs tikaConfigs = TikaConfigs.load(config);
        TikaPluginManager tikaPluginManager = TikaPluginManager.load(tikaConfigs);
        FetcherManager fetcherManager = FetcherManager.load(tikaPluginManager, tikaConfigs);
        assertEquals(1, fetcherManager.getSupported().size());
        Fetcher f = fetcherManager.getFetcher();
        assertEquals("fsf", f.getExtensionConfig().id());
        assertEquals("org.apache.tika.pipes.fetcher.fs.FileSystemFetcher", f.getClass().getName());
    }
}
