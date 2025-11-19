package org.apache.tika.plugins;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private final TikaConfigs tikaConfigs;

    public static TikaPluginManager load(Path path) throws TikaConfigException, IOException {
        return new TikaPluginManager(TikaConfigs.load(path));
    }

    public static TikaPluginManager load(InputStream is) throws TikaConfigException, IOException {
        return new TikaPluginManager(TikaConfigs.load(is));
    }

    public TikaPluginManager(TikaConfigs tikaConfigs) throws IOException {
        super(tikaConfigs.getPluginRoots());
        this.tikaConfigs = tikaConfigs;
        init();
    }

    public TikaPluginManager(List<Path> pluginRoots) throws IOException{
        super(pluginRoots);
        this.tikaConfigs = null;
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

    public <E extends TikaExtension, F extends TikaExtensionFactory<E>> List<E> buildConfiguredExtensions(Class<F> clazz)
            throws TikaConfigException, IOException {

        List<F> factories = getExtensions(clazz);
        List<E> extensions = new ArrayList<>();
        //in some circumstances (when the plugins are available on the classpath and in a plugin dir), factories are loaded twice
        Set<String> seen = new HashSet<>();
        for (TikaExtensionFactory<E> factory : factories) {
            if (seen.contains(factory.getExtensionName())) {
                continue;
            }
            seen.add(factory.getExtensionName());
            String extensionName = factory.getExtensionName();
            for (ExtensionConfig extensionConfig : tikaConfigs.getExtensionConfigs().getByExtensionName(extensionName)) {
                E extension = factory.buildExtension(extensionConfig);
                extensions.add(extension);
            }
        }
        return extensions;
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
