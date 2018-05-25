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
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserFactory;
import org.apache.tika.parser.ParserFactoryFactory;
import org.apache.tika.parser.mock.MockParser;
import org.apache.tika.parser.mock.MockParserFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToHTMLContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ForkParserTikaBinTest extends TikaTest {
    private static Path JAR_DIR;
    private static final String JAR_FILE_NAME = "mock-tika-app.jar";
    private static Path JAR_FILE;

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
        }
    }


    @AfterClass
    public static void tearDown() throws Exception {
        Files.delete(JAR_FILE);
        Files.delete(JAR_DIR);
    }

    @Test
    public void testHelloWorld() throws Exception {
        ParserFactoryFactory pff = new ParserFactoryFactory("org.apache.tika.parser.mock.MockParserFactory",
                Collections.EMPTY_MAP);
        List<String> java = new ArrayList<>();
        java.add("java");
        ForkParser parser = new ForkParser(JAR_DIR, pff);
        parser.setJavaCommand(java);
        parser.setServerPulseMillis(10000);

        ContentHandler contentHandler = new ToHTMLContentHandler();
        Metadata m = new Metadata();
        try (InputStream is = getClass().getResourceAsStream("/test-documents/example.xml")) {
            parser.parse(is,contentHandler, m, new ParseContext());
        } finally {
            parser.close();
        }
        assertContains("hello world!", contentHandler.toString());
        assertEquals("Nikolai Lobachevsky", m.get(TikaCoreProperties.CREATOR));
    }

    private static List<Class> getClasses(String packageName)
            throws ClassNotFoundException, IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile().replaceAll("%20", " ")));
        }
        ArrayList classes = new ArrayList();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes;
    }

    private static List<Class> findClasses(File dir, String packageName) throws ClassNotFoundException {
        List<Class> classes = new ArrayList();
        if (!dir.exists()) {
            return classes;
        }
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                assert !file.getName().contains(".");
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
            }
        }
        return classes;
    }
}
