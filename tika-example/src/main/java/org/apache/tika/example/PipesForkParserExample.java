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
package org.apache.tika.example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.core.PipesException;
import org.apache.tika.pipes.fork.PipesForkParser;
import org.apache.tika.pipes.fork.PipesForkParserConfig;
import org.apache.tika.pipes.fork.PipesForkParserException;
import org.apache.tika.pipes.fork.PipesForkResult;
import org.apache.tika.sax.BasicContentHandlerFactory;

/**
 * Examples of how to use the {@link PipesForkParser} to parse documents
 * in a forked JVM process.
 * <p>
 * The PipesForkParser provides isolation from crashes, memory leaks, and
 * other issues that can occur during parsing of untrusted or malformed
 * documents. If parsing fails catastrophically (OOM, infinite loop, etc.),
 * only the forked process is affected - your main application continues
 * running.
 * <p>
 * <b>Key features:</b>
 * <ul>
 *   <li>Process isolation - crashes don't affect your main JVM</li>
 *   <li>Automatic process restart after crashes</li>
 *   <li>Configurable timeouts to prevent infinite loops</li>
 *   <li>Memory isolation - each forked process has its own heap</li>
 *   <li>Thread-safe - can be shared across multiple threads</li>
 * </ul>
 * <p>
 * <b>IMPORTANT - Resource Management:</b>
 * <ul>
 *   <li>Always close both the {@link PipesForkParser} and {@link TikaInputStream} using
 *       try-with-resources or explicit close() calls</li>
 *   <li>TikaInputStream may create temporary files when parsing from streams - these
 *       are only cleaned up when the stream is closed</li>
 *   <li>PipesForkParser manages forked JVM processes - closing it terminates these processes
 *       and cleans up the temporary config file</li>
 * </ul>
 * <p>
 * <b>Performance Tip:</b> Tika is significantly more efficient on some file types
 * (especially those requiring random access like ZIP, OLE2/Office, PDF) when you have
 * a file on disk and use {@code TikaInputStream.get(Path)} instead of
 * {@code TikaInputStream.get(Files.newInputStream(path))}. The latter will cause
 * TikaInputStream to spool the entire stream to a temporary file before parsing,
 * which adds overhead. If you already have a file, always use the Path-based method.
 */
public class PipesForkParserExample {

    /**
     * Basic example of parsing a file using PipesForkParser with default settings.
     * <p>
     * This is the simplest way to use PipesForkParser. It uses default configuration
     * which includes:
     * <ul>
     *   <li>Single forked process</li>
     *   <li>TEXT output (plain text extraction)</li>
     *   <li>RMETA mode (separate metadata for container and each embedded document)</li>
     * </ul>
     * <p>
     * <b>Note:</b> This example uses {@code result.getContent()} which only returns
     * the container document's content. For files with embedded documents (ZIP, email,
     * Office docs with attachments), embedded content is NOT included. See
     * {@link #parseEmbeddedDocumentsRmeta(Path)} for the proper way to access all content
     * including embedded documents.
     *
     * @param filePath the path to the file to parse
     * @return the container document's extracted text content (embedded content not included)
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if parsing is interrupted
     * @throws TikaException if a Tika error occurs
     * @throws PipesException if a pipes infrastructure error occurs
     * @see #parseEmbeddedDocumentsRmeta(Path) for accessing all content including embedded documents
     */
    public String parseFileBasic(Path filePath)
            throws IOException, InterruptedException, TikaException, PipesException {
        try (PipesForkParser parser = new PipesForkParser();
             TikaInputStream tis = TikaInputStream.get(filePath)) {
            PipesForkResult result = parser.parse(tis);
            if (result.isSuccess()) {
                return result.getContent();
            } else {
                throw new TikaException("Parse failed: " + result.getStatus() +
                        " - " + result.getMessage());
            }
        }
    }

    /**
     * Example of parsing a file and getting ALL content (container + embedded documents).
     * <p>
     * This is the recommended approach when using RMETA mode (the default) if you need
     * all content from a document that may contain embedded files.
     * <p>
     * This method iterates over all metadata objects and concatenates their content,
     * giving you content from the container AND all embedded documents.
     *
     * @param filePath the path to the file to parse
     * @return all extracted text content (container + all embedded documents)
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if parsing is interrupted
     * @throws TikaException if a Tika error occurs
     * @throws PipesException if a pipes infrastructure error occurs
     */
    public String parseFileAllContent(Path filePath)
            throws IOException, InterruptedException, TikaException, PipesException {
        try (PipesForkParser parser = new PipesForkParser();
             TikaInputStream tis = TikaInputStream.get(filePath)) {
            PipesForkResult result = parser.parse(tis);
            if (result.isSuccess()) {
                // Iterate over ALL metadata objects to get container + embedded content
                StringBuilder allContent = new StringBuilder();
                for (Metadata m : result.getMetadataList()) {
                    String content = m.get(TikaCoreProperties.TIKA_CONTENT);
                    if (content != null) {
                        if (allContent.length() > 0) {
                            allContent.append("\n\n");
                        }
                        allContent.append(content);
                    }
                }
                return allContent.toString();
            } else {
                throw new TikaException("Parse failed: " + result.getStatus() +
                        " - " + result.getMessage());
            }
        }
    }

    /**
     * Example of parsing from an InputStream.
     * <p>
     * When parsing from an InputStream (as opposed to a file), TikaInputStream
     * will automatically spool the stream to a temporary file. This is necessary
     * because the forked process needs file system access.
     * <p>
     * <b>Performance Note:</b> If you already have a file on disk, use
     * {@link #parseFileBasic(Path)} with {@code TikaInputStream.get(Path)} instead.
     * This avoids the overhead of spooling the stream to a temporary file.
     * For file types that require random access (ZIP, OLE2/Office documents, PDF),
     * the performance difference can be significant.
     * <p>
     * The temporary file is automatically cleaned up when the TikaInputStream is closed.
     * <b>Always close the TikaInputStream</b> to ensure temp files are deleted.
     *
     * @param inputStream the input stream to parse
     * @return the extracted text content
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if parsing is interrupted
     * @throws TikaException if a Tika error occurs
     * @throws PipesException if a pipes infrastructure error occurs
     */
    public String parseInputStream(InputStream inputStream)
            throws IOException, InterruptedException, TikaException, PipesException {
        try (PipesForkParser parser = new PipesForkParser();
             TikaInputStream tis = TikaInputStream.get(inputStream)) {
            PipesForkResult result = parser.parse(tis);
            return result.getContent();
        }
    }

    /**
     * Example of parsing with custom configuration.
     * <p>
     * This example shows how to configure:
     * <ul>
     *   <li>HTML output instead of plain text</li>
     *   <li>Parse timeout of 60 seconds</li>
     *   <li>JVM memory settings for the forked process</li>
     *   <li>Maximum files before process restart (to prevent memory leaks)</li>
     * </ul>
     *
     * @param filePath the path to the file to parse
     * @return the extracted HTML content
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if parsing is interrupted
     * @throws TikaException if a Tika error occurs
     * @throws PipesException if a pipes infrastructure error occurs
     */
    public String parseWithCustomConfig(Path filePath)
            throws IOException, InterruptedException, TikaException, PipesException {
        PipesForkParserConfig config = new PipesForkParserConfig()
                .setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE.HTML)
                .setTimeoutMillis(60000)
                .addJvmArg("-Xmx512m")
                .setMaxFilesPerProcess(100);

        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(filePath)) {
            PipesForkResult result = parser.parse(tis);
            return result.getContent();
        }
    }

    /**
     * Example of parsing with metadata extraction.
     * <p>
     * This example demonstrates how to access both content and metadata
     * from the parse result.
     *
     * @param filePath the path to the file to parse
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if parsing is interrupted
     * @throws TikaException if a Tika error occurs
     * @throws PipesException if a pipes infrastructure error occurs
     */
    public void parseWithMetadata(Path filePath)
            throws IOException, InterruptedException, TikaException, PipesException {
        try (PipesForkParser parser = new PipesForkParser();
             TikaInputStream tis = TikaInputStream.get(filePath)) {
            PipesForkResult result = parser.parse(tis);

            if (result.isSuccess()) {
                Metadata metadata = result.getMetadata();
                System.out.println("Content-Type: " + metadata.get(Metadata.CONTENT_TYPE));
                System.out.println("Title: " + metadata.get(TikaCoreProperties.TITLE));
                System.out.println("Creator: " + metadata.get(TikaCoreProperties.CREATOR));
                System.out.println("Content: " + result.getContent());
            }
        }
    }

    /**
     * Example of parsing documents with embedded files using RMETA mode.
     * <p>
     * <b>Both RMETA and CONCATENATE modes parse embedded content.</b> The key differences are:
     * <p>
     * <b>RMETA mode (recommended for most use cases):</b>
     * <ul>
     *   <li>Returns separate metadata objects for the container and each embedded document</li>
     *   <li>Preserves per-document metadata (author, title, dates, etc.) for each embedded file</li>
     *   <li>Exceptions from embedded documents are captured in each document's metadata
     *       (via {@link TikaCoreProperties#EMBEDDED_EXCEPTION}) - they are NOT silently swallowed</li>
     *   <li>You can see which embedded document caused a problem</li>
     * </ul>
     * <p>
     * <b>CONCATENATE mode (legacy behavior):</b>
     * <ul>
     *   <li>Returns a single metadata object with all content concatenated together</li>
     *   <li>Embedded document metadata is lost (only container metadata is preserved)</li>
     *   <li>Exceptions from embedded documents may be silently swallowed</li>
     *   <li>Simpler output but less visibility into what happened</li>
     * </ul>
     *
     * @param filePath the path to the file to parse
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if parsing is interrupted
     * @throws TikaException if a Tika error occurs
     * @throws PipesException if a pipes infrastructure error occurs
     * @see #parseEmbeddedDocumentsConcatenate(Path) for the legacy CONCATENATE mode example
     */
    public void parseEmbeddedDocumentsRmeta(Path filePath)
            throws IOException, InterruptedException, TikaException, PipesException {
        PipesForkParserConfig config = new PipesForkParserConfig()
                .setParseMode(ParseMode.RMETA);

        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(filePath)) {
            PipesForkResult result = parser.parse(tis);

            List<Metadata> metadataList = result.getMetadataList();
            System.out.println("Found " + metadataList.size() + " documents");

            for (int i = 0; i < metadataList.size(); i++) {
                Metadata m = metadataList.get(i);
                String resourceName = m.get(TikaCoreProperties.RESOURCE_NAME_KEY);
                String content = m.get(TikaCoreProperties.TIKA_CONTENT);

                if (i == 0) {
                    System.out.println("Container document:");
                } else {
                    System.out.println("Embedded document #" + i + ": " + resourceName);
                }
                System.out.println("  Content type: " + m.get(Metadata.CONTENT_TYPE));
                System.out.println("  Content length: " +
                        (content != null ? content.length() : 0) + " chars");

                // Check for exceptions that occurred while parsing this specific document
                String embeddedException = m.get(TikaCoreProperties.EMBEDDED_EXCEPTION);
                if (embeddedException != null) {
                    System.out.println("  WARNING - Exception occurred: " + embeddedException);
                }
            }
        }
    }

    /**
     * Example of parsing documents with embedded files using CONCATENATE mode (legacy).
     * <p>
     * <b>Both RMETA and CONCATENATE modes parse embedded content.</b> However, CONCATENATE
     * mode provides less visibility into the parsing process:
     * <ul>
     *   <li>All content from container and embedded documents is concatenated into one string</li>
     *   <li>Only a single metadata object is returned (container metadata only)</li>
     *   <li>Per-embedded-document metadata is lost</li>
     *   <li>Exceptions from embedded documents may be silently swallowed</li>
     * </ul>
     * <p>
     * <b>Recommendation:</b> Use RMETA mode ({@link #parseEmbeddedDocumentsRmeta(Path)}) unless
     * you specifically need the legacy concatenation behavior. RMETA gives you visibility into
     * embedded document exceptions and preserves metadata for each document.
     *
     * @param filePath the path to the file to parse
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if parsing is interrupted
     * @throws TikaException if a Tika error occurs
     * @throws PipesException if a pipes infrastructure error occurs
     */
    public void parseEmbeddedDocumentsConcatenate(Path filePath)
            throws IOException, InterruptedException, TikaException, PipesException {
        PipesForkParserConfig config = new PipesForkParserConfig()
                .setParseMode(ParseMode.CONCATENATE);

        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(filePath)) {
            PipesForkResult result = parser.parse(tis);

            // In CONCATENATE mode, there's only one metadata object
            List<Metadata> metadataList = result.getMetadataList();
            System.out.println("Metadata objects returned: " + metadataList.size()); // Always 1

            Metadata m = result.getMetadata();
            String content = result.getContent();

            System.out.println("Container content type: " + m.get(Metadata.CONTENT_TYPE));
            System.out.println("Total concatenated content length: " +
                    (content != null ? content.length() : 0) + " chars");

            // Note: In CONCATENATE mode, you cannot see:
            // - Which embedded documents were processed
            // - Metadata from individual embedded documents
            // - Exceptions that occurred in specific embedded documents
            // Use RMETA mode if you need this visibility
        }
    }

    /**
     * Example of proper error handling with PipesForkParser.
     * <p>
     * There are three categories of results to handle:
     * <ol>
     *   <li><b>Success</b> - Parsing completed successfully</li>
     *   <li><b>Process crash</b> - The forked JVM crashed (OOM, timeout, etc.).
     *       The parser will automatically restart for the next parse.</li>
     *   <li><b>Application error</b> - Configuration or infrastructure error.
     *       These throw {@link PipesForkParserException}.</li>
     * </ol>
     *
     * @param filePath the path to the file to parse
     * @return the extracted content, or error message if parsing failed
     */
    public String parseWithErrorHandling(Path filePath) {
        PipesForkParserConfig config = new PipesForkParserConfig()
                .setTimeoutMillis(30000);

        try (PipesForkParser parser = new PipesForkParser(config);
             TikaInputStream tis = TikaInputStream.get(filePath)) {

            PipesForkResult result = parser.parse(tis);

            if (result.isSuccess()) {
                return result.getContent();
            } else if (result.isProcessCrash()) {
                // Process crashed - could be OOM, timeout, or other crash
                // The next parse() call will automatically restart the process
                return "Process crashed: " + result.getStatus() +
                        ". Consider reducing memory usage or increasing timeout.";
            } else {
                // Other non-success status (e.g., fetch exception, parse exception)
                return "Parse failed: " + result.getStatus() + " - " + result.getMessage();
            }

        } catch (PipesForkParserException e) {
            // Application error - something is misconfigured
            return "Application error (" + e.getStatus() + "): " + e.getMessage();
        } catch (IOException | InterruptedException | TikaException | PipesException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Example of reusing PipesForkParser for multiple documents.
     * <p>
     * PipesForkParser is designed to be reused. Creating a new parser for each
     * document is inefficient because it requires starting a new forked JVM process.
     * <p>
     * This example shows the recommended pattern: create the parser once and
     * reuse it for multiple documents.
     *
     * @param filePaths the files to parse
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if parsing is interrupted
     * @throws TikaException if a Tika error occurs
     * @throws PipesException if a pipes infrastructure error occurs
     */
    public void parseManyFiles(List<Path> filePaths)
            throws IOException, InterruptedException, TikaException, PipesException {
        PipesForkParserConfig config = new PipesForkParserConfig()
                .setTimeoutMillis(30000)
                .setMaxFilesPerProcess(50);

        try (PipesForkParser parser = new PipesForkParser(config)) {
            for (Path filePath : filePaths) {
                try (TikaInputStream tis = TikaInputStream.get(filePath)) {
                    PipesForkResult result = parser.parse(tis);
                    if (result.isSuccess()) {
                        System.out.println("Parsed: " + filePath);
                        System.out.println("Content type: " +
                                result.getMetadata().get(Metadata.CONTENT_TYPE));
                    } else if (result.isProcessCrash()) {
                        System.err.println("Process crashed on: " + filePath +
                                " - " + result.getStatus());
                        // Parser will automatically restart for next document
                    } else {
                        System.err.println("Failed: " + filePath +
                                " - " + result.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Example of providing initial metadata hints.
     * <p>
     * You can provide metadata hints to the parser, such as the content type
     * if you already know it. This can improve parsing accuracy or performance.
     *
     * @param filePath the path to the file to parse
     * @param contentType the known content type
     * @return the extracted content
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if parsing is interrupted
     * @throws TikaException if a Tika error occurs
     * @throws PipesException if a pipes infrastructure error occurs
     */
    public String parseWithContentTypeHint(Path filePath, String contentType)
            throws IOException, InterruptedException, TikaException, PipesException {
        try (PipesForkParser parser = new PipesForkParser();
             TikaInputStream tis = TikaInputStream.get(filePath)) {

            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, contentType);

            PipesForkResult result = parser.parse(tis, metadata);
            return result.getContent();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: PipesForkParserExample <file-path>");
            System.exit(1);
        }

        Path filePath = Paths.get(args[0]);
        PipesForkParserExample example = new PipesForkParserExample();

        System.out.println("=== Basic Parse ===");
        String content = example.parseFileBasic(filePath);
        System.out.println(content);

        System.out.println("\n=== Parse with Metadata ===");
        example.parseWithMetadata(filePath);
    }
}
