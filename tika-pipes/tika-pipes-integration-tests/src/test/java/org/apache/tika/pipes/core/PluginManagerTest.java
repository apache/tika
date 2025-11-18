package org.apache.tika.pipes.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.fetcher.FetcherFactory;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcher;
import org.apache.tika.plugins.TikaExtensionConfigsManager;
import org.apache.tika.plugins.TikaPluginManager;

public class PluginManagerTest {

    @Test
    public void testBasic(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        try (InputStream is = Files.newInputStream(config)) {
            TikaExtensionConfigsManager tikaExtensionConfigsManager = TikaExtensionConfigsManager.load(is, true,
                    TikaExtensionConfigsManager.EXTENSION_TYPES.FETCHERS);

            TikaPluginManager tikaPluginManager = new TikaPluginManager(tikaExtensionConfigsManager.getPluginRoots());
            List<Fetcher> fetchers = tikaPluginManager.buildConfiguredExtensions(FetcherFactory.class,
                    tikaExtensionConfigsManager.get(TikaExtensionConfigsManager.EXTENSION_TYPES.FETCHERS).get());
            assertEquals(1, fetchers.size());
            Fetcher f = fetchers.get(0);
            assertEquals("fsf", f.getExtensionConfig().id());
            assertEquals(FileSystemFetcher.class, f.getClass());
        }
    }
}
