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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.CoreOptions.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.apache.tika.Tika;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.fork.ForkParser;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.internal.Activator;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
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
    public Option[] configuration() throws IOException, URISyntaxException {
        File base = new File(TARGET, "test-bundles");
        return options(
                junitBundles(),
                bundle(new File(base, "tika-core.jar").toURI().toURL().toString()),
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
    public void testBundleDetection() throws Exception {
        Tika tika = new Tika();

        // Simple type detection
        assertEquals("text/plain", tika.detect("test.txt"));
        assertEquals("application/pdf", tika.detect("test.pdf"));
    }


    @Test
    public void testForkParser() throws Exception {
        ForkParser parser = new ForkParser(Activator.class.getClassLoader(), defaultParser);
        String data = "<!DOCTYPE html>\n<html><body><p>test <span>content</span></p></body></html>";
        InputStream stream = new ByteArrayInputStream(data.getBytes("UTF-8"));
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


    @Ignore // TODO Fix this test
    @Test
    public void testBundleSimpleText() throws Exception {
        Tika tika = new Tika();

        // Simple text extraction
        String xml = tika.parseToString(new File("pom.xml"));
        assertTrue(xml.contains("tika-bundle"));
    }


    @Ignore // TODO Fix this test
    @Test
    public void testBundleDetectors() throws Exception {
        // Get the raw detectors list
        // TODO Why is this not finding the detector service resource files?
        TestingServiceLoader loader = new TestingServiceLoader();
        List<String> rawDetectors = loader.identifyStaticServiceProviders(Detector.class);

        // Check we did get a few, just in case...
        assertNotNull(rawDetectors);
        assertTrue("Should have several Detector names, found " + rawDetectors.size(),
                rawDetectors.size() > 3);

        // Get the classes found within OSGi
        DefaultDetector detector = new DefaultDetector();
        Set<String> osgiDetectors = new HashSet<String>();
        for (Detector d : detector.getDetectors()) {
            osgiDetectors.add(d.getClass().getName());
        }

        // Check that OSGi didn't miss any
        for (String detectorName : rawDetectors) {
            if (!osgiDetectors.contains(detectorName)) {
                fail("Detector " + detectorName
                        + " not found within OSGi Detector list: " + osgiDetectors);
            }
        }
    }


    @Test
    public void testBundleParsers() throws Exception {
        TikaConfig tika = new TikaConfig();

        // TODO Implement as with Detectors
    }


    @Ignore // TODO Fix this test
    @Test
    public void testTikaBundle() throws Exception {
        Tika tika = new Tika();

        // Package extraction
        ContentHandler handler = new BodyContentHandler();

        Parser parser = tika.getParser();
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);

        InputStream stream
                = new FileInputStream("src/test/resources/test-documents.zip");
        try {
            parser.parse(stream, handler, new Metadata(), context);
        } finally {
            stream.close();
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

    /**
     * Alternate ServiceLoader which works outside of OSGi, so we can compare between the two environments
     */
    private static class TestingServiceLoader extends ServiceLoader {

        private TestingServiceLoader() {
            super();
        }


        public <T> List<String> identifyStaticServiceProviders(Class<T> iface) {
            return super.identifyStaticServiceProviders(iface);
        }
    }
}
