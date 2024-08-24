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
