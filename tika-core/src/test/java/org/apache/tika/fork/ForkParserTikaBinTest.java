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

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParserFactory;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;

public class ForkParserTikaBinTest extends TikaTest {
    private static Path JAR_DIR;
    private static final String JAR_FILE_NAME = "mock-tika-app.jar";
    private static Path JAR_FILE;

    @SuppressWarnings("unchecked")
    private static final Map<String, String> EMPTY_MAP = Collections.EMPTY_MAP;

    @BeforeClass
    public static void bootstrapJar() throws Exception {
        JAR_DIR = Files.createTempDirectory("tika-fork-tikabin-");
        JAR_FILE = JAR_DIR.resolve(JAR_FILE_NAME);

        try (JarOutputStream jarOs = new JarOutputStream(Files.newOutputStream(JAR_FILE))) {
            ClassLoader loader = ForkServer.class.getClassLoader();
            for (Class<?> klass : getClasses("org.apache.tika")) {
                String path = klass.getName().replace('.', '/') + ".class";
                try (InputStream input = loader.getResourceAsStream(path)) {
                    jarOs.putNextEntry(new JarEntry(path));
                    IOUtils.copy(input, jarOs);
                }
            }
            try (InputStream input = ForkParserTikaBinTest.class.getResourceAsStream("/org/apache/tika/config/TIKA-2653-vowel-parser-ae.xml")) {
                jarOs.putNextEntry(new JarEntry("org/apache/tika/parser/TIKA-2653-vowel-parser-ae.xml"));
                IOUtils.copy(input, jarOs);
            }
            try (InputStream input = ForkParserTikaBinTest.class.getResourceAsStream("/org/apache/tika/mime/tika-mimetypes.xml")) {
                jarOs.putNextEntry(new JarEntry("org/apache/tika/mime/tika-mimetypes.xml"));
                IOUtils.copy(input, jarOs);
            }
            try (InputStream input = ForkParserTikaBinTest.class.getResourceAsStream("/org/apache/tika/mime/custom-mimetypes.xml")) {
                jarOs.putNextEntry(new JarEntry("org/apache/tika/mime/custom-mimetypes.xml"));
                IOUtils.copy(input, jarOs);
            }

            jarOs.putNextEntry(new JarEntry("META-INF/services/org.apache.tika.parser.Parser"));
            jarOs.write("org.apache.tika.parser.mock.VowelParser\n".getBytes(StandardCharsets.UTF_8));
        }

        Path tikaConfigVowelParser = JAR_DIR.resolve("TIKA_2653-iou.xml");
        try (InputStream is = ForkServer.class.getResourceAsStream("/org/apache/tika/config/TIKA-2653-vowel-parser-iou.xml");
             OutputStream os = Files.newOutputStream(tikaConfigVowelParser)) {
            IOUtils.copy(is, os);
        }
    }


    @AfterClass
    public static void tearDown() throws Exception {

        Files.delete(JAR_DIR.resolve("TIKA_2653-iou.xml"));
        Files.delete(JAR_FILE);
        Files.delete(JAR_DIR);
    }

    @Test
    public void testExplicitParserFactory() throws Exception {
        XMLResult xmlResult = getXML(new ParserFactoryFactory("org.apache.tika.parser.mock.MockParserFactory",
                EMPTY_MAP));
        assertContains("hello world!", xmlResult.xml);
        assertEquals("Nikolai Lobachevsky", xmlResult.metadata.get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testVowelParserAsDefault() throws Exception {
        ParserFactoryFactory pff = new ParserFactoryFactory(
                "org.apache.tika.parser.AutoDetectParserFactory",
                EMPTY_MAP);
        XMLResult xmlResult = getXML(pff);
        assertContains("eooeuiooueoeeao", xmlResult.xml);
        assertEquals("Nikolai Lobachevsky", xmlResult.metadata.get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testVowelParserInClassPath() throws Exception {
        Map<String, String> args = new HashMap<>();
        args.put(AutoDetectParserFactory.TIKA_CONFIG_PATH, "TIKA-2653-vowel-parser-ae.xml");
        ParserFactoryFactory pff = new ParserFactoryFactory(
                "org.apache.tika.parser.AutoDetectParserFactory",
                args);
        XMLResult xmlResult = getXML(pff);
        assertContains("eeeeea", xmlResult.xml);
        assertEquals("Nikolai Lobachevsky", xmlResult.metadata.get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testVowelParserFromDirectory() throws Exception {
        Map<String, String> args = new HashMap<>();
        args.put(AutoDetectParserFactory.TIKA_CONFIG_PATH, JAR_DIR.resolve("TIKA_2653-iou.xml").toAbsolutePath().toString());
        ParserFactoryFactory pff = new ParserFactoryFactory(
                "org.apache.tika.parser.AutoDetectParserFactory",
                args);
        XMLResult xmlResult = getXML(pff);
        assertContains("oouioouoo", xmlResult.xml);
        assertEquals("Nikolai Lobachevsky", xmlResult.metadata.get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testPFFWithClassLoaderFromParentProcess() throws Exception {
        //The UpperCasingContentHandler is not sent to the bootstrap test jar file in @BeforeClass.
        //this tests that the content handler was loaded from the parent process.

        ParserFactoryFactory pff = new ParserFactoryFactory(
                "org.apache.tika.parser.AutoDetectParserFactory",
                EMPTY_MAP);
        XMLResult xmlResult = getXML(pff, this.getClass().getClassLoader(), new UpperCasingContentHandler());
        assertContains("EOOEUIOOUEOEEAO", xmlResult.xml);
        assertEquals("Nikolai Lobachevsky", xmlResult.metadata.get(TikaCoreProperties.CREATOR));

    }

    private XMLResult getXML(ParserFactoryFactory pff) throws TikaException, SAXException, IOException {
        return getXML(pff, null, null);
    }

    private XMLResult getXML(ParserFactoryFactory pff, ClassLoader classloader, ContentHandler contentHandler) throws TikaException, SAXException, IOException {

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

        ContentHandler handler = (contentHandler == null) ? new ToXMLContentHandler() : contentHandler;
        Metadata m = new Metadata();
        try (InputStream is = getClass().getResourceAsStream("/test-documents/example.xml")) {
            parser.parse(is, handler, m, new ParseContext());
        } finally {
            parser.close();
        }
        return new XMLResult(handler.toString(), m);
    }

    private static List<Class> getClasses(String packageName)
            throws ClassNotFoundException, IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile().replaceAll("%20", " ")));
        }
        ArrayList<Class> classes = new ArrayList<>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes;
    }

    private static List<Class> findClasses(File dir, String packageName) throws ClassNotFoundException {
        List<Class> classes = new ArrayList<>();
        if (!dir.exists()) {
            return classes;
        }
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                //exclude TypeDetectionBenchmark because it is not serializable
                //exclude UpperCasingContentHandler because we want to test that
                //we can serialize it from the parent process into the child process
                if (! file.getName().contains("TypeDetectionBenchmark") &&
                        !file.getName().contains("UpperCasingContentHandler")) {
                    classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
                }
            }
        }
        return classes;
    }
}
