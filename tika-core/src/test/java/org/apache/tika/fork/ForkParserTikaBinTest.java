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
package org.apache.tika.fork;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParserFactory;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ForkParserTikaBinTest extends TikaTest {
    private static final String JAR_FILE_NAME = "mock-tika-app.jar";
    private static final Map<String, String> EMPTY_MAP = Collections.emptyMap();

    @TempDir private static Path JAR_DIR;
    private static Path JAR_FILE;

    @BeforeAll
    public static void bootstrapJar() throws Exception {
        JAR_FILE = JAR_DIR.resolve(JAR_FILE_NAME);

        try (JarOutputStream jarOs = new JarOutputStream(Files.newOutputStream(JAR_FILE))) {
            ClassLoader loader = ForkServer.class.getClassLoader();
            ClassPath classPath = ClassPath.from(loader);
            addClasses(jarOs, classPath, ci -> ci.getPackageName().startsWith("org.slf4j"));
            addClasses(
                    jarOs, classPath, ci -> ci.getPackageName().startsWith("org.apache.logging"));
            addClasses(
                    jarOs,
                    classPath,
                    ci -> ci.getPackageName().startsWith("org.apache.commons.io"));
            // exclude TypeDetectionBenchmark because it is not serializable
            // exclude UpperCasingContentHandler because we want to test that
            // we can serialize it from the parent process into the forked process
            addClasses(
                    jarOs,
                    classPath,
                    ci ->
                            ci.getPackageName().startsWith("org.apache.tika")
                                    && (!ci.getName().contains("TypeDetectionBenchmark"))
                                    && (!ci.getName().contains("UpperCasingContentHandler")));

            try (InputStream input =
                    ForkParserTikaBinTest.class.getResourceAsStream(
                            "/org/apache/tika/config/TIKA-2653-vowel-parser-ae.xml")) {
                jarOs.putNextEntry(
                        new JarEntry("org/apache/tika/parser/TIKA-2653-vowel-parser-ae.xml"));
                IOUtils.copy(input, jarOs);
            }
            try (InputStream input =
                    ForkParserTikaBinTest.class.getResourceAsStream(
                            "/org/apache/tika/mime/tika-mimetypes.xml")) {
                jarOs.putNextEntry(new JarEntry("org/apache/tika/mime/tika-mimetypes.xml"));
                IOUtils.copy(input, jarOs);
            }
            try (InputStream input =
                    ForkParserTikaBinTest.class.getResourceAsStream("/custom-mimetypes.xml")) {
                jarOs.putNextEntry(new JarEntry("custom-mimetypes.xml"));
                IOUtils.copy(input, jarOs);
            }

            jarOs.putNextEntry(new JarEntry("META-INF/services/org.apache.tika.parser.Parser"));
            jarOs.write(
                    "org.apache.tika.parser.mock.VowelParser\n".getBytes(StandardCharsets.UTF_8));
        }

        Path tikaConfigVowelParser = JAR_DIR.resolve("TIKA_2653-iou.xml");
        try (InputStream is =
                        ForkServer.class.getResourceAsStream(
                                "/org/apache/tika/config/TIKA-2653-vowel-parser-iou.xml");
                OutputStream os = Files.newOutputStream(tikaConfigVowelParser)) {
            IOUtils.copy(is, os);
        }
    }

    private static void addClasses(
            JarOutputStream jarOs, ClassPath classPath, Predicate<ClassPath.ClassInfo> predicate)
            throws IOException {
        for (ClassPath.ClassInfo classInfo : classPath.getAllClasses()) {
            if (predicate.test(classInfo)) {
                jarOs.putNextEntry(new JarEntry(classInfo.getResourceName()));
                classInfo.asByteSource().copyTo(jarOs);
            }
        }
    }

    @Test
    public void testExplicitParserFactory() throws Exception {
        XMLResult xmlResult =
                getXML(
                        new ParserFactoryFactory(
                                "org.apache.tika.parser.mock.MockParserFactory", EMPTY_MAP));
        assertContains("hello world!", xmlResult.xml);
        assertEquals("Nikolai Lobachevsky", xmlResult.metadata.get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testVowelParserAsDefault() throws Exception {
        ParserFactoryFactory pff =
                new ParserFactoryFactory(
                        "org.apache.tika.parser.AutoDetectParserFactory", EMPTY_MAP);
        XMLResult xmlResult = getXML(pff);
        assertContains("eooeuiooueoeeao", xmlResult.xml);
        assertEquals("Nikolai Lobachevsky", xmlResult.metadata.get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testVowelParserInClassPath() throws Exception {
        Map<String, String> args = new HashMap<>();
        args.put(AutoDetectParserFactory.TIKA_CONFIG_PATH, "TIKA-2653-vowel-parser-ae.xml");
        ParserFactoryFactory pff =
                new ParserFactoryFactory("org.apache.tika.parser.AutoDetectParserFactory", args);
        XMLResult xmlResult = getXML(pff);
        assertContains("eeeeea", xmlResult.xml);
        assertEquals("Nikolai Lobachevsky", xmlResult.metadata.get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testVowelParserFromDirectory() throws Exception {
        Map<String, String> args = new HashMap<>();
        args.put(
                AutoDetectParserFactory.TIKA_CONFIG_PATH,
                JAR_DIR.resolve("TIKA_2653-iou.xml").toAbsolutePath().toString());
        ParserFactoryFactory pff =
                new ParserFactoryFactory("org.apache.tika.parser.AutoDetectParserFactory", args);
        XMLResult xmlResult = getXML(pff);
        assertContains("oouioouoo", xmlResult.xml);
        assertEquals("Nikolai Lobachevsky", xmlResult.metadata.get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testPFFWithClassLoaderFromParentProcess() throws Exception {
        // The UpperCasingContentHandler is not sent to the bootstrap test jar file in @BeforeClass.
        // this tests that the content handler was loaded from the parent process.

        ParserFactoryFactory pff =
                new ParserFactoryFactory(
                        "org.apache.tika.parser.AutoDetectParserFactory", EMPTY_MAP);
        XMLResult xmlResult =
                getXML(pff, this.getClass().getClassLoader(), new UpperCasingContentHandler());
        assertContains("EOOEUIOOUEOEEAO", xmlResult.xml);
        assertEquals("Nikolai Lobachevsky", xmlResult.metadata.get(TikaCoreProperties.CREATOR));
    }

    private XMLResult getXML(ParserFactoryFactory pff)
            throws TikaException, SAXException, IOException {
        return getXML(pff, null, null);
    }

    private XMLResult getXML(
            ParserFactoryFactory pff, ClassLoader classloader, ContentHandler contentHandler)
            throws TikaException, SAXException, IOException {

        List<String> java = new ArrayList<>();
        java.add("java");
        java.add("-Djava.awt.headless=true");
        ForkParser parser = null;
        if (classloader != null) {
            parser = new ForkParser(JAR_DIR, pff, classloader);
        } else {
            parser = new ForkParser(JAR_DIR, pff);
        }
        parser.setJavaCommand(java);
        parser.setServerPulseMillis(10000);

        ContentHandler handler =
                (contentHandler == null) ? new ToXMLContentHandler() : contentHandler;
        Metadata m = new Metadata();
        try (InputStream is = getResourceAsStream("/test-documents/example.xml")) {
            parser.parse(is, handler, m, new ParseContext());
        } finally {
            parser.close();
        }
        return new XMLResult(handler.toString(), m);
    }
}
