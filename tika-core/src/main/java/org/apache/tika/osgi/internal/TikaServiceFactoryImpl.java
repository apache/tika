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
package org.apache.tika.osgi.internal;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.osgi.TikaService;
import org.apache.tika.osgi.TikaServiceFactory;
import org.osgi.framework.BundleContext;

public class TikaServiceFactoryImpl implements TikaServiceFactory {
    
    private static Logger LOG = Logger.getLogger(TikaServiceFactoryImpl.class.getName());
    
    private final BundleContext context;
    
    public TikaServiceFactoryImpl(BundleContext context) {
        this.context = context;
    }
    
    @Override
    public TikaService createTikaService() {
        TikaConfig config = null;
        String configFilePath = context.getProperty("org.apache.tika.osgi.internal.TikaServiceImpl.tikaConfigPath");
        
        if(configFilePath != null)
        {
            File configFile = new File(configFilePath);
            try {
                config = new TikaConfig(configFile);
            } catch (Exception e ) {
                config = TikaConfig.getDefaultConfig();
                LOG.log(Level.WARNING, "Error Creating TikaConfig. Using Default Config", e);
            }
        }
        else
        {
            config = TikaConfig.getDefaultConfig();
        }
        return createTikaService(config);
    }

    @Override
    public TikaService createTikaService(TikaConfig config) {
        return new TikaServiceImpl(config);
    }

}
