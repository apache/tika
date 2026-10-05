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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

    static final String fetcherName = "fsf";
    static final String emitterName = "fse";
    static final String testDocWithEmbedded = "mock-embedded.xml";
    static final String simpleDoc = "mock_times.xml";

    @TempDir
    static Path tmp;

    // emitter is onExists=EXCEPTION: each test emits under its own subdirectory of output
    private static PipesClient pipesClient;

    @BeforeAll
    public static void setUp() throws Exception {
        pipesClient = init(tmp);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (pipesClient != null) {
            pipesClient.close();
        }
    }

    private static PipesClient init(Path dir) throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(dir, dir.resolve("input"), dir.resolve("output"));
        PluginsTestHelper.copyTestFilesToTmpInput(dir, testDocWithEmbedded, simpleDoc);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        return new PipesClient(pipesConfig, tikaConfigPath);
    }

    private static Path outputDir(String testName) {
        return tmp.resolve("output").resolve(testName);
    }

    private static PipesResult process(String testName, String doc, String emitName, ParseContext parseContext)
            throws Exception {
        return pipesClient.process(
                new FetchEmitTuple(emitName, new FetchKey(fetcherName, doc),
                        new EmitKey(emitterName, testName + "/" + emitName), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));
    }

    @Test
    public void testUnpackModeBasic() throws Exception {
        // No UnpackConfig: UNPACK sets up the extractor and emitter itself
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        PipesResult pipesResult = process("basic", testDocWithEmbedded, testDocWithEmbedded, parseContext);
        assertTrue(pipesResult.isSuccess(), "Status: " + pipesResult.status() +
                ", Message: " + pipesResult.message());

        Path outputDir = outputDir("basic");
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
    public void testUnpackModeRequiresEmitter(@TempDir Path dir) throws Exception {
        // own client: the missing-emitter TikaConfigException takes down the fork
        try (PipesClient client = init(dir)) {
            ParseContext parseContext = new ParseContext();
            parseContext.set(ParseMode.class, ParseMode.UNPACK);

            // Create EmitKey with no emitterId to trigger the error
            PipesResult pipesResult = client.process(
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
    public void testUnpackModeWithCustomUnpackConfig() throws Exception {
        // Test that UNPACK mode respects custom UnpackConfig settings
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        // Create custom UnpackConfig with specific settings
        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(emitterName);
        unpackConfig.setZeroPadName(8);
        unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = process("custom", testDocWithEmbedded, testDocWithEmbedded, parseContext);
        assertTrue(pipesResult.isSuccess(),
                "UNPACK with custom UnpackConfig should succeed. Status: " + pipesResult.status());

        List<String> names = embeddedFiles(outputDir("custom"), testDocWithEmbedded).stream()
                .map(f -> f.getFileName().toString()).toList();
        assertEquals(4, names.size(), names.toString());
        for (String name : names) {
            assertTrue(name.matches("\\d{8}\\..+"), "zero-padded with detected suffix: " + name);
        }
    }

    @Test
    public void testUnpackModeWithIncludeOriginal() throws Exception {
        // Test that includeOriginal=true works with UNPACK mode
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(emitterName);
        unpackConfig.setIncludeOriginal(true);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = process("includeOriginal", testDocWithEmbedded, testDocWithEmbedded, parseContext);
        assertTrue(pipesResult.isSuccess(),
                "UNPACK with includeOriginal should succeed. Status: " + pipesResult.status());

        List<Path> embedded = embeddedFiles(outputDir("includeOriginal"), testDocWithEmbedded);
        assertEquals(5, embedded.size(), "4 embedded + the original: " + embedded);
    }

    @Test
    public void testUnpackModeVsRmetaMode() throws Exception {
        // Compare UNPACK mode output with RMETA mode to verify metadata consistency
        ParseContext rmetaContext = new ParseContext();
        rmetaContext.set(ParseMode.class, ParseMode.RMETA);
        PipesResult rmetaResult = process("vsRmeta", testDocWithEmbedded, testDocWithEmbedded + "-rmeta",
                rmetaContext);

        ParseContext unpackContext = new ParseContext();
        unpackContext.set(ParseMode.class, ParseMode.UNPACK);
        PipesResult unpackResult = process("vsRmeta", testDocWithEmbedded, testDocWithEmbedded + "-unpack",
                unpackContext);

        assertTrue(rmetaResult.isSuccess(), "RMETA processing should succeed. Status: " + rmetaResult.status());
        assertTrue(unpackResult.isSuccess(), "UNPACK processing should succeed. Status: " + unpackResult.status() +
                ", Message: " + unpackResult.message());

        // RMETA passes the list back; UNPACK only emits it
        List<Metadata> rmetaList = rmetaResult.emitData().getMetadataList();
        List<Metadata> unpackList = readMetadataList(outputDir("vsRmeta"), testDocWithEmbedded + "-unpack");
        assertEquals(5, rmetaList.size());
        assertEquals(rmetaList.size(), unpackList.size());
        for (int i = 0; i < rmetaList.size(); i++) {
            assertEquals(rmetaList.get(i).get("author"), unpackList.get(i).get("author"), "index " + i);
            assertEquals(rmetaList.get(i).get("Content-Type"), unpackList.get(i).get("Content-Type"),
                    "index " + i);
        }
    }

    @Test
    public void testUnpackModeWithSimpleDocument() throws Exception {
        // Test UNPACK mode with a simple document (no embedded files)
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        PipesResult pipesResult = process("simple", simpleDoc, simpleDoc, parseContext);
        assertTrue(pipesResult.isSuccess(),
                "UNPACK should work with simple documents. Status: " + pipesResult.status() +
                        ", Message: " + pipesResult.message());

        Path outputDir = outputDir("simple");
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
    public void testUnpackModeZipOutput() throws Exception {
        // Test that zipEmbeddedFiles=true creates a zip file containing embedded documents
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(emitterName);
        unpackConfig.setZipEmbeddedFiles(true);
        unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = process("zip", testDocWithEmbedded, testDocWithEmbedded, parseContext);
        assertTrue(pipesResult.isSuccess(),
                "UNPACK with zipEmbeddedFiles should succeed. Status: " + pipesResult.status() +
                        ", Message: " + pipesResult.message());

        Path zipFile = onlyZip(outputDir("zip"));
        assertTrue(Files.size(zipFile) > 0, "Zip file should not be empty");

        // mock-embedded.xml has 4 embedded documents (test-embedded-1.txt through test-embedded-4.txt)
        Set<String> zipEntries = zipEntries(zipFile);
        assertTrue(zipEntries.size() >= 4,
                "Zip should contain at least 4 embedded files. Found: " + zipEntries);
    }

    @Test
    public void testUnpackModeZipWithMetadata() throws Exception {
        // Test that includeMetadataInZip=true includes .metadata.json files in the zip
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(emitterName);
        unpackConfig.setZipEmbeddedFiles(true);
        unpackConfig.setIncludeMetadataInZip(true);
        unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = process("zipMetadata", testDocWithEmbedded, testDocWithEmbedded, parseContext);
        assertTrue(pipesResult.isSuccess(),
                "UNPACK with zipEmbeddedFiles and metadata should succeed. Status: " + pipesResult.status() +
                        ", Message: " + pipesResult.message());

        Set<String> metadataFiles = new HashSet<>();
        for (String name : zipEntries(onlyZip(outputDir("zipMetadata")))) {
            if (name.endsWith(".metadata.json")) {
                metadataFiles.add(name);
            }
        }
        // Each embedded document should have a corresponding .metadata.json file
        assertTrue(metadataFiles.size() >= 4,
                "Zip should contain metadata JSON files for embedded documents. Found: " + metadataFiles);
    }

    @Test
    public void testUnpackModeZipWithIncludeOriginal() throws Exception {
        // Test that includeOriginal=true includes the container document in the zip
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(emitterName);
        unpackConfig.setZipEmbeddedFiles(true);
        unpackConfig.setIncludeOriginal(true);
        unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = process("zipIncludeOriginal", testDocWithEmbedded, testDocWithEmbedded,
                parseContext);
        assertTrue(pipesResult.isSuccess(),
                "UNPACK with includeOriginal should succeed. Status: " + pipesResult.status());

        Set<String> allEntries = zipEntries(onlyZip(outputDir("zipIncludeOriginal")));
        assertTrue(allEntries.size() >= 5,
                "Zip should contain embedded files plus original. Found: " + allEntries);
    }

    @Test
    public void testUnpackModeZipNoEmbedded() throws Exception {
        // zipEmbeddedFiles=true on a document with no embedded files
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(emitterName);
        unpackConfig.setZipEmbeddedFiles(true);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = process("zipNoEmbedded", simpleDoc, simpleDoc, parseContext);
        assertTrue(pipesResult.isSuccess(),
                "UNPACK with zipEmbeddedFiles on simple doc should succeed. Status: " + pipesResult.status());

        Path outputDir = outputDir("zipNoEmbedded");
        assertTrue(zips(outputDir).isEmpty(), "no embedded files, no zip: " + zips(outputDir));
        assertEquals(1, readMetadataList(outputDir, simpleDoc).size());
    }

    @Test
    public void testMaxUnpackBytesLimit() throws Exception {
        // Test that maxUnpackBytes limit is enforced during byte extraction
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(emitterName);
        unpackConfig.setMaxUnpackBytes(10L);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = process("limited", testDocWithEmbedded, testDocWithEmbedded + "-limited",
                parseContext);
        // The parse should succeed (limit exceeded just stops extraction, doesn't fail)
        assertTrue(pipesResult.isSuccess(),
                "UNPACK with maxUnpackBytes limit should succeed. Status: " + pipesResult.status());

        long totalBytesWritten;
        try (Stream<Path> files = Files.walk(outputDir("limited"))) {
            totalBytesWritten = files
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.toString().endsWith(".json"))
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        }
        // testMaxUnpackBytesUnlimited shows these files otherwise total 4 x 146 bytes
        assertTrue(totalBytesWritten <= 10,
                "Total bytes written should be limited by maxUnpackBytes. Got: " + totalBytesWritten);
    }

    @Test
    public void testMaxUnpackBytesDefault() throws Exception {
        // Test that the default maxUnpackBytes (10GB) allows normal extraction
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(emitterName);
        assertEquals(UnpackConfig.DEFAULT_MAX_UNPACK_BYTES, unpackConfig.getMaxUnpackBytes(),
                "Default maxUnpackBytes should be 10GB");
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = process("default", testDocWithEmbedded, testDocWithEmbedded + "-default",
                parseContext);
        assertTrue(pipesResult.isSuccess(),
                "UNPACK with default maxUnpackBytes should succeed. Status: " + pipesResult.status());

        List<Path> embedded = embeddedFiles(outputDir("default"), testDocWithEmbedded + "-default");
        assertEquals(4, embedded.size(), embedded.toString());
        for (Path p : embedded) {
            assertEquals(146, Files.size(p), p.toString());
        }
    }

    @Test
    public void testMaxUnpackBytesUnlimited() throws Exception {
        // Test that maxUnpackBytes=-1 allows unlimited extraction
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(emitterName);
        unpackConfig.setMaxUnpackBytes(-1L);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = process("unlimited", testDocWithEmbedded, testDocWithEmbedded + "-unlimited",
                parseContext);
        assertTrue(pipesResult.isSuccess(),
                "UNPACK with unlimited maxUnpackBytes should succeed. Status: " + pipesResult.status());

        List<Path> embedded = embeddedFiles(outputDir("unlimited"), testDocWithEmbedded + "-unlimited");
        assertEquals(4, embedded.size(), embedded.toString());
        for (Path p : embedded) {
            assertEquals(146, Files.size(p), p.toString());
        }
    }

    private static List<Path> zips(Path outputDir) throws IOException {
        try (Stream<Path> files = Files.list(outputDir)) {
            return files.filter(p -> p.toString().endsWith("-embedded.zip")).toList();
        }
    }

    private static Path onlyZip(Path outputDir) throws IOException {
        List<Path> zipFiles = zips(outputDir);
        assertEquals(1, zipFiles.size(), "Should create exactly one zip file. Found: " + zipFiles);
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
