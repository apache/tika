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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.FrameworkWiring;
import org.xml.sax.ContentHandler;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Integration test that boots an OSGi container, installs the
 * tika-core and tika-bundle-standard bundles, and verifies that the bundles
 * activate, services register, and parsing works.
 * <p>
 * The tests run outside the OSGi container (on the JVM classpath), so
 * service lookups use string-based names rather than class references.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BundleIT {

    private static final Path TEST_BUNDLES = Paths.get("target", "test-bundles");

    // SPI interfaces whose providers tika-bundle-standard ships
    private static final String[] SPI_INTERFACES = {
            "org.apache.tika.parser.Parser",
            "org.apache.tika.detect.Detector",
            "org.apache.tika.detect.EncodingDetector",
            "org.apache.tika.detect.zip.ZipContainerDetector",
            "org.apache.tika.language.detect.LanguageDetector",
            "org.apache.tika.metadata.filter.MetadataFilter",
            "org.apache.tika.renderer.Renderer"};

    private Framework framework;
    private BundleContext ctx;

    protected abstract FrameworkFactory frameworkFactory();

    private Framework newFramework(String storage) throws Exception {
        Map<String, String> config = new HashMap<>();
        config.put(Constants.FRAMEWORK_STORAGE_CLEAN,
                Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        config.put(Constants.FRAMEWORK_STORAGE,
                "target/osgi-cache/" + getClass().getSimpleName() + "-" + storage);
        config.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, String.join(",",
                "javax.xml.bind",
                "org.slf4j;version=2.0.17",
                "org.slf4j.event;version=2.0.17",
                "org.slf4j.helpers;version=2.0.17",
                "org.slf4j.spi;version=2.0.17"
        ));
        // No Service Loader Mediator capabilities: tika-core must resolve without one (TIKA-4945).
        Framework fw = frameworkFactory().newFramework(config);
        fw.start();
        return fw;
    }

    @BeforeAll
    void startFramework() throws Exception {
        framework = newFramework("all");
        ctx = framework.getBundleContext();

        // Install all bundles first, then start. The test-bundles directory also holds the
        // dependencies of both that are OSGi bundles themselves.
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
    void stopFramework() throws Exception {
        stop(framework);
    }

    private static void stop(Framework fw) throws Exception {
        if (fw != null) {
            fw.stop();
            fw.waitForStop(10_000);
        }
    }

    // TIKA-4945: a p2 install resolves tika-core on its own, before any provider bundle exists
    @Test
    public void testCoreResolvesWithoutStandardBundle() throws Exception {
        assertTrue(resolvesWithout("org.apache.tika.core", "tika-bundle-standard.jar"),
                "tika-core did not resolve without tika-bundle-standard");
    }

    // TIKA-4955: a missing dependency bundle must fail resolution, not a parse
    @Test
    public void testMissingDependencyBundleFailsResolution() throws Exception {
        assertFalse(resolvesWithout("org.apache.tika.bundle-standard", "pdfbox-io.jar"),
                "tika-bundle-standard resolved without pdfbox-io");
    }

    private boolean resolvesWithout(String symbolicName, String omittedJar) throws Exception {
        Framework fw = newFramework("without-" + omittedJar);
        try {
            Bundle target = null;
            boolean omitted = false;
            try (DirectoryStream<Path> jars = Files.newDirectoryStream(TEST_BUNDLES, "*.jar")) {
                for (Path jar : jars) {
                    if (jar.getFileName().toString().equals(omittedJar)) {
                        omitted = true;
                        continue;
                    }
                    Bundle b = fw.getBundleContext().installBundle(jar.toUri().toString());
                    if (symbolicName.equals(b.getSymbolicName())) {
                        target = b;
                    }
                }
            }
            assertTrue(omitted, omittedJar + " not in " + TEST_BUNDLES);
            assertNotNull(target, symbolicName + " not installed");
            return fw.adapt(FrameworkWiring.class).resolveBundles(List.of(target));
        } finally {
            stop(fw);
        }
    }

    // ServiceLoader logs and skips a provider that fails to load, so a missing embedded
    // dependency silently drops a parser or detector; instantiate every one here
    @Test
    public void testAllServiceProvidersLoad() throws Exception {
        Bundle tikaBundle = findBundle("org.apache.tika.bundle-standard");
        Set<String> failures = new TreeSet<>();
        int count = 0;
        for (String spi : SPI_INTERFACES) {
            Enumeration<URL> files =
                    tikaBundle.getResources("META-INF/services/" + spi);
            while (files != null && files.hasMoreElements()) {
                for (String line : new String(files.nextElement().openStream().readAllBytes(),
                        StandardCharsets.UTF_8).split("\\R")) {
                    String name = line.replaceAll("#.*", "").trim();
                    if (name.isEmpty()) {
                        continue;
                    }
                    count++;
                    try {
                        tikaBundle.loadClass(name).getConstructor().newInstance();
                    } catch (Throwable t) {
                        Throwable root = t;
                        while (root.getCause() != null) {
                            root = root.getCause();
                        }
                        failures.add(name + " -> " + root);
                    }
                }
            }
        }
        assertTrue(count > 50, "found only " + count + " service providers");
        assertTrue(failures.isEmpty(), failures.size() + " of " + count
                + " service providers failed to load:\n" + String.join("\n", failures));
    }

    @Test
    public void testAllBundlesActive() {
        for (Bundle b : ctx.getBundles()) {
            assertEquals(Bundle.ACTIVE, b.getState(), "Bundle not active: " + b.getSymbolicName());
        }
    }

    // tika-core's defaults inside OSGi must be built from the same providers as on the
    // classpath; a dependency missing from the bundle drops its providers silently
    @Test
    public void testDefaultsMatchClasspath() throws Exception {
        Bundle core = findBundle("org.apache.tika.core");
        Object parser = ctx.getService(ctx.getAllServiceReferences(
                "org.apache.tika.parser.Parser", null)[0]);
        Object detector = ctx.getService(ctx.getAllServiceReferences(
                "org.apache.tika.detect.Detector", null)[0]);
        Object encodingDetector = core.loadClass("org.apache.tika.detect.DefaultEncodingDetector")
                .getConstructor().newInstance();

        assertEquals(classNames(new DefaultParser().getAllComponentParsers()),
                classNames((Collection<?>) parser.getClass()
                        .getMethod("getAllComponentParsers").invoke(parser)), "parsers");
        assertEquals(classNames(new DefaultDetector().getDetectors()),
                classNames((Collection<?>) detector.getClass()
                        .getMethod("getDetectors").invoke(detector)), "detectors");
        assertEquals(classNames(new DefaultEncodingDetector().getDetectors()),
                classNames((Collection<?>) encodingDetector.getClass()
                        .getMethod("getDetectors").invoke(encodingDetector)), "encoding detectors");
    }

    private static List<String> classNames(Collection<?> objects) {
        List<String> names = new ArrayList<>();
        for (Object o : objects) {
            names.add(o.getClass().getName());
        }
        return names;
    }

    // runs the docs page's example as a consumer bundle would
    @Test
    public void testDocumentedExample() throws Exception {
        String pkg = "org.apache.tika.bundle.example";
        Path classes = Paths.get("target", "test-classes", pkg.replace('.', '/'));
        Path jar = Paths.get("target", "osgi-example.jar");
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
        attributes.putValue(Constants.BUNDLE_SYMBOLICNAME, pkg);
        attributes.putValue(Constants.IMPORT_PACKAGE, String.join(",",
                "org.apache.tika.detect", "org.apache.tika.io", "org.apache.tika.metadata",
                "org.apache.tika.parser", "org.apache.tika.sax", "org.osgi.framework",
                "org.xml.sax"));
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest);
                DirectoryStream<Path> files = Files.newDirectoryStream(classes, "*.class")) {
            for (Path f : files) {
                out.putNextEntry(new JarEntry(pkg.replace('.', '/') + "/" + f.getFileName()));
                out.write(Files.readAllBytes(f));
                out.closeEntry();
            }
        }

        Bundle example = ctx.installBundle(jar.toUri().toString());
        try {
            example.start();
            Path pdf = Files.createTempFile("osgi-example", ".pdf");
            try {
                Files.write(pdf, testDocuments().get("testPDF.pdf"));
                Class<?> exampleClass = example.loadClass(pkg + ".OsgiParseExample");
                assertNotEquals(BundleIT.class.getClassLoader(), exampleClass.getClassLoader());
                String text = (String) exampleClass
                        .getMethod("parse", BundleContext.class, Path.class)
                        .invoke(null, example.getBundleContext(), pdf);
                assertTrue(text.contains("Apache Tika"), text);
            } finally {
                Files.delete(pdf);
            }
        } finally {
            example.uninstall();
        }
    }

    // Detect and parse every sample, embedded documents included, through the OSGi services
    // and through the plain classpath; any difference is a class the bundle cannot see
    @Test
    public void testParseMatchesClasspath() throws Exception {
        Bundle core = findBundle("org.apache.tika.core");
        Class<?> metadataClass = core.loadClass("org.apache.tika.metadata.Metadata");
        Class<?> propertyClass = core.loadClass("org.apache.tika.metadata.Property");
        Class<?> tisClass = core.loadClass("org.apache.tika.io.TikaInputStream");
        Class<?> contextClass = core.loadClass("org.apache.tika.parser.ParseContext");
        Class<?> parserClass = core.loadClass("org.apache.tika.parser.Parser");
        Class<?> detectorClass = core.loadClass("org.apache.tika.detect.Detector");
        Object resourceName = core.loadClass("org.apache.tika.metadata.TikaCoreProperties")
                .getField("RESOURCE_NAME_KEY").get(null);
        Method tisGet = tisClass.getMethod("get", byte[].class);
        Method metadataSet = metadataClass.getMethod("set", propertyClass, String.class);
        Method metadataGet = metadataClass.getMethod("get", String.class);
        Method contextSet = contextClass.getMethod("set", Class.class, Object.class);
        Method parse = parserClass.getMethod("parse", tisClass, ContentHandler.class,
                metadataClass, contextClass);

        Object parsers = Array.newInstance(parserClass, 1);
        Array.set(parsers, 0, ctx.getService(ctx.getAllServiceReferences(
                parserClass.getName(), null)[0]));
        Object osgiParser = core.loadClass("org.apache.tika.parser.AutoDetectParser")
                .getConstructor(detectorClass, parsers.getClass())
                .newInstance(ctx.getService(ctx.getAllServiceReferences(
                        detectorClass.getName(), null)[0]), parsers);
        AutoDetectParser classpathParser = new AutoDetectParser();

        Map<String, byte[]> docs = testDocuments();
        assertTrue(docs.size() >= 20, "found only " + docs.size() + " test documents");
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, byte[]> doc : docs.entrySet()) {
            Metadata expectedMetadata = new Metadata();
            expectedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, doc.getKey());
            ParseContext expectedContext = new ParseContext();
            expectedContext.set(Parser.class, classpathParser);
            BodyContentHandler expectedText = new BodyContentHandler(-1);
            try (TikaInputStream tis = TikaInputStream.get(doc.getValue())) {
                classpathParser.parse(tis, expectedText, expectedMetadata, expectedContext);
            }

            Object metadata = metadataClass.getConstructor().newInstance();
            metadataSet.invoke(metadata, resourceName, doc.getKey());
            Object context = contextClass.getConstructor().newInstance();
            contextSet.invoke(context, parserClass, osgiParser);
            ContentHandler text = (ContentHandler) core
                    .loadClass("org.apache.tika.sax.BodyContentHandler")
                    .getConstructor(int.class).newInstance(-1);
            try (AutoCloseable tis = (AutoCloseable) tisGet.invoke(null, (Object) doc.getValue())) {
                parse.invoke(osgiParser, tis, text, metadata, context);
            } catch (InvocationTargetException e) {
                failures.add(doc.getKey() + ": " + e.getCause());
                continue;
            }

            String expectedType = expectedMetadata.get(HttpHeaders.CONTENT_TYPE);
            Object type = metadataGet.invoke(metadata, HttpHeaders.CONTENT_TYPE.getName());
            if (!expectedType.equals(type)) {
                failures.add(doc.getKey() + ": type " + type + ", classpath " + expectedType);
            } else {
                String expected = normalize(expectedText.toString());
                String actual = normalize(text.toString());
                if (!expected.equals(actual)) {
                    int i = 0;
                    while (i < Math.min(expected.length(), actual.length())
                            && expected.charAt(i) == actual.charAt(i)) {
                        i++;
                    }
                    failures.add(doc.getKey() + ": text differs from classpath at " + i + ": \""
                            + actual.substring(i, Math.min(actual.length(), i + 60)) + "\" vs \""
                            + expected.substring(i, Math.min(expected.length(), i + 60)) + "\"");
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static Map<String, byte[]> testDocuments() throws Exception {
        Map<String, byte[]> docs = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(
                BundleIT.class.getResourceAsStream("/test-documents.zip"))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                docs.put(entry.getName(), zip.readAllBytes());
            }
        }
        return docs;
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
