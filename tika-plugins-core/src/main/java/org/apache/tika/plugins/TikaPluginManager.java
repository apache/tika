package org.apache.tika.plugins;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.pf4j.DefaultPluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;

public class TikaPluginManager extends DefaultPluginManager {


    private static final Logger LOG = LoggerFactory.getLogger(TikaPluginManager.class);

    public static TikaPluginManager load(Path p) throws TikaConfigException, IOException {
        try (InputStream is = Files.newInputStream(p)) {
            return load(is);
        }
    }

    public static TikaPluginManager load(InputStream is) throws TikaConfigException, IOException {
        return load(TikaConfigs.load(is));
    }

    public static TikaPluginManager load(TikaConfigs tikaConfigs) throws TikaConfigException, IOException {
        JsonNode root = tikaConfigs.getRoot();
        JsonNode pluginRoots = root.get("plugin-roots");
        if (pluginRoots == null) {
            throw new TikaConfigException("plugin-roots must be specified");
        }
        List<Path> roots = TikaConfigs.OBJECT_MAPPER.convertValue(pluginRoots, new TypeReference<List<Path>>() {
        });
        if (roots.isEmpty()) {
            throw new TikaConfigException("plugin-roots must not be empty");
        }
        return new TikaPluginManager(roots);
    }

    public TikaPluginManager(List<Path> pluginRoots) throws IOException {
        super(pluginRoots);
        init();
    }

    private void init() throws IOException {
        for (Path root : pluginsRoots) {
            unzip(root);
        }
    }

    private void unzip(Path root) throws IOException {
        long start = System.currentTimeMillis();
        if (!Files.isDirectory(root)) {
            return;
        }

        for (File f : root
                .toFile()
                .listFiles()) {
            if (f
                    .getName()
                    .endsWith(".zip")) {
                ThreadSafeUnzipper.unzipPlugin(f.toPath());
            }
        }
        LOG.debug("took {} ms to unzip/check for unzipped plugins", System.currentTimeMillis() - start);
    }
}
