package org.apache.tika.pipes.plugin;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginLoader;
import org.pf4j.PluginRepository;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GrpcPluginManager extends DefaultPluginManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcPluginManager.class);

    public GrpcPluginManager() {
    }

    public GrpcPluginManager(Path... pluginsRoots) {
        super(pluginsRoots);
    }

    public GrpcPluginManager(List<Path> pluginsRoots) {
        super(pluginsRoots);
    }

    @Override
    protected PluginWrapper loadPluginFromPath(Path pluginPath) {
        return super.loadPluginFromPath(pluginPath);
    }

    @Override
    protected PluginDescriptorFinder createPluginDescriptorFinder() {
        return new ClasspathPluginPropertiesFinder();
    }

    @Override
    protected PluginLoader createPluginLoader() {
        PluginLoader pluginLoader = super.createPluginLoader();
        return new PluginLoader() {
            @Override
            public boolean isApplicable(Path pluginPath) {
                return pluginLoader.isApplicable(pluginPath) && !isCoreLibrary(pluginPath);
            }

            @Override
            public ClassLoader loadPlugin(Path pluginPath, PluginDescriptor pluginDescriptor) {
                ClassLoader classLoader = pluginLoader.loadPlugin(pluginPath, pluginDescriptor);
                log.info("Loaded plugin: pluginDescriptor={}, pluginPath={}", pluginDescriptor, pluginPath);
                return classLoader;
            }
        };
    }

    @Override
    protected PluginRepository createPluginRepository() {
        PluginRepository pluginRepository = super.createPluginRepository();
        return new PluginRepository() {
            @Override
            public List<Path> getPluginPaths() {
                return pluginRepository.getPluginPaths().stream()
                            .filter(path -> !isCoreLibrary(path))
                            .collect(Collectors.toList());
            }

            @Override
            public boolean deletePluginPath(Path pluginPath) {
                return false;
            }
        };
    }

    /**
     * If the path ends with -core, it's a core library.
     * It's not a plugin, ignore it.
     */
    private static boolean isCoreLibrary(Path path) {
        return StringUtils.endsWith(path.toString(), "-core");
    }

    @Override
    public void loadPlugins() {
        super.loadPlugins();
        LOGGER.info("Loaded {} plugins", getPlugins().size());
    }

    @Override
    public void startPlugins() {
        if (getPlugins().isEmpty()) {
            loadPlugins();
        }
        super.startPlugins();
        for (PluginWrapper plugin : getStartedPlugins()) {
            LOGGER.info("Add-in " + plugin.getPluginId() + " : " + plugin.getDescriptor() + " has started.");
        }
    }
}
