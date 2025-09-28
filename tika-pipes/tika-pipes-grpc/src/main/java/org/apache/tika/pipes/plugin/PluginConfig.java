package org.apache.tika.pipes.plugin;

import java.nio.file.Paths;
import java.util.Arrays;

import org.pf4j.PluginManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.apache.tika.utils.StringUtils;

@Configuration
public class PluginConfig {
    @Value("${plugins.pluginDirs:#{null}}")
    private String pluginDirs;

    @Bean(initMethod = "startPlugins")
    public PluginManager pluginManager() {
        if (StringUtils.isBlank(pluginDirs)) {
            return new GrpcPluginManager();
        }
        return new GrpcPluginManager(Arrays
                .stream(pluginDirs.split(","))
                .map(Paths::get)
                .toList());
    }
}
