package org.apache.tika.pipes.grpc.plugin;

import java.nio.file.Path;
import java.util.List;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginLoader;

public class GrpcPluginManager extends DefaultPluginManager {
    public GrpcPluginManager() {
    }

    public GrpcPluginManager(Path... pluginsRoots) {
        super(pluginsRoots);
    }

    public GrpcPluginManager(List<Path> pluginsRoots) {
        super(pluginsRoots);
    }

    @Override
    protected PluginDescriptorFinder createPluginDescriptorFinder() {
        return new ClasspathPluginPropertiesFinder();
    }

    @Override
    protected PluginLoader createPluginLoader() {
        return super.createPluginLoader();
    }
}
