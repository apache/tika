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
package org.apache.tika.config;

import org.apache.tika.detect.Detector;
import org.apache.tika.parser.Parser;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * Bundle activator that adjust the class loading mechanism of the
 * {@link ServiceLoader} class to work correctly in an OSGi environment.
 * <p>
 * Note that you should <strong>not</strong> access this class directly.
 * Instead the OSGi environment (if present) will automatically invoke the
 * methods of this class based on the Bundle-Activator setting in the bundle
 * manifest.
 *
 * @since Apache Tika 0.9
 */
public class TikaActivator implements BundleActivator, ServiceTrackerCustomizer {

	private ServiceTracker detectorTracker;

	private ServiceTracker parserTracker;

	private BundleContext bundleContext;
    //-----------------------------------------------------< BundleActivator >

    public void start(final BundleContext context) throws Exception {
    	bundleContext = context;

    	detectorTracker = new ServiceTracker(context, Detector.class.getName(), this);
        parserTracker = new ServiceTracker(context, Parser.class.getName(), this);

        detectorTracker.open();
        parserTracker.open();
    }

    public void stop(BundleContext context) throws Exception {
    	parserTracker.close();
    	detectorTracker.close();
    }

	public Object addingService(ServiceReference reference) {
        Object service = bundleContext.getService(reference);
        ServiceLoader.addService(reference, service);
		return service;
	}

	public void modifiedService(ServiceReference reference, Object service) {
	}

	public void removedService(ServiceReference reference, Object service) {
        ServiceLoader.removeService(reference);
        bundleContext.ungetService(reference);
	}

}
