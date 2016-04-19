/**
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
package org.apache.tika;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A wrapper around a {@link ClassLoader} that logs all
 *  the Resources loaded through it.
 * Used to check that a specific ClassLoader was used
 *  when unit testing
 */
public class ResourceLoggingClassLoader extends ClassLoader {
    private Map<String,List<URL>> loadedResources = new HashMap<String, List<URL>>();
    
    public ResourceLoggingClassLoader(ClassLoader realClassloader) {
        super(realClassloader);
    }
    
    private List<URL> fetchRecord(String name) {
        List<URL> alreadyLoaded = loadedResources.get(name);
        if (alreadyLoaded == null) {
            alreadyLoaded = new ArrayList<URL>();
            loadedResources.put(name, alreadyLoaded);
        }
        return alreadyLoaded;
    }
    
    @Override
    public URL getResource(String name) {
        URL resource = super.getResource(name);
        List<URL> alreadyLoaded = fetchRecord(name);
        alreadyLoaded.add(resource);
        return resource;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration<URL> resources = super.getResources(name);
        List<URL> alreadyLoaded = fetchRecord(name);
        
        // Need to copy as we record
        List<URL> these = Collections.list(resources);
        alreadyLoaded.addAll(these);
        
        // Return our copy
        return Collections.enumeration(these);
    }

    public List<URL> getLoadedResources(String resourceName) {
        List<URL> resources = loadedResources.get(resourceName);
        if (resources == null) return Collections.emptyList();
        return Collections.unmodifiableList(resources);
    }
    public Map<String,List<URL>> getLoadedResources() {
        return Collections.unmodifiableMap(loadedResources);
    }
    public void resetLoadedResources() {
        loadedResources.clear();
    }
}
