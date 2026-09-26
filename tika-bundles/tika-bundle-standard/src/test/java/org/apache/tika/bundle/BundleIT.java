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

import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.xml.sax.ContentHandler;

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
        //
        // tika-core requires osgi.serviceloader capabilities that are provided by tika-bundle-standard,
        // so both must be installed before either can resolve.
        //
        // The test-bundles directory also holds the dependencies of both that are OSGi bundles themselves.
        List<Bundle> bundles = new ArrayList<>();
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(TEST_BUNDLES, "*.jar")) {
            for (Path jar : jars) {
                bundles.add(ctx.installBundle(jar.toUri().toString()));
            }
        }
        assertNotNull(findBundle("org.apache.tika.core"), "tika-core bundle not installed");
        assertNotNull(findBundle("org.apache.tika.bundle-standard"),
                "tika-bundle-standard not installed");

        for (Bundle bundle : bundles) {
            bundle.start();
        }
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
    public void testAllBundlesActive() {
        for (Bundle b : ctx.getBundles()) {
            assertEquals(Bundle.ACTIVE, b.getState(), "Bundle not active: " + b.getSymbolicName());
        }
    }

    @Test
    public void testExternalDependenciesWired() throws Exception {
        // All imports of tika-bundle-standard are optional, so check that the
        // packages of dependencies that are not embedded are actually wired.
        Bundle tikaBundle = findBundle("org.apache.tika.bundle-standard");
        assertNotNull(tikaBundle, "tika-bundle-standard not found");
        for (String className : new String[]{
                "com.adobe.internal.xmp.XMPMetaFactory",
                "com.dd.plist.PropertyListParser",
                "org.apache.commons.codec.digest.DigestUtils",
                "org.apache.commons.collections4.MapUtils",
                "org.apache.commons.compress.archivers.ArchiveStreamFactory",
                "org.apache.commons.csv.CSVFormat",
                "org.apache.commons.exec.CommandLine",
                "org.apache.commons.io.IOUtils",
                "org.apache.commons.lang3.StringUtils",
                "org.apache.commons.math3.util.FastMath",
                "org.apache.fontbox.ttf.TrueTypeFont",
                "org.apache.pdfbox.Loader",
                "org.apache.pdfbox.io.RandomAccessRead",
                "org.bouncycastle.cms.CMSSignedData",
                "org.bouncycastle.jce.provider.BouncyCastleProvider",
                "org.jsoup.Jsoup",
                "org.objectweb.asm.ClassReader"}) {
            assertNotNull(tikaBundle.loadClass(className), className);
        }
        Bundle commonsCompress = findBundle("org.apache.commons.commons-compress");
        assertNotNull(commonsCompress, "commons-compress bundle not found");
        assertNotNull(commonsCompress.loadClass("org.tukaani.xz.XZInputStream"));
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
    public void testPdfParsing() throws Exception {
        byte[] pdf = null;
        try (ZipInputStream zip = new ZipInputStream(
                BundleIT.class.getResourceAsStream("/test-documents.zip"))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if ("testPDF.pdf".equals(entry.getName())) {
                    pdf = zip.readAllBytes();
                }
            }
        }
        assertNotNull(pdf, "testPDF.pdf not found");

        Bundle tikaCore = findBundle("org.apache.tika.core");
        Class<?> metadataClass = tikaCore.loadClass("org.apache.tika.metadata.Metadata");
        Class<?> tisClass = tikaCore.loadClass("org.apache.tika.io.TikaInputStream");
        Class<?> contextClass = tikaCore.loadClass("org.apache.tika.parser.ParseContext");
        Method parse = tikaCore.loadClass("org.apache.tika.parser.Parser").getMethod("parse",
                tisClass, ContentHandler.class, metadataClass, contextClass);

        Object metadata = metadataClass.getConstructor().newInstance();
        metadataClass.getMethod("set", String.class, String.class)
                .invoke(metadata, "Content-Type", "application/pdf");
        ContentHandler handler = (ContentHandler) tikaCore
                .loadClass("org.apache.tika.sax.BodyContentHandler")
                .getConstructor(int.class).newInstance(-1);

        // Uses PDFParser directly: parsing through the registered DefaultParser
        // service recurses, as TikaActivator feeds it back to itself.
        Object parser = findBundle("org.apache.tika.bundle-standard")
                .loadClass("org.apache.tika.parser.pdf.PDFParser")
                .getConstructor().newInstance();
        try (AutoCloseable tis = (AutoCloseable) tisClass.getMethod("get", byte[].class)
                .invoke(null, (Object) pdf)) {
            parse.invoke(parser, tis, handler, metadata, contextClass.getConstructor().newInstance());
        }

        Method get = metadataClass.getMethod("get", String.class);
        assertEquals("Apache Tika - Apache Tika", get.invoke(metadata, "dc:title"));
        assertTrue(handler.toString().contains("Apache Tika"), "PDF content not extracted");
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

    private static Bundle findBundle(String symbolicName) {
        for (Bundle b : ctx.getBundles()) {
            if (symbolicName.equals(b.getSymbolicName())) {
                return b;
            }
        }
        return null;
    }
}
