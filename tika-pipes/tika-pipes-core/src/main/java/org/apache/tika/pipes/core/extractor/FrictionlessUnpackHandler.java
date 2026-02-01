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
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.core.extractor.frictionless.DataPackage;
import org.apache.tika.pipes.core.extractor.frictionless.FrictionlessResource;

/**
 * An UnpackHandler that collects embedded files for Frictionless Data Package output.
 *
 * Files are stored in a temporary directory under an "unpacked/" subdirectory.
 * SHA256 hashes are computed during the add() operation using DigestInputStream.
 * After parsing completes, buildDataPackage() creates the manifest.
 *
 * Output structure:
 * <pre>
 * temp-dir/
 * └── unpacked/
 *     ├── 00000001.pdf
 *     ├── 00000002.png
 *     └── ...
 * </pre>
 */
public class FrictionlessUnpackHandler extends AbstractUnpackHandler implements Closeable {

    private static final String UNPACKED_DIR = "unpacked";

    private final Path tempDirectory;
    private final Path unpackedDirectory;
    private final EmitKey containerEmitKey;
    private final UnpackConfig unpackConfig;
    private final List<FrictionlessFileInfo> embeddedFiles = new ArrayList<>();
    private Path originalDocumentPath;
    private String originalDocumentName;
    private String originalDocumentHash;
    private long originalDocumentBytes;
    private boolean closed = false;

    /**
     * Information about an embedded file including its SHA256 hash.
     */
    public record FrictionlessFileInfo(
            int id,
            String fileName,
            Path filePath,
            Metadata metadata,
            String sha256Hash,
            long bytes,
            String mediatype
    ) {
    }

    /**
     * Creates a new FrictionlessUnpackHandler.
     *
     * @param containerEmitKey the emit key for the container document
     * @param unpackConfig     the unpack configuration
     * @throws IOException if temp directory creation fails
     */
    public FrictionlessUnpackHandler(EmitKey containerEmitKey,
                                     UnpackConfig unpackConfig) throws IOException {
        this.containerEmitKey = containerEmitKey;
        this.unpackConfig = unpackConfig;
        this.tempDirectory = Files.createTempDirectory("tika-frictionless-");
        this.unpackedDirectory = tempDirectory.resolve(UNPACKED_DIR);
        Files.createDirectories(unpackedDirectory);
    }

    @Override
    public void add(int id, Metadata metadata, InputStream inputStream) throws IOException {
        super.add(id, metadata, inputStream);

        // Generate the file name based on emit key logic
        String emitKey = getEmitKey(containerEmitKey.getEmitKey(), id, unpackConfig, metadata);
        String fileName = flattenFileName(emitKey, id);

        // Get mediatype from metadata
        String mediatype = metadata.get(Metadata.CONTENT_TYPE);
        if (mediatype == null) {
            mediatype = "application/octet-stream";
        }
        // Remove any parameters from content type (e.g., charset)
        int semicolon = mediatype.indexOf(';');
        if (semicolon > 0) {
            mediatype = mediatype.substring(0, semicolon).trim();
        }

        // Write to temp file while computing SHA256 hash
        Path tempFile = unpackedDirectory.resolve(fileName);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }

        long bytes = 0;
        try (DigestInputStream dis = new DigestInputStream(inputStream, digest);
             OutputStream os = Files.newOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = dis.read(buffer)) != -1) {
                os.write(buffer, 0, read);
                bytes += read;
            }
        }

        String sha256Hash = FrictionlessResource.formatHash(digest.digest());

        embeddedFiles.add(new FrictionlessFileInfo(
                id, fileName, tempFile, metadata, sha256Hash, bytes, mediatype));
    }

    /**
     * Flattens an emit key path to a simple filename suitable for a zip entry.
     */
    private String flattenFileName(String emitKey, int id) {
        int lastSlash = Math.max(emitKey.lastIndexOf('/'), emitKey.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < emitKey.length() - 1) {
            return emitKey.substring(lastSlash + 1);
        }
        return emitKey;
    }

    /**
     * Stores the original container document for optional inclusion.
     *
     * @param inputStream the original document input stream
     * @param fileName    the file name for the original document
     * @throws IOException if storing fails
     */
    public void storeOriginalDocument(InputStream inputStream, String fileName) throws IOException {
        this.originalDocumentName = fileName;
        this.originalDocumentPath = tempDirectory.resolve(fileName);

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }

        long bytes = 0;
        try (DigestInputStream dis = new DigestInputStream(inputStream, digest);
             OutputStream os = Files.newOutputStream(originalDocumentPath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = dis.read(buffer)) != -1) {
                os.write(buffer, 0, read);
                bytes += read;
            }
        }

        this.originalDocumentHash = FrictionlessResource.formatHash(digest.digest());
        this.originalDocumentBytes = bytes;
    }

    /**
     * Builds the DataPackage manifest from collected files.
     *
     * @param containerName the name of the container document
     * @return the built DataPackage
     */
    public DataPackage buildDataPackage(String containerName) {
        DataPackage dataPackage = new DataPackage(containerName);

        // Add original document if included
        if (unpackConfig.isIncludeOriginal() && hasOriginalDocument()) {
            dataPackage.addResource(FrictionlessResource.create(
                    originalDocumentName,
                    detectMediatypeFromFilename(originalDocumentName),
                    originalDocumentBytes,
                    originalDocumentHash,
                    originalDocumentName
            ));
        }

        // Add all embedded files with unpacked/ prefix
        for (FrictionlessFileInfo fileInfo : embeddedFiles) {
            String path = UNPACKED_DIR + "/" + fileInfo.fileName();
            String originalName = fileInfo.metadata().get(TikaCoreProperties.RESOURCE_NAME_KEY);
            dataPackage.addResource(FrictionlessResource.create(
                    path,
                    fileInfo.mediatype(),
                    fileInfo.bytes(),
                    fileInfo.sha256Hash(),
                    originalName
            ));
        }

        return dataPackage;
    }

    /**
     * Simple mediatype detection from filename extension.
     */
    private String detectMediatypeFromFilename(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lower.endsWith(".xml")) {
            return "application/xml";
        } else if (lower.endsWith(".doc")) {
            return "application/msword";
        } else if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lower.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (lower.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        } else if (lower.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        } else if (lower.endsWith(".txt")) {
            return "text/plain";
        } else if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html";
        } else if (lower.endsWith(".json")) {
            return "application/json";
        } else if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lower.endsWith(".gif")) {
            return "image/gif";
        } else if (lower.endsWith(".zip")) {
            return "application/zip";
        }
        return "application/octet-stream";
    }

    /**
     * Returns the temporary directory where files are stored.
     */
    public Path getTempDirectory() {
        return tempDirectory;
    }

    /**
     * Returns the unpacked subdirectory where embedded files are stored.
     */
    public Path getUnpackedDirectory() {
        return unpackedDirectory;
    }

    /**
     * Returns information about all embedded files.
     */
    public List<FrictionlessFileInfo> getEmbeddedFiles() {
        return embeddedFiles;
    }

    /**
     * Returns true if there are any embedded files.
     */
    public boolean hasEmbeddedFiles() {
        return !embeddedFiles.isEmpty();
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

    /**
     * Returns the UnpackConfig used by this handler.
     */
    public UnpackConfig getUnpackConfig() {
        return unpackConfig;
    }

    /**
     * Returns the container emit key.
     */
    public EmitKey getContainerEmitKey() {
        return containerEmitKey;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            FileUtils.deleteDirectory(tempDirectory.toFile());
        }
    }
}
