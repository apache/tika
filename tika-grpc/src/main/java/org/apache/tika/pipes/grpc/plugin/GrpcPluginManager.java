/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.pipes.grpc.plugin;

import java.nio.file.Path;
import java.util.List;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginLoader;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.grpc.exception.TikaGrpcException;

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
    protected PluginDescriptorFinder createPluginDescriptorFinder() {
        return new ClasspathPluginPropertiesFinder();
    }

    @Override
    protected PluginLoader createPluginLoader() {
        return super.createPluginLoader();
    }

    @Override
    public void loadPlugins() {
        super.loadPlugins();
        LOGGER.info("Loaded {} plugins", getPlugins().size());
    }

    @Override
    public void startPlugins() {
        super.startPlugins();
        for (PluginWrapper plugin : getStartedPlugins()) {
            LOGGER.info("Add-in " + plugin.getPluginId() + " : " + plugin.getDescriptor() + " has started.");
            checkFetcherExtensions(plugin);
        }
    }

    private void checkFetcherExtensions(PluginWrapper plugin) {
        for (Class<?> extensionClass : getExtensionClasses(Fetcher.class, plugin.getPluginId())) {
            if (!Fetcher.class.isAssignableFrom(extensionClass)) {
                throw new TikaGrpcException("Something is wrong with the classpath. " + Fetcher.class.getName() + " should be assignable from " + extensionClass.getName() + ". Did tika-core accidentally get in your plugin lib?");
            }
            LOGGER.info("    Extension " + extensionClass + " has been registered to plugin " + plugin.getPluginId());
        }
    }
}
