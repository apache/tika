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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import javax.inject.Inject;

import org.apache.tika.Tika;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.fork.ForkParser;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.internal.Activator;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.xml.sax.ContentHandler;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class BundleIT {
	
	private final File TARGET = new File("target");
	
    @Inject
    private Parser defaultParser;

    @Inject
    private Detector contentTypeDetector;

    @Inject
    private BundleContext bc;

    @Configuration
    public Option[] configuration() throws IOException, URISyntaxException, ClassNotFoundException {
    	File base = new File(TARGET, "test-bundles");
        return options(
        		bundle(new File(base, "tika-core.jar").toURI().toURL().toString()),
        		mavenBundle("org.ops4j.pax.logging", "pax-logging-api", "1.8.5"),
        		mavenBundle("org.ops4j.pax.logging", "pax-logging-service", "1.8.5"),
        		junitBundles(),
        		bundle(new File(base, "tika-bundle.jar").toURI().toURL().toString()));
    }

    @Test
    public void testBundleLoaded() throws Exception {
        boolean hasCore = false, hasBundle = false;
        for (Bundle b : bc.getBundles()) {
            if ("org.apache.tika.core".equals(b.getSymbolicName())) {
                hasCore = true;
                assertEquals("Core not activated", Bundle.ACTIVE, b.getState());
            }
            if ("org.apache.tika.bundle".equals(b.getSymbolicName())) {
                hasBundle = true;
                assertEquals("Bundle not activated", Bundle.ACTIVE, b.getState());
            }
        }
        assertTrue("Core bundle not found", hasCore);
        assertTrue("Bundle bundle not found", hasBundle);
    }

    @Test
    public void testManifestNoJUnit() throws Exception {
        File TARGET = new File("target");
        File base = new File(TARGET, "test-bundles");
        File tikaBundle = new File(base, "tika-bundle.jar");

        JarInputStream jarIs = new JarInputStream(new FileInputStream(tikaBundle));
        Manifest mf = jarIs.getManifest();

        Attributes main = mf.getMainAttributes();

        String importPackage = main.getValue("Import-Package");

        boolean containsJunit = importPackage.contains("junit");

        assertFalse("The bundle should not import junit", containsJunit);
    }

    @Test
    public void testBundleDetection() throws Exception {
        Metadata metadataTXT = new Metadata();
        metadataTXT.set(Metadata.RESOURCE_NAME_KEY, "test.txt");

        Metadata metadataPDF = new Metadata();
        metadataPDF.set(Metadata.RESOURCE_NAME_KEY, "test.pdf");

        // Simple type detection
        assertEquals(MediaType.TEXT_PLAIN, contentTypeDetector.detect(null, metadataTXT));
        assertEquals(MediaType.application("pdf"), contentTypeDetector.detect(null, metadataPDF));
    }

    @Test
    public void testForkParser() throws Exception {
        ForkParser parser = new ForkParser(Activator.class.getClassLoader(), defaultParser);
        String data = "<!DOCTYPE html>\n<html><body><p>test <span>content</span></p></body></html>";
        InputStream stream = new ByteArrayInputStream(data.getBytes(UTF_8));
        Writer writer = new StringWriter();
        ContentHandler contentHandler = new BodyContentHandler(writer);
        Metadata metadata = new Metadata();
        MediaType type = contentTypeDetector.detect(stream, metadata);
        assertEquals(type.toString(), "text/html");
        metadata.add(Metadata.CONTENT_TYPE, type.toString());
        ParseContext parseCtx = new ParseContext();
        parser.parse(stream, contentHandler, metadata, parseCtx);
        writer.flush();
        String content = writer.toString();
        assertTrue(content.length() > 0);
        assertEquals("test content", content.trim());
    }

    @Test
    public void testBundleSimpleText() throws Exception {
        Tika tika = new Tika();

        // Simple text extraction
        String xml = tika.parseToString(new File("pom.xml"));
        assertTrue(xml.contains("tika-bundle"));
    }

    @Test
    public void testBundleDetectors() throws Exception {
        //For some reason, the detector created by OSGi has a flat
        //list of detectors, whereas the detector created by the traditional
        //service loading method has children: DefaultDetector, MimeTypes.
        //We have to flatten the service loaded DefaultDetector to get equivalence.
        //Detection behavior should all be the same.

        // Get the classes found within OSGi
        ServiceReference<Detector> detectorRef = bc.getServiceReference(Detector.class);
        DefaultDetector detectorService = (DefaultDetector) bc.getService(detectorRef);

        Set<String> osgiDetectors = new HashSet<>();
        for (Detector d : detectorService.getDetectors()) {
            osgiDetectors.add(d.getClass().getName());
        }

        // Check we did get a few, just in case...
        assertTrue("Should have several Detector names, found " + osgiDetectors.size(),
                osgiDetectors.size() > 3);

        // Get the raw detectors list from the traditional service loading mechanism
        DefaultDetector detector = new DefaultDetector();
        Set<String> rawDetectors = new HashSet<String>();
        for (Detector d : detector.getDetectors()) {
            if (d instanceof DefaultDetector) {
                for (Detector dChild : ((DefaultDetector) d).getDetectors()) {
                    rawDetectors.add(dChild.getClass().getName());
                }
            } else {
                rawDetectors.add(d.getClass().getName());
            }
        }
        assertEquals(osgiDetectors, rawDetectors);
    }

    @Test
    public void testBundleParsers() throws Exception {
        // Get the classes found within OSGi
        ServiceReference<Parser> parserRef = bc.getServiceReference(Parser.class);
        DefaultParser parserService = (DefaultParser) bc.getService(parserRef);

        Set<String> osgiParsers = new HashSet<>();
        for (Parser p : parserService.getAllComponentParsers()) {
            osgiParsers.add(p.getClass().getName());
        }

        // Check we did get a few, just in case...
        assertTrue("Should have lots Parser names, found " + osgiParsers.size(),
                osgiParsers.size() > 15);

        // Get the raw parsers list from the traditional service loading mechanism
        CompositeParser parser = (CompositeParser) defaultParser;
        Set<String> rawParsers = new HashSet<>();
        for (Parser p : parser.getAllComponentParsers()) {
            if (p instanceof DefaultParser) {
                for (Parser pChild : ((DefaultParser) p).getAllComponentParsers()) {
                    rawParsers.add(pChild.getClass().getName());
                }
            } else {
                rawParsers.add(p.getClass().getName());
            }
        }
        assertEquals(rawParsers, osgiParsers);
    }

    @Test
    public void testTesseractParser() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        Parser tesseractParser = new TesseractOCRParser();
        try (InputStream stream = new FileInputStream("src/test/resources/testOCR.jpg")) {
            tesseractParser.parse(stream, handler, new Metadata(), context);
        }

    }

    @Test
    public void testTikaBundle() throws Exception {
        Tika tika = new Tika();

        // Package extraction
        ContentHandler handler = new BodyContentHandler();

        Parser parser = tika.getParser();
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);

        try (InputStream stream =
                     new FileInputStream("src/test/resources/test-documents.zip")) {
            parser.parse(stream, handler, new Metadata(), context);
        }

        String content = handler.toString();
        assertTrue(content.contains("testEXCEL.xls"));
        assertTrue(content.contains("Sample Excel Worksheet"));
        assertTrue(content.contains("testHTML.html"));
        assertTrue(content.contains("Test Indexation Html"));
        assertTrue(content.contains("testOpenOffice2.odt"));
        assertTrue(content.contains("This is a sample Open Office document"));
        assertTrue(content.contains("testPDF.pdf"));
        assertTrue(content.contains("Apache Tika"));
        assertTrue(content.contains("testPPT.ppt"));
        assertTrue(content.contains("Sample Powerpoint Slide"));
        assertTrue(content.contains("testRTF.rtf"));
        assertTrue(content.contains("indexation Word"));
        assertTrue(content.contains("testTXT.txt"));
        assertTrue(content.contains("Test d'indexation de Txt"));
        assertTrue(content.contains("testWORD.doc"));
        assertTrue(content.contains("This is a sample Microsoft Word Document"));
        assertTrue(content.contains("testXML.xml"));
        assertTrue(content.contains("Rida Benjelloun"));
    }
}
