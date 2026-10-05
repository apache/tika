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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.core.fetcher.InlineBytes;
import org.apache.tika.sax.BasicContentHandlerFactory;

public class PipesForkParserTest {

    private static final Path PLUGINS_DIR = Paths.get("target/plugins");

    // PipesForkParser stamps handler and ParseMode from its config onto every request, so one
    // parser per distinct config; each forks lazily on its first parse.
    private static PipesForkParser defaultParser;
    private static PipesForkParser concatenateParser;
    private static PipesForkParser noParseParser;
    private static PipesForkParser xmlParser;
    private static PipesForkParser writeLimitParser;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUp() throws Exception {
        if (!Files.isDirectory(PLUGINS_DIR)) {
            System.err.println("WARNING: Plugins directory not found at " + PLUGINS_DIR.toAbsolutePath() +
                    ". Tests may fail. Run 'mvn process-test-resources' first.");
        }
        defaultParser = new PipesForkParser(new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .addJvmArg("-Xmx256m"));
        concatenateParser = new PipesForkParser(new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.CONCATENATE));
        noParseParser = new PipesForkParser(new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.NO_PARSE));
        xmlParser = new PipesForkParser(new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.XML)
                .setParseMode(ParseMode.RMETA));
        writeLimitParser = new PipesForkParser(new PipesForkParserConfig()
                .setPluginsDir(PLUGINS_DIR)
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.TEXT)
                .setParseMode(ParseMode.RMETA)
                .setWriteLimit(100));
    }

    @AfterAll
    static void tearDown() throws Exception {
        for (PipesForkParser p : new PipesForkParser[]{defaultParser, concatenateParser,
                noParseParser, xmlParser, writeLimitParser}) {
            if (p != null) {
                p.close();
            }
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
    public void testDefaultConfigIsRmetaUnlimitedText() {
        PipesForkParserConfig config = new PipesForkParserConfig();
        assertEquals(ParseMode.RMETA, config.getParseMode());
        BasicContentHandlerFactory factory =
                (BasicContentHandlerFactory) config.getContentHandlerFactory();
        assertEquals(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, factory.getType());
        assertEquals(-1, factory.getWriteLimit());
    }

    @Test
    public void testResultCategorization() {
        for (PipesResult.RESULT_STATUS status : PipesResult.RESULT_STATUS.values()) {
            PipesForkResult result = new PipesForkResult(new PipesResult(status));
            int trueCount = 0;
            if (result.isSuccess()) trueCount++;
            if (result.isProcessCrash()) trueCount++;
            if (result.isFatal()) trueCount++;
            if (result.isInitializationFailure()) trueCount++;
            if (result.isTaskException()) trueCount++;
            assertEquals(1, trueCount, "Exactly one category should be true for " + status);
        }
    }

    @Test
    public void testParseTextFile() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        String content = "Hello, this is a test document.\nIt has multiple lines.";
        Files.writeString(testFile, content);

        try (TikaInputStream tis = TikaInputStream.get(testFile)) {
            PipesForkResult result = defaultParser.parse(tis);

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

    /**
     * The inline payload is request-owned: a caller reusing the ParseContext must not have this
     * document's bytes retained and re-serialized into later requests.
     */
    @Test
    public void testInlinePayloadNotRetainedInCallerContext() throws Exception {
        ParseContext parseContext = new ParseContext();
        byte[] content = "inline body".getBytes(StandardCharsets.UTF_8);
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(content))) {
            PipesForkResult result = defaultParser.parse(tis, new Metadata(), parseContext);
            assertTrue(result.isSuccess(), "Parse should succeed. Status: " + result.getStatus()
                    + ", message: " + result.getMessage());
            assertTrue(result.getContent().contains("inline body"),
                    "worker did not parse the inline payload; content: " + result.getContent());
            assertNull(parseContext.get(InlineBytes.class),
                    "inline payload must not outlive its request in the caller's context");
        }
    }

    @Test
    public void testParseWithMetadata() throws Exception {
        Path testFile = tempDir.resolve("test.html");
        String html = "<html><head><title>Test Title</title></head>" +
                "<body><p>Test paragraph content.</p></body></html>";
        Files.writeString(testFile, html);

        try (TikaInputStream tis = TikaInputStream.get(testFile)) {
            Metadata initialMetadata = new Metadata();
            PipesForkResult result = defaultParser.parse(tis, initialMetadata);

            assertTrue(result.isSuccess(), "Parse should succeed");

            Metadata metadata = result.getMetadata();
            assertNotNull(metadata, "Metadata should not be null");

            String extractedContent = result.getContent();
            assertNotNull(extractedContent, "Content should not be null");
            assertTrue(extractedContent.contains("Test paragraph"), "Content should contain paragraph text");
        }
    }

    @Test
    public void testConcatenateMode() throws Exception {
        Path testZip = createZipWithEmbeddedFiles("test_with_embedded.zip",
                "embedded1.txt", "Content from first embedded file",
                "embedded2.txt", "Content from second embedded file");

        try (TikaInputStream tis = TikaInputStream.get(testZip)) {
            PipesForkResult result = concatenateParser.parse(tis);

            assertTrue(result.isSuccess(), "Parse should succeed");

            // In CONCATENATE mode, there should be exactly one metadata object
            // even though the zip contains multiple embedded files
            List<Metadata> metadataList = result.getMetadataList();
            assertEquals(1, metadataList.size(), "CONCATENATE mode should return single metadata");

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
        Path testFile = tempDir.resolve("test_no_parse.txt");
        String content = "This content should NOT be extracted in NO_PARSE mode.";
        Files.writeString(testFile, content);

        try (TikaInputStream tis = TikaInputStream.get(testFile)) {
            PipesForkResult result = noParseParser.parse(tis);

            assertTrue(result.isSuccess(), "Parse should succeed. Status: " + result.getStatus()
                    + ", message: " + result.getMessage());

            List<Metadata> metadataList = result.getMetadataList();
            assertEquals(1, metadataList.size(), "NO_PARSE mode should return single metadata");

            Metadata metadata = metadataList.get(0);
            String contentType = metadata.get(HttpHeaders.CONTENT_TYPE);
            assertNotNull(contentType, "Content type should be detected");
            assertTrue(contentType.contains("text/plain"),
                    "Content type should be text/plain, got: " + contentType);

            String extractedContent = result.getContent();
            assertTrue(extractedContent == null || extractedContent.isBlank(),
                    "NO_PARSE mode should not extract content, got: " + extractedContent);
        }
    }

    @Test
    public void testNoParseModeWithZip() throws Exception {
        // NO_PARSE must not extract embedded files
        Path testZip = createZipWithEmbeddedFiles("test_no_parse.zip",
                "embedded1.txt", "Content from first embedded file",
                "embedded2.txt", "Content from second embedded file");

        try (TikaInputStream tis = TikaInputStream.get(testZip)) {
            PipesForkResult result = noParseParser.parse(tis);

            assertTrue(result.isSuccess(), "Parse should succeed");

            List<Metadata> metadataList = result.getMetadataList();
            assertEquals(1, metadataList.size(),
                    "NO_PARSE mode should return only container metadata, not embedded files");

            Metadata metadata = metadataList.get(0);
            String contentType = metadata.get(HttpHeaders.CONTENT_TYPE);
            assertNotNull(contentType, "Content type should be detected");
            assertTrue(contentType.contains("zip"),
                    "Content type should be zip, got: " + contentType);

            String extractedContent = result.getContent();
            assertTrue(extractedContent == null || extractedContent.isBlank(),
                    "NO_PARSE mode should not extract content");
        }
    }

    // defaultParser sets only pluginsDir, so this also checks the default mode is RMETA end to end
    @Test
    public void testRmetaModeWithEmbedded() throws Exception {
        Path testZip = createZipWithEmbeddedFiles("test_rmeta_embedded.zip",
                "file1.txt", "First file content",
                "file2.txt", "Second file content");

        try (TikaInputStream tis = TikaInputStream.get(testZip)) {
            PipesForkResult result = defaultParser.parse(tis);

            assertTrue(result.isSuccess(), "Parse should succeed");

            // container (zip) + one per embedded file
            List<Metadata> metadataList = result.getMetadataList();
            assertTrue(metadataList.size() >= 3,
                    "RMETA mode should return metadata for container + embedded files, got: "
                    + metadataList.size());
        }
    }

    @Test
    public void testTextVsXhtmlHandlerType() throws Exception {
        Path testFile = tempDir.resolve("test_handler.html");
        String html = "<html><head><title>Test Title</title></head>" +
                "<body><p>Paragraph one.</p><p>Paragraph two.</p></body></html>";
        Files.writeString(testFile, html);

        String textContent;
        try (TikaInputStream tis = TikaInputStream.get(testFile)) {
            PipesForkResult result = defaultParser.parse(tis);
            assertTrue(result.isSuccess(), "TEXT parse should succeed");
            textContent = result.getContent();
            assertNotNull(textContent, "TEXT content should not be null");
            assertFalse(textContent.contains("<p>"), "TEXT content should not contain <p> tags");
            assertFalse(textContent.contains("<html>"), "TEXT content should not contain <html> tags");
            assertTrue(textContent.contains("Paragraph one"), "TEXT content should contain text");
        }

        String xmlContent;
        try (TikaInputStream tis = TikaInputStream.get(testFile)) {
            PipesForkResult result = xmlParser.parse(tis);
            assertTrue(result.isSuccess(), "XML parse should succeed");
            xmlContent = result.getContent();
            assertNotNull(xmlContent, "XML content should not be null");
            assertTrue(xmlContent.contains("<p>") || xmlContent.contains("<p "),
                    "XML content should contain <p> tags");
            assertTrue(xmlContent.contains("Paragraph one"), "XML content should contain text");
        }

        assertTrue(xmlContent.length() > textContent.length(),
                "XML content should be longer than TEXT content due to markup");
    }

    @Test
    public void testWriteLimit() throws Exception {
        Path testFile = tempDir.resolve("longfile.txt");
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContent.append("This is line ").append(i).append(" of the test document.\n");
        }
        Files.writeString(testFile, longContent.toString());

        try (TikaInputStream tis = TikaInputStream.get(testFile)) {
            PipesForkResult result = writeLimitParser.parse(tis);

            assertTrue(result.isSuccess(), "status: " + result.getStatus());
            String content = result.getContent();
            assertTrue(content.contains("line 0 "), content);
            assertFalse(content.contains("line 999 "), "write limit ignored: " + content.length() + " chars");
        }
    }

    @Test
    public void testFileNotFoundThrowsException() throws Exception {
        Path nonExistentFile = tempDir.resolve("does_not_exist.txt");

        // TikaInputStream.get(Path) reads file attributes (size), so it throws before any parse
        assertThrows(java.nio.file.NoSuchFileException.class, () -> {
            TikaInputStream.get(nonExistentFile);
        });
    }

    @Test
    public void testParseWithPathAndMetadata() throws Exception {
        Path testFile = tempDir.resolve("test_path_metadata.txt");
        Files.writeString(testFile, "Content for metadata test");

        Metadata initialMetadata = new Metadata();
        initialMetadata.set("custom-key", "custom-value");

        PipesForkResult result = defaultParser.parse(testFile, initialMetadata);

        assertTrue(result.isSuccess(), "Parse should succeed");
        assertNotNull(result.getMetadata(), "Metadata should not be null");
        assertTrue(result.getContent().contains("metadata test"));
        assertEquals("custom-value", result.getMetadata().get("custom-key"));
    }

    // Two files in sequence through parse(Path) and parse(TikaInputStream) on one fork
    @Test
    public void testParsePathMatchesTikaInputStream() throws Exception {
        Path testFile1 = tempDir.resolve("path1.txt");
        Path testFile2 = tempDir.resolve("path2.txt");
        Files.writeString(testFile1, "Content of first path file");
        Files.writeString(testFile2, "Content of second path file");

        PipesForkResult pathResult1 = defaultParser.parse(testFile1);
        assertTrue(pathResult1.isSuccess(), "Parse should succeed. Status: " + pathResult1.getStatus()
                + ", message: " + pathResult1.getMessage());
        assertFalse(pathResult1.isProcessCrash(), "Should not be a process crash");
        assertNotNull(pathResult1.getMetadataList(), "Metadata list should not be null");
        assertFalse(pathResult1.getMetadataList().isEmpty(), "Metadata list should not be empty");
        assertTrue(pathResult1.getContent().contains("first path file"), pathResult1.getContent());

        PipesForkResult pathResult2 = defaultParser.parse(testFile2);
        assertTrue(pathResult2.isSuccess());
        assertTrue(pathResult2.getContent().contains("second path file"), pathResult2.getContent());

        try (TikaInputStream tis1 = TikaInputStream.get(testFile1);
             TikaInputStream tis2 = TikaInputStream.get(testFile2)) {
            PipesForkResult tisResult1 = defaultParser.parse(tis1);
            assertTrue(tisResult1.isSuccess());
            assertEquals(pathResult1.getContent(), tisResult1.getContent());
            PipesForkResult tisResult2 = defaultParser.parse(tis2);
            assertTrue(tisResult2.isSuccess());
            assertEquals(pathResult2.getContent(), tisResult2.getContent());
        }
    }
}
