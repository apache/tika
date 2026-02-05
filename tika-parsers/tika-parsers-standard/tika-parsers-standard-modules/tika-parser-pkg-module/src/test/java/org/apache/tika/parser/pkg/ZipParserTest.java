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
package org.apache.tika.parser.pkg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.Zip;
import org.apache.tika.parser.ParseContext;

/**
 * Test case for parsing zip files.
 */
public class ZipParserTest extends AbstractPkgTest {

    /**
     * Tests that the ParseContext parser is correctly
     * fired for all the embedded entries.
     */
    @Test
    public void testEmbedded() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("test-documents.zip");

        // First metadata is the container, rest are embedded documents
        // With recursive parsing, we get more than 10 entries due to nested documents
        // (e.g., ODT, PPT, DOC contain embedded resources)
        assertTrue(metadataList.size() >= 10, "Expected at least 10 metadata entries");

        // Collect all resource names for verification
        List<String> resourceNames = new java.util.ArrayList<>();
        for (Metadata m : metadataList) {
            String name = m.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            if (name != null) {
                resourceNames.add(name);
            }
        }

        // Should contain all 9 direct embedded files from the ZIP
        assertContains("testEXCEL.xls", resourceNames);
        assertContains("testHTML.html", resourceNames);
        assertContains("testOpenOffice2.odt", resourceNames);
        assertContains("testPDF.pdf", resourceNames);
        assertContains("testPPT.ppt", resourceNames);
        assertContains("testRTF.rtf", resourceNames);
        assertContains("testTXT.txt", resourceNames);
        assertContains("testWORD.doc", resourceNames);
        assertContains("testXML.xml", resourceNames);
    }

    /**
     * Test case for the ability of the ZIP parser to extract the name of
     * a ZIP entry even if the content of the entry is unreadable due to an
     * unsupported compression method.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-346">TIKA-346</a>
     */
    @Test
    public void testUnsupportedZipCompressionMethod() throws Exception {
        String content = new Tika().parseToString(getResourceAsStream("/test-documents/moby.zip"));
        assertContains("README", content);
    }


    @Test // TIKA-936
    public void testCustomEncoding() throws Exception {
        ZipParserConfig config = new ZipParserConfig();
        config.setEntryEncoding(Charset.forName("SJIS"));
        ParseContext context = new ParseContext();
        context.set(ZipParserConfig.class, config);

        List<Metadata> metadataList;
        try (TikaInputStream tis = TikaInputStream.get(Base64.decodeBase64(
                "UEsDBBQAAAAIAI+CvUCDo3+zIgAAACgAAAAOAAAAk/qWe4zqg4GDgi50" +
                        "eHRr2tj0qulsc2pzRHN609Gm7Y1OvFxNYLHJv6ZV97yCiQEAUEsBAh" +
                        "QLFAAAAAgAj4K9QIOjf7MiAAAAKAAAAA4AAAAAAAAAAAAgAAAAAAAA" +
                        "AJP6lnuM6oOBg4IudHh0UEsFBgAAAAABAAEAPAAAAE4AAAAAAA=="))) {
            metadataList = getRecursiveMetadata(tis, new Metadata(), context, false);
        }

        // Container + 1 embedded document
        assertEquals(2, metadataList.size());
        assertEquals("\u65E5\u672C\u8A9E\u30E1\u30E2.txt",
                metadataList.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void testCharsetAutoDetectionDisabled() throws Exception {
        // Test that disabling charset detection leaves non-UTF8 names as-is (garbled)
        ZipParserConfig config = new ZipParserConfig();
        config.setDetectCharsetsInEntryNames(false);
        ParseContext context = new ParseContext();
        context.set(ZipParserConfig.class, config);

        List<Metadata> metadataList;
        try (TikaInputStream tis = TikaInputStream.get(Base64.decodeBase64(
                "UEsDBBQAAAAIAI+CvUCDo3+zIgAAACgAAAAOAAAAk/qWe4zqg4GDgi50" +
                        "eHRr2tj0qulsc2pzRHN609Gm7Y1OvFxNYLHJv6ZV97yCiQEAUEsBAh" +
                        "QLFAAAAAgAj4K9QIOjf7MiAAAAKAAAAA4AAAAAAAAAAAAgAAAAAAAA" +
                        "AJP6lnuM6oOBg4IudHh0UEsFBgAAAAABAAEAPAAAAE4AAAAAAA=="))) {
            metadataList = getRecursiveMetadata(tis, new Metadata(), context, false);
        }

        // Container + 1 embedded document
        assertEquals(2, metadataList.size());
        String name = metadataList.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY);
        // With detection disabled, the SJIS bytes are interpreted as default charset (garbled)
        // The correct Japanese name is 日本語メモ.txt - verify we DON'T get that
        assertTrue(!"\u65E5\u672C\u8A9E\u30E1\u30E2.txt".equals(name),
                "With detection disabled, SJIS name should NOT be correctly decoded");
    }

    @Test
    public void testQuineRecursiveParserWrapper() throws Exception {
        //Anti-virus can surreptitiously remove this file
        assumeTrue(
                ZipParserTest.class.getResourceAsStream("/test-documents/droste.zip") != null);
        //received permission from author via dm
        //2019-07-25 to include
        //http://alf.nu/s/droste.zip in unit tests
        //Out of respect to the author, please maintain
        //the original file name
        getRecursiveMetadata("droste.zip");
    }

    @Test
    public void testQuine() {
        //Anti-virus can surreptitiously remove this file
        assumeTrue(
                ZipParserTest.class.getResourceAsStream("/test-documents/droste.zip") != null);
        assertThrows(TikaException.class, () -> {
            getXML("droste.zip");
        });
    }

    @Test
    public void testZipUsingStoredWithDataDescriptor() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testZip_with_DataDescriptor.zip");

        // Container + 5 embedded documents
        assertEquals(6, metadataList.size());
        assertEquals("en0", metadataList.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("en1", metadataList.get(2).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("en2", metadataList.get(3).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("en3", metadataList.get(4).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("en4", metadataList.get(5).get(TikaCoreProperties.RESOURCE_NAME_KEY));

        // This ZIP with DATA_DESCRIPTOR is salvaged and parsed with file-based access
        // Integrity check can compare central directory vs local headers
        Metadata containerMetadata = metadataList.get(0);
        assertEquals("PASS", containerMetadata.get(Zip.INTEGRITY_CHECK_RESULT));
    }

    @Test
    public void testIntegrityCheckPass() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("test-documents.zip");

        // Normal ZIP with file-based access should pass integrity check
        Metadata containerMetadata = metadataList.get(0);
        assertEquals("PASS", containerMetadata.get(Zip.INTEGRITY_CHECK_RESULT));
        assertNull(containerMetadata.get(Zip.DUPLICATE_ENTRY_NAMES));
        assertNull(containerMetadata.get(Zip.CENTRAL_DIRECTORY_ONLY_ENTRIES));
        assertNull(containerMetadata.get(Zip.LOCAL_HEADER_ONLY_ENTRIES));
    }

    @Test
    public void testIntegrityCheckDisabled() throws Exception {
        ZipParserConfig config = new ZipParserConfig();
        config.setIntegrityCheck(false);
        ParseContext context = new ParseContext();
        context.set(ZipParserConfig.class, config);

        List<Metadata> metadataList = getRecursiveMetadata("test-documents.zip", context);

        // Integrity check disabled - no result should be set
        Metadata containerMetadata = metadataList.get(0);
        assertNull(containerMetadata.get(Zip.INTEGRITY_CHECK_RESULT));
    }

    @Test
    public void testIntegrityCheckHiddenEntry(@TempDir Path tempDir) throws Exception {
        // Create a ZIP with a hidden entry (in local headers but not central directory)
        Path zipPath = tempDir.resolve("hidden-entry.zip");
        byte[] zipBytes = createZipWithHiddenEntry();
        Files.write(zipPath, zipBytes);

        List<Metadata> metadataList = getRecursiveMetadata(zipPath, false);

        Metadata containerMetadata = metadataList.get(0);
        assertEquals("FAIL", containerMetadata.get(Zip.INTEGRITY_CHECK_RESULT));
        String[] localOnly = containerMetadata.getValues(Zip.LOCAL_HEADER_ONLY_ENTRIES);
        assertEquals(1, localOnly.length);
        assertEquals("hidden.txt", localOnly[0]);
    }

    /**
     * Creates a ZIP file with an entry that exists in local headers but not in the
     * central directory. This simulates a hidden/smuggled entry attack.
     */
    private byte[] createZipWithHiddenEntry() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Entry 1: visible.txt (will be in both local header and central directory)
        byte[] visible = "visible content".getBytes(StandardCharsets.UTF_8);
        // Entry 2: hidden.txt (will be in local header ONLY - not in central directory)
        byte[] hidden = "hidden content".getBytes(StandardCharsets.UTF_8);

        // Local file header for visible.txt
        int visibleLocalOffset = baos.size();
        writeLocalFileHeader(baos, "visible.txt", visible);

        // Local file header for hidden.txt (this won't have a central directory entry)
        writeLocalFileHeader(baos, "hidden.txt", hidden);

        // Central directory - only includes visible.txt
        int centralDirOffset = baos.size();
        writeCentralDirectoryEntry(baos, "visible.txt", visible, visibleLocalOffset);

        // End of central directory
        int centralDirSize = baos.size() - centralDirOffset;
        writeEndOfCentralDirectory(baos, 1, centralDirSize, centralDirOffset);

        return baos.toByteArray();
    }

    private void writeLocalFileHeader(ByteArrayOutputStream baos, String name, byte[] content)
            throws Exception {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);

        // Local file header signature
        writeInt(baos, 0x04034b50);
        // Version needed
        writeShort(baos, 10);
        // General purpose bit flag
        writeShort(baos, 0);
        // Compression method (0 = stored)
        writeShort(baos, 0);
        // Last mod time/date
        writeShort(baos, 0);
        writeShort(baos, 0);
        // CRC-32
        writeInt(baos, (int) computeCrc32(content));
        // Compressed size
        writeInt(baos, content.length);
        // Uncompressed size
        writeInt(baos, content.length);
        // File name length
        writeShort(baos, nameBytes.length);
        // Extra field length
        writeShort(baos, 0);
        // File name
        baos.write(nameBytes);
        // File data
        baos.write(content);
    }

    private void writeCentralDirectoryEntry(ByteArrayOutputStream baos, String name,
                                             byte[] content, int localHeaderOffset) throws Exception {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);

        // Central directory file header signature
        writeInt(baos, 0x02014b50);
        // Version made by
        writeShort(baos, 20);
        // Version needed
        writeShort(baos, 10);
        // General purpose bit flag
        writeShort(baos, 0);
        // Compression method
        writeShort(baos, 0);
        // Last mod time/date
        writeShort(baos, 0);
        writeShort(baos, 0);
        // CRC-32
        writeInt(baos, (int) computeCrc32(content));
        // Compressed size
        writeInt(baos, content.length);
        // Uncompressed size
        writeInt(baos, content.length);
        // File name length
        writeShort(baos, nameBytes.length);
        // Extra field length
        writeShort(baos, 0);
        // File comment length
        writeShort(baos, 0);
        // Disk number start
        writeShort(baos, 0);
        // Internal file attributes
        writeShort(baos, 0);
        // External file attributes
        writeInt(baos, 0);
        // Relative offset of local header
        writeInt(baos, localHeaderOffset);
        // File name
        baos.write(nameBytes);
    }

    private void writeEndOfCentralDirectory(ByteArrayOutputStream baos, int numEntries,
                                             int centralDirSize, int centralDirOffset) {
        // End of central directory signature
        writeInt(baos, 0x06054b50);
        // Disk number
        writeShort(baos, 0);
        // Disk number with central directory
        writeShort(baos, 0);
        // Number of entries on this disk
        writeShort(baos, numEntries);
        // Total number of entries
        writeShort(baos, numEntries);
        // Size of central directory
        writeInt(baos, centralDirSize);
        // Offset of central directory
        writeInt(baos, centralDirOffset);
        // Comment length
        writeShort(baos, 0);
    }

    private void writeInt(ByteArrayOutputStream baos, int value) {
        baos.write(value & 0xff);
        baos.write((value >> 8) & 0xff);
        baos.write((value >> 16) & 0xff);
        baos.write((value >> 24) & 0xff);
    }

    private void writeShort(ByteArrayOutputStream baos, int value) {
        baos.write(value & 0xff);
        baos.write((value >> 8) & 0xff);
    }

    private long computeCrc32(byte[] data) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        return crc.getValue();
    }

    /**
     * Microbenchmark to measure the performance impact of integrity checking.
     * This test is disabled by default - remove the assumeTrue to run it.
     *
     * WARNING: The large ZIP test creates a multi-GB file and takes significant time.
     */
    @Test
    public void benchmarkIntegrityCheck(@TempDir Path tempDir) throws Exception {
        // Skip by default - set this to true to run the benchmark
        assumeTrue(false, "Benchmark disabled by default - set to true to run");

        int iterations = 20;
        int warmupIterations = 3;

        // Create small ZIP (10 entries, ~1KB each) - ~10KB total
        Path smallZip = tempDir.resolve("small.zip");
        System.out.println("Creating small ZIP (10 entries, ~10KB)...");
        createBenchmarkZip(smallZip, 10, 1024);
        System.out.println("  Created: " + Files.size(smallZip) / 1024 + " KB");

        // Create medium ZIP (1000 entries, ~100KB each) - ~100MB total
        Path mediumZip = tempDir.resolve("medium.zip");
        System.out.println("Creating medium ZIP (1000 entries, ~100MB)...");
        createBenchmarkZip(mediumZip, 1000, 100 * 1024);
        System.out.println("  Created: " + Files.size(mediumZip) / (1024 * 1024) + " MB");

        // Create large ZIP (5000 entries, ~500KB each) - ~2.5GB total
        Path largeZip = tempDir.resolve("large.zip");
        System.out.println("Creating large ZIP (5000 entries, ~2.5GB)...");
        createBenchmarkZip(largeZip, 5000, 500 * 1024);
        System.out.println("  Created: " + Files.size(largeZip) / (1024 * 1024) + " MB");

        System.out.println();
        System.out.println("=== Integrity Check Benchmark ===");
        System.out.println("Iterations: " + iterations + " (warmup: " + warmupIterations + ")");
        System.out.println();

        // Benchmark small ZIP
        System.out.println("Small ZIP (10 entries, ~10KB):");
        runBenchmark(smallZip, iterations, warmupIterations);

        System.out.println();

        // Benchmark medium ZIP
        System.out.println("Medium ZIP (1000 entries, ~100MB):");
        runBenchmark(mediumZip, 10, 2);

        System.out.println();

        // Benchmark large ZIP
        System.out.println("Large ZIP (5000 entries, ~2.5GB):");
        runBenchmark(largeZip, 5, 1);
    }

    private void createBenchmarkZip(Path zipPath, int numEntries, int entrySize) throws Exception {
        try (java.util.zip.ZipOutputStream zos =
                     new java.util.zip.ZipOutputStream(Files.newOutputStream(zipPath))) {
            // Use STORED to avoid compression - we want actual file size
            zos.setMethod(java.util.zip.ZipOutputStream.STORED);

            // Use random data to prevent any accidental compression
            java.util.Random random = new java.util.Random(42);
            byte[] content = new byte[entrySize];
            random.nextBytes(content);

            for (int i = 0; i < numEntries; i++) {
                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("entry" + i + ".txt");
                entry.setMethod(java.util.zip.ZipEntry.STORED);
                entry.setSize(content.length);
                entry.setCompressedSize(content.length);
                entry.setCrc(computeCrc32(content));
                zos.putNextEntry(entry);
                zos.write(content);
                zos.closeEntry();
            }
        }
    }

    private void runBenchmark(Path zipPath, int iterations, int warmupIterations) throws Exception {
        ZipParser parser = new ZipParser();

        // Config with integrity check enabled
        ZipParserConfig configWithCheck = new ZipParserConfig();
        configWithCheck.setIntegrityCheck(true);

        // Config with integrity check disabled
        ZipParserConfig configWithoutCheck = new ZipParserConfig();
        configWithoutCheck.setIntegrityCheck(false);

        // Warmup - with integrity check
        for (int i = 0; i < warmupIterations; i++) {
            parseZip(parser, zipPath, configWithCheck);
        }

        // Warmup - without integrity check
        for (int i = 0; i < warmupIterations; i++) {
            parseZip(parser, zipPath, configWithoutCheck);
        }

        // Benchmark with integrity check
        long startWithCheck = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            parseZip(parser, zipPath, configWithCheck);
        }
        long durationWithCheck = System.nanoTime() - startWithCheck;

        // Benchmark without integrity check
        long startWithoutCheck = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            parseZip(parser, zipPath, configWithoutCheck);
        }
        long durationWithoutCheck = System.nanoTime() - startWithoutCheck;

        double avgWithCheck = durationWithCheck / (double) iterations / 1_000_000.0;
        double avgWithoutCheck = durationWithoutCheck / (double) iterations / 1_000_000.0;
        double overhead = avgWithCheck - avgWithoutCheck;
        double overheadPercent = (overhead / avgWithoutCheck) * 100;

        System.out.printf("  Without integrity check: %.3f ms/parse%n", avgWithoutCheck);
        System.out.printf("  With integrity check:    %.3f ms/parse%n", avgWithCheck);
        System.out.printf("  Overhead:                %.3f ms (%.1f%%)%n", overhead, overheadPercent);
    }

    private void parseZip(ZipParser parser, Path zipPath, ZipParserConfig config) throws Exception {
        ParseContext context = new ParseContext();
        context.set(ZipParserConfig.class, config);

        try (TikaInputStream tis = TikaInputStream.get(zipPath)) {
            Metadata metadata = new Metadata();
            parser.parse(tis, new org.xml.sax.helpers.DefaultHandler(), metadata, context);
        }
    }
}
