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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
import org.apache.tika.serialization.JsonMetadataList;

/**
 * Tests for the UNPACK ParseMode functionality.
 */
public class UnpackModeTest {

    String fetcherName = "fsf";
    String emitterName = "fse";
    String testDocWithEmbedded = "mock-embedded.xml";

    private PipesClient init(Path tmp, String testFileName) throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(tmp, tmp.resolve("input"), tmp.resolve("output"));
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testFileName);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        return new PipesClient(pipesConfig, tikaConfigPath);
    }

    @Test
    public void testUnpackModeBasic(@TempDir Path tmp) throws Exception {
        // No UnpackConfig: UNPACK sets up the extractor and emitter itself
        try (PipesClient pipesClient = init(tmp, testDocWithEmbedded)) {
            ParseContext parseContext = new ParseContext();
            parseContext.set(ParseMode.class, ParseMode.UNPACK);

            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple(testDocWithEmbedded, new FetchKey(fetcherName, testDocWithEmbedded),
                            new EmitKey(emitterName, testDocWithEmbedded), new Metadata(), parseContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));
            assertTrue(pipesResult.isSuccess(), "Status: " + pipesResult.status() +
                    ", Message: " + pipesResult.message());
        }
        Path outputDir = tmp.resolve("output");
        // container + 4 embedded, RMETA-style
        List<Metadata> metadataList = readMetadataList(outputDir, testDocWithEmbedded);
        assertEquals(5, metadataList.size());
        assertEquals("Nikolai Lobachevsky", metadataList.get(0).get("author"));
        for (int i = 1; i < metadataList.size(); i++) {
            assertEquals("embeddedAuthor", metadataList.get(i).get("author"), "embedded " + i);
        }
        for (Metadata m : metadataList) {
            assertNotNull(m.get("Content-Type"));
        }
        List<Path> embedded = embeddedFiles(outputDir, testDocWithEmbedded);
        assertEquals(4, embedded.size(), "embedded bytes: " + embedded);
        for (Path p : embedded) {
            assertTrue(Files.size(p) > 0, p.toString());
        }
    }
    @Test
    public void testUnpackModeRequiresEmitter(@TempDir Path tmp) throws Exception {
        // Test that UNPACK mode fails gracefully when no emitter is specified
        try (PipesClient pipesClient = init(tmp, testDocWithEmbedded)) {
            ParseContext parseContext = new ParseContext();
            parseContext.set(ParseMode.class, ParseMode.UNPACK);

            // Create EmitKey with no emitterId to trigger the error
            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple(testDocWithEmbedded, new FetchKey(fetcherName, testDocWithEmbedded),
                            new EmitKey("", ""), new Metadata(), parseContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

            // Should fail because no emitter is configured
            // The error could be a crash (TikaConfigException thrown), initialization failure, or task exception
            assertTrue(!pipesResult.isSuccess(),
                    "UNPACK without emitter should fail. Status: " + pipesResult.status());
            assertNotNull(pipesResult.message());
            assertTrue(pipesResult.message().contains("emitter") || pipesResult.message().contains("UNPACK") ||
                    pipesResult.message().contains("TikaConfigException"),
                    "Error message should mention emitter requirement: " + pipesResult.message());
        }
    }

    @Test
    public void testUnpackModeWithCustomUnpackConfig(@TempDir Path tmp) throws Exception {
        // Test that UNPACK mode respects custom UnpackConfig settings
        try (PipesClient pipesClient = init(tmp, testDocWithEmbedded)) {
            ParseContext parseContext = new ParseContext();
            parseContext.set(ParseMode.class, ParseMode.UNPACK);

            // Create custom UnpackConfig with specific settings
            UnpackConfig unpackConfig = new UnpackConfig();
            unpackConfig.setEmitter(emitterName);
            unpackConfig.setZeroPadName(8);
            unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
            parseContext.set(UnpackConfig.class, unpackConfig);

            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple(testDocWithEmbedded, new FetchKey(fetcherName, testDocWithEmbedded),
                            new EmitKey(emitterName, testDocWithEmbedded), new Metadata(), parseContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

            assertTrue(pipesResult.isSuccess(),
                    "UNPACK with custom UnpackConfig should succeed. Status: " + pipesResult.status());
        }
        List<String> names = embeddedFiles(tmp.resolve("output"), testDocWithEmbedded).stream()
                .map(f -> f.getFileName().toString()).toList();
        assertEquals(4, names.size(), names.toString());
        for (String name : names) {
            assertTrue(name.matches("\\d{8}\\..+"), "zero-padded with detected suffix: " + name);
        }
    }

    @Test
    public void testUnpackModeWithIncludeOriginal(@TempDir Path tmp) throws Exception {
        // Test that includeOriginal=true works with UNPACK mode
        try (PipesClient pipesClient = init(tmp, testDocWithEmbedded)) {
            ParseContext parseContext = new ParseContext();
            parseContext.set(ParseMode.class, ParseMode.UNPACK);
            
            UnpackConfig unpackConfig = new UnpackConfig();
            unpackConfig.setEmitter(emitterName);
            unpackConfig.setIncludeOriginal(true);
            parseContext.set(UnpackConfig.class, unpackConfig);

            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple(testDocWithEmbedded, new FetchKey(fetcherName, testDocWithEmbedded),
                            new EmitKey(emitterName, testDocWithEmbedded), new Metadata(), parseContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

            assertTrue(pipesResult.isSuccess(),
                    "UNPACK with includeOriginal should succeed. Status: " + pipesResult.status());
        }
        List<Path> embedded = embeddedFiles(tmp.resolve("output"), testDocWithEmbedded);
        assertEquals(5, embedded.size(), "4 embedded + the original: " + embedded);
    }

    @Test
    public void testUnpackModeVsRmetaMode(@TempDir Path tmp) throws Exception {
        // Compare UNPACK mode output with RMETA mode to verify metadata consistency
        try (PipesClient pipesClient = init(tmp, testDocWithEmbedded)) {
            // Process with RMETA
            ParseContext rmetaContext = new ParseContext();
            rmetaContext.set(ParseMode.class, ParseMode.RMETA);
            
            PipesResult rmetaResult = pipesClient.process(
                    new FetchEmitTuple(testDocWithEmbedded + "-rmeta", new FetchKey(fetcherName, testDocWithEmbedded),
                            new EmitKey(emitterName, testDocWithEmbedded + "-rmeta"), new Metadata(), rmetaContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

            // Process with UNPACK
            ParseContext unpackContext = new ParseContext();
            unpackContext.set(ParseMode.class, ParseMode.UNPACK);

            PipesResult unpackResult = pipesClient.process(
                    new FetchEmitTuple(testDocWithEmbedded + "-unpack", new FetchKey(fetcherName, testDocWithEmbedded),
                            new EmitKey(emitterName, testDocWithEmbedded + "-unpack"), new Metadata(), unpackContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

            // Both should succeed
            assertTrue(rmetaResult.isSuccess(), "RMETA processing should succeed. Status: " + rmetaResult.status());
            assertTrue(unpackResult.isSuccess(), "UNPACK processing should succeed. Status: " + unpackResult.status() +
                    ", Message: " + unpackResult.message());


            // RMETA passes the list back; UNPACK only emits it
            List<Metadata> rmetaList = rmetaResult.emitData().getMetadataList();
            List<Metadata> unpackList = readMetadataList(tmp.resolve("output"), testDocWithEmbedded + "-unpack");
            assertEquals(5, rmetaList.size());
            assertEquals(rmetaList.size(), unpackList.size());
            for (int i = 0; i < rmetaList.size(); i++) {
                assertEquals(rmetaList.get(i).get("author"), unpackList.get(i).get("author"), "index " + i);
                assertEquals(rmetaList.get(i).get("Content-Type"), unpackList.get(i).get("Content-Type"),
                        "index " + i);
            }
        }
    }
    @Test
    public void testUnpackModeWithSimpleDocument(@TempDir Path tmp) throws Exception {
        // Test UNPACK mode with a simple document (no embedded files)
        String simpleDoc = "mock_times.xml";
        try (PipesClient pipesClient = init(tmp, simpleDoc)) {
            ParseContext parseContext = new ParseContext();
            parseContext.set(ParseMode.class, ParseMode.UNPACK);

            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple(simpleDoc, new FetchKey(fetcherName, simpleDoc),
                            new EmitKey(emitterName, simpleDoc), new Metadata(), parseContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

            assertTrue(pipesResult.isSuccess(),
                    "UNPACK should work with simple documents. Status: " + pipesResult.status() +
                            ", Message: " + pipesResult.message());

        }
        Path outputDir = tmp.resolve("output");
        assertEquals(1, readMetadataList(outputDir, simpleDoc).size());
        assertTrue(embeddedFiles(outputDir, simpleDoc).isEmpty());
    }
    @Test
    public void testParseModeParseMethod() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ParseMode.parse("INVALID_MODE"));
        assertTrue(e.getMessage().contains("UNPACK"),
                "Error message should include UNPACK as a valid option: " + e.getMessage());

        assertEquals(ParseMode.UNPACK, ParseMode.parse("UNPACK"));
        assertEquals(ParseMode.UNPACK, ParseMode.parse("unpack"));
        assertEquals(ParseMode.UNPACK, ParseMode.parse("Unpack"));
    }
    @Test
    public void testUnpackModeZipOutput(@TempDir Path tmp) throws Exception {
        // Test that zipEmbeddedFiles=true creates a zip file containing embedded documents
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        try (PipesClient pipesClient = init(tmp, testDocWithEmbedded)) {
            ParseContext parseContext = new ParseContext();
            parseContext.set(ParseMode.class, ParseMode.UNPACK);

            // Configure UnpackConfig for zip output
            UnpackConfig unpackConfig = new UnpackConfig();
            unpackConfig.setEmitter(emitterName);
            unpackConfig.setZipEmbeddedFiles(true);
            unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
            parseContext.set(UnpackConfig.class, unpackConfig);

            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple(testDocWithEmbedded, new FetchKey(fetcherName, testDocWithEmbedded),
                            new EmitKey(emitterName, testDocWithEmbedded), new Metadata(), parseContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

            assertTrue(pipesResult.isSuccess(),
                    "UNPACK with zipEmbeddedFiles should succeed. Status: " + pipesResult.status() +
                            ", Message: " + pipesResult.message());
        }

        // Find the zip file in output directory
        List<Path> zipFiles = Files.list(outputDir)
                .filter(p -> p.toString().endsWith("-embedded.zip"))
                .toList();

        assertEquals(1, zipFiles.size(), "Should create exactly one zip file. Found: " +
                Files.list(outputDir).map(p -> p.getFileName().toString()).toList());

        Path zipFile = zipFiles.get(0);
        assertTrue(Files.size(zipFile) > 0, "Zip file should not be empty");

        // Verify zip contents
        Set<String> zipEntries = new HashSet<>();
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                zipEntries.add(entry.getName());
            }
        }

        // mock-embedded.xml has 4 embedded documents (test-embedded-1.txt through test-embedded-4.txt)
        assertTrue(zipEntries.size() >= 4,
                "Zip should contain at least 4 embedded files. Found: " + zipEntries);
    }

    @Test
    public void testUnpackModeZipWithMetadata(@TempDir Path tmp) throws Exception {
        // Test that includeMetadataInZip=true includes .metadata.json files in the zip
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        try (PipesClient pipesClient = init(tmp, testDocWithEmbedded)) {
            ParseContext parseContext = new ParseContext();
            parseContext.set(ParseMode.class, ParseMode.UNPACK);

            // Configure UnpackConfig for zip output with metadata
            UnpackConfig unpackConfig = new UnpackConfig();
            unpackConfig.setEmitter(emitterName);
            unpackConfig.setZipEmbeddedFiles(true);
            unpackConfig.setIncludeMetadataInZip(true);
            unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
            parseContext.set(UnpackConfig.class, unpackConfig);

            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple(testDocWithEmbedded, new FetchKey(fetcherName, testDocWithEmbedded),
                            new EmitKey(emitterName, testDocWithEmbedded), new Metadata(), parseContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

            assertTrue(pipesResult.isSuccess(),
                    "UNPACK with zipEmbeddedFiles and metadata should succeed. Status: " + pipesResult.status() +
                            ", Message: " + pipesResult.message());
        }

        // Find the zip file
        List<Path> zipFiles = Files.list(outputDir)
                .filter(p -> p.toString().endsWith("-embedded.zip"))
                .toList();

        assertEquals(1, zipFiles.size(), "Should create exactly one zip file");

        Path zipFile = zipFiles.get(0);

        // Verify zip contains metadata JSON files
        Set<String> metadataFiles = new HashSet<>();
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".metadata.json")) {
                    metadataFiles.add(entry.getName());
                }
            }
        }

        // Each embedded document should have a corresponding .metadata.json file
        assertTrue(metadataFiles.size() >= 4,
                "Zip should contain metadata JSON files for embedded documents. Found: " + metadataFiles);
    }

    @Test
    public void testUnpackModeZipWithIncludeOriginal(@TempDir Path tmp) throws Exception {
        // Test that includeOriginal=true includes the container document in the zip
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        try (PipesClient pipesClient = init(tmp, testDocWithEmbedded)) {
            ParseContext parseContext = new ParseContext();
            parseContext.set(ParseMode.class, ParseMode.UNPACK);

            // Configure UnpackConfig for zip output with original document
            UnpackConfig unpackConfig = new UnpackConfig();
            unpackConfig.setEmitter(emitterName);
            unpackConfig.setZipEmbeddedFiles(true);
            unpackConfig.setIncludeOriginal(true);
            unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
            parseContext.set(UnpackConfig.class, unpackConfig);

            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple(testDocWithEmbedded, new FetchKey(fetcherName, testDocWithEmbedded),
                            new EmitKey(emitterName, testDocWithEmbedded), new Metadata(), parseContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

            assertTrue(pipesResult.isSuccess(),
                    "UNPACK with includeOriginal should succeed. Status: " + pipesResult.status());
        }

        // Find the zip file
        List<Path> zipFiles = Files.list(outputDir)
                .filter(p -> p.toString().endsWith("-embedded.zip"))
                .toList();

        assertEquals(1, zipFiles.size(), "Should create exactly one zip file");

        Path zipFile = zipFiles.get(0);

        // Verify zip contents include the original document
        Set<String> allEntries = new HashSet<>();
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                allEntries.add(entry.getName());
            }
        }

        // With includeOriginal=true, should have embedded files + original
        // The original may have a name like the original file name
        assertTrue(allEntries.size() >= 5,
                "Zip should contain embedded files plus original. Found: " + allEntries);
    }

    @Test
    public void testUnpackModeZipNoEmbedded(@TempDir Path tmp) throws Exception {
        // Test that zipEmbeddedFiles=true with a document with no embedded files
        // doesn't create a zip (or creates an empty one)
        String simpleDoc = "mock_times.xml";
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        try (PipesClient pipesClient = init(tmp, simpleDoc)) {
            ParseContext parseContext = new ParseContext();
            parseContext.set(ParseMode.class, ParseMode.UNPACK);

            UnpackConfig unpackConfig = new UnpackConfig();
            unpackConfig.setEmitter(emitterName);
            unpackConfig.setZipEmbeddedFiles(true);
            parseContext.set(UnpackConfig.class, unpackConfig);

            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple(simpleDoc, new FetchKey(fetcherName, simpleDoc),
                            new EmitKey(emitterName, simpleDoc), new Metadata(), parseContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

            assertTrue(pipesResult.isSuccess(),
                    "UNPACK with zipEmbeddedFiles on simple doc should succeed. Status: " + pipesResult.status());
        }

        // Check for zip files - there may be none if no embedded docs
        List<Path> zipFiles = Files.list(outputDir)
                .filter(p -> p.toString().endsWith("-embedded.zip"))
                .toList();

        assertTrue(zipFiles.isEmpty(), "no embedded files, no zip: " + zipFiles);
        assertEquals(1, readMetadataList(outputDir, simpleDoc).size());
    }
    @Test
    public void testMaxUnpackBytesLimit(@TempDir Path tmp) throws Exception {
        // Test that maxUnpackBytes limit is enforced during byte extraction
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        try (PipesClient pipesClient = init(tmp, testDocWithEmbedded)) {
            ParseContext parseContext = new ParseContext();
            parseContext.set(ParseMode.class, ParseMode.UNPACK);

            // Set a very low maxUnpackBytes limit (10 bytes) - this should cause
            // extraction to stop early after the first few bytes
            UnpackConfig unpackConfig = new UnpackConfig();
            unpackConfig.setEmitter(emitterName);
            unpackConfig.setMaxUnpackBytes(10L);  // Only allow 10 bytes total
            parseContext.set(UnpackConfig.class, unpackConfig);

            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple(testDocWithEmbedded + "-limited", new FetchKey(fetcherName, testDocWithEmbedded),
                            new EmitKey(emitterName, testDocWithEmbedded + "-limited"), new Metadata(), parseContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

            // The parse should succeed (limit exceeded just stops extraction, doesn't fail)
            assertTrue(pipesResult.isSuccess(),
                    "UNPACK with maxUnpackBytes limit should succeed. Status: " + pipesResult.status());
        }

        // The output should be limited - total extracted bytes should be <= 10
        // We can verify this by checking that not all embedded files were written
        // or that they were truncated
        long totalBytesWritten = Files.walk(outputDir)
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().endsWith(".json"))  // Exclude metadata JSON
                .mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();

        // With a 10-byte limit, we should have extracted very little
        // The first embedded file in mock-embedded.xml is larger than 10 bytes
        // testMaxUnpackBytesUnlimited shows these files otherwise total 4 x 146 bytes
        assertTrue(totalBytesWritten <= 10,
                "Total bytes written should be limited by maxUnpackBytes. Got: " + totalBytesWritten);
    }

    @Test
    public void testMaxUnpackBytesDefault(@TempDir Path tmp) throws Exception {
        // Test that the default maxUnpackBytes (10GB) allows normal extraction
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        try (PipesClient pipesClient = init(tmp, testDocWithEmbedded)) {
            ParseContext parseContext = new ParseContext();
            parseContext.set(ParseMode.class, ParseMode.UNPACK);
            
            // Use UnpackConfig with default maxUnpackBytes (10GB)
            UnpackConfig unpackConfig = new UnpackConfig();
            unpackConfig.setEmitter(emitterName);
            // Not setting maxUnpackBytes - should use default 10GB
            assertEquals(UnpackConfig.DEFAULT_MAX_UNPACK_BYTES, unpackConfig.getMaxUnpackBytes(),
                    "Default maxUnpackBytes should be 10GB");
            parseContext.set(UnpackConfig.class, unpackConfig);

            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple(testDocWithEmbedded + "-default", new FetchKey(fetcherName, testDocWithEmbedded),
                            new EmitKey(emitterName, testDocWithEmbedded + "-default"), new Metadata(), parseContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

            assertTrue(pipesResult.isSuccess(),
                    "UNPACK with default maxUnpackBytes should succeed. Status: " + pipesResult.status());
        }
        List<Path> embedded = embeddedFiles(outputDir, testDocWithEmbedded + "-default");
        assertEquals(4, embedded.size(), embedded.toString());
        for (Path p : embedded) {
            assertEquals(146, Files.size(p), p.toString());
        }
    }
    @Test
    public void testMaxUnpackBytesUnlimited(@TempDir Path tmp) throws Exception {
        // Test that maxUnpackBytes=-1 allows unlimited extraction
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        try (PipesClient pipesClient = init(tmp, testDocWithEmbedded)) {
            ParseContext parseContext = new ParseContext();
            parseContext.set(ParseMode.class, ParseMode.UNPACK);

            // Set maxUnpackBytes to -1 (unlimited)
            UnpackConfig unpackConfig = new UnpackConfig();
            unpackConfig.setEmitter(emitterName);
            unpackConfig.setMaxUnpackBytes(-1L);  // Unlimited
            parseContext.set(UnpackConfig.class, unpackConfig);

            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple(testDocWithEmbedded + "-unlimited", new FetchKey(fetcherName, testDocWithEmbedded),
                            new EmitKey(emitterName, testDocWithEmbedded + "-unlimited"), new Metadata(), parseContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

            assertTrue(pipesResult.isSuccess(),
                    "UNPACK with unlimited maxUnpackBytes should succeed. Status: " + pipesResult.status());
        }
        List<Path> embedded = embeddedFiles(outputDir, testDocWithEmbedded + "-unlimited");
        assertEquals(4, embedded.size(), embedded.toString());
        for (Path p : embedded) {
            assertEquals(146, Files.size(p), p.toString());
        }
    }

    private static List<Metadata> readMetadataList(Path outputDir, String emitKey) throws IOException {
        try (Reader reader = Files.newBufferedReader(outputDir.resolve(emitKey + ".json"),
                StandardCharsets.UTF_8)) {
            return JsonMetadataList.fromJson(reader);
        }
    }

    private static List<Path> embeddedFiles(Path outputDir, String emitKey) throws IOException {
        Path dir = outputDir.resolve(emitKey + "-embed");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.sorted().toList();
        }
    }
}
