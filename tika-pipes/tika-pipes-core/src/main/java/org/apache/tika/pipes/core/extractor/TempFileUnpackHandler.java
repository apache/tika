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
package org.apache.tika.pipes.core.extractor;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.api.emitter.EmitKey;

/**
 * An UnpackHandler that writes embedded bytes to a temporary directory
 * for later zipping. Files are stored with their emit key names (flattened, with path
 * separators replaced).
 */
public class TempFileUnpackHandler extends AbstractUnpackHandler
        implements Closeable {

    private final Path tempDirectory;
    private final EmitKey containerEmitKey;
    private final UnpackConfig unpackConfig;
    private final List<EmbeddedFileInfo> embeddedFiles = new ArrayList<>();
    private Path originalDocumentPath;
    private String originalDocumentName;
    private boolean closed = false;

    /**
     * Information about an embedded file stored in the temp directory.
     */
    public record EmbeddedFileInfo(int id, String fileName, Path filePath, Metadata metadata) {
    }

    public TempFileUnpackHandler(EmitKey containerEmitKey,
                                 UnpackConfig unpackConfig) throws IOException {
        this.containerEmitKey = containerEmitKey;
        this.unpackConfig = unpackConfig;
        this.tempDirectory = Files.createTempDirectory("tika-unpack-");
    }

    @Override
    public void add(int id, Metadata metadata, InputStream inputStream) throws IOException {
        super.add(id, metadata, inputStream);

        // Generate the file name based on emit key logic
        String emitKey = getEmitKey(containerEmitKey.getEmitKey(), id, unpackConfig, metadata);

        // Flatten the path for zip entry name - use just the filename portion
        String fileName = flattenFileName(emitKey, id);

        // Write to temp file
        Path tempFile = tempDirectory.resolve(fileName);
        try (OutputStream os = Files.newOutputStream(tempFile)) {
            inputStream.transferTo(os);
        }

        embeddedFiles.add(new EmbeddedFileInfo(id, fileName, tempFile, metadata));
    }

    /**
     * Flattens an emit key path to a simple filename suitable for a zip entry.
     * Replaces path separators and uses the last component plus id for uniqueness.
     */
    private String flattenFileName(String emitKey, int id) {
        // Get the last path component
        int lastSlash = Math.max(emitKey.lastIndexOf('/'), emitKey.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < emitKey.length() - 1) {
            return emitKey.substring(lastSlash + 1);
        }
        return emitKey;
    }

    /**
     * Returns the temporary directory where embedded files are stored.
     */
    public Path getTempDirectory() {
        return tempDirectory;
    }

    /**
     * Returns information about all embedded files stored.
     */
    public List<EmbeddedFileInfo> getEmbeddedFiles() {
        return embeddedFiles;
    }

    /**
     * Returns true if there are any embedded files stored.
     */
    public boolean hasEmbeddedFiles() {
        return !embeddedFiles.isEmpty();
    }

    /**
     * Stores the original container document for inclusion in the zip.
     * Call this before parsing if includeOriginal is enabled.
     *
     * @param inputStream the original document input stream
     * @param fileName the file name for the original document
     */
    public void storeOriginalDocument(InputStream inputStream, String fileName) throws IOException {
        this.originalDocumentName = fileName;
        this.originalDocumentPath = tempDirectory.resolve("_original_" + fileName);
        try (OutputStream os = Files.newOutputStream(originalDocumentPath)) {
            inputStream.transferTo(os);
        }
    }

    /**
     * Returns the path to the original document if stored.
     */
    public Path getOriginalDocumentPath() {
        return originalDocumentPath;
    }

    /**
     * Returns the name of the original document if stored.
     */
    public String getOriginalDocumentName() {
        return originalDocumentName;
    }

    /**
     * Returns true if the original document was stored.
     */
    public boolean hasOriginalDocument() {
        return originalDocumentPath != null && Files.exists(originalDocumentPath);
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            // Clean up temp directory - caller should have already zipped if needed
            FileUtils.deleteDirectory(tempDirectory.toFile());
        }
    }
}
