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

import org.apache.tika.parser.Parser;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

public abstract class TikaAbstractBundleActivator implements BundleActivator {

    Dictionary createServiceRankProperties(String configName, BundleContext context) {
        Dictionary serviceProps = new Properties();
        String serviceRank = context.getProperty(configName);
        if (serviceRank != null) {
            serviceProps.put(Constants.SERVICE_RANKING, Integer.parseInt(serviceRank));
        }
        return serviceProps;

    }
    
    public void registerTikaParserServiceLoader(BundleContext context, ClassLoader loader)
    {
        ServiceLoader<Parser> serviceLoader = ServiceLoader.load(Parser.class, loader);
        for(Parser currentParser: serviceLoader)
        {
            registerTikaService(context, currentParser, null);
        }
    }

    void registerTikaService(BundleContext context, Parser parserService,
            Dictionary additionalServiceProperties) {
        String parserFullyClassifiedName = parserService.getClass().getCanonicalName().toLowerCase(Locale.US);

        String serviceRankingPropName = parserFullyClassifiedName + ".serviceRanking";

        Dictionary serviceProperties = createServiceRankProperties(serviceRankingPropName, context);

        if (additionalServiceProperties != null) {
            Enumeration keys = additionalServiceProperties.keys();
            while (keys.hasMoreElements()) {
                String currentKey = (String) keys.nextElement();
                serviceProperties.put(currentKey, additionalServiceProperties.get(currentKey));
            }

        }

        context.registerService(Parser.class, parserService, serviceProperties);
    }

}
