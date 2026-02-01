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
package org.apache.tika.pipes.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.extractor.UnpackSelector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.extractor.StandardUnpackSelector;
import org.apache.tika.pipes.core.extractor.UnpackConfig;

/**
 * Tests for Frictionless Data Package output format in UNPACK mode.
 *
 * The Frictionless Data format produces a datapackage.json manifest file
 * along with embedded files organized in an unpacked/ subdirectory.
 *
 * Output structure:
 * <pre>
 * output/
 * ├── datapackage.json      # Frictionless manifest with file list, hashes, mimetypes
 * ├── metadata.json         # Full RMETA-style metadata (optional)
 * └── unpacked/
 *     ├── 00000001.xml
 *     ├── 00000002.xml
 *     └── ...
 * </pre>
 */
public class FrictionlessUnpackTest {

    private static final String FETCHER_NAME = "fsf";
    private static final String EMITTER_NAME = "fse";
    private static final String TEST_DOC_WITH_EMBEDDED = "mock-embedded.xml";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PipesClient init(Path tmp, String testFileName) throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                tmp, tmp.resolve("input"), tmp.resolve("output"));
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testFileName);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        return new PipesClient(pipesConfig, tikaConfigPath);
    }

    @Test
    public void testFrictionlessZippedOutput(@TempDir Path tmp) throws Exception {
        // Test that FRICTIONLESS format with ZIPPED output mode creates correct structure
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(EMITTER_NAME);
        unpackConfig.setOutputFormat(UnpackConfig.OUTPUT_FORMAT.FRICTIONLESS);
        unpackConfig.setOutputMode(UnpackConfig.OUTPUT_MODE.ZIPPED);
        unpackConfig.setZeroPadName(8);
        unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(EMITTER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(),
                "FRICTIONLESS ZIPPED mode should succeed. Status: " + pipesResult.status() +
                        ", Message: " + pipesResult.message());

        // Find the frictionless zip file
        List<Path> zipFiles = Files.list(outputDir)
                .filter(p -> p.toString().endsWith("-frictionless.zip"))
                .toList();

        assertEquals(1, zipFiles.size(),
                "Should create exactly one frictionless zip. Found: " +
                        Files.list(outputDir).map(p -> p.getFileName().toString()).toList());

        Path zipFile = zipFiles.get(0);
        assertTrue(Files.size(zipFile) > 0, "Zip file should not be empty");

        // Verify zip structure
        Set<String> zipEntries = new HashSet<>();
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                zipEntries.add(entry.getName());
            }
        }

        // Should contain datapackage.json at root
        assertTrue(zipEntries.contains("datapackage.json"),
                "Zip should contain datapackage.json. Found: " + zipEntries);

        // Embedded files should be under unpacked/
        long unpackedCount = zipEntries.stream()
                .filter(e -> e.startsWith("unpacked/") && !e.equals("unpacked/"))
                .count();
        assertTrue(unpackedCount >= 4,
                "Should have at least 4 embedded files in unpacked/. Found: " + zipEntries);
    }

    @Test
    public void testFrictionlessDirectoryOutput(@TempDir Path tmp) throws Exception {
        // Test that FRICTIONLESS format with DIRECTORY output mode emits files directly
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(EMITTER_NAME);
        unpackConfig.setOutputFormat(UnpackConfig.OUTPUT_FORMAT.FRICTIONLESS);
        unpackConfig.setOutputMode(UnpackConfig.OUTPUT_MODE.DIRECTORY);
        unpackConfig.setZeroPadName(8);
        unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(EMITTER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(),
                "FRICTIONLESS DIRECTORY mode should succeed. Status: " + pipesResult.status() +
                        ", Message: " + pipesResult.message());

        // Check that datapackage.json exists in output
        List<Path> dataPackageFiles = Files.walk(outputDir)
                .filter(p -> p.getFileName().toString().equals("datapackage.json") ||
                        p.toString().contains("datapackage.json"))
                .toList();

        assertFalse(dataPackageFiles.isEmpty(),
                "Should create datapackage.json. Output dir contents: " +
                        Files.walk(outputDir).map(p -> outputDir.relativize(p).toString()).toList());

        // Check that unpacked files were emitted
        List<Path> unpackedFiles = Files.walk(outputDir)
                .filter(p -> p.toString().contains("unpacked") ||
                        (Files.isRegularFile(p) && !p.toString().endsWith(".json")))
                .filter(Files::isRegularFile)
                .toList();

        assertTrue(unpackedFiles.size() >= 4,
                "Should have at least 4 embedded files. Found: " + unpackedFiles);
    }

    @Test
    public void testDataPackageJsonFormat(@TempDir Path tmp) throws Exception {
        // Test that datapackage.json has correct Frictionless schema
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(EMITTER_NAME);
        unpackConfig.setOutputFormat(UnpackConfig.OUTPUT_FORMAT.FRICTIONLESS);
        unpackConfig.setOutputMode(UnpackConfig.OUTPUT_MODE.ZIPPED);
        unpackConfig.setZeroPadName(8);
        unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(EMITTER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(), "Processing should succeed");

        // Extract and parse datapackage.json from zip
        List<Path> zipFiles = Files.list(outputDir)
                .filter(p -> p.toString().endsWith("-frictionless.zip"))
                .toList();
        assertEquals(1, zipFiles.size());

        JsonNode dataPackage;
        try (ZipFile zip = new ZipFile(zipFiles.get(0).toFile())) {
            ZipEntry dpEntry = zip.getEntry("datapackage.json");
            assertNotNull(dpEntry, "datapackage.json should exist in zip");
            try (InputStream is = zip.getInputStream(dpEntry)) {
                dataPackage = OBJECT_MAPPER.readTree(is);
            }
        }

        // Verify required Frictionless fields
        assertTrue(dataPackage.has("name"),
                "datapackage.json must have 'name' field");
        assertTrue(dataPackage.has("resources"),
                "datapackage.json must have 'resources' array");
        assertTrue(dataPackage.has("created"),
                "datapackage.json should have 'created' timestamp");

        // Verify resources array
        JsonNode resources = dataPackage.get("resources");
        assertTrue(resources.isArray(), "resources should be an array");
        assertTrue(resources.size() >= 4,
                "Should have at least 4 resources. Found: " + resources.size());

        // Verify each resource has required fields
        for (JsonNode resource : resources) {
            assertTrue(resource.has("path"),
                    "Each resource must have 'path': " + resource);
            assertTrue(resource.has("mediatype"),
                    "Each resource must have 'mediatype': " + resource);
            assertTrue(resource.has("bytes"),
                    "Each resource must have 'bytes' (file size): " + resource);
            assertTrue(resource.has("hash"),
                    "Each resource must have 'hash' (SHA256): " + resource);

            // Verify path starts with unpacked/
            String path = resource.get("path").asText();
            assertTrue(path.startsWith("unpacked/"),
                    "Resource path should start with 'unpacked/': " + path);

            // Verify hash format (sha256:...)
            String hash = resource.get("hash").asText();
            assertTrue(hash.startsWith("sha256:"),
                    "Hash should start with 'sha256:': " + hash);
            // SHA256 hex is 64 characters
            assertEquals(64, hash.substring(7).length(),
                    "SHA256 hex should be 64 characters: " + hash);
        }
    }

    @Test
    public void testSHA256HashCorrectness(@TempDir Path tmp) throws Exception {
        // Test that SHA256 hashes in datapackage.json are correct
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(EMITTER_NAME);
        unpackConfig.setOutputFormat(UnpackConfig.OUTPUT_FORMAT.FRICTIONLESS);
        unpackConfig.setOutputMode(UnpackConfig.OUTPUT_MODE.ZIPPED);
        unpackConfig.setZeroPadName(8);
        unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(EMITTER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(), "Processing should succeed");

        // Extract datapackage.json and verify hashes against actual file content
        List<Path> zipFiles = Files.list(outputDir)
                .filter(p -> p.toString().endsWith("-frictionless.zip"))
                .toList();

        try (ZipFile zip = new ZipFile(zipFiles.get(0).toFile())) {
            // Parse datapackage.json
            ZipEntry dpEntry = zip.getEntry("datapackage.json");
            JsonNode dataPackage;
            try (InputStream is = zip.getInputStream(dpEntry)) {
                dataPackage = OBJECT_MAPPER.readTree(is);
            }

            // Verify each resource hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (JsonNode resource : dataPackage.get("resources")) {
                String path = resource.get("path").asText();
                String expectedHash = resource.get("hash").asText();

                ZipEntry fileEntry = zip.getEntry(path);
                assertNotNull(fileEntry, "File should exist in zip: " + path);

                // Compute actual hash
                digest.reset();
                try (InputStream is = zip.getInputStream(fileEntry)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        digest.update(buffer, 0, read);
                    }
                }
                String actualHash = "sha256:" + bytesToHex(digest.digest());

                assertEquals(expectedHash, actualHash,
                        "SHA256 hash mismatch for " + path);
            }
        }
    }

    @Test
    public void testIncludeMetadataJson(@TempDir Path tmp) throws Exception {
        // Test that includeFullMetadata=true creates metadata.json with RMETA-style output
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(EMITTER_NAME);
        unpackConfig.setOutputFormat(UnpackConfig.OUTPUT_FORMAT.FRICTIONLESS);
        unpackConfig.setOutputMode(UnpackConfig.OUTPUT_MODE.ZIPPED);
        unpackConfig.setIncludeFullMetadata(true);  // Include metadata.json
        unpackConfig.setZeroPadName(8);
        unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(EMITTER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(),
                "Processing with includeFullMetadata should succeed. Status: " +
                        pipesResult.status() + ", Message: " + pipesResult.message());

        // Find the zip file and verify metadata.json exists
        List<Path> zipFiles = Files.list(outputDir)
                .filter(p -> p.toString().endsWith("-frictionless.zip"))
                .toList();
        assertEquals(1, zipFiles.size());

        Set<String> zipEntries = new HashSet<>();
        JsonNode metadataJson = null;
        try (ZipFile zip = new ZipFile(zipFiles.get(0).toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                zipEntries.add(entry.getName());
            }

            // Extract metadata.json
            ZipEntry metadataEntry = zip.getEntry("metadata.json");
            if (metadataEntry != null) {
                try (InputStream is = zip.getInputStream(metadataEntry)) {
                    metadataJson = OBJECT_MAPPER.readTree(is);
                }
            }
        }

        assertTrue(zipEntries.contains("metadata.json"),
                "Zip should contain metadata.json when includeFullMetadata=true. Found: " + zipEntries);

        assertNotNull(metadataJson, "metadata.json should be parseable JSON");

        // metadata.json should be an array (RMETA-style)
        assertTrue(metadataJson.isArray(),
                "metadata.json should be an array of metadata objects");

        // Should have container + 4 embedded docs = 5 entries
        assertTrue(metadataJson.size() >= 5,
                "metadata.json should have at least 5 entries (container + 4 embedded). Found: " +
                        metadataJson.size());

        // Verify first entry has author from container
        JsonNode containerMeta = metadataJson.get(0);
        assertTrue(containerMeta.has("author") || containerMeta.has("dc:creator"),
                "Container metadata should have author field: " + containerMeta);
    }

    @Test
    public void testAutoAddSHA256Digester(@TempDir Path tmp) throws Exception {
        // Test that SHA256 digester is auto-added when no DigesterFactory is configured
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        // No DigesterFactory in parseContext - should auto-add SHA256
        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(EMITTER_NAME);
        unpackConfig.setOutputFormat(UnpackConfig.OUTPUT_FORMAT.FRICTIONLESS);
        unpackConfig.setOutputMode(UnpackConfig.OUTPUT_MODE.ZIPPED);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(EMITTER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(),
                "Processing without DigesterFactory should succeed");

        // Verify hashes are still computed (auto-added digester)
        List<Path> zipFiles = Files.list(outputDir)
                .filter(p -> p.toString().endsWith("-frictionless.zip"))
                .toList();

        try (ZipFile zip = new ZipFile(zipFiles.get(0).toFile())) {
            ZipEntry dpEntry = zip.getEntry("datapackage.json");
            JsonNode dataPackage;
            try (InputStream is = zip.getInputStream(dpEntry)) {
                dataPackage = OBJECT_MAPPER.readTree(is);
            }

            // Verify resources have hashes
            for (JsonNode resource : dataPackage.get("resources")) {
                assertTrue(resource.has("hash"),
                        "Resource should have hash even without explicit DigesterFactory: " + resource);
                String hash = resource.get("hash").asText();
                assertTrue(hash.startsWith("sha256:"),
                        "Hash should be SHA256: " + hash);
            }
        }
    }

    @Test
    public void testWithUnpackSelector(@TempDir Path tmp) throws Exception {
        // Test that UnpackSelector filtering works with Frictionless format
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        // Use a config that includes UnpackSelector
        Path configBase = tmp;
        Path inputDir = tmp.resolve("input");
        Path pipesConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                configBase, inputDir, outputDir);
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, TEST_DOC_WITH_EMBEDDED);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(pipesConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        PipesClient pipesClient = new PipesClient(pipesConfig, pipesConfigPath);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(EMITTER_NAME);
        unpackConfig.setOutputFormat(UnpackConfig.OUTPUT_FORMAT.FRICTIONLESS);
        unpackConfig.setOutputMode(UnpackConfig.OUTPUT_MODE.ZIPPED);
        parseContext.set(UnpackConfig.class, unpackConfig);

        // Add selector to only include XML files
        // (The mock embedded files are XML)
        StandardUnpackSelector selector = new StandardUnpackSelector();
        selector.setIncludeMimeTypes(Set.of("application/mock+xml", "application/xml", "text/xml"));
        parseContext.set(UnpackSelector.class, selector);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(EMITTER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(),
                "Processing with UnpackSelector should succeed");

        // Verify that filtering was applied
        List<Path> zipFiles = Files.list(outputDir)
                .filter(p -> p.toString().endsWith("-frictionless.zip"))
                .toList();

        if (!zipFiles.isEmpty()) {
            try (ZipFile zip = new ZipFile(zipFiles.get(0).toFile())) {
                ZipEntry dpEntry = zip.getEntry("datapackage.json");
                if (dpEntry != null) {
                    JsonNode dataPackage;
                    try (InputStream is = zip.getInputStream(dpEntry)) {
                        dataPackage = OBJECT_MAPPER.readTree(is);
                    }

                    // All resources should be XML (mock+xml)
                    for (JsonNode resource : dataPackage.get("resources")) {
                        String mediatype = resource.get("mediatype").asText();
                        assertTrue(mediatype.contains("xml") || mediatype.contains("mock"),
                                "Filtered resources should only include XML. Found: " + mediatype);
                    }
                }
            }
        }
    }

    @Test
    public void testRegularFormatUnchanged(@TempDir Path tmp) throws Exception {
        // Test that OUTPUT_FORMAT.REGULAR (default) still works as before
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(EMITTER_NAME);
        unpackConfig.setOutputFormat(UnpackConfig.OUTPUT_FORMAT.REGULAR);  // Explicit REGULAR
        unpackConfig.setZipEmbeddedFiles(true);  // Use zip output for comparison
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(EMITTER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(),
                "REGULAR format should still work. Status: " + pipesResult.status());

        // Should create -embedded.zip (not -frictionless.zip)
        List<Path> regularZips = Files.list(outputDir)
                .filter(p -> p.toString().endsWith("-embedded.zip"))
                .toList();

        List<Path> frictionlessZips = Files.list(outputDir)
                .filter(p -> p.toString().endsWith("-frictionless.zip"))
                .toList();

        assertEquals(1, regularZips.size(),
                "REGULAR format should create -embedded.zip");
        assertEquals(0, frictionlessZips.size(),
                "REGULAR format should not create -frictionless.zip");
    }

    @Test
    public void testFrictionlessWithNoEmbeddedFiles(@TempDir Path tmp) throws Exception {
        // Test Frictionless format with document that has no embedded files
        String simpleDoc = "mock_times.xml";
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        PipesClient pipesClient = init(tmp, simpleDoc);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(EMITTER_NAME);
        unpackConfig.setOutputFormat(UnpackConfig.OUTPUT_FORMAT.FRICTIONLESS);
        unpackConfig.setOutputMode(UnpackConfig.OUTPUT_MODE.ZIPPED);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(simpleDoc,
                        new FetchKey(FETCHER_NAME, simpleDoc),
                        new EmitKey(EMITTER_NAME, simpleDoc),
                        new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(),
                "Frictionless should succeed with no embedded files");

        // Should either not create zip or create zip with empty resources
        List<Path> zipFiles = Files.list(outputDir)
                .filter(p -> p.toString().endsWith("-frictionless.zip"))
                .toList();

        if (!zipFiles.isEmpty()) {
            try (ZipFile zip = new ZipFile(zipFiles.get(0).toFile())) {
                ZipEntry dpEntry = zip.getEntry("datapackage.json");
                if (dpEntry != null) {
                    JsonNode dataPackage;
                    try (InputStream is = zip.getInputStream(dpEntry)) {
                        dataPackage = OBJECT_MAPPER.readTree(is);
                    }
                    // Resources should be empty or contain only original if included
                    assertTrue(dataPackage.get("resources").isEmpty() ||
                                    dataPackage.get("resources").size() <= 1,
                            "Document with no embedded files should have empty or minimal resources");
                }
            }
        }
    }

    @Test
    public void testFrictionlessWithIncludeOriginal(@TempDir Path tmp) throws Exception {
        // Test that includeOriginal works with Frictionless format
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(EMITTER_NAME);
        unpackConfig.setOutputFormat(UnpackConfig.OUTPUT_FORMAT.FRICTIONLESS);
        unpackConfig.setOutputMode(UnpackConfig.OUTPUT_MODE.ZIPPED);
        unpackConfig.setIncludeOriginal(true);  // Include container document
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(EMITTER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(),
                "Frictionless with includeOriginal should succeed");

        List<Path> zipFiles = Files.list(outputDir)
                .filter(p -> p.toString().endsWith("-frictionless.zip"))
                .toList();

        try (ZipFile zip = new ZipFile(zipFiles.get(0).toFile())) {
            // Original file should be at root level or in a specific location
            boolean hasOriginal = false;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                // Original could be at root or documented location
                if (entry.getName().contains(TEST_DOC_WITH_EMBEDDED) ||
                        entry.getName().equals("original/" + TEST_DOC_WITH_EMBEDDED)) {
                    hasOriginal = true;
                    break;
                }
            }

            // Also check datapackage.json for original in resources
            ZipEntry dpEntry = zip.getEntry("datapackage.json");
            if (dpEntry != null) {
                JsonNode dataPackage;
                try (InputStream is = zip.getInputStream(dpEntry)) {
                    dataPackage = OBJECT_MAPPER.readTree(is);
                }
                for (JsonNode resource : dataPackage.get("resources")) {
                    String path = resource.get("path").asText();
                    if (!path.startsWith("unpacked/")) {
                        hasOriginal = true;
                        break;
                    }
                }
            }

            assertTrue(hasOriginal,
                    "With includeOriginal=true, original document should be in package");
        }
    }

    /**
     * Helper to convert bytes to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
