package org.apache.tika.pipes.fetchers.filesystem;

import org.pf4j.Extension;

import org.apache.tika.pipes.fetchers.core.DefaultFetcherConfig;

@Extension
public class FileSystemFetcherConfig extends DefaultFetcherConfig {
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
