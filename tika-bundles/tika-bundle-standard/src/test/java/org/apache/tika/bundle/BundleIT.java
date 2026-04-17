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
package org.apache.tika.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * Integration test that boots an Apache Felix OSGi container, installs the
 * tika-core and tika-bundle-standard bundles, and verifies that the bundles
 * activate, services register, and parsing works.
 * <p>
 * The tests run outside the OSGi container (on the JVM classpath), so
 * service lookups use string-based names rather than class references.
 */
public class BundleIT {

    private static final Path TEST_BUNDLES = Paths.get("target", "test-bundles");

    private static Framework framework;
    private static BundleContext ctx;

    @BeforeAll
    static void startFramework() throws Exception {
        Map<String, String> config = new HashMap<>();
        config.put(Constants.FRAMEWORK_STORAGE_CLEAN,
                Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        config.put(Constants.FRAMEWORK_STORAGE,
                "target/osgi-cache");
        config.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, String.join(",",
                "javax.xml.bind",
                "org.slf4j;version=2.0.17",
                "org.slf4j.event;version=2.0.17",
                "org.slf4j.helpers;version=2.0.17",
                "org.slf4j.spi;version=2.0.17"
        ));
        config.put("org.osgi.framework.system.capabilities.extra", String.join(",",
                "osgi.extender;osgi.extender=osgi.serviceloader.processor;version:Version=1.0",
                "osgi.extender;osgi.extender=osgi.serviceloader.registrar;version:Version=1.0",
                "osgi.serviceloader;osgi.serviceloader=org.apache.tika.detect.Detector",
                "osgi.serviceloader;osgi.serviceloader=org.apache.tika.detect.EncodingDetector",
                "osgi.serviceloader;osgi.serviceloader=org.apache.tika.language.detect.LanguageDetector",
                "osgi.serviceloader;osgi.serviceloader=org.apache.tika.metadata.filter.MetadataFilter",
                "osgi.serviceloader;osgi.serviceloader=org.apache.tika.parser.Parser"
        ));

        FrameworkFactory factory = ServiceLoader.load(FrameworkFactory.class)
                .iterator().next();
        framework = factory.newFramework(config);
        framework.start();
        ctx = framework.getBundleContext();

        // Install all bundles first, then start.
        // tika-core requires osgi.serviceloader capabilities that are
        // provided by tika-bundle-standard, so both must be installed
        // before either can resolve.
        Bundle commonsIo = install("commons-io.jar");
        Bundle tikaCore = install("tika-core.jar");
        Bundle tikaBundle = install("tika-bundle-standard.jar");

        commonsIo.start();
        tikaCore.start();
        tikaBundle.start();
    }

    private static Bundle install(String filename) throws Exception {
        File f = TEST_BUNDLES.resolve(filename).toFile();
        assertTrue(f.exists(), "Bundle not found: " + f);
        return ctx.installBundle(f.toURI().toString());
    }

    @AfterAll
    static void stopFramework() throws Exception {
        if (framework != null) {
            framework.stop();
            framework.waitForStop(10_000);
        }
    }

    @Test
    public void testBundleLoaded() {
        boolean hasCore = false, hasBundle = false;
        for (Bundle b : ctx.getBundles()) {
            if ("org.apache.tika.core".equals(b.getSymbolicName())) {
                hasCore = true;
                assertEquals(Bundle.ACTIVE, b.getState(), "Core not activated");
            }
            if ("org.apache.tika.bundle-standard".equals(b.getSymbolicName())) {
                hasBundle = true;
                assertEquals(Bundle.ACTIVE, b.getState(), "Bundle not activated");
            }
        }
        assertTrue(hasCore, "Core bundle not found");
        assertTrue(hasBundle, "Standard bundle not found");
    }

    @Test
    public void testDetectorServiceRegistered() throws Exception {
        ServiceReference<?>[] refs = ctx.getAllServiceReferences(
                "org.apache.tika.detect.Detector", null);
        assertNotNull(refs, "Detector service not registered");
        assertTrue(refs.length > 0, "Should have at least one Detector service");
        Object detector = ctx.getService(refs[0]);
        assertNotNull(detector);
        assertEquals("org.apache.tika.detect.DefaultDetector",
                detector.getClass().getName());
    }

    @Test
    public void testParserServiceRegistered() throws Exception {
        ServiceReference<?>[] refs = ctx.getAllServiceReferences(
                "org.apache.tika.parser.Parser", null);
        assertNotNull(refs, "Parser service not registered");
        assertTrue(refs.length > 0, "Should have at least one Parser service");
        Object parser = ctx.getService(refs[0]);
        assertNotNull(parser);
        assertEquals("org.apache.tika.parser.DefaultParser",
                parser.getClass().getName());
    }

    @Test
    public void testDetectorHasMultipleDetectors() throws Exception {
        ServiceReference<?>[] refs = ctx.getAllServiceReferences(
                "org.apache.tika.detect.Detector", null);
        Object detector = ctx.getService(refs[0]);
        Object detectors = detector.getClass()
                .getMethod("getDetectors").invoke(detector);
        int size = ((java.util.List<?>) detectors).size();
        assertTrue(size > 3,
                "Should have several detectors, found " + size);
    }

    @Test
    public void testParserHasMultipleParsers() throws Exception {
        ServiceReference<?>[] refs = ctx.getAllServiceReferences(
                "org.apache.tika.parser.Parser", null);
        Object parser = ctx.getService(refs[0]);
        Object parsers = parser.getClass()
                .getMethod("getAllComponentParsers").invoke(parser);
        int size = ((java.util.Collection<?>) parsers).size();
        assertTrue(size > 15,
                "Should have lots of parsers, found " + size);
    }

    @Test
    public void testTikaClassLoadable() throws Exception {
        // Verify key Tika classes can be loaded from the bundle's classloader
        Bundle tikaCore = findBundle("org.apache.tika.core");
        assertNotNull(tikaCore, "tika-core bundle not found");
        assertNotNull(tikaCore.loadClass("org.apache.tika.Tika"));
        assertNotNull(tikaCore.loadClass("org.apache.tika.parser.AutoDetectParser"));
        assertNotNull(tikaCore.loadClass("org.apache.tika.detect.DefaultDetector"));

        Bundle tikaBundle = findBundle("org.apache.tika.bundle-standard");
        assertNotNull(tikaBundle, "tika-bundle-standard not found");
        // Parser implementations should be loadable from the bundle
        assertNotNull(tikaBundle.loadClass("org.apache.tika.parser.pdf.PDFParser"));
        assertNotNull(tikaBundle.loadClass("org.apache.tika.parser.microsoft.ooxml.OOXMLParser"));
    }

    private Bundle findBundle(String symbolicName) {
        for (Bundle b : ctx.getBundles()) {
            if (symbolicName.equals(b.getSymbolicName())) {
                return b;
            }
        }
        return null;
    }
}
