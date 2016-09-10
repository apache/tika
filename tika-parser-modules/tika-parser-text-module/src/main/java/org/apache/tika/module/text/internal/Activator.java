package org.apache.tika.module.text.internal;

import org.apache.tika.osgi.TikaAbstractBundleActivator;
import org.osgi.framework.BundleContext;

public class Activator extends TikaAbstractBundleActivator {

    @Override
    public void start(BundleContext context) throws Exception {

        registerAllTikaServiceLoaders(context, Activator.class.getClassLoader());

    }

    @Override
    public void stop(BundleContext context) throws Exception {

    }

}
