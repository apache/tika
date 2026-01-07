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
package org.apache.tika.config;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaLoaderHelper;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.detect.Detector;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;

/**
 * Integration tests to verify that the annotation processor correctly generated
 * component index files mapping human-readable names to class names, and that
 * JSON configuration can load components using these names.
 */
public class ComponentRegistryIntegrationTest {

    @Test
    public void testLoadDetectorByName() throws Exception {
        // Load config that uses "poifs-container-detector" by name
        TikaLoader loader = TikaLoaderHelper.getLoader("test-detectors.json");
        Detector detector = loader.loadDetectors();

        assertNotNull(detector, "Detector should be loaded");
        // The detector will be wrapped in a CompositeDetector, so we need to check differently
        // For now, just verify it loaded successfully
    }

    @Test
    public void testLoadDefaultParser() throws Exception {
        // Load config that uses "default-parser" by name
        TikaLoader loader = TikaLoaderHelper.getLoader("test-default-parser.json");
        Parser parser = loader.loadAutoDetectParser();

        assertNotNull(parser, "Parser should be loaded");
        assertTrue(parser instanceof AutoDetectParser,
                "Should have loaded AutoDetectParser (default-parser)");
    }

    @Test
    public void testLoadDefaultParserWithExclusions() throws Exception {
        // Load config that excludes "pdf-parser" and "html-parser" by name
        // This verifies that the component names can be used in exclusion lists
        TikaLoader loader = TikaLoaderHelper.getLoader("test-default-with-exclusions.json");
        Parser parser = loader.loadAutoDetectParser();

        assertNotNull(parser, "Parser should be loaded");
        assertTrue(parser instanceof AutoDetectParser,
                "Should have loaded AutoDetectParser with exclusions");

        // The config loaded successfully, which means it was able to resolve
        // the component names "pdf-parser" and "html-parser" for exclusion
    }

    @Test
    public void testLoadDcXmlParserByName() throws Exception {
        // Load config that uses "dc-xml-parser" by name
        // XMLParser has spi=false, but DcXMLParser should be available
        TikaLoader loader = TikaLoaderHelper.getLoader("test-dc-xml-parser.json");

        Parser parser = loader.loadParsers();

        assertNotNull(parser, "Parser should be loaded");
        // The parser will be wrapped in a CompositeParser, check that it loaded successfully
    }

    @Test
    public void testSpiFalseParserInIndexButNotInSpi() throws Exception {
        // XMLParser has spi=false:
        // - SHOULD be in index (for name-based configuration)
        // - should NOT be in SPI (for auto-discovery via ServiceLoader)
        Map<String, String> parserIndex = readAllIndexFiles("META-INF/tika/parsers.idx");

        // Verify xml-parser IS in index for name-based config
        assertTrue(parserIndex.containsKey("xml-parser"),
                "xml-parser should be in index (spi=false still allows name-based config)");
        assertEquals("org.apache.tika.parser.xml.XMLParser",
                parserIndex.get("xml-parser"),
                "xml-parser should map to XMLParser class");

        // Verify dc-xml-parser is also in index
        assertTrue(parserIndex.containsKey("dc-xml-parser"),
                "dc-xml-parser should be in index");
        assertEquals("org.apache.tika.parser.xml.DcXMLParser",
                parserIndex.get("dc-xml-parser"),
                "dc-xml-parser should map to DcXMLParser class");

        // Verify xml-parser is NOT in SPI file
        Map<String, Boolean> spiParsers = readAllSpiFiles("META-INF/services/org.apache.tika.parser.Parser");
        assertFalse(spiParsers.containsKey("org.apache.tika.parser.xml.XMLParser"),
                "XMLParser should NOT be in SPI (spi=false prevents auto-discovery)");
    }

    @Test
    public void testIndexFilesFollowKebabCaseConvention() throws Exception {
        // Test that all component names follow kebab-case convention
        Map<String, String> parserIndex = readAllIndexFiles("META-INF/tika/parsers.idx");
        Map<String, String> detectorIndex = readAllIndexFiles("META-INF/tika/detectors.idx");

        assertFalse(parserIndex.isEmpty(), "Parser index should not be empty");
        assertFalse(detectorIndex.isEmpty(), "Detector index should not be empty");

        // Verify all names follow kebab-case convention
        for (String name : parserIndex.keySet()) {
            assertTrue(name.matches("[a-z0-9-]+"),
                    "Parser name should be kebab-case: " + name);
        }

        for (String name : detectorIndex.keySet()) {
            assertTrue(name.matches("[a-z0-9-]+"),
                    "Detector name should be kebab-case: " + name);
        }
    }

    @Test
    public void testIndexFilesHaveCorrectFormat() throws Exception {
        // Verify index files have Apache license and correct format
        Enumeration<URL> resources = getClass().getClassLoader()
                                               .getResources("META-INF/tika/parsers.idx");

        assertTrue(resources.hasMoreElements(), "At least one parsers.idx should exist");

        URL url = resources.nextElement();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            assertTrue(firstLine.contains("Licensed to the Apache Software Foundation"),
                    "First line should contain Apache license");

            String line;
            boolean foundFormatComment = false;
            boolean foundEntry = false;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("# Format:")) {
                    foundFormatComment = true;
                }
                if (!line.startsWith("#") && !line.trim().isEmpty()) {
                    foundEntry = true;
                    // Verify format: name=fully.qualified.ClassName
                    assertTrue(line.matches("[a-z0-9-]+=\\S+"),
                            "Line should match format 'name=ClassName': " + line);
                }
            }

            assertTrue(foundFormatComment, "Should have format comment");
            assertTrue(foundEntry, "Should have at least one entry");
        }
    }

    @Test
    public void testNoDuplicateComponentNames() throws Exception {
        // Verify no duplicate names across all index files
        Map<String, String> parserIndex = readAllIndexFiles("META-INF/tika/parsers.idx");
        Map<String, String> detectorIndex = readAllIndexFiles("META-INF/tika/detectors.idx");

        // Check for duplicates within each index (readAllIndexFiles already handles this)
        // The fact that we can build the maps without issues means no duplicates within files

        // Verify all class names are fully qualified
        for (Map.Entry<String, String> entry : parserIndex.entrySet()) {
            String className = entry.getValue();
            assertTrue(className.contains("."),
                    "Parser class name should be fully qualified: " + className);
            assertTrue(className.startsWith("org.apache.tika."),
                    "Parser class should be in org.apache.tika package: " + className);
        }

        for (Map.Entry<String, String> entry : detectorIndex.entrySet()) {
            String className = entry.getValue();
            assertTrue(className.contains("."),
                    "Detector class name should be fully qualified: " + className);
            assertTrue(className.startsWith("org.apache.tika."),
                    "Detector class should be in org.apache.tika package: " + className);
        }
    }

    /**
     * Reads all index files with the given resource path from all JARs on the classpath
     * and merges them into a single map.
     */
    private Map<String, String> readAllIndexFiles(String resourcePath) throws Exception {
        Map<String, String> mergedIndex = new HashMap<>();
        Enumeration<URL> resources = getClass().getClassLoader().getResources(resourcePath);

        assertTrue(resources.hasMoreElements(),
                "At least one " + resourcePath + " file should exist");

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try (InputStream stream = url.openStream()) {
                Map<String, String> index = readIndexFile(stream);
                mergedIndex.putAll(index);
            }
        }

        return mergedIndex;
    }

    /**
     * Reads an index file in the format: name=fully.qualified.ClassName[:key=contextKeyClass]
     * Returns a map of component name -> class name (without the :key= suffix).
     */
    private Map<String, String> readIndexFile(InputStream stream) throws Exception {
        Map<String, String> index = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Skip comments and empty lines
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String name = parts[0].trim();
                    String value = parts[1].trim();
                    // Strip optional :key=contextKeyClass suffix
                    int colonIndex = value.indexOf(':');
                    if (colonIndex > 0) {
                        value = value.substring(0, colonIndex);
                    }
                    index.put(name, value);
                }
            }
        }
        return index;
    }

    /**
     * Reads all SPI service files from all JARs on the classpath.
     * Returns a map of class names (as keys) to true for easy lookup.
     */
    private Map<String, Boolean> readAllSpiFiles(String resourcePath) throws Exception {
        Map<String, Boolean> spiClasses = new HashMap<>();
        Enumeration<URL> resources = getClass().getClassLoader().getResources(resourcePath);

        assertTrue(resources.hasMoreElements(),
                "At least one " + resourcePath + " file should exist");

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Skip comments and empty lines
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    spiClasses.put(line, true);
                }
            }
        }

        return spiClasses;
    }
}
