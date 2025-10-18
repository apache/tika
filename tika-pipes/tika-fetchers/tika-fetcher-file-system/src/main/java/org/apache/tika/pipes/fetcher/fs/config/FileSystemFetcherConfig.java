package org.apache.tika.pipes.fetcher.fs.config;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

public class FileSystemFetcherConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static FileSystemFetcherConfig load(String json) throws IOException  {
        return OBJECT_MAPPER.readValue(json, FileSystemFetcherConfig.class);
    }

    private String basePath;
    private boolean extractFileSystemMetadata;

    public boolean isExtractFileSystemMetadata() {
        return extractFileSystemMetadata;
    }

    public FileSystemFetcherConfig setExtractFileSystemMetadata(boolean extractFileSystemMetadata) {
        this.extractFileSystemMetadata = extractFileSystemMetadata;
        return this;
    }

    public String getBasePath() {
        return basePath;
    }

    public FileSystemFetcherConfig setBasePath(String basePath) {
        this.basePath = basePath;
        return this;
    }
}
