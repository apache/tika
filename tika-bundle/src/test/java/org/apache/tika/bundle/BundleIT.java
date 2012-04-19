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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.BundleContext;
import org.xml.sax.ContentHandler;

@RunWith(JUnit4TestRunner.class)
public class BundleIT {

    private final File TARGET = new File("target");

    @Configuration
    public Option[] configuration() throws IOException, URISyntaxException {
        File base = new File(TARGET, "test-bundles");
        return CoreOptions.options(
                junitBundles(),
                bundle(new File(base, "tika-core.jar").toURL().toURI().toString()),
                bundle(new File(base, "tika-bundle.jar").toURL().toURI().toString()));
    }
 
    @Test
    public void testTikaBundle(BundleContext bc) throws Exception {
        Tika tika = new Tika();

        // Simple type detection
        assertEquals("text/plain", tika.detect("test.txt"));
        assertEquals("application/pdf", tika.detect("test.pdf"));

        // Simple text extraction
        String xml = tika.parseToString(new File("pom.xml"));
        assertTrue(xml.contains("tika-bundle"));

        // Package extraction
        ContentHandler handler = new BodyContentHandler();

        Parser parser = tika.getParser();
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);

        InputStream stream =
                new FileInputStream("src/test/resources/test-documents.zip");
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

}
