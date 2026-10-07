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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
    private static final String SIMPLE_DOC = "mock_times.xml";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    static Path tmp;

    // emitter is onExists=EXCEPTION: each test emits under its own subdirectory of output
    private static PipesClient pipesClient;

    @BeforeAll
    public static void setUp() throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                tmp, tmp.resolve("input"), tmp.resolve("output"));
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, TEST_DOC_WITH_EMBEDDED, SIMPLE_DOC);
        pipesClient = new PipesClient(PipesConfig.load(TikaJsonConfig.load(tikaConfigPath)), tikaConfigPath);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (pipesClient != null) {
            pipesClient.close();
        }
    }

    private static Path outputDir(String testName) {
        return tmp.resolve("output").resolve(testName);
    }

    private static PipesResult process(String testName, String doc, ParseContext parseContext) throws Exception {
        return pipesClient.process(
                new FetchEmitTuple(doc,
                        new FetchKey(FETCHER_NAME, doc),
                        new EmitKey(EMITTER_NAME, testName + "/" + doc),
                        new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));
    }

    private static ParseContext frictionless(UnpackConfig.OUTPUT_MODE outputMode, boolean paddedDetected) {
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);
        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(EMITTER_NAME);
        unpackConfig.setOutputFormat(UnpackConfig.OUTPUT_FORMAT.FRICTIONLESS);
        unpackConfig.setOutputMode(outputMode);
        if (paddedDetected) {
            unpackConfig.setZeroPadName(8);
            unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
        }
        parseContext.set(UnpackConfig.class, unpackConfig);
        return parseContext;
    }

    /** TIKA-4681: an UnpackConfig that says nothing about format is a Frictionless package. */
    @Test
    public void testDefaultFormatIsFrictionless() throws Exception {
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);
        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(EMITTER_NAME);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = process("defaultFormat", TEST_DOC_WITH_EMBEDDED, parseContext);
        assertTrue(pipesResult.isSuccess(), "Status: " + pipesResult.status()
                + ", Message: " + pipesResult.message());

        Path outputDir = outputDir("defaultFormat");
        List<String> names;
        try (Stream<Path> files = Files.list(outputDir)) {
            names = files.map(p -> p.getFileName().toString()).toList();
        }
        assertTrue(names.contains(TEST_DOC_WITH_EMBEDDED + "-frictionless.zip"), names.toString());
        assertFalse(names.contains(TEST_DOC_WITH_EMBEDDED + "-embedded.zip"), names.toString());
        Set<String> entries = zipEntries(onlyFrictionlessZip(outputDir));
        assertTrue(entries.contains("datapackage.json"), entries.toString());
        assertTrue(entries.contains("metadata.json"), "a package carries metadata.json by default: " + entries);
    }

    @Test
    public void testFrictionlessZippedOutput() throws Exception {
        // ZIPPED structure, datapackage.json schema, and SHA256 correctness, from one parse
        PipesResult pipesResult = process("zipped", TEST_DOC_WITH_EMBEDDED,
                frictionless(UnpackConfig.OUTPUT_MODE.ZIPPED, true));
        assertTrue(pipesResult.isSuccess(),
                "FRICTIONLESS ZIPPED mode should succeed. Status: " + pipesResult.status() +
                        ", Message: " + pipesResult.message());

        Path zipFile = onlyFrictionlessZip(outputDir("zipped"));
        assertTrue(Files.size(zipFile) > 0, "Zip file should not be empty");

        Set<String> zipEntries = zipEntries(zipFile);
        assertTrue(zipEntries.contains("datapackage.json"),
                "Zip should contain datapackage.json. Found: " + zipEntries);
        long unpackedCount = zipEntries.stream()
                .filter(e -> e.startsWith("unpacked/") && !e.equals("unpacked/"))
                .count();
        assertTrue(unpackedCount >= 4,
                "Should have at least 4 embedded files in unpacked/. Found: " + zipEntries);

        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            JsonNode dataPackage;
            try (InputStream is = zip.getInputStream(zip.getEntry("datapackage.json"))) {
                dataPackage = OBJECT_MAPPER.readTree(is);
            }

            assertTrue(dataPackage.has("name"),
                    "datapackage.json must have 'name' field");
            assertTrue(dataPackage.has("resources"),
                    "datapackage.json must have 'resources' array");
            assertTrue(dataPackage.has("created"),
                    "datapackage.json should have 'created' timestamp");

            JsonNode resources = dataPackage.get("resources");
            assertTrue(resources.isArray(), "resources should be an array");
            assertTrue(resources.size() >= 4,
                    "Should have at least 4 resources. Found: " + resources.size());

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (JsonNode resource : resources) {
                assertTrue(resource.has("path"),
                        "Each resource must have 'path': " + resource);
                assertTrue(resource.has("mediatype"),
                        "Each resource must have 'mediatype': " + resource);
                assertTrue(resource.has("bytes"),
                        "Each resource must have 'bytes' (file size): " + resource);
                assertTrue(resource.has("hash"),
                        "Each resource must have 'hash' (SHA256): " + resource);

                String path = resource.get("path").asText();
                assertTrue(path.startsWith("unpacked/"),
                        "Resource path should start with 'unpacked/': " + path);

                String hash = resource.get("hash").asText();
                assertTrue(hash.startsWith("sha256:"),
                        "Hash should start with 'sha256:': " + hash);
                assertEquals(64, hash.substring(7).length(),
                        "SHA256 hex should be 64 characters: " + hash);

                ZipEntry fileEntry = zip.getEntry(path);
                assertNotNull(fileEntry, "File should exist in zip: " + path);
                digest.reset();
                try (InputStream is = zip.getInputStream(fileEntry)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        digest.update(buffer, 0, read);
                    }
                }
                assertEquals(hash, "sha256:" + bytesToHex(digest.digest()),
                        "SHA256 hash mismatch for " + path);
            }
        }
    }

    @Test
    public void testFrictionlessDirectoryOutput() throws Exception {
        // Test that FRICTIONLESS format with DIRECTORY output mode emits files directly
        PipesResult pipesResult = process("directory", TEST_DOC_WITH_EMBEDDED,
                frictionless(UnpackConfig.OUTPUT_MODE.DIRECTORY, true));
        assertTrue(pipesResult.isSuccess(),
                "FRICTIONLESS DIRECTORY mode should succeed. Status: " + pipesResult.status() +
                        ", Message: " + pipesResult.message());

        Path outputDir = outputDir("directory");
        List<Path> all;
        try (Stream<Path> files = Files.walk(outputDir)) {
            all = files.toList();
        }
        assertTrue(all.stream().anyMatch(p -> p.getFileName().toString().equals("datapackage.json")),
                "Should create datapackage.json. Output dir contents: " + all);

        List<Path> unpackedFiles = all.stream()
                .filter(p -> p.toString().contains("unpacked") ||
                        (Files.isRegularFile(p) && !p.toString().endsWith(".json")))
                .filter(Files::isRegularFile)
                .toList();
        assertTrue(unpackedFiles.size() >= 4,
                "Should have at least 4 embedded files. Found: " + unpackedFiles);
    }

    @Test
    public void testIncludeMetadataJson() throws Exception {
        // Test that includeFullMetadata=true creates metadata.json with RMETA-style output
        ParseContext parseContext = frictionless(UnpackConfig.OUTPUT_MODE.ZIPPED, true);
        parseContext.get(UnpackConfig.class).setIncludeFullMetadata(true);
        PipesResult pipesResult = process("fullMetadata", TEST_DOC_WITH_EMBEDDED, parseContext);
        assertTrue(pipesResult.isSuccess(),
                "Processing with includeFullMetadata should succeed. Status: " +
                        pipesResult.status() + ", Message: " + pipesResult.message());

        Path zipFile = onlyFrictionlessZip(outputDir("fullMetadata"));
        Set<String> zipEntries = zipEntries(zipFile);
        assertTrue(zipEntries.contains("metadata.json"),
                "Zip should contain metadata.json when includeFullMetadata=true. Found: " + zipEntries);

        JsonNode metadataJson;
        try (ZipFile zip = new ZipFile(zipFile.toFile());
                InputStream is = zip.getInputStream(zip.getEntry("metadata.json"))) {
            metadataJson = OBJECT_MAPPER.readTree(is);
        }
        assertNotNull(metadataJson, "metadata.json should be parseable JSON");
        assertTrue(metadataJson.isArray(),
                "metadata.json should be an array of metadata objects");
        // container + 4 embedded
        assertTrue(metadataJson.size() >= 5,
                "metadata.json should have at least 5 entries (container + 4 embedded). Found: " +
                        metadataJson.size());

        JsonNode containerMeta = metadataJson.get(0);
        assertTrue(containerMeta.has("author") || containerMeta.has("dc:creator"),
                "Container metadata should have author field: " + containerMeta);
    }

    @Test
    public void testWithUnpackSelector() throws Exception {
        ParseContext parseContext = frictionless(UnpackConfig.OUTPUT_MODE.ZIPPED, false);
        // every embedded file is application/mock+xml, so a PDF-only selector keeps none
        StandardUnpackSelector selector = new StandardUnpackSelector();
        selector.setIncludeMimeTypes(Set.of("application/pdf"));
        parseContext.set(UnpackSelector.class, selector);

        PipesResult pipesResult = process("selector", TEST_DOC_WITH_EMBEDDED, parseContext);
        assertTrue(pipesResult.isSuccess(),
                "Processing with UnpackSelector should succeed");

        assertNothingPackaged(outputDir("selector"), TEST_DOC_WITH_EMBEDDED);
    }

    @Test
    public void testRegularFormatUnchanged() throws Exception {
        // Test that OUTPUT_FORMAT.REGULAR (default) still works as before
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);
        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(EMITTER_NAME);
        unpackConfig.setOutputFormat(UnpackConfig.OUTPUT_FORMAT.REGULAR);
        unpackConfig.setZipEmbeddedFiles(true);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = process("regular", TEST_DOC_WITH_EMBEDDED, parseContext);
        assertTrue(pipesResult.isSuccess(),
                "REGULAR format should still work. Status: " + pipesResult.status());

        List<String> names;
        try (Stream<Path> files = Files.list(outputDir("regular"))) {
            names = files.map(p -> p.getFileName().toString()).toList();
        }
        assertEquals(1, names.stream().filter(n -> n.endsWith("-embedded.zip")).count(),
                "REGULAR format should create -embedded.zip: " + names);
        assertEquals(0, names.stream().filter(n -> n.endsWith("-frictionless.zip")).count(),
                "REGULAR format should not create -frictionless.zip: " + names);
    }

    @Test
    public void testFrictionlessWithNoEmbeddedFiles() throws Exception {
        PipesResult pipesResult = process("noEmbedded", SIMPLE_DOC,
                frictionless(UnpackConfig.OUTPUT_MODE.ZIPPED, false));
        assertTrue(pipesResult.isSuccess(),
                "Frictionless should succeed with no embedded files");

        assertNothingPackaged(outputDir("noEmbedded"), SIMPLE_DOC);
    }

    @Test
    public void testFrictionlessWithIncludeOriginal() throws Exception {
        // includeOriginal=true causes the container to appear in the Frictionless
        // package as "unpacked/0.<ext>" (added by ParseHandler._preParse via
        // unpackHandler.add(0, ...)) and to be listed once in datapackage.json.
        ParseContext parseContext = frictionless(UnpackConfig.OUTPUT_MODE.ZIPPED, false);
        parseContext.get(UnpackConfig.class).setIncludeOriginal(true);
        PipesResult pipesResult = process("includeOriginal", TEST_DOC_WITH_EMBEDDED, parseContext);
        assertTrue(pipesResult.isSuccess(),
                "Frictionless with includeOriginal should succeed");

        Path zipFile = onlyFrictionlessZip(outputDir("includeOriginal"));
        // "unpacked/0" or "unpacked/0.<ext>", whichever the active SUFFIX_STRATEGY produces
        Set<String> allEntries = zipEntries(zipFile);
        boolean hasContainerAsId0 = allEntries.stream()
                .anyMatch(n -> n.equals("unpacked/0") || n.startsWith("unpacked/0."));
        assertTrue(hasContainerAsId0,
                "With includeOriginal=true, the container should appear as the " +
                        "unpacked/0 entry. Entries: " + allEntries);

        // no resource path should escape unpacked/ (no separate root-level "original" entry)
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            ZipEntry dpEntry = zip.getEntry("datapackage.json");
            assertNotNull(dpEntry, "datapackage.json should be present");
            JsonNode dataPackage;
            try (InputStream is = zip.getInputStream(dpEntry)) {
                dataPackage = OBJECT_MAPPER.readTree(is);
            }
            boolean manifestListsContainer = false;
            for (JsonNode resource : dataPackage.get("resources")) {
                String path = resource.get("path").asText();
                assertTrue(path.startsWith("unpacked/"),
                        "Manifest resources should only list unpacked/ paths; got " + path);
                if (path.equals("unpacked/0") || path.startsWith("unpacked/0.")) {
                    manifestListsContainer = true;
                }
            }
            assertTrue(manifestListsContainer,
                    "Manifest should list the container at unpacked/0");
        }
    }

    private static Path onlyFrictionlessZip(Path outputDir) throws IOException {
        List<Path> zipFiles;
        try (Stream<Path> files = Files.list(outputDir)) {
            zipFiles = files.filter(p -> p.toString().endsWith("-frictionless.zip")).toList();
        }
        assertEquals(1, zipFiles.size(), "Should create exactly one frictionless zip. Found: " + zipFiles);
        return zipFiles.get(0);
    }

    private static Set<String> zipEntries(Path zipFile) throws IOException {
        Set<String> names = new HashSet<>();
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        }
        return names;
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

    /** Nothing to package writes no zip; the metadata JSON shows the parse still ran. */
    private static void assertNothingPackaged(Path outputDir, String emitKey) throws IOException {
        try (Stream<Path> files = Files.list(outputDir)) {
            List<String> zips = files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith("-frictionless.zip")).toList();
            assertTrue(zips.isEmpty(), "unexpected zips: " + zips);
        }
        assertTrue(Files.isRegularFile(outputDir.resolve(emitKey + ".json")), "no metadata JSON for " + emitKey);
    }
}
