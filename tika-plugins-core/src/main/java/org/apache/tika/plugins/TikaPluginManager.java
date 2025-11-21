package org.apache.tika.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pf4j.DefaultPluginLoader;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginFactory;
import org.pf4j.PluginLoader;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
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
        JsonNode pluginRoots = root.get("pluginRoots");
        if (pluginRoots == null) {
            throw new TikaConfigException("pluginRoots must be specified");
        }
        List<Path> roots = TikaConfigs.OBJECT_MAPPER.convertValue(pluginRoots,
                new TypeReference<List<Path>>() {});
        if (roots.isEmpty()) {
            throw new TikaConfigException("pluginRoots must not be empty");
        }
        return new TikaPluginManager(roots);
    }

    public TikaPluginManager(List<Path> pluginRoots) throws IOException{
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

        for (File f : root.toFile().listFiles()) {
            if (f.getName().endsWith(".zip")) {
                ThreadSafeUnzipper.unzipPlugin(f.toPath());
            }
        }
        LOG.debug("took {} ms to unzip/check for unzipped plugins", System.currentTimeMillis() - start);
    }

    @Override
    protected boolean isPluginValid(PluginWrapper pluginWrapper) {
        return super.isPluginValid(pluginWrapper);
    }

    @Override
    protected PluginFactory createPluginFactory() {
        return super.createPluginFactory();
    }

    @Override
    protected PluginLoader createPluginLoader() {
        return new TikaPluginLoader(this);
    }


    private static class TikaPluginLoader extends DefaultPluginLoader {

        public TikaPluginLoader(PluginManager pluginManager) {
            super(pluginManager);
        }

        @Override
        public boolean isApplicable(Path pluginPath) {
            return super.isApplicable(pluginPath);
        }

        @Override
        public ClassLoader loadPlugin(Path pluginPath, PluginDescriptor pluginDescriptor) {
            return super.loadPlugin(pluginPath, pluginDescriptor);
        }

        @Override
        protected PluginClassLoader createPluginClassLoader(Path pluginPath, PluginDescriptor pluginDescriptor) {
            return super.createPluginClassLoader(pluginPath, pluginDescriptor);
        }

        @Override
        protected void loadClasses(Path pluginPath, PluginClassLoader pluginClassLoader) {
            super.loadClasses(pluginPath, pluginClassLoader);
        }

        @Override
        protected void loadJars(Path pluginPath, PluginClassLoader pluginClassLoader) {
            super.loadJars(pluginPath, pluginClassLoader);
        }
    }
}
