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
package org.apache.tika.osgi;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.ServiceLoader;

import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.parser.Parser;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

/**
 * 
 * Abstract class that is used by Tika bundles to initialize 
 * Parsers, Detectors, EncodingDetectors, and Language Detectors as
 * OSGi services based on Java ServiceLoader config.
 * 
 * @since 2.0
 *
 */
public abstract class TikaAbstractBundleActivator implements BundleActivator {
    
    public void registerAllTikaServiceLoaders(BundleContext context, ClassLoader loader)
    {
        registerServiceFromServiceLoader(context, loader, Parser.class);
        registerServiceFromServiceLoader(context, loader, Detector.class);
        registerServiceFromServiceLoader(context, loader, EncodingDetector.class);
        registerServiceFromServiceLoader(context, loader, LanguageDetector.class);
    }
    
    public <T> void registerServiceFromServiceLoader(BundleContext context, ClassLoader loader, Class<T> iface)
    {
        ServiceLoader<T> serviceLoader = ServiceLoader.load(iface, loader);
        for(T currentService: serviceLoader)
        {
            registerTikaService(context, iface, currentService, null);
        }
    }
    
    

    void registerTikaService(BundleContext context, Class klass, Object service,
            Dictionary additionalServiceProperties) {
        String parserFullyClassifiedName = service.getClass().getCanonicalName().toLowerCase(Locale.US);

        String serviceRankingPropName = parserFullyClassifiedName + ".serviceRanking";

        Dictionary serviceProperties = new Properties();
        
        createServiceRankProperties(serviceProperties, serviceRankingPropName, context);

        if (additionalServiceProperties != null) {
            Enumeration keys = additionalServiceProperties.keys();
            while (keys.hasMoreElements()) {
                String currentKey = (String) keys.nextElement();
                serviceProperties.put(currentKey, additionalServiceProperties.get(currentKey));
            }

        }

        context.registerService(klass, service, serviceProperties);
    }
    
    void createServiceRankProperties(Dictionary serviceProps, String configName, BundleContext context) {
        String serviceRank = context.getProperty(configName);
        if (serviceRank != null) {
            serviceProps.put(Constants.SERVICE_RANKING, Integer.parseInt(serviceRank));
        }
    }

}
