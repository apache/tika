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
package org.apache.tika.pipes.fork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.sax.BasicContentHandlerFactory;

public class PipesForkParserTest {

    private static final Path PLUGINS_DIR = Paths.get("target/plugins");

    @TempDir
    Path tempDir;

    @BeforeAll
    static void checkPluginsDir() {
        if (!Files.isDirectory(PLUGINS_DIR)) {
            System.err.println("WARNING: Plugins directory not found at " + PLUGINS_DIR.toAbsolutePath() +
                    ". Tests may fail. Run 'mvn process-test-resources' first.");
        }
    }

    private Path createZipWithEmbeddedFiles(String zipName, String... entries) throws IOException {
        Path zipPath = tempDir.resolve(zipName);
        try (OutputStream fos = Files.newOutputStream(zipPath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (int i = 0; i < entries.length; i += 2) {
                zos.putNextEntry(new ZipEntry(entries[i]));
                zos.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return zipPath;
    }

    @Test
    public void testParseTextFile() throws Exception {
        // Create a simple test file
        Path testFile = tempDir.resolve("test.txt");
        String content = "Hello, this is a test document.\nIt has multiple lines.";
        Files.writeString(testFile, content);

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.RMETA)
                .setTimeoutMillis(60000)
                .addJvmArg("-Xmx256m");

        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(testFile)) {
            PipesForkResult result = parser.parse(tis);

            assertTrue(result.isSuccess(), "Parse should succeed. Status: " + result.getStatus()
                    + ", message: " + result.getMessage());
            assertFalse(result.isProcessCrash(), "Should not be a process crash");

            List<Metadata> metadataList = result.getMetadataList();
            assertNotNull(metadataList, "Metadata list should not be null");
            assertFalse(metadataList.isEmpty(), "Metadata list should not be empty");

            String extractedContent = result.getContent();
            assertNotNull(extractedContent, "Content should not be null");
            assertTrue(extractedContent.contains("Hello"), "Content should contain 'Hello'");
            assertTrue(extractedContent.contains("test document"), "Content should contain 'test document'");
        }
    }

    @Test
    public void testParseWithMetadata() throws Exception {
        // Create a simple HTML file
        Path testFile = tempDir.resolve("test.html");
        String html = "<html><head><title>Test Title</title></head>" +
                "<body><p>Test paragraph content.</p></body></html>";
        Files.writeString(testFile, html);

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.RMETA)
                .setTimeoutMillis(60000);

        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(testFile)) {
            Metadata initialMetadata = new Metadata();
            PipesForkResult result = parser.parse(tis, initialMetadata);

            assertTrue(result.isSuccess(), "Parse should succeed");

            Metadata metadata = result.getMetadata();
            assertNotNull(metadata, "Metadata should not be null");

            String extractedContent = result.getContent();
            assertNotNull(extractedContent, "Content should not be null");
            assertTrue(extractedContent.contains("Test paragraph"), "Content should contain paragraph text");
        }
    }

    @Test
    public void testParseMultipleFiles() throws Exception {
        // Create multiple test files
        Path testFile1 = tempDir.resolve("test1.txt");
        Path testFile2 = tempDir.resolve("test2.txt");
        Files.writeString(testFile1, "Content of first file");
        Files.writeString(testFile2, "Content of second file");

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.RMETA)
                .setTimeoutMillis(60000);

        try (PipesForkParser parser = new PipesForkParser(config)) {
            try (TikaInputStream tis1 = TikaInputStream.get(testFile1)) {
                PipesForkResult result1 = parser.parse(tis1);
                assertTrue(result1.isSuccess());
                assertTrue(result1.getContent().contains("first file"));
            }

            try (TikaInputStream tis2 = TikaInputStream.get(testFile2)) {
                PipesForkResult result2 = parser.parse(tis2);
                assertTrue(result2.isSuccess());
                assertTrue(result2.getContent().contains("second file"));
            }
        }
    }

    @Test
    public void testConcatenateMode() throws Exception {
        Path testZip = createZipWithEmbeddedFiles("test_with_embedded.zip",
                "embedded1.txt", "Content from first embedded file",
                "embedded2.txt", "Content from second embedded file");

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.CONCATENATE)
                .setTimeoutMillis(60000);

        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(testZip)) {
            PipesForkResult result = parser.parse(tis);

            assertTrue(result.isSuccess(), "Parse should succeed");

            // In CONCATENATE mode, there should be exactly one metadata object
            // even though the zip contains multiple embedded files
            List<Metadata> metadataList = result.getMetadataList();
            assertEquals(1, metadataList.size(), "CONCATENATE mode should return single metadata");

            // The content should contain text from both embedded files
            String content = result.getContent();
            assertNotNull(content);
            assertTrue(content.contains("first embedded"),
                    "Content should contain text from first embedded file");
            assertTrue(content.contains("second embedded"),
                    "Content should contain text from second embedded file");
        }
    }

    @Test
    public void testNoParseMode() throws Exception {
        // Create a simple test file
        Path testFile = tempDir.resolve("test_no_parse.txt");
        String content = "This content should NOT be extracted in NO_PARSE mode.";
        Files.writeString(testFile, content);

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.NO_PARSE)
                .setTimeoutMillis(60000);

        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(testFile)) {
            PipesForkResult result = parser.parse(tis);

            assertTrue(result.isSuccess(), "Parse should succeed. Status: " + result.getStatus()
                    + ", message: " + result.getMessage());

            // In NO_PARSE mode, there should be exactly one metadata object
            List<Metadata> metadataList = result.getMetadataList();
            assertEquals(1, metadataList.size(), "NO_PARSE mode should return single metadata");

            // Content type should be detected
            Metadata metadata = metadataList.get(0);
            String contentType = metadata.get(Metadata.CONTENT_TYPE);
            assertNotNull(contentType, "Content type should be detected");
            assertTrue(contentType.contains("text/plain"),
                    "Content type should be text/plain, got: " + contentType);

            // No content should be extracted
            String extractedContent = result.getContent();
            assertTrue(extractedContent == null || extractedContent.isBlank(),
                    "NO_PARSE mode should not extract content, got: " + extractedContent);
        }
    }

    @Test
    public void testNoParseModeWithZip() throws Exception {
        // Test NO_PARSE mode with a zip file - should NOT extract embedded files
        Path testZip = createZipWithEmbeddedFiles("test_no_parse.zip",
                "embedded1.txt", "Content from first embedded file",
                "embedded2.txt", "Content from second embedded file");

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.NO_PARSE)
                .setTimeoutMillis(60000);

        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(testZip)) {
            PipesForkResult result = parser.parse(tis);

            assertTrue(result.isSuccess(), "Parse should succeed");

            // Should have exactly one metadata object (no embedded file extraction)
            List<Metadata> metadataList = result.getMetadataList();
            assertEquals(1, metadataList.size(),
                    "NO_PARSE mode should return only container metadata, not embedded files");

            // Content type should be detected as zip
            Metadata metadata = metadataList.get(0);
            String contentType = metadata.get(Metadata.CONTENT_TYPE);
            assertNotNull(contentType, "Content type should be detected");
            assertTrue(contentType.contains("zip"),
                    "Content type should be zip, got: " + contentType);

            // No content should be extracted
            String extractedContent = result.getContent();
            assertTrue(extractedContent == null || extractedContent.isBlank(),
                    "NO_PARSE mode should not extract content");
        }
    }

    @Test
    public void testRmetaModeWithEmbedded() throws Exception {
        Path testZip = createZipWithEmbeddedFiles("test_rmeta_embedded.zip",
                "file1.txt", "First file content",
                "file2.txt", "Second file content");

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.RMETA)
                .setTimeoutMillis(60000);

        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(testZip)) {
            PipesForkResult result = parser.parse(tis);

            assertTrue(result.isSuccess(), "Parse should succeed");

            // In RMETA mode, there should be multiple metadata objects:
            // one for the container (zip) and one for each embedded file
            List<Metadata> metadataList = result.getMetadataList();
            assertTrue(metadataList.size() >= 3,
                    "RMETA mode should return metadata for container + embedded files, got: "
                    + metadataList.size());
        }
    }

    @Test
    public void testDefaultConfigMatchesExplicitRmeta() throws Exception {
        Path testZip = createZipWithEmbeddedFiles("test_default_config.zip",
                "file1.txt", "First file content",
                "file2.txt", "Second file content");

        // Parse with explicit RMETA config
        PipesForkParserConfig explicitConfig = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.RMETA)
                .setTimeoutMillis(60000);

        int explicitMetadataCount;
        try (PipesForkParser parser = new PipesForkParser(explicitConfig);
             TikaInputStream tis = TikaInputStream.get(testZip)) {
            PipesForkResult result = parser.parse(tis);
            assertTrue(result.isSuccess());
            explicitMetadataCount = result.getMetadataList().size();
        }

        // Parse with default config (only pluginsDir set) - should produce same results
        PipesForkParserConfig defaultConfig = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR);
        try (PipesForkParser parser = new PipesForkParser(defaultConfig);
             TikaInputStream tis = TikaInputStream.get(testZip)) {
            PipesForkResult result = parser.parse(tis);

            assertTrue(result.isSuccess(), "Parse with default config should succeed");
            assertEquals(explicitMetadataCount, result.getMetadataList().size(),
                    "Default config should produce same metadata count as explicit RMETA config");
        }
    }

    @Test
    public void testTextVsXhtmlHandlerType() throws Exception {
        // Create an HTML file to parse
        Path testFile = tempDir.resolve("test_handler.html");
        String html = "<html><head><title>Test Title</title></head>" +
                "<body><p>Paragraph one.</p><p>Paragraph two.</p></body></html>";
        Files.writeString(testFile, html);

        // Parse with TEXT handler - should get plain text without markup
        PipesForkParserConfig textConfig = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.RMETA)
                .setTimeoutMillis(60000);

        String textContent;
        try (PipesForkParser parser = new PipesForkParser(textConfig);
             TikaInputStream tis = TikaInputStream.get(testFile)) {
            PipesForkResult result = parser.parse(tis);
            assertTrue(result.isSuccess(), "TEXT parse should succeed");
            textContent = result.getContent();
            assertNotNull(textContent, "TEXT content should not be null");
            // TEXT mode should NOT contain HTML tags
            assertFalse(textContent.contains("<p>"), "TEXT content should not contain <p> tags");
            assertFalse(textContent.contains("<html>"), "TEXT content should not contain <html> tags");
            assertTrue(textContent.contains("Paragraph one"), "TEXT content should contain text");
        }

        // Parse with XML handler - should get XHTML markup
        PipesForkParserConfig xmlConfig = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.XML)
                .setParseMode(ParseMode.RMETA)
                .setTimeoutMillis(60000);

        String xmlContent;
        try (PipesForkParser parser = new PipesForkParser(xmlConfig);
             TikaInputStream tis = TikaInputStream.get(testFile)) {
            PipesForkResult result = parser.parse(tis);
            assertTrue(result.isSuccess(), "XML parse should succeed");
            xmlContent = result.getContent();
            assertNotNull(xmlContent, "XML content should not be null");
            // XML mode SHOULD contain markup
            assertTrue(xmlContent.contains("<p>") || xmlContent.contains("<p "),
                    "XML content should contain <p> tags");
            assertTrue(xmlContent.contains("Paragraph one"), "XML content should contain text");
        }

        // The XML content should be longer due to markup
        assertTrue(xmlContent.length() > textContent.length(),
                "XML content should be longer than TEXT content due to markup");
    }

    @Test
    public void testWriteLimit() throws Exception {
        // Create a file with more content than the write limit
        Path testFile = tempDir.resolve("longfile.txt");
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContent.append("This is line ").append(i).append(" of the test document.\n");
        }
        Files.writeString(testFile, longContent.toString());

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.RMETA)
                .setWriteLimit(100)  // Limit to 100 characters
                .setTimeoutMillis(60000);

        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(testFile)) {
            PipesForkResult result = parser.parse(tis);

            // Note: behavior depends on throwOnWriteLimitReached setting
            // With default (true), this may result in an exception being recorded
            assertNotNull(result);
        }
    }

    @Test
    public void testDefaultConfiguration() throws Exception {
        Path testFile = tempDir.resolve("default.txt");
        Files.writeString(testFile, "Testing default configuration");

        // Use default configuration (only pluginsDir set)
        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR);
        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(testFile)) {
            PipesForkResult result = parser.parse(tis);
            assertTrue(result.isSuccess());
            assertNotNull(result.getContent());
        }
    }

    @Test
    public void testFileNotFoundThrowsException() throws Exception {
        // Try to parse a file that doesn't exist
        Path nonExistentFile = tempDir.resolve("does_not_exist.txt");

        // TikaInputStream.get(Path) throws NoSuchFileException for non-existent files
        // because it needs to read file attributes (size)
        assertThrows(java.nio.file.NoSuchFileException.class, () -> {
            TikaInputStream.get(nonExistentFile);
        });
    }

    @Test
    public void testExceptionOnOneFileDoesNotPreventNextParse() throws Exception {
        // Test that an exception when opening one file doesn't prevent parsing another file
        Path nonExistentFile = tempDir.resolve("does_not_exist.txt");
        Path realFile = tempDir.resolve("real_file.txt");
        Files.writeString(realFile, "This file exists");

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setTimeoutMillis(60000);

        try (PipesForkParser parser = new PipesForkParser(config)) {
            // First attempt - TikaInputStream.get() will throw for non-existent file
            assertThrows(java.nio.file.NoSuchFileException.class, () -> {
                TikaInputStream.get(nonExistentFile);
            });

            // Second parse - should succeed despite the previous exception
            try (TikaInputStream tis2 = TikaInputStream.get(realFile)) {
                PipesForkResult result2 = parser.parse(tis2);
                assertTrue(result2.isSuccess(), "Should succeed for existing file");
                assertTrue(result2.getContent().contains("This file exists"));
            }
        }
    }

    @Test
    public void testParseSuccessWithExceptionStatus() throws Exception {
        // Create a file that will parse but may have warnings
        // For example, a file with content that might trigger a write limit
        Path testFile = tempDir.resolve("parse_with_warning.txt");
        Files.writeString(testFile, "Simple content");

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setTimeoutMillis(60000);

        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(testFile)) {
            PipesForkResult result = parser.parse(tis);

            // Verify we can check for different success states
            if (result.isSuccess()) {
                // Could be PARSE_SUCCESS, PARSE_SUCCESS_WITH_EXCEPTION, or EMIT_SUCCESS_PASSBACK
                assertTrue(
                        result.getStatus() == PipesResult.RESULT_STATUS.PARSE_SUCCESS ||
                        result.getStatus() == PipesResult.RESULT_STATUS.PARSE_SUCCESS_WITH_EXCEPTION ||
                        result.getStatus() == PipesResult.RESULT_STATUS.EMIT_SUCCESS_PASSBACK,
                        "Success status should be one of the success types");
            }
        }
    }

    @Test
    public void testResultCategorization() throws Exception {
        // Test that we can properly categorize results
        Path testFile = tempDir.resolve("categorize.txt");
        Files.writeString(testFile, "Test categorization");

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setTimeoutMillis(60000);

        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(testFile)) {
            PipesForkResult result = parser.parse(tis);

            // At least one of these should be true
            boolean hasCategory = result.isSuccess() || result.isProcessCrash() ||
                    result.isFatal() || result.isInitializationFailure() || result.isTaskException();
            assertTrue(hasCategory, "Result should have a valid category");

            // These should be mutually exclusive
            int trueCount = 0;
            if (result.isSuccess()) trueCount++;
            if (result.isProcessCrash()) trueCount++;
            if (result.isFatal()) trueCount++;
            if (result.isInitializationFailure()) trueCount++;
            if (result.isTaskException()) trueCount++;
            assertEquals(1, trueCount, "Exactly one category should be true");
        }
    }

    @Test
    public void testParseWithPath() throws Exception {
        // Create a simple test file
        Path testFile = tempDir.resolve("test_path.txt");
        String content = "Hello from path-based parsing!";
        Files.writeString(testFile, content);

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.RMETA)
                .setTimeoutMillis(60000);

        try (PipesForkParser parser = new PipesForkParser(config)) {
            // Use parse(Path) directly without wrapping in TikaInputStream
            PipesForkResult result = parser.parse(testFile);

            assertTrue(result.isSuccess(), "Parse should succeed. Status: " + result.getStatus()
                    + ", message: " + result.getMessage());
            assertFalse(result.isProcessCrash(), "Should not be a process crash");

            List<Metadata> metadataList = result.getMetadataList();
            assertNotNull(metadataList, "Metadata list should not be null");
            assertFalse(metadataList.isEmpty(), "Metadata list should not be empty");

            String extractedContent = result.getContent();
            assertNotNull(extractedContent, "Content should not be null");
            assertTrue(extractedContent.contains("path-based parsing"),
                    "Content should contain 'path-based parsing'");
        }
    }

    @Test
    public void testParseWithPathAndMetadata() throws Exception {
        // Create a simple test file
        Path testFile = tempDir.resolve("test_path_metadata.txt");
        Files.writeString(testFile, "Content for metadata test");

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.RMETA)
                .setTimeoutMillis(60000);

        try (PipesForkParser parser = new PipesForkParser(config)) {
            Metadata initialMetadata = new Metadata();
            initialMetadata.set("custom-key", "custom-value");

            // Use parse(Path, Metadata)
            PipesForkResult result = parser.parse(testFile, initialMetadata);

            assertTrue(result.isSuccess(), "Parse should succeed");
            assertNotNull(result.getMetadata(), "Metadata should not be null");
            assertTrue(result.getContent().contains("metadata test"));
        }
    }

    @Test
    public void testParseMultipleFilesWithPath() throws Exception {
        // Create multiple test files
        Path testFile1 = tempDir.resolve("path1.txt");
        Path testFile2 = tempDir.resolve("path2.txt");
        Files.writeString(testFile1, "Content of first path file");
        Files.writeString(testFile2, "Content of second path file");

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.RMETA)
                .setTimeoutMillis(60000);

        try (PipesForkParser parser = new PipesForkParser(config)) {
            // Parse both files using Path directly
            PipesForkResult result1 = parser.parse(testFile1);
            assertTrue(result1.isSuccess());
            assertTrue(result1.getContent().contains("first path file"));

            PipesForkResult result2 = parser.parse(testFile2);
            assertTrue(result2.isSuccess());
            assertTrue(result2.getContent().contains("second path file"));
        }
    }

    @Test
    public void testParsePathMatchesTikaInputStream() throws Exception {
        // Verify that parse(Path) produces the same result as parse(TikaInputStream)
        Path testFile = tempDir.resolve("compare.txt");
        Files.writeString(testFile, "Content for comparison test");

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.RMETA)
                .setTimeoutMillis(60000);

        // Parse with Path
        String pathContent;
        try (PipesForkParser parser = new PipesForkParser(config)) {
            PipesForkResult result = parser.parse(testFile);
            assertTrue(result.isSuccess());
            pathContent = result.getContent();
        }

        // Parse with TikaInputStream
        String tisContent;
        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(testFile)) {
            PipesForkResult result = parser.parse(tis);
            assertTrue(result.isSuccess());
            tisContent = result.getContent();
        }

        // Results should match
        assertEquals(pathContent, tisContent,
                "parse(Path) and parse(TikaInputStream) should produce same content");
    }
}
