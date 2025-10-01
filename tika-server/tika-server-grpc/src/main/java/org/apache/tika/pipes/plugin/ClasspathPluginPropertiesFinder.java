package org.apache.tika.pipes.plugin;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.pf4j.PropertiesPluginDescriptorFinder;

public class ClasspathPluginPropertiesFinder extends PropertiesPluginDescriptorFinder {
    @Override
    protected Path getPropertiesPath(Path pluginPath, String propertiesFileName) {
        Path propertiesPath = super.getPropertiesPath(pluginPath, propertiesFileName);
        if (!propertiesPath.toFile().exists()) {
            // If in development mode, we can also pull the plugin.properties from $pluginDir/src/main/resources/plugin.properties
            propertiesPath = Paths.get(propertiesPath.getParent().toAbsolutePath().toString(), "src", "main", "resources", "plugin.properties");
        }
        return propertiesPath;
    }
}
